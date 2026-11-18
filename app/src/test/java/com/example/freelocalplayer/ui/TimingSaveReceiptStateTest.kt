package com.example.freelocalplayer.ui

import com.example.freelocalplayer.lyrics.EnhancedLrcWordAdjuster
import com.example.freelocalplayer.lyrics.LrcTimingAdjuster
import com.example.freelocalplayer.lyrics.LyricLineSync
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class TimingSaveReceiptStateTest {
    @Test fun noConfirmationBeforeCommit() {
        val edit = TimingSaveReceiptState("[00:01.00]First").start()
        assertTrue(edit.saving)
        assertFalse(edit.failed)
        assertEquals(TimingSaveReceiptState.Phase.WRITING, edit.phase)
    }

    @Test fun failurePreservesSourceAndAllowsRetry() {
        val source = "[00:01.00]First\n[00:04.00]Second"
        val savedCandidate = LrcTimingAdjuster.retime(source, 0, 1200L, 8000L)
        assertEquals("[00:01.20]First\n[00:04.00]Second", savedCandidate)
        val failed = TimingSaveReceiptState(source).start().complete(false)
        assertEquals(source, failed.sourceLyrics)
        assertTrue(failed.failed)
        assertFalse(failed.saving)
        assertEquals(savedCandidate,
            LrcTimingAdjuster.retime(failed.start().sourceLyrics, 0, 1200L, 8000L))
    }

    @Test fun enhancedWordFailureMaintainsWordIndexAndSource() {
        val source = "[00:02.00]<00:02.00>We <00:02.40>sing <00:02.90>now"
        val expected = "[00:02.00]<00:02.00>We <00:02.50>sing <00:02.90>now"
        val edit = TimingSaveReceiptState(source).start().complete(false).start()
        assertEquals(expected,
            EnhancedLrcWordAdjuster.retimeWord(edit.sourceLyrics, 0, 1, 2500L, 6000L))
        assertTrue(edit.saving)
    }

    @Test fun txtMarksAreUnchangedUntilSavedReceipt() {
        val source = "First\nSecond"
        val marks = listOf(1100L, 3400L)
        val lrc = LyricLineSync.toLrc(LyricLineSync.lines(source), marks)
        assertEquals("[00:01.10]First\n[00:03.40]Second", lrc)
        val pending = TimingSaveReceiptState(source).start()
        assertTrue(pending.saving)
        val saved = pending.complete(true)
        assertEquals(TimingSaveReceiptState.Phase.SAVED, saved.phase)
        assertEquals(source, saved.sourceLyrics)
    }

    @Test fun duplicateSaveAndUnexpectedReceiptAreRejected() {
        val waiting = TimingSaveReceiptState("[00:01.00]Track").start()
        assertThrows(IllegalStateException::class.java) { waiting.start() }
        assertThrows(IllegalStateException::class.java) {
            TimingSaveReceiptState("[00:01.00]Track").complete(true)
        }
        assertThrows(IllegalStateException::class.java) { waiting.complete(true).start() }
    }
}
