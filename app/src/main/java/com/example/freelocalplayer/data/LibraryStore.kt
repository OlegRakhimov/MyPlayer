package com.example.freelocalplayer.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

/** Persists user-added URI references and lyrics only. Media bytes never leave the device. */
class LibraryStore(context: Context) {
    // Application context only: queued disk writes must survive Activity recreation.
    private val preferences = context.applicationContext.getSharedPreferences(
        "local_library_v1", Context.MODE_PRIVATE
    )

    private data class WriteRequest(
        val preferences: android.content.SharedPreferences,
        val snapshot: LibraryState,
        val onError: (Throwable) -> Unit
    )

    companion object {
        // One writer for the whole process: old and new Activity instances cannot
        // persist overlapping generations of the library out of order.
        private val writer = LatestSnapshotWriter<WriteRequest>(
            write = { request ->
                val json = encodeLibrary(request.snapshot)
                if (!request.preferences.edit().putString("library", json).commit()) {
                    throw IOException("SharedPreferences.commit() вернул false")
                }
            },
            onWriteFailure = { request, error -> request.onError(error) }
        )

        private fun encodeLibrary(state: LibraryState): String {
            val root = JSONObject()
            root.put("media", JSONArray().apply {
                state.media.forEach { entry ->
                    put(JSONObject().apply {
                        put("id", entry.id)
                        put("uri", entry.uri)
                        put("title", entry.title)
                        put("video", entry.isVideo)
                        put("lyrics", entry.lyrics)
                        put("sourceKey", entry.sourceKey)
                        put("sizeBytes", entry.sizeBytes)
                        put("durationMs", entry.durationMs)
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
            root.put("retiredMedia", JSONArray().apply {
                state.retiredMedia.forEach { record ->
                    put(JSONObject().apply {
                        put("entry", JSONObject().apply {
                            put("id", record.entry.id)
                            put("uri", record.entry.uri)
                            put("title", record.entry.title)
                            put("video", record.entry.isVideo)
                            put("lyrics", record.entry.lyrics)
                            put("sourceKey", record.entry.sourceKey)
                            put("sizeBytes", record.entry.sizeBytes)
                            put("durationMs", record.entry.durationMs)
                        })
                        put("playlistPlaces", JSONArray().apply {
                            record.playlistPlaces.forEach { place ->
                                put(JSONObject().put("playlistId", place.playlistId).put("index", place.index))
                            }
                        })
                    })
                }
            })
            return root.toString()
        }
    }

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
                    lyrics = obj.optString("lyrics", ""),
                    sourceKey = obj.optString("sourceKey", ""),
                    sizeBytes = obj.optLong("sizeBytes", -1L),
                    durationMs = obj.optLong("durationMs", -1L)
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
        val retired = root.optJSONArray("retiredMedia")?.let { array ->
            (0 until array.length()).mapNotNull { index ->
                val wrapper = array.optJSONObject(index) ?: return@mapNotNull null
                val obj = wrapper.optJSONObject("entry") ?: return@mapNotNull null
                val id = obj.optString("id")
                val uri = obj.optString("uri")
                if (id.isBlank() || uri.isBlank()) return@mapNotNull null
                val entry = MediaEntry(
                    id = id, uri = uri,
                    title = obj.optString("title", "Без названия"),
                    isVideo = obj.optBoolean("video", false),
                    lyrics = obj.optString("lyrics", ""),
                    sourceKey = obj.optString("sourceKey", ""),
                    sizeBytes = obj.optLong("sizeBytes", -1L),
                    durationMs = obj.optLong("durationMs", -1L)
                )
                val places = wrapper.optJSONArray("playlistPlaces")?.let { refs ->
                    (0 until refs.length()).mapNotNull placeLoop@{ i ->
                        val place = refs.optJSONObject(i) ?: return@placeLoop null
                        val playlistId = place.optString("playlistId")
                        val slot = place.optInt("index", -1)
                        if (playlistId.isBlank() || slot < 0) null else PlaylistPlace(playlistId, slot)
                    }
                } ?: emptyList()
                RetiredMedia(entry, places)
            }
        } ?: emptyList()
        LibraryState(media, playlists, retired)
    } catch (error: Exception) {
        // Returning an empty library would silently destroy user data at next save.
        throw IOException("Повреждён JSON медиатеки; запись отключена до восстановления", error)
    }

    /** Non-blocking for UI callers. Coalesces superseded whole-library snapshots. */
    fun save(state: LibraryState, onError: (Throwable) -> Unit = {}) {
        writer.submit(WriteRequest(preferences, state, onError))
    }

    /** Call only from Dispatchers.IO before backup, import, or a new Activity load. */
    fun awaitPendingWrites() = writer.awaitIdle()

    fun getSpeed(): Float = preferences.getFloat("speed", 1f).coerceIn(0.5f, 2.0f)
    fun setSpeed(value: Float) = preferences.edit().putFloat("speed", value).apply()

    fun getRepeatMode(): Int = preferences.getInt("repeat", 0)
    fun setRepeatMode(value: Int) = preferences.edit().putInt("repeat", value).apply()

    fun getShuffle(): Boolean = preferences.getBoolean("shuffle", false)
    fun setShuffle(value: Boolean) = preferences.edit().putBoolean("shuffle", value).apply()
}
