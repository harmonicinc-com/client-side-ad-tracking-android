package com.harmonicinc.clientsideadtracking.tracking.model.icon.iconclicks

import com.harmonicinc.clientsideadtracking.tracking.model.icon.StaticResource
import org.json.JSONObject

class IconClickFallbackImage(json: JSONObject) {
    val width: Int
    val height: Int
    private val altText: String
    val staticResource: StaticResource

    init {
        width = json.optInt("width")
        height = json.optInt("height")
        altText = json.optString("altText")
        staticResource = StaticResource(json.getJSONObject("staticResource"))
    }
}