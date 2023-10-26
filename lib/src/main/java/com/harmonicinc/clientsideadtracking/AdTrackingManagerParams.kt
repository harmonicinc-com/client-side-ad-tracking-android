package com.harmonicinc.clientsideadtracking

data class AdTrackingManagerParams(
    val descriptionUrl: String,
    val iconSupported: Boolean,
    val playerType: String,
    val playerVersion: String,
    val ppid: String,
    val supportedApiFrameworks: Set<Int>,
    var playerHeight: Int,
    var playerWidth: Int,
    val willAdAutoplay: Boolean,
    val willAdPlayMuted: Boolean,
    val continuousPlayback: Boolean
)