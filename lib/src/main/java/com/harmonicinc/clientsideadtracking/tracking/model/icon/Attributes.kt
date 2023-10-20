package com.harmonicinc.clientsideadtracking.tracking.model.icon

import com.harmonicinc.clientsideadtracking.tracking.extensions.optDuration
import com.harmonicinc.clientsideadtracking.tracking.extensions.optIntNull
import org.json.JSONObject

class Attributes(json: JSONObject) {
    private val program: String
    val width: Int
    val height: Int
    val xPosition: Int?
    var xPositionStr: String? = null
    val yPosition: Int?
    var yPositionStr: String? = null
    private val duration: Long
    private val offset: Long
    private val apiFramework: String
    private val altText: String

    init {
        program = json.optString("program")
        width = json.optInt("width")
        height = json.optInt("height")
        xPosition = json.optIntNull("xPosition")
        if (xPosition == null) {
            xPositionStr = json.optString("xPosition")
        }
        yPosition = json.optIntNull("yPosition")
        if (yPosition == null) {
            yPositionStr = json.optString("yPosition")
        }
        duration = json.optDuration("duration")
        offset = json.optDuration("offset")
        apiFramework = json.optString("apiFramework")
        altText = json.optString("altText")
    }
}