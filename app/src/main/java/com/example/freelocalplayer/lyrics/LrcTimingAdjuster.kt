package com.example.freelocalplayer.lyrics

import java.util.Locale

/** A displayed LRC cue mapped to its original source line and timestamp tag. */
data class EditableLrcCue(
    val sourceLineIndex: Int,
    val timestampOrdinal: Int,
    val timeMs: Long,
    val text: String,
    val hasWordTiming: Boolean,
    val hasMultipleLineTags: Boolean
)

/**
 * Non-destructive editor for existing LRC/Enhanced LRC timing.
 * Does not regenerate lyrics, reorder source lines or remove metadata.
 */
object LrcTimingAdjuster {
    private val lineTag = Regex("\\[(\\d{1,3}):(\\d{2})(?:([.,])(\\d{1,3}))?\\]")
    private val wordTag = Regex("<(\\d{1,3}):(\\d{2})(?:([.,])(\\d{1,3}))?>")
    private const val MAX_TIME_MS = 59_999_999L

    fun cues(input: String): List<EditableLrcCue> {
        val output = mutableListOf<EditableLrcCue>()
        input.split('\n').forEachIndexed { sourceLineIndex, source ->
            val timestamps = lineTag.findAll(source).toList()
            if (timestamps.isEmpty()) return@forEachIndexed
            val payload = lineTag.replace(source, "")
            val text = wordTag.replace(payload, "").trim()
            if (text.isBlank()) return@forEachIndexed
            timestamps.forEachIndexed tagLoop@{ ordinal, tag ->
                val timeMs = readTime(tag) ?: return@tagLoop
                output.add(EditableLrcCue(
                    sourceLineIndex = sourceLineIndex,
                    timestampOrdinal = ordinal,
                    timeMs = timeMs,
                    text = text,
                    hasWordTiming = wordTag.containsMatchIn(payload),
                    hasMultipleLineTags = timestamps.size > 1
                ))
            }
        }
        return output.sortedBy { it.timeMs }
    }

    /**
     * Returns null when the requested edit could break ordering, word timing or duration.
     * A repeated line with Enhanced LRC tags cannot be edited independently because
     * its word timestamps are shared across all repeat tags.
     */
    fun retime(input: String, displayIndex: Int, newTimeMs: Long, durationMs: Long = 0L): String? {
        val all = cues(input)
        val cue = all.getOrNull(displayIndex) ?: return null
        if (newTimeMs == cue.timeMs) return input
        if (newTimeMs !in 0L..MAX_TIME_MS) return null
        if (durationMs > 0 && newTimeMs > durationMs) return null
        if (displayIndex > 0 && newTimeMs < all[displayIndex - 1].timeMs) return null
        if (displayIndex + 1 < all.size && newTimeMs > all[displayIndex + 1].timeMs) return null
        if (cue.hasWordTiming && cue.hasMultipleLineTags) return null

        val sourceLines = input.split('\n').toMutableList()
        val original = sourceLines[cue.sourceLineIndex]
        val timeTags = lineTag.findAll(original).toList()
        val targetTag = timeTags.getOrNull(cue.timestampOrdinal) ?: return null
        if (readTime(targetTag) != cue.timeMs) return null
        val delta = newTimeMs - cue.timeMs
        val updates = mutableListOf<Pair<IntRange, String>>()
        updates.add(targetTag.range to formatTag(targetTag, newTimeMs, '[', ']'))

        if (cue.hasWordTiming) {
            for (tag in wordTag.findAll(original)) {
                val oldWordTime = readTime(tag) ?: return null
                val newWordTime = oldWordTime + delta
                if (newWordTime !in 0L..MAX_TIME_MS) return null
                if (durationMs > 0 && newWordTime > durationMs) return null
                updates.add(tag.range to formatTag(tag, newWordTime, '<', '>'))
            }
        }

        var result = original
        for ((range, newTag) in updates.sortedByDescending { it.first.first }) {
            result = result.replaceRange(range, newTag)
        }
        sourceLines[cue.sourceLineIndex] = result
        return sourceLines.joinToString("\n")
    }

    private fun readTime(tag: MatchResult): Long? {
        val minutes = tag.groupValues[1].toLongOrNull() ?: return null
        val seconds = tag.groupValues[2].toLongOrNull() ?: return null
        if (seconds !in 0L..59L || minutes !in 0L..999L) return null
        val digits = tag.groupValues[4]
        val ms = when (digits.length) {
            0 -> 0L
            1 -> digits.toLong() * 100
            2 -> digits.toLong() * 10
            3 -> digits.toLong()
            else -> return null
        }
        return (minutes * 60 + seconds) * 1000 + ms
    }

    private fun formatTag(
        original: MatchResult,
        timeMs: Long,
        opening: Char,
        closing: Char
    ): String {
        val minutes = timeMs / 60_000
        val seconds = timeMs / 1000 % 60
        val millisecond = (timeMs % 1000).toInt()
        val existingPrecision = original.groupValues[4].length
        val requiredPrecision = when {
            millisecond == 0 -> 0
            millisecond % 100 == 0 -> 1
            millisecond % 10 == 0 -> 2
            else -> 3
        }
        val precision = maxOf(existingPrecision, requiredPrecision)
        val separator = original.groupValues[3].ifEmpty { "." }
        val fraction = if (precision == 0) "" else {
            separator + String.format(Locale.ROOT, "%03d", millisecond).take(precision)
        }
        val minuteWidth = maxOf(2, original.groupValues[1].length)
        val mm = minutes.toString().padStart(minuteWidth, '0')
        val ss = seconds.toString().padStart(2, '0')
        return "$opening$mm:$ss$fraction$closing"
    }
}
