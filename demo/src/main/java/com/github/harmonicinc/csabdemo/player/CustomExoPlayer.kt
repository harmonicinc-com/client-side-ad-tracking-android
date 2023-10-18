package com.github.harmonicinc.csabdemo.player

import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.dash.manifest.DashManifest
import androidx.media3.exoplayer.hls.HlsManifest
import com.harmonicinc.clientsideadtracking.player.baseplayer.AbstractPlayer
import java.util.Date

@UnstableApi class CustomExoPlayer(
    private var player: ExoPlayer
): AbstractPlayer() {
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