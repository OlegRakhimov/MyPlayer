package com.example.freelocalplayer.data

import org.json.JSONArray
import org.json.JSONObject

/**
 * Strict decoder for the canonical SharedPreferences `library` value.
 *
 * Older LibraryStore versions used optJSONArray/mapNotNull and could silently
 * omit damaged tracks/playlist entries, later persisting the shortened state.
 * This decoder never treats a present but malformed snapshot as an empty one.
 *
 * The only implicit empty library is an ABSENT `library` preference, as on a
 * fresh install. `retiredMedia` remains optional for older valid snapshots.
 * No repair, Room read, UUID generation, sorting or data deletion is performed.
 */
object CanonicalLibraryJsonDecoder {
    fun decode(raw: String?, keyExists: Boolean): LibraryState {
        if (!keyExists) {
            require(raw == null) { "CANONICAL_KEY_ABSENT_BUT_VALUE_PRESENT" }
            return LibraryState()
        }
        require(!raw.isNullOrBlank()) { "CANONICAL_JSON_MISSING_OR_BLANK" }
        val root = JSONObject(raw)
        val media = requiredArray(root, "media")
        val playlists = requiredArray(root, "playlists")
        val retired = if (root.has("retiredMedia")) requiredArray(root, "retiredMedia")
            else JSONArray() // pre-tombstone historical snapshots

        val liveEntries = (0 until media.length()).map { index ->
            decodeEntry(media.getJSONObject(index))
        }
        val userPlaylists = (0 until playlists.length()).map { index ->
            val obj = playlists.getJSONObject(index)
            val ids = requiredArray(obj, "mediaIds")
            UserPlaylist(
                id = requiredText(obj, "id"),
                name = obj.optString("name", "Мой список"),
                mediaIds = (0 until ids.length()).map { i ->
                    val item = ids.get(i)
                    require(item is String && item.isNotBlank()) {
                        "CANONICAL_INVALID_PLAYLIST_MEDIA_ID"
                    }
                    item
                }
            )
        }
        val removedEntries = (0 until retired.length()).map { index ->
            val wrapper = retired.getJSONObject(index)
            // Tombstones were introduced together with playlistPlaces. Missing
            // positions here could silently erase the user's restore order.
            val places = requiredArray(wrapper, "playlistPlaces")
            RetiredMedia(
                entry = decodeEntry(wrapper.getJSONObject("entry")),
                playlistPlaces = (0 until places.length()).map { i ->
                    val obj = places.getJSONObject(i)
                    require(obj.has("index")) { "CANONICAL_MISSING_RETIRED_INDEX" }
                    val rawIndex = obj.get("index")
                    require(rawIndex is Number && rawIndex.toLong() in 0L..Int.MAX_VALUE.toLong()) {
                        "CANONICAL_INVALID_RETIRED_INDEX"
                    }
                    PlaylistPlace(requiredText(obj, "playlistId"), rawIndex.toInt())
                }
            )
        }
        return LibraryState(liveEntries, userPlaylists, removedEntries).also {
            CanonicalLibraryStateGuard.requireValid(it)
            check(it.media.size == media.length() &&
                it.playlists.size == playlists.length() &&
                it.retiredMedia.size == retired.length()) {
                "CANONICAL_COUNT_CHANGED_WHILE_DECODING"
            }
        }
    }

    private fun requiredArray(parent: JSONObject, name: String): JSONArray {
        require(parent.has(name)) { "CANONICAL_MISSING_ARRAY_$name" }
        return parent.optJSONArray(name) ?: throw IllegalArgumentException(
            "CANONICAL_INVALID_ARRAY_$name"
        )
    }

    private fun requiredText(parent: JSONObject, name: String): String {
        require(parent.has(name)) { "CANONICAL_MISSING_TEXT_$name" }
        val raw = parent.get(name)
        require(raw is String && raw.isNotBlank()) { "CANONICAL_INVALID_TEXT_$name" }
        return raw
    }

    private fun decodeEntry(obj: JSONObject): MediaEntry = MediaEntry(
        id = requiredText(obj, "id"),
        uri = requiredText(obj, "uri"),
        title = obj.optString("title", "Без названия"),
        isVideo = obj.optBoolean("video", false),
        lyrics = obj.optString("lyrics", ""),
        sourceKey = obj.optString("sourceKey", ""),
        sizeBytes = obj.optLong("sizeBytes", -1L),
        durationMs = obj.optLong("durationMs", -1L)
    )
}
