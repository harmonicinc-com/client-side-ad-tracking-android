package com.harmonicinc.vosplayer.addon.csab.tracking.util

import com.harmonicinc.vosplayer.baseplayer.AbstractPlayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object DashHelper {
    suspend fun getMpdTimeMs(player: AbstractPlayer): Long = withContext(Dispatchers.Main){
        val position = player.getCurrentPositionInMS()
        val presentationStartTime = player.getDvrWindow()?.startingMediaTimeMs ?: 0
        return@withContext position + presentationStartTime
    }
}