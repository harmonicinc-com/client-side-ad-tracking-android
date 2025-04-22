package com.harmonicinc.clientsideadtracking.tracking.model

import kotlinx.serialization.Serializable

@Serializable
class InitResponse(
    val manifestUrl: String,
    val trackingUrl: String
)
