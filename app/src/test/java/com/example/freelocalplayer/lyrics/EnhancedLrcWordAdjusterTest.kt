package com.example.freelocalplayer.lyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EnhancedLrcWordAdjusterTest {
    @Test fun updatesOneWordWithoutChangingItsNeighbors() {
        val source = "[00:02.00]<00:02.00>My <00:02.40>life <00:02.90>now"
        assertEquals("[00:02.00]<00:02.00>My <00:02.50>life <00:02.90>now",
            EnhancedLrcWordAdjuster.retimeWord(source, 0, 1, 2500, 5000))
    }

    @Test fun preservesCrLfMetadataAndCommas() {
        val source = "[ar:Artist]\r\n[ti:Title]\r\n[00:01,000]<00:01,000>One <00:01,200>two\r\n"
        assertEquals("[ar:Artist]\r\n[ti:Title]\r\n[00:01,000]<00:01,000>One <00:01,300>two\r\n",
            EnhancedLrcWordAdjuster.retimeWord(source, 0, 1, 1300L))
    }

    @Test fun respectsBothAdjacentWordTimesAndLineStart() {
        val source = "[00:02.00]<00:02.00>One <00:02.40>two <00:02.90>three"
        assertNull(EnhancedLrcWordAdjuster.retimeWord(source, 0, 1, 1999))
        assertNull(EnhancedLrcWordAdjuster.retimeWord(source, 0, 1, 3000))
        assertEquals(source, EnhancedLrcWordAdjuster.retimeWord(source, 0, 1, 2400))
    }

    @Test fun cannotCrossNextLyricLineOrKnownTrackEnd() {
        val source = "[00:01.00]<00:01.00>First\n[00:02.00]<00:02.00>Next"
        assertNull(EnhancedLrcWordAdjuster.retimeWord(source, 0, 0, 2001))
        assertNull(EnhancedLrcWordAdjuster.retimeWord(source, 0, 0, 1500, 1400))
    }

    @Test fun refusesMultiTagRowsWithSharedWords() {
        val source = "[00:01.00][00:03.00]<00:01.00>Repeat"
        assertTrue(EnhancedLrcWordAdjuster.words(source, 0).isEmpty())
        assertNull(EnhancedLrcWordAdjuster.retimeWord(source, 1, 0, 3100))
    }

    @Test fun rejectsInvalidInputAndOutOfOrderExistingTimes() {
        val source = "[00:02.00]<00:02.40>One <00:02.10>two"
        assertNull(EnhancedLrcWordAdjuster.retimeWord(source, 0, 0, 2300))
        assertNull(EnhancedLrcWordAdjuster.retimeWord(source, 0, 9, 2300))
        assertNull(EnhancedLrcWordAdjuster.retimeWord(source, -1, 0, 2300))
        assertNull(EnhancedLrcWordAdjuster.retimeWord(source, 0, 0, -1))
    }

    @Test fun indexReflectsActualWordTagEvenWithEmptyTagText() {
        val source = "[00:01.00]<00:01.00><00:01.10>Hi <00:01.20>you"
        val words = EnhancedLrcWordAdjuster.words(source, 0)
        assertEquals(listOf(1, 2), words.map { it.tagOrdinal })
        assertEquals("[00:01.00]<00:01.00><00:01.15>Hi <00:01.20>you",
            EnhancedLrcWordAdjuster.retimeWord(source, 0, 0, 1150))
    }

    @Test fun cannotCrossInvisibleEmptyTag() {
        val source = "[00:01.00]<00:01.00>First <00:01.20><00:01.30>last"
        assertNull(EnhancedLrcWordAdjuster.retimeWord(source, 0, 1, 1199))
    }

    @Test fun doesNotChangeUneditedWordsOrStandardLrc() {
        val source = "[00:01.00]Ordinary\n[00:02.00]<00:02.00>Enhanced"
        assertTrue(EnhancedLrcWordAdjuster.words(source, 0).isEmpty())
        assertNull(EnhancedLrcWordAdjuster.retimeWord(source, 0, 0, 1100))
        assertEquals(source, EnhancedLrcWordAdjuster.retimeWord(source, 1, 0, 2000))
    }

    @Test fun preservesMillisecondsForNonRoundValue() {
        val source = "[00:01.00]<00:01.200>One"
        assertEquals("[00:01.00]<00:01.257>One",
            EnhancedLrcWordAdjuster.retimeWord(source, 0, 0, 1257))
    }

    @Test fun recognizesOnlyRealExistingTaggedWords() {
        val source = "[00:01.00]Before <00:01.50>after"
        val words = EnhancedLrcWordAdjuster.words(source, 0)
        assertEquals(1, words.size)
        assertEquals("after", words.first().text)
        assertEquals(1500L, words.first().timeMs)
        assertFalse(words.any { it.text == "Before" })
    }
}
