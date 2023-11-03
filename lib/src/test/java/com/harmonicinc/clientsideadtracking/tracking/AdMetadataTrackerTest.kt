package com.harmonicinc.clientsideadtracking.tracking

import com.harmonicinc.clientsideadtracking.OkHttpService
import com.harmonicinc.clientsideadtracking.player.MockPlayerAdapter
import com.harmonicinc.clientsideadtracking.tracking.model.Ad
import com.harmonicinc.clientsideadtracking.tracking.model.AdBreak
import com.harmonicinc.clientsideadtracking.tracking.model.EventManifest
import com.harmonicinc.clientsideadtracking.tracking.model.Tracking
import io.mockk.MockKAnnotations
import io.mockk.justRun
import io.mockk.slot
import io.mockk.spyk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.QueueDispatcher
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.time.Duration.Companion.seconds

@RunWith(RobolectricTestRunner::class)
@OptIn(ExperimentalCoroutinesApi::class)
class AdMetadataTrackerTest {
    private lateinit var mockWebServer: MockWebServer
    private val mockPlayerAdapter = MockPlayerAdapter()

    @Before
    fun setup() {
        MockKAnnotations.init(this)
        mockWebServer = MockWebServer()
        mockWebServer.start(0)
    }

    @Test
    fun testEventTracking() = runTest(timeout = 10.seconds) {
        val baseUrl = mockWebServer.url("/metadata").toString()
        val sessionId = "test"

        // Mock metadata response
        val jsonStr = javaClass.classLoader!!.getResourceAsStream("metadata/normal.json").bufferedReader().use { it.readText() }
        val expectedMetadata = EventManifest()
        expectedMetadata.parse(jsonStr)

        val dispatcher = QueueDispatcher()
        dispatcher.setFailFast(MockResponse().setBody(jsonStr))
        mockWebServer.dispatcher = dispatcher
        mockPlayerAdapter.setCurrentPositionMs(1560332865457 + 1) // anywhere between start & Q1

        val mockAdBreakListener: AdBreakListener = spyk()
        val mockAdProgressListener: AdProgressListener = spyk()
        val actualAdBreak = slot<AdBreak?>()
        val actualAd = slot<Ad?>()
        val actualTracking = mutableListOf<Tracking>()
        justRun { mockAdBreakListener.onCurrentAdBreakUpdate(captureNullable(actualAdBreak)) }
        justRun { mockAdBreakListener.onCurrentAdUpdate(captureNullable(actualAd)) }
        justRun { mockAdProgressListener.onAdProgress(any(), any(), capture(actualTracking)) }

        val tracker = AdMetadataTracker(mockPlayerAdapter, OkHttpService(coroutineContext), backgroundScope, backgroundScope)
        tracker.addAdBreakListener(mockAdBreakListener)
        tracker.addAdProgressListener(mockAdProgressListener)
        tracker.onPlay(baseUrl, sessionId)

        advanceTimeBy(101L) // "wait" at least 100ms for post progress loop to be executed

        val expectedAdBreak = expectedMetadata.adBreaks[0]
        val expectedAd = expectedAdBreak.ads[0]

        verify { mockAdBreakListener.onCurrentAdBreakUpdate(any(AdBreak::class)) }
        verify { mockAdBreakListener.onCurrentAdUpdate(any(Ad::class)) }
        verify { mockAdProgressListener.onAdProgress(any(AdBreak::class), any(Ad::class), any(Tracking::class)) }

        assertEquals(actualAdBreak.captured?.id, expectedAdBreak.id)
        assertEquals(actualAd.captured?.id, expectedAd.id)
        // The mocked PT is between start & Q1, so expect 2 events: IMPRESSION & START
        assertEquals(actualTracking[0].event, expectedAd.tracking[0].event)
        assertEquals(actualTracking[1].event, expectedAd.tracking[1].event)
        assertEquals(actualTracking.size, 2)

        mockPlayerAdapter.setCurrentPositionMs(Long.MAX_VALUE) // trigger ad break end
        advanceTimeBy(100L) // "wait" another 100ms for post progress loop to be executed

        verify { mockAdBreakListener.onCurrentAdBreakUpdate(null) }
        verify { mockAdBreakListener.onCurrentAdUpdate(null) }
        verify { mockAdBreakListener.onCurrentTrackingUpdate(null) }
        verify { mockAdProgressListener.onAdProgress(null, null, any(Tracking::class)) }

        tracker.onStopped()
    }
}