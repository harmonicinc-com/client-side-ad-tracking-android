package com.harmonicinc.clientsideadtracking.tracking.model

import com.harmonicinc.clientsideadtracking.tracking.extensions.toList
import org.json.JSONObject
import java.security.MessageDigest

class Tracking(
    var event: Event = Event.UNKNOWN,
    var url: List<String> = listOf(),
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
        MUTE,
        UNMUTE,
        BUFFER_START,
        BUFFER_END,
        VOLUME,
        SKIPPED,

        // Custom events
        STOPPED,
        UNKNOWN;

        companion object {
            /**
             * Player-initiated events that should only fire when the user performs
             * the corresponding action, not based on playback time.
             */
            val PLAYER_INITIATED_EVENTS = setOf(
                PAUSE,
                RESUME,
                MUTE,
                UNMUTE,
                CLICK_ABSTRACT_TYPE,
                CLICK_TRACKING,
            )
        }

        /**
         * Returns true if this event is player-initiated and should not be
         * auto-fired based on playback time.
         */
        fun isPlayerInitiated(): Boolean = this in PLAYER_INITIATED_EVENTS
    }

    fun parse(json: JSONObject) {
        this.event = this.eventMap(json.optString("event"))
        this.url = json.optJSONArray("signalingUrls")?.toList()?.filterIsInstance<String>() ?: listOf()
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
            "pause" -> Event.PAUSE
            "resume" -> Event.RESUME
            "mute" -> Event.MUTE
            "unmute" -> Event.UNMUTE
            else -> Event.UNKNOWN
        }
    }

    /**
     * Generate a unique identifier for this tracking event to maintain fired state
     * across metadata updates and seeking operations
     */
    fun getUniqueId(): String {
        val urlHash = if (url.isNotEmpty()) {
            val urlString = url.sorted().joinToString("")
            hashString(urlString)
        } else {
            "no_urls"
        }
        return "${startTime}_${event.name}_$urlHash"
    }

    private fun hashString(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }.take(8) // Take first 8 chars for brevity
    }
}