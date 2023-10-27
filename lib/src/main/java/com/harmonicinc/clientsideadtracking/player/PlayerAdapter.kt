package com.harmonicinc.clientsideadtracking.player

import java.util.concurrent.CopyOnWriteArrayList

interface PlayerAdapter {
    val eventListeners: CopyOnWriteArrayList<PlayerEventListener>
    fun addEventListener(listener: PlayerEventListener) {
        eventListeners.addIfAbsent(listener)
    }

    fun getCurrentPositionMs(): Long
    fun getPresentationStartTimeMs(): Long
    fun getPlaybackRate(): Float
    fun getDuration(): Long
    fun getAudioVolume(): Float
    fun isPaused(): Boolean
}
