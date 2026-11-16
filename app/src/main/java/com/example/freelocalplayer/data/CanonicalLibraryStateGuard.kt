package com.example.freelocalplayer.data

/**
 * Stage 0-C.3-D: structural invariants of the canonical user library.
 *
 * This guard has no Android, Room, JSON or persistence dependencies. It must
 * reject a damaged state BEFORE JSON commit or a future Room-primary cutover.
 * It never invents new UUIDs, removes entries or normalizes user-owned data.
 *
 * Playlist references are not required to resolve to currently live tracks:
 * legacy imports may deliberately retain references to unavailable media.
 */
object CanonicalLibraryStateGuard {
    fun requireValid(state: LibraryState) {
        val mediaIds = HashSet<String>(state.media.size + state.retiredMedia.size)
        for (entry in state.media) {
            require(entry.id.isNotBlank()) { "CANONICAL_EMPTY_TRACK_ID" }
            require(entry.uri.isNotBlank()) { "CANONICAL_EMPTY_TRACK_URI" }
            require(mediaIds.add(entry.id)) { "CANONICAL_DUPLICATE_TRACK_ID" }
        }
        for (removed in state.retiredMedia) {
            val entry = removed.entry
            require(entry.id.isNotBlank()) { "CANONICAL_EMPTY_RETIRED_ID" }
            require(entry.uri.isNotBlank()) { "CANONICAL_EMPTY_RETIRED_URI" }
            require(mediaIds.add(entry.id)) { "CANONICAL_DUPLICATE_LIVE_RETIRED_ID" }
            for (place in removed.playlistPlaces) {
                require(place.playlistId.isNotBlank()) { "CANONICAL_EMPTY_RETIRED_PLAYLIST_ID" }
                require(place.index >= 0) { "CANONICAL_NEGATIVE_RETIRED_PLAYLIST_INDEX" }
            }
        }
        val playlistIds = HashSet<String>(state.playlists.size)
        for (playlist in state.playlists) {
            require(playlist.id.isNotBlank()) { "CANONICAL_EMPTY_PLAYLIST_ID" }
            require(playlistIds.add(playlist.id)) { "CANONICAL_DUPLICATE_PLAYLIST_ID" }
            require(playlist.mediaIds.none(String::isBlank)) { "CANONICAL_BLANK_PLAYLIST_TRACK_ID" }
        }
    }
}
