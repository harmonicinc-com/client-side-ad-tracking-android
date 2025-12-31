package com.harmonicinc.clientsideadtracking.tracking.client

import android.content.Context
import android.content.Intent
import com.harmonicinc.clientsideadtracking.OkHttpService
import com.harmonicinc.clientsideadtracking.tracking.AdMetadataTracker
import com.harmonicinc.clientsideadtracking.tracking.AdProgressListener
import com.harmonicinc.clientsideadtracking.tracking.EventLogListener
import com.harmonicinc.clientsideadtracking.tracking.model.EventLog
import com.harmonicinc.clientsideadtracking.tracking.model.EventManifest
import com.harmonicinc.clientsideadtracking.tracking.model.Tracking
import com.harmonicinc.clientsideadtracking.tracking.util.Constants
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.slot
import io.mockk.spyk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class PMMClientTest {

    @MockK
    private lateinit var okHttpService: OkHttpService
    
    @MockK
    private lateinit var tracker: AdMetadataTracker
    
    private lateinit var context: Context

    @Before
    fun setup() {
        MockKAnnotations.init(this)
        context = spyk(RuntimeEnvironment.getApplication().applicationContext)
    }

    @Test
    fun testBeaconSendingWithAdProgress() = runTest(timeout = 10.seconds) {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val testScope = TestScope(testDispatcher)
        
        val adProgressListenerSlot = slot<AdProgressListener>()
        val eventLogSlot = slot<EventLog>()
        val intentSlot = slot<Intent>()
        val mockEventLogListener = mockk<EventLogListener>()
        
        // Set up mocks
        justRun { tracker.addAdProgressListener(capture(adProgressListenerSlot)) }
        justRun { mockEventLogListener.onEvent(capture(eventLogSlot)) }
        justRun { context.sendBroadcast(capture(intentSlot)) }
        coEvery { okHttpService.getString(any()) } returns ""
        
        // Create PMMClient
        val client = PMMClient(
            tracker = tracker,
            okHttpService = okHttpService,
            context = context,
            coroutineScope = testScope,
            ioDispatcher = testDispatcher
        )
        client.setListener(mockEventLogListener)
        
        // Verify listener was registered
        verify { tracker.addAdProgressListener(adProgressListenerSlot.captured) }
        
        // Load mock metadata
        val jsonStr = javaClass.classLoader!!.getResourceAsStream("metadata/normal.json")
            .bufferedReader().use { it.readText() }
        val expectedMetadata = EventManifest()
        expectedMetadata.parse(jsonStr)
        val expectAdBreak = expectedMetadata.adBreaks[0]
        val expectAd = expectAdBreak.ads[0]
        
        // Test impression event
        val impressionTracking = expectAd.tracking.find { it.event == Tracking.Event.IMPRESSION }!!
        adProgressListenerSlot.captured.onAdProgress(
            expectAdBreak, 
            expectAd, 
            impressionTracking
        )
        
        // Advance test scheduler to execute all pending coroutines
        testScheduler.advanceUntilIdle()

        // Verify beacon was sent
        coVerify { okHttpService.getString(impressionTracking.url[0]) }
        
        // Verify event log was created
        verify { mockEventLogListener.onEvent(eventLogSlot.captured) }
        assertEquals(eventLogSlot.captured.event, Tracking.Event.IMPRESSION)
        assertEquals(eventLogSlot.captured.adBreakId, expectAdBreak.id)
        assertEquals(eventLogSlot.captured.adId, expectAd.id)
        assertEquals(eventLogSlot.captured.clientTag, "PMM")
        
        // Verify intent was broadcast
        verify { context.sendBroadcast(intentSlot.captured) }
        assertEquals(intentSlot.captured.action, Constants.CSAT_INTENT_LOG_ACTION)
    }

    @Test
    fun testMultipleTrackingEvents() = runTest(timeout = 10.seconds) {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val testScope = TestScope(testDispatcher)
        
        val adProgressListenerSlot = slot<AdProgressListener>()
        val eventLogSlot = slot<EventLog>()
        val mockEventLogListener = mockk<EventLogListener>()

        // Set up mocks
        justRun { tracker.addAdProgressListener(capture(adProgressListenerSlot)) }
        justRun { mockEventLogListener.onEvent(capture(eventLogSlot)) }
        justRun { context.sendBroadcast(any()) }
        coEvery { okHttpService.getString(any()) } returns ""

        // Create PMMClient
        val client = PMMClient(
            tracker = tracker,
            okHttpService = okHttpService,
            context = context,
            coroutineScope = testScope,
            ioDispatcher = testDispatcher
        )
        client.setListener(mockEventLogListener)

        // Load mock metadata
        val jsonStr = javaClass.classLoader!!.getResourceAsStream("metadata/normal.json")
            .bufferedReader().use { it.readText() }
        val expectedMetadata = EventManifest()
        expectedMetadata.parse(jsonStr)
        val expectAdBreak = expectedMetadata.adBreaks[0]
        val expectAd = expectAdBreak.ads[0]

        // Test various tracking events
        val trackingEvents = listOf(
            Tracking.Event.IMPRESSION,
            Tracking.Event.START,
            Tracking.Event.FIRST_QUARTILE,
            Tracking.Event.MIDPOINT,
            Tracking.Event.THIRD_QUARTILE,
            Tracking.Event.COMPLETE
        )

        trackingEvents.forEach { eventType ->
            val tracking = expectAd.tracking.find { it.event == eventType }!!
            adProgressListenerSlot.captured.onAdProgress(
                expectAdBreak,
                expectAd,
                tracking
            )
        }

        // Advance test scheduler to execute all pending coroutines
        testScheduler.advanceUntilIdle()

        // Verify all beacons were sent
        trackingEvents.forEach { eventType ->
            val tracking = expectAd.tracking.find { it.event == eventType }!!
            coVerify { okHttpService.getString(tracking.url[0]) }
        }

        // Verify all event logs were created
        verify(exactly = trackingEvents.size) { mockEventLogListener.onEvent(any()) }
    }

    @Test
    fun testBeaconFailureHandling() = runTest(timeout = 10.seconds) {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val testScope = TestScope(testDispatcher)
        
        val mockEventLogListener = mockk<EventLogListener>()

        // Set up mocks - simulate network failure
        justRun { tracker.addAdProgressListener(any()) }
        justRun { mockEventLogListener.onEvent(any()) }
        justRun { context.sendBroadcast(any()) }
        coEvery { okHttpService.getString(any()) } throws Exception("Network error")

        // Create PMMClient
        val client = PMMClient(
            tracker = tracker,
            okHttpService = okHttpService,
            context = context,
            coroutineScope = testScope,
            ioDispatcher = testDispatcher
        )
        client.setListener(mockEventLogListener)

        // Test that beacon failure doesn't crash the app
        val testUrl = "https://example.com/beacon"
        client.impressionOccurred(listOf(testUrl))

        // Advance test scheduler to execute all pending coroutines
        testScheduler.advanceUntilIdle()

        // Verify beacon attempt was made
        coVerify { okHttpService.getString(testUrl) }

        // Verify event log was still created despite beacon failure
        verify { mockEventLogListener.onEvent(any()) }
    }

    @Test
    fun testPlayerMuteSendsBeaconWhenAdPlaying() = runTest(timeout = 10.seconds) {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val testScope = TestScope(testDispatcher)
        
        val eventLogSlot = slot<EventLog>()
        val mockEventLogListener = mockk<EventLogListener>()

        // Load mock metadata with player events
        val jsonStr = javaClass.classLoader!!.getResourceAsStream("metadata/with_player_events.json")
            .bufferedReader().use { it.readText() }
        val expectedMetadata = EventManifest()
        expectedMetadata.parse(jsonStr)
        val expectAdBreak = expectedMetadata.adBreaks[0]
        val expectAd = expectAdBreak.ads[0]

        // Set up mocks
        justRun { tracker.addAdProgressListener(any()) }
        justRun { mockEventLogListener.onEvent(capture(eventLogSlot)) }
        justRun { context.sendBroadcast(any()) }
        coEvery { okHttpService.getString(any()) } returns ""
        
        // Mock tracker to return current ad and mute tracking URLs
        val muteUrls = expectAd.tracking
            .filter { it.event == Tracking.Event.MUTE }
            .flatMap { it.url }
        every { tracker.getTrackingUrlsForEvent(Tracking.Event.MUTE) } returns muteUrls
        every { tracker.getCurrentAdBreak() } returns expectAdBreak
        every { tracker.getCurrentAd() } returns expectAd

        // Create PMMClient
        val client = PMMClient(
            tracker = tracker,
            okHttpService = okHttpService,
            context = context,
            coroutineScope = testScope,
            ioDispatcher = testDispatcher
        )
        client.setListener(mockEventLogListener)

        // Trigger mute event
        client.onPlayerMute()

        // Advance test scheduler to execute all pending coroutines
        testScheduler.advanceUntilIdle()

        // Verify beacon was sent for mute event
        coVerify { okHttpService.getString(muteUrls[0]) }
        
        // Verify event log was created with MUTE event
        verify { mockEventLogListener.onEvent(eventLogSlot.captured) }
        assertEquals(Tracking.Event.MUTE, eventLogSlot.captured.event)
        assertEquals(expectAdBreak.id, eventLogSlot.captured.adBreakId)
        assertEquals(expectAd.id, eventLogSlot.captured.adId)
    }

    @Test
    fun testPlayerUnmuteSendsBeaconWhenAdPlaying() = runTest(timeout = 10.seconds) {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val testScope = TestScope(testDispatcher)
        
        val eventLogSlot = slot<EventLog>()
        val mockEventLogListener = mockk<EventLogListener>()

        // Load mock metadata with player events
        val jsonStr = javaClass.classLoader!!.getResourceAsStream("metadata/with_player_events.json")
            .bufferedReader().use { it.readText() }
        val expectedMetadata = EventManifest()
        expectedMetadata.parse(jsonStr)
        val expectAdBreak = expectedMetadata.adBreaks[0]
        val expectAd = expectAdBreak.ads[0]

        // Set up mocks
        justRun { tracker.addAdProgressListener(any()) }
        justRun { mockEventLogListener.onEvent(capture(eventLogSlot)) }
        justRun { context.sendBroadcast(any()) }
        coEvery { okHttpService.getString(any()) } returns ""
        
        // Mock tracker to return current ad and unmute tracking URLs
        val unmuteUrls = expectAd.tracking
            .filter { it.event == Tracking.Event.UNMUTE }
            .flatMap { it.url }
        every { tracker.getTrackingUrlsForEvent(Tracking.Event.UNMUTE) } returns unmuteUrls
        every { tracker.getCurrentAdBreak() } returns expectAdBreak
        every { tracker.getCurrentAd() } returns expectAd

        // Create PMMClient
        val client = PMMClient(
            tracker = tracker,
            okHttpService = okHttpService,
            context = context,
            coroutineScope = testScope,
            ioDispatcher = testDispatcher
        )
        client.setListener(mockEventLogListener)

        // Trigger unmute event
        client.onPlayerUnmute()

        // Advance test scheduler to execute all pending coroutines
        testScheduler.advanceUntilIdle()

        // Verify beacon was sent for unmute event
        coVerify { okHttpService.getString(unmuteUrls[0]) }
        
        // Verify event log was created with UNMUTE event
        verify { mockEventLogListener.onEvent(eventLogSlot.captured) }
        assertEquals(Tracking.Event.UNMUTE, eventLogSlot.captured.event)
    }

    @Test
    fun testPlayerPauseResumeSendsBeaconWhenAdPlaying() = runTest(timeout = 10.seconds) {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val testScope = TestScope(testDispatcher)
        
        val eventLogList = mutableListOf<EventLog>()
        val mockEventLogListener = mockk<EventLogListener>()

        // Load mock metadata with player events
        val jsonStr = javaClass.classLoader!!.getResourceAsStream("metadata/with_player_events.json")
            .bufferedReader().use { it.readText() }
        val expectedMetadata = EventManifest()
        expectedMetadata.parse(jsonStr)
        val expectAdBreak = expectedMetadata.adBreaks[0]
        val expectAd = expectAdBreak.ads[0]

        // Set up mocks
        justRun { tracker.addAdProgressListener(any()) }
        justRun { mockEventLogListener.onEvent(capture(eventLogList)) }
        justRun { context.sendBroadcast(any()) }
        coEvery { okHttpService.getString(any()) } returns ""
        
        // Mock tracker to return current ad and pause/resume tracking URLs
        val pauseUrls = expectAd.tracking
            .filter { it.event == Tracking.Event.PAUSE }
            .flatMap { it.url }
        val resumeUrls = expectAd.tracking
            .filter { it.event == Tracking.Event.RESUME }
            .flatMap { it.url }
        every { tracker.getTrackingUrlsForEvent(Tracking.Event.PAUSE) } returns pauseUrls
        every { tracker.getTrackingUrlsForEvent(Tracking.Event.RESUME) } returns resumeUrls
        every { tracker.getCurrentAdBreak() } returns expectAdBreak
        every { tracker.getCurrentAd() } returns expectAd

        // Create PMMClient
        val client = PMMClient(
            tracker = tracker,
            okHttpService = okHttpService,
            context = context,
            coroutineScope = testScope,
            ioDispatcher = testDispatcher
        )
        client.setListener(mockEventLogListener)

        // Trigger pause event
        client.onPlayerPause()
        testScheduler.advanceUntilIdle()

        // Verify beacon was sent for pause event
        coVerify { okHttpService.getString(pauseUrls[0]) }
        assertEquals(Tracking.Event.PAUSE, eventLogList[0].event)

        // Trigger resume event
        client.onPlayerResume()
        testScheduler.advanceUntilIdle()

        // Verify beacon was sent for resume event
        coVerify { okHttpService.getString(resumeUrls[0]) }
        assertEquals(Tracking.Event.RESUME, eventLogList[1].event)
    }

    @Test
    fun testPlayerMuteDoesNotSendBeaconWhenNoAdPlaying() = runTest(timeout = 10.seconds) {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val testScope = TestScope(testDispatcher)
        
        val mockEventLogListener = mockk<EventLogListener>()

        // Set up mocks - no current ad
        justRun { tracker.addAdProgressListener(any()) }
        justRun { context.sendBroadcast(any()) }
        
        // Mock tracker to return empty URLs (no ad playing)
        every { tracker.getTrackingUrlsForEvent(Tracking.Event.MUTE) } returns emptyList()

        // Create PMMClient
        val client = PMMClient(
            tracker = tracker,
            okHttpService = okHttpService,
            context = context,
            coroutineScope = testScope,
            ioDispatcher = testDispatcher
        )
        client.setListener(mockEventLogListener)

        // Trigger mute event when no ad is playing
        client.onPlayerMute()

        // Advance test scheduler
        testScheduler.advanceUntilIdle()

        // Verify no beacon was sent and no event log was created
        coVerify(exactly = 0) { okHttpService.getString(any()) }
        verify(exactly = 0) { mockEventLogListener.onEvent(any()) }
    }
}
