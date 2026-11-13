package com.example.freelocalplayer.data.room

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.freelocalplayer.data.LibraryState
import com.example.freelocalplayer.data.MediaEntry
import com.example.freelocalplayer.data.PlaylistPlace
import com.example.freelocalplayer.data.RetiredMedia
import com.example.freelocalplayer.data.UserPlaylist
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Tests a fresh *in-memory* Room database; never touches user's actual app data. */
@RunWith(AndroidJUnit4::class)
class ShadowRoomMigrationDeviceTest {
    private fun sample() = LibraryState(
        media = listOf(MediaEntry("keep-uuid", "content://media/x/audio/media/1", "Трек", false,
            "[00:01.000]Караоке", "mediastore:x:audio:1", 500, 1000)),
        playlists = listOf(UserPlaylist("list-uuid", "Плейлист", listOf("keep-uuid"))),
        retiredMedia = listOf(RetiredMedia(
            MediaEntry("archived-uuid", "content://media/x/audio/media/2", "Архив", false, "LRC"),
            listOf(PlaylistPlace("list-uuid", 1))
        ))
    )

    @Test fun inMemorySqliteRoundTripAndTransactionalRollback() {
        val app = ApplicationProvider.getApplicationContext<android.content.Context>()
        val db = Room.inMemoryDatabaseBuilder(app, ShadowRoomDatabase::class.java).build()
        try {
            val source = sample()
            val rows = ShadowRoomMapping.toRows(source)
            val dao = db.dao()
            db.runInTransaction {
                dao.putTracks(rows.tracks)
                dao.putPlaylists(rows.playlists)
                dao.putPlaylistItems(rows.playlistItems)
                dao.putRetiredPlaces(rows.retiredPlaces)
                dao.putStamp(ShadowStamp(signature = ShadowRoomMapping.fingerprint(rows),
                    tracks = source.media.size, playlists = source.playlists.size,
                    retiredTracks = source.retiredMedia.size))
            }
            val persisted = ShadowRowSet(dao.tracks(), dao.playlists(), dao.playlistItems(), dao.retiredPlaces())
            assertEquals(source, persisted.reconstruct())
            assertEquals(ShadowRoomMapping.fingerprint(rows), dao.stamp()?.signature)

            try {
                db.runInTransaction {
                    dao.deleteTracks(listOf("keep-uuid"))
                    error("Deliberate rollback test")
                }
            } catch (_: IllegalStateException) {
                // Expected: the SQLite transaction must undo the deletion.
            }
            assertEquals(source, ShadowRowSet(
                dao.tracks(), dao.playlists(), dao.playlistItems(), dao.retiredPlaces()
            ).reconstruct())
        } finally {
            db.close()
        }
    }

    /** The production mirroring algorithm, not a synthetic manual DAO insert. */
    @Test fun productionMirrorUpdatesDeletesAndKeepsUserUuid() {
        val app = ApplicationProvider.getApplicationContext<android.content.Context>()
        val db = Room.inMemoryDatabaseBuilder(app, ShadowRoomDatabase::class.java).build()
        try {
            val original = sample()
            ShadowRoomMirror.mirrorInto(db, original)
            assertEquals(original, currentData(db))
            val originalSignature = db.dao().stamp()?.signature
            assertEquals(
                ShadowRoomMapping.fingerprint(ShadowRoomMapping.toRows(original)),
                originalSignature
            )

            val updated = original.copy(
                media = listOf(
                    original.media.first().copy(lyrics = "[00:03.250]Новая строка"),
                    MediaEntry("new-uuid", "content://media/x/audio/media/3", "Новое", false)
                ),
                playlists = listOf(
                    UserPlaylist("list-uuid", "Обновлённый", listOf("new-uuid", "keep-uuid")),
                    UserPlaylist("second-list", "Второй", listOf("keep-uuid"))
                ),
                retiredMedia = emptyList()
            )
            ShadowRoomMirror.mirrorInto(db, updated)
            assertEquals(updated, currentData(db))
            assertNotEquals(originalSignature, db.dao().stamp()?.signature)

            // Reopening without changes must not mutate the current snapshot.
            val stamp = db.dao().stamp()
            ShadowRoomMirror.mirrorInto(db, updated)
            assertEquals(stamp, db.dao().stamp())
            assertEquals(updated, currentData(db))

            // Removing the last track and both playlists prunes obsolete DB rows.
            ShadowRoomMirror.mirrorInto(db, LibraryState())
            assertEquals(LibraryState(), currentData(db))
            assertTrue(db.dao().tracks().isEmpty())
            assertTrue(db.dao().playlistItems().isEmpty())
            assertTrue(db.dao().retiredPlaces().isEmpty())
        } finally {
            db.close()
        }
    }

    /** Malformed input is rejected before any write to the existing Room snapshot. */
    @Test fun productionMirrorRejectsDuplicateUuidAndPreservesPreviousRows() {
        val app = ApplicationProvider.getApplicationContext<android.content.Context>()
        val db = Room.inMemoryDatabaseBuilder(app, ShadowRoomDatabase::class.java).build()
        try {
            val original = sample()
            ShadowRoomMirror.mirrorInto(db, original)
            val signature = db.dao().stamp()?.signature
            val invalid = original.copy(media = original.media + original.media.first())
            var failed = false
            try {
                ShadowRoomMirror.mirrorInto(db, invalid)
            } catch (_: IllegalArgumentException) {
                failed = true
            }
            assertTrue("Duplicate UUID must abort the transaction", failed)
            assertEquals(original, currentData(db))
            assertEquals(signature, db.dao().stamp()?.signature)
        } finally {
            db.close()
        }
    }

    /** Exercises the production diff algorithm with many rows and ordered lists. */
    @Test fun productionMirrorBulkCatalogRoundTrip() {
        val app = ApplicationProvider.getApplicationContext<android.content.Context>()
        val db = Room.inMemoryDatabaseBuilder(app, ShadowRoomDatabase::class.java).build()
        try {
            val entries = (0 until 1200).map { index ->
                MediaEntry(
                    "stable-$index", "content://media/x/audio/media/$index", "Трек $index",
                    false, "[00:00.100]Текст $index", sizeBytes = index.toLong() + 1,
                    durationMs = 120000L
                )
            }
            val lists = (0 until 5).map { index ->
                UserPlaylist("playlist-$index", "Список $index",
                    entries.subList(index * 200, (index + 1) * 200).map { it.id })
            }
            val state = LibraryState(entries, lists)
            ShadowRoomMirror.mirrorInto(db, state)
            assertEquals(state, currentData(db))
            assertEquals(state.media.size, db.dao().stamp()?.tracks)
            // A consecutive mirror with identical content must also succeed.
            ShadowRoomMirror.mirrorInto(db, state)
            assertEquals(state, currentData(db))
        } finally {
            db.close()
        }
    }

    private fun currentData(db: ShadowRoomDatabase): LibraryState = ShadowRowSet(
        db.dao().tracks(), db.dao().playlists(),
        db.dao().playlistItems(), db.dao().retiredPlaces()
    ).reconstruct()

}
