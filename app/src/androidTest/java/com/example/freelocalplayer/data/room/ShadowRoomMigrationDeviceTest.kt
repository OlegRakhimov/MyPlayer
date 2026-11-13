package com.example.freelocalplayer.data.room

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.freelocalplayer.data.LibraryState
import com.example.freelocalplayer.data.MediaEntry
import com.example.freelocalplayer.data.PlaylistPlace
import com.example.freelocalplayer.data.RetiredMedia
import com.example.freelocalplayer.data.UserPlaylist
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith

/** Tests a fresh *in-memory* Room database; never touches user's actual app data. */
@RunWith(AndroidJUnit4::class)
class ShadowRoomMigrationDeviceTest {
    private fun sample() = LibraryState(
        media = listOf(MediaEntry("keep-uuid", "content://media/x/audio/media/1", "Трек", false,
            "[00:01.000]Караоке", "mediastore:x:audio:1", 500, 1000)),
        playlists = listOf(UserPlaylist("list-uuid", "Плейлист", listOf("keep-uuid"))),
        retiredMedia = listOf(RetiredMedia(
            MediaEntry("archived-uuid", "content://media/x/audio/media/2", "Архив", false, "LRC"),
            listOf(PlaylistPlace("list-uuid", 1))
        ))
    )

    @Test fun inMemorySqliteRoundTripAndTransactionalRollback() {
        val app = ApplicationProvider.getApplicationContext<android.content.Context>()
        val db = Room.inMemoryDatabaseBuilder(app, ShadowRoomDatabase::class.java).build()
        try {
            val source = sample()
            val rows = ShadowRoomMapping.toRows(source)
            val dao = db.dao()
            db.runInTransaction {
                dao.putTracks(rows.tracks)
                dao.putPlaylists(rows.playlists)
                dao.putPlaylistItems(rows.playlistItems)
                dao.putRetiredPlaces(rows.retiredPlaces)
                dao.putStamp(ShadowStamp(signature = ShadowRoomMapping.fingerprint(rows),
                    tracks = source.media.size, playlists = source.playlists.size,
                    retiredTracks = source.retiredMedia.size))
            }
            val persisted = ShadowRowSet(dao.tracks(), dao.playlists(), dao.playlistItems(), dao.retiredPlaces())
            assertEquals(source, persisted.reconstruct())
            assertEquals(ShadowRoomMapping.fingerprint(rows), dao.stamp()?.signature)

            try {
                db.runInTransaction {
                    dao.deleteTracks(listOf("keep-uuid"))
                    error("Deliberate rollback test")
                }
            } catch (_: IllegalStateException) {
                // Expected: the SQLite transaction must undo the deletion.
            }
            assertEquals(source, ShadowRowSet(
                dao.tracks(), dao.playlists(), dao.playlistItems(), dao.retiredPlaces()
            ).reconstruct())
        } finally {
            db.close()
        }
    }
}
