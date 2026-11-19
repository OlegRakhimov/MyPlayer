package com.example.freelocalplayer.playback

/**
 * A bounded, single-item checkpoint. It is NOT the user media library or an
 * authoritative replacement for a Media3 playlist.
 */
internal data class PlaybackResumeSnapshot(
    val mediaId: String,
    val uri: String,
    val title: String,
    val positionMs: Long
)

internal object PlaybackResumePolicy {
    const val MAX_URI_LENGTH = 4096
    const val MAX_TITLE_LENGTH = 240
    const val MAX_MEDIA_ID_LENGTH = 256
    // Restrict corruption from absurd or malformed timestamps without guessing duration.
    const val MAX_POSITION_MS = 604_800_000L // Seven days of continuous media

    /** Reject remote streams; this app's resume source is strictly local media. */
    fun isLocalUri(uri: String): Boolean {
        if (uri.length !in 9..MAX_URI_LENGTH) return false
        return if (uri.startsWith("content://", ignoreCase = true)) {
            val authority = uri.substring(10).substringBefore('/')
            authority.isNotBlank() && '?' !in authority && '#' !in authority
        } else {
            uri.startsWith("file:///", ignoreCase = true) && uri.length > 8
        }
    }

    fun create(
        mediaId: String?,
        uri: String?,
        title: String?,
        positionMs: Long
    ): PlaybackResumeSnapshot? {
        val id = mediaId?.trim() ?: return null
        val rawUri = uri?.trim() ?: return null
        if (id.isBlank() || id.length > MAX_MEDIA_ID_LENGTH || !isLocalUri(rawUri)) return null
        val safePosition = positionMs.coerceIn(0L, MAX_POSITION_MS)
        return PlaybackResumeSnapshot(
            mediaId = id,
            uri = rawUri,
            title = title.orEmpty().take(MAX_TITLE_LENGTH),
            positionMs = safePosition
        )
    }
}
