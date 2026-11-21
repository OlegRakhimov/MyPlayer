package com.example.freelocalplayer.qa

import com.example.freelocalplayer.data.LibraryState
import com.example.freelocalplayer.data.MediaEntry
import com.example.freelocalplayer.data.PlaylistPlace
import com.example.freelocalplayer.data.RetiredMedia
import com.example.freelocalplayer.data.UserPlaylist

/**
 * QA build only. No real MediaStore entries or filesystem operations.
 * Every call returns independent immutable collections.
 */
internal object RelinkQaFixture {
    const val OLD_UUID = "qa-retired-original-uuid"
    const val NEW_UUID = "qa-scanned-temporary-uuid"
    const val MISMATCH_UUID = "qa-mismatched-size"
    const val PLAYLIST_ID = "qa-playlist-one"
    const val SAVED_LYRICS = "QA: сохранённые строки синтетического текста"

    fun create(): LibraryState {
        val old = MediaEntry(
            id = OLD_UUID,
            uri = "content://media/external/audio/media/41001",
            title = "QA: старый трек",
            isVideo = false,
            lyrics = SAVED_LYRICS,
            sizeBytes = 324_567L,
            durationMs = 194_000L
        )
        val scanned = MediaEntry(
            id = NEW_UUID,
            uri = "content://media/external/audio/media/41002",
            title = "QA: найденный после переиндексации",
            isVideo = false,
            sizeBytes = 324_567L,
            durationMs = 194_000L
        )
        val mismatch = MediaEntry(
            id = MISMATCH_UUID,
            uri = "content://media/external/audio/media/41003",
            title = "QA: неверный размер",
            isVideo = false,
            sizeBytes = 324_568L,
            durationMs = 194_000L
        )
        val existing = MediaEntry(
            id = "qa-existing-track",
            uri = "content://media/external/audio/media/41004",
            title = "QA: существующий трек",
            isVideo = false,
            sizeBytes = 82_000L,
            durationMs = 90_000L
        )
        return LibraryState(
            media = listOf(scanned, mismatch, existing),
            playlists = listOf(
                UserPlaylist(
                    id = PLAYLIST_ID,
                    name = "QA: учебный плейлист",
                    mediaIds = listOf(existing.id)
                )
            ),
            retiredMedia = listOf(
                RetiredMedia(
                    entry = old,
                    playlistPlaces = listOf(PlaylistPlace(PLAYLIST_ID, 1))
                )
            )
        )
    }
}
