package com.harmonicinc.clientsideadtracking.tracking.client

import android.content.Context
import android.content.Intent
import android.util.Log
import android.view.View
import com.harmonicinc.clientsideadtracking.player.PlayerAdapter
import com.harmonicinc.clientsideadtracking.tracking.AdMetadataTracker
import com.harmonicinc.clientsideadtracking.tracking.AdProgressListener
import com.harmonicinc.clientsideadtracking.tracking.EventLogListener
import com.harmonicinc.clientsideadtracking.tracking.client.omsdk.AdSessionUtil
import com.harmonicinc.clientsideadtracking.tracking.model.Ad
import com.harmonicinc.clientsideadtracking.tracking.model.AdBreak
import com.harmonicinc.clientsideadtracking.tracking.model.AdVerification
import com.harmonicinc.clientsideadtracking.tracking.model.EventLog
import com.harmonicinc.clientsideadtracking.tracking.model.Tracking
import com.harmonicinc.clientsideadtracking.tracking.util.Constants.CSAT_INTENT_LOG_ACTION
import com.iab.omid.library.harmonicinc.Omid
import com.iab.omid.library.harmonicinc.adsession.AdEvents
import com.iab.omid.library.harmonicinc.adsession.AdSession
import com.iab.omid.library.harmonicinc.adsession.CreativeType
import com.iab.omid.library.harmonicinc.adsession.media.MediaEvents
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.net.MalformedURLException
import java.util.concurrent.CopyOnWriteArrayList

class OMSDKClient(
    private val context: Context,
    private val playerAdapter: PlayerAdapter,
    private val playerView: View,
    private val tracker: AdMetadataTracker,
    private val customReferenceData: String?,
    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.Main)
) {
    private var adSession: AdSession? = null
    private var mediaEvents: MediaEvents? = null
    private var adEvents: AdEvents? = null
    private var adVerifications: List<AdVerification>? = null
    private var eventLogListeners: CopyOnWriteArrayList<EventLogListener> = CopyOnWriteArrayList()
    private var currentAdBreak: AdBreak? = null
    private var currentAd: Ad? = null

    private val TAG = "OMSDKClient"

    init {
        Omid.activate(context)
        Omid.updateLastActivity()
        initHandlers()
    }

    fun addEventLogListener(listener: EventLogListener) {
        eventLogListeners.addIfAbsent(listener)
    }
    fun pause() {
        if (isPlayingAd()) {
            Log.d(TAG, "mediaEvents.pause()")
            mediaEvents!!.pause()
            pushEventLog(Tracking.Event.PAUSE)
        }
    }

    fun resume() {
        if (isPlayingAd()) {
            Log.d(TAG, "mediaEvents.resume()")
            mediaEvents!!.resume()
            pushEventLog(Tracking.Event.RESUME)
        }
    }

    fun bufferStart() {
        if (isPlayingAd()) {
            Log.d(TAG, "mediaEvents.bufferStart()")
            mediaEvents!!.bufferStart()
            pushEventLog(Tracking.Event.BUFFER_START)
        }
    }

    fun bufferEnd() {
        if (isPlayingAd()) {
            Log.d(TAG, "mediaEvents.bufferFinish()")
            mediaEvents!!.bufferFinish()
            pushEventLog(Tracking.Event.BUFFER_END)
        }
    }

    fun volumeChange(volume: Float) {
        if (isPlayingAd()) {
            Log.d(TAG, "mediaEvents.volumeChange()")
            mediaEvents!!.volumeChange(volume)
            pushEventLog(Tracking.Event.VOLUME)
        }
    }


    private fun impressionOccurred() {
        preFireEvent(Tracking.Event.IMPRESSION)
        Log.d(TAG, "adEvents.impressionOccurred()")
        adEvents?.impressionOccurred()
        pushEventLog(Tracking.Event.IMPRESSION)
    }

    private fun start() {
        preFireEvent(Tracking.Event.START)
        val duration = playerAdapter.getDuration().toFloat()
        val volume = playerAdapter.getAudioVolume()
        Log.d(TAG, "mediaEvents.start($duration, $volume)")
        mediaEvents?.start(duration, volume)
        pushEventLog(Tracking.Event.START)
    }

    private fun firstQuartile() {
        Log.d(TAG, "mediaEvents.firstQuartile()")
        preFireEvent(Tracking.Event.FIRST_QUARTILE)
        mediaEvents?.firstQuartile()
        pushEventLog(Tracking.Event.FIRST_QUARTILE)
    }

    private fun midpoint() {
        Log.d(TAG, "mediaEvents.midpoint()")
        preFireEvent(Tracking.Event.MIDPOINT)
        mediaEvents?.midpoint()
        pushEventLog(Tracking.Event.MIDPOINT)
    }

    private fun thirdQuartile() {
        Log.d(TAG, "mediaEvents.thirdQuartile()")
        preFireEvent(Tracking.Event.THIRD_QUARTILE)
        mediaEvents?.thirdQuartile()
        pushEventLog(Tracking.Event.THIRD_QUARTILE)
    }

    private fun complete() {
        Log.d(TAG, "mediaEvents.complete()")
        preFireEvent(Tracking.Event.COMPLETE)
        mediaEvents?.complete()
        pushEventLog(Tracking.Event.COMPLETE)
        destroySession()
    }
    private fun onSkipped() {
        Log.d(TAG, "mediaEvents.skipped()")
        mediaEvents?.skipped()
        destroySession()
    }

    private fun destroySession() {
        Log.d(TAG, "Destroying session if any")
        mediaEvents = null
        adSession?.finish()
        adSession = null
        adVerifications = null
        adEvents = null
        currentAdBreak = null
        currentAd = null
    }

    private fun pushEventLog(event: Tracking.Event) {
        val adBreakId = currentAdBreak?.id ?: ""
        val adId = currentAd?.id ?: ""
        coroutineScope.launch {
            val eventLog = EventLog(
                "OMSDK",
                adBreakId,
                adId,
                System.currentTimeMillis(),
                event
            )
            eventLogListeners.forEach { it.onEvent(eventLog)}
            // Fire intent as well
            val intent = Intent(CSAT_INTENT_LOG_ACTION)
            intent.putExtra("message", "[${eventLog.clientTag}] ${eventLog.adBreakId} > ${eventLog.adId} > ${eventLog.event.name}")
            context.sendBroadcast(intent)
        }
    }

    private fun isPlayingAd(): Boolean {
        return adSession != null
    }

    private fun initHandlers() {
        tracker.addAdProgressListener(object : AdProgressListener {
            override fun onAdProgress(currentAdBreak: AdBreak?, currentAd: Ad?, event: Tracking) {
                this@OMSDKClient.currentAdBreak = currentAdBreak
                this@OMSDKClient.currentAd = currentAd
                this@OMSDKClient.adVerifications = currentAd?.adVerifications
                when (event.event) {
                    Tracking.Event.IMPRESSION -> impressionOccurred()
                    Tracking.Event.START -> start()
                    Tracking.Event.FIRST_QUARTILE -> firstQuartile()
                    Tracking.Event.MIDPOINT -> midpoint()
                    Tracking.Event.THIRD_QUARTILE -> thirdQuartile()
                    Tracking.Event.COMPLETE -> complete()

                    // Custom event handlers
                    Tracking.Event.SKIPPED -> this@OMSDKClient.onSkipped()
                    Tracking.Event.STOPPED -> this@OMSDKClient.destroySession()
                    else -> {}
                }
            }
        })

    }

    private fun preFireEvent(event: Tracking.Event) {
        if (adSession != null && event == Tracking.Event.IMPRESSION) {
            // Terminate previous session first
            destroySession()
        }
        if (adSession == null && adVerifications != null) {
            createSession()
        }
    }

    private fun createSession() {
        val verifications = adVerifications
        if (verifications == null) {
            Log.w(TAG, "Cannot create session without ad verifications")
            return
        }
        
        adSession = try {
            AdSessionUtil.getNativeAdSession(
                context,
                customReferenceData,
                CreativeType.VIDEO,
                verifications
            )
        } catch (e: MalformedURLException) {
            Log.d(TAG, "setupAdSession failed", e)
            throw java.lang.UnsupportedOperationException(e)
        }
        mediaEvents = MediaEvents.createMediaEvents(adSession)
        adEvents = AdEvents.createAdEvents(adSession)
        adSession?.registerAdView(playerView)

        adSession?.start()
        adEvents?.loaded()
        Log.d(TAG, "Session created")
    }
}
