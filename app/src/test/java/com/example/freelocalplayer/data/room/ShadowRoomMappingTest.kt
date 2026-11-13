package com.example.freelocalplayer.data.room

import com.example.freelocalplayer.data.LibraryState
import com.example.freelocalplayer.data.MediaEntry
import com.example.freelocalplayer.data.PlaylistPlace
import com.example.freelocalplayer.data.RetiredMedia
import com.example.freelocalplayer.data.UserPlaylist
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ShadowRoomMappingTest {
    private val one = MediaEntry(
        id = "old-uuid-A", uri = "content://media/external/audio/media/900",
        title = "Песня 🎵", isVideo = false, lyrics = "[00:01.1]<00:01.3>текст",
        sourceKey = "mediastore:external:audio:900", sizeBytes = 123456,
        durationMs = 90000
    )
    private val two = MediaEntry(
        id = "old-uuid-B", uri = "content://media/external/video/media/5",
        title = "Клип", isVideo = true, lyrics = "строка\nещё строка"
    )
    private val retired = RetiredMedia(
        entry = MediaEntry(
            id = "old-uuid-Z", uri = "content://documents/tree/my/file", title = "Удалённая",
            isVideo = false, lyrics = "<01:55.8>🎶"
        ),
        playlistPlaces = listOf(PlaylistPlace("playlist-2", 5), PlaylistPlace("playlist-1", 1))
    )
    private val initial = LibraryState(
        media = listOf(one, two),
        playlists = listOf(
            UserPlaylist("playlist-2", "Избранное 🕯", listOf("old-uuid-B", "old-uuid-A")),
            UserPlaylist("playlist-1", "Аккорды", listOf("old-uuid-A"))
        ),
        retiredMedia = listOf(retired)
    )

    @Test fun fullRoundTripKeepsAllUserDataAndUuid() {
        val rows = ShadowRoomMapping.toRows(initial)
        assertEquals(initial, rows.reconstruct())
        assertEquals(3, rows.tracks.size)
        assertEquals(2, rows.playlists.size)
        assertEquals(3, rows.playlistItems.size)
        assertEquals(2, rows.retiredPlaces.size)
        assertEquals("old-uuid-Z", rows.tracks.last().id)
    }

    @Test fun oldJsonLayoutWithoutRetiredEntriesStillRoundTrips() {
        val legacy = LibraryState(media = listOf(one.copy(sourceKey = "", sizeBytes = -1, durationMs = -1)))
        assertEquals(legacy, ShadowRoomMapping.toRows(legacy).reconstruct())
    }

    @Test fun emptyMediaEmptyPlaylistsAreSupported() {
        val empty = LibraryState()
        val rows = ShadowRoomMapping.toRows(empty)
        assertEquals(empty, rows.reconstruct())
        assertTrue(rows.tracks.isEmpty())
        assertTrue(rows.playlists.isEmpty())
    }

    @Test fun fingerprintChangesForLyricsAndPlaylistOrder() {
        val original = ShadowRoomMapping.fingerprint(ShadowRoomMapping.toRows(initial))
        val changedText = initial.copy(media = listOf(one.copy(lyrics = "Новый текст"), two))
        val changedOrder = initial.copy(playlists = initial.playlists.reversed())
        assertNotEquals(original, ShadowRoomMapping.fingerprint(ShadowRoomMapping.toRows(changedText)))
        assertNotEquals(original, ShadowRoomMapping.fingerprint(ShadowRoomMapping.toRows(changedOrder)))
    }

    @Test fun fingerprintIsStableUnderSqlRowOrdering() {
        val rows = ShadowRoomMapping.toRows(initial)
        val shuffledSql = rows.copy(
            tracks = rows.tracks.reversed(),
            playlists = rows.playlists.reversed(),
            playlistItems = rows.playlistItems.reversed(),
            retiredPlaces = rows.retiredPlaces.reversed()
        )
        assertEquals(ShadowRoomMapping.fingerprint(rows), ShadowRoomMapping.fingerprint(shuffledSql))
        assertEquals(initial, shuffledSql.reconstruct())
    }

    @Test fun identicalNameDoesNotChangeTrackIdentity() {
        val state = initial.copy(media = listOf(one, two.copy(title = one.title)))
        val result = ShadowRoomMapping.toRows(state).reconstruct()
        assertEquals(listOf("old-uuid-A", "old-uuid-B"), result.media.map { it.id })
        assertFalse(result.media[0].uri == result.media[1].uri)
    }

    @Test fun duplicateLiveAndRetiredUuidIsRejectedWithoutMutation() {
        val broken = initial.copy(retiredMedia = listOf(retired.copy(entry = retired.entry.copy(id = one.id))))
        var rejected = false
        try { ShadowRoomMapping.toRows(broken) } catch (_: IllegalArgumentException) { rejected = true }
        assertTrue(rejected)
        assertEquals("old-uuid-A", initial.media.first().id)
    }

    @Test fun duplicatePlaylistIdsAreRejected() {
        val broken = initial.copy(playlists = listOf(
            UserPlaylist("same-id", "Первый"), UserPlaylist("same-id", "Второй")
        ))
        var rejected = false
        try { ShadowRoomMapping.toRows(broken) } catch (_: IllegalArgumentException) { rejected = true }
        assertTrue(rejected)
    }

    @Test fun fingerprintDisambiguatesSeparatorCharacters() {
        val a = initial.copy(media = listOf(one.copy(title = "A|B", lyrics = "C"), two))
        val b = initial.copy(media = listOf(one.copy(title = "A", lyrics = "B|C"), two))
        assertNotEquals(
            ShadowRoomMapping.fingerprint(ShadowRoomMapping.toRows(a)),
            ShadowRoomMapping.fingerprint(ShadowRoomMapping.toRows(b))
        )
    }

    @Test fun savingReopenedTrackKeepsItsUuid() {
        val recovered = initial.copy(media = initial.media + retired.entry,
            retiredMedia = emptyList())
        assertEquals(recovered, ShadowRoomMapping.toRows(recovered).reconstruct())
        assertEquals("old-uuid-Z", ShadowRoomMapping.toRows(recovered).tracks.last().id)
    }
}
