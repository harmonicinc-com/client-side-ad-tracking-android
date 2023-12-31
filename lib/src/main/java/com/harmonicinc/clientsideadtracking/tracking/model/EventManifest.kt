package com.harmonicinc.clientsideadtracking.tracking.model

import org.json.JSONArray
import org.json.JSONObject

class EventManifest {
    val dataRange: DataRange = DataRange(0, 0)
    val adBreaks: ArrayList<AdBreak> = ArrayList()

    // Improvements: Use Kotlin serialization
    fun parse(eventManifestStr: String) {
        val eventJson = JSONObject(eventManifestStr)
        val dataRangeJson = eventJson.optJSONObject("dataRange")
        val adBreaksJson = eventJson.optJSONArray("adBreaks")
        if (dataRangeJson != null) {
            this.dataRange.parse(dataRangeJson)
        }
        if (adBreaksJson != null) {
            this.parseAdBreaks(adBreaksJson)
        }
    }

    private fun parseAdBreaks(adBreaksJson: JSONArray) {
        for (i in 0 until adBreaksJson.length()) {
            val adBreak = AdBreak()
            adBreak.parse(adBreaksJson.optJSONObject(i))
            this.adBreaks.add(adBreak)
        }
    }

    inner class DataRange(var start: Long, var end: Long) {
        fun parse(json: JSONObject) {
            this.start = json.optLong("start")
            this.end = json.optLong("end")
        }
    }
}