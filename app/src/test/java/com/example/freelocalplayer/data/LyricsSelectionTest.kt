package com.example.freelocalplayer.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LyricsSelectionTest {
    private val first = MediaEntry(id = "first", uri = "content://song/1", title = "One", isVideo = false, lyrics = "ONE")
    private val second = MediaEntry(id = "second", uri = "content://song/2", title = "Two", isVideo = false, lyrics = "TWO")

    @Test fun playerAlwaysUsesCurrentSongsOwnLyrics() {
        assertEquals("TWO", selectLyricsEntry(listOf(first, second), "second", "first")?.lyrics)
        assertEquals("ONE", selectLyricsEntry(listOf(first, second), "first", "second")?.lyrics)
    }

    @Test fun stoppedPlayerMayUseSelection() {
        assertEquals("TWO", selectLyricsEntry(listOf(first, second), null, "second")?.lyrics)
        assertEquals("ONE", selectLyricsEntry(listOf(first, second), null, null)?.lyrics)
        assertNull(selectLyricsEntry(emptyList(), null, null))
    }
}
