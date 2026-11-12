package com.example.freelocalplayer.ui

import com.example.freelocalplayer.music.ABRepeat

/** Per-track in-memory practice segment. No files or media queues are altered. */
internal data class PracticeLoopState(
    val startMs: Long? = null,
    val endMs: Long? = null,
    val enabled: Boolean = false
)

internal enum class PracticeLoopBoundary { START, END }

/** Pure, testable rules for safe A–B repeat controls. */
internal object PracticeLoopRules {
    fun isReady(loop: PracticeLoopState): Boolean {
        val a = loop.startMs ?: return false
        val b = loop.endMs ?: return false
        return a >= 0L && b >= a && b - a >= ABRepeat.MIN_LENGTH_MS
    }

    fun canMarkStart(positionMs: Long, durationMs: Long): Boolean =
        positionMs >= 0L && (durationMs <= 0L || positionMs <= durationMs)

    fun markStart(loop: PracticeLoopState, positionMs: Long, durationMs: Long): PracticeLoopState =
        if (canMarkStart(positionMs, durationMs)) {
            // Setting A always clears the old B to avoid an invisible stale loop.
            PracticeLoopState(startMs = positionMs)
        } else loop

    fun canMarkEnd(loop: PracticeLoopState, positionMs: Long, durationMs: Long): Boolean {
        val a = loop.startMs ?: return false
        return a >= 0L && positionMs >= a &&
            positionMs - a >= ABRepeat.MIN_LENGTH_MS &&
            (durationMs <= 0L || positionMs <= durationMs)
    }

    fun markEnd(loop: PracticeLoopState, positionMs: Long, durationMs: Long): PracticeLoopState =
        if (canMarkEnd(loop, positionMs, durationMs)) {
            loop.copy(endMs = positionMs, enabled = true)
        } else loop

    fun setEnabled(loop: PracticeLoopState, enabled: Boolean): PracticeLoopState =
        if (enabled && !isReady(loop)) loop else loop.copy(enabled = enabled)

    fun shift(
        loop: PracticeLoopState,
        boundary: PracticeLoopBoundary,
        deltaMs: Long,
        durationMs: Long
    ): PracticeLoopState {
        if (!isReady(loop)) return loop
        val a = loop.startMs ?: return loop
        val b = loop.endMs ?: return loop
        val original = when (boundary) {
            PracticeLoopBoundary.START -> a
            PracticeLoopBoundary.END -> b
        }
        // Reject arithmetic overflow and all out-of-range positions.
        if (deltaMs > 0L && original > Long.MAX_VALUE - deltaMs) return loop
        if (deltaMs < 0L && original < Long.MIN_VALUE - deltaMs) return loop
        val newValue = original + deltaMs
        if (newValue < 0L || (durationMs > 0L && newValue > durationMs)) return loop
        val proposed = when (boundary) {
            PracticeLoopBoundary.START -> loop.copy(startMs = newValue)
            PracticeLoopBoundary.END -> loop.copy(endMs = newValue)
        }
        return proposed.takeIf(::isReady) ?: loop
    }

    fun shouldSeek(
        loop: PracticeLoopState,
        positionMs: Long,
        playing: Boolean,
        isCurrentTrack: Boolean
    ): Boolean = isCurrentTrack && loop.enabled &&
        ABRepeat.shouldRepeat(loop.startMs, loop.endMs, positionMs, playing)
}
