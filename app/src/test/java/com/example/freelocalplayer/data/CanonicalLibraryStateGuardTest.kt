package com.example.freelocalplayer.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CanonicalLibraryStateGuardTest {
    private val entry = MediaEntry(
        id = "stable-1", uri = "content://media/external/audio/media/1",
        title = "Track", isVideo = false, lyrics = "[00:01.00]text"
    )

    private fun rejected(expected: String, state: LibraryState) {
        val error = try {
            CanonicalLibraryStateGuard.requireValid(state)
            null
        } catch (bad: IllegalArgumentException) { bad }
        assertTrue("Expected rejection: $expected", error != null)
        assertEquals(expected, error?.message)
    }

    @Test fun validEmptyAndLegacyUnresolvedPlaylistRefsRemainAllowed() {
        CanonicalLibraryStateGuard.requireValid(LibraryState())
        CanonicalLibraryStateGuard.requireValid(LibraryState(
            media = listOf(entry),
            playlists = listOf(UserPlaylist("p1", "Old", listOf("missing-old-uuid", entry.id)))
        ))
    }

    @Test fun validRetiredRecordsAndTheirPositionsRemainUnchanged() {
        val removed = RetiredMedia(entry.copy(id = "retired-2"), listOf(PlaylistPlace("p1", 8)))
        val state = LibraryState(listOf(entry), listOf(UserPlaylist("p1", "One")), listOf(removed))
        CanonicalLibraryStateGuard.requireValid(state)
        assertEquals(8, state.retiredMedia.single().playlistPlaces.single().index)
    }

    @Test fun duplicateLiveIdIsRejected() = rejected(
        "CANONICAL_DUPLICATE_TRACK_ID", LibraryState(media = listOf(entry, entry))
    )

    @Test fun duplicateLiveAndRetiredIdIsRejected() = rejected(
        "CANONICAL_DUPLICATE_LIVE_RETIRED_ID",
        LibraryState(media = listOf(entry), retiredMedia = listOf(RetiredMedia(entry)))
    )

    @Test fun blankLiveIdIsRejected() = rejected(
        "CANONICAL_EMPTY_TRACK_ID", LibraryState(media = listOf(entry.copy(id = " ")))
    )

    @Test fun blankUriIsRejected() = rejected(
        "CANONICAL_EMPTY_TRACK_URI", LibraryState(media = listOf(entry.copy(uri = "")))
    )

    @Test fun duplicatePlaylistIdIsRejected() = rejected(
        "CANONICAL_DUPLICATE_PLAYLIST_ID", LibraryState(playlists = listOf(
            UserPlaylist("same", "a"), UserPlaylist("same", "b")
        ))
    )

    @Test fun blankPlaylistTrackIdIsRejected() = rejected(
        "CANONICAL_BLANK_PLAYLIST_TRACK_ID", LibraryState(playlists = listOf(
            UserPlaylist("p", "a", listOf(""))
        ))
    )

    @Test fun negativeRetiredPositionIsRejected() = rejected(
        "CANONICAL_NEGATIVE_RETIRED_PLAYLIST_INDEX",
        LibraryState(retiredMedia = listOf(RetiredMedia(entry, listOf(PlaylistPlace("p", -1)))))
    )

    @Test fun originalStateIsNotMutatedByGuard() {
        val state = LibraryState(listOf(entry), listOf(UserPlaylist("p", "One", listOf(entry.id))))
        val copy = state.copy()
        CanonicalLibraryStateGuard.requireValid(state)
        assertEquals(copy, state)
    }
}
