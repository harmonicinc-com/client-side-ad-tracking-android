package com.harmonicinc.clientsideadtracking.player

import android.view.MotionEvent
import android.view.View
import java.util.concurrent.CopyOnWriteArrayList

interface PlayerAdapter {
    // Methods to be overridden
    fun getCurrentPositionMs(): Long
    fun getPresentationStartTimeMs(): Long
    fun getPlaybackRate(): Float
    fun getDuration(): Long
    fun getAudioVolume(): Float
    fun isPaused(): Boolean

    // Listeners. No need to override
    val eventListeners: CopyOnWriteArrayList<PlayerEventListener>

    fun addEventListener(listener: PlayerEventListener) {
        eventListeners.addIfAbsent(listener)
    }

    // Helper functions. No need to override
    fun onBufferStart() {
        eventListeners.forEach {
            it.onBufferStart()
        }
    }

    fun onBufferEnd() {
        eventListeners.forEach {
            it.onBufferEnd()
        }
    }

    fun onResume() {
        eventListeners.forEach {
            it.onResume()
        }
    }

    fun onPause() {
        eventListeners.forEach {
            it.onPause()
        }
    }

    fun onVideoAdClick() {
        eventListeners.forEach {
            it.onVideoAdClick()
        }
    }

    fun onVideoAdViewTouch(view: View, event: MotionEvent)  {
        eventListeners.forEach {
            it.onVideoAdViewTouch(view, event)
        }
    }

    fun onVolumeChanged(volume: Float)  {
        eventListeners.forEach {
            it.onVolumeChanged(volume)
        }
    }
}
