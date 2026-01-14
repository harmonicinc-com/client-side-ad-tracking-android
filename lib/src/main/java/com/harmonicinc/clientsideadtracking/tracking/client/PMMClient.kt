package com.harmonicinc.clientsideadtracking.tracking.client

import android.content.Context
import android.content.Intent
import android.util.Log
import com.harmonicinc.clientsideadtracking.OkHttpService
import com.harmonicinc.clientsideadtracking.error.AdTrackingError
import com.harmonicinc.clientsideadtracking.error.AdTrackingErrorListener
import com.harmonicinc.clientsideadtracking.tracking.AdMetadataTracker
import com.harmonicinc.clientsideadtracking.tracking.AdProgressListener
import com.harmonicinc.clientsideadtracking.tracking.EventLogListener
import com.harmonicinc.clientsideadtracking.tracking.model.Ad
import com.harmonicinc.clientsideadtracking.tracking.model.AdBreak
import com.harmonicinc.clientsideadtracking.tracking.model.EventLog
import com.harmonicinc.clientsideadtracking.tracking.model.Tracking
import com.harmonicinc.clientsideadtracking.tracking.util.Constants.CSAT_INTENT_LOG_ACTION
import com.harmonicinc.clientsideadtracking.tracking.util.Constants.EXTRA_MESSAGE_KEY
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// PMM Client for sending HTTP beacons based on tracking events
class PMMClient(
    private val tracker: AdMetadataTracker,
    private val okHttpService: OkHttpService,
    private val context: Context,
    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.Main),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    private val TAG: String = "PMMClient"
    private var eventLogListener: EventLogListener? = null
    private var errorListener: AdTrackingErrorListener? = null

    private var currentAdBreak: AdBreak? = null
    private var currentAd: Ad? = null

    init {
        initHandlers()
    }

    fun setListener(listener: EventLogListener) {
        eventLogListener = listener
    }

    fun setErrorListener(listener: AdTrackingErrorListener?) {
        errorListener = listener
    }

    private suspend fun sendBeacon(urls: List<String>, event: Tracking.Event) {
        Log.d(TAG, "sendBeacon: sending ${urls.size} beacons for ${event.name}")
        withContext(ioDispatcher) {
            urls.forEach { url ->
                try {
                    Log.d(TAG, "sendBeacon: sending beacon to $url")
                    okHttpService.getString(url)
                    Log.d(TAG, "Beacon sent successfully for ${event.name}: $url")
                } catch (e: Exception) {
                    val error = AdTrackingError.BeaconError(
                        beaconUrl = url,
                        event = event.name,
                        errorMessage = "Failed to send beacon",
                        errorCause = e
                    )
                    errorListener?.onError(error)
                    Log.e(TAG, "Failed to send beacon for ${event.name}: $url", e)
                }
            }
        }
    }

    private fun initHandlers() {
        tracker.addAdProgressListener(object : AdProgressListener {
            override fun onAdProgress(currentAdBreak: AdBreak?, currentAd: Ad?, event: Tracking) {
                this@PMMClient.currentAdBreak = currentAdBreak
                this@PMMClient.currentAd = currentAd
                
                Log.d(TAG, "onAdProgress: currentAd=${currentAd?.id}, event=${event.event}, url=${event.url}, fired=${event.fired}")
                
                if (currentAd != null && event.url.isNotEmpty()) {
                    when (event.event) {
                        Tracking.Event.IMPRESSION -> impressionOccurred(event.url)
                        Tracking.Event.START -> start(event.url)
                        Tracking.Event.FIRST_QUARTILE -> firstQuartile(event.url)
                        Tracking.Event.MIDPOINT -> midpoint(event.url)
                        Tracking.Event.THIRD_QUARTILE -> thirdQuartile(event.url)
                        Tracking.Event.COMPLETE -> complete(event.url)
                        else -> {}
                    }
                } else {
                    Log.d(TAG, "onAdProgress: skipping beacon - currentAd=${currentAd?.id}, urlEmpty=${event.url.isEmpty()}, fired=${event.fired}")
                }
            }
        })
    }

    private fun pushEventLog(event: Tracking.Event) {
        val adBreakId = currentAdBreak?.id ?: ""
        val adId = currentAd?.id ?: ""
        val eventLog = EventLog(
            "PMM",
            adBreakId,
            adId,
            System.currentTimeMillis(),
            event
        )
        
        eventLogListener?.onEvent(eventLog)
        
        val intent = Intent(CSAT_INTENT_LOG_ACTION)
        intent.putExtra(EXTRA_MESSAGE_KEY, "[${eventLog.clientTag}] ${eventLog.adBreakId} > ${eventLog.adId} > ${eventLog.event.name}")
        context.sendBroadcast(intent)
    }

    private fun sendBeaconAndLog(urls: List<String>, event: Tracking.Event) {
        coroutineScope.launch {
            sendBeacon(urls, event)
        }
        pushEventLog(event)
    }

    fun start(urls: List<String>) {
        sendBeaconAndLog(urls, Tracking.Event.START)
    }

    fun impressionOccurred(urls: List<String>) {
        sendBeaconAndLog(urls, Tracking.Event.IMPRESSION)
    }

    fun firstQuartile(urls: List<String>) {
        sendBeaconAndLog(urls, Tracking.Event.FIRST_QUARTILE)
    }

    fun midpoint(urls: List<String>) {
        sendBeaconAndLog(urls, Tracking.Event.MIDPOINT)
    }

    fun thirdQuartile(urls: List<String>) {
        sendBeaconAndLog(urls, Tracking.Event.THIRD_QUARTILE)
    }

    fun complete(urls: List<String>) {
        sendBeaconAndLog(urls, Tracking.Event.COMPLETE)
    }

    // Player-initiated event handlers - fire beacons based on player actions
    /**
     * Call when the player is paused during ad playback.
     * Fires pause beacons if available in the current ad's tracking data.
     */
    fun onPlayerPause() {
        val urls = tracker.getTrackingUrlsForEvent(Tracking.Event.PAUSE)
        if (urls.isNotEmpty()) {
            currentAdBreak = tracker.getCurrentAdBreak()
            currentAd = tracker.getCurrentAd()
            sendBeaconAndLog(urls, Tracking.Event.PAUSE)
        }
    }

    /**
     * Call when the player resumes after being paused during ad playback.
     * Fires resume beacons if available in the current ad's tracking data.
     */
    fun onPlayerResume() {
        val urls = tracker.getTrackingUrlsForEvent(Tracking.Event.RESUME)
        if (urls.isNotEmpty()) {
            currentAdBreak = tracker.getCurrentAdBreak()
            currentAd = tracker.getCurrentAd()
            sendBeaconAndLog(urls, Tracking.Event.RESUME)
        }
    }

    /**
     * Call when the player is muted during ad playback.
     * Fires mute beacons if available in the current ad's tracking data.
     */
    fun onPlayerMute() {
        val urls = tracker.getTrackingUrlsForEvent(Tracking.Event.MUTE)
        if (urls.isNotEmpty()) {
            currentAdBreak = tracker.getCurrentAdBreak()
            currentAd = tracker.getCurrentAd()
            sendBeaconAndLog(urls, Tracking.Event.MUTE)
        }
    }

    /**
     * Call when the player is unmuted during ad playback.
     * Fires unmute beacons if available in the current ad's tracking data.
     */
    fun onPlayerUnmute() {
        val urls = tracker.getTrackingUrlsForEvent(Tracking.Event.UNMUTE)
        if (urls.isNotEmpty()) {
            currentAdBreak = tracker.getCurrentAdBreak()
            currentAd = tracker.getCurrentAd()
            sendBeaconAndLog(urls, Tracking.Event.UNMUTE)
        }
    }

    /**
     * Call when the player rewinds during ad playback.
     * Fires rewind beacons if available in the current ad's tracking data.
     */
    fun onPlayerRewind() {
        val urls = tracker.getTrackingUrlsForEvent(Tracking.Event.REWIND)
        if (urls.isNotEmpty()) {
            currentAdBreak = tracker.getCurrentAdBreak()
            currentAd = tracker.getCurrentAd()
            sendBeaconAndLog(urls, Tracking.Event.REWIND)
        }
    }

    /**
     * Call when the ad is skipped during ad playback.
     * Fires skip beacons if available in the current ad's tracking data.
     */
    fun onPlayerSkip() {
        val urls = tracker.getTrackingUrlsForEvent(Tracking.Event.SKIP)
        if (urls.isNotEmpty()) {
            currentAdBreak = tracker.getCurrentAdBreak()
            currentAd = tracker.getCurrentAd()
            sendBeaconAndLog(urls, Tracking.Event.SKIP)
        }
    }

    /**
     * Call when the player is expanded (e.g., enters fullscreen) during ad playback.
     * Fires playerExpand beacons if available in the current ad's tracking data.
     */
    fun onPlayerExpand() {
        val urls = tracker.getTrackingUrlsForEvent(Tracking.Event.PLAYER_EXPAND)
        if (urls.isNotEmpty()) {
            currentAdBreak = tracker.getCurrentAdBreak()
            currentAd = tracker.getCurrentAd()
            sendBeaconAndLog(urls, Tracking.Event.PLAYER_EXPAND)
        }
    }

    /**
     * Call when the player is collapsed (e.g., exits fullscreen) during ad playback.
     * Fires playerCollapse beacons if available in the current ad's tracking data.
     */
    fun onPlayerCollapse() {
        val urls = tracker.getTrackingUrlsForEvent(Tracking.Event.PLAYER_COLLAPSE)
        if (urls.isNotEmpty()) {
            currentAdBreak = tracker.getCurrentAdBreak()
            currentAd = tracker.getCurrentAd()
            sendBeaconAndLog(urls, Tracking.Event.PLAYER_COLLAPSE)
        }
    }
}