package com.example.freelocalplayer.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class PlaylistBulkAddTest {
    @Test
    fun addsValidEntriesInOrderWithoutDuplicates() {
        val a = MediaEntry(id = "a", uri = "uri:a", title = "A", isVideo = false)
        val b = MediaEntry(id = "b", uri = "uri:b", title = "B", isVideo = false)
        val c = MediaEntry(id = "c", uri = "uri:c", title = "C", isVideo = true)
        val state = LibraryState(media = listOf(a, b, c), playlists = listOf(
            UserPlaylist(id = "first", name = "Первый", mediaIds = listOf("b")),
            UserPlaylist(id = "other", name = "Другой")
        ))
        val result = state.addManyToPlaylist("first", listOf("c", "a", "c", "missing", "b"))
        assertEquals(listOf("b", "c", "a"), result.playlists.first().mediaIds)
        assertEquals(emptyList<String>(), result.playlists.last().mediaIds)
        assertEquals(3, result.media.size)
    }

    @Test
    fun ignoresUnknownPlaylistAndAlreadyAddedTracks() {
        val track = MediaEntry(id = "a", uri = "uri:a", title = "A", isVideo = false)
        val state = LibraryState(media = listOf(track), playlists = listOf(
            UserPlaylist(id = "list", name = "Тест", mediaIds = listOf("a"))
        ))
        assertSame(state, state.addManyToPlaylist("other", listOf("a")))
        assertSame(state, state.addManyToPlaylist("list", listOf("a", "a")))
        assertSame(state, state.addManyToPlaylist("list", emptyList()))
    }

    @Test
    fun singleTrackAdditionUsesSameValidation() {
        val track = MediaEntry(id = "a", uri = "uri:a", title = "A", isVideo = false)
        val state = LibraryState(media = listOf(track), playlists = listOf(
            UserPlaylist(id = "list", name = "Тест")
        ))
        val added = state.addToPlaylist("list", "a")
        assertEquals(listOf("a"), added.playlists.single().mediaIds)
        assertSame(added, added.addToPlaylist("list", "a"))
    }
}
