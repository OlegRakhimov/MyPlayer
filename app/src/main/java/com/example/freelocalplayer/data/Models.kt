package com.example.freelocalplayer.data

import java.util.UUID

data class MediaEntry(
    val id: String = UUID.randomUUID().toString(),
    val uri: String,
    val title: String,
    val isVideo: Boolean,
    val lyrics: String = "",
    // Optional scan evidence. Existing UUIDs and original lyrics are never recalculated.
    val sourceKey: String = "",
    val sizeBytes: Long = -1L,
    val durationMs: Long = -1L
)

data class UserPlaylist(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val mediaIds: List<String> = emptyList()
)

data class PlaylistPlace(val playlistId: String, val index: Int)

/** A user-removed record whose UUID, lyrics and playlist positions may be recovered. */
data class RetiredMedia(val entry: MediaEntry, val playlistPlaces: List<PlaylistPlace> = emptyList())

data class LibraryState(
    val media: List<MediaEntry> = emptyList(),
    val playlists: List<UserPlaylist> = emptyList(),
    val retiredMedia: List<RetiredMedia> = emptyList()
) {
    /**
     * Restore an old UUID only when source evidence is unambiguous. No filename-only
     * or size-only relink: two legitimate copies of one song must remain separate.
     * Maps are built once, not searched linearly for each item of a 20k-file scan.
     */
    fun addMedia(items: List<MediaEntry>): LibraryState {
        if (items.isEmpty()) return this
        val live = media.toMutableList()
        val retired = LinkedHashMap<String, RetiredMedia>()
        retiredMedia.forEach { retired[it.entry.id] = it }
        val retiredUriIndex = HashMap<String, MutableSet<String>>()
        val retiredStrongIndex = HashMap<String, MutableSet<String>>()
        val retiredLegacyIndex = HashMap<String, MutableSet<String>>()
        for (record in retired.values) {
            val entry = record.entry
            retiredUriIndex.getOrPut(entry.uri) { linkedSetOf() }.add(entry.id)
            MediaIdentity.strongKey(entry)?.let { key ->
                retiredStrongIndex.getOrPut(key) { linkedSetOf() }.add(entry.id)
            }
            MediaIdentity.legacyKey(entry.uri)?.let { key ->
                retiredLegacyIndex.getOrPut(key) { linkedSetOf() }.add(entry.id)
            }
        }
        val liveIds = media.mapTo(HashSet<String>()) { it.id }
        val revisedPlaylists = playlists.toMutableList()
        var changed = false

        // The indexes contain positions rather than objects, so a metadata enrichment can
        // replace the original entry without invalidating the mapping.
        val uriIndex = HashMap<String, Int>()
        val strongIndex = HashMap<String, MutableSet<Int>>()
        val legacyIndex = HashMap<String, MutableSet<Int>>()
        fun register(entry: MediaEntry, position: Int) {
            uriIndex[entry.uri] = position
            val strong = MediaIdentity.strongKey(entry)
            if (strong != null) strongIndex.getOrPut(strong) { linkedSetOf() }.add(position)
            val legacy = MediaIdentity.legacyKey(entry.uri)
            if (legacy != null) legacyIndex.getOrPut(legacy) { linkedSetOf() }.add(position)
        }
        live.forEachIndexed { index, entry -> register(entry, index) }

        fun compatible(original: MediaEntry, candidate: MediaEntry): Boolean {
            if (original.isVideo != candidate.isVideo) return false
            if (original.sizeBytes > 0 && candidate.sizeBytes > 0 &&
                original.sizeBytes != candidate.sizeBytes) return false
            if (original.durationMs > 0 && candidate.durationMs > 0 &&
                original.durationMs != candidate.durationMs) return false
            return true
        }
        fun sameSong(original: MediaEntry, candidate: MediaEntry): Boolean {
            if (!compatible(original, candidate)) return false
            if (original.uri == candidate.uri) {
                // A reused MediaStore row must not silently inherit old lyrics when
                // its known metadata or name has changed.
                return original.title == candidate.title ||
                    (original.sizeBytes > 0 && candidate.sizeBytes > 0 &&
                        original.durationMs > 0 && candidate.durationMs > 0)
            }
            val oldStrong = MediaIdentity.strongKey(original)
            val newStrong = MediaIdentity.strongKey(candidate)
            if (oldStrong != null && oldStrong == newStrong) {
                // Strong provider identity also needs corroboration to guard ID recycling.
                return (original.sizeBytes > 0 && candidate.sizeBytes > 0 &&
                    original.sizeBytes == candidate.sizeBytes) &&
                    (original.durationMs <= 0 || candidate.durationMs <= 0 ||
                        original.durationMs == candidate.durationMs)
            }
            // Legacy Android picker URI and MediaStore URI sometimes point to the
            // same database row. This weak alias is accepted ONLY for a matching name.
            if (oldStrong != null && newStrong != null) return false
            val a = MediaIdentity.legacyKey(original.uri)
            return a != null && a == MediaIdentity.legacyKey(candidate.uri) &&
                original.title == candidate.title
        }
        fun enrich(original: MediaEntry, candidate: MediaEntry): MediaEntry = original.copy(
            sourceKey = MediaIdentity.strongKey(candidate) ?: original.sourceKey,
            sizeBytes = if (original.sizeBytes <= 0) candidate.sizeBytes else original.sizeBytes,
            durationMs = if (original.durationMs <= 0) candidate.durationMs else original.durationMs
        )
        fun uniqueIndex(candidate: MediaEntry): Int? {
            val uri = uriIndex[candidate.uri]
            if (uri != null && sameSong(live[uri], candidate)) return uri
            val strong = MediaIdentity.strongKey(candidate)
            if (strong != null) {
                val hits = strongIndex[strong].orEmpty().filter { sameSong(live[it], candidate) }
                if (hits.size == 1) return hits[0]
                if (hits.size > 1) return null
            }
            val legacy = MediaIdentity.legacyKey(candidate.uri)
            if (legacy != null) {
                val hits = legacyIndex[legacy].orEmpty().filter { sameSong(live[it], candidate) }
                if (hits.size == 1) return hits[0]
            }
            return null
        }
        for (candidate in items) {
            // The source may emit two representations of the same row in one scan.
            val oldPosition = uniqueIndex(candidate)
            if (oldPosition != null) {
                val old = live[oldPosition]
                val updated = enrich(old, candidate)
                if (old != updated) {
                    live[oldPosition] = updated
                    register(updated, oldPosition)
                    changed = true
                }
                continue
            }
            // Do not restore from an ambiguous tombstone, even if names are equal.
            val potentialIds = linkedSetOf<String>()
            potentialIds.addAll(retiredUriIndex[candidate.uri].orEmpty())
            MediaIdentity.strongKey(candidate)?.let { potentialIds.addAll(retiredStrongIndex[it].orEmpty()) }
            MediaIdentity.legacyKey(candidate.uri)?.let { potentialIds.addAll(retiredLegacyIndex[it].orEmpty()) }
            val matches = potentialIds.mapNotNull(retired::get)
                .filter { sameSong(it.entry, candidate) }
            if (matches.size == 1 && matches[0].entry.id !in liveIds) {
                val retiredRecord = matches[0]
                retired.remove(retiredRecord.entry.id)
                val restored = enrich(retiredRecord.entry, candidate).copy(
                    uri = candidate.uri,
                    title = candidate.title,
                    isVideo = candidate.isVideo
                )
                live += restored
                liveIds += restored.id
                register(restored, live.lastIndex)
                for (place in retiredRecord.playlistPlaces) {
                    val pIndex = revisedPlaylists.indexOfFirst { it.id == place.playlistId }
                    if (pIndex >= 0) {
                        val playlist = revisedPlaylists[pIndex]
                        if (restored.id !in playlist.mediaIds) {
                            val entries = playlist.mediaIds.toMutableList()
                            entries.add(place.index.coerceIn(0, entries.size), restored.id)
                            revisedPlaylists[pIndex] = playlist.copy(mediaIds = entries)
                        }
                    }
                }
                changed = true
                continue
            }
            // Exact URI collision but an incompatible fingerprint: do NOT add a
            // second duplicate entry or attach another file's data.
            if (candidate.uri in uriIndex) continue
            // Protect against new IDs colliding with old persisted ones.
            val candidateId = candidate.id
            val fresh = if (candidateId in liveIds || candidateId in retired) {
                candidate.copy(id = UUID.randomUUID().toString())
            } else candidate
            val newEntry = enrich(fresh, candidate)
            live += newEntry
            liveIds += newEntry.id
            register(newEntry, live.lastIndex)
            changed = true
        }
        return if (changed) copy(media = live, playlists = revisedPlaylists, retiredMedia = retired.values.toList()) else this
    }

    /** Removal hides a track, but preserves its UUID and associated user data. */
    fun removeMedia(id: String): LibraryState {
        val removed = media.firstOrNull { it.id == id } ?: return this
        val positions = playlists.flatMap { playlist ->
            playlist.mediaIds.mapIndexedNotNull { index, mediaId ->
                if (mediaId == id) PlaylistPlace(playlist.id, index) else null
            }
        }
        return copy(
            media = media.filterNot { it.id == id },
            playlists = playlists.map { playlist ->
                playlist.copy(mediaIds = playlist.mediaIds.filterNot { it == id })
            },
            retiredMedia = retiredMedia.filterNot { it.entry.id == id } + RetiredMedia(removed, positions)
        )
    }

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

/**
 * Identifiers are aliases, never replacements for a user's persistent UUID.
 * MediaStore IDs can be recycled and are not cross-device content hashes.
 */
object MediaIdentity {
    private val mediaStore = Regex("^content://media/([^/]+)/(audio|video)/media/(\\d+)$", RegexOption.IGNORE_CASE)
    private val mediaDocument = Regex("/document/(audio|video):(\\d+)", RegexOption.IGNORE_CASE)

    fun strongKey(entry: MediaEntry): String? {
        if (entry.sourceKey.isNotBlank()) return entry.sourceKey
        val m = mediaStore.matchEntire(entry.uri) ?: return null
        return "mediastore:${m.groupValues[1].lowercase()}:${m.groupValues[2].lowercase()}:${m.groupValues[3]}"
    }

    fun legacyKey(uri: String): String? {
        val decoded = uri.replace("%3A", ":", ignoreCase = true)
        val doc = mediaDocument.find(decoded)
        if (doc != null) return "${doc.groupValues[1].lowercase()}:${doc.groupValues[2]}"
        val store = mediaStore.matchEntire(decoded)
        return if (store != null) "${store.groupValues[2].lowercase()}:${store.groupValues[3]}" else null
    }
}
