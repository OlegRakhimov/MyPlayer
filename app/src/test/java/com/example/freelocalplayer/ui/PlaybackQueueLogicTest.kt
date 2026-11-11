package com.example.freelocalplayer.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlaybackQueueLogicTest {
    @Test fun picksOnlyValidIndices() {
        assertEquals(0, safeQueueIndex(0, 1))
        assertEquals(2, safeQueueIndex(2, 3))
        assertNull(safeQueueIndex(-1, 5))
        assertNull(safeQueueIndex(3, 3))
        assertNull(safeQueueIndex(0, 0))
    }

    @Test fun usesRealTitleBeforeFallback() {
        assertEquals("Song", queueTitle(" Song ", "media-1", 0))
        assertEquals("media-1", queueTitle(" ", "media-1", 0))
        assertEquals("Запись 3", queueTitle(null, "", 2))
    }
}
