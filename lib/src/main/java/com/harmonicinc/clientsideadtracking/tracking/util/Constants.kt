package com.harmonicinc.clientsideadtracking.tracking.util

object Constants {
    // PAL nonce properties
    const val PAL_DESCRIPTION_URL = "harmonicinc.com"
    const val PAL_PPID = ""
    val PAL_SUPPORTED_API = setOf(7)

    const val PAL_NONCE_QUERY_PARAM_KEY = "paln"
    const val SESSION_ID_QUERY_PARAM_KEY = "sessid"
    const val PMM_METADATA_START_QUERY_PARAM_KEY = "start"
    const val CSAT_INTENT_LOG_ACTION = "harmonicinc.csat.log"
}