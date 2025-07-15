package com.harmonicinc.clientsideadtracking

import android.util.Log
import com.harmonicinc.clientsideadtracking.model.SessionResult
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

    suspend fun getSessionIdAndUrl(url: String): SessionResult? = withContext(coroutineContext) {
        val req = Request.Builder().url(url).build()
        client.newCall(req).execute().use { response ->
            // First check for redirects
            if ((response.code == HttpURLConnection.HTTP_MOVED_PERM || response.code == HttpURLConnection.HTTP_MOVED_TEMP) && response.headers["location"] != null) {
                val redirectUrl = response.headers["location"]!!
                val resolvedUrl = resolveUrl(url, redirectUrl)
                val queryList = getSessionIdFromUrl(resolvedUrl)
                return@withContext if (queryList.isNotEmpty()) SessionResult(queryList[0], resolvedUrl) else null
            }
            
            // If no redirect, parse the manifest content
            if (response.code == HttpURLConnection.HTTP_OK) {
                val body = response.body?.string()
                if (body != null) {
                    return@withContext parseSessionFromManifest(body, url)
                }
            }
            
            return@withContext null
        }
    }

    suspend fun getInitResponse(url: String): InitResponse? = withContext(coroutineContext) {
        // First try GET request with initSession=true query param
        val getUrl = URIBuilder(url).addParameter("initSession", "true").build().toString()
        val getReq = Request.Builder().url(getUrl).build()
        
        client.newCall(getReq).execute().use { response ->
            if (response.code == HttpURLConnection.HTTP_OK) {
                val body = response.body?.string()
                if (body != null) {
                    try {
                        return@withContext Json.decodeFromString<InitResponse>(body)
                    } catch (e: Exception) {
                        Log.d("OkHttpService", "GET request response could not be parsed as InitResponse, falling back to POST: ${e.message}")
                        // Continue to POST fallback
                    }
                }
            }
        }
        
        // Fallback to POST request
        val postReq = Request.Builder().url(url).post("".toRequestBody()).build()
        client.newCall(postReq).execute().use {
            if (it.code == HttpURLConnection.HTTP_OK) {
                val body = it.body?.string()
                return@withContext if (body != null) {
                    try {
                        Json.decodeFromString<InitResponse>(body)
                    } catch (e: Exception) {
                        Log.e("OkHttpService", "Failed to decode POST response: ${e.message}", e)
                        null
                    }
                } else null
            } else {
                Log.e("OkHttpService", "Init POST request failed with status code: ${it.code}")
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

    private fun parseSessionFromManifest(manifestContent: String, baseUrl: String): SessionResult? {
        return if (isHlsManifest(manifestContent)) {
            parseHlsManifestForSession(manifestContent, baseUrl)
        } else if (isDashManifest(manifestContent)) {
            parseDashManifestForSession(manifestContent, baseUrl)
        } else {
            null
        }
    }

    private fun isHlsManifest(content: String): Boolean {
        return content.trim().startsWith("#EXTM3U")
    }

    private fun isDashManifest(content: String): Boolean {
        return content.trim().startsWith("<") && content.contains("<MPD")
    }

    private fun resolveUrl(baseUrl: String, relativeUrl: String): String {
        return try {
            val base = java.net.URL(baseUrl)
            val resolved = java.net.URL(base, relativeUrl)
            resolved.toString()
        } catch (e: Exception) {
            Log.w("OkHttpService", "Failed to resolve URL: $relativeUrl against $baseUrl")
            relativeUrl
        }
    }

    private fun parseHlsManifestForSession(content: String, baseUrl: String): SessionResult? {
        val lines = content.lines()
        
        // Look for the first media playlist URL (non-master playlist)
        for (line in lines) {
            val trimmedLine = line.trim()
            if (trimmedLine.isNotEmpty() && !trimmedLine.startsWith("#")) {
                // This is a URL line
                val resolvedUrl = resolveUrl(baseUrl, trimmedLine)
                val sessionIds = getSessionIdFromUrl(resolvedUrl)
                if (sessionIds.isNotEmpty()) {
                    val modifiedBaseUrl = buildModifiedBaseUrl(baseUrl, resolvedUrl)
                    return SessionResult(sessionIds[0], modifiedBaseUrl)
                }
            }
        }
        return null
    }

    private fun buildModifiedBaseUrl(baseUrl: String, resolvedUrl: String): String {
        return try {
            val baseUri = java.net.URL(baseUrl)
            val resolvedUri = java.net.URL(resolvedUrl)
            
            // Check if the resolved URL path has the prefix "/pmm-"
            val resolvedPath = resolvedUri.path
            val basePath = baseUri.path
            
            val finalPath = if (resolvedPath.startsWith("/pmm-")) {
                // Extract only the /pmm-???/ part from the resolved path
                val pmmMatch = Regex("(/pmm-[^/]*)").find(resolvedPath)
                if (pmmMatch != null) {
                    val pmmPath = pmmMatch.groupValues[1]
                    // Prepend only the pmm path to the base path
                    pmmPath + basePath
                } else {
                    basePath
                }
            } else {
                basePath
            }
            
            // Handle query parameters
            val resolvedQuery = resolvedUri.query
            val finalQuery = if (resolvedQuery != null) {
                // Use resolved URL's query params but remove "isstream" parameter
                val params = URIBuilder("?$resolvedQuery").queryParams
                val filteredParams = params.filter { !it.name.equals("isstream", ignoreCase = true) }
                if (filteredParams.isNotEmpty()) {
                    filteredParams.joinToString("&") { "${it.name}=${it.value}" }
                } else {
                    null
                }
            } else {
                baseUri.query
            }
            
            // Construct the final URL
            val finalUrl = java.net.URL(
                baseUri.protocol,
                baseUri.host,
                baseUri.port,
                finalPath + if (finalQuery != null) "?$finalQuery" else ""
            )
            
            finalUrl.toString()
        } catch (e: Exception) {
            Log.w("OkHttpService", "Failed to build modified base URL: ${e.message}")
            baseUrl
        }
    }

    private fun parseDashManifestForSession(content: String, baseUrl: String): SessionResult? {
        try {
            // Look for Location element in DASH manifest
            val locationRegex = Regex("<Location[^>]*>([^<]+)</Location>", RegexOption.IGNORE_CASE)
            val match = locationRegex.find(content)
            
            if (match != null) {
                val locationUrl = match.groupValues[1].trim()
                val resolvedUrl = resolveUrl(baseUrl, locationUrl)
                val sessionIds = getSessionIdFromUrl(resolvedUrl)
                if (sessionIds.isNotEmpty()) {
                    return SessionResult(sessionIds[0], resolvedUrl)
                }
            }
        } catch (e: Exception) {
            Log.w("OkHttpService", "Failed to parse DASH manifest: ${e.message}")
        }
        return null
    }
}