package com.example.freelocalplayer.data

/** Display the lyrics of the actually playing file before an older manual selection. */
fun selectLyricsEntry(media: List<MediaEntry>, playingId: String?, selectedId: String?): MediaEntry? {
    return media.firstOrNull { it.id == playingId }
        ?: media.firstOrNull { it.id == selectedId }
        ?: media.firstOrNull()
}
