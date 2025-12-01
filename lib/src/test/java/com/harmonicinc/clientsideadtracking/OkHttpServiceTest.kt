package com.harmonicinc.clientsideadtracking

import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.net.HttpURLConnection

@RunWith(RobolectricTestRunner::class)
class OkHttpServiceTest {

    private lateinit var mockWebServer: MockWebServer

    @Before
    fun setUp() {
        mockWebServer = MockWebServer()
        mockWebServer.start()
    }

    @After
    fun tearDown() {
        mockWebServer.shutdown()
    }

    @Test
    fun `getString sets User-Agent header when userAgent is provided`() = runTest {
        // Given
        val expectedUserAgent = "TestApp/1.0 (Android 13; Pixel 7)"
        val okHttpService = OkHttpService(userAgent = expectedUserAgent)
        
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(HttpURLConnection.HTTP_OK)
                .setBody("test response")
        )

        // When
        val url = mockWebServer.url("/test").toString()
        okHttpService.getString(url)

        // Then
        val recordedRequest = mockWebServer.takeRequest()
        assertEquals(expectedUserAgent, recordedRequest.getHeader("User-Agent"))
    }

    @Test
    fun `getString does not override User-Agent when userAgent is null`() = runTest {
        // Given
        val okHttpService = OkHttpService(userAgent = null)
        
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(HttpURLConnection.HTTP_OK)
                .setBody("test response")
        )

        // When
        val url = mockWebServer.url("/test").toString()
        okHttpService.getString(url)

        // Then
        val recordedRequest = mockWebServer.takeRequest()
        // OkHttp sets a default User-Agent, so it should not be our custom one
        val userAgent = recordedRequest.getHeader("User-Agent")
        // Default OkHttp User-Agent starts with "okhttp/"
        assertEquals(true, userAgent?.startsWith("okhttp/"))
    }
}
