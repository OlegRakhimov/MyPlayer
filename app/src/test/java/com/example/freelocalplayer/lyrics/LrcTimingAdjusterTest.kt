package com.example.freelocalplayer.lyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LrcTimingAdjusterTest {
    @Test fun keepsMetadataAndCrLfUnchanged() {
        val source = "[ar:Artist]\r\n[ti:Title]\r\n[00:02.00]Hello\r\n[00:08.00]World\r\n"
        val edited = LrcTimingAdjuster.retime(source, 0, 2100L, 12_000L)
        assertEquals("[ar:Artist]\r\n[ti:Title]\r\n[00:02.10]Hello\r\n[00:08.00]World\r\n", edited)
    }

    @Test fun changesOnlyOneTagWhenLineRepeats() {
        val source = "[00:05.1][00:10.50]Repeat"
        assertEquals("[00:05.2][00:10.50]Repeat", LrcTimingAdjuster.retime(source, 0, 5200))
        assertEquals("[00:05.1][00:10.60]Repeat", LrcTimingAdjuster.retime(source, 1, 10600))
    }

    @Test fun shiftsEveryEnhancedWordTagByExactDelta() {
        val source = "[00:02.00]<00:02.00>My <00:02.35>life <00:02.70>rules"
        val edited = LrcTimingAdjuster.retime(source, 0, 2100L, 10_000L)
        assertEquals("[00:02.10]<00:02.10>My <00:02.45>life <00:02.80>rules", edited)
        val words = LrcParser.parse(edited!!).single().words
        assertEquals(listOf(2100L, 2450L, 2800L), words.map { it.timeMs })
    }

    @Test fun refusesSharedWordTimesOnMultiCueLine() {
        val source = "[00:02.00][00:04.00]<00:02.00>One"
        assertNull(LrcTimingAdjuster.retime(source, 0, 2100))
        assertNull(LrcTimingAdjuster.retime(source, 1, 4100))
    }

    @Test fun preventsCrossingPreviousOrNextCue() {
        val source = "[00:02.00]First\n[00:04.00]Second\n[00:06.00]Third"
        assertNull(LrcTimingAdjuster.retime(source, 1, 1999))
        assertNull(LrcTimingAdjuster.retime(source, 1, 6001))
        assertNotNull(LrcTimingAdjuster.retime(source, 1, 2000))
        assertNotNull(LrcTimingAdjuster.retime(source, 1, 6000))
    }

    @Test fun blocksNegativeWordTimeAndDurationOverflow() {
        val source = "[00:02.00]<00:01.00>Start <00:02.50>End"
        assertNull(LrcTimingAdjuster.retime(source, 0, 900))
        assertNull(LrcTimingAdjuster.retime(source, 0, 2600, 3000))
    }

    @Test fun ignoresInvalidLrcSecondsLikeParser() {
        val source = "[00:70.00]Invalid\n[00:01.00]Valid"
        val cues = LrcTimingAdjuster.cues(source)
        assertEquals(1, cues.size)
        assertEquals("Valid", cues.single().text)
        assertEquals("[00:70.00]Invalid\n[00:01.10]Valid", LrcTimingAdjuster.retime(source, 0, 1100))
    }

    @Test fun preservesFineMillisecondPrecisionAndComma() {
        val source = "[00:02,123]<00:02,225>A"
        assertEquals("[00:02,223]<00:02,325>A", LrcTimingAdjuster.retime(source, 0, 2223))
    }

    @Test fun keepsOriginalBytesWhenNoTimeChange() {
        val source = "[ar:One]\r\n[00:01.500]abc\r\n"
        assertEquals(source, LrcTimingAdjuster.retime(source, 0, 1500))
    }

    @Test fun rejectsInvalidTargetAndNegativeTime() {
        val source = "[00:01.00]X"
        assertNull(LrcTimingAdjuster.retime(source, -1, 1000))
        assertNull(LrcTimingAdjuster.retime(source, 1, 1000))
        assertNull(LrcTimingAdjuster.retime(source, 0, -1))
        assertTrue(LrcTimingAdjuster.cues("[ar:Artist]").isEmpty())
    }
}
