package com.harmonicinc.vosplayer.addon.csab.tracking

import com.harmonicinc.vosplayer.addon.csab.tracking.model.Ad
import com.harmonicinc.vosplayer.addon.csab.tracking.model.AdBreak
import com.harmonicinc.vosplayer.addon.csab.tracking.model.Tracking

interface AdProgressListener {
    fun onAdProgress(currentAdBreak: AdBreak?, currentAd: Ad?, event: Tracking)
}