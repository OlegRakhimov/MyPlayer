package com.example.freelocalplayer.lyrics

import java.util.Locale

/** Real Enhanced LRC word tag, not an audio-derived prediction. */
data class EditableLrcWord(
    val tagOrdinal: Int,
    val timeMs: Long,
    val text: String
)

/**
 * Changes precisely one existing <mm:ss.xxx> word tag without altering lyrics,
 * other tags, separators, metadata or line ordering. No word timing is invented.
 */
object EnhancedLrcWordAdjuster {
    private val wordTag = Regex("<(\\d{1,3}):(\\d{2})(?:([.,])(\\d{1,3}))?>")
    private const val MAX_TIME_MS = 59_999_999L

    /** Index is the same sorted line index used by LrcParser and the v7 UI. */
    fun words(input: String, displayLineIndex: Int): List<EditableLrcWord> {
        val cue = LrcTimingAdjuster.cues(input).getOrNull(displayLineIndex) ?: return emptyList()
        // One physical line repeated at several times shares its word tags.
        if (!cue.hasWordTiming || cue.hasMultipleLineTags) return emptyList()
        val sourceLine = input.split('\n').getOrNull(cue.sourceLineIndex) ?: return emptyList()
        val matches = wordTag.findAll(sourceLine).toList()
        return matches.mapIndexedNotNull { ordinal, match ->
            val time = parseTime(match) ?: return@mapIndexedNotNull null
            val start = match.range.last + 1
            val end = matches.getOrNull(ordinal + 1)?.range?.first ?: sourceLine.length
            val text = sourceLine.substring(start, end).trim()
            if (text.isEmpty()) null else EditableLrcWord(ordinal, time, text)
        }
    }

    /** Returns null rather than creating an invalid word order or touching shared tags. */
    fun retimeWord(
        input: String,
        displayLineIndex: Int,
        wordIndex: Int,
        newTimeMs: Long,
        durationMs: Long = 0L
    ): String? {
        val cues = LrcTimingAdjuster.cues(input)
        val cue = cues.getOrNull(displayLineIndex) ?: return null
        if (!cue.hasWordTiming || cue.hasMultipleLineTags) return null
        val words = words(input, displayLineIndex)
        val word = words.getOrNull(wordIndex) ?: return null
        if (newTimeMs == word.timeMs) return input
        if (newTimeMs !in 0L..MAX_TIME_MS) return null
        if (durationMs > 0L && newTimeMs > durationMs) return null

        val sourceLines = input.split('\n').toMutableList()
        val original = sourceLines.getOrNull(cue.sourceLineIndex) ?: return null
        val tags = wordTag.findAll(original).toList()
        val actualTag = tags.getOrNull(word.tagOrdinal) ?: return null
        if (parseTime(actualTag) != word.timeMs) return null
        // Include tags with empty text: LrcParser skips them but ordering still matters.
        val allTimes = tags.map { parseTime(it) ?: return null }
        if (allTimes.zipWithNext().any { (a, b) -> a > b }) return null
        val lower = maxOf(cue.timeMs, allTimes.getOrNull(word.tagOrdinal - 1) ?: 0L)
        val upper = listOfNotNull(
            allTimes.getOrNull(word.tagOrdinal + 1),
            cues.getOrNull(displayLineIndex + 1)?.timeMs,
            durationMs.takeIf { it > 0L },
            MAX_TIME_MS
        ).minOrNull() ?: return null
        if (newTimeMs < lower || newTimeMs > upper) return null

        val replacement = formattedTimeTag(actualTag, newTimeMs)
        sourceLines[cue.sourceLineIndex] = original.replaceRange(actualTag.range, replacement)
        return sourceLines.joinToString("\n")
    }

    private fun parseTime(tag: MatchResult): Long? {
        val minutes = tag.groupValues[1].toLongOrNull() ?: return null
        val seconds = tag.groupValues[2].toLongOrNull() ?: return null
        if (seconds !in 0L..59L || minutes !in 0L..999L) return null
        val digits = tag.groupValues[4]
        val fractional = when (digits.length) {
            0 -> 0L
            1 -> digits.toLong() * 100L
            2 -> digits.toLong() * 10L
            3 -> digits.toLong()
            else -> return null
        }
        return (minutes * 60L + seconds) * 1000L + fractional
    }

    private fun formattedTimeTag(original: MatchResult, timeMs: Long): String {
        val minuteWidth = maxOf(2, original.groupValues[1].length)
        val minutes = (timeMs / 60_000L).toString().padStart(minuteWidth, '0')
        val seconds = ((timeMs / 1000L) % 60L).toString().padStart(2, '0')
        val millis = (timeMs % 1000L).toInt()
        val requiredPrecision = when {
            millis == 0 -> 0
            millis % 100 == 0 -> 1
            millis % 10 == 0 -> 2
            else -> 3
        }
        val precision = maxOf(original.groupValues[4].length, requiredPrecision)
        val fraction = if (precision == 0) "" else {
            original.groupValues[3].ifEmpty { "." } +
                String.format(Locale.ROOT, "%03d", millis).take(precision)
        }
        return "<$minutes:$seconds$fraction>"
    }
}
