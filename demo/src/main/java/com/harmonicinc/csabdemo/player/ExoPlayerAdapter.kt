package com.harmonicinc.csabdemo.player

import android.media.session.PlaybackState
import android.util.Log
import androidx.media3.common.Player
import androidx.media3.common.Player.STATE_BUFFERING
import androidx.media3.common.Player.STATE_READY
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.dash.manifest.DashManifest
import androidx.media3.exoplayer.hls.HlsManifest
import com.harmonicinc.clientsideadtracking.player.PlayerAdapter
import java.util.Date

@UnstableApi class ExoPlayerAdapter(
    private var player: ExoPlayer
): PlayerAdapter() {
    private var previousPlaybackState = PlaybackState.STATE_NONE

    private val tag = "CustomExoPlayer"

    private val playerEventListener: Player.Listener = object : Player.Listener {
        // Need to handle buffer start/end only
        override fun onPlaybackStateChanged(playbackState: Int) {
            val playWhenReady = player.playWhenReady
            Log.d(
                tag,
                "onPlayerStateChanged: playWhenReady=${playWhenReady} playbackState=${playbackState}"
            )

            if (!playWhenReady) return
            when (playbackState) {
                STATE_BUFFERING -> {
                    eventListeners.forEach {
                        it.onBufferStart()
                    }
                }
                STATE_READY -> {
                    if (previousPlaybackState == STATE_BUFFERING) {
                        eventListeners.forEach {
                            it.onBufferEnd()
                        }
                    }
                }
                else -> {}
            }
            previousPlaybackState = playbackState
            Log.d(tag, "finished handling onPlayerStateChanged")
        }

        // Handle play/pause only
        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            eventListeners.forEach {
                if (playWhenReady) it.onResume() else it.onPause()
            }
        }
    }

    init {
        player.addListener(playerEventListener)
    }

    override fun getCurrentPositionMs(): Long {
        return player.currentPosition
    }

    override fun getPresentationStartTimeMs(): Long {
        val manifest = player.currentManifest
        val currentWindow = player.currentTimeline.getWindow(
            player.currentMediaItemIndex,
            Timeline.Window()
        )
        return when (manifest) {
            is DashManifest -> {
                val manifestPeriod = manifest.getPeriod(currentWindow.firstPeriodIndex)
                manifestPeriod.startMs + currentWindow.positionInFirstPeriodMs
            }
            is HlsManifest -> {
                val startingProgramDateTime = if (currentWindow.windowStartTimeMs >= 0) Date(currentWindow.windowStartTimeMs) else null
                startingProgramDateTime?.time ?: 0
            }
            else -> -1
        }
    }

    override fun getPlaybackRate(): Float {
        return player.playbackParameters.speed
    }

    override fun getDuration(): Long {
        return player.duration
    }

    override fun getAudioVolume(): Float {
        return player.volume
    }

    override fun isPaused(): Boolean {
        return !player.playWhenReady
    }
}