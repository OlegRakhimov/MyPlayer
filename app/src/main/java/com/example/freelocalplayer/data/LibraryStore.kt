package com.example.freelocalplayer.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Persists user-added URI references and lyrics only. Media bytes never leave the device. */
class LibraryStore(context: Context) {
    private val preferences = context.getSharedPreferences("local_library_v1", Context.MODE_PRIVATE)

    fun load(): LibraryState = try {
        val root = JSONObject(preferences.getString("library", "{}") ?: "{}")
        val media = root.optJSONArray("media")?.let { array ->
            (0 until array.length()).mapNotNull { index ->
                val obj = array.optJSONObject(index) ?: return@mapNotNull null
                val id = obj.optString("id")
                val uri = obj.optString("uri")
                if (id.isEmpty() || uri.isEmpty()) return@mapNotNull null
                MediaEntry(
                    id = id,
                    uri = uri,
                    title = obj.optString("title", "Без названия"),
                    isVideo = obj.optBoolean("video", false),
                    lyrics = obj.optString("lyrics", "")
                )
            }
        } ?: emptyList()
        val playlists = root.optJSONArray("playlists")?.let { array ->
            (0 until array.length()).mapNotNull { index ->
                val obj = array.optJSONObject(index) ?: return@mapNotNull null
                val id = obj.optString("id")
                if (id.isEmpty()) return@mapNotNull null
                val ids = obj.optJSONArray("mediaIds")?.let { idArray ->
                    (0 until idArray.length()).mapNotNull { i -> idArray.optString(i).takeIf(String::isNotEmpty) }
                } ?: emptyList()
                UserPlaylist(id, obj.optString("name", "Мой список"), ids)
            }
        } ?: emptyList()
        LibraryState(media, playlists)
    } catch (_: Exception) {
        LibraryState()
    }

    fun save(state: LibraryState) {
        val root = JSONObject()
        root.put("media", JSONArray().apply {
            state.media.forEach { entry ->
                put(JSONObject().apply {
                    put("id", entry.id)
                    put("uri", entry.uri)
                    put("title", entry.title)
                    put("video", entry.isVideo)
                    put("lyrics", entry.lyrics)
                })
            }
        })
        root.put("playlists", JSONArray().apply {
            state.playlists.forEach { playlist ->
                put(JSONObject().apply {
                    put("id", playlist.id)
                    put("name", playlist.name)
                    put("mediaIds", JSONArray(playlist.mediaIds))
                })
            }
        })
        preferences.edit().putString("library", root.toString()).apply()
    }

    fun getSpeed(): Float = preferences.getFloat("speed", 1f).coerceIn(0.5f, 2.0f)
    fun setSpeed(value: Float) = preferences.edit().putFloat("speed", value).apply()

    fun getRepeatMode(): Int = preferences.getInt("repeat", 0)
    fun setRepeatMode(value: Int) = preferences.edit().putInt("repeat", value).apply()

    fun getShuffle(): Boolean = preferences.getBoolean("shuffle", false)
    fun setShuffle(value: Boolean) = preferences.edit().putBoolean("shuffle", value).apply()
}
