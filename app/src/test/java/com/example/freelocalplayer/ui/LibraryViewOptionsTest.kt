package com.example.freelocalplayer.ui

import com.example.freelocalplayer.data.MediaEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryViewOptionsTest {
    private val first = MediaEntry(id = "1", uri = "content://one", title = "zeta.mp3", isVideo = false)
    private val second = MediaEntry(id = "2", uri = "content://two", title = "Animal House.mp3", isVideo = false, lyrics = "[00:10.00]Text")
    private val third = MediaEntry(id = "3", uri = "content://three", title = "alpha.mp3", isVideo = false)
    private val source = listOf(first, second, third)

    @Test fun defaultOrderIsNeverChanged() {
        assertEquals(listOf("1", "2", "3"), selectLibraryMedia(source, "", MediaSortOption.ADDED).map { it.id })
        assertEquals(listOf("1", "2", "3"), source.map { it.id })
    }

    @Test fun titleSearchIgnoresCaseAndOuterWhitespace() {
        assertEquals(listOf("2"), selectLibraryMedia(source, "  anIMAl  ", MediaSortOption.ADDED).map { it.id })
        assertTrue(selectLibraryMedia(source, "nothing", MediaSortOption.ADDED).isEmpty())
    }

    @Test fun alphabeticalSortingIsStableAndDoesNotModifySource() {
        assertEquals(listOf("3", "2", "1"), selectLibraryMedia(source, "", MediaSortOption.TITLE_AZ).map { it.id })
        assertEquals(listOf("1", "2", "3"), selectLibraryMedia(source, "", MediaSortOption.TITLE_ZA).map { it.id })
        assertEquals(listOf("1", "2", "3"), source.map { it.id })
    }

    @Test fun lyricsFilterIsAppliedBeforeSorting() {
        assertEquals(listOf("2"), selectLibraryMedia(source, "", MediaSortOption.TITLE_AZ, LyricsViewFilter.WITH_TEXT).map { it.id })
        assertEquals(listOf("3", "1"), selectLibraryMedia(source, "", MediaSortOption.TITLE_AZ, LyricsViewFilter.WITHOUT_TEXT).map { it.id })
    }

    @Test fun invalidSavedOptionsFallbackSafely() {
        assertEquals(MediaSortOption.ADDED, MediaSortOption.fromStored("MISSING"))
        assertEquals(MediaSortOption.ADDED, MediaSortOption.fromStored(null))
        assertEquals(LyricsViewFilter.ALL, LyricsViewFilter.fromStored("MISSING"))
    }
}
