package com.harmonicinc.clientsideadtracking.tracking.model.icon

import com.harmonicinc.clientsideadtracking.tracking.extensions.toList
import com.harmonicinc.clientsideadtracking.tracking.model.icon.iconclicks.IconClicks
import org.json.JSONObject

class Icon(json: JSONObject) {
    val attributes: Attributes
    val staticResource: StaticResource
    private val iFrameResource: String
    private val htmlResource: String
    val iconClicks: IconClicks
    private val iconViewTracking: List<String>

    init {
        attributes = Attributes(json.getJSONObject("attributes"))
        staticResource = StaticResource(json.optJSONObject("staticResource"))
        iFrameResource = json.optString("iFrameResource")
        htmlResource = json.optString("htmlResource")
        iconClicks = IconClicks(json.getJSONObject("iconClicks"))
        iconViewTracking = json.optJSONArray("iconViewTracking")?.toList() as List<String>? ?: listOf("")
    }
}
