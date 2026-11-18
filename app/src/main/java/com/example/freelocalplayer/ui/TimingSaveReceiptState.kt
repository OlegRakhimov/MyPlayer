package com.example.freelocalplayer.ui

/**
 * An immutable per-editor receipt for an in-flight timing edit.
 *
 * The baseline is captured when the dialog opens, not recomputed from the
 * optimistically changed Compose lyrics field. Failure therefore keeps the
 * original LRC coordinates and the proposed edit available for retry.
 * The containing MusicModeScreen uses key(entry.id) to isolate tracks.
 */
internal data class TimingSaveReceiptState(
    val sourceLyrics: String,
    val phase: Phase = Phase.READY
) {
    enum class Phase { READY, WRITING, FAILED, SAVED }

    val saving: Boolean get() = phase == Phase.WRITING
    val failed: Boolean get() = phase == Phase.FAILED

    fun start(): TimingSaveReceiptState {
        check(!saving) { "TIMING_SAVE_ALREADY_PENDING" }
        check(phase != Phase.SAVED) { "TIMING_SAVE_ALREADY_CONFIRMED" }
        return copy(phase = Phase.WRITING)
    }

    fun complete(confirmed: Boolean): TimingSaveReceiptState {
        check(saving) { "TIMING_SAVE_WITHOUT_PENDING_WRITE" }
        return copy(phase = if (confirmed) Phase.SAVED else Phase.FAILED)
    }
}
