package com.example.freelocalplayer.playback

import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.example.freelocalplayer.media.MediaFormatSupport

/**
 * Media session is kept outside MainActivity. A media-button resume explicitly
 * restores ONE previously played local item, not an invented playlist.
 */
class PlaybackService : MediaSessionService() {
    private var session: MediaSession? = null
    private lateinit var checkpoint: PlaybackResumeStore
    private val handler = Handler(Looper.getMainLooper())
    private val ticker = object : Runnable {
        override fun run() {
            val player = session?.player ?: return
            if (player.isPlaying) {
                saveCheckpoint(player)
                handler.postDelayed(this, 5_000L)
            }
        }
    }

    private val listener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            if (mediaItem != null) saveCheckpoint(session?.player)
        }

        override fun onPositionDiscontinuity(
            oldPosition: Player.PositionInfo,
            newPosition: Player.PositionInfo,
            reason: Int
        ) {
            saveCheckpoint(session?.player)
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            saveCheckpoint(session?.player)
            handler.removeCallbacks(ticker)
            if (isPlaying) handler.postDelayed(ticker, 5_000L)
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_ENDED) saveCheckpoint(session?.player)
        }
    }

    override fun onCreate() {
        super.onCreate()
        checkpoint = PlaybackResumeStore.forApplication(this)
        val player = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                true
            )
            .setHandleAudioBecomingNoisy(true)
            .build()
        player.addListener(listener)
        session = MediaSession.Builder(this, player)
            .setCallback(object : MediaSession.Callback {
                @OptIn(UnstableApi::class)
                override fun onPlaybackResumption(
                    mediaSession: MediaSession,
                    controller: MediaSession.ControllerInfo,
                    isForPlayback: Boolean
                ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
                    // Media3 invokes this only following an explicit resume request
                    // (or a metadata query). This code never calls play() itself.
                    val state = checkpoint.load()
                    val items = if (state == null) emptyList() else listOf(
                        MediaItem.Builder()
                            .setMediaId(state.mediaId)
                            .setUri(Uri.parse(state.uri))
                            .setMediaMetadata(MediaMetadata.Builder().setTitle(state.title).build())
                            .apply {
                                if (MediaFormatSupport.isWave(state.title)) {
                                    setMimeType("audio/wav")
                                }
                            }
                            .build()
                    )
                    val result = MediaSession.MediaItemsWithStartPosition(
                        items,
                        if (items.isEmpty()) C.INDEX_UNSET else 0,
                        if (items.isEmpty()) C.TIME_UNSET else state!!.positionMs
                    )
                    return Futures.immediateFuture(result)
                }
            })
            .build()
    }

    private fun saveCheckpoint(player: Player?) {
        val playing = player ?: return
        val item = playing.currentMediaItem ?: return
        val uri = item.localConfiguration?.uri?.toString() ?: return
        val position = if (playing.playbackState == Player.STATE_ENDED) 0L
            else playing.currentPosition.coerceAtLeast(0L)
        val state = PlaybackResumePolicy.create(
            mediaId = item.mediaId,
            uri = uri,
            title = item.mediaMetadata.title?.toString(),
            positionMs = position
        ) ?: return
        checkpoint.save(state)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        saveCheckpoint(session?.player)
        val activePlayer = session?.player
        if (activePlayer?.playWhenReady != true || activePlayer.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        handler.removeCallbacks(ticker)
        session?.run {
            saveCheckpoint(player)
            player.removeListener(listener)
            player.release()
            release()
        }
        session = null
        super.onDestroy()
    }
}
