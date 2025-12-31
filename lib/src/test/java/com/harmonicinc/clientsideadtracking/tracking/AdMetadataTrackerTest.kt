package com.harmonicinc.clientsideadtracking.tracking

import com.harmonicinc.clientsideadtracking.OkHttpService
import com.harmonicinc.clientsideadtracking.player.MockPlayerAdapter
import com.harmonicinc.clientsideadtracking.tracking.model.Ad
import com.harmonicinc.clientsideadtracking.tracking.model.AdBreak
import com.harmonicinc.clientsideadtracking.tracking.model.EventManifest
import com.harmonicinc.clientsideadtracking.tracking.model.Tracking
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.impl.annotations.MockK
import io.mockk.justRun
import io.mockk.slot
import io.mockk.spyk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.time.Duration.Companion.seconds

@RunWith(RobolectricTestRunner::class)
@OptIn(ExperimentalCoroutinesApi::class)
class AdMetadataTrackerTest {
    private val mockPlayerAdapter = MockPlayerAdapter()
    
    @MockK
    private lateinit var mockOkHttpService: OkHttpService

    @Before
    fun setup() {
        MockKAnnotations.init(this)
    }

    @Test
    fun testEventTracking() = runTest(timeout = 10.seconds) {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val testScope = TestScope(testDispatcher)
        
        val baseUrl = "http://localhost/metadata"
        val sessionId = "test"

        // Mock metadata response
        val jsonStr = javaClass.classLoader!!.getResourceAsStream("metadata/normal.json").bufferedReader().use { it.readText() }
        val expectedMetadata = EventManifest()
        expectedMetadata.parse(jsonStr)

        // Mock the OkHttpService to return the metadata JSON
        coEvery { mockOkHttpService.getString(any()) } returns jsonStr
        
        mockPlayerAdapter.setCurrentPositionMs(1560332865457 + 1) // anywhere between start & Q1

        val mockAdBreakListener: AdBreakListener = spyk()
        val mockAdProgressListener: AdProgressListener = spyk()
        val actualAdBreak = slot<AdBreak?>()
        val actualAd = slot<Ad?>()
        val actualTracking = mutableListOf<Tracking>()
        justRun { mockAdBreakListener.onCurrentAdBreakUpdate(captureNullable(actualAdBreak)) }
        justRun { mockAdBreakListener.onCurrentAdUpdate(captureNullable(actualAd)) }
        justRun { mockAdProgressListener.onAdProgress(any(), any(), capture(actualTracking)) }

        val tracker = AdMetadataTracker(mockPlayerAdapter, mockOkHttpService, testScope, testScope)
        tracker.addAdBreakListener(mockAdBreakListener)
        tracker.addAdProgressListener(mockAdProgressListener)
        tracker.onPlay(baseUrl, sessionId)

        testScheduler.advanceTimeBy(101L) // "wait" at least 100ms for post progress loop to be executed
        testScheduler.runCurrent()

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
        testScheduler.advanceTimeBy(100L) // "wait" another 100ms for post progress loop to be executed
        testScheduler.runCurrent()

        verify { mockAdBreakListener.onCurrentAdBreakUpdate(null) }
        verify { mockAdBreakListener.onCurrentAdUpdate(null) }
        verify { mockAdBreakListener.onCurrentTrackingUpdate(null) }
        verify { mockAdProgressListener.onAdProgress(null, null, any(Tracking::class)) }

        tracker.onStopped()
        testScope.cancel()
    }

    @Test
    fun testPlayerInitiatedEventsAreNotAutoFired() = runTest(timeout = 10.seconds) {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val testScope = TestScope(testDispatcher)
        
        val baseUrl = "http://localhost/metadata"
        val sessionId = "test"

        // Load mock metadata with player-initiated events (pause, resume, mute, unmute)
        val jsonStr = javaClass.classLoader!!.getResourceAsStream("metadata/with_player_events.json")
            .bufferedReader().use { it.readText() }
        val expectedMetadata = EventManifest()
        expectedMetadata.parse(jsonStr)

        // Mock the OkHttpService to return the metadata JSON
        coEvery { mockOkHttpService.getString(any()) } returns jsonStr
        
        // Set player position to the middle of the ad (between start & Q1)
        mockPlayerAdapter.setCurrentPositionMs(1560332865457 + 1)

        val mockAdBreakListener: AdBreakListener = spyk()
        val mockAdProgressListener: AdProgressListener = spyk()
        val actualTracking = mutableListOf<Tracking>()
        justRun { mockAdBreakListener.onCurrentAdBreakUpdate(any()) }
        justRun { mockAdBreakListener.onCurrentAdUpdate(any()) }
        justRun { mockAdProgressListener.onAdProgress(any(), any(), capture(actualTracking)) }

        val tracker = AdMetadataTracker(mockPlayerAdapter, mockOkHttpService, testScope, testScope)
        tracker.addAdBreakListener(mockAdBreakListener)
        tracker.addAdProgressListener(mockAdProgressListener)
        tracker.onPlay(baseUrl, sessionId)

        testScheduler.advanceTimeBy(101L) // Wait at least 100ms for post progress loop to be executed
        testScheduler.runCurrent()

        // Verify that player-initiated events (pause, resume, mute, unmute) are NOT auto-fired
        val firedEventTypes = actualTracking.map { it.event }
        
        // These events should be fired based on playback time
        assert(firedEventTypes.contains(Tracking.Event.IMPRESSION)) { "IMPRESSION should be auto-fired" }
        assert(firedEventTypes.contains(Tracking.Event.START)) { "START should be auto-fired" }
        
        // These player-initiated events should NOT be auto-fired
        assert(!firedEventTypes.contains(Tracking.Event.PAUSE)) { "PAUSE should NOT be auto-fired" }
        assert(!firedEventTypes.contains(Tracking.Event.RESUME)) { "RESUME should NOT be auto-fired" }
        assert(!firedEventTypes.contains(Tracking.Event.MUTE)) { "MUTE should NOT be auto-fired" }
        assert(!firedEventTypes.contains(Tracking.Event.UNMUTE)) { "UNMUTE should NOT be auto-fired" }
        assert(!firedEventTypes.contains(Tracking.Event.CLICK_TRACKING)) { "CLICK_TRACKING should NOT be auto-fired" }

        tracker.onStopped()
        testScope.cancel()
    }

    @Test
    fun testGetTrackingUrlsForEvent() = runTest(timeout = 10.seconds) {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val testScope = TestScope(testDispatcher)
        
        val baseUrl = "http://localhost/metadata"
        val sessionId = "test"

        // Load mock metadata with player-initiated events
        val jsonStr = javaClass.classLoader!!.getResourceAsStream("metadata/with_player_events.json")
            .bufferedReader().use { it.readText() }

        // Mock the OkHttpService to return the metadata JSON
        coEvery { mockOkHttpService.getString(any()) } returns jsonStr
        
        // Set player position to the middle of the ad
        mockPlayerAdapter.setCurrentPositionMs(1560332865457 + 1)

        val tracker = AdMetadataTracker(mockPlayerAdapter, mockOkHttpService, testScope, testScope)
        tracker.onPlay(baseUrl, sessionId)

        testScheduler.advanceTimeBy(101L) // Wait for metadata to load and current ad to be set
        testScheduler.runCurrent()

        // Verify getTrackingUrlsForEvent returns correct URLs for player-initiated events
        val muteUrls = tracker.getTrackingUrlsForEvent(Tracking.Event.MUTE)
        assert(muteUrls.isNotEmpty()) { "Should return mute tracking URLs" }
        assert(muteUrls[0].contains("cn=mute")) { "Mute URL should contain cn=mute" }

        val unmuteUrls = tracker.getTrackingUrlsForEvent(Tracking.Event.UNMUTE)
        assert(unmuteUrls.isNotEmpty()) { "Should return unmute tracking URLs" }
        assert(unmuteUrls[0].contains("cn=unmute")) { "Unmute URL should contain cn=unmute" }

        val pauseUrls = tracker.getTrackingUrlsForEvent(Tracking.Event.PAUSE)
        assert(pauseUrls.isNotEmpty()) { "Should return pause tracking URLs" }
        assert(pauseUrls[0].contains("cn=pause")) { "Pause URL should contain cn=pause" }

        val resumeUrls = tracker.getTrackingUrlsForEvent(Tracking.Event.RESUME)
        assert(resumeUrls.isNotEmpty()) { "Should return resume tracking URLs" }
        assert(resumeUrls[0].contains("cn=resume")) { "Resume URL should contain cn=resume" }

        tracker.onStopped()
        testScope.cancel()
    }

    @Test
    fun testGetTrackingUrlsForEventReturnsEmptyWhenNoAd() = runTest(timeout = 10.seconds) {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val testScope = TestScope(testDispatcher)
        
        val baseUrl = "http://localhost/metadata"
        val sessionId = "test"

        // Load mock metadata
        val jsonStr = javaClass.classLoader!!.getResourceAsStream("metadata/with_player_events.json")
            .bufferedReader().use { it.readText() }

        // Mock the OkHttpService to return the metadata JSON
        coEvery { mockOkHttpService.getString(any()) } returns jsonStr
        
        // Set player position BEFORE the ad break starts
        mockPlayerAdapter.setCurrentPositionMs(1560332865457 - 10000)

        val tracker = AdMetadataTracker(mockPlayerAdapter, mockOkHttpService, testScope, testScope)
        tracker.onPlay(baseUrl, sessionId)

        testScheduler.advanceTimeBy(101L) // Wait for metadata to load
        testScheduler.runCurrent()

        // When no ad is playing, getTrackingUrlsForEvent should return empty list
        val muteUrls = tracker.getTrackingUrlsForEvent(Tracking.Event.MUTE)
        assert(muteUrls.isEmpty()) { "Should return empty list when no ad is playing" }

        tracker.onStopped()
        testScope.cancel()
    }
}