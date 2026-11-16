package com.example.freelocalplayer.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.freelocalplayer.data.room.VerifiedRoomReadSelector
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.security.MessageDigest

/**
 * Stage 0-C.3-D. Synthetic tests operate solely on in-memory JSON strings.
 * The real-catalog test READS persistent preferences and Room but never calls
 * LibraryStore.load(), mirrors a database, changes a flag or saves a library.
 */
@RunWith(AndroidJUnit4::class)
class CanonicalLibraryJsonDecoderDeviceTest {
    private fun track(id: String = "media-uuid"): JSONObject = JSONObject()
        .put("id", id)
        .put("uri", "content://media/external/audio/media/42")
        .put("title", "Старый текст")
        .put("video", false)
        .put("lyrics", "[00:01.000]Привет 🎵")
        .put("sourceKey", "mediastore:external:audio:42")
        .put("sizeBytes", 123456L)
        .put("durationMs", 180000L)

    private fun sample(): JSONObject = JSONObject()
        .put("media", JSONArray().put(track()))
        .put("playlists", JSONArray().put(JSONObject()
            .put("id", "list-uuid")
            .put("name", "Любимые")
            .put("mediaIds", JSONArray().put("media-uuid"))))
        .put("retiredMedia", JSONArray().put(JSONObject()
            .put("entry", track("retired-uuid"))
            .put("playlistPlaces", JSONArray().put(JSONObject()
                .put("playlistId", "list-uuid")
                .put("index", 2)))))

    private fun rejected(expectedFragment: String, json: String) {
        val failure = try {
            CanonicalLibraryJsonDecoder.decode(json, true)
            null
        } catch (error: Exception) { error }
        assertTrue("Expected rejection containing $expectedFragment", failure != null)
        assertTrue("Wrong error: ${failure?.message}",
            failure?.message?.contains(expectedFragment) == true)
    }

    @Test fun firstInstallWithoutKeyIsEmpty() {
        assertEquals(LibraryState(), CanonicalLibraryJsonDecoder.decode(null, false))
    }

    @Test fun currentSnapshotPreservesUuidLyricsOrderAndRetiredPositions() {
        val original = sample().toString()
        val state = CanonicalLibraryJsonDecoder.decode(original, true)
        assertEquals(listOf("media-uuid"), state.media.map { it.id })
        assertEquals("[00:01.000]Привет 🎵", state.media.first().lyrics)
        assertEquals(listOf("media-uuid"), state.playlists.single().mediaIds)
        assertEquals(2, state.retiredMedia.single().playlistPlaces.single().index)
        assertEquals("retired-uuid", state.retiredMedia.single().entry.id)
    }

    @Test fun historicalSnapshotWithoutRetiredMediaRemainsSupported() {
        val original = sample().apply { remove("retiredMedia") }.toString()
        assertTrue(CanonicalLibraryJsonDecoder.decode(original, true).retiredMedia.isEmpty())
    }

    @Test fun presentButEmptyOrMissingMediaArrayMustNotBecomeEmptyLibrary() {
        rejected("CANONICAL_JSON_MISSING_OR_BLANK", "")
        rejected("CANONICAL_MISSING_ARRAY_media", "{}")
        rejected("CANONICAL_MISSING_ARRAY_media", "{\"playlists\":[]}")
    }

    @Test fun malformedMediaItemIsNotSilentlySkipped() {
        val json = sample().put("media", JSONArray().put(JSONObject().put("title", "lost")))
        rejected("CANONICAL_MISSING_TEXT_id", json.toString())
    }

    @Test fun malformedPlaylistItemIsNotSilentlySkipped() {
        val json = sample().put("playlists", JSONArray().put(JSONObject()
            .put("id", "list-uuid").put("mediaIds", JSONArray().put(JSONObject()))))
        rejected("CANONICAL_INVALID_PLAYLIST_MEDIA_ID", json.toString())
    }

    @Test fun duplicateTrackIdIsRejectedBeforeAnyWrite() {
        val json = sample().put("media", JSONArray().put(track()).put(track()))
        rejected("CANONICAL_DUPLICATE_TRACK_ID", json.toString())
    }

    @Test fun malformedRetiredIndexCannotBeSilentlyRemoved() {
        val json = sample()
        val wrapper = json.getJSONArray("retiredMedia").getJSONObject(0)
        wrapper.getJSONArray("playlistPlaces").getJSONObject(0).put("index", -7)
        rejected("CANONICAL_INVALID_RETIRED_INDEX", json.toString())
    }

    @Test fun missingRetiredPlaylistPositionsCannotBecomeEmptyPositions() {
        val json = sample()
        json.getJSONArray("retiredMedia").getJSONObject(0).remove("playlistPlaces")
        rejected("CANONICAL_MISSING_ARRAY_playlistPlaces", json.toString())
    }

    @Test fun actualPhoneCatalogIsValidAndNotChangedByReadOnlyCheck() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = context.getSharedPreferences("local_library_v1", Context.MODE_PRIVATE)
        assertTrue("CANONICAL_JSON_KEY_MISSING", prefs.contains("library"))
        val original = prefs.getString("library", null)
        val snapshot = CanonicalLibraryJsonDecoder.decode(original, true)
        assertTrue("Expected a real catalog", snapshot.media.isNotEmpty())
        val shaBefore = sha256(original!!)
        val room = LibraryStore(context).previewVerifiedRoomReadWithoutRepairForQa()
        assertEquals(VerifiedRoomReadSelector.Path.ROOM, room.path)
        assertEquals(snapshot, room.state)
        assertTrue("Read-only check must not remove JSON", prefs.contains("library"))
        assertEquals(shaBefore, sha256(prefs.getString("library", null)!!))
    }

    private fun sha256(raw: String): String = MessageDigest.getInstance("SHA-256")
        .digest(raw.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it.toInt() and 0xff) }
}
