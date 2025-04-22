package com.harmonicinc.clientsideadtracking

import android.util.Log
import com.harmonicinc.clientsideadtracking.tracking.model.InitResponse
import com.harmonicinc.clientsideadtracking.tracking.util.Constants.SESSION_ID_QUERY_PARAM_KEY
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.apache.hc.core5.net.URIBuilder
import java.net.HttpURLConnection
import kotlin.coroutines.CoroutineContext

class OkHttpService(
    private val coroutineContext: CoroutineContext = Dispatchers.IO
) {
    private val client = OkHttpClient.Builder().followRedirects(false).build()

    suspend fun getSessionId(url: String): String? = withContext(coroutineContext) {
        val req = Request.Builder().url(url).build()
        client.newCall(req).execute().use {
            if ((it.code == HttpURLConnection.HTTP_MOVED_PERM || it.code == HttpURLConnection.HTTP_MOVED_TEMP) && it.headers["location"] != null) {
                val queryList = getSessionIdFromUrl(it.headers["location"]!!)
                return@withContext if (queryList.isNotEmpty()) queryList[0] else null
            }
            return@withContext null
        }
    }

    suspend fun getInitResponse(url: String): InitResponse? = withContext(coroutineContext) {
        val req = Request.Builder().url(url).post("".toRequestBody()).build()
        client.newCall(req).execute().use {
            if (it.code == HttpURLConnection.HTTP_OK) {
                val body = it.body?.string()
                return@withContext if (body != null) {
                    try {
                        Json.decodeFromString<InitResponse>(body)
                    } catch (e: Exception) {
                        Log.e("OkHttpService", "Failed to decode response: ${e.message}", e)
                        null
                    }
                } else null
            } else {
                Log.e("OkHttpService", "Init request failed with status code: ${it.code}")
            }
            return@withContext null
        }
    }

    suspend fun getString(url: String): String = withContext(coroutineContext) {
        val req = Request.Builder().url(url).build()
        client.newCall(req).execute().use {
            return@withContext it.body?.string() ?: ""
        }
    }

    private fun getSessionIdFromUrl(url: String): List<String> {
        val params = URIBuilder(url).queryParams
        return params
            .filter { it.name.equals(SESSION_ID_QUERY_PARAM_KEY, ignoreCase = true) }
            .map { it.value }
    }
}