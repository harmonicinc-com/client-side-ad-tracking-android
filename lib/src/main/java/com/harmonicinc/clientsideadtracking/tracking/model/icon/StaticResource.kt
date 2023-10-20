package com.harmonicinc.clientsideadtracking.tracking.model.icon

import org.json.JSONObject

class StaticResource(json: JSONObject) {
    private val creativeType: String
    val uri: String

    init {
        creativeType = json.optString("creativeType", "")
        uri = json.optString("uri", "")
    }
}