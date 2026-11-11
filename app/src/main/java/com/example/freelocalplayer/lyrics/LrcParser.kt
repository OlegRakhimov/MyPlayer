package com.example.freelocalplayer.lyrics

/** Time-synchronized lyric line; inline word tags are optional (Enhanced LRC). */
data class TimedLine(val timeMs: Long, val text: String, val words: List<TimedWord> = emptyList())
data class TimedWord(val timeMs: Long, val text: String)

object LrcParser {
    private val lineTag = Regex("\\[(\\d{1,3}):(\\d{2})(?:[.,](\\d{1,3}))?\\]")
    private val inlineTag = Regex("<(\\d{1,3}):(\\d{2})(?:[.,](\\d{1,3}))?>")

    fun parse(input: String): List<TimedLine> {
        val output = mutableListOf<TimedLine>()
        input.lineSequence().forEach { raw ->
            val times = lineTag.findAll(raw).mapNotNull { match -> parseTime(match.groupValues) }.toList()
            if (times.isEmpty()) return@forEach
            val payload = lineTag.replace(raw, "")
            val plainText = inlineTag.replace(payload, "").trim()
            if (plainText.isEmpty()) return@forEach
            times.forEach { start ->
                output += TimedLine(start, plainText, parseWords(payload, start))
            }
        }
        return output.sortedBy(TimedLine::timeMs)
    }

    private fun parseWords(payload: String, defaultTimeMs: Long): List<TimedWord> {
        val matches = inlineTag.findAll(payload).toList()
        if (matches.isEmpty()) return emptyList()
        val words = ArrayList<TimedWord>()
        val prefix = payload.substring(0, matches.first().range.first)
        if (prefix.isNotBlank()) words += TimedWord(defaultTimeMs, prefix)
        matches.forEachIndexed { index, match ->
            val time = parseTime(match.groupValues) ?: return@forEachIndexed
            val from = match.range.last + 1
            val to = matches.getOrNull(index + 1)?.range?.first ?: payload.length
            val segment = payload.substring(from, to)
            if (segment.isNotBlank()) words += TimedWord(time, segment)
        }
        return words
    }

    private fun parseTime(groups: List<String>): Long? {
        val minutes = groups.getOrNull(1)?.toLongOrNull() ?: return null
        val seconds = groups.getOrNull(2)?.toLongOrNull() ?: return null
        if (seconds !in 0..59) return null
        val fraction = groups.getOrNull(3).orEmpty()
        val ms = when (fraction.length) {
            1 -> fraction.toLong() * 100
            2 -> fraction.toLong() * 10
            3 -> fraction.toLong()
            else -> 0L
        }
        return (minutes * 60 + seconds) * 1_000 + ms
    }

    fun currentLine(lines: List<TimedLine>, positionMs: Long): Int {
        if (lines.isEmpty()) return -1
        val index = lines.binarySearch { it.timeMs.compareTo(positionMs) }
        return if (index >= 0) index else -index - 2
    }

    fun currentWord(words: List<TimedWord>, positionMs: Long): Int {
        if (words.isEmpty()) return -1
        val index = words.binarySearch { it.timeMs.compareTo(positionMs) }
        return if (index >= 0) index else -index - 2
    }
}
