package com.harmonicinc.clientsideadtracking.player

import java.util.concurrent.CopyOnWriteArrayList

class MockPlayerAdapter: PlayerAdapter {
    private var currentPositionMs = 0L
    private var presentationStartTimeMs = 0L
    private var playbackRate = 1f
    private var duration = 0L
    private var audioVolume = 0f
    private var isPaused = false

    override val eventListeners = CopyOnWriteArrayList<PlayerEventListener>()

    override fun getCurrentPositionMs(): Long {
        return currentPositionMs
    }

    override fun getPresentationStartTimeMs(): Long {
        return presentationStartTimeMs
    }

    override fun getPlaybackRate(): Float {
        return playbackRate
    }

    override fun getDuration(): Long {
        return duration
    }

    override fun getAudioVolume(): Float {
        return audioVolume
    }

    override fun isPaused(): Boolean {
        return isPaused
    }

    fun setCurrentPositionMs(v: Long) {
        currentPositionMs = v
    }

    fun setPresentationStartTimeMs(v: Long) {
        presentationStartTimeMs = v
    }

    fun setPlaybackRate(v: Float) {
        playbackRate = v
    }

    fun setDuration(v: Long) {
        duration = v
    }

    fun setAudioVolume(v: Float) {
        audioVolume = v
    }

    fun setPaused(v: Boolean) {
        isPaused = v
    }
}