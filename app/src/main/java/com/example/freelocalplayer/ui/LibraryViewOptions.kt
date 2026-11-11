package com.example.freelocalplayer.ui

import com.example.freelocalplayer.data.MediaEntry
import java.util.Locale

/** Display-only options: playback queue and library persistence are never reordered. */
internal enum class MediaSortOption(val label: String) {
    ADDED("Как добавлено"),
    TITLE_AZ("А–Я"),
    TITLE_ZA("Я–А");

    companion object {
        fun fromStored(value: String?): MediaSortOption =
            entries.firstOrNull { it.name == value } ?: ADDED
    }
}

internal enum class LyricsViewFilter(val label: String, val shortLabel: String) {
    ALL("Все композиции", "Текст"),
    WITH_TEXT("С текстом", "Есть"),
    WITHOUT_TEXT("Без текста", "Нет");

    companion object {
        fun fromStored(value: String?): LyricsViewFilter =
            entries.firstOrNull { it.name == value } ?: ALL
    }
}

/** Search by known title only: the model has no separate artist/album/date fields. */
internal fun selectLibraryMedia(
    source: List<MediaEntry>,
    query: String,
    sort: MediaSortOption,
    lyricsFilter: LyricsViewFilter = LyricsViewFilter.ALL
): List<MediaEntry> {
    val term = query.trim()
    val filtered = source.asSequence()
        .filter { term.isEmpty() || it.title.contains(term, ignoreCase = true) }
        .filter {
            when (lyricsFilter) {
                LyricsViewFilter.ALL -> true
                LyricsViewFilter.WITH_TEXT -> it.lyrics.isNotBlank()
                LyricsViewFilter.WITHOUT_TEXT -> it.lyrics.isBlank()
            }
        }
        .toList()
    return when (sort) {
        MediaSortOption.ADDED -> filtered
        MediaSortOption.TITLE_AZ -> filtered.sortedBy { it.title.lowercase(Locale.ROOT) }
        MediaSortOption.TITLE_ZA -> filtered.sortedByDescending { it.title.lowercase(Locale.ROOT) }
    }
}
