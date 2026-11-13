package com.example.freelocalplayer.data.room

import android.content.Context
import android.util.Log
import androidx.room.Room
import com.example.freelocalplayer.data.LibraryState

/**
 * Migration stage 0-C.2: JSON is STILL AUTHORITATIVE. Room is a verified shadow copy.
 * Room failure must never overwrite or invalidate existing preferences, SAF ZIP or UUIDs.
 * Every method is called from a background worker, not the UI thread.
 */
object ShadowRoomMirror {
    private const val TAG = "FLP_ROOM_MIRROR"
    private const val DATABASE_NAME = "flp-room-shadow-v1.db"
    @Volatile private var database: ShadowRoomDatabase? = null

    private fun open(context: Context): ShadowRoomDatabase {
        val ready = database
        if (ready != null) return ready
        return synchronized(this) {
            database ?: Room.databaseBuilder(
                context.applicationContext, ShadowRoomDatabase::class.java, DATABASE_NAME
            ).build().also { database = it }
        }
    }

    private fun rows(dao: ShadowRoomDao): ShadowRowSet = ShadowRowSet(
        tracks = dao.tracks(),
        playlists = dao.playlists(),
        playlistItems = dao.playlistItems(),
        retiredPlaces = dao.retiredPlaces()
    )

    /**
     * Upsert changed rows and remove stale rows; do not rewrite every track on every change.
     * An SQLite transaction includes final round-trip validation and the commit stamp.
     */
    @Synchronized
    fun mirror(context: Context, source: LibraryState) {
        mirrorInto(open(context), source)
        Log.i(TAG, "ROOM_MIRROR_VERIFIED tracks=${source.media.size} " +
            "retired=${source.retiredMedia.size} playlists=${source.playlists.size}")
    }

    /**
     * Exercise the exact production transaction with an isolated in-memory Room
     * database in Android instrumented tests. The caller owns the database.
     * This function never reads or changes SharedPreferences or user media.
     */
    internal fun mirrorInto(room: ShadowRoomDatabase, source: LibraryState) {
        val expected = ShadowRoomMapping.toRows(source)
        val signature = ShadowRoomMapping.fingerprint(expected)
        room.runInTransaction {
            val dao = room.dao()
            val previous = rows(dao)
            val stamp = dao.stamp()
            if (previous.reconstruct() == source && stamp != null && stamp.signature == signature &&
                stamp.schemaVersion == 1 && stamp.tracks == source.media.size &&
                stamp.playlists == source.playlists.size &&
                stamp.retiredTracks == source.retiredMedia.size) {
                return@runInTransaction
            }

            val prevTracks = previous.tracks.associateBy { it.id }
            val nextTracks = expected.tracks.associateBy { it.id }
            (prevTracks.keys - nextTracks.keys).toList().chunked(400).forEach(dao::deleteTracks)
            val updatedTracks = expected.tracks.filter { prevTracks[it.id] != it }
            updatedTracks.chunked(400).forEach(dao::putTracks)

            val prevLists = previous.playlists.associateBy { it.id }
            val nextLists = expected.playlists.associateBy { it.id }
            (prevLists.keys - nextLists.keys).toList().chunked(400).forEach(dao::deletePlaylists)
            expected.playlists.filter { prevLists[it.id] != it }.chunked(400)
                .forEach(dao::putPlaylists)

            val prevItems = previous.playlistItems.associateBy { it.playlistId to it.position }
            val nextItems = expected.playlistItems.associateBy { it.playlistId to it.position }
            for (key in prevItems.keys - nextItems.keys) {
                dao.deletePlaylistItem(key.first, key.second)
            }
            expected.playlistItems.filter { prevItems[it.playlistId to it.position] != it }
                .chunked(400).forEach(dao::putPlaylistItems)

            val prevPlaces = previous.retiredPlaces.associateBy { it.trackId to it.position }
            val nextPlaces = expected.retiredPlaces.associateBy { it.trackId to it.position }
            for (key in prevPlaces.keys - nextPlaces.keys) {
                dao.deleteRetiredPlace(key.first, key.second)
            }
            expected.retiredPlaces.filter { prevPlaces[it.trackId to it.position] != it }
                .chunked(400).forEach(dao::putRetiredPlaces)

            // Read the database back BEFORE committing. Any mismatch rolls everything back.
            val roundTrip = rows(dao)
            check(roundTrip.reconstruct() == source) {
                "Room round-trip differs from saved JSON; rolling back SQLite transaction"
            }
            check(ShadowRoomMapping.fingerprint(roundTrip) == signature) {
                "Room content fingerprint differs; rolling back SQLite transaction"
            }
            dao.putStamp(ShadowStamp(
                signature = signature,
                tracks = source.media.size,
                playlists = source.playlists.size,
                retiredTracks = source.retiredMedia.size
            ))
        }
    }

    /** Non-fatal: protects the canonical JSON if Room fails to compile/open/write. */
    fun tryMirror(context: Context, source: LibraryState) {
        try {
            mirror(context, source)
        } catch (error: Exception) {
            Log.e(TAG, "ROOM_MIRROR_FAILED JSON_REMAINS_AUTHORITATIVE", error)
        }
    }
}
