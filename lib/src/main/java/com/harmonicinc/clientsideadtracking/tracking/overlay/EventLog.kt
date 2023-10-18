package com.harmonicinc.clientsideadtracking.tracking.overlay

import com.harmonicinc.clientsideadtracking.tracking.model.Tracking

class EventLog(
    val clientTag: String,
    val adBreakId: String,
    val adId: String,
    val time: Long,
    val event: Tracking.Event
)