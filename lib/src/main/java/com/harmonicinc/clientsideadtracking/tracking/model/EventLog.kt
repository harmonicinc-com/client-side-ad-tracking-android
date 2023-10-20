package com.harmonicinc.clientsideadtracking.tracking.model

data class EventLog(
    val clientTag: String,
    val adBreakId: String,
    val adId: String,
    val time: Long,
    val event: Tracking.Event
)