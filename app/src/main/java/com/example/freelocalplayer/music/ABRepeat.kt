package com.example.freelocalplayer.music

/** A-B repeat is enabled only with an ordered pair of valid positions. */
object ABRepeat {
    const val MIN_LENGTH_MS = 250L
    fun shouldRepeat(startMs: Long?, endMs: Long?, positionMs: Long, playing: Boolean): Boolean =
        playing && startMs != null && endMs != null && startMs >= 0L &&
            endMs >= startMs + MIN_LENGTH_MS && positionMs >= endMs
}
