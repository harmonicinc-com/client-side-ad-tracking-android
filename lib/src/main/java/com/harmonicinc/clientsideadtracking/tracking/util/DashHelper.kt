package com.harmonicinc.clientsideadtracking.tracking.util

import com.harmonicinc.clientsideadtracking.player.baseplayer.AbstractPlayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object DashHelper {
    suspend fun getMpdTimeMs(player: AbstractPlayer): Long = withContext(Dispatchers.Main){
        val position = player.getCurrentPositionMs()
        val presentationStartTime = player.getPresentationStartTimeMs()
        return@withContext position + presentationStartTime
    }
}