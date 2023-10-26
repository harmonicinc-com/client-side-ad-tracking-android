package com.harmonicinc.clientsideadtracking

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.view.View.OnClickListener
import android.view.View.OnTouchListener
import android.view.ViewGroup
import com.android.volley.DefaultRetryPolicy
import com.android.volley.Request
import com.android.volley.ServerError
import com.android.volley.toolbox.BaseHttpStack
import com.android.volley.toolbox.HurlStack
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley
import com.google.ads.interactivemedia.pal.ConsentSettings
import com.google.ads.interactivemedia.pal.NonceLoader
import com.google.ads.interactivemedia.pal.NonceManager
import com.google.ads.interactivemedia.pal.NonceRequest
import com.harmonicinc.clientsideadtracking.player.PlayerAdapter
import com.harmonicinc.clientsideadtracking.tracking.AdMetadataTracker
import com.harmonicinc.clientsideadtracking.tracking.adchoices.AdChoiceManager
import com.harmonicinc.clientsideadtracking.tracking.client.OMSDKClient
import com.harmonicinc.clientsideadtracking.tracking.client.PMMClient
import com.harmonicinc.clientsideadtracking.tracking.overlay.TrackingOverlay
import com.harmonicinc.clientsideadtracking.tracking.util.Constants
import com.harmonicinc.clientsideadtracking.tracking.util.Constants.PAL_NONCE_QUERY_PARAM_KEY
import com.harmonicinc.clientsideadtracking.tracking.util.Constants.SESSION_ID_QUERY_PARAM_KEY
import com.iab.omid.library.harmonicinc.Omid
import kotlinx.coroutines.tasks.await
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

class AdTrackingManager(
    private val androidContext: Context,
    private val params: AdTrackingManagerParams,
    httpStack: BaseHttpStack,
    private val nonceLoader: NonceLoader
) {
    private var TAG = "AdTrackingManager"
    private var sessionId: String? = null
    private var ssaiSupported = false
    private var manifestUrl: String? = null
    private var omsdkClient: OMSDKClient? = null
    private var pmmClient: PMMClient? = null

    private val urlFilenameRegex = Regex("[^/\\\\&?]+\\.\\w{3,4}(?=([?&].*\$|\$))")
    private val queue = Volley.newRequestQueue(androidContext, httpStack)
    private lateinit var trackingOverlay: TrackingOverlay
    private lateinit var adChoiceManager: AdChoiceManager
    private lateinit var nonceManager: NonceManager
    private lateinit var metadataTracker: AdMetadataTracker

    constructor(androidContext: Context, params: AdTrackingManagerParams) : this(
        androidContext,
        params,
        object : HurlStack() {
            override fun createConnection(url: URL?): HttpURLConnection {
                val connection = super.createConnection(url)
                connection.instanceFollowRedirects = false
                return connection
            }
        },
        NonceLoader(
            androidContext, ConsentSettings.builder()
                .allowStorage(true)
                .build()
        )
    )

    suspend fun prepareBeforeLoad(manifestUrl: String) {
        this.manifestUrl = manifestUrl
        sessionId = getSessionId(manifestUrl)
        if (sessionId == null) {
            Log.w(TAG, "Unsupported SSAI stream")
            return
        }
        ssaiSupported = true

        // Nonce manager must be initialized before playback
        generateNonceForAdRequest()
    }

    fun onPlay(
        context: Context,
        playerAdapter: PlayerAdapter,
        overlayViewContainer: ViewGroup?,
        playerView: ViewGroup
    ) {
        Log.i(TAG, "Ad Tracking manager starting")
        // Player is available only after this point
        // Init tracking client
        metadataTracker = AdMetadataTracker(playerAdapter, queue)
        omsdkClient = OMSDKClient(context, playerAdapter, playerView, metadataTracker)
        pmmClient = PMMClient(playerAdapter, metadataTracker)
        trackingOverlay = TrackingOverlay(context, playerAdapter, overlayViewContainer, playerView, metadataTracker, omsdkClient, pmmClient)
        adChoiceManager = AdChoiceManager(context, overlayViewContainer, playerView, metadataTracker)

        if (manifestUrl == null) {
            Log.e(TAG, "Manifest URL not set. Unable to start metadata tracker & overlay")
            return
        }

        val metadataUrl = urlFilenameRegex.replace(this.manifestUrl!!, "metadata")
        metadataTracker.onPlay(metadataUrl, sessionId!!)

        sendPlaybackStart()
        Log.i(TAG, "Ad Tracking manager started")
    }

    fun cleanupAfterStop() {
        trackingOverlay.onDestroy()
        metadataTracker.onStopped()

        if (ssaiSupported) {
            sendPlaybackEnd()
        }
        Log.i(TAG, "Ad Tracking manager stopped")
    }

    fun appendNonceToUrl(urls: List<String>): List<String> {
        return urls.map {
            val builder = Uri.parse(it).buildUpon()
            builder
                .appendQueryParameter(PAL_NONCE_QUERY_PARAM_KEY, nonceManager.nonce)
                .appendQueryParameter(SESSION_ID_QUERY_PARAM_KEY, sessionId)
                .build()
                .toString()
        }
    }

    fun isSSAISupported(): Boolean {
        return ssaiSupported
    }

    private suspend fun generateNonceForAdRequest() {
        Log.d(TAG, "Generating nonce for ad request")

        val nonceRequest = NonceRequest.builder()
            .descriptionURL(params.descriptionUrl)
            .iconsSupported(params.iconSupported)
            .omidVersion(Omid.getVersion())
//            .omidPartnerVersion(com.harmonicinc.vosplayer.BuildConfig.VERSION_NAME)
//            .omidPartnerName(BuildConfig.PARTNER_NAME)
            .playerType(params.playerType)
            .playerVersion(params.playerVersion)
            .ppid(params.ppid)
            .sessionId(sessionId!!)
            .supportedApiFrameworks(params.supportedApiFrameworks)
            .videoPlayerHeight(params.playerHeight)
            .videoPlayerWidth(params.playerWidth)
            .willAdAutoPlay(params.willAdPlayMuted)
            .willAdPlayMuted(params.willAdPlayMuted)
            .continuousPlayback(params.continuousPlayback)
            .build()

        nonceManager = nonceLoader.loadNonceManager(nonceRequest).await()
        Log.d(TAG, "Generated nonce for ad request")
    }

    private fun sendPlaybackStart() {
        Log.d(TAG, "Sending playback start event")
        nonceManager.sendPlaybackStart()
        pushEventLog("sendPlaybackStart")
    }

    private fun sendPlaybackEnd() {
        Log.d(TAG, "Sending playback end event")
        nonceManager.sendPlaybackEnd()
        pushEventLog("sendPlaybackEnd")
    }

    // No effect on Android TV?
    // Called each time the viewer clicks an ad
    private val onVideoAdClick = OnClickListener {
        // Trigger on click only if ad is playing
        if (metadataTracker.isPlayingAd() == true) {
            Log.d(TAG, "Sending ad click event")
            nonceManager.sendAdClick()
            pushEventLog("sendAdClick")
        }
    }

    // No effect on Android TV?
    // Called on every touch interaction with the player
    private val onVideoAdViewTouch = OnTouchListener { view, event ->
        view?.performClick()
        if (metadataTracker.isPlayingAd() == true) {
            Log.d(TAG, "Sending on touch event")
            nonceManager.sendAdTouch(event)
            pushEventLog("sendAdTouch")
        }
        false
    }

    private fun pushEventLog(event: String) {
        // Fire intent
        val intent = Intent(Constants.CSAT_INTENT_LOG_ACTION)
        intent.putExtra("message", "[PAL] $event")
        androidContext.sendBroadcast(intent)
    }

    private suspend fun getSessionId(manifestUrl: String): String? = suspendCoroutine { cont ->
        try {
            val stringRequest = StringRequest(Request.Method.GET, manifestUrl, {
                Log.d(TAG, it)
                cont.resume(null)
            }, { e ->
                if (e is ServerError && (e.networkResponse.statusCode == HttpURLConnection.HTTP_MOVED_PERM || e.networkResponse.statusCode == HttpURLConnection.HTTP_MOVED_TEMP) && e.networkResponse.headers?.contains(
                        "location"
                    ) == true
                ) {
                    val location = e.networkResponse.headers!!["location"] as String
                    // Assume there's one param only, and named "sessId"
                    val sessIdSplitList = location.split("$SESSION_ID_QUERY_PARAM_KEY=")
                    cont.resume(if (sessIdSplitList.size != 2) null else sessIdSplitList.last())
                } else {
                    cont.resumeWithException(e)
                }
            })
            stringRequest.retryPolicy = DefaultRetryPolicy(5000, 1, 1f)
            queue.add(stringRequest)
        } catch (e: Exception) {
            cont.resumeWithException(e)
        }
    }
}