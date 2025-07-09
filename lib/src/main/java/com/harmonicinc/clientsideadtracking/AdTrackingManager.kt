package com.harmonicinc.clientsideadtracking

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.annotation.VisibleForTesting
import androidx.core.net.toUri
import com.google.ads.interactivemedia.pal.ConsentSettings
import com.google.ads.interactivemedia.pal.NonceLoader
import com.google.ads.interactivemedia.pal.NonceManager
import com.google.ads.interactivemedia.pal.NonceRequest
import com.google.android.tv.ads.SignalCollector
import com.harmonicinc.clientsideadtracking.player.PlayerAdapter
import com.harmonicinc.clientsideadtracking.player.PlayerEventListener
import com.harmonicinc.clientsideadtracking.tracking.AdMetadataTracker
import com.harmonicinc.clientsideadtracking.tracking.adchoices.AdChoiceManager
import com.harmonicinc.clientsideadtracking.tracking.client.OMSDKClient
import com.harmonicinc.clientsideadtracking.tracking.client.PMMClient
import com.harmonicinc.clientsideadtracking.tracking.overlay.TrackingOverlay
import com.harmonicinc.clientsideadtracking.tracking.util.Constants
import com.harmonicinc.clientsideadtracking.tracking.util.Constants.PAL_NONCE_QUERY_PARAM_KEY
import com.harmonicinc.clientsideadtracking.tracking.util.Constants.SESSION_ID_QUERY_PARAM_KEY
import com.iab.omid.library.harmonicinc.Omid
import java.net.URL
import kotlinx.coroutines.tasks.await

class AdTrackingManager(
    private val androidContext: Context,
    private val nonceLoader: NonceLoader,
    private val okHttpService: OkHttpService
) {
    private var TAG = "AdTrackingManager"
    private var sessionId: String? = null
    private var ssaiSupported = false
    private var manifestUrl: String? = null
    private var metadataUrl: String? = null
    private var obtainedQueryParams = mutableMapOf<String, String>()

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    var omsdkClient: OMSDKClient? = null
    private var pmmClient: PMMClient? = null
    private var showOverlay = false

    private val urlFilenameRegex = Regex("[^/\\\\&?]+\\.\\w{3,4}(?=([?&].*\$|\$))")
    private val signalCollector = SignalCollector()

    private lateinit var trackingOverlay: TrackingOverlay
    private lateinit var adChoiceManager: AdChoiceManager
    private lateinit var nonceManager: NonceManager

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    lateinit var metadataTracker: AdMetadataTracker

    private lateinit var params: AdTrackingManagerParams
    private lateinit var playerAdapter: PlayerAdapter

    constructor(androidContext: Context) : this(
        androidContext,
        NonceLoader(
            androidContext, ConsentSettings.builder()
                .allowStorage(true)
                .build()
        ),
        OkHttpService()
    )

    suspend fun prepareBeforeLoad(manifestUrl: String, params: AdTrackingManagerParams) {
        this.manifestUrl = manifestUrl
        this.params = params

        if (params.initRequest) {
            val baseUri = URL(manifestUrl)
            val initResponse = okHttpService.getInitResponse(manifestUrl)
            if (initResponse != null) {
                Log.d(TAG, "Obtained URLs from POST init request. manifest: ${initResponse.manifestUrl}, metadata: ${initResponse.trackingUrl}")

                try {
                    val resolvedMetadataUrl = URL(baseUri, initResponse.trackingUrl)
                    metadataUrl = resolvedMetadataUrl.toString()
                } catch (e: Exception) {
                    Log.e(TAG, "Error constructing metadataUrl from originalUrl '$manifestUrl' and trackingUrl in initResponse '${initResponse.trackingUrl}': ${e.message}")
                }

                val obtainedUrl: Uri?
                try {
                    val resolvedManifestUrl = URL(baseUri, initResponse.manifestUrl)
                    obtainedUrl = resolvedManifestUrl.toString().toUri()

                    sessionId = obtainedUrl.getQueryParameter(SESSION_ID_QUERY_PARAM_KEY)

                    // There may be other query params in the init response that need to be passed on.
                    obtainedUrl.queryParameterNames.forEach { name ->
                        // Skip session ID
                        if (name != SESSION_ID_QUERY_PARAM_KEY) {
                            obtainedQueryParams[name] = obtainedUrl.getQueryParameter(name) ?: ""
                        }
                    }

                    this.manifestUrl = obtainedUrl.toString()
                } catch (e: Exception) {
                    Log.e(TAG, "Error constructing manifestUrl from originalUrl '$manifestUrl' and manifestUrl in initResponse '${initResponse.manifestUrl}': ${e.message}")
                }

                if (sessionId == null) {
                    Log.w(TAG, "Session ID not found in init response")
                } else {
                    Log.d(TAG, "Session ID found in init response: $sessionId")
                }
            } else {
                Log.w(TAG, "POST init request failed, falling back to GET request")
            }
        }

        if (metadataUrl == null || sessionId == null) {
            sessionId = okHttpService.getSessionId(manifestUrl)
            if (sessionId == null) {
                Log.w(TAG, "Unsupported SSAI stream")
                return
            }
            metadataUrl = urlFilenameRegex.replace(this.manifestUrl!!, "metadata")
        }

        ssaiSupported = true

        // Nonce manager must be initialized before playback
        generateNonceForAdRequest(params)
    }

    fun onPlay(
        context: Context,
        playerAdapter: PlayerAdapter,
        overlayViewContainer: ViewGroup?,
        playerView: ViewGroup
    ) {
        if (manifestUrl == null) {
            Log.e(TAG, "Manifest URL not set. Unable to start metadata tracker & overlay")
            throw RuntimeException("Manifest URL not set. (Did you call prepareBeforeLoad?)")
        }

        Log.i(TAG, "Ad Tracking manager starting")
        // Init tracking client
        this.playerAdapter = playerAdapter
        metadataTracker = AdMetadataTracker(playerAdapter, okHttpService)
        omsdkClient = OMSDKClient(context, playerAdapter, playerView, metadataTracker, params.omidCustomReferenceData)
        pmmClient = PMMClient(metadataTracker, okHttpService, context)
        trackingOverlay = TrackingOverlay(context, playerAdapter, overlayViewContainer, playerView, metadataTracker, omsdkClient, pmmClient)
        adChoiceManager = AdChoiceManager(context, overlayViewContainer, playerView, metadataTracker)

        trackingOverlay.showOverlay = showOverlay

        setupListeners()

        if (metadataUrl == null || sessionId == null) {
            Log.e(TAG, "Metadata URL or Session ID not set. Unable to start metadata tracker.")
            throw RuntimeException("Metadata URL or Session ID not set. (Did you call prepareBeforeLoad?)")
        }
        metadataTracker.onPlay(metadataUrl!!, sessionId!!)

        sendPlaybackStart()
        Log.i(TAG, "Ad Tracking manager started")
    }

    fun cleanupAfterStop() {
        trackingOverlay.onDestroy()
        metadataTracker.onStopped()
        adChoiceManager.onDestroy()

        if (ssaiSupported) {
            sendPlaybackEnd()
        }
        Log.i(TAG, "Ad Tracking manager stopped")
    }

    fun appendNonceToUrl(urls: List<String>): List<String> {
        return urls.map {
            val builder = Uri.parse(it).buildUpon()

            if (obtainedQueryParams.isNotEmpty()) {
                obtainedQueryParams.forEach { (key, value) ->
                    builder.appendQueryParameter(key, value)
                }
            }

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

    fun getObtainedManifestUrl(): String? {
        return manifestUrl
    }

    fun showTrackingOverlay(state: Boolean) {
        if (::trackingOverlay.isInitialized) {
            trackingOverlay.showOverlay = state
        }
        showOverlay = state
    }

    private suspend fun generateNonceForAdRequest(params: AdTrackingManagerParams) {
        Log.d(TAG, "Generating nonce for ad request")

        val nonceRequest = NonceRequest.builder()
            .descriptionURL(params.descriptionUrl)
            .iconsSupported(params.iconSupported)
            .omidVersion(Omid.getVersion())
            .omidPartnerVersion(params.omidPartnerVersion ?: "")
            .omidPartnerName(params.omidPartnerName ?: "")
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
            .platformSignalCollector(signalCollector)
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

    // Called each time the viewer clicks an ad
    private fun onVideoAdClick() {
        // Trigger on click only if ad is playing
        if (metadataTracker.isPlayingAd()) {
            Log.d(TAG, "Sending ad click event")
            nonceManager.sendAdClick()
            pushEventLog("sendAdClick")
        }
    }

    // Called on every touch interaction with the player
    private fun onVideoAdViewTouch(view: View, event: MotionEvent) {
        view.performClick()
        if (metadataTracker.isPlayingAd()) {
            Log.d(TAG, "Sending on touch event")
            nonceManager.sendAdTouch(event)
            pushEventLog("sendAdTouch")
        }
    }

    private fun pushEventLog(event: String) {
        // Fire intent
        val intent = Intent(Constants.CSAT_INTENT_LOG_ACTION)
        intent.putExtra("message", "[PAL] $event")
        androidContext.sendBroadcast(intent)
    }

    private fun setupListeners() {
        playerAdapter.addEventListener(object : PlayerEventListener {
            override fun onBufferStart() {
                omsdkClient?.bufferStart()
            }

            override fun onBufferEnd() {
                omsdkClient?.bufferEnd()
            }

            override fun onPause() {
                omsdkClient?.pause()
            }

            override fun onResume() {
                omsdkClient?.resume()
            }

            override fun onVideoAdClick() {
                this@AdTrackingManager.onVideoAdClick()
            }

            override fun onVideoAdViewTouch(view: View, event: MotionEvent) {
                this@AdTrackingManager.onVideoAdViewTouch(view, event)
            }

            override fun onVolumeChanged(volume: Float) {
                omsdkClient?.volumeChange(volume)
            }
        })
    }
}