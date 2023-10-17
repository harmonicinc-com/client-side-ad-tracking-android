package com.harmonicinc.vosplayer.addon.csab.tracking.overlay

import com.harmonicinc.vosplayer.addon.csab.tracking.model.Tracking

class EventLog(
    val clientTag: String,
    val adBreakId: String,
    val adId: String,
    val time: Long,
    val event: Tracking.Event
)