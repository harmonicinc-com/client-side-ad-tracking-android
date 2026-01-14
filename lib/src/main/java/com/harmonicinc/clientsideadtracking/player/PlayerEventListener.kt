package com.harmonicinc.clientsideadtracking.player

import android.view.MotionEvent
import android.view.View

interface PlayerEventListener {
    fun onBufferStart() {}
    fun onBufferEnd() {}
    fun onPause() {}
    fun onResume() {}
    fun onMute() {}
    fun onUnmute() {}
    fun onRewind() {}
    fun onSkip() {}
    fun onPlayerExpand() {}
    fun onPlayerCollapse() {}
    fun onVideoAdClick() {}
    fun onVideoAdViewTouch(view: View, event: MotionEvent) {}
    fun onVolumeChanged(volume: Float) {}
}
