package com.example.freelocalplayer.music

/** Saved separately from automatic recognition; future re-analysis does not erase corrections. */
data class ChordCorrection(val source: String, val timeMs: Long, val name: String)

object ChordCorrections {
    private val sources = setOf("auto", "manual", "midi")
    private val chordPattern = Regex(
        "^[A-G](?:[#b♯♭])?(?:(?:m|m7|maj7|7|dim|aug|sus2|sus4|add9|m6|6|9))?(?:/[A-G][#b♯♭]?)?$"
    )

    fun isValidName(value: String): Boolean = value == "—" || chordPattern.matches(value)

    fun sourceId(sourceLabel: String): String = when (sourceLabel) {
        "Авто" -> "auto"
        "Ручные" -> "manual"
        "MIDI" -> "midi"
        else -> "auto"
    }

    /** Keep all source data immutable; substitute only names at matching timestamps. */
    fun apply(cues: List<ChordCue>, edits: List<ChordCorrection>, source: String): List<ChordCue> {
        if (edits.isEmpty() || cues.isEmpty()) return cues
        val replacements = edits.asSequence().filter { it.source == source }
            .associateBy { it.timeMs }
        return cues.map { cue ->
            val corrected = replacements[cue.timeMs]
            if (corrected == null) cue else cue.copy(name = corrected.name, source = "user-correction")
        }
    }

    fun upsert(current: List<ChordCorrection>, next: ChordCorrection): List<ChordCorrection> {
        require(next.source in sources && next.timeMs >= 0 && isValidName(next.name))
        return (current.filterNot { it.source == next.source && it.timeMs == next.timeMs } + next)
            .sortedWith(compareBy<ChordCorrection> { it.source }.thenBy { it.timeMs })
            .take(3000)
    }

    fun remove(current: List<ChordCorrection>, source: String, timeMs: Long): List<ChordCorrection> =
        current.filterNot { it.source == source && it.timeMs == timeMs }

    fun decode(text: String): List<ChordCorrection> {
        if (text.length > 160_000) return emptyList()
        val result = mutableListOf<ChordCorrection>()
        for (line in text.lineSequence().take(3000)) {
            val fields = line.split('\t')
            if (fields.size != 3) continue
            val time = fields[1].toLongOrNull() ?: continue
            val edit = ChordCorrection(fields[0], time, fields[2])
            if (edit.source in sources && time >= 0 && isValidName(edit.name)) result += edit
        }
        return result.distinctBy { it.source to it.timeMs }
    }

    fun encode(edits: List<ChordCorrection>): String = edits.take(3000).joinToString("\n") {
        require(it.source in sources && it.timeMs >= 0 && isValidName(it.name))
        "${it.source}\t${it.timeMs}\t${it.name}"
    }
}
