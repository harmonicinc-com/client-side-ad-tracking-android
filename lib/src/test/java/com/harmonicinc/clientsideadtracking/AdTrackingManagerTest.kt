package com.harmonicinc.clientsideadtracking

import android.app.Activity
import android.view.ViewGroup
import android.widget.FrameLayout
import com.android.volley.Header
import com.android.volley.mock.MockHttpStack
import com.android.volley.toolbox.HttpResponse
import com.google.ads.interactivemedia.pal.NonceLoader
import com.harmonicinc.clientsideadtracking.player.PlayerAdapter
import com.harmonicinc.clientsideadtracking.tracking.util.Constants.SESSION_ID_QUERY_PARAM_KEY
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Ignore
import org.junit.Test
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import java.net.HttpURLConnection
import kotlin.time.DurationUnit
import kotlin.time.toDuration

@RunWith(RobolectricTestRunner::class)
class AdTrackingManagerTest {
    private var player = mockk<PlayerAdapter>()
    private var playerView = mockk<ViewGroup>()
    private var nonceLoader = mockk<NonceLoader>()

    private lateinit var overlayViewContainer: FrameLayout
    private lateinit var activity: Activity

    @Before
    fun setUp() {
        activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        overlayViewContainer = FrameLayout(activity)
    }

    @Test
    @Ignore("WIP")
    fun testNonceAppend() = runTest(timeout = 100.toDuration(DurationUnit.SECONDS)) {
        val sessId = "test1234"
        val baseUrl = "http://www.example.com"
        val responseHeaders = listOf(Header("location", "$baseUrl&sessId=$sessId"))
        val fakeResponse = HttpResponse(HttpURLConnection.HTTP_MOVED_PERM, responseHeaders)
        val adTrackingParams = AdTrackingManagerParams(
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
            null
        )

        val mockHttpStack = MockHttpStack()
        mockHttpStack.setResponseToReturn(fakeResponse)
        val adTrackingManager = AdTrackingManager(activity, mockHttpStack, nonceLoader)
        adTrackingManager.prepareBeforeLoad(baseUrl, adTrackingParams)

        assertTrue(adTrackingManager.isSSAISupported())
        val actualUrls = adTrackingManager.appendNonceToUrl(listOf(baseUrl))
        assertTrue(actualUrls.isNotEmpty())
        assertTrue(actualUrls[0].contains("$SESSION_ID_QUERY_PARAM_KEY=$sessId"))
    }

    @Test
    @Ignore("WIP")
    fun testSubscribePlayerEvents() {
    }
}