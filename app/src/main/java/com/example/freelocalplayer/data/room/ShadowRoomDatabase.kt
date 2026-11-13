package com.example.freelocalplayer.data.room

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RoomDatabase

@Dao
interface ShadowRoomDao {
    @Query("SELECT * FROM shadow_tracks ORDER BY retired ASC, position ASC")
    fun tracks(): List<ShadowTrack>

    @Query("SELECT * FROM shadow_playlists ORDER BY position ASC")
    fun playlists(): List<ShadowPlaylist>

    @Query("SELECT * FROM shadow_playlist_items ORDER BY playlistId ASC, position ASC")
    fun playlistItems(): List<ShadowPlaylistItem>

    @Query("SELECT * FROM shadow_retired_places ORDER BY trackId ASC, position ASC")
    fun retiredPlaces(): List<ShadowRetiredPlace>

    @Query("SELECT * FROM shadow_stamp WHERE singleton = 1")
    fun stamp(): ShadowStamp?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun putTracks(items: List<ShadowTrack>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun putPlaylists(items: List<ShadowPlaylist>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun putPlaylistItems(items: List<ShadowPlaylistItem>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun putRetiredPlaces(items: List<ShadowRetiredPlace>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun putStamp(stamp: ShadowStamp)

    @Query("DELETE FROM shadow_tracks WHERE id IN (:ids)")
    fun deleteTracks(ids: List<String>)

    @Query("DELETE FROM shadow_playlists WHERE id IN (:ids)")
    fun deletePlaylists(ids: List<String>)

    @Query("DELETE FROM shadow_playlist_items WHERE playlistId = :playlistId AND position = :position")
    fun deletePlaylistItem(playlistId: String, position: Int)

    @Query("DELETE FROM shadow_retired_places WHERE trackId = :trackId AND position = :position")
    fun deleteRetiredPlace(trackId: String, position: Int)
}

@Database(
    entities = [
        ShadowTrack::class, ShadowPlaylist::class, ShadowPlaylistItem::class,
        ShadowRetiredPlace::class, ShadowStamp::class
    ],
    version = 1,
    exportSchema = true
)
abstract class ShadowRoomDatabase : RoomDatabase() {
    abstract fun dao(): ShadowRoomDao
}
