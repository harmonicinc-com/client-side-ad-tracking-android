package com.harmonicinc.clientsideadtracking

import android.app.Activity
import android.content.Context
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import com.google.ads.interactivemedia.pal.NonceLoader
import com.google.ads.interactivemedia.pal.NonceManager
import com.google.android.gms.tasks.Task
import com.harmonicinc.clientsideadtracking.player.MockPlayerAdapter
import com.harmonicinc.clientsideadtracking.tracking.AdMetadataTracker
import com.harmonicinc.clientsideadtracking.tracking.client.OMSDKClient
import com.harmonicinc.clientsideadtracking.tracking.model.InitResponse
import com.harmonicinc.clientsideadtracking.tracking.util.Constants.PAL_NONCE_QUERY_PARAM_KEY
import com.harmonicinc.clientsideadtracking.tracking.util.Constants.SESSION_ID_QUERY_PARAM_KEY
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.net.HttpURLConnection
import kotlin.time.Duration.Companion.seconds
import kotlin.time.DurationUnit
import kotlin.time.toDuration

// Using JUnit 4 is mandatory as Robolectric is still not compatible with v5
@RunWith(RobolectricTestRunner::class)
class AdTrackingManagerTest {
    @MockK
    private lateinit var nonceLoader: NonceLoader
    @MockK
    private lateinit var nonceManager: NonceManager
    @MockK
    private lateinit var playerView: ViewGroup

    private lateinit var overlayViewContainer: FrameLayout
    private lateinit var activity: Activity
    private lateinit var mockWebServer: MockWebServer
    private lateinit var context: Context

    private val mockPlayerAdapter = MockPlayerAdapter()
    private val adTrackingParams = AdTrackingManagerParams(
        "",
        true,
        "unittest",
        "0.0.1",
        "",
        setOf(7),
        0,
        0,
        willAdAutoplay = false,
        willAdPlayMuted = false,
        continuousPlayback = false,
        null,
        null,
        null,
        initRequest = false
    )

    @Before
    fun setUpBeforeEach() {
        MockKAnnotations.init(this)
        activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        context = RuntimeEnvironment.getApplication().applicationContext
        overlayViewContainer = FrameLayout(activity)

        mockWebServer = MockWebServer()
        mockWebServer.start(0)
    }

    @After
    fun cleanupAfterEach() {
        mockWebServer.shutdown()
    }

    @Test
    fun testNonceAndSessId() = runTest(timeout = 10.seconds) {
        val expectSessId = "testSessId"
        val expectNonce = "testNonce"
        val baseUrl = mockWebServer.url("/master.mpd").toString()

        val mockResponse = MockResponse()
            .setResponseCode(HttpURLConnection.HTTP_MOVED_PERM)
            .addHeader("location", "$baseUrl?$SESSION_ID_QUERY_PARAM_KEY=$expectSessId")
        mockWebServer.enqueue(mockResponse)
        every { nonceManager.nonce } returns expectNonce
        every { nonceLoader.loadNonceManager(any()) } returns getFakeTask(nonceManager)

        val adTrackingManager = AdTrackingManager(activity, nonceLoader, OkHttpService())
        adTrackingManager.prepareBeforeLoad(baseUrl, adTrackingParams)
        assertTrue(adTrackingManager.isSSAISupported())

        val actualUrls = adTrackingManager.appendNonceToUrl(listOf(baseUrl))
        assertTrue(actualUrls.isNotEmpty())
        assertTrue(actualUrls[0].contains("$PAL_NONCE_QUERY_PARAM_KEY=$expectNonce"))
        assertTrue(actualUrls[0].contains("$SESSION_ID_QUERY_PARAM_KEY=$expectSessId"))
    }

    @Test
    fun testInitRequest() = runTest(timeout = 10.seconds) {
        val expectSessId = "testSessId"
        val expectNonce = "testNonce"
        val baseUrl = mockWebServer.url("/master.mpd").toString()

        val testInitResponse = InitResponse(
            manifestUrl = "/manifest?$SESSION_ID_QUERY_PARAM_KEY=$expectSessId",
            trackingUrl = "/tracking?$SESSION_ID_QUERY_PARAM_KEY=$expectSessId"
        )

        val mockResponse = MockResponse()
            .setResponseCode(HttpURLConnection.HTTP_OK)
            .setBody(Json.encodeToString(InitResponse.serializer(), testInitResponse))
        mockWebServer.enqueue(mockResponse)
        every { nonceManager.nonce } returns expectNonce
        every { nonceLoader.loadNonceManager(any()) } returns getFakeTask(nonceManager)

        val newAdTrackingParams = adTrackingParams.copy(
            initRequest = true,
        )

        val adTrackingManager = AdTrackingManager(activity, nonceLoader, OkHttpService())
        adTrackingManager.prepareBeforeLoad(baseUrl, newAdTrackingParams)
        assertTrue(adTrackingManager.isSSAISupported())

        val actualUrls = adTrackingManager.appendNonceToUrl(listOf(baseUrl))
        assertTrue(actualUrls.isNotEmpty())
        assertTrue(actualUrls[0].contains("$PAL_NONCE_QUERY_PARAM_KEY=$expectNonce"))
        assertTrue(actualUrls[0].contains("$SESSION_ID_QUERY_PARAM_KEY=$expectSessId"))
    }

    @Test
    fun testSubscribePlayerEvents() = runTest(timeout = 10.seconds){
        val baseUrl = mockWebServer.url("/master.mpd").toString()
        val mockResponse = MockResponse()
            .setResponseCode(HttpURLConnection.HTTP_MOVED_PERM)
            .addHeader("location", "$baseUrl?$SESSION_ID_QUERY_PARAM_KEY=")
        mockWebServer.enqueue(mockResponse)

        mockkConstructor(AdTrackingManager::class)
        every { nonceManager.nonce } returns ""
        every { nonceLoader.loadNonceManager(any()) } returns getFakeTask(nonceManager)

        val adTrackingManager = AdTrackingManager(activity, nonceLoader, OkHttpService())

        adTrackingManager.prepareBeforeLoad(baseUrl, adTrackingParams)

        justRun { nonceManager.sendPlaybackStart() }
        justRun { nonceManager.sendPlaybackEnd() }
        justRun { nonceManager.sendAdClick() }
        justRun { nonceManager.sendAdTouch(any()) }

        adTrackingManager.onPlay(context, mockPlayerAdapter, overlayViewContainer, playerView)
        val mockMetadataTracker = mockk<AdMetadataTracker>()
        every { mockMetadataTracker.isPlayingAd() } returns true
        justRun { mockMetadataTracker.onStopped() }
        adTrackingManager.metadataTracker = mockMetadataTracker

        val mockEvent: MotionEvent = mockk()
        val mockView: View = mockk()
        val expectVolume = 0.5f
        every { mockView.performClick() } returns true

        // Mock OMSDK client
        val mockOMSDKClient = mockk<OMSDKClient>()
        justRun { mockOMSDKClient.bufferStart() }
        justRun { mockOMSDKClient.bufferEnd() }
        justRun { mockOMSDKClient.pause() }
        justRun { mockOMSDKClient.resume() }
        justRun { mockOMSDKClient.volumeChange(expectVolume) }
        adTrackingManager.omsdkClient = mockOMSDKClient

        mockPlayerAdapter.onVideoAdClick()
        mockPlayerAdapter.onVideoAdViewTouch(mockView, mockEvent)
        mockPlayerAdapter.onBufferStart()
        mockPlayerAdapter.onBufferEnd()
        mockPlayerAdapter.onResume()
        mockPlayerAdapter.onPause()
        mockPlayerAdapter.onVolumeChanged(expectVolume)

        verify { nonceManager.sendPlaybackStart() }
        verify { nonceManager.sendAdClick() }
        verify { nonceManager.sendAdTouch(mockEvent) }

        verify { mockOMSDKClient.bufferStart() }
        verify { mockOMSDKClient.bufferEnd() }
        verify { mockOMSDKClient.pause() }
        verify { mockOMSDKClient.resume() }
        verify { mockOMSDKClient.volumeChange(expectVolume) }

        adTrackingManager.cleanupAfterStop()
        verify { nonceManager.sendPlaybackEnd() }
    }

    private fun <T: Any> getFakeTask(mockResult: T): Task<T> {
        val fakeTask = mockk<Task<T>>()
        every { fakeTask.isComplete } returns true
        every { fakeTask.exception } returns null
        every { fakeTask.isCanceled } returns false
        every { fakeTask.result } returns mockResult
        return fakeTask
    }
}