package com.example.freelocalplayer.music

import com.example.freelocalplayer.lyrics.TimedLine

/** Chords tied to a lyric line, with their original position on the audio timeline. */
data class ChordOnLyricLine(val audioTimeMs: Long, val name: String)
data class LyricLineChords(val lyric: TimedLine, val chords: List<ChordOnLyricLine>)
data class InlineLyricToken(val text: String, val chord: String?)

/** Pure formatting and alignment logic: it never invents chords or lyric timestamps. */
object LyricChordTimeline {
    // Strictly recognize chord symbols. Do not mistake LRC timestamps for chords.
    private val chordSymbol = Regex(
        "^[A-G](?:[#b♯♭])?(?:(?:m|maj|min|dim|aug|sus|add|M)?[0-9+#b♯♭()a-zA-Z-]{0,12})?(?:/[A-G][#b♯♭]?)?$"
    )
    private val candidate = Regex("\\[([^\\]\\r\\n]{1,24})]")
    private val word = Regex("\\S+\\s*")

    fun bind(
        lines: List<TimedLine>,
        cues: List<ChordCue>,
        offsetMs: Long,
        trackDurationMs: Long
    ): List<LyricLineChords> {
        if (lines.isEmpty()) return emptyList()
        val ordered = cues.sortedBy { it.timeMs }
        return lines.mapIndexed { index, line ->
            val nextStart = lines.getOrNull(index + 1)?.timeMs
                ?: if (trackDurationMs > line.timeMs) trackDurationMs else line.timeMs + 8000L
            val result = ArrayList<ChordOnLyricLine>()
            val chordIndex = ChordTimeline.current(ordered, line.timeMs - offsetMs)
            if (chordIndex >= 0) {
                val carrying = ordered[chordIndex]
                result += ChordOnLyricLine(line.timeMs, carrying.name)
            }
            // Changes within the lyric line are preserved, but repeated short-window
            // measurements of the same harmony are shown only once.
            val from = line.timeMs - offsetMs
            val until = nextStart - offsetMs
            for (cue in ordered) {
                if (cue.timeMs <= from) continue
                if (cue.timeMs >= until) break
                if (result.lastOrNull()?.name != cue.name) {
                    result += ChordOnLyricLine(cue.timeMs + offsetMs, cue.name)
                }
            }
            LyricLineChords(line, result)
        }
    }

    /** Parse manual [Am]lyrics [G]annotations without mistaking [00:12] for chords. */
    fun parseInline(raw: String): List<InlineLyricToken> {
        val output = ArrayList<InlineLyricToken>()
        var activeChord: String? = null
        var cursor = 0
        fun consume(text: String) {
            for (match in word.findAll(text)) {
                output += InlineLyricToken(match.value, activeChord)
                activeChord = null
            }
        }
        for (match in candidate.findAll(raw)) {
            val symbol = match.groupValues[1]
            if (!chordSymbol.matches(symbol)) continue
            consume(raw.substring(cursor, match.range.first))
            activeChord = symbol
            cursor = match.range.last + 1
        }
        consume(raw.substring(cursor))
        return if (output.isEmpty() && raw.isNotBlank()) listOf(InlineLyricToken(raw, null)) else output
    }

    fun containsInlineChords(raw: String): Boolean = candidate.findAll(raw).any {
        chordSymbol.matches(it.groupValues[1])
    }

    /** The chord physically playing at a timestamp, accounting for user calibration. */
    fun chordAt(cues: List<ChordCue>, audioTimeMs: Long, offsetMs: Long): String? {
        val index = ChordTimeline.current(cues, audioTimeMs - offsetMs)
        return cues.getOrNull(index)?.name
    }
}
