package com.harmonicinc.clientsideadtracking.tracking.client

import android.content.Context
import android.content.Intent
import android.util.Log
import com.harmonicinc.clientsideadtracking.OkHttpService
import com.harmonicinc.clientsideadtracking.tracking.AdMetadataTracker
import com.harmonicinc.clientsideadtracking.tracking.AdProgressListener
import com.harmonicinc.clientsideadtracking.tracking.EventLogListener
import com.harmonicinc.clientsideadtracking.tracking.model.Ad
import com.harmonicinc.clientsideadtracking.tracking.model.AdBreak
import com.harmonicinc.clientsideadtracking.tracking.model.EventLog
import com.harmonicinc.clientsideadtracking.tracking.model.Tracking
import com.harmonicinc.clientsideadtracking.tracking.util.Constants.CSAT_INTENT_LOG_ACTION
import com.harmonicinc.clientsideadtracking.tracking.util.Constants.EXTRA_MESSAGE_KEY
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// PMM Client for sending HTTP beacons based on tracking events
class PMMClient(
    private val tracker: AdMetadataTracker,
    private val okHttpService: OkHttpService,
    private val context: Context
) {
    private val TAG: String = "PMMClient"
    private var eventLogListener: EventLogListener? = null
    private val coroutineScope = CoroutineScope(Dispatchers.Main)
    
    private var currentAdBreak: AdBreak? = null
    private var currentAd: Ad? = null

    init {
        initHandlers()
    }

    fun setListener(listener: EventLogListener) {
        eventLogListener = listener
    }

    private suspend fun sendBeacon(urls: List<String>, event: Tracking.Event) {
        Log.d(TAG, "sendBeacon: sending ${urls.size} beacons for ${event.name}")
        withContext(Dispatchers.IO) {
            urls.forEach { url ->
                try {
                    Log.d(TAG, "sendBeacon: sending beacon to $url")
                    okHttpService.getString(url)
                    Log.d(TAG, "Beacon sent successfully for ${event.name}: $url")
                } catch (e: Exception) {
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
                
                if (currentAd != null && event.url.isNotEmpty() && !event.fired) {
                    when (event.event) {
                        Tracking.Event.IMPRESSION -> impressionOccurred(event.url)
                        Tracking.Event.START -> start(event.url)
                        Tracking.Event.FIRST_QUARTILE -> firstQuartile(event.url)
                        Tracking.Event.MIDPOINT -> midpoint(event.url)
                        Tracking.Event.THIRD_QUARTILE -> thirdQuartile(event.url)
                        Tracking.Event.COMPLETE -> complete(event.url)
                        else -> {}
                    }
                    event.fired = true
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
}