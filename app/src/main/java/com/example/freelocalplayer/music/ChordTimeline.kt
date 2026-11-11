package com.example.freelocalplayer.music

import kotlin.math.max
import kotlin.math.min

data class ChordCue(val timeMs: Long, val name: String, val source: String)

/** An explicit chord chart format: [00:12.50] Am7, one chord per line. */
object ChordTimeline {
    private val cue = Regex("^\\[(\\d{1,3}):(\\d{2})(?:[.,](\\d{1,3}))?]\\s*([^\\[\\]]{1,48})\\s*$")
    fun parse(text: String): List<ChordCue> {
        if (text.length > 100_000) return emptyList()
        val result = ArrayList<ChordCue>()
        for (line in text.lineSequence()) {
            val match = cue.matchEntire(line.trim()) ?: continue
            val minutes = match.groupValues[1].toLongOrNull() ?: continue
            val seconds = match.groupValues[2].toLongOrNull() ?: continue
            if (seconds !in 0..59) continue
            val fractional = match.groupValues[3].padEnd(3, '0').take(3).toLongOrNull() ?: 0
            val whenMs = minutes * 60_000 + seconds * 1000 + fractional
            val name = match.groupValues[4].trim()
            if (name.isNotBlank() && result.size < 3000) result += ChordCue(whenMs, name, "manual")
        }
        return result.distinctBy { it.timeMs }.sortedBy { it.timeMs }
    }

    fun current(cues: List<ChordCue>, positionMs: Long): Int {
        if (cues.isEmpty() || positionMs < cues[0].timeMs) return -1
        val index = cues.binarySearch { it.timeMs.compareTo(positionMs) }
        return if (index >= 0) index else max(0, -index - 2)
    }

    /** Chords inferred from symbolic MIDI pitch classes. Not recognition from an MP3. */
    fun approximateFromMidi(score: MusicScore): List<ChordCue> {
        val notes = score.parts.filter { it.family != PartFamily.DRUMS }.flatMap { it.notes }
        if (notes.isEmpty()) return emptyList()
        val span = max(250L, min(1000L, 60_000L / score.beatsPerMinute.coerceAtLeast(1)))
        val duration = min(score.durationMs, 7_200_000L)
        val result = mutableListOf<ChordCue>()
        val chronological = notes.sortedBy { it.startMs }
        val ongoing = mutableListOf<ScoreNote>()
        var nextNote = 0
        var at = 0L
        var previous = ""
        while (at <= duration && result.size < 2000) {
            val scores = DoubleArray(12)
            val end = min(duration + 1, at + span)
            while (nextNote < chronological.size && chronological[nextNote].startMs < end) {
                ongoing.add(chronological[nextNote++])
            }
            ongoing.removeAll { it.endMs <= at }
            for (note in ongoing) {
                val overlap = (min(note.endMs, end) - max(note.startMs, at)).coerceAtLeast(0)
                if (overlap > 0) scores[note.pitch.mod(12)] += overlap.toDouble() / (end - at).coerceAtLeast(1)
            }
            val chord = infer(scores)
            if (chord != previous) {
                if (chord != null) result += ChordCue(at, chord, "midi-estimate")
                previous = chord.orEmpty()
            }
            at += span
        }
        return result
    }

    private fun infer(weights: DoubleArray): String? {
        val present = weights.indices.filter { weights[it] >= 0.1 }
        if (present.size < 3) return null
        val roots = arrayOf("C", "C♯", "D", "E♭", "E", "F", "F♯", "G", "A♭", "A", "B♭", "B")
        val qualities = listOf(
            "" to setOf(0, 4, 7), "m" to setOf(0, 3, 7),
            "dim" to setOf(0, 3, 6), "aug" to setOf(0, 4, 8),
            "sus2" to setOf(0, 2, 7), "sus4" to setOf(0, 5, 7),
            "7" to setOf(0, 4, 7, 10), "m7" to setOf(0, 3, 7, 10),
            "maj7" to setOf(0, 4, 7, 11)
        )
        var best: String? = null
        var bestValue = 0.0
        for (root in 0..11) for ((suffix, pattern) in qualities) {
            val included = pattern.sumOf { weights[(root + it) % 12] }
            val excluded = weights.sum() - included
            val seen = pattern.count { weights[(root + it) % 12] > 0.1 }
            if (seen < 3) continue
            val quality = included - excluded * 1.2 + if (weights[root] > 0.1) 0.3 else 0.0
            if (quality > bestValue) { bestValue = quality; best = roots[root] + suffix }
        }
        return best
    }
}
