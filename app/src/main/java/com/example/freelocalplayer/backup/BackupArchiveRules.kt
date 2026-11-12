package com.example.freelocalplayer.backup

/** Deliberately narrow allow-list: an untrusted ZIP cannot write outside private data. */
object BackupArchiveRules {
    const val FORMAT = "FreeLocalPlayer-offline-backup"
    const val VERSION = 1
    const val MAX_ARCHIVE_BYTES = 512L * 1024 * 1024
    const val MAX_UNCOMPRESSED_BYTES = 512L * 1024 * 1024
    const val MAX_FILE_BYTES = 12L * 1024 * 1024
    const val MAX_PREFS_BYTES = 80L * 1024 * 1024
    const val MAX_MANIFEST_BYTES = 12L * 1024 * 1024
    const val MAX_ENTRY_COUNT = 60_000

    val preferenceFiles = listOf(
        "local_library_v1", "player_shortcuts_v1", "music_mode_offsets_v1",
        "freelocalplayer_appearance", "media_discovery_v1"
    )

    private val musicFilename = Regex("[a-f0-9]{64}\\.(?:mid|chords|auto\\.chords|chord-edits)")

    fun isMusicFilename(name: String): Boolean = musicFilename.matches(name)

    fun isAllowedPayloadPath(name: String): Boolean {
        if (name == "prefs.json") return true
        if (!name.startsWith("music/")) return false
        val file = name.removePrefix("music/")
        return file.isNotEmpty() && '/' !in file && '\\' !in file && musicFilename.matches(file)
    }

    fun maxEntryBytes(name: String): Long = when (name) {
        "manifest.json" -> MAX_MANIFEST_BYTES
        "prefs.json" -> MAX_PREFS_BYTES
        else -> if (isAllowedPayloadPath(name)) MAX_FILE_BYTES else 0L
    }

    fun isSafeDeclaredSize(name: String, size: Long): Boolean =
        size >= 0L && size <= maxEntryBytes(name) && maxEntryBytes(name) > 0L

    fun isSafeTotal(total: Long, next: Long): Boolean =
        next >= 0 && total >= 0 && total <= MAX_UNCOMPRESSED_BYTES - next

    fun isSha256(value: String): Boolean = value.length == 64 && value.all {
        it in '0'..'9' || it in 'a'..'f'
    }
}
