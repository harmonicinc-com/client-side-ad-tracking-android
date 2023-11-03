package com.harmonicinc.clientsideadtracking.tracking.util

import android.net.Uri
import com.harmonicinc.clientsideadtracking.OkHttpService
import com.harmonicinc.clientsideadtracking.tracking.model.EventManifest

object AdMetadataLoader {
    suspend fun load(okHttpService: OkHttpService, metadataUrl: String, sessionId: String, mpdTime: Long): EventManifest {
        var eventManifestStr = getEventManifest(okHttpService, metadataUrl, sessionId, null)
        val event = EventManifest()
        event.parse(eventManifestStr)
        if (mpdTime !in event.dataRange.start ..event.dataRange.end) {
            eventManifestStr = getEventManifest(okHttpService, metadataUrl, sessionId, mpdTime)
            event.parse(eventManifestStr)
        }
        return event
    }

    private suspend fun getEventManifest(okHttpService: OkHttpService, metadataUrl: String, sessionId: String, startOverride: Long?): String {
        val uriBuilder = Uri.parse(metadataUrl)
            .buildUpon()
            .appendQueryParameter(Constants.SESSION_ID_QUERY_PARAM_KEY, sessionId)
        if (startOverride != null) {
            uriBuilder.appendQueryParameter(Constants.PMM_METADATA_START_QUERY_PARAM_KEY, startOverride.toString())
        }
        return okHttpService.getString(uriBuilder.build().toString())
    }
}