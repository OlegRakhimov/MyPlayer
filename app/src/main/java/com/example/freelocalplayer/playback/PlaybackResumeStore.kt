package com.example.freelocalplayer.playback

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONObject

/**
 * Separate from local_library_v1 so a playback checkpoint can never alter a
 * lyric, playlist or stable media ID. Writes are nonblocking and best-effort.
 */
internal class PlaybackResumeStore(private val preferences: SharedPreferences) {
    companion object {
        const val PREF_NAME = "playback_resume_v1"
        private const val KEY = "last_item"
        private const val VERSION = 1

        fun forApplication(context: Context): PlaybackResumeStore = PlaybackResumeStore(
            context.applicationContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        )
    }

    fun save(snapshot: PlaybackResumeSnapshot): Boolean {
        val validated = PlaybackResumePolicy.create(
            snapshot.mediaId, snapshot.uri, snapshot.title, snapshot.positionMs
        ) ?: return false
        val raw = JSONObject().apply {
            put("version", VERSION)
            put("mediaId", validated.mediaId)
            put("uri", validated.uri)
            put("title", validated.title)
            put("positionMs", validated.positionMs)
        }.toString()
        preferences.edit().putString(KEY, raw).apply()
        return true
    }

    fun load(): PlaybackResumeSnapshot? {
        return try {
        val raw = preferences.getString(KEY, null) ?: return null
        val json = JSONObject(raw)
        if (json.getInt("version") != VERSION) return null
        val position = json.getLong("positionMs")
        if (position < 0 || position > PlaybackResumePolicy.MAX_POSITION_MS) return null
        PlaybackResumePolicy.create(
            json.getString("mediaId"),
            json.getString("uri"),
            json.optString("title", ""),
            position
        )
        } catch (_: Exception) {
            // A damaged optional checkpoint must never prevent library startup.
            null
        }
    }
}
