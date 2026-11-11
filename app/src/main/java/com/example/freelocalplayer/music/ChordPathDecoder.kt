package com.example.freelocalplayer.music

import kotlin.math.max

/**
 * Deterministic, offline HMM/Viterbi for 12 major + 12 minor triads and no-chord.
 *
 * Inputs are 12-dimensional chroma observations accumulated from audio; they are
 * not ground-truth chords. Comparing an absolute template score to a fixed
 * no-chord threshold caused a pathological all-unknown result on dense music.
 * Here each candidate is measured relative to the *other* candidates in the
 * same window, while flat spectra and silent windows still favour no-chord.
 *
 * This is an approximate recognizer, not a transcription model or CQT.
 */
internal object ChordPathDecoder {
    private val roots = arrayOf(
        "C", "C♯", "D", "E♭", "E", "F", "F♯", "G", "A♭", "A", "B♭", "B"
    )
    private const val NO_CHORD = 24
    private const val STATE_COUNT = 25
    private const val SWITCH_COST = 0.075
    private const val NO_CHORD_SWITCH_COST = 0.065
    private const val NO_CHORD_SCORE = 0.125
    private const val EVIDENCE_SCALE = 3.8

    data class Window(val atMs: Long, val chroma: DoubleArray?, val validFrames: Int)

    fun decode(windows: List<Window>, limit: Int = 3000): List<ChordCue> {
        if (windows.isEmpty()) return emptyList()
        val frames = windows.take(limit)
        val backtrack = Array(frames.size) { IntArray(STATE_COUNT) }
        var previous = DoubleArray(STATE_COUNT)
        for (t in frames.indices) {
            val observation = emissions(frames[t])
            val current = DoubleArray(STATE_COUNT) { Double.NEGATIVE_INFINITY }
            if (t == 0) {
                for (state in 0 until STATE_COUNT) current[state] = observation[state]
            } else {
                for (state in 0 until STATE_COUNT) {
                    var bestPrior = 0
                    var bestPath = Double.NEGATIVE_INFINITY
                    for (prior in 0 until STATE_COUNT) {
                        val penalty = when {
                            prior == state -> 0.0
                            prior == NO_CHORD || state == NO_CHORD -> NO_CHORD_SWITCH_COST
                            else -> SWITCH_COST
                        }
                        val candidate = previous[prior] - penalty
                        if (candidate > bestPath) {
                            bestPath = candidate
                            bestPrior = prior
                        }
                    }
                    current[state] = bestPath + observation[state]
                    backtrack[t][state] = bestPrior
                }
            }
            val maxValue = current.maxOrNull() ?: 0.0
            for (state in current.indices) current[state] -= maxValue
            previous = current
        }

        var chosenState = previous.indices.maxByOrNull { previous[it] } ?: NO_CHORD
        val sequence = IntArray(frames.size)
        for (t in frames.indices.reversed()) {
            sequence[t] = chosenState
            if (t != 0) chosenState = backtrack[t][chosenState]
        }
        if (sequence.all { it == NO_CHORD }) return emptyList()

        return List(frames.size) { index ->
            val state = sequence[index]
            val label = if (state == NO_CHORD) AudioChroma.UNKNOWN_CHORD
                else roots[state % 12] + if (state >= 12) "m" else ""
            ChordCue(
                frames[index].atMs,
                label,
                if (state == NO_CHORD) "audio-unresolved" else "audio-estimate"
            )
        }
    }

    /** Counters contain no media titles, paths, or raw music. */
    fun evidenceSummary(windows: List<Window>): String {
        var usable = 0
        var detectable = 0
        var maxContrast = 0.0
        var totalContrast = 0.0
        for (window in windows) {
            val scores = scoreWindow(window) ?: continue
            usable++
            val contrast = scores.maxOrNull() ?: 0.0
            if (contrast * EVIDENCE_SCALE > NO_CHORD_SCORE) detectable++
            totalContrast += contrast
            if (contrast > maxContrast) maxContrast = contrast
        }
        val mean = if (usable == 0) 0.0 else totalContrast / usable
        return "usableWindows=$usable evidenceWindows=$detectable " +
            "meanContrast=${"%.4f".format(java.util.Locale.US, mean)} " +
            "maxContrast=${"%.4f".format(java.util.Locale.US, maxContrast)}"
    }

    private fun emissions(window: Window): DoubleArray {
        val scores = scoreWindow(window)
        if (scores == null) {
            return DoubleArray(STATE_COUNT) { state -> if (state == NO_CHORD) 1.0 else -1.0 }
        }
        val result = DoubleArray(STATE_COUNT)
        for (chord in 0 until NO_CHORD) result[chord] = scores[chord] * EVIDENCE_SCALE
        result[NO_CHORD] = NO_CHORD_SCORE
        return result
    }

    /**
     * A score of zero means "no better than an average chord template".
     * This removes the accidental bias toward no-chord for spectrally busy music.
     */
    private fun scoreWindow(window: Window): DoubleArray? {
        val chroma = window.chroma
        if (chroma == null || chroma.size != 12 || window.validFrames <= 0) return null
        if (chroma.any { !it.isFinite() }) return null
        val sum = chroma.sumOf { max(0.0, it) }
        if (!sum.isFinite() || sum <= 1e-8) return null
        val normalized = DoubleArray(12) { pitch -> max(0.0, chroma[pitch]) / sum }

        // An almost uniform chromagram is not evidence of any particular chord.
        // The threshold is intentionally small: dense arrangements are allowed.
        val spread = normalized.sumOf { (it - 1.0 / 12.0) * (it - 1.0 / 12.0) }
        if (spread < 0.0006) return null

        val templates = DoubleArray(NO_CHORD)
        for (minor in 0..1) {
            for (root in 0..11) {
                val third = (root + if (minor == 1) 3 else 4) % 12
                val fifth = (root + 7) % 12
                // Third is crucial for major/minor, root remains slightly favoured.
                templates[minor * 12 + root] =
                    1.12 * normalized[root] + 1.12 * normalized[third] + 1.05 * normalized[fifth]
            }
        }
        val average = templates.average()
        return DoubleArray(NO_CHORD) { index -> templates[index] - average }
    }
}
