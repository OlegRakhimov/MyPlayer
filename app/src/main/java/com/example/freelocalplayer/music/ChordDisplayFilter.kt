package com.example.freelocalplayer.music

/**
 * A reversible, display-only stabilizer for the 500-ms automatic timeline.
 * It NEVER rewrites the stored analysis, changes manual/MIDI charts, or
 * fabricates a new chord name. Any chord shown was present in an observation.
 *
 * In stable mode, weak evidence for a chord shorter than 750 ms cannot be
 * distinguished from a musical transient by the stored text-only timeline.
 * Therefore such isolated flashes may be merged into an adjacent sustained
 * chord. Raw/fast mode exposes every original 500-ms observation.
 */
object ChordDisplayFilter {
    const val MIN_STABLE_MS: Long = 750L

    fun apply(cues: List<ChordCue>, stable: Boolean): List<ChordCue> {
        if (!stable || cues.size < 3) return cues
        // Source contains one observation per 500 ms. Preserve every timestamp:
        // user corrections are keyed by this timestamp and lyrics use it too.
        if (cues.zipWithNext().any { (first, next) -> next.timeMs <= first.timeMs }) return cues
        val names = cues.map { it.name }.toMutableList()
        repeat(2) {
            val runs = ArrayList<Run>()
            var index = 0
            while (index < names.size) {
                var stop = index + 1
                while (stop < names.size && names[stop] == names[index]) stop++
                runs += Run(index, stop, names[index])
                index = stop
            }
            if (runs.size < 3) return@repeat
            val changes = ArrayList<Pair<Run, String>>()
            for (runIndex in 1 until runs.lastIndex) {
                val current = runs[runIndex]
                val left = runs[runIndex - 1]
                val right = runs[runIndex + 1]
                // An unknown interval might be real silence. Never fill it in.
                if (current.name == AudioChroma.UNKNOWN_CHORD ||
                    left.name == AudioChroma.UNKNOWN_CHORD ||
                    right.name == AudioChroma.UNKNOWN_CHORD) continue
                val runDuration = duration(cues, current)
                if (runDuration >= MIN_STABLE_MS) continue
                val replacement = when {
                    left.name == right.name -> left.name
                    duration(cues, left) >= MIN_STABLE_MS && duration(cues, right) < MIN_STABLE_MS -> left.name
                    duration(cues, right) >= MIN_STABLE_MS && duration(cues, left) < MIN_STABLE_MS -> right.name
                    duration(cues, left) >= MIN_STABLE_MS && duration(cues, right) >= MIN_STABLE_MS ->
                        if (duration(cues, left) >= duration(cues, right)) left.name else right.name
                    else -> null // leave a genuinely fast passage intact
                }
                if (replacement != null) changes += current to replacement
            }
            for ((run, newName) in changes) for (i in run.from until run.toExclusive) names[i] = newName
        }
        return cues.mapIndexed { index, cue ->
            if (cue.name == names[index]) cue else cue.copy(name = names[index], source = "audio-stabilized")
        }
    }

    /** Only actual changes in chord labels are shown in the scrollable overview. */
    fun changesOnly(cues: List<ChordCue>): List<ChordCue> = cues.filterIndexed { index, cue ->
        index == 0 || cue.name != cues[index - 1].name
    }

    private data class Run(val from: Int, val toExclusive: Int, val name: String)

    private fun duration(cues: List<ChordCue>, run: Run): Long {
        val start = cues[run.from].timeMs
        val end = if (run.toExclusive < cues.size) cues[run.toExclusive].timeMs
            else start + ((run.toExclusive - run.from) * AudioChroma.SEGMENT_MS)
        return (end - start).coerceAtLeast(0L)
    }
}
