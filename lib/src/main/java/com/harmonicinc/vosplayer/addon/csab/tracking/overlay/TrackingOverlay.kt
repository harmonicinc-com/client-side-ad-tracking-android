package com.harmonicinc.vosplayer.addon.csab.tracking.overlay

import android.content.Context
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import com.harmonicinc.vosplayer.addon.PlayerContext
import com.harmonicinc.vosplayer.addon.csab.R
import com.harmonicinc.vosplayer.addon.csab.tracking.AdBreakListener
import com.harmonicinc.vosplayer.addon.csab.tracking.EventLogListener
import com.harmonicinc.vosplayer.addon.csab.tracking.AdMetadataTracker
import com.harmonicinc.vosplayer.addon.csab.tracking.client.OMSDKClient
import com.harmonicinc.vosplayer.addon.csab.tracking.client.PMMClient
import com.harmonicinc.vosplayer.addon.csab.tracking.model.Ad
import com.harmonicinc.vosplayer.addon.csab.tracking.model.AdBreak
import com.harmonicinc.vosplayer.addon.csab.tracking.model.Tracking
import com.harmonicinc.vosplayer.addon.csab.tracking.util.DashHelper
import com.harmonicinc.vosplayer.addon.csab.tracking.util.OverlayHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class TrackingOverlay(
    private val playerContext: PlayerContext,
    private val tracker: AdMetadataTracker,
    private val omsdkClient: OMSDKClient?,
    private val pmmClient: PMMClient?
) {
    private var updateJob: Job? = null
    private var prevPodId: String? = null
    private var currentAdBreak: AdBreak? = null
    private var currentAd: Ad? = null
    private var currentTracking: List<Tracking>? = null

    private lateinit var overlayView: View
    private lateinit var layoutController: LayoutController

    private val updateDelayMs: Long = 500

    init {
        CoroutineScope(Dispatchers.Main).launch {
            val inflater = playerContext.androidContext!!.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
            overlayView = inflater.inflate(R.layout.tracking_event_view, null)
            overlayView.visibility = View.VISIBLE
            playerContext.overlayViewContainer?.let {
                OverlayHelper.addViewToContainerView(overlayView, it)
            } ?: run {
                // add to top-level as workaround
                Log.w("TrackingOverlay", "OverlayViewContainer missing")
                OverlayHelper.addViewToContainerView(overlayView, playerContext.playerView!!)
            }
            layoutController = LayoutController(overlayView)
            layoutController.start()

            addListeners()
            updateOverlayRunner()
        }
    }

    fun onDestroy() {
        updateJob?.cancel()
    }

    private fun addListeners() {
        val trackingListener = object: EventLogListener {
            override fun onEvent(eventLog: EventLog) {
                layoutController.pushEventLog(eventLog)
            }
        }

        val adBreakListener = object: AdBreakListener {
            override fun onCurrentAdBreakUpdate(pod: AdBreak?) {
                currentAdBreak = pod
            }

            override fun onCurrentAdUpdate(ad: Ad?) {
                currentAd = ad
            }

            override fun onCurrentTrackingUpdate(tracking: List<Tracking>?) {
                currentTracking = tracking
            }
        }

        omsdkClient?.setOverlayListener(trackingListener)
        pmmClient?.setListener(trackingListener)
        tracker.addAdBreakListener(adBreakListener)
    }

    private suspend fun updateLayout() {
        val pos = DashHelper.getMpdTimeMs(playerContext.wrappedPlayer!!)
        layoutController.setRawPlayerPosition(pos / 1000)

        if (currentAdBreak == null) {
            layoutController.setNoPod()
            layoutController.setTimeToNextAd(null)
        } else {
            if (currentAdBreak!!.id != prevPodId) {
                layoutController.setNoPod() // clear previous ad & events
                prevPodId = currentAdBreak!!.id
            }
            // FIXME: Incorrect date time from PMM
            layoutController.setCurrentPod(currentAdBreak!!.id, currentAdBreak!!.startTime, currentAdBreak!!.duration.toLong())
            layoutController.setTimeToNextAd((currentAdBreak!!.startTime - pos) / 1000)
        }

        if (currentAd == null) {
            layoutController.setNoAd()
        } else {
            // FIXME: Incorrect date time from PMM
            layoutController.setCurrentAd(currentAd!!.id, currentAd!!.startTime, currentAd!!.duration.toLong())
        }
    }

    private fun updateOverlayRunner() {
        updateJob?.cancel()
        updateJob = CoroutineScope(Dispatchers.Main).launch {
            while (isActive) {
                updateLayout()
                delay(updateDelayMs)
            }
        }
        updateJob!!.start()
    }
}