package com.harmonicinc.clientsideadtracking.tracking

import com.harmonicinc.clientsideadtracking.tracking.model.Ad
import com.harmonicinc.clientsideadtracking.tracking.model.AdBreak
import com.harmonicinc.clientsideadtracking.tracking.model.Tracking

interface AdBreakListener {
    fun onCurrentAdBreakUpdate(pod: AdBreak?) {}
    fun onCurrentAdUpdate(ad: Ad?) {}
    fun onCurrentTrackingUpdate(tracking: List<Tracking>?) {}
}