package com.example.freelocalplayer.data

/**
 * A verified SAF ZIP authorizes an explicit UUID relink only for the exact
 * unchanged in-memory catalog and the auxiliary user-data generation captured
 * when the ZIP was exported.
 *
 * This is a conservative in-process gate, not a durable backup format or a
 * replacement for the AppDataBackup SHA-256 verification. No I/O is performed.
 */
internal class RelinkBackupReceipt private constructor(
    private val snapshot: LibraryState,
    private val musicGeneration: Int,
    private val recentSnapshot: List<String>,
    private val favoriteSnapshot: Set<String>
) {
    fun matches(
        current: LibraryState,
        musicRevision: Int,
        recentIds: List<String>,
        favoriteIds: Set<String>,
        sidecarWritesInFlight: Int
    ): Boolean {
        return snapshot === current &&
            musicGeneration == musicRevision &&
            recentSnapshot == recentIds &&
            favoriteSnapshot == favoriteIds &&
            sidecarWritesInFlight == 0
    }

    companion object {
        fun capture(
            library: LibraryState,
            musicRevision: Int,
            recentIds: List<String>,
            favoriteIds: Set<String>
        ): RelinkBackupReceipt = RelinkBackupReceipt(
            snapshot = library,
            musicGeneration = musicRevision,
            recentSnapshot = recentIds.toList(),
            favoriteSnapshot = favoriteIds.toSet()
        )
    }
}
