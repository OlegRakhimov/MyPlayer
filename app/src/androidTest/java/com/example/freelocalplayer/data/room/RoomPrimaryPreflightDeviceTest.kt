package com.example.freelocalplayer.data.room

import android.content.Context
import android.os.SystemClock
import android.util.Log
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.freelocalplayer.data.LibraryState
import com.example.freelocalplayer.data.MediaEntry
import com.example.freelocalplayer.data.PlaylistPlace
import com.example.freelocalplayer.data.RetiredMedia
import com.example.freelocalplayer.data.UserPlaylist
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Preflight ONLY. Never runs on a physical phone. Existing canonical JSON and Room
 * tables are read but never changed. Synthetic benchmarks own in-memory DBs.
 */
@RunWith(AndroidJUnit4::class)
class RoomPrimaryPreflightDeviceTest {
    private val tag = "FLP_ROOM_GATE"

    /** Matches the precise field defaults used by LibraryStore.load(), but does not mirror. */
    private fun decodeEntry(obj: JSONObject): MediaEntry = MediaEntry(
        id = obj.getString("id"),
        uri = obj.getString("uri"),
        title = obj.optString("title", "Без названия"),
        isVideo = obj.optBoolean("video", false),
        lyrics = obj.optString("lyrics", ""),
        sourceKey = obj.optString("sourceKey", ""),
        sizeBytes = obj.optLong("sizeBytes", -1L),
        durationMs = obj.optLong("durationMs", -1L)
    )

    private fun JSONObject.requiredArray(name: String): JSONArray =
        optJSONArray(name) ?: throw IllegalStateException("INVALID_JSON_ARRAY_$name")

    private fun parseCanonicalJson(json: String): LibraryState {
        val root = JSONObject(json)
        val media = root.requiredArray("media")
        val playlists = root.requiredArray("playlists")
        val retired = root.optJSONArray("retiredMedia") ?: JSONArray() // old ZIP v1

        return LibraryState(
            media = (0 until media.length()).map { decodeEntry(media.getJSONObject(it)) },
            playlists = (0 until playlists.length()).map { i ->
                val obj = playlists.getJSONObject(i)
                val ids = obj.requiredArray("mediaIds")
                UserPlaylist(obj.getString("id"), obj.optString("name", "Мой список"),
                    (0 until ids.length()).map { ids.getString(it) })
            },
            retiredMedia = (0 until retired.length()).map { i ->
                val record = retired.getJSONObject(i)
                val places = record.requiredArray("playlistPlaces")
                RetiredMedia(
                    decodeEntry(record.getJSONObject("entry")),
                    (0 until places.length()).map { j ->
                        val place = places.getJSONObject(j)
                        PlaylistPlace(place.getString("playlistId"), place.getInt("index"))
                    }
                )
            }
        )
    }

    private fun readRows(room: ShadowRoomDatabase): ShadowRowSet = ShadowRowSet(
        tracks = room.dao().tracks(),
        playlists = room.dao().playlists(),
        playlistItems = room.dao().playlistItems(),
        retiredPlaces = room.dao().retiredPlaces()
    )

    @Test
    fun importedCanonicalJsonMatchesExistingRoomShadowReadOnly() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val raw = context.getSharedPreferences("local_library_v1", Context.MODE_PRIVATE)
            .getString("library", null)
            ?: throw AssertionError("CANONICAL_JSON_MISSING: no fallback to an empty library")
        val canonical = parseCanonicalJson(raw)
        assertTrue("NO_REAL_TRACKS: confirm actual SAF import first", canonical.media.isNotEmpty())
        val databaseFile = context.getDatabasePath("flp-room-shadow-v1.db")
        assertTrue("PERSISTENT_ROOM_DB_MISSING: start the app once before preflight", databaseFile.isFile)

        // This test does NOT call LibraryStore.load() or ShadowRoomMirror.mirror().
        val db = Room.databaseBuilder(context, ShadowRoomDatabase::class.java,
            "flp-room-shadow-v1.db").build()
        try {
            val verified = RoomPrimaryCutoverGate.evaluate(canonical, readRows(db), db.dao().stamp())
            Log.i(tag, "REAL_CATALOG_GATE=${verified.status} tracks=${verified.tracks} " +
                "playlists=${verified.playlists} retired=${verified.retiredTracks}")
            assertTrue("ROOM_PRECHECK_NOT_READY=${verified.status}", verified.ready)
            Log.i(tag, "ROOM_PRIMARY_PREFLIGHT=PASS REAL_JSON_ROOM_MATCH=YES")
        } finally {
            db.close()
        }
    }

    private fun bench(count: Int) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        // No on-disk Room or SharedPreferences access: all records are synthetic.
        val entries = (0 until count).map { n ->
            MediaEntry(
                id = "load-uuid-$n", uri = "content://media/external/audio/media/${n + 1}",
                title = "Synthetic $n", isVideo = false, lyrics = "[00:01.10]Line $n",
                sourceKey = "mediastore:external:audio:${n + 1}",
                sizeBytes = 900000L + n, durationMs = 180000L
            )
        }
        val partitionSize = count / 5
        val playlists = (0 until 5).map { p ->
            UserPlaylist("load-list-$p", "Test playlist $p",
                entries.subList(p * partitionSize, (p + 1) * partitionSize).map { it.id })
        }
        val source = LibraryState(media = entries, playlists = playlists)
        val db = Room.inMemoryDatabaseBuilder(context, ShadowRoomDatabase::class.java).build()
        try {
            val start = SystemClock.elapsedRealtimeNanos()
            ShadowRoomMirror.mirrorInto(db, source)
            val durationMs = (SystemClock.elapsedRealtimeNanos() - start) / 1_000_000
            val checked = RoomPrimaryCutoverGate.evaluate(source, readRows(db), db.dao().stamp())
            assertTrue("SYNTHETIC_GATE_${count}=${checked.status}", checked.ready)
            assertEquals(count, checked.tracks)
            // Second pass tests production incremental/unchanged branch.
            val againStart = SystemClock.elapsedRealtimeNanos()
            ShadowRoomMirror.mirrorInto(db, source)
            val againMs = (SystemClock.elapsedRealtimeNanos() - againStart) / 1_000_000
            assertTrue(RoomPrimaryCutoverGate.evaluate(source, readRows(db), db.dao().stamp()).ready)
            Log.i(tag, "PERF_${count}_TRACKS=PASS writeMs=$durationMs repeatMs=$againMs " +
                "playlists=${playlists.size} source=synthetic")
        } finally {
            db.close()
        }
    }

    @Test fun synthetic5000RecordsMirrorRoundTrip() = bench(5000)
    @Test fun synthetic20000RecordsMirrorRoundTrip() = bench(20000)
}
