package com.example.freelocalplayer.data.room

import android.content.Context
import android.util.Log
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.freelocalplayer.data.LibraryState
import com.example.freelocalplayer.data.LibraryStore
import com.example.freelocalplayer.data.MediaEntry
import com.example.freelocalplayer.data.UserPlaylist
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Phone QA: production JSON + Room is read only. Synthetic failure tests use
 * an in-memory Room DB and cannot overwrite any persistent user data.
 */
@RunWith(AndroidJUnit4::class)
class VerifiedRoomReadDeviceTest {
    private val tag = "FLP_ROOM_READ_QA"

    @Test
    fun actualPhoneCatalogIsVerifiedWithoutRepairOrFlagChanges() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = context.getSharedPreferences("local_library_v1", Context.MODE_PRIVATE)
        val beforeJson = prefs.getString("library", null)
        assertTrue("Canonical JSON must exist; do not accept empty app data", !beforeJson.isNullOrBlank())
        assertFalse("Rollout must remain disabled on a personal phone",
            prefs.getBoolean("room_verified_read_0c3b", false))

        val store = LibraryStore(context)
        store.awaitPendingWrites()
        // Does NOT call load(), which would repair/rewrite the persistent Room.
        val decision = store.previewVerifiedRoomReadWithoutRepairForQa()
        assertEquals("Read-only source must be fully verified", VerifiedRoomReadSelector.Path.ROOM,
            decision.path)
        assertTrue(decision.verifiedRoom)
        assertTrue("Real catalog must not be empty", decision.state.media.isNotEmpty())
        assertEquals(beforeJson, prefs.getString("library", null))
        assertFalse(prefs.getBoolean("room_verified_read_0c3b", false))
        Log.i(tag, "PHONE_VERIFIED_ROOM_READ=PASS tracks=${decision.state.media.size} " +
            "playlists=${decision.state.playlists.size} retired=${decision.state.retiredMedia.size}")
    }

    @Test
    fun staleAndWrongSignatureInMemoryMustReturnExactJson() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, ShadowRoomDatabase::class.java).build()
        try {
            val original = LibraryState(
                media = listOf(MediaEntry("qa-fixed-uuid", "content://media/x/audio/media/1",
                    "QA", false, "[00:01.10]old")),
                playlists = listOf(UserPlaylist("qa-playlist", "QA list", listOf("qa-fixed-uuid")))
            )
            ShadowRoomMirror.mirrorInto(db, original)
            val readRows = ShadowRowSet(db.dao().tracks(), db.dao().playlists(),
                db.dao().playlistItems(), db.dao().retiredPlaces())
            val originalStamp = db.dao().stamp()
            val valid = VerifiedRoomReadSelector.select(original, true) { readRows to originalStamp }
            assertEquals(VerifiedRoomReadSelector.Path.ROOM, valid.path)

            val newerJson = original.copy(media = listOf(original.media.single().copy(lyrics = "newer LRC")))
            val rejected = VerifiedRoomReadSelector.select(newerJson, true) { readRows to originalStamp }
            assertEquals(VerifiedRoomReadSelector.Path.JSON, rejected.path)
            assertEquals(newerJson, rejected.state)
            assertEquals(RoomPrimaryCutoverGate.Status.ROOM_DATA_MISMATCH, rejected.validation)

            val wrongSignature = VerifiedRoomReadSelector.select(original, true) {
                readRows to originalStamp?.copy(signature = "0".repeat(64))
            }
            assertEquals(VerifiedRoomReadSelector.Path.JSON, wrongSignature.path)
            assertEquals(RoomPrimaryCutoverGate.Status.ROOM_SIGNATURE_MISMATCH,
                wrongSignature.validation)
            assertEquals(original, ShadowRowSet(db.dao().tracks(), db.dao().playlists(),
                db.dao().playlistItems(), db.dao().retiredPlaces()).reconstruct())
            Log.i(tag, "IN_MEMORY_STALE_AND_SIGNATURE_FALLBACK=PASS")
        } finally { db.close() }
    }

    @Test
    fun readFailureFallsBackWithoutTouchingPersistentStorage() {
        val canonical = LibraryState(media = listOf(MediaEntry(
            "persisted-uuid", "content://media/x/audio/media/42", "Saved", false, "TXT")))
        val disabled = VerifiedRoomReadSelector.select(canonical, false) {
            throw AssertionError("Room must not even be opened when disabled")
        }
        assertEquals(VerifiedRoomReadSelector.Path.JSON, disabled.path)
        val fault = VerifiedRoomReadSelector.select(canonical, true) {
            throw IllegalStateException("Injected SQLite I/O failure")
        }
        assertEquals(VerifiedRoomReadSelector.Path.JSON, fault.path)
        assertEquals(VerifiedRoomReadSelector.Reason.ROOM_READ_FAILED, fault.reason)
        assertEquals(canonical, fault.state)
        Log.i(tag, "IN_MEMORY_ROOM_FAILURE_JSON_FALLBACK=PASS")
    }
}
