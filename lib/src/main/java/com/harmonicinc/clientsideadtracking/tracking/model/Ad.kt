package com.harmonicinc.clientsideadtracking.tracking.model

import org.json.JSONObject

class Ad(ad: JSONObject) {
    val id: String
    val duration: Double
    val tracking: List<Tracking>
    val adVerifications: List<AdVerification>
    val startTime: Long
    init {
        id = ad.optString("id")
        duration = ad.optDouble("duration")
        this.tracking = ArrayList()
        val trackingEventsList = ad.optJSONArray("trackingEvents")
        if (trackingEventsList != null) {
            for (i in 0 until trackingEventsList.length()) {
                val tracking = Tracking()
                tracking.parse(trackingEventsList.optJSONObject(i))
                this.tracking.add(tracking)
            }
        }
        adVerifications = ArrayList()

        val adVerificationList = ad.optJSONArray("adVerifications")
        if (adVerificationList != null) {
            for (i in 0 until adVerificationList.length()) {
                adVerifications.add(AdVerification(adVerificationList.optJSONObject(i)))
            }
        }

        startTime = ad.optLong("startTime", 0L)
    }
}