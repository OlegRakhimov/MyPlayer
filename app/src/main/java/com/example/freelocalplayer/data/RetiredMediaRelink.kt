package com.example.freelocalplayer.data

/**
 * Stage 0-B.2. Explicit, opt-in re-linking of a retired media UUID to a NEW
 * MediaStore/picker URI after re-indexing. Nothing calls this automatically.
 *
 * Never guess an identity from title/size/duration. Matching metadata only
 * acts as a refusal gate; a human confirmation is still required. If an
 * unrelated, already-live candidate carries lyrics or MIDI/chord attachments,
 * do not merge it here: external UUID-keyed assets need their own migration.
 *
 * This pure state transformer does not read files, persist JSON, mutate Room,
 * grant URI permissions, or decide whether a candidate can actually play.
 */
internal object RetiredMediaRelink {
    enum class Status {
        READY,
        OLD_UUID_NOT_RETIRED,
        OLD_UUID_IS_LIVE,
        INVALID_CANDIDATE,
        CANDIDATE_HAS_USER_LYRICS,
        CANDIDATE_ID_ALREADY_USED,
        MEDIA_TYPE_MISMATCH,
        EVIDENCE_UNAVAILABLE,
        SIZE_MISMATCH,
        DURATION_MISMATCH,
        TARGET_URI_COLLISION,
        TARGET_SOURCE_COLLISION
    }

    /** This is a preview only; it never changes the library. */
    fun preview(state: LibraryState, oldUuid: String, scanned: MediaEntry): Status {
        CanonicalLibraryStateGuard.requireValid(state)
        if (state.media.any { it.id == oldUuid }) return Status.OLD_UUID_IS_LIVE
        val record = state.retiredMedia.find { it.entry.id == oldUuid }
            ?: return Status.OLD_UUID_NOT_RETIRED
        if (scanned.uri.isBlank() || scanned.title.isBlank() || oldUuid.isBlank()) {
            return Status.INVALID_CANDIDATE
        }
        if (scanned.lyrics.isNotEmpty()) return Status.CANDIDATE_HAS_USER_LYRICS
        if (scanned.id != oldUuid &&
            (state.media.any { it.id == scanned.id } ||
                state.retiredMedia.any { it.entry.id == scanned.id })) {
            return Status.CANDIDATE_ID_ALREADY_USED
        }
        val old = record.entry
        if (old.isVideo != scanned.isVideo) return Status.MEDIA_TYPE_MISMATCH
        if (old.sizeBytes <= 0L || scanned.sizeBytes <= 0L ||
            old.durationMs <= 0L || scanned.durationMs <= 0L) {
            return Status.EVIDENCE_UNAVAILABLE
        }
        if (old.sizeBytes != scanned.sizeBytes) return Status.SIZE_MISMATCH
        if (old.durationMs != scanned.durationMs) return Status.DURATION_MISMATCH

        // Never steal an existing URI (or even a retired URI) from another UUID.
        if (state.media.any { it.uri == scanned.uri } ||
            state.retiredMedia.any { it.entry.id != oldUuid && it.entry.uri == scanned.uri }) {
            return Status.TARGET_URI_COLLISION
        }
        val targetKey = MediaIdentity.strongKey(scanned)
        if (targetKey != null &&
            (state.media.any { MediaIdentity.strongKey(it) == targetKey } ||
                state.retiredMedia.any { it.entry.id != oldUuid &&
                    MediaIdentity.strongKey(it.entry) == targetKey })) {
            return Status.TARGET_SOURCE_COLLISION
        }
        return Status.READY
    }

    /**
     * Deliberately requires a separate, affirmative user decision. A stale or
     * ambiguous preview must not allow a write; all guards are rechecked here.
     * Throws without altering the input on refusal.
     */
    fun applyConfirmed(
        state: LibraryState,
        oldUuid: String,
        selectedScanResult: MediaEntry,
        userConfirmed: Boolean
    ): LibraryState {
        require(userConfirmed) { "RELINK_REQUIRES_EXPLICIT_CONFIRMATION" }
        val status = preview(state, oldUuid, selectedScanResult)
        require(status == Status.READY) { "RELINK_REJECTED_" + status.name }
        val retired = state.retiredMedia.first { it.entry.id == oldUuid }
        val original = retired.entry
        // Keep the OLD UUID and all user-owned lyrics. Only scanning evidence
        // (URI, display title, source, file size, duration, media type) changes.
        val relinked = original.copy(
            uri = selectedScanResult.uri,
            title = selectedScanResult.title,
            sourceKey = MediaIdentity.strongKey(selectedScanResult).orEmpty(),
            sizeBytes = selectedScanResult.sizeBytes,
            durationMs = selectedScanResult.durationMs
        )
        val updatedPlaylists = state.playlists.map { playlist ->
            val places = retired.playlistPlaces.filter { it.playlistId == playlist.id }
            if (places.isEmpty() || oldUuid in playlist.mediaIds) playlist
            else {
                val ids = playlist.mediaIds.toMutableList()
                // Original insertion offsets refer to the old, pre-delete list.
                for (place in places.sortedBy { it.index }) {
                    if (oldUuid !in ids) ids.add(place.index.coerceIn(0, ids.size), oldUuid)
                }
                playlist.copy(mediaIds = ids)
            }
        }
        return state.copy(
            media = state.media + relinked,
            playlists = updatedPlaylists,
            retiredMedia = state.retiredMedia.filterNot { it.entry.id == oldUuid }
        ).also(CanonicalLibraryStateGuard::requireValid)
    }
}
