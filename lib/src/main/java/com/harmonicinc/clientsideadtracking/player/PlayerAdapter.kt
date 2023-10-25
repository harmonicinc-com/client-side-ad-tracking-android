package com.harmonicinc.clientsideadtracking.player

import java.util.concurrent.CopyOnWriteArrayList

abstract class PlayerAdapter {
    val eventListeners: CopyOnWriteArrayList<PlayerEventListener> = CopyOnWriteArrayList()
    fun addEventListener(listener: PlayerEventListener) {
        eventListeners.addIfAbsent(listener)
    }

    abstract fun getCurrentPositionMs(): Long
    abstract fun getPresentationStartTimeMs(): Long
    abstract fun getPlaybackRate(): Float
    abstract fun getDuration(): Long
    abstract fun getAudioVolume(): Float
    abstract fun isPaused(): Boolean
}
