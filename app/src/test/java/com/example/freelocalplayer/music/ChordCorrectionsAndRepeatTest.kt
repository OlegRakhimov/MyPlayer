package com.example.freelocalplayer.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChordCorrectionsAndRepeatTest {
    @Test fun correctionsAreScopedAndSurviveASecondAnalysis() {
        val firstAnalysis = listOf(
            ChordCue(0L, "C", "audio-estimate"),
            ChordCue(500L, "Am", "audio-estimate")
        )
        val edit = ChordCorrection("auto", 500L, "G7")
        val stored = ChordCorrections.decode(ChordCorrections.encode(ChordCorrections.upsert(emptyList(), edit)))
        assertEquals("G7", ChordCorrections.apply(firstAnalysis, stored, "auto")[1].name)
        assertEquals("Am", firstAnalysis[1].name)
        val secondAnalysis = listOf(
            ChordCue(0L, "F", "audio-estimate"),
            ChordCue(500L, "Dm", "audio-estimate")
        )
        assertEquals("G7", ChordCorrections.apply(secondAnalysis, stored, "auto")[1].name)
        assertEquals("Dm", ChordCorrections.apply(secondAnalysis, stored, "midi")[1].name)
        assertTrue(ChordCorrections.remove(stored, "auto", 500L).isEmpty())
    }

    @Test fun rejectsBadChordStrings() {
        assertTrue(ChordCorrections.isValidName("Am"))
        assertTrue(ChordCorrections.isValidName("G7"))
        assertFalse(ChordCorrections.isValidName("DROP TABLE"))
    }

    @Test fun repeatRequiresTwoPointsAndPlayback() {
        assertFalse(ABRepeat.shouldRepeat(null, 2000L, 2500L, true))
        assertFalse(ABRepeat.shouldRepeat(1000L, null, 2500L, true))
        assertFalse(ABRepeat.shouldRepeat(1000L, 1200L, 2500L, true))
        assertFalse(ABRepeat.shouldRepeat(1000L, 3000L, 3000L, false))
        assertFalse(ABRepeat.shouldRepeat(1000L, 3000L, 2999L, true))
        assertTrue(ABRepeat.shouldRepeat(1000L, 3000L, 3000L, true))
    }
}
