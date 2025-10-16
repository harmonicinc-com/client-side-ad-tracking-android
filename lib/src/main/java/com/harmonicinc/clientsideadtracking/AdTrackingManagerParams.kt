package com.harmonicinc.clientsideadtracking

import com.harmonicinc.clientsideadtracking.tracking.util.Constants.DEFAULT_CACHE_RETENTION_TIME_MS
import com.harmonicinc.clientsideadtracking.tracking.util.Constants.DEFAULT_METADATA_FETCH_INTERVAL_MS

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
    val omidCustomReferenceData: String?,
    // Optional params
    val initRequest: Boolean = true,
    val cacheRetentionTimeMs: Long = DEFAULT_CACHE_RETENTION_TIME_MS,
    val metadataFetchIntervalMs: Long = DEFAULT_METADATA_FETCH_INTERVAL_MS
)