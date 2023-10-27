package com.harmonicinc.clientsideadtracking

data class AdTrackingManagerParams(
    val descriptionUrl: String,
    val iconSupported: Boolean,
    val playerType: String,
    val playerVersion: String,
    val ppid: String,
    val supportedApiFrameworks: Set<Int>,
    val playerHeight: Int,
    val playerWidth: Int,
    val willAdAutoplay: Boolean,
    val willAdPlayMuted: Boolean,
    val continuousPlayback: Boolean,
    // OMSDK params: Mandatory if using OMSDK
    val omidPartnerVersion: String?,
    val omidPartnerName: String?,
    // Custom reference data in JSON string
    val omidCustomReferenceData: String?
)