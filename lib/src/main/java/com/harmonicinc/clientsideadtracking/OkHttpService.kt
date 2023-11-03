package com.harmonicinc.clientsideadtracking

import com.harmonicinc.clientsideadtracking.tracking.util.Constants.SESSION_ID_QUERY_PARAM_KEY
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
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