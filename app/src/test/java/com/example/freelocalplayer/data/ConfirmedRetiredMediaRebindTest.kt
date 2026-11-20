package com.example.freelocalplayer.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** 0-B.3: no Android filesystem, MediaStore, Room or user data is touched. */
class ConfirmedRetiredMediaRebindTest {
    private fun old() = MediaEntry(
        id = "old", uri = "content://media/external/audio/media/4",
        title = "My track.mp3", isVideo = false,
        lyrics = "[00:01.00]My saved text",
        sourceKey = "mediastore:external:audio:4",
        sizeBytes = 4000, durationMs = 90000
    )

    private fun fresh() = MediaEntry(
        id = "new", uri = "content://media/external/audio/media/99",
        title = "Renamed.mp3", isVideo = false, lyrics = "",
        sourceKey = "mediastore:external:audio:99",
        sizeBytes = 4000, durationMs = 90000
    )

    private fun state(): LibraryState {
        val a = MediaEntry(id = "a", uri = "content://qa/a", title = "A", isVideo = false)
        val b = MediaEntry(id = "b", uri = "content://qa/b", title = "B", isVideo = false)
        val initial = LibraryState(
            media = listOf(a, old(), b, fresh()),
            playlists = listOf(
                UserPlaylist(id = "p1", name = "One", mediaIds = listOf("a", "old", "b")),
                UserPlaylist(id = "p2", name = "Two", mediaIds = listOf("old", "a"))
            )
        )
        return initial.removeMedia("old")
    }

    private fun refuse(
        state: LibraryState,
        expected: ConfirmedRetiredMediaRebind.Refusal,
        oldId: String = "old",
        newId: String = "new"
    ) {
        assertEquals(expected, ConfirmedRetiredMediaRebind.preview(state, oldId, newId))
        try {
            ConfirmedRetiredMediaRebind.confirmed(state, oldId, newId, true)
            fail("Expected refusal " + expected.name)
        } catch (error: IllegalArgumentException) {
            assertTrue(error.message.orEmpty().contains(expected.name))
        }
    }

    @Test fun restoresOldUuidAndSavedLyricsAndBothPlaylists() {
        val baseline = state()
        assertEquals(ConfirmedRetiredMediaRebind.Refusal.NONE,
            ConfirmedRetiredMediaRebind.preview(baseline, "old", "new"))
        val after = ConfirmedRetiredMediaRebind.confirmed(baseline, "old", "new", true)
        assertEquals(listOf("a", "b", "old"), after.media.map { it.id })
        assertEquals("content://media/external/audio/media/99",
            after.media.single { it.id == "old" }.uri)
        assertEquals(old().lyrics, after.media.single { it.id == "old" }.lyrics)
        assertEquals("Renamed.mp3", after.media.single { it.id == "old" }.title)
        assertEquals(listOf("a", "old", "b"), after.playlists[0].mediaIds)
        assertEquals(listOf("old", "a"), after.playlists[1].mediaIds)
        assertTrue(after.retiredMedia.isEmpty())
        assertFalse(after.media.any { it.id == "new" })
        assertEquals(1, baseline.retiredMedia.size)
        assertTrue(baseline.media.any { it.id == "new" })
        CanonicalLibraryStateGuard.requireValid(after)
    }

    @Test fun requiresExplicitConfirmation() {
        try {
            ConfirmedRetiredMediaRebind.confirmed(state(), "old", "new", false)
            fail("Must require confirmation")
        } catch (error: IllegalArgumentException) {
            assertEquals("RELINK_EXPLICIT_USER_CONFIRMATION_REQUIRED", error.message)
        }
    }

    @Test fun refusesMissingTombstone() =
        refuse(state(), ConfirmedRetiredMediaRebind.Refusal.OLD_NOT_RETIRED, oldId = "missing")

    @Test fun refusesLiveOldUuid() =
        refuse(state(), ConfirmedRetiredMediaRebind.Refusal.OLD_UUID_ALREADY_LIVE, oldId = "a")

    @Test fun refusesUnknownCandidate() =
        refuse(state(), ConfirmedRetiredMediaRebind.Refusal.NEW_NOT_LIVE, newId = "unknown")

    @Test fun refusesSameUuid() =
        refuse(state(), ConfirmedRetiredMediaRebind.Refusal.SAME_UUID, oldId = "old", newId = "old")

    @Test fun refusesDifferentFileSize() {
        val s = state().copy(media = state().media.map {
            if (it.id == "new") it.copy(sizeBytes = 4001) else it
        })
        refuse(s, ConfirmedRetiredMediaRebind.Refusal.SIZE_MISMATCH)
    }

    @Test fun refusesDifferentDuration() {
        val s = state().copy(media = state().media.map {
            if (it.id == "new") it.copy(durationMs = 90001) else it
        })
        refuse(s, ConfirmedRetiredMediaRebind.Refusal.DURATION_MISMATCH)
    }

    @Test fun refusesUnknownMetadata() {
        val s = state().copy(media = state().media.map {
            if (it.id == "new") it.copy(durationMs = -1) else it
        })
        refuse(s, ConfirmedRetiredMediaRebind.Refusal.EVIDENCE_UNAVAILABLE)
    }

    @Test fun refusesVideoAudioMismatch() {
        val s = state().copy(media = state().media.map {
            if (it.id == "new") it.copy(isVideo = true) else it
        })
        refuse(s, ConfirmedRetiredMediaRebind.Refusal.MEDIA_TYPE_MISMATCH)
    }

    @Test fun refusesCandidateLyrics() {
        val s = state().copy(media = state().media.map {
            if (it.id == "new") it.copy(lyrics = "My independent edits") else it
        })
        refuse(s, ConfirmedRetiredMediaRebind.Refusal.CANDIDATE_HAS_LYRICS)
    }

    @Test fun refusesCandidateInPlaylist() {
        val s = state().copy(playlists = state().playlists.map {
            if (it.id == "p1") it.copy(mediaIds = it.mediaIds + "new") else it
        })
        refuse(s, ConfirmedRetiredMediaRebind.Refusal.CANDIDATE_IN_PLAYLIST)
    }

    @Test fun refusesNonMediaStoreUri() {
        val s = state().copy(media = state().media.map {
            if (it.id == "new") it.copy(uri = "content://other.provider/99", sourceKey = "") else it
        })
        refuse(s, ConfirmedRetiredMediaRebind.Refusal.EVIDENCE_UNAVAILABLE)
    }

    @Test fun refusesForgedSourceKeyThatDisagreesWithMediaStoreUri() {
        val s = state().copy(media = state().media.map {
            if (it.id == "new") it.copy(sourceKey = "mediastore:external:audio:123456") else it
        })
        refuse(s, ConfirmedRetiredMediaRebind.Refusal.EVIDENCE_UNAVAILABLE)
    }

    @Test fun refusesUriClaimedByAnotherRetiredUuid() {
        val s = state().copy(retiredMedia = state().retiredMedia +
            RetiredMedia(fresh().copy(id = "other")))
        refuse(s, ConfirmedRetiredMediaRebind.Refusal.CANDIDATE_URI_COLLISION)
    }

    @Test fun refusesSameSourceKeyClaimedByAnotherLiveUuid() {
        val s = state().copy(media = state().media +
            fresh().copy(id = "different", uri = "content://another/99"))
        refuse(s, ConfirmedRetiredMediaRebind.Refusal.CANDIDATE_SOURCE_COLLISION)
    }

    @Test fun neverChangesUnrelatedPlaylistsOrExtraRetiredEntries() {
        val baseline = state()
        val unrelated = MediaEntry(id = "unrelated", uri = "content://other/74",
            title = "Missing", isVideo = false, lyrics = "Keep")
        val s = baseline.copy(
            retiredMedia = baseline.retiredMedia + RetiredMedia(unrelated),
            playlists = baseline.playlists + UserPlaylist("p3", "Third", listOf("a", "b"))
        )
        val result = ConfirmedRetiredMediaRebind.confirmed(s, "old", "new", true)
        assertEquals("Keep", result.retiredMedia.single().entry.lyrics)
        assertEquals(listOf("a", "b"), result.playlists[2].mediaIds)
    }
}
