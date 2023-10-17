package com.harmonicinc.vosplayer.baseplayer

import com.harmonicinc.vosplayer.DvrWindowInfo
import java.util.concurrent.CopyOnWriteArrayList

abstract class AbstractPlayer {
    val eventListeners: CopyOnWriteArrayList<CorePlayerEventListener> = CopyOnWriteArrayList()
    fun addEventListener(listener: CorePlayerEventListener) {
        eventListeners.addIfAbsent(listener)
    }

    fun removeEventListener(listener: CorePlayerEventListener) {
        eventListeners.remove(listener)
    }

    abstract fun init()
    abstract fun isPaused(): Boolean
    abstract fun isEnded(): Boolean
    abstract fun clearGraphicOverlay()
    abstract fun monitorGraphicOverlayChange()

    abstract fun getSourceUri(): String

    abstract fun load(mediaInfo: Any)
    abstract fun stop()
    abstract fun pause()
    abstract fun play()
    abstract fun seekTo(time: Any, showController:Boolean = true )
    abstract fun seekToWindowPosition(positionMs: Long)
    abstract suspend fun getCurrentPositionInMS(): Long
    abstract fun getLiveLatencyInMS(): Long
    abstract fun getDvrWindow(): DvrWindowInfo?
    abstract fun getPlayhead(): Any?
    abstract fun getMediaDurationMs(): Long
    abstract fun getBitrate(): Double
    abstract fun getThroughput(): Double
    abstract fun getWidth(): Int
    abstract fun getHeight(): Int
    abstract fun getPlaybackRate(): Double
    abstract fun getThumbnailTileInfo(): Any?

    abstract fun getScte35EventsInCurrentPeriod(): List<Any>
    abstract fun getAllScte35Events(): List<Any>

    open fun setRepeatMode(repeatMode: Int) {}

    open fun getPresentationLatencyInfo(): Any? {
        // default no-op
        return null
    }

    open fun getSubtitleTrackController(): Any? {
        return null
    }

    open fun setPreferredSubtitleLanguage(language: String?) {
        // default no-op
    }

    open fun isLiveStream(): Boolean {
        return false
    }

    open fun getDuration(): Long {
        return 0
    }

    open fun setVideoRendererEnabled(enabled: Boolean) {
        // default no-op
    }

    open fun setAudioVolume(volume: Float) {
        // default no-op
    }

    open fun getAudioVolume(): Float {
        return 0f
    }

    open fun getBufferAheadMs(): Long? {
        return null
    }

    open fun setTargetLiveLatencyOverride(offsetMs: Long?) {}

    open fun getTargetLiveLatencyOverride(): Long {
        return 0
    }

    open fun getManifestTargetLiveLatency(): Long {
        return 0
    }

    open fun getMinimumLiveLatency(): Long {
        return 0
    }

    open fun getMaximumLiveLatency(): Long {
        return 0
    }
}
