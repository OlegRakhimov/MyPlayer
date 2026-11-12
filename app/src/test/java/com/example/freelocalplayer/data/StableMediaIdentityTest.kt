package com.example.freelocalplayer.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class StableMediaIdentityTest {
    private fun track(
        id: String = "original",
        row: Long = 42L,
        title: String = "Song.mp3",
        size: Long = 5_000L,
        duration: Long = 120_000L
    ) = MediaEntry(
        id = id,
        uri = "content://media/external/audio/media/$row",
        title = title, isVideo = false, lyrics = "[00:01.00]Saved lyrics",
        sizeBytes = size, durationMs = duration
    )

    @Test fun removeAndRescanPreservesUuidAndLyrics() {
        val initial = track()
        val deleted = LibraryState(media = listOf(initial)).removeMedia(initial.id)
        assertTrue(deleted.media.isEmpty())
        assertEquals("original", deleted.retiredMedia.single().entry.id)
        val restored = deleted.addMedia(listOf(track(id = "random-new-uuid", title = "Song.mp3")))
        assertEquals("original", restored.media.single().id)
        assertEquals(initial.lyrics, restored.media.single().lyrics)
        assertTrue(restored.retiredMedia.isEmpty())
    }

    @Test fun recoversPlaylistPositionsOnRescan() {
        val first = track("first", 1, "First.mp3")
        val middle = track("middle", 2, "Middle.mp3")
        val last = track("last", 3, "Last.mp3")
        var s = LibraryState(media = listOf(first, middle, last), playlists = listOf(
            UserPlaylist(id = "p1", name = "Playlist", mediaIds = listOf("first", "middle", "last")),
            UserPlaylist(id = "p2", name = "Another", mediaIds = listOf("middle", "first"))
        ))
        s = s.removeMedia("middle")
        assertEquals(listOf("first", "last"), s.playlists[0].mediaIds)
        assertEquals(listOf("first"), s.playlists[1].mediaIds)
        s = s.addMedia(listOf(track(id = "new", row = 2, title = "Middle.mp3")))
        assertEquals(listOf("first", "middle", "last"), s.playlists[0].mediaIds)
        assertEquals(listOf("middle", "first"), s.playlists[1].mediaIds)
    }

    @Test fun retainsOldUuidWhenSameMediaStoreRowScannedAgain() {
        val first = track()
        val state = LibraryState(media = listOf(first))
        val scanned = state.addMedia(listOf(track(id = "different")))
        assertEquals(1, scanned.media.size)
        assertEquals("original", scanned.media.single().id)
    }

    @Test fun enrichingLegacyRowDoesNotDestroyUserData() {
        val old = MediaEntry(id = "legacy", uri = "content://media/external/audio/media/42",
            title = "Song.mp3", isVideo = false, lyrics = "My saved text")
        val next = LibraryState(media = listOf(old)).addMedia(listOf(track(id = "temporary")))
        assertEquals(1, next.media.size)
        assertEquals("legacy", next.media[0].id)
        assertEquals("My saved text", next.media[0].lyrics)
        assertEquals(5000L, next.media[0].sizeBytes)
        assertEquals(120000L, next.media[0].durationMs)
    }

    @Test fun differentStoreIdsNeverMergeJustBecauseNamesMatch() {
        val old = track()
        val retired = LibraryState(media = listOf(old)).removeMedia(old.id)
        val reindexed = retired.addMedia(listOf(track(id = "new", row = 843)))
        assertEquals("new", reindexed.media.single().id)
        assertEquals("original", reindexed.retiredMedia.single().entry.id)
    }

    @Test fun mismatchedSizePreventsResurrectionOfRecycledMediaStoreRow() {
        val retired = LibraryState(media = listOf(track())).removeMedia("original")
        val newSong = retired.addMedia(listOf(track(id = "new", size = 7000L)))
        assertEquals("new", newSong.media.single().id)
        assertEquals("original", newSong.retiredMedia.single().entry.id)
    }

    @Test fun renameWithSameStrongFingerprintRestoresOldUuid() {
        val retired = LibraryState(media = listOf(track())).removeMedia("original")
        val restored = retired.addMedia(listOf(track(id = "new", title = "Renamed.mp3")))
        assertEquals("original", restored.media.single().id)
        assertEquals("Renamed.mp3", restored.media.single().title)
    }

    @Test fun neverUsesSizeAndDurationAloneToMergeTwoCopies() {
        val a = track(id = "a", row = 11)
        val b = track(id = "b", row = 12)
        val result = LibraryState(media = listOf(a)).addMedia(listOf(b))
        assertEquals(2, result.media.size)
    }

    @Test fun mediaDocumentsAliasDeduplicatesWithMatchingName() {
        val picked = MediaEntry(id = "doc", uri = "content://com.android.providers.media.documents/document/audio%3A42",
            title = "Song.mp3", isVideo = false, lyrics = "Real lyrics")
        val newScan = track(id = "scan")
        val result = LibraryState(media = listOf(picked)).addMedia(listOf(newScan))
        assertEquals(1, result.media.size)
        assertEquals("doc", result.media.single().id)
        assertEquals("Real lyrics", result.media.single().lyrics)
    }

    @Test fun noCrossVolumeStrongAlias() {
        val ext = track(id = "external")
        val other = ext.copy(id = "primary", uri = "content://media/external_primary/audio/media/42")
        val result = LibraryState(media = listOf(ext)).addMedia(listOf(other))
        // Legacy weak key cannot override an existing strong volume identity.
        assertEquals(2, result.media.size)
    }

    @Test fun unknownFileCannotRelinkByDisplayName() {
        val retired = LibraryState(media = listOf(track(id = "before"))).removeMedia("before")
        val newItem = MediaEntry(id = "after", uri = "content://another.provider/document/123",
            title = "Song.mp3", isVideo = false, sizeBytes = 5000, durationMs = 120000)
        val after = retired.addMedia(listOf(newItem))
        assertEquals("after", after.media.single().id)
        assertEquals("before", after.retiredMedia.single().entry.id)
    }

    @Test fun duplicateInputsDoNotDuplicateInDatabase() {
        val candidate = track(id = "a")
        val after = LibraryState().addMedia(listOf(candidate, candidate.copy(id = "b"), candidate.copy(id = "c")))
        assertEquals(1, after.media.size)
    }

    @Test fun doesNotDropRetiredEntriesOnUnrelatedImports() {
        val r = LibraryState(media = listOf(track())).removeMedia("original")
        val next = r.addMedia(listOf(track(id = "other", row = 89)))
        assertEquals(1, next.retiredMedia.size)
        assertEquals("original", next.retiredMedia[0].entry.id)
        assertFalse(next.media.any { it.id == "original" })
    }

    @Test fun emptyScanDoesNotWriteState() {
        val state = LibraryState(media = listOf(track()))
        assertSame(state, state.addMedia(emptyList()))
    }
}
