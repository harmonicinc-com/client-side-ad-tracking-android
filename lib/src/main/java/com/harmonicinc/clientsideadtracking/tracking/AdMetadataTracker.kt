package com.harmonicinc.clientsideadtracking.tracking

import android.util.Log
import androidx.annotation.VisibleForTesting
import com.harmonicinc.clientsideadtracking.OkHttpService
import com.harmonicinc.clientsideadtracking.player.PlayerAdapter
import com.harmonicinc.clientsideadtracking.tracking.model.Ad
import com.harmonicinc.clientsideadtracking.tracking.model.AdBreak
import com.harmonicinc.clientsideadtracking.tracking.model.EventManifest
import com.harmonicinc.clientsideadtracking.tracking.model.Tracking
import com.harmonicinc.clientsideadtracking.tracking.util.AdMetadataLoader
import com.harmonicinc.clientsideadtracking.tracking.util.DashHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicReference

class AdMetadataTracker(
    private val playerAdapter: PlayerAdapter,
    private val okHttpService: OkHttpService,
    private val coroutineIOScope: CoroutineScope = CoroutineScope(Dispatchers.IO),
    private val coroutineMainScope: CoroutineScope = CoroutineScope(Dispatchers.Main),
) {
    private var progressJob: Job? = null
    private var metadataUpdateJob: Job? = null

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    var curEvent: Tracking.Event = Tracking.Event.UNKNOWN

    private var currentAdBreak: AdBreak? = null
    private var currentAd: Ad? = null
    private var currentTracking: List<Tracking>? = null
    private var eventRef : AtomicReference<EventManifest> = AtomicReference()

    private val adBreakListeners = CopyOnWriteArrayList<AdBreakListener>()
    private val adProgressListeners = CopyOnWriteArrayList<AdProgressListener>()


    private val NORMAL_PLAYBACK_SPEED_RANGE = 0.95..1.05
    private val TAG = "AdMetadataTracker"

    // Max offset to fire event
    private val EVENT_FIRING_TIME_RANGE_LOWER_LIMIT_MS = 1000
    private val EVENT_FIRING_TIME_RANGE_UPPER_LIMIT_MS = 1000
    // Assume ad break ends later than advertised due to delayed metadata response
    // If the value is too small, then the tracker thinks the ad break has ended and will destroy the session
    private val ADBREAK_END_TIME_TOLERANCE_MS = 500
    private val METADATA_FETCH_INTERVAL_MS = 2000L
    private val POST_PROGRESS_INTERVAL_MS = 100L

    // Should only be triggered once after user attempts to play an asset
    fun onPlay(metadataUrl: String, sessionId: String) {
        startMetadataUpdateJob(metadataUrl, sessionId)
        postProgress()
    }

    // Should be called when player closed
    fun onStopped() {
        adProgressListeners.forEach { it.onAdProgress(null, null, Tracking(Tracking.Event.SKIPPED)) }
        progressJob?.cancel()
        metadataUpdateJob?.cancel()
    }

    fun addAdBreakListener(listener: AdBreakListener) {
        adBreakListeners.addIfAbsent(listener)
    }

    fun addAdProgressListener(listener: AdProgressListener) {
        adProgressListeners.addIfAbsent(listener)
    }

    fun isPlayingAd(): Boolean {
        return currentAd != null
    }

    private fun startMetadataUpdateJob(metadataUrl: String, sessionId: String) {
        metadataUpdateJob?.cancel()
        metadataUpdateJob = coroutineIOScope.launch {
            while (isActive) {
                try {
                    val mpdTime = withContext(coroutineMainScope.coroutineContext) {
                        DashHelper.getMpdTimeMs(playerAdapter)
                    }
                    eventRef.set(AdMetadataLoader.load(okHttpService, metadataUrl, sessionId, mpdTime))
                } catch (e: Exception) {
                    Log.e(TAG, "Unable to get metadata due to error: ${e.message}")
                    // Ignore error and keep retrying
                } finally {
                    delay(METADATA_FETCH_INTERVAL_MS)
                }
            }
        }
    }

    private fun postProgress() {
        progressJob?.cancel()
        progressJob = coroutineMainScope.launch {
            while (isActive) {
                delay(POST_PROGRESS_INTERVAL_MS)
                val mpdTime = DashHelper.getMpdTimeMs(playerAdapter)
                updateCurrentAds(mpdTime)
                onProgress(mpdTime)
            }
        }
    }

    private fun updateCurrentAds(mpdTime: Long) {
        val event = eventRef.get()
        if (event == null) {
            Log.w(TAG, "No event metadata available")
            return
        }
        // Update sequence: Pod > Ad > Tracking
        // Update current pod
        val prevAdBreak = currentAdBreak
        currentAdBreak = null
        for (i in event.adBreaks.indices) {
            val pod = event.adBreaks[i]
            if (mpdTime < pod.startTime + pod.duration.toLong() + ADBREAK_END_TIME_TOLERANCE_MS) {
                currentAdBreak = pod
                adBreakListeners.forEach { it.onCurrentAdBreakUpdate(currentAdBreak) }
                break
            }
        }

        // Update current ad
        if (currentAdBreak == null) {
            if (prevAdBreak?.id != null) {
                currentAd = null
                currentTracking = null
                adBreakListeners.forEach {
                    it.onCurrentAdBreakUpdate(null)
                    it.onCurrentAdUpdate(null)
                    it.onCurrentTrackingUpdate(null)
                }
                adProgressListeners.forEach { it.onAdProgress(null, null, Tracking(Tracking.Event.STOPPED)) }
            }
            return
        }

        val ads = currentAdBreak!!.ads
        val prevAd = currentAd
        var tempTracking: List<Tracking>? = null
        currentAd = null

        for (j in ads.indices) {
            val ad = ads[j]
            if (mpdTime in ad.startTime .. ad.startTime + ad.duration.toLong() + ADBREAK_END_TIME_TOLERANCE_MS) {
                currentAd = ad
                tempTracking = ad.tracking.filter { tracking ->
                    tracking.event != Tracking.Event.CLICK_TRACKING && tracking.event != Tracking.Event.CLICK_ABSTRACT_TYPE
                }
                // Need to fire update as end time & duration changes
                adBreakListeners.forEach {
                    it.onCurrentAdUpdate(currentAd)
                }
                break
            }
        }
        if (currentAd?.id != prevAd?.id) {
            currentTracking = tempTracking
            adBreakListeners.forEach {
                it.onCurrentAdUpdate(currentAd)
                it.onCurrentTrackingUpdate(currentTracking)
            }
            if (currentAd == null) {
                adProgressListeners.forEach { it.onAdProgress(null, null, Tracking(Tracking.Event.STOPPED)) }
            }
        }
    }

    private fun onProgress(mpdTime: Long) {
        if (currentTracking == null) return

        // Check if playing at normal speed. We should not fire any beacon if player is fast-forwarding/rewinding
        val isPlayingAtNormalSpeed = playerAdapter.getPlaybackRate() in NORMAL_PLAYBACK_SPEED_RANGE
        if (!isPlayingAtNormalSpeed) return

        for (i in 0 until currentTracking!!.size) {
            val event = currentTracking!![i]
            val eventTime = event.startTime
            if (eventTime in mpdTime - EVENT_FIRING_TIME_RANGE_LOWER_LIMIT_MS .. mpdTime + EVENT_FIRING_TIME_RANGE_UPPER_LIMIT_MS && !event.fired) {
                Log.d(TAG, "Got event update at position $mpdTime current Event: $curEvent new Event: ${event.event} ")
                adProgressListeners.forEach { client -> client.onAdProgress(currentAdBreak!!, currentAd!!, event) }
                event.fired = true
                curEvent = event.event
            }
        }
    }
}