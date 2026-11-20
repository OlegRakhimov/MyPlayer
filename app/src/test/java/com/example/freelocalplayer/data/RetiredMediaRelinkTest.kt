package com.example.freelocalplayer.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** Isolated JVM-only tests. Never touches an actual user's library or URI. */
class RetiredMediaRelinkTest {
    private fun original() = MediaEntry(
        id = "uuid-old", uri = "content://media/external/audio/media/42",
        title = "Earlier title.mp3", isVideo = false,
        lyrics = "[00:01.00]My own words",
        sourceKey = "mediastore:external:audio:42",
        sizeBytes = 3_000_000L, durationMs = 191_000L
    )

    private fun candidate(id: String = "scan-temporary") = MediaEntry(
        id = id, uri = "content://media/external/audio/media/700",
        title = "Renamed song.mp3", isVideo = false,
        sizeBytes = 3_000_000L, durationMs = 191_000L,
        sourceKey = "mediastore:external:audio:700"
    )

    private fun state(): LibraryState {
        val first = MediaEntry(id = "first", uri = "content://qa/first", title = "First", isVideo = false)
        val middle = original()
        val last = MediaEntry(id = "last", uri = "content://qa/last", title = "Last", isVideo = false)
        return LibraryState(
            media = listOf(first, middle, last),
            playlists = listOf(
                UserPlaylist("p1", "Playlist", listOf("first", "uuid-old", "last")),
                UserPlaylist("p2", "Another", listOf("uuid-old", "first"))
            )
        ).removeMedia("uuid-old")
    }

    private fun rejected(state: LibraryState, media: MediaEntry, expected: RetiredMediaRelink.Status) {
        assertEquals(expected, RetiredMediaRelink.preview(state, "uuid-old", media))
        try {
            RetiredMediaRelink.applyConfirmed(state, "uuid-old", media, true)
            fail("Relink must be rejected: $expected")
        } catch (error: IllegalArgumentException) {
            assertTrue(error.message.orEmpty().contains(expected.name))
        }
        assertTrue(state.retiredMedia.any { it.entry.id == "uuid-old" })
        assertFalse(state.media.any { it.id == "uuid-old" })
    }

    @Test fun newMediaStoreRowRestoresOldUuidLyricsAndBothPlaylistOrdersOnlyAfterConfirmation() {
        val state = state()
        val selected = candidate()
        assertEquals(RetiredMediaRelink.Status.READY,
            RetiredMediaRelink.preview(state, "uuid-old", selected))
        val after = RetiredMediaRelink.applyConfirmed(state, "uuid-old", selected, true)
        val track = after.media.single { it.id == "uuid-old" }
        assertEquals(selected.uri, track.uri)
        assertEquals(selected.title, track.title)
        assertEquals(selected.sourceKey, track.sourceKey)
        assertEquals(original().lyrics, track.lyrics)
        assertTrue(after.retiredMedia.isEmpty())
        assertEquals(listOf("first", "uuid-old", "last"), after.playlists[0].mediaIds)
        assertEquals(listOf("uuid-old", "first"), after.playlists[1].mediaIds)
        assertEquals(state.media.size + 1, after.media.size)
        CanonicalLibraryStateGuard.requireValid(after)
        // The original immutable snapshot is not modified or persisted here.
        assertEquals(1, state.retiredMedia.size)
    }

    @Test fun explicitConfirmationIsMandatoryEvenWithMatchingEvidence() {
        val state = state()
        try {
            RetiredMediaRelink.applyConfirmed(state, "uuid-old", candidate(), false)
            fail("Missing user confirmation must fail")
        } catch (error: IllegalArgumentException) {
            assertEquals("RELINK_REQUIRES_EXPLICIT_CONFIRMATION", error.message)
        }
    }

    @Test fun oldUuidMustBeRetiredNotLiveOrMissing() {
        val state = state()
        assertEquals(RetiredMediaRelink.Status.OLD_UUID_IS_LIVE,
            RetiredMediaRelink.preview(state, "first", candidate()))
        assertEquals(RetiredMediaRelink.Status.OLD_UUID_NOT_RETIRED,
            RetiredMediaRelink.preview(state, "missing", candidate()))
    }

    @Test fun matchingTitleAndSizeCannotOverrideWrongDuration() = rejected(
        state(), candidate().copy(durationMs = 191_001L),
        RetiredMediaRelink.Status.DURATION_MISMATCH)

    @Test fun differingSizeIsRejected() = rejected(
        state(), candidate().copy(sizeBytes = 3_000_001L),
        RetiredMediaRelink.Status.SIZE_MISMATCH)

    @Test fun unknownMetadataIsNotEnoughToRelink() = rejected(
        state(), candidate().copy(durationMs = -1L),
        RetiredMediaRelink.Status.EVIDENCE_UNAVAILABLE)

    @Test fun cannotAttachVideoToAudioUuid() = rejected(
        state(), candidate().copy(isVideo = true),
        RetiredMediaRelink.Status.MEDIA_TYPE_MISMATCH)

    @Test fun duplicateLiveUriIsRejectedWithoutMergingOtherUsersData() {
        val current = state()
        val occupied = current.copy(media = current.media +
            candidate(id = "someone-else").copy(lyrics = "Different user's edits"))
        rejected(occupied, candidate(), RetiredMediaRelink.Status.TARGET_URI_COLLISION)
    }

    @Test fun existingLiveUuidCannotBeSilentlyConsumed() = rejected(
        state(), candidate(id = "first"),
        RetiredMediaRelink.Status.CANDIDATE_ID_ALREADY_USED)

    @Test fun existingRetiredTargetUuidCannotBeSilentlyConsumed() {
        val s = state().copy(retiredMedia = state().retiredMedia +
            RetiredMedia(candidate(id = "old-unrelated")))
        rejected(s, candidate(id = "old-unrelated"),
            RetiredMediaRelink.Status.CANDIDATE_ID_ALREADY_USED)
    }

    @Test fun anotherRetiredRecordWithSameUriMakesSelectionAmbiguous() {
        val s = state().copy(retiredMedia = state().retiredMedia +
            RetiredMedia(candidate(id = "other-retired")))
        rejected(s, candidate(), RetiredMediaRelink.Status.TARGET_URI_COLLISION)
    }

    @Test fun anotherLiveRecordWithSameStrongKeyMakesSelectionAmbiguous() {
        val s = state().copy(media = state().media + candidate("other-live").copy(
            uri = "content://other.provider/new/700"))
        rejected(s, candidate(), RetiredMediaRelink.Status.TARGET_SOURCE_COLLISION)
    }

    @Test fun scannedCandidateMustBeNewAndHaveNoUserLyrics() = rejected(
        state(), candidate().copy(lyrics = "Already edited"),
        RetiredMediaRelink.Status.CANDIDATE_HAS_USER_LYRICS)

    @Test fun invalidCandidateMetadataCannotBeApplied() = rejected(
        state(), candidate().copy(uri = ""),
        RetiredMediaRelink.Status.INVALID_CANDIDATE)

    @Test fun neverRelinksAutomaticallyFromSameDisplayNameAlone() {
        val s = state()
        val vague = candidate().copy(uri = "content://random/same-name", title = original().title,
            sizeBytes = -1L, durationMs = -1L, sourceKey = "")
        rejected(s, vague, RetiredMediaRelink.Status.EVIDENCE_UNAVAILABLE)
    }
}
