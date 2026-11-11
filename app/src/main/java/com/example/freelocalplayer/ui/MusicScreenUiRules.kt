package com.example.freelocalplayer.ui

/** Pure UI transport rules; never modify the Media3 playback queue or library state. */
internal fun musicSeekTarget(positionMs: Long, deltaMs: Long, durationMs: Long): Long {
    val position = positionMs.coerceAtLeast(0L)
    val target = if (deltaMs < 0L) {
        val magnitude = if (deltaMs == Long.MIN_VALUE) Long.MAX_VALUE else -deltaMs
        position - magnitude.coerceAtMost(position)
    } else {
        if (deltaMs > Long.MAX_VALUE - position) Long.MAX_VALUE else position + deltaMs
    }
    return if (durationMs > 0L) target.coerceAtMost(durationMs) else target
}

/** Keep two lines of context before the sung line, unless manual reading is enabled. */
internal fun lyricScrollTarget(activeIndex: Int, count: Int, autoFollow: Boolean): Int? {
    if (!autoFollow || activeIndex < 0 || activeIndex >= count) return null
    return (activeIndex - 2).coerceAtLeast(0)
}
