package com.example.freelocalplayer.ui

import androidx.media3.common.Player
import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackModesTest {
    @Test fun chronologicalOrderIsNotShuffledOrRepeated() {
        assertEquals(PlaybackPreset.ORDER, detectPlaybackPreset(Player.REPEAT_MODE_OFF, false))
    }

    @Test fun individualSongRepeatRemainsAccessible() {
        assertEquals(PlaybackPreset.REPEAT_ONE, detectPlaybackPreset(Player.REPEAT_MODE_ONE, false))
    }

    @Test fun playlistRepeatRemainsAccessible() {
        assertEquals(PlaybackPreset.REPEAT_ALL, detectPlaybackPreset(Player.REPEAT_MODE_ALL, false))
    }

    @Test fun shuffleCanRepeatFullQueue() {
        assertEquals(PlaybackPreset.SHUFFLE, detectPlaybackPreset(Player.REPEAT_MODE_OFF, true))
        assertEquals(PlaybackPreset.SHUFFLE_REPEAT, detectPlaybackPreset(Player.REPEAT_MODE_ALL, true))
    }

    @Test fun eachModeHasDistinctSettings() {
        val pairs = PlaybackPreset.entries.map { it.shuffle to it.repeat }
        assertEquals(pairs.size, pairs.toSet().size)
    }
}
