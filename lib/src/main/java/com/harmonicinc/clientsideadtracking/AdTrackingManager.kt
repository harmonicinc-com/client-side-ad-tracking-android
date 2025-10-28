package com.harmonicinc.clientsideadtracking

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Looper
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
import com.harmonicinc.clientsideadtracking.error.AdTrackingError
import com.harmonicinc.clientsideadtracking.error.AdTrackingErrorListener
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
    private var errorListener: AdTrackingErrorListener? = null

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
        Log.d(TAG, "Preparing AdTrackingManager with manifestUrl: $manifestUrl")
        this.manifestUrl = manifestUrl
        this.params = params

        // Set error listener on OkHttpService
        okHttpService.setErrorListener(errorListener)

        if (params.initRequest) {
            val initResponse = okHttpService.getInitResponse(manifestUrl)
            if (initResponse != null) {
                Log.d(TAG, "Obtained URLs from init request API. manifest: ${initResponse.manifestUrl}, metadata: ${initResponse.trackingUrl}")

                // URLs are already resolved by OkHttpService
                metadataUrl = initResponse.trackingUrl
                val obtainedUrl = initResponse.manifestUrl.toUri()
                sessionId = obtainedUrl.getQueryParameter(SESSION_ID_QUERY_PARAM_KEY)

                // Store all query parameters from the obtained URL
                for (paramName in obtainedUrl.queryParameterNames) {
                    val paramValue = obtainedUrl.getQueryParameter(paramName)
                    if (paramValue != null) {
                        obtainedQueryParams[paramName] = paramValue
                    }
                }
                
                this.manifestUrl = obtainedUrl.toString()

                if (sessionId == null) {
                    val error = AdTrackingError.SessionInitError(
                        "Session ID not found in init response, falling back to redirect/parsing manifest",
                        errorIsRecoverable = true
                    )
                    errorListener?.onError(error)
                    Log.w(TAG, "Session ID not found in init response")
                } else {
                    Log.d(TAG, "Session ID found in init response: $sessionId")
                }
            } else {
                val error = AdTrackingError.SessionInitError(
                    "Init request failed, falling back to redirect/parsing manifest",
                    errorIsRecoverable = true
                )
                errorListener?.onError(error)
                Log.w(TAG, "Init request failed, falling back to redirect/parsing manifest")
            }
        }

        if (metadataUrl == null || sessionId == null) {
            val result = okHttpService.getSessionIdAndUrl(manifestUrl)
            if (result != null) {
                sessionId = result.sessionId
                Log.d(TAG, "Session ID found in manifest: $sessionId")
                if (result.resolvedUrl != null) {
                    this.manifestUrl = result.resolvedUrl
                    Log.d(TAG, "Resolved manifest URL: ${this.manifestUrl}")
                }
            } else {
                val error = AdTrackingError.SessionInitError(
                    "Failed to retrieve session ID and URL from manifest",
                    errorIsRecoverable = false
                )
                errorListener?.onError(error)
            }
            
            if (sessionId == null) {
                val error = AdTrackingError.SessionInitError(
                    "Unsupported SSAI stream - no session ID found",
                    errorIsRecoverable = false
                )
                errorListener?.onError(error)
                Log.w(TAG, "Unsupported SSAI stream")
                return
            }
            metadataUrl = urlFilenameRegex.replace(this.manifestUrl!!, "metadata")
        }

        ssaiSupported = true

        // Nonce manager must be initialized before playback
        generateNonceForAdRequest(params)
    }

    /**
     * Starts the ad tracking manager and initializes tracking components.
     * **Must be called from the main thread.**
     * 
     * @param context Application context
     * @param playerAdapter Adapter for the player implementation
     * @param overlayViewContainer Optional container for overlay views
     * @param playerView Optional player view for UI components. 
     *
     * @throws IllegalStateException if called from a background thread
     * @throws RuntimeException if manifest URL or session ID is not set
     */
    fun onPlay(
        context: Context,
        playerAdapter: PlayerAdapter,
        overlayViewContainer: ViewGroup? = null,
        playerView: ViewGroup? = null
    ) {
        if (manifestUrl == null) {
            Log.e(TAG, "Manifest URL not set. Unable to start metadata tracker")
            throw RuntimeException("Manifest URL not set. (Did you call prepareBeforeLoad?)")
        }

        Log.i(TAG, "Ad Tracking manager starting")
        // Init tracking client
        this.playerAdapter = playerAdapter
        metadataTracker = AdMetadataTracker(
            playerAdapter, 
            okHttpService, 
            cacheRetentionTimeMs = params.cacheRetentionTimeMs,
            metadataFetchIntervalMs = params.metadataFetchIntervalMs
        )
        metadataTracker.setErrorListener(errorListener)
        
        // Always initialize PMMClient for beacon tracking
        pmmClient = PMMClient(metadataTracker, okHttpService, context)
        pmmClient!!.setErrorListener(errorListener)
        
        // Only initialize view-dependent components if playerView is provided
        if (playerView != null) {
            Log.d(TAG, "PlayerView provided, initializing full tracking components")

            // Check thread
            if (Looper.myLooper() != Looper.getMainLooper()) {
                throw IllegalStateException("onPlay() must be called from the main thread.")
            }

            omsdkClient = OMSDKClient(context, playerAdapter, playerView, metadataTracker, params.omidCustomReferenceData)
            trackingOverlay = TrackingOverlay(context, playerAdapter, overlayViewContainer, playerView, metadataTracker, omsdkClient, pmmClient)
            adChoiceManager = AdChoiceManager(context, overlayViewContainer, playerView, metadataTracker)
            
            trackingOverlay.showOverlay = showOverlay
        } else {
            Log.d(TAG, "PlayerView not provided, using PMM beacon tracking only")
            omsdkClient = null
        }

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
        if (::trackingOverlay.isInitialized) {
            trackingOverlay.onDestroy()
        }
        metadataTracker.onStopped()
        if (::adChoiceManager.isInitialized) {
            adChoiceManager.onDestroy()
        }

        if (ssaiSupported) {
            sendPlaybackEnd()
        }
        Log.i(TAG, "Ad Tracking manager stopped")
    }

    fun appendNonceToUrl(urls: List<String>): List<String> {
        return urls.map {
            val uri = Uri.parse(it)
            val builder = uri.buildUpon()

            if (obtainedQueryParams.isNotEmpty()) {
                obtainedQueryParams.forEach { (key, value) ->
                    if (uri.getQueryParameter(key) == null) {
                        builder.appendQueryParameter(key, value)
                    }
                }
            }

            builder.appendQueryParameter(PAL_NONCE_QUERY_PARAM_KEY, nonceManager.nonce)
            
            // Only add sessionId if it doesn't already exist in the URL
            if (uri.getQueryParameter(SESSION_ID_QUERY_PARAM_KEY) == null) {
                builder.appendQueryParameter(SESSION_ID_QUERY_PARAM_KEY, sessionId)
            }
            
            builder.build().toString()
        }
    }

    fun isSSAISupported(): Boolean {
        return ssaiSupported
    }

    fun getObtainedManifestUrl(): String? {
        Log.d(TAG, "Obtained manifest URL: $manifestUrl")
        return manifestUrl
    }

    fun showTrackingOverlay(state: Boolean) {
        if (::trackingOverlay.isInitialized) {
            trackingOverlay.showOverlay = state
        }
        showOverlay = state
    }

    /**
     * Sets the error listener to receive callbacks when errors occur in the ad tracking system.
     *
     * @param listener The listener to receive error callbacks, or null to remove the listener
     */
    fun setErrorListener(listener: AdTrackingErrorListener?) {
        this.errorListener = listener
        // Propagate error listener to OkHttpService if it's already been used
        okHttpService.setErrorListener(listener)
        // Error listener for child components will be set in onPlay()
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