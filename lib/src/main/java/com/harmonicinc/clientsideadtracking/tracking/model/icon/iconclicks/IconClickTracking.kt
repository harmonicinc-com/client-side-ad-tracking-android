package com.harmonicinc.clientsideadtracking.tracking.model.icon.iconclicks

import org.json.JSONObject

class IconClickTracking(json: JSONObject) {
    private val id: String
    private val uri: String

    init {
        id = json.optString("id")
        uri = json.optString("uri")
    }
}