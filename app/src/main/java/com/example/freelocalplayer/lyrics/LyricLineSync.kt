package com.example.freelocalplayer.lyrics

/** Pure, device-independent logic for manually timestamping plain TXT lyric lines. */
object LyricLineSync {
    fun lines(lyrics: String): List<String> = lyrics.lines()
        .map(String::trimEnd)
        .filter(String::isNotBlank)

    fun canMark(marks: List<Long>, timeMs: Long, totalLines: Int): Boolean =
        marks.size < totalLines && timeMs >= 0L &&
            (marks.lastOrNull()?.let { timeMs >= it } ?: true)

    fun mark(marks: List<Long>, timeMs: Long, totalLines: Int): List<Long> {
        require(canMark(marks, timeMs, totalLines)) { "Invalid timestamp or all lines marked" }
        return marks + timeMs
    }

    fun undo(marks: List<Long>): List<Long> = if (marks.isEmpty()) marks else marks.dropLast(1)

    fun adjustLast(marks: List<Long>, deltaMs: Long, durationMs: Long): List<Long> {
        if (marks.isEmpty()) return marks
        val minimum = if (marks.size > 1) marks[marks.lastIndex - 1] else 0L
        val maximum = if (durationMs > 0) durationMs.coerceAtLeast(minimum) else Long.MAX_VALUE
        val updated = (marks.last() + deltaMs).coerceIn(minimum, maximum)
        return marks.dropLast(1) + updated
    }

    fun ready(lines: List<String>, marks: List<Long>): Boolean =
        lines.isNotEmpty() && lines.size == marks.size && marks.all { it >= 0L } &&
            marks.zipWithNext().all { (a, b) -> b >= a }

    fun toLrc(lines: List<String>, marks: List<Long>): String {
        require(ready(lines, marks)) { "Every line must have a valid timestamp" }
        return lines.indices.joinToString("\n") { index ->
            formatTime(marks[index]) + lines[index]
        }
    }

    fun formatTime(timeMs: Long): String {
        val safe = timeMs.coerceAtLeast(0L)
        val totalSeconds = safe / 1000L
        val centiseconds = (safe % 1000L) / 10L
        return "[%02d:%02d.%02d]".format(
            java.util.Locale.ROOT, totalSeconds / 60L, totalSeconds % 60L, centiseconds
        )
    }
}
