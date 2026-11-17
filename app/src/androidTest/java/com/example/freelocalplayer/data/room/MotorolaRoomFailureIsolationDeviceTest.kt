package com.example.freelocalplayer.data.room

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.freelocalplayer.data.CanonicalLibraryJsonDecoder
import com.example.freelocalplayer.data.LatestSnapshotWriter
import com.example.freelocalplayer.data.LibraryState
import com.example.freelocalplayer.data.MediaEntry
import com.example.freelocalplayer.data.PlaylistPlace
import com.example.freelocalplayer.data.RetiredMedia
import com.example.freelocalplayer.data.UserPlaylist
import java.io.IOException
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 0-C.3-E1: fault injection on a physical Android phone, but NEVER on user data.
 *
 * All Room tests use Room.inMemoryDatabaseBuilder except a single explicitly named,
 * randomly generated QA database, which is deleted in finally. The real database
 * flp-room-shadow-v1.db is never opened. Production SharedPreferences, music,
 * playback state, real media, and SAF backups are never read or written here.
 * No actual process kill, application restart, database migration, or Room-primary
 * activation is performed.
 */
@RunWith(AndroidJUnit4::class)
class MotorolaRoomFailureIsolationDeviceTest {
    private fun original() = LibraryState(
        media = listOf(
            MediaEntry(
                "qa-track-uuid-1", "content://media/external/audio/media/1",
                "QA Первый 🎵", false, "[00:01.000]Первый текст",
                "mediastore:external:audio:1", 1234L, 150000L
            ),
            MediaEntry(
                "qa-track-uuid-2", "content://media/external/audio/media/2",
                "QA Второй", false, "[00:02.000]Второй текст",
                "mediastore:external:audio:2", 5678L, 180000L
            )
        ),
        playlists = listOf(
            UserPlaylist("qa-playlist-uuid", "QA Плейлист", listOf("qa-track-uuid-2", "qa-track-uuid-1"))
        ),
        retiredMedia = listOf(
            RetiredMedia(
                MediaEntry("qa-retired-uuid", "content://media/external/audio/media/3",
                    "Удалённый QA", false, "[00:03.000]Архив"),
                listOf(PlaylistPlace("qa-playlist-uuid", 1))
            )
        )
    )

    private fun current(db: ShadowRoomDatabase) = ShadowRowSet(
        db.dao().tracks(), db.dao().playlists(),
        db.dao().playlistItems(), db.dao().retiredPlaces()
    ).reconstruct()

    private fun context(): Context = ApplicationProvider.getApplicationContext()

    @Test
    fun sqliteTransactionExceptionRollsBackTrackDeletionAndStamp() {
        val db = Room.inMemoryDatabaseBuilder(context(), ShadowRoomDatabase::class.java).build()
        try {
            val canonical = original()
            ShadowRoomMirror.mirrorInto(db, canonical)
            val beforeStamp = db.dao().stamp()
            assertNotNull(beforeStamp)
            var rejected = false
            try {
                db.runInTransaction {
                    db.dao().deleteTracks(listOf("qa-track-uuid-1"))
                    error("QA-only injected rollback")
                }
            } catch (expected: IllegalStateException) {
                rejected = true
            }
            assertTrue("Injected failure must abort the transaction", rejected)
            assertEquals(canonical, current(db))
            assertEquals(beforeStamp, db.dao().stamp())
        } finally {
            db.close()
        }
    }

    @Test
    fun sqliteTransactionExceptionRollsBackPlaylistAndLyricsMutation() {
        val db = Room.inMemoryDatabaseBuilder(context(), ShadowRoomDatabase::class.java).build()
        try {
            val canonical = original()
            ShadowRoomMirror.mirrorInto(db, canonical)
            val beforeStamp = db.dao().stamp()
            var rejected = false
            try {
                db.runInTransaction {
                    db.dao().putTracks(listOf(db.dao().tracks().first().copy(lyrics = "BAD QA LRC")))
                    db.dao().deletePlaylistItem("qa-playlist-uuid", 0)
                    error("QA-only mid-transaction disk-write failure")
                }
            } catch (expected: IllegalStateException) {
                rejected = true
            }
            assertTrue(rejected)
            assertEquals(canonical, current(db))
            assertEquals(beforeStamp, db.dao().stamp())
        } finally {
            db.close()
        }
    }

    @Test
    fun staleSqliteReturnsNewerCanonicalJsonWithoutRepair() {
        val db = Room.inMemoryDatabaseBuilder(context(), ShadowRoomDatabase::class.java).build()
        try {
            val old = original()
            ShadowRoomMirror.mirrorInto(db, old)
            val rows = ShadowRowSet(db.dao().tracks(), db.dao().playlists(),
                db.dao().playlistItems(), db.dao().retiredPlaces())
            val stamp = db.dao().stamp()
            val newer = old.copy(media = old.media.map { item ->
                if (item.id == "qa-track-uuid-1") item.copy(lyrics = "[00:04.000]Новый текст") else item
            })
            val decision = VerifiedRoomReadSelector.select(newer, true) { rows to stamp }
            assertEquals(VerifiedRoomReadSelector.Path.JSON, decision.path)
            assertEquals(RoomPrimaryCutoverGate.Status.ROOM_DATA_MISMATCH, decision.validation)
            assertEquals(newer, decision.state)
            assertEquals(old, current(db))
        } finally {
            db.close()
        }
    }

    @Test
    fun badSignatureAndIncorrectCountersReturnOriginalJson() {
        val db = Room.inMemoryDatabaseBuilder(context(), ShadowRoomDatabase::class.java).build()
        try {
            val canonical = original()
            ShadowRoomMirror.mirrorInto(db, canonical)
            val rows = ShadowRowSet(db.dao().tracks(), db.dao().playlists(),
                db.dao().playlistItems(), db.dao().retiredPlaces())
            val stamp = db.dao().stamp() ?: error("Missing synthetic stamp")
            val forged = VerifiedRoomReadSelector.select(canonical, true) {
                rows to stamp.copy(signature = "0".repeat(64))
            }
            assertEquals(VerifiedRoomReadSelector.Path.JSON, forged.path)
            assertEquals(RoomPrimaryCutoverGate.Status.ROOM_SIGNATURE_MISMATCH, forged.validation)
            assertEquals(canonical, forged.state)
            val wrongCount = VerifiedRoomReadSelector.select(canonical, true) {
                rows to stamp.copy(tracks = stamp.tracks + 1)
            }
            assertEquals(VerifiedRoomReadSelector.Path.JSON, wrongCount.path)
            assertEquals(RoomPrimaryCutoverGate.Status.ROOM_COUNTERS_MISMATCH, wrongCount.validation)
            assertEquals(canonical, wrongCount.state)
            assertEquals(canonical, current(db))
        } finally {
            db.close()
        }
    }

    @Test
    fun missingStampReturnsJsonEvenWhenSqliteRowsMatch() {
        val db = Room.inMemoryDatabaseBuilder(context(), ShadowRoomDatabase::class.java).build()
        try {
            val canonical = original()
            ShadowRoomMirror.mirrorInto(db, canonical)
            val rows = ShadowRowSet(db.dao().tracks(), db.dao().playlists(),
                db.dao().playlistItems(), db.dao().retiredPlaces())
            val decision = VerifiedRoomReadSelector.select(canonical, true) { rows to null }
            assertEquals(VerifiedRoomReadSelector.Path.JSON, decision.path)
            assertEquals(RoomPrimaryCutoverGate.Status.ROOM_STAMP_MISSING, decision.validation)
            assertEquals(canonical, decision.state)
            assertEquals(canonical, current(db))
        } finally {
            db.close()
        }
    }

    @Test
    fun sqliteReadExceptionAndDisabledReadPreserveJson() {
        val canonical = original()
        val failed = VerifiedRoomReadSelector.select(canonical, true) {
            throw IOException("QA-only simulated SQLite I/O failure")
        }
        assertEquals(VerifiedRoomReadSelector.Path.JSON, failed.path)
        assertEquals(VerifiedRoomReadSelector.Reason.ROOM_READ_FAILED, failed.reason)
        assertEquals(canonical, failed.state)
        val disabled = VerifiedRoomReadSelector.select(canonical, false) {
            fail("Disabled Room must never read SQLite")
            throw AssertionError("Unreachable if disabled")
        }
        assertEquals(VerifiedRoomReadSelector.Path.JSON, disabled.path)
        assertEquals(VerifiedRoomReadSelector.Reason.DISABLED, disabled.reason)
        assertEquals(canonical, disabled.state)
    }

    @Test
    fun uniqueQaDatabaseSurvivesCloseAndReopenWithoutTouchingProduction() {
        val app = context()
        val databaseName = "flp-qa-0c3e1-${UUID.randomUUID()}.db"
        assertTrue(databaseName.startsWith("flp-qa-0c3e1-"))
        assertFalse(databaseName == "flp-room-shadow-v1.db")
        val userDb = app.getDatabasePath("flp-room-shadow-v1.db")
        val userDbPath = userDb.absolutePath
        val testDb = app.getDatabasePath(databaseName)
        assertFalse(testDb.exists())
        assertFalse(testDb.absolutePath == userDbPath)
        var db: ShadowRoomDatabase? = null
        try {
            db = Room.databaseBuilder(app, ShadowRoomDatabase::class.java, databaseName).build()
            val canonical = original()
            ShadowRoomMirror.mirrorInto(db, canonical)
            val savedStamp = db.dao().stamp()
            assertEquals(canonical, current(db))
            db.close()
            db = null
            val reopened = Room.databaseBuilder(app, ShadowRoomDatabase::class.java, databaseName).build()
            db = reopened
            assertEquals(canonical, current(reopened))
            assertEquals(savedStamp, reopened.dao().stamp())
            val choice = VerifiedRoomReadSelector.select(canonical, true) {
                ShadowRowSet(reopened.dao().tracks(), reopened.dao().playlists(),
                    reopened.dao().playlistItems(), reopened.dao().retiredPlaces()) to reopened.dao().stamp()
            }
            assertEquals(VerifiedRoomReadSelector.Path.ROOM, choice.path)
            assertEquals(canonical, choice.state)
        } finally {
            db?.close()
            assertTrue("Unique QA database must be removed after test", app.deleteDatabase(databaseName))
            assertFalse(testDb.exists())
        }
    }

    @Test
    fun writerFailureIsObservableThenNewerSyntheticSaveRecovers() {
        val failures = mutableListOf<String>()
        val persisted = mutableListOf<String>()
        val writer = LatestSnapshotWriter<String>(
            write = { value ->
                if (value == "broken") throw IOException("QA simulated SharedPreferences commit failure")
                synchronized(persisted) { persisted.add(value) }
            },
            onWriteFailure = { _, problem -> synchronized(failures) { failures.add(problem.message.orEmpty()) } }
        )
        writer.submit("broken")
        var rejected = false
        try {
            writer.awaitIdle()
        } catch (expected: IOException) {
            rejected = true
        }
        assertTrue("Failed write must reach caller", rejected)
        assertTrue(synchronized(persisted) { persisted.isEmpty() })
        writer.submit("recovered")
        writer.awaitIdle()
        assertEquals(listOf("recovered"), synchronized(persisted) { persisted.toList() })
        assertEquals(1, synchronized(failures) { failures.size })
    }

    @Test
    fun expiredRoomLeaseCannotEnableLongLivedRoomRead() {
        val now = 1_900_000_000_000L
        assertEquals(RoomReadRolloutWindow.Status.DISABLED,
            RoomReadRolloutWindow.evaluate(false, now + 1000L, now))
        assertEquals(RoomReadRolloutWindow.Status.EXPIRED_OR_INVALID,
            RoomReadRolloutWindow.evaluate(true, now - 1L, now))
        assertEquals(RoomReadRolloutWindow.Status.EXPIRED_OR_INVALID,
            RoomReadRolloutWindow.evaluate(true, now + RoomReadRolloutWindow.MAX_LEASE_MS + 1L, now))
        assertEquals(RoomReadRolloutWindow.Status.ACTIVE,
            RoomReadRolloutWindow.evaluate(true, now + 1000L, now))
        assertEquals(RoomReadRolloutWindow.Status.EXPIRED_OR_INVALID,
            RoomReadRolloutWindow.evaluate(true, Long.MAX_VALUE, now))
    }

    @Test
    fun malformedSyntheticCanonicalJsonCannotBecomeEmptyCatalog() {
        val malformed = """{"media":[{"id":"","uri":"content://media/external/audio/media/1"}],"playlists":[]}"""
        var rejected = false
        try {
            CanonicalLibraryJsonDecoder.decode(malformed, true)
        } catch (expected: IllegalArgumentException) {
            rejected = true
        }
        assertTrue("Malformed persisted JSON must fail closed", rejected)
        assertEquals(LibraryState(), CanonicalLibraryJsonDecoder.decode(null, false))
    }
}
