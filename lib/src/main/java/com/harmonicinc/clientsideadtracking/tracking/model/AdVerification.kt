package com.harmonicinc.clientsideadtracking.tracking.model

import org.json.JSONObject

class AdVerification(
    val vendor: String,
    val javascriptResourceUrl: String,
    val verificationParameters: String
) {
    constructor(json: JSONObject) : this(
        json.getString("vendor"),
        json.getJSONObject("javaScriptResource").getString("uri"),
        json.getString("verificationParameters")
    )
}