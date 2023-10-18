package com.harmonicinc.clientsideadtracking.player.baseplayer

import java.util.concurrent.CopyOnWriteArrayList

abstract class AbstractPlayer {
    private val eventListeners: CopyOnWriteArrayList<CorePlayerEventListener> = CopyOnWriteArrayList()
    fun addEventListener(listener: CorePlayerEventListener) {
        eventListeners.addIfAbsent(listener)
    }

    abstract fun getCurrentPositionMs(): Long
    abstract fun getPresentationStartTimeMs(): Long
    abstract fun getPlaybackRate(): Float
    abstract fun getDuration(): Long
    abstract fun getAudioVolume(): Float
    abstract fun isPaused(): Boolean
}
