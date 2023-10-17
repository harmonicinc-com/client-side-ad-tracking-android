package com.harmonicinc.vosplayer

import java.util.Date

data class DvrWindowInfo(
    val startingUtcTime: Date?,
    val startingMediaTimeMs: Long,
    val durationMs: Long
)