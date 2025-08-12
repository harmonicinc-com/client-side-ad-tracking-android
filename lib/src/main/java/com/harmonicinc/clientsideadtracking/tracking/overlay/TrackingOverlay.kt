package com.harmonicinc.clientsideadtracking.tracking.overlay

import android.content.Context
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.github.harmonicinc.clientsideadtracking.R
import com.harmonicinc.clientsideadtracking.player.PlayerAdapter
import com.harmonicinc.clientsideadtracking.tracking.AdBreakListener
import com.harmonicinc.clientsideadtracking.tracking.EventLogListener
import com.harmonicinc.clientsideadtracking.tracking.AdMetadataTracker
import com.harmonicinc.clientsideadtracking.tracking.client.OMSDKClient
import com.harmonicinc.clientsideadtracking.tracking.client.PMMClient
import com.harmonicinc.clientsideadtracking.tracking.model.Ad
import com.harmonicinc.clientsideadtracking.tracking.model.AdBreak
import com.harmonicinc.clientsideadtracking.tracking.model.EventLog
import com.harmonicinc.clientsideadtracking.tracking.model.Tracking
import com.harmonicinc.clientsideadtracking.tracking.util.DashHelper
import com.harmonicinc.clientsideadtracking.tracking.util.OverlayHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class TrackingOverlay(
    context: Context,
    private val playerAdapter: PlayerAdapter,
    overlayViewContainer: ViewGroup?,
    playerView: ViewGroup,
    private val tracker: AdMetadataTracker,
    private val omsdkClient: OMSDKClient?,
    private val pmmClient: PMMClient?,
    private val coroutineScope: CoroutineScope,
) {
    private var updateJob: Job? = null
    private var prevPodId: String? = null
    private var currentAdBreak: AdBreak? = null
    private var currentAd: Ad? = null
    private var currentTracking: List<Tracking>? = null

    private val overlayView: View
    private val layoutController: LayoutController

    private val updateDelayMs: Long = 500

    var showOverlay = false
        set(value) {
            // Ensure UI operations happen on main thread
            coroutineScope.launch {
                overlayView.visibility = if (value) View.VISIBLE else View.INVISIBLE
                field = value
            }
        }

    init {
        val inflater = context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        overlayView = inflater.inflate(R.layout.tracking_event_view, null)
        overlayView.visibility = if (showOverlay) View.VISIBLE else View.INVISIBLE
        
        // Ensure UI operations happen on main thread
        coroutineScope.launch {
            overlayViewContainer?.let {
                OverlayHelper.addViewToContainerView(overlayView, it)
            } ?: run {
                // add to top-level as workaround
                Log.w("TrackingOverlay", "OverlayViewContainer missing")
                OverlayHelper.addViewToContainerView(overlayView, playerView)
            }
        }
        
        layoutController = LayoutController(overlayView, coroutineScope)
        layoutController.start()

        addListeners()
        updateOverlayRunner()
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

        omsdkClient?.addEventLogListener(trackingListener)
        pmmClient?.setListener(trackingListener)
        tracker.addAdBreakListener(adBreakListener)
    }

    private fun updateLayout() {
        val pos = DashHelper.getMpdTimeMs(playerAdapter)
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