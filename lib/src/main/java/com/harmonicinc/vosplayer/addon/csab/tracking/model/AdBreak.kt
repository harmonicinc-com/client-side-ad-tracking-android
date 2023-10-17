package com.harmonicinc.vosplayer.addon.csab.tracking.model

import org.json.JSONObject

class AdBreak(
    var id: String = "",
    var duration: Double = 0.0,
    var ads: ArrayList<Ad> = ArrayList(),
    var startTime: Long = 0L
) {
    fun parse(json: JSONObject) {
        this.id = json.optString("id", "")
        this.duration = json.optDouble("duration")
        this.startTime = json.optLong("startTime", 0L)
        val adsArray = json.optJSONArray("ads")
        if (adsArray != null) {
            for (i in 0 until adsArray.length()) {
                this.ads.add(Ad(adsArray.optJSONObject(i)))
            }
        }
    }
}