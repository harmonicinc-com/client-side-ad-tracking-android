package com.harmonicinc.clientsideadtracking.tracking

import com.harmonicinc.clientsideadtracking.tracking.model.Ad
import com.harmonicinc.clientsideadtracking.tracking.model.AdBreak
import com.harmonicinc.clientsideadtracking.tracking.model.Tracking

interface AdProgressListener {
    fun onAdProgress(currentAdBreak: AdBreak?, currentAd: Ad?, event: Tracking)
}