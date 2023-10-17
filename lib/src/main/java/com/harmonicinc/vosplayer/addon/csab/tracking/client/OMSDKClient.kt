package com.harmonicinc.vosplayer.addon.csab.tracking.client

import android.media.session.PlaybackState
import android.util.Log
import com.harmonicinc.vosplayer.addon.PlayerContext
import com.harmonicinc.vosplayer.addon.csab.tracking.AdMetadataTracker
import com.harmonicinc.vosplayer.addon.csab.tracking.AdProgressListener
import com.harmonicinc.vosplayer.addon.csab.tracking.EventLogListener
import com.harmonicinc.vosplayer.addon.csab.tracking.client.omsdk.AdSessionUtil
import com.harmonicinc.vosplayer.addon.csab.tracking.model.Ad
import com.harmonicinc.vosplayer.addon.csab.tracking.model.AdBreak
import com.harmonicinc.vosplayer.addon.csab.tracking.model.AdVerification
import com.harmonicinc.vosplayer.addon.csab.tracking.model.Tracking
import com.harmonicinc.vosplayer.addon.csab.tracking.overlay.EventLog
import com.harmonicinc.vosplayer.baseplayer.CorePlayerEventListener
import com.iab.omid.library.harmonicinc.Omid
import com.iab.omid.library.harmonicinc.adsession.AdEvents
import com.iab.omid.library.harmonicinc.adsession.AdSession
import com.iab.omid.library.harmonicinc.adsession.CreativeType
import com.iab.omid.library.harmonicinc.adsession.media.MediaEvents
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.net.MalformedURLException

class OMSDKClient(
    private val playerContext: PlayerContext,
    private val tracker: AdMetadataTracker
) {
    private var adSession: AdSession? = null
    private var mediaEvents: MediaEvents? = null
    private var adEvents: AdEvents? = null
    private var adVerifications: List<AdVerification>? = null
    private var playbackState = PlaybackState.STATE_BUFFERING
    private var eventLogListener: EventLogListener? = null
    private var currentAdBreak: AdBreak? = null
    private var currentAd: Ad? = null

    // FIXME: WIP
    private val CUSTOM_REFERENCE_DATA = "{\"user\":\"me\" }"
    private val TAG = "OMSDKClient"

    init {
        Omid.activate(playerContext.androidContext)
        Omid.updateLastActivity()
        initHandlers()
    }

    fun setOverlayListener(listener: EventLogListener) {
        eventLogListener = listener
    }

    private fun impressionOccurred() {
        preFireEvent(Tracking.Event.IMPRESSION)
        Log.d(TAG, "adEvents.impressionOccurred()")
        adEvents!!.impressionOccurred()
        pushEventLog(Tracking.Event.IMPRESSION)
    }

    private fun start() {
        preFireEvent(Tracking.Event.START)
        val duration = playerContext.wrappedPlayer?.getDuration()?.toFloat() ?: 0f
        val volume = playerContext.wrappedPlayer?.getAudioVolume() ?: -1f
        Log.d(TAG, "mediaEvents.start($duration, $volume)")
        mediaEvents!!.start(duration, volume)
        pushEventLog(Tracking.Event.START)
    }

    private fun firstQuartile() {
        Log.d(TAG, "mediaEvents!!.firstQuartile()")
        preFireEvent(Tracking.Event.FIRST_QUARTILE)
        mediaEvents!!.firstQuartile()
        pushEventLog(Tracking.Event.FIRST_QUARTILE)
    }

    private fun midpoint() {
        Log.d(TAG, "mediaEvents!!.midpoint()")
        preFireEvent(Tracking.Event.MIDPOINT)
        mediaEvents!!.midpoint()
        pushEventLog(Tracking.Event.MIDPOINT)
    }

    private fun thirdQuartile() {
        Log.d(TAG, "mediaEvents!!.thirdQuartile()")
        preFireEvent(Tracking.Event.THIRD_QUARTILE)
        mediaEvents!!.thirdQuartile()
        pushEventLog(Tracking.Event.THIRD_QUARTILE)
    }

    private fun complete() {
        Log.d(TAG, "mediaEvents!!.complete()")
        preFireEvent(Tracking.Event.COMPLETE)
        mediaEvents!!.complete()
        pushEventLog(Tracking.Event.COMPLETE)
        destroySession()
    }

    private fun pause() {
        if (isPlayingAd()) {
            Log.d(TAG, "mediaEvents.pause()")
            mediaEvents!!.pause()
            pushEventLog(Tracking.Event.PAUSE)
        }
    }

    private fun resume() {
        if (isPlayingAd()) {
            Log.d(TAG, "mediaEvents.resume()")
            mediaEvents!!.resume()
            pushEventLog(Tracking.Event.RESUME)
        }
    }

    private fun bufferStart() {
        if (isPlayingAd()) {
            Log.d(TAG, "mediaEvents.bufferStart()")
            mediaEvents!!.bufferStart()
            pushEventLog(Tracking.Event.BUFFER_START)
        }
    }

    private fun bufferEnd() {
        if (isPlayingAd()) {
            Log.d(TAG, "mediaEvents.bufferFinish()")
            mediaEvents!!.bufferFinish()
            pushEventLog(Tracking.Event.BUFFER_END)
        }
    }

    private fun volumeChange() {
        // We don't have creative volume change, no need to handle it manually
        TODO("Not yet implemented")
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
        val adBreakId = currentAdBreak!!.id
        val adId = currentAd!!.id
        CoroutineScope(Dispatchers.Main).launch {
            eventLogListener?.onEvent(
                EventLog(
                    "OMSDK",
                    adBreakId,
                    adId,
                    System.currentTimeMillis(),
                    event
                )
            )
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

        playerContext.wrappedPlayer!!.addEventListener(object : CorePlayerEventListener {
            override fun onMediaPresentationBuffering(playWhenReady: Boolean) {
                if (playWhenReady) {
                    bufferStart()
                    playbackState = PlaybackState.STATE_BUFFERING
                }
            }

            override fun onMediaPresentationResumed() {
                if (playbackState == PlaybackState.STATE_BUFFERING) {
                    bufferEnd()
                }
                if (playbackState != PlaybackState.STATE_PLAYING) {
                    playbackState = PlaybackState.STATE_PLAYING
                    resume()
                }
            }

            override fun onMediaPresentationPaused() {
                if (playerContext.wrappedPlayer?.isPaused() == true && playbackState != PlaybackState.STATE_PAUSED && playbackState != PlaybackState.STATE_ERROR) {
                    playbackState = PlaybackState.STATE_PAUSED
                    pause()
                }
            }

            override fun onError(error: Any) {
                playbackState = PlaybackState.STATE_ERROR
            }
        })
    }

    private fun preFireEvent(event: Tracking.Event) {
        if (adSession != null && event == Tracking.Event.IMPRESSION) {
            // Terminate previous session first
            destroySession()
        }
        if (adSession == null) {
            createSession()
        }
    }

    private fun createSession() {
        adSession = try {
            AdSessionUtil.getNativeAdSession(
                playerContext.androidContext!!,
                CUSTOM_REFERENCE_DATA,
                CreativeType.VIDEO,
                adVerifications!!
            )
        } catch (e: MalformedURLException) {
            Log.d(TAG, "setupAdSession failed", e)
            throw java.lang.UnsupportedOperationException(e)
        }
        mediaEvents = MediaEvents.createMediaEvents(adSession)
        adEvents = AdEvents.createAdEvents(adSession)
        adSession!!.registerAdView(playerContext.playerView)

        adSession!!.start()
        adEvents!!.loaded()
        Log.d(TAG, "Session created")
    }
}
