package com.harmonicinc.vosplayer.addon.csab

import org.json.JSONObject

data class CsabConfig(
   val palEnabled: Boolean
) {
   constructor(json: JSONObject) : this(parseJson(json).palEnabled)
}

private fun parseJson(json: JSONObject): CsabConfig {
   return CsabConfig(json.optBoolean("palEnabled", false))
}
