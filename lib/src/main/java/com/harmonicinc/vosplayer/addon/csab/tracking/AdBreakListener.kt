package com.harmonicinc.vosplayer.addon.csab.tracking

import com.harmonicinc.vosplayer.addon.csab.tracking.model.Ad
import com.harmonicinc.vosplayer.addon.csab.tracking.model.AdBreak
import com.harmonicinc.vosplayer.addon.csab.tracking.model.Tracking

interface AdBreakListener {
    fun onCurrentAdBreakUpdate(pod: AdBreak?) {}
    fun onCurrentAdUpdate(ad: Ad?) {}
    fun onCurrentTrackingUpdate(tracking: List<Tracking>?) {}
}