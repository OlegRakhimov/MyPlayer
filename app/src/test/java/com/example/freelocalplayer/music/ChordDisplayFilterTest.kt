package com.example.freelocalplayer.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChordDisplayFilterTest {
    private fun samples(vararg names: String): List<ChordCue> = names.mapIndexed { i, name ->
        ChordCue(i * 500L, name, "audio-estimate")
    }

    @Test fun stableRemovesIsolatedFalseChangeWithoutTouchingOriginalSamples() {
        val raw = samples("C", "C", "C", "F", "C", "C", "C")
        val filtered = ChordDisplayFilter.apply(raw, stable = true)
        assertEquals(listOf("C"), ChordDisplayFilter.changesOnly(filtered).map { it.name })
        assertEquals("F", raw[3].name)
        assertEquals(raw.map { it.timeMs }, filtered.map { it.timeMs })
    }

    @Test fun stableRetainsRealOneSecondChordChanges() {
        val raw = samples("C", "C", "Am", "Am", "F", "F", "G", "G")
        val filtered = ChordDisplayFilter.apply(raw, stable = true)
        assertEquals(listOf("C", "Am", "F", "G"),
            ChordDisplayFilter.changesOnly(filtered).map { it.name })
        assertEquals(listOf(0L, 1000L, 2000L, 3000L),
            ChordDisplayFilter.changesOnly(filtered).map { it.timeMs })
    }

    @Test fun rawModePreservesHalfSecondPassages() {
        val raw = samples("C", "Am", "F", "G", "C")
        assertEquals(raw, ChordDisplayFilter.apply(raw, stable = false))
    }

    @Test fun unknownIntervalsAreNotTurnedIntoInventedChords() {
        val raw = samples("C", "C", "—", "C", "C")
        val filtered = ChordDisplayFilter.apply(raw, stable = true)
        assertEquals("—", filtered[2].name)
    }

    @Test fun manualCorrectionHasPriorityAfterStabilization() {
        val raw = samples("C", "C", "C", "F", "C", "C")
        val stabilized = ChordDisplayFilter.apply(raw, stable = true)
        val correction = ChordCorrection("auto", 1500L, "G7")
        val shown = ChordCorrections.apply(stabilized, listOf(correction), "auto")
        assertEquals("G7", shown[3].name)
        assertTrue(shown[3].source == "user-correction")
        assertEquals("F", raw[3].name)
    }

    @Test fun noResortingOfBadTimestampData() {
        val raw = listOf(ChordCue(1000L, "C", "audio-estimate"),
            ChordCue(500L, "F", "audio-estimate"),
            ChordCue(1500L, "C", "audio-estimate"))
        assertEquals(raw, ChordDisplayFilter.apply(raw, stable = true))
    }
}
