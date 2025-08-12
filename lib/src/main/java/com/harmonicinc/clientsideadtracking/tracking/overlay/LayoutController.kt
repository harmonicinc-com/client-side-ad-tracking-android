package com.harmonicinc.clientsideadtracking.tracking.overlay

import android.annotation.SuppressLint
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.TextView
import com.github.harmonicinc.clientsideadtracking.R
import com.harmonicinc.clientsideadtracking.tracking.model.EventLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@SuppressLint("SetTextI18n")
class LayoutController(
    private var overlayView: View,
    private val coroutineScope: CoroutineScope,
) {
    private lateinit var rawPlayerPosition: TextView
    private lateinit var timeToNextAd: TextView

    private lateinit var currentPodLayout: RelativeLayout
    private lateinit var currentPodId: TextView
    private lateinit var currentPodStart: TextView
    private lateinit var currentPodEnd: TextView
    private lateinit var currentPodDuration: TextView

    private lateinit var currentAdLayout: RelativeLayout
    private lateinit var currentAdId: TextView
    private lateinit var currentAdStart: TextView
    private lateinit var currentAdEnd: TextView
    private lateinit var currentAdDuration: TextView

    private lateinit var trackingEventLabel: TextView
    private lateinit var trackingEventLayout: LinearLayout
    // Convert dp to px
    private val Int.dp get()  = (this * overlayView.resources.displayMetrics.density).toInt()

    fun start() {
        rawPlayerPosition = overlayView.findViewById(R.id.raw_position)
        timeToNextAd = overlayView.findViewById(R.id.time_next_ad)

        currentPodLayout = overlayView.findViewById(R.id.current_pod_layout)
        currentPodId = overlayView.findViewById(R.id.current_pod_id)
        currentPodStart = overlayView.findViewById(R.id.current_pod_start)
        currentPodEnd = overlayView.findViewById(R.id.current_pod_end)
        currentPodDuration = overlayView.findViewById(R.id.current_pod_duration)

        currentAdLayout = overlayView.findViewById(R.id.current_ad_layout)
        currentAdId = overlayView.findViewById(R.id.current_ad_id)
        currentAdStart = overlayView.findViewById(R.id.current_ad_start)
        currentAdEnd = overlayView.findViewById(R.id.current_ad_end)
        currentAdDuration = overlayView.findViewById(R.id.current_ad_duration)

        trackingEventLabel = overlayView.findViewById(R.id.tracking_event_label)
        trackingEventLayout = overlayView.findViewById(R.id.tracking_events)
        // Remove those dummy views
        trackingEventLayout.removeAllViews()
    }

    fun setRawPlayerPosition(position: Long) {
        rawPlayerPosition.text = "${position}s"
    }

    fun setTimeToNextAd(time: Long?) {
        if (time != null) {
            timeToNextAd.text = if (time > 0) "${time}s" else "Playing"
        } else {
            timeToNextAd.text = "-"
        }
    }

    fun setNoPod() {
        coroutineScope.launch {
            currentPodLayout.visibility = View.INVISIBLE
        }
    }

    fun setNoAd() {
        coroutineScope.launch {
            currentAdLayout.visibility = View.INVISIBLE
        }
    }

    fun setCurrentPod(podId: String, podStart: Long, podDuration: Long) {
        coroutineScope.launch {
            currentPodLayout.visibility = View.VISIBLE
            currentPodId.text = podId
            currentPodStart.text = "${podStart / 1000}s"
            currentPodEnd.text = "${(podStart + podDuration) / 1000}s"
            currentPodDuration.text = "${podDuration / 1000}s"
        }
    }

    fun setCurrentAd(adId: String, adStart: Long, adDuration: Long) {
        coroutineScope.launch {
            currentAdLayout.visibility = View.VISIBLE
            trackingEventLabel.visibility = View.VISIBLE
            currentAdId.text = adId
            currentAdStart.text = "${adStart / 1000}s"
            currentAdEnd.text = "${(adStart + adDuration) / 1000}s"
            currentAdDuration.text = "${adDuration / 1000}s"
        }
    }

    fun pushEventLog(eventLog: EventLog) {
        coroutineScope.launch {
            // Keep the last 10 only
            if (trackingEventLayout.childCount >= 10) {
                trackingEventLayout.removeViews(0, trackingEventLayout.childCount - 9)
            }

            val eventLayout = RelativeLayout(overlayView.context)
            eventLayout.setPadding(0, 5.dp, 0, 5.dp)
            eventLayout.layoutParams =
                RelativeLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)

            // Event name
            val eventName = TextView(overlayView.context)
            val eventLayoutParams =
                RelativeLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
            eventName.layoutParams = eventLayoutParams
            eventName.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
            eventName.text = "[${eventLog.clientTag}] ${eventLog.adBreakId} > ${eventLog.adId} > ${eventLog.event.name}"

            // Event time
            val eventTime = TextView(overlayView.context)
            eventTime.layoutParams =
                RelativeLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
            eventTime.gravity = Gravity.RIGHT
            eventTime.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
            eventTime.text = formatTime(eventLog.time)

            eventLayout.addView(eventName)
            eventLayout.addView(eventTime)

            trackingEventLayout.addView(eventLayout)
        }
    }

    private fun formatTime(time: Long): String {
        val format = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        return format.format(Date(time))
    }
}