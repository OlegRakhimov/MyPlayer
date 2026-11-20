package com.example.freelocalplayer.data

/**
 * 0-B.3: explicitly replace a newly scanned, unused live UUID with a retired
 * UUID. This is only a state transformation, never an automatic scan rule.
 *
 * External UUID-owned files, playback history and favorites are intentionally
 * checked by the calling Activity before any commit. The candidate must have
 * no user data in this LibraryState.
 */
internal object ConfirmedRetiredMediaRebind {
    enum class Refusal {
        NONE,
        OLD_NOT_RETIRED,
        OLD_UUID_ALREADY_LIVE,
        NEW_NOT_LIVE,
        SAME_UUID,
        MEDIA_TYPE_MISMATCH,
        EVIDENCE_UNAVAILABLE,
        SIZE_MISMATCH,
        DURATION_MISMATCH,
        CANDIDATE_HAS_LYRICS,
        CANDIDATE_IN_PLAYLIST,
        CANDIDATE_URI_COLLISION,
        CANDIDATE_SOURCE_COLLISION
    }

    fun preview(state: LibraryState, oldUuid: String, candidateUuid: String): Refusal {
        CanonicalLibraryStateGuard.requireValid(state)
        if (oldUuid == candidateUuid) return Refusal.SAME_UUID
        if (state.media.any { it.id == oldUuid }) return Refusal.OLD_UUID_ALREADY_LIVE
        val old = state.retiredMedia.firstOrNull { it.entry.id == oldUuid }?.entry
            ?: return Refusal.OLD_NOT_RETIRED
        val fresh = state.media.firstOrNull { it.id == candidateUuid }
            ?: return Refusal.NEW_NOT_LIVE
        if (old.isVideo != fresh.isVideo) return Refusal.MEDIA_TYPE_MISMATCH
        if (old.sizeBytes <= 0L || old.durationMs <= 0L ||
            fresh.sizeBytes <= 0L || fresh.durationMs <= 0L) {
            return Refusal.EVIDENCE_UNAVAILABLE
        }
        if (old.sizeBytes != fresh.sizeBytes) return Refusal.SIZE_MISMATCH
        if (old.durationMs != fresh.durationMs) return Refusal.DURATION_MISMATCH
        if (fresh.lyrics.isNotEmpty()) return Refusal.CANDIDATE_HAS_LYRICS
        if (state.playlists.any { candidateUuid in it.mediaIds }) {
            return Refusal.CANDIDATE_IN_PLAYLIST
        }
        // An ordinary MediaStore scan should supply a volume-qualified source
        // key. SAF-only entries without verified metadata are not supported.
        val strong = MediaIdentity.strongKey(fresh)
        if (strong == null || !fresh.uri.startsWith("content://media/")) {
            return Refusal.EVIDENCE_UNAVAILABLE
        }
        if (state.media.any { it.id != candidateUuid && it.uri == fresh.uri } ||
            state.retiredMedia.any { it.entry.uri == fresh.uri && it.entry.id != oldUuid }) {
            return Refusal.CANDIDATE_URI_COLLISION
        }
        if (state.media.any { it.id != candidateUuid && MediaIdentity.strongKey(it) == strong } ||
            state.retiredMedia.any { it.entry.id != oldUuid &&
                MediaIdentity.strongKey(it.entry) == strong }) {
            return Refusal.CANDIDATE_SOURCE_COLLISION
        }
        return Refusal.NONE
    }

    /**
     * Fails without changing the input state. Caller must independently verify
     * real file accessibility and the absence of UUID-keyed private assets,
     * favorites, recents and pending playback for candidateUuid.
     */
    fun confirmed(
        state: LibraryState,
        oldUuid: String,
        candidateUuid: String,
        userConfirmed: Boolean
    ): LibraryState {
        require(userConfirmed) { "RELINK_EXPLICIT_USER_CONFIRMATION_REQUIRED" }
        val status = preview(state, oldUuid, candidateUuid)
        require(status == Refusal.NONE) { "RELINK_REJECTED_" + status.name }
        val tombstone = state.retiredMedia.first { it.entry.id == oldUuid }
        val candidate = state.media.first { it.id == candidateUuid }
        val restored = tombstone.entry.copy(
            uri = candidate.uri,
            title = candidate.title,
            isVideo = candidate.isVideo,
            sourceKey = MediaIdentity.strongKey(candidate).orEmpty(),
            sizeBytes = candidate.sizeBytes,
            durationMs = candidate.durationMs
        )
        val playlists = state.playlists.map { list ->
            val positions = tombstone.playlistPlaces
                .filter { it.playlistId == list.id }
                .sortedBy { it.index }
            if (positions.isEmpty() || oldUuid in list.mediaIds) list
            else {
                val ids = list.mediaIds.toMutableList()
                ids.add(positions.first().index.coerceIn(0, ids.size), oldUuid)
                list.copy(mediaIds = ids)
            }
        }
        return state.copy(
            media = state.media.filterNot { it.id == candidateUuid } + restored,
            retiredMedia = state.retiredMedia.filterNot { it.entry.id == oldUuid },
            playlists = playlists
        ).also(CanonicalLibraryStateGuard::requireValid)
    }
}
