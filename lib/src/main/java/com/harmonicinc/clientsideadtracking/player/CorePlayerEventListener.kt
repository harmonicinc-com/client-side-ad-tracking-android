package com.harmonicinc.clientsideadtracking.player

interface CorePlayerEventListener {
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
}
