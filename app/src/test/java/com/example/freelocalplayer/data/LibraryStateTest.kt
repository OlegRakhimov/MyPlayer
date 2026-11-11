package com.example.freelocalplayer.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryStateTest {
    private val first = MediaEntry(id = "a", uri = "content://one", title = "A", isVideo = false)
    private val second = MediaEntry(id = "b", uri = "content://two", title = "B", isVideo = true)

    @Test
    fun avoidsDuplicateImports() {
        val state = LibraryState().addMedia(listOf(first, second, first))
        assertEquals(2, state.media.size)
    }

    @Test
    fun savesPlaylistOrder() {
        var state = LibraryState(media = listOf(first, second)).createPlaylist("Songs")
        val id = state.playlists.single().id
        state = state.addToPlaylist(id, "a").addToPlaylist(id, "b")
        state = state.moveInPlaylist(id, "b", -1)
        assertEquals(listOf("b", "a"), state.playlists.single().mediaIds)
        assertEquals(listOf("b", "a"), state.tracksFor("playlist:$id").map { it.id })
    }

    @Test
    fun removingMediaRemovesReferencesWithoutTouchingOthers() {
        var state = LibraryState(media = listOf(first, second)).createPlaylist("Work")
        val id = state.playlists.single().id
        state = state.addToPlaylist(id, "a").addToPlaylist(id, "b").removeMedia("a")
        assertEquals(listOf("b"), state.playlists.single().mediaIds)
        assertEquals(listOf("b"), state.media.map { it.id })
    }

    @Test
    fun filtersAudioAndVideo() {
        val state = LibraryState(media = listOf(first, second))
        assertEquals(listOf(first), state.tracksFor("audio"))
        assertEquals(listOf(second), state.tracksFor("video"))
    }

    @Test
    fun doesNotImportSameAudioTwiceFromMediaDocumentsAndMediaStore() {
        val fromPicker = MediaEntry(
            id = "doc", uri = "content://com.android.providers.media.documents/document/audio%3A12345",
            title = "Song.mp3", isVideo = false, lyrics = "Saved lyrics"
        )
        val fromDeviceScan = MediaEntry(
            id = "scan", uri = "content://media/external/audio/media/12345",
            title = "Song.mp3", isVideo = false
        )
        val merged = LibraryState().addMedia(listOf(fromPicker)).addMedia(listOf(fromDeviceScan))
        assertEquals(1, merged.media.size)
        assertEquals("Saved lyrics", merged.media.single().lyrics)
    }

}
