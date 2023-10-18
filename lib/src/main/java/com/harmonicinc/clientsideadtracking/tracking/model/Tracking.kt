package com.harmonicinc.clientsideadtracking.tracking.model

import org.json.JSONObject

class Tracking(
    var event: Event = Event.UNKNOWN,
    var url: String = "",
    var startTime: Long = 0L
) {
    var fired = false

    enum class Event {
        IMPRESSION,
        START,
        FIRST_QUARTILE,
        MIDPOINT,
        THIRD_QUARTILE,
        COMPLETE,
        CLICK_ABSTRACT_TYPE,
        CLICK_TRACKING,
        PAUSE,
        RESUME,
        BUFFER_START,
        BUFFER_END,
        VOLUME,
        SKIPPED,

        // Custom events
        STOPPED,
        UNKNOWN
    }

    fun parse(json: JSONObject) {
        this.event = this.eventMap(json.optString("event"))
        this.url = json.optJSONArray("signalingUrls")?.optString(0) ?: ""
        this.startTime = json.optLong("startTime")
    }

    private fun eventMap(eventString: String): Event {
        return when(eventString.lowercase()){
            "impression" -> Event.IMPRESSION
            "start" -> Event.START
            "firstquartile" -> Event.FIRST_QUARTILE
            "midpoint" -> Event.MIDPOINT
            "thirdquartile" -> Event.THIRD_QUARTILE
            "complete" -> Event.COMPLETE
            "clickabstracttype" -> Event.CLICK_ABSTRACT_TYPE
            "clicktracking" -> Event.CLICK_TRACKING
            else -> Event.UNKNOWN
        }
    }
}