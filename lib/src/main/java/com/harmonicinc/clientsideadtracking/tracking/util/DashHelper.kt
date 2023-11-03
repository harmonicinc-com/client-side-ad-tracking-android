package com.harmonicinc.clientsideadtracking.tracking.util

import com.harmonicinc.clientsideadtracking.player.PlayerAdapter

object DashHelper {
    fun getMpdTimeMs(player: PlayerAdapter): Long {
        val position = player.getCurrentPositionMs()
        val presentationStartTime = player.getPresentationStartTimeMs()
        return position + presentationStartTime
    }
}