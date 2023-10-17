package com.harmonicinc.vosplayer.addon;

import android.content.Context
import android.view.View
import androidx.leanback.widget.Action
import com.harmonicinc.vosplayer.PlaybackOption

interface PlayerAddon {

    fun getOverlayView(): View? {
        return null
    }

    fun getOverlayViewOrder(): Int {
        return -1
    }

    fun prepareAfterPlayerViewCreated(playerContext: PlayerContext) {
        //implement optionally
    }

    fun prepareBeforePlay(playbackContext: PlaybackContext) {
        //implement optionally
    }

    fun cleanupAfterStop(playbackContext: PlaybackContext) {
        //implement optionally
    }

    fun cleanupBeforePlayerViewDestroyed(playerContext: PlayerContext) {
        //implement optionally
    }

    fun updateAfterPlaybackOptionChanged(playbackOption: PlaybackOption) {
        //implement optionally
    }

    fun onViewResume() {
        //implement optionally
    }

    fun onViewPause() {
        //implement optionally
    }

    fun getMetadata(): Map<String, String> {
        return mapOf()
    }

    fun getLeanbackAction(context: Context): Action?{
        return null
    }

    fun onTimelineUpdated() {
        // implement optionally
    }

    open class AddonAction(id:Long): Action(id) {
        open fun run(){
        }
    }
}
