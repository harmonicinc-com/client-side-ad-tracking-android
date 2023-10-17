package com.harmonicinc.vosplayer.baseplayer

import android.view.ViewGroup
import org.json.JSONArray

interface CorePlayerEventListener {

    fun onMediaReset() {
        //implement optionally
    }

    fun onMediaPresentationResumed() {
        //implement optionally
    }

    fun onMediaPresentationPaused() {
        //implement optionally
    }

    fun onMediaPresentationEnded() {
        //implement optionally
    }

    fun onMediaPresentationBuffering(playWhenReady: Boolean) {
        //implement optionally
    }

    fun onError(error: Any) {
        //implement optionally
    }

    fun onAlternativeViewCreated(view: ViewGroup) {
        //implement optionally
    }

    fun onAlternativeViewDestroyed() {
        //implement optionally
    }

    fun onVideoAspectRatioChanged(aspectRatio: Float) {
        //implement optionally
    }

    fun onSubtitleTracksUpdated() {
        //implement optionally
    }

    //return cur graphic cue and delta to start
    //TODO: change datastructure to support other event type
    fun onGraphicOverlayChanged(overlayInfoJson: JSONArray, delta: Int) {
        //implement optionally
    }

    fun onNextGraphicOverlayChanged(overlayInfoJson: JSONArray) {
        //implement optionally
    }

    fun onTimedMetadataEventReceived(event: Any) {
        //implement optionally
    }

    fun onTimedMetadataEventStarted(event: Any) {
        //implement optionally
    }

    fun onSeekBegin() {
        //implement optionally
    }

    fun onSeekEnd() {
        //implement optionally
    }

    fun onLoadSegment(segmentInfo: Any) {
        //implement optionally
    }

    fun onSegmentLoaded(segmentInfo: Any) {
        //implement optionally
    }

    fun onDroppedVideoFrames(droppedFrames: Int) {
        //implement optionally
    }

    fun onTimelineUpdated() {
        //implement optionally
    }

    fun onAdEvent(adEvent: Any) {
        //implement optionally
    }

    // For thumbnail track addon
    fun onTimeBarScrubStart(time: Int){
        //implement optionally
    }
    fun onTimeBarScrubMove(time:Int){
        //implement optionally
    }
    fun onTimeBarScrubStop(){
        //implement optionally
    }
}
