package com.example.freelocalplayer.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import com.example.freelocalplayer.data.room.ShadowRoomMirror
import com.example.freelocalplayer.data.room.VerifiedRoomReadSelector
import com.example.freelocalplayer.data.room.RoomReadRolloutWindow
import android.util.Log

/** Persists user-added URI references and lyrics only. Media bytes never leave the device. */
class LibraryStore(context: Context) {
    private val appContext = context.applicationContext
    // Application context only: queued disk writes must survive Activity recreation.
    private val preferences = context.applicationContext.getSharedPreferences(
        "local_library_v1", Context.MODE_PRIVATE
    )

    private data class WriteRequest(
        val preferences: android.content.SharedPreferences,
        val snapshot: LibraryState,
        val onError: (Throwable) -> Unit,
        val context: Context
    )

    companion object {
        // One writer for the whole process: old and new Activity instances cannot
        // persist overlapping generations of the library out of order.
        private val writer = LatestSnapshotWriter<WriteRequest>(
            write = { request ->
                CanonicalLibraryStateGuard.requireValid(request.snapshot)
                val json = encodeLibrary(request.snapshot)
                if (!request.preferences.edit().putString("library", json).commit()) {
                    throw IOException("SharedPreferences.commit() вернул false")
                }
                // Stage 0-C.2: Room is a non-authoritative replica; its failure MUST
                // NOT turn a successful canonical JSON write into a reported failure.
                ShadowRoomMirror.tryMirror(request.context, request.snapshot)
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

    private fun readCanonicalJson(): LibraryState {
        return try {
            CanonicalLibraryJsonDecoder.decode(
                raw = preferences.getString("library", null),
                keyExists = preferences.contains("library")
            )
        } catch (error: Exception) {
            // Never replace a partial snapshot with an empty writable library.
            throw IOException("РџРѕРІСЂРµР¶РґС‘РЅ JSON РјРµРґРёР°С‚РµРєРё; Р·Р°РїРёСЃСЊ РѕС‚РєР»СЋС‡РµРЅР° РґРѕ РІРѕСЃСЃС‚Р°РЅРѕРІР»РµРЅРёСЏ", error)
        }
    }
    /**
     * Runs only on a background thread, after awaitPendingWrites().
     * Default remains JSON until a separately verified rollout enables the
     * existing local_library_v1 / room_verified_read_0c3b preference. A valid
     * canonical JSON snapshot is always parsed first and remains recoverable.
     */
    fun load(): LibraryState {
        val canonical = readCanonicalJson()
        // A verified read may be enabled ONLY by a time-limited QA lease.
        // The timeout is evaluated before any SQLite access, including after a
        // process death or a disconnected ADB session.
        val requested = try {
            preferences.getBoolean(RoomReadRolloutWindow.ENABLED_KEY, false)
        } catch (_: ClassCastException) {
            false
        }
        val leaseExpiry = try {
            preferences.getLong(RoomReadRolloutWindow.EXPIRY_KEY, 0L)
        } catch (_: ClassCastException) {
            0L
        }
        val window = RoomReadRolloutWindow.evaluate(
            requested, leaseExpiry, System.currentTimeMillis()
        )
        if (window == RoomReadRolloutWindow.Status.EXPIRED_OR_INVALID) {
            disableTemporaryRoomRead("lease_expired_or_invalid")
        }
        val enabled = window == RoomReadRolloutWindow.Status.ACTIVE
        val decision = selectVerifiedRoom(canonical, enabled)
        when (decision.reason) {
            VerifiedRoomReadSelector.Reason.VERIFIED ->
                Log.i("FLP_ROOM_READ", "ROOM_READ_VERIFIED")
            VerifiedRoomReadSelector.Reason.DISABLED ->
                Log.i("FLP_ROOM_READ", "ROOM_READ_JSON_DEFAULT")
            VerifiedRoomReadSelector.Reason.GATE_REJECTED ->
                Log.w("FLP_ROOM_READ", "ROOM_READ_JSON_FALLBACK gate=${decision.validation}")
            VerifiedRoomReadSelector.Reason.ROOM_READ_FAILED ->
                Log.w("FLP_ROOM_READ", "ROOM_READ_JSON_FALLBACK read_error")
        }
        if (enabled && !decision.verifiedRoom) {
            // Never keep a rejected rollout active across the next cold start.
            // Even a failed flag commit is safe because the JSON gate will rerun.
            disableTemporaryRoomRead("room_gate_" + decision.reason.name)
        }
        // Preserve existing behavior: JSON repairs an outdated Room mirror only
        // AFTER the read decision was taken, never before verification.
        // The source passed to the writer is ALWAYS canonical JSON, not a
        // potentially stale or incomplete SQLite candidate.
        ShadowRoomMirror.tryMirror(appContext, canonical)
        return decision.state
    }

    private fun disableTemporaryRoomRead(reason: String) {
        try {
            val saved = preferences.edit()
                .putBoolean(RoomReadRolloutWindow.ENABLED_KEY, false)
                .remove(RoomReadRolloutWindow.EXPIRY_KEY)
                .commit()
            if (!saved) {
                Log.e("FLP_ROOM_READ", "ROOM_READ_ROLLBACK_FLAG_COMMIT_FAILED reason=$reason")
            } else {
                Log.w("FLP_ROOM_READ", "ROOM_READ_ROLLBACK_TO_JSON reason=$reason")
            }
        } catch (error: Exception) {
            // The canonical JSON has already been parsed successfully.
            // A flag-write failure is never permission to use unverified Room.
            Log.e("FLP_ROOM_READ", "ROOM_READ_ROLLBACK_FLAG_EXCEPTION reason=$reason", error)
        }
    }

    private fun selectVerifiedRoom(canonical: LibraryState, enabled: Boolean) =
        VerifiedRoomReadSelector.select(canonical, enabled) {
            ShadowRoomMirror.readSnapshotWithoutRepair(appContext)
        }

    /**
     * Device QA uses the exact production read path, but does not mutate the
     * flag, persist new JSON or repair Room. Never run this on the UI thread.
     */
    internal fun previewVerifiedRoomReadWithoutRepairForQa(): VerifiedRoomReadSelector.Decision =
        selectVerifiedRoom(readCanonicalJson(), enabled = true)

    /** Non-blocking for UI callers. Coalesces superseded whole-library snapshots. */
    fun save(state: LibraryState, onError: (Throwable) -> Unit = {}) {
        writer.submit(WriteRequest(preferences, state, onError, appContext))
    }

    /**
     * Completion is delivered by the background writer after the canonical JSON
     * commit, never when a snapshot merely enters its coalescing queue.
     * Superseded snapshot receipts follow the newest persisted snapshot.
     * The callback runs on the writer thread; callers must dispatch UI work.
     */
    fun saveConfirmed(state: LibraryState, onPersisted: (Result<Unit>) -> Unit) {
        writer.submit(WriteRequest(preferences, state, {}, appContext), onPersisted)
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
