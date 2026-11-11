package com.example.freelocalplayer.data

import java.util.UUID

data class MediaEntry(
    val id: String = UUID.randomUUID().toString(),
    val uri: String,
    val title: String,
    val isVideo: Boolean,
    val lyrics: String = ""
)

data class UserPlaylist(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val mediaIds: List<String> = emptyList()
)

data class LibraryState(
    val media: List<MediaEntry> = emptyList(),
    val playlists: List<UserPlaylist> = emptyList()
) {
    fun addMedia(items: List<MediaEntry>): LibraryState {
        val seen = media.mapTo(mutableSetOf()) { it.uri }
        val knownDocumentKeys = media.mapNotNullTo(mutableSetOf()) { mediaStoreKey(it.uri) }
        val additions = items.filter { candidate ->
            val key = mediaStoreKey(candidate.uri)
            if (candidate.uri in seen || (key != null && key in knownDocumentKeys)) false
            else {
                seen.add(candidate.uri)
                if (key != null) knownDocumentKeys.add(key)
                true
            }
        }
        return copy(media = media + additions)
    }

    fun removeMedia(id: String): LibraryState = copy(
        media = media.filterNot { it.id == id },
        playlists = playlists.map { it.copy(mediaIds = it.mediaIds.filterNot { entry -> entry == id }) }
    )

    fun updateLyrics(id: String, text: String): LibraryState = copy(
        media = media.map { if (it.id == id) it.copy(lyrics = text) else it }
    )

    fun createPlaylist(name: String): LibraryState {
        val normalized = name.trim()
        if (normalized.isEmpty() || playlists.any { it.name.equals(normalized, ignoreCase = true) }) return this
        return copy(playlists = playlists + UserPlaylist(name = normalized))
    }

    fun deletePlaylist(id: String): LibraryState = copy(playlists = playlists.filterNot { it.id == id })

    fun addToPlaylist(playlistId: String, mediaId: String): LibraryState =
        addManyToPlaylist(playlistId, listOf(mediaId))

    /** Insert valid media IDs in the given order, with no duplicates, in one state update. */
    fun addManyToPlaylist(playlistId: String, mediaIds: List<String>): LibraryState {
        if (mediaIds.isEmpty()) return this
        val validIds = media.mapTo(HashSet<String>()) { it.id }
        val requested = mediaIds.filter { it in validIds }.distinct()
        if (requested.isEmpty()) return this
        var changed = false
        val updated = playlists.map { playlist ->
            if (playlist.id != playlistId) playlist else {
                val seen = playlist.mediaIds.toHashSet()
                val added = requested.filter { seen.add(it) }
                if (added.isEmpty()) playlist else {
                    changed = true
                    playlist.copy(mediaIds = playlist.mediaIds + added)
                }
            }
        }
        return if (changed) copy(playlists = updated) else this
    }

    fun removeFromPlaylist(playlistId: String, mediaId: String): LibraryState = copy(
        playlists = playlists.map { playlist ->
            if (playlist.id == playlistId) playlist.copy(mediaIds = playlist.mediaIds.filterNot { it == mediaId })
            else playlist
        }
    )

    fun moveInPlaylist(playlistId: String, mediaId: String, direction: Int): LibraryState = copy(
        playlists = playlists.map { playlist ->
            if (playlist.id != playlistId) playlist else {
                val index = playlist.mediaIds.indexOf(mediaId)
                val destination = index + direction
                if (index < 0 || destination !in playlist.mediaIds.indices) playlist
                else {
                    val result = playlist.mediaIds.toMutableList()
                    result[index] = result[destination].also { result[destination] = result[index] }
                    playlist.copy(mediaIds = result)
                }
            }
        }
    )

    fun tracksFor(selection: String): List<MediaEntry> = when (selection) {
        "audio" -> media.filterNot { it.isVideo }
        "video" -> media.filter { it.isVideo }
        "all" -> media
        else -> {
            val playlist = playlists.find { "playlist:${it.id}" == selection }
            val byId = media.associateBy { it.id }
            playlist?.mediaIds?.mapNotNull(byId::get) ?: emptyList()
        }
    }
}

/** MediaDocuments and MediaStore may refer to the same Android media database row. */
private fun mediaStoreKey(uri: String): String? {
    val decoded = uri.replace("%3A", ":", ignoreCase = true)
    val document = Regex("/document/(audio|video):(\\d+)").find(decoded)
    if (document != null) return "${document.groupValues[1]}:${document.groupValues[2]}"
    val mediaStore = Regex("content://media/[^/]+/(audio|video)/media/(\\d+)").find(decoded)
    if (mediaStore != null) return "${mediaStore.groupValues[1]}:${mediaStore.groupValues[2]}"
    return null
}
