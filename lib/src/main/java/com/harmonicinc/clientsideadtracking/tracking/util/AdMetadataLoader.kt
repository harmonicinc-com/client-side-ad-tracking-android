package com.harmonicinc.clientsideadtracking.tracking.util

import android.net.Uri
import com.android.volley.Request
import com.android.volley.RequestQueue
import com.android.volley.toolbox.StringRequest
import com.harmonicinc.clientsideadtracking.tracking.model.EventManifest
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

object AdMetadataLoader {
    suspend fun load(queue: RequestQueue, metadataUrl: String, sessionId: String, mpdTime: Long): EventManifest {
        var eventManifestStr = getEventManifest(queue, metadataUrl, sessionId, null)
        val event = EventManifest()
        event.parse(eventManifestStr)
        if (mpdTime !in event.dataRange.start ..event.dataRange.end) {
            eventManifestStr = getEventManifest(queue, metadataUrl, sessionId, mpdTime)
            event.parse(eventManifestStr)
        }
        return event
    }

    private suspend fun getEventManifest(queue: RequestQueue, metadataUrl: String, sessionId: String, startOverride: Long?): String = suspendCoroutine { cont ->
        val uriBuilder = Uri.parse(metadataUrl)
            .buildUpon()
            .appendQueryParameter(Constants.SESSION_ID_QUERY_PARAM_KEY, sessionId)
        if (startOverride != null) {
            uriBuilder.appendQueryParameter(Constants.PMM_METADATA_START_QUERY_PARAM_KEY, startOverride.toString())
        }

        val stringRequest = StringRequest(
            Request.Method.GET, uriBuilder.build().toString(),
            { res ->
                cont.resume(res)
            },
            { e ->
                cont.resumeWithException(e)
            }
        )
        queue.add(stringRequest)
    }
}