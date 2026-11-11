package com.example.freelocalplayer.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MusicScreenUiRulesTest {
    @Test fun backwardSeekNeverBecomesNegative() {
        assertEquals(0L, musicSeekTarget(2_000L, -10_000L, 200_000L))
    }

    @Test fun forwardSeekStopsAtTrackDuration() {
        assertEquals(30_000L, musicSeekTarget(27_000L, 10_000L, 30_000L))
    }

    @Test fun seekingWithUnknownDurationUsesRequestedDelta() {
        assertEquals(11_000L, musicSeekTarget(1_000L, 10_000L, 0L))
    }

    @Test fun unknownDurationCannotOverflow() {
        assertEquals(Long.MAX_VALUE, musicSeekTarget(Long.MAX_VALUE - 2L, 10_000L, 0L))
    }

    @Test fun firstLinesStayVisibleWithContext() {
        assertEquals(0, lyricScrollTarget(0, 6, true))
        assertEquals(0, lyricScrollTarget(2, 6, true))
        assertEquals(2, lyricScrollTarget(4, 6, true))
    }

    @Test fun manuallyPausedAutoFollowNeverScrolls() {
        assertNull(lyricScrollTarget(4, 6, false))
    }

    @Test fun invalidOrEmptyTimelinesDoNotTryToScroll() {
        assertNull(lyricScrollTarget(-1, 0, true))
        assertNull(lyricScrollTarget(6, 6, true))
    }
}
