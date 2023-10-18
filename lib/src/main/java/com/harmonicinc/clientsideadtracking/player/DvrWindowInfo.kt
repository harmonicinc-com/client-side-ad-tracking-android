package com.harmonicinc.clientsideadtracking.player

import java.util.Date

data class DvrWindowInfo(
    val startingUtcTime: Date?,
    val startingMediaTimeMs: Long,
    val durationMs: Long
)