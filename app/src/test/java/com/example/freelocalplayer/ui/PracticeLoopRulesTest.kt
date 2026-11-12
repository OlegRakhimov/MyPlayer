package com.example.freelocalplayer.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PracticeLoopRulesTest {
    @Test fun emptyLoopIsInactive() {
        val loop = PracticeLoopState()
        assertFalse(PracticeLoopRules.isReady(loop))
        assertFalse(PracticeLoopRules.shouldSeek(loop, 7000L, true, true))
    }

    @Test fun startRequiresValidPositionAndResetsOldEnd() {
        val existing = PracticeLoopState(1000L, 4000L, enabled = true)
        assertEquals(PracticeLoopState(2000L), PracticeLoopRules.markStart(existing, 2000L, 10000L))
        assertEquals(existing, PracticeLoopRules.markStart(existing, -1L, 10000L))
        assertEquals(existing, PracticeLoopRules.markStart(existing, 11000L, 10000L))
    }

    @Test fun endMustBeAtLeastQuarterSecondAfterStart() {
        val loop = PracticeLoopState(startMs = 1000L)
        assertFalse(PracticeLoopRules.canMarkEnd(loop, 1249L, 5000L))
        assertTrue(PracticeLoopRules.canMarkEnd(loop, 1250L, 5000L))
        assertFalse(PracticeLoopRules.canMarkEnd(loop, 5500L, 5000L))
        assertFalse(PracticeLoopRules.canMarkEnd(PracticeLoopState(), 5000L, 5000L))
        assertEquals(loop, PracticeLoopRules.markEnd(loop, 1249L, 5000L))
    }

    @Test fun markingEndEnablesLoopImmediately() {
        val loop = PracticeLoopRules.markEnd(PracticeLoopState(3000L), 9000L, 12000L)
        assertEquals(PracticeLoopState(3000L, 9000L, enabled = true), loop)
        assertTrue(PracticeLoopRules.isReady(loop))
    }

    @Test fun toggleOnlyChangesLoopNotPlayback() {
        val loop = PracticeLoopState(3000L, 9000L, enabled = true)
        assertEquals(loop.copy(enabled = false), PracticeLoopRules.setEnabled(loop, false))
        assertEquals(loop, PracticeLoopRules.setEnabled(loop.copy(enabled = false), true))
        assertEquals(PracticeLoopState(3000L), PracticeLoopRules.setEnabled(PracticeLoopState(3000L), true))
    }

    @Test fun seekGuardRequiresSameMediaItem() {
        val loop = PracticeLoopState(1000L, 3000L, enabled = true)
        assertTrue(PracticeLoopRules.shouldSeek(loop, 3000L, true, true))
        assertFalse(PracticeLoopRules.shouldSeek(loop, 3000L, false, true))
        assertFalse(PracticeLoopRules.shouldSeek(loop, 3000L, true, false))
        assertFalse(PracticeLoopRules.shouldSeek(loop, 2999L, true, true))
    }

    @Test fun disabledLoopNeverSeeks() {
        val loop = PracticeLoopState(1000L, 3000L, enabled = false)
        assertFalse(PracticeLoopRules.shouldSeek(loop, 4000L, true, true))
    }

    @Test fun preciseShiftsRemainInsideBounds() {
        val loop = PracticeLoopState(2000L, 4000L, enabled = true)
        assertEquals(PracticeLoopState(2100L, 4000L, true), PracticeLoopRules.shift(loop, PracticeLoopBoundary.START, 100L, 6000L))
        assertEquals(PracticeLoopState(2000L, 3900L, true), PracticeLoopRules.shift(loop, PracticeLoopBoundary.END, -100L, 6000L))
        assertEquals(loop, PracticeLoopRules.shift(loop, PracticeLoopBoundary.START, -3000L, 6000L))
        assertEquals(loop, PracticeLoopRules.shift(loop, PracticeLoopBoundary.END, 3000L, 6000L))
    }

    @Test fun preciseShiftsMustPreserveMinDistance() {
        val loop = PracticeLoopState(1000L, 1250L, enabled = true)
        assertEquals(loop, PracticeLoopRules.shift(loop, PracticeLoopBoundary.START, 100L, 9000L))
        assertEquals(loop, PracticeLoopRules.shift(loop, PracticeLoopBoundary.END, -100L, 9000L))
        assertEquals(PracticeLoopState(900L, 1250L, true), PracticeLoopRules.shift(loop, PracticeLoopBoundary.START, -100L, 9000L))
    }

    @Test fun extremeOffsetsCannotOverflow() {
        val loop = PracticeLoopState(1000L, 9000L, enabled = true)
        assertEquals(loop, PracticeLoopRules.shift(loop, PracticeLoopBoundary.START, Long.MAX_VALUE, 0L))
        assertEquals(loop, PracticeLoopRules.shift(loop, PracticeLoopBoundary.END, Long.MIN_VALUE, 0L))
    }

    @Test fun unknownDurationAllowsValidTimes() {
        val start = PracticeLoopRules.markStart(PracticeLoopState(), 3000L, 0L)
        val complete = PracticeLoopRules.markEnd(start, 8000L, 0L)
        assertTrue(PracticeLoopRules.isReady(complete))
        assertTrue(PracticeLoopRules.shouldSeek(complete, 8000L, true, true))
    }
}
