package com.harmonicinc.clientsideadtracking

import android.content.Context
import android.content.res.Resources
import android.media.session.PlaybackState
import android.net.Uri
import android.util.Log
import android.view.View.OnClickListener
import android.view.View.OnTouchListener
import android.view.ViewGroup
import com.android.volley.Request
import com.android.volley.ServerError
import com.android.volley.toolbox.HurlStack
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley
import com.google.ads.interactivemedia.pal.ConsentSettings
import com.google.ads.interactivemedia.pal.NonceLoader
import com.google.ads.interactivemedia.pal.NonceManager
import com.google.ads.interactivemedia.pal.NonceRequest
import com.harmonicinc.clientsideadtracking.player.PlaybackContext
import com.harmonicinc.clientsideadtracking.player.PlayerAddon
import com.harmonicinc.clientsideadtracking.player.PlayerContext
import com.harmonicinc.clientsideadtracking.tracking.AdMetadataTracker
import com.harmonicinc.clientsideadtracking.tracking.client.OMSDKClient
import com.harmonicinc.clientsideadtracking.tracking.client.PMMClient
import com.harmonicinc.clientsideadtracking.tracking.overlay.TrackingOverlay
import com.harmonicinc.clientsideadtracking.tracking.util.Constants.PAL_DESCRIPTION_URL
import com.harmonicinc.clientsideadtracking.tracking.util.Constants.PAL_NONCE_QUERY_PARAM_KEY
import com.harmonicinc.clientsideadtracking.tracking.util.Constants.PAL_PPID
import com.harmonicinc.clientsideadtracking.tracking.util.Constants.PAL_SUPPORTED_API
import com.harmonicinc.clientsideadtracking.tracking.util.Constants.SESSION_ID_QUERY_PARAM_KEY
import com.harmonicinc.clientsideadtracking.player.baseplayer.CorePlayerEventListener
import com.harmonicinc.clientsideadtracking.tracking.adchoices.AdChoiceManager
import com.iab.omid.library.harmonicinc.Omid
import kotlinx.coroutines.tasks.await
import java.lang.Exception
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

class GooglePalAddon(private val androidContext: Context) : PlayerAddon {
    private var TAG = "GooglePALAddon"
    private var sessionId: String? = null
    private var ssaiSupported = false
    private var playbackState = PlaybackState.STATE_NONE
    private var manifestUrl: String? = null
    private var omsdkClient: OMSDKClient? = null
    private var pmmClient: PMMClient? = null

    private val urlFilenameRegex = Regex("[^/\\\\&\\?]+\\.\\w{3,4}(?=([\\?&].*\$|\$))")
    private val queue = Volley.newRequestQueue(androidContext, object: HurlStack() {
        override fun createConnection(url: URL?): HttpURLConnection {
            val connection = super.createConnection(url)
            connection.instanceFollowRedirects = false
            return connection
        }
    })
    lateinit var trackingOverlay: TrackingOverlay
    lateinit var adChoiceManager: AdChoiceManager

    private lateinit var nonceLoader: NonceLoader
    private lateinit var nonceManager: NonceManager
    private lateinit var metadataTracker: AdMetadataTracker
    private lateinit var playerContext: PlayerContext

    suspend fun prepareBeforeLoad(manifestUrl: String) {
        // Init Google PAL
        initializePAL()

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

    override fun prepareAfterPlayerViewCreated(playerContext: PlayerContext) {
        // Player is available only after this point
        this.playerContext = playerContext

        // Init tracking client
        metadataTracker = AdMetadataTracker(playerContext, queue)
        omsdkClient = OMSDKClient(playerContext, metadataTracker)
        pmmClient = PMMClient(playerContext, metadataTracker)
        trackingOverlay = TrackingOverlay(playerContext, metadataTracker, omsdkClient, pmmClient)
        adChoiceManager = AdChoiceManager(playerContext, metadataTracker)

        // Subscribe to player events
        subscribeToPlayerEvents()
    }

    override fun cleanupAfterStop(playbackContext: PlaybackContext) {
        trackingOverlay.onDestroy()
        metadataTracker.onStopped()

        if (ssaiSupported) {
            sendPlaybackEnd()
        }
        Log.i(TAG, "Stopped Google PAL addon")
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

    private fun initializePAL() {
        Log.i(TAG, "Starting Google PAL addon")
//        View can only have one listener at a time
//        videoView.setOnTouchListener(this.onVideoViewTouch)
//        videoView.setOnClickListener(this.onVideoAdClick)

        val consentSettings = ConsentSettings.builder()
            .allowStorage(true)
            .build()

        // It is important to instantiate the NonceLoader as early as possible to
        // allow it to initialize and preload data for a faster experience when
        // loading the NonceManager. A new NonceLoader will need to be instantiated
        //if the ConsentSettings change for the user.

        nonceLoader = NonceLoader(androidContext, consentSettings)
        Log.i(TAG, "Started Google PAL addon")
    }

    private suspend fun generateNonceForAdRequest() {
        Log.d(TAG, "Generating nonce for ad request")
        // NOTE: Assume this addon is used in Android TV only, where player is always fullscreen
        // due to player display always return 0x0 before playback
        val displayMetrics = Resources.getSystem().displayMetrics
        val height = displayMetrics.heightPixels
        val width = displayMetrics.widthPixels

        val nonceRequest = NonceRequest.builder()
            .descriptionURL(PAL_DESCRIPTION_URL)
            .iconsSupported(true)
            .omidVersion(Omid.getVersion())
//            .omidPartnerVersion(com.harmonicinc.vosplayer.BuildConfig.VERSION_NAME)
//            .omidPartnerName(BuildConfig.PARTNER_NAME)
            .playerType("vosplayertvdemo")
            .playerVersion("1.0.0")
            .ppid(PAL_PPID)
            .sessionId(sessionId!!)
            .supportedApiFrameworks(PAL_SUPPORTED_API)
            .videoPlayerHeight(height)
            .videoPlayerWidth(width)
            .willAdAutoPlay(true)
            .willAdPlayMuted(false)
            .continuousPlayback(false)
            .build()

        nonceManager = nonceLoader.loadNonceManager(nonceRequest).await()
        Log.d(TAG, "Generated nonce for ad request")
    }

    private fun sendPlaybackStart() {
        Log.d(TAG, "Sending playback start event")
        nonceManager.sendPlaybackStart()
    }

    private fun sendPlaybackEnd() {
        Log.d(TAG, "Sending playback end event")
        nonceManager.sendPlaybackEnd()
    }

    // Has no effect on Android TV
    private val onVideoAdClick = OnClickListener {
        // Trigger on click only if ad is playing
        if (metadataTracker.isPlayingAd()) {
            Log.d(TAG, "Sending ad click event")
            nonceManager.sendAdClick()
        }
    }

    // Has no effect on Android TV
    private val onVideoViewTouch = OnTouchListener { view, event ->
        view?.performClick()
        if (metadataTracker.isPlayingAd()) {
            Log.d(TAG, "Sending on touch event")
            nonceManager.sendAdTouch(event)
        }
        false
    }

    private suspend fun getSessionId(manifestUrl: String): String? = suspendCoroutine { cont ->
        try {
            val stringRequest = StringRequest(Request.Method.GET, manifestUrl, {
                Log.d(TAG, it)
            }, { e ->
                if (e is ServerError && (e.networkResponse.statusCode == HttpURLConnection.HTTP_MOVED_PERM || e.networkResponse.statusCode == HttpURLConnection.HTTP_MOVED_TEMP) && e.networkResponse.headers?.contains("location") == true) {
                    val location = e.networkResponse.headers!!["location"] as String
                    // Assume there's one param only, and named "sessId"
                    val sessIdSplitList = location.split("$SESSION_ID_QUERY_PARAM_KEY=")
                    cont.resume( if (sessIdSplitList.size != 2) null else sessIdSplitList.last())
                } else {
                    cont.resumeWithException(e)
                }
            })
            queue.add(stringRequest)
        } catch (_: Exception) {}
    }

    private fun onPlay() {
        if (manifestUrl == null) {
            Log.e(TAG, "Manifest URL not set. Unable to start metadata tracker & overlay")
            return
        }

        val metadataUrl = urlFilenameRegex.replace(this.manifestUrl!!, "metadata")
        metadataTracker.onPlay(metadataUrl, sessionId!!)

        sendPlaybackStart()
    }

    private fun subscribeToPlayerEvents() {
        playerContext.wrappedPlayer!!.addEventListener(object: CorePlayerEventListener {
            override fun onMediaPresentationResumed() {
                if (playbackState != PlaybackState.STATE_PLAYING) {
                    playbackState = PlaybackState.STATE_PLAYING
                    onPlay()
                }
            }
        })
    }
}