package com.harmonicinc.clientsideadtracking.tracking.model.icon.iconclicks

import org.json.JSONObject

class IconClicks(json: JSONObject) {
    val iconClickThrough: String
    private val iconClickTracking: List<IconClickTracking>
    val iconClickFallbackImages: List<IconClickFallbackImage>

    init {
        iconClickThrough = json.optString("iconClickThrough")

        iconClickTracking = ArrayList()
        val iconClickTrackingList = json.optJSONArray("iconClickTracking")
        if (iconClickTrackingList != null) {
            for (i in 0 until iconClickTrackingList.length()) {
                iconClickTracking.add(IconClickTracking(iconClickTrackingList.optJSONObject(i)))
            }
        }

        iconClickFallbackImages = ArrayList()
        val iconClickFallbackImagesList = json.optJSONArray("iconClickFallbackImages")
        if (iconClickFallbackImagesList != null) {
            for (i in 0 until iconClickFallbackImagesList.length()) {
                iconClickFallbackImages.add(IconClickFallbackImage(iconClickFallbackImagesList.optJSONObject(i)))
            }
        }
    }
}