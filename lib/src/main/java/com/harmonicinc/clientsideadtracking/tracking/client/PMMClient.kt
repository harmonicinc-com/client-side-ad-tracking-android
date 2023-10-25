package com.harmonicinc.clientsideadtracking.tracking.client

import com.harmonicinc.clientsideadtracking.player.PlayerAdapter
import com.harmonicinc.clientsideadtracking.tracking.AdMetadataTracker
import com.harmonicinc.clientsideadtracking.tracking.EventLogListener

// Currently unused. Reserved for future implementations
class PMMClient(playerAdapter: PlayerAdapter, private val tracker: AdMetadataTracker) {
    private val TAG: String = "PMMClient"
    private var eventLogListener: EventLogListener? = null

    fun setListener(listener: EventLogListener) {
        eventLogListener = listener
    }

    fun start(url: String) {
        TODO("Not yet implemented")
    }

    fun impressionOccurred(url: String) {
        TODO("Not yet implemented")
    }

    fun firstQuartile(url: String) {
        TODO("Not yet implemented")
    }

    fun midpoint(url: String) {
        TODO("Not yet implemented")
    }

    fun thirdQuartile(url: String) {
        TODO("Not yet implemented")
    }

    fun complete(url: String) {
        TODO("Not yet implemented")
    }

    fun pause(url: String) {
        TODO("Not yet implemented")
    }

    fun resume(url: String) {
        TODO("Not yet implemented")
    }

    fun bufferStart(url: String) {
        TODO("Not yet implemented")
    }

    fun bufferEnd(url: String) {
        TODO("Not yet implemented")
    }

    fun volumeChange(url: String) {
        TODO("Not yet implemented")
    }

    fun isPlayingAd(url: String): Boolean {
        TODO("Not yet implemented")
    }
}