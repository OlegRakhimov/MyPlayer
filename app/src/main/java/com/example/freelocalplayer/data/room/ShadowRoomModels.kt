package com.example.freelocalplayer.data.room

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.freelocalplayer.data.LibraryState
import com.example.freelocalplayer.data.MediaEntry
import com.example.freelocalplayer.data.PlaylistPlace
import com.example.freelocalplayer.data.RetiredMedia
import com.example.freelocalplayer.data.UserPlaylist
import java.security.MessageDigest

/** One row per known user-owned track ID. This ID is never recalculated from MediaStore. */
@Entity(tableName = "shadow_tracks")
data class ShadowTrack(
    @PrimaryKey val id: String,
    val position: Int,
    val retired: Boolean,
    val uri: String,
    val title: String,
    val video: Boolean,
    val lyrics: String,
    val sourceKey: String,
    val sizeBytes: Long,
    val durationMs: Long
)

@Entity(tableName = "shadow_playlists")
data class ShadowPlaylist(
    @PrimaryKey val id: String,
    val position: Int,
    val name: String
)

@Entity(tableName = "shadow_playlist_items", primaryKeys = ["playlistId", "position"])
data class ShadowPlaylistItem(
    val playlistId: String,
    val position: Int,
    val trackId: String
)

@Entity(tableName = "shadow_retired_places", primaryKeys = ["trackId", "position"])
data class ShadowRetiredPlace(
    val trackId: String,
    val position: Int,
    val playlistId: String,
    val playlistIndex: Int
)

/** Commit marker is updated in the same SQLite transaction as all rows. */
@Entity(tableName = "shadow_stamp")
data class ShadowStamp(
    @PrimaryKey val singleton: Int = 1,
    val schemaVersion: Int = 1,
    val signature: String,
    val tracks: Int,
    val playlists: Int,
    val retiredTracks: Int
)

/** Pure conversion: no Android APIs, no hidden UUID generation and no mutable global state. */
data class ShadowRowSet(
    val tracks: List<ShadowTrack>,
    val playlists: List<ShadowPlaylist>,
    val playlistItems: List<ShadowPlaylistItem>,
    val retiredPlaces: List<ShadowRetiredPlace>
) {
    fun reconstruct(): LibraryState {
        check(tracks.map { it.id }.distinct().size == tracks.size) { "Repeated track UUID" }
        check(playlists.map { it.id }.distinct().size == playlists.size) { "Repeated playlist UUID" }
        check(tracks.map { it.retired to it.position }.distinct().size == tracks.size) {
            "Repeated track position"
        }
        check(playlists.map { it.position }.distinct().size == playlists.size) { "Repeated playlist position" }
        check(playlistItems.map { it.playlistId to it.position }.distinct().size == playlistItems.size) {
            "Repeated playlist slot"
        }
        check(retiredPlaces.map { it.trackId to it.position }.distinct().size == retiredPlaces.size) {
            "Repeated retired slot"
        }
        val members = playlistItems.groupBy { it.playlistId }
        val retiredMembership = retiredPlaces.groupBy { it.trackId }
        val live = tracks.filterNot { it.retired }.sortedBy { it.position }.map(ShadowTrack::entry)
        val removed = tracks.filter { it.retired }.sortedBy { it.position }.map { row ->
            RetiredMedia(
                entry = row.entry(),
                playlistPlaces = retiredMembership[row.id].orEmpty()
                    .sortedBy { it.position }
                    .map { PlaylistPlace(it.playlistId, it.playlistIndex) }
            )
        }
        val savedLists = playlists.sortedBy { it.position }.map { row ->
            UserPlaylist(
                id = row.id,
                name = row.name,
                mediaIds = members[row.id].orEmpty().sortedBy { it.position }.map { it.trackId }
            )
        }
        return LibraryState(live, savedLists, removed)
    }
}

private fun ShadowTrack.entry() = MediaEntry(
    id = id, uri = uri, title = title, isVideo = video, lyrics = lyrics,
    sourceKey = sourceKey, sizeBytes = sizeBytes, durationMs = durationMs
)

object ShadowRoomMapping {
    /** A single authoritative LibraryState is serialized into separate, ordered SQLite tables. */
    fun toRows(state: LibraryState): ShadowRowSet {
        val allIds = state.media.map { it.id } + state.retiredMedia.map { it.entry.id }
        require(allIds.none(String::isBlank) && allIds.distinct().size == allIds.size) {
            "Media IDs are empty or duplicated: shadow migration refused"
        }
        val listIds = state.playlists.map { it.id }
        require(listIds.none(String::isBlank) && listIds.distinct().size == listIds.size) {
            "Playlist IDs are empty or duplicated: shadow migration refused"
        }
        fun MediaEntry.row(position: Int, retired: Boolean) = ShadowTrack(
            id = id, position = position, retired = retired, uri = uri, title = title,
            video = isVideo, lyrics = lyrics, sourceKey = sourceKey,
            sizeBytes = sizeBytes, durationMs = durationMs
        )
        val tracks = state.media.mapIndexed { index, entry -> entry.row(index, false) } +
            state.retiredMedia.mapIndexed { index, item -> item.entry.row(index, true) }
        val playlists = state.playlists.mapIndexed { index, item ->
            ShadowPlaylist(item.id, index, item.name)
        }
        val members = state.playlists.flatMap { playlist ->
            playlist.mediaIds.mapIndexed { index, mediaId ->
                ShadowPlaylistItem(playlist.id, index, mediaId)
            }
        }
        val retiredPlaces = state.retiredMedia.flatMap { item ->
            item.playlistPlaces.mapIndexed { index, place ->
                ShadowRetiredPlace(item.entry.id, index, place.playlistId, place.index)
            }
        }
        return ShadowRowSet(tracks, playlists, members, retiredPlaces).also { result ->
            check(result.reconstruct() == state) { "Room mapping lost user-owned data" }
        }
    }

    /** Collision-free framing for strings/lengths: independent of JSON key order. */
    fun fingerprint(rows: ShadowRowSet): String {
        val hash = MessageDigest.getInstance("SHA-256")
        fun value(raw: String) {
            val bytes = raw.toByteArray(Charsets.UTF_8)
            val n = bytes.size
            hash.update(byteArrayOf((n ushr 24).toByte(), (n ushr 16).toByte(),
                (n ushr 8).toByte(), n.toByte()))
            hash.update(bytes)
        }
        value("FLP-ROOM-SHADOW-V1")
        for (t in rows.tracks.sortedWith(compareBy<ShadowTrack> { it.retired }.thenBy { it.position })) {
            value("track")
            listOf(t.id, t.position.toString(), t.retired.toString(), t.uri, t.title,
                t.video.toString(), t.lyrics, t.sourceKey, t.sizeBytes.toString(),
                t.durationMs.toString()).forEach(::value)
        }
        for (p in rows.playlists.sortedBy { it.position }) {
            value("playlist")
            listOf(p.id, p.position.toString(), p.name).forEach(::value)
        }
        for (m in rows.playlistItems.sortedWith(compareBy<ShadowPlaylistItem> { it.playlistId }.thenBy { it.position })) {
            value("item")
            listOf(m.playlistId, m.position.toString(), m.trackId).forEach(::value)
        }
        for (r in rows.retiredPlaces.sortedWith(compareBy<ShadowRetiredPlace> { it.trackId }.thenBy { it.position })) {
            value("retired-place")
            listOf(r.trackId, r.position.toString(), r.playlistId,
                r.playlistIndex.toString()).forEach(::value)
        }
        return hash.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }
}
