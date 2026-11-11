package com.example.freelocalplayer.lyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricLineSyncTest {
    @Test fun timestampsFollowThePlaybackAndExportCorrectLrc() {
        val lines = LyricLineSync.lines("First line\n\nSecond line\nThird line\n")
        var marks = emptyList<Long>()
        marks = LyricLineSync.mark(marks, 1200L, lines.size)
        marks = LyricLineSync.mark(marks, 2400L, lines.size)
        marks = LyricLineSync.mark(marks, 3880L, lines.size)
        assertTrue(LyricLineSync.ready(lines, marks))
        assertEquals("[00:01.20]First line\n[00:02.40]Second line\n[00:03.88]Third line", LyricLineSync.toLrc(lines, marks))
    }

    @Test fun markingCannotMoveBackwardsOrExceedTheLineCount() {
        assertFalse(LyricLineSync.canMark(listOf(3000L), 2000L, 3))
        assertFalse(LyricLineSync.canMark(listOf(1000L, 2000L), 2500L, 2))
        assertFalse(LyricLineSync.canMark(emptyList(), -1L, 2))
    }

    @Test fun undoAndTimingAdjustAreBounded() {
        val marks = listOf(900L, 2000L)
        assertEquals(listOf(900L, 1900L), LyricLineSync.adjustLast(marks, -100L, 5000L))
        assertEquals(listOf(900L, 900L), LyricLineSync.adjustLast(marks, -9000L, 5000L))
        assertEquals(listOf(900L, 5000L), LyricLineSync.adjustLast(marks, 9000L, 5000L))
        assertEquals(listOf(900L), LyricLineSync.undo(marks))
        assertEquals(emptyList<Long>(), LyricLineSync.undo(emptyList()))
    }

    @Test fun rejectsPartialAndNonMonotonicExport() {
        val lines = listOf("One", "Two")
        assertFalse(LyricLineSync.ready(lines, listOf(1000L)))
        assertFalse(LyricLineSync.ready(lines, listOf(2000L, 1000L)))
        assertTrue(LyricLineSync.ready(lines, listOf(1000L, 2000L)))
    }
}
