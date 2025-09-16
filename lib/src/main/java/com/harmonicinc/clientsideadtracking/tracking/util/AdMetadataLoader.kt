package com.harmonicinc.clientsideadtracking.tracking.util

import android.net.Uri
import com.harmonicinc.clientsideadtracking.OkHttpService
import com.harmonicinc.clientsideadtracking.tracking.model.EventManifest

object AdMetadataLoader {
    suspend fun load(okHttpService: OkHttpService, metadataUrl: String, sessionId: String): EventManifest {
        val eventManifestStr = getEventManifest(okHttpService, metadataUrl, sessionId)
        val event = EventManifest()
        event.parse(eventManifestStr)
        return event
    }

    private suspend fun getEventManifest(okHttpService: OkHttpService, metadataUrl: String, sessionId: String): String {
        val uri = Uri.parse(metadataUrl)
        val builder = uri.buildUpon()

        if (uri.getQueryParameter(Constants.SESSION_ID_QUERY_PARAM_KEY) == null) {
            builder.appendQueryParameter(Constants.SESSION_ID_QUERY_PARAM_KEY, sessionId)
        }
        return okHttpService.getString(builder.build().toString())
    }
}