package com.example.freelocalplayer.media

/** Supported media recognition for document providers with unreliable MIME types. */
object MediaFormatSupport {
    private val videoExtensions = setOf("mp4", "mkv", "webm", "avi", "mov", "m4v", "3gp", "ts")
    private val audioExtensions = setOf("mp3", "m4a", "aac", "flac", "ogg", "opus", "wav", "wave", "wma", "amr", "aiff")

    fun isWave(name: String): Boolean = name.substringAfterLast('.', "").lowercase() in setOf("wav", "wave")

    /** True = video, false = audio, null = not identified as a playable media file. */
    fun isVideo(name: String, mime: String?): Boolean? {
        val extension = name.substringAfterLast('.', "").lowercase()
        if (extension in videoExtensions) return true
        if (extension in audioExtensions) return false
        if (mime?.startsWith("video/", ignoreCase = true) == true) return true
        if (mime?.startsWith("audio/", ignoreCase = true) == true) return false
        return null
    }
}
