package com.harmonicinc.clientsideadtracking.player

import android.view.MotionEvent
import android.view.View

interface PlayerEventListener {
    fun onBufferStart() {}
    fun onBufferEnd() {}
    fun onPause() {}
    fun onResume() {}
    fun onVideoAdClick() {}
    fun onVideoAdViewTouch(view: View, event: MotionEvent) {}
    fun onVolumeChanged(volume: Float) {}
}
