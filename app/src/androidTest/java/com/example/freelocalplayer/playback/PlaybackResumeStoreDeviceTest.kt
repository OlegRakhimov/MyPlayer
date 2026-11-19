package com.example.freelocalplayer.playback

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.util.UUID
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Isolated QA preferences; never uses playback_resume_v1 or local_library_v1. */
@RunWith(AndroidJUnit4::class)
class PlaybackResumeStoreDeviceTest {
    private lateinit var app: Context
    private lateinit var name: String
    private lateinit var store: PlaybackResumeStore

    @Before fun setup() {
        app = ApplicationProvider.getApplicationContext()
        name = "__flp_0e1_resume_" + UUID.randomUUID().toString().replace("-", "")
        store = PlaybackResumeStore(app.getSharedPreferences(name, Context.MODE_PRIVATE))
    }

    @After fun cleanup() {
        assertTrue(app.deleteSharedPreferences(name))
    }

    private fun snapshot(position: Long = 31_000L) = PlaybackResumeSnapshot(
        "qa-track", "content://media/external/audio/media/77", "QA only", position
    )

    @Test fun savesAndLoadsOnlySingleLocalCheckpoint() {
        assertTrue(store.save(snapshot()))
        assertEquals(snapshot(), store.load())
    }

    @Test fun overwritesOnlyTheOptionalCheckpoint() {
        assertTrue(store.save(snapshot(10_000)))
        assertTrue(store.save(snapshot(99_000)))
        assertEquals(99_000L, store.load()?.positionMs)
    }

    @Test fun rejectsRemoteSourcesWithoutChangingSavedState() {
        assertTrue(store.save(snapshot()))
        val unsafe = snapshot().copy(uri = "https://example.com/song.mp3")
        assertEquals(false, store.save(unsafe))
        assertEquals(snapshot(), store.load())
    }

    @Test fun malformedStoredPayloadIsIgnoredRatherThanCrashing() {
        assertTrue(app.getSharedPreferences(name, Context.MODE_PRIVATE).edit()
            .putString("last_item", "{this is invalid").commit())
        assertNull(store.load())
    }

    @Test fun unknownVersionIsIgnored() {
        assertTrue(app.getSharedPreferences(name, Context.MODE_PRIVATE).edit().putString(
            "last_item", """{"version":987,"mediaId":"qa","uri":"content://media/1","positionMs":1}"""
        ).commit())
        assertNull(store.load())
    }

    @Test fun checkpointCanBeReadByNewStoreInstance() {
        assertTrue(store.save(snapshot()))
        val another = PlaybackResumeStore(app.getSharedPreferences(name, Context.MODE_PRIVATE))
        assertNotNull(another.load())
        assertEquals(snapshot(), another.load())
    }
}
