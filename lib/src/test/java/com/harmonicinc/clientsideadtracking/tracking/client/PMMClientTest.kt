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
import io.mockk.impl.annotations.MockK
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.slot
import io.mockk.spyk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
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
        val client = PMMClient(tracker, okHttpService, context, this)
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
        
        // Give coroutines time to complete
        advanceUntilIdle()

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
        val adProgressListenerSlot = slot<AdProgressListener>()
        val eventLogSlot = slot<EventLog>()
        val mockEventLogListener = mockk<EventLogListener>()

        // Set up mocks
        justRun { tracker.addAdProgressListener(capture(adProgressListenerSlot)) }
        justRun { mockEventLogListener.onEvent(capture(eventLogSlot)) }
        justRun { context.sendBroadcast(any()) }
        coEvery { okHttpService.getString(any()) } returns ""

        // Create PMMClient
        val client = PMMClient(tracker, okHttpService, context, this)
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

        // Give coroutines time to complete
        advanceUntilIdle()

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
        val mockEventLogListener = mockk<EventLogListener>()

        // Set up mocks - simulate network failure
        justRun { tracker.addAdProgressListener(any()) }
        justRun { mockEventLogListener.onEvent(any()) }
        justRun { context.sendBroadcast(any()) }
        coEvery { okHttpService.getString(any()) } throws Exception("Network error")

        // Create PMMClient
        val client = PMMClient(tracker, okHttpService, context, this)
        client.setListener(mockEventLogListener)

        // Test that beacon failure doesn't crash the app
        val testUrl = "https://example.com/beacon"
        client.impressionOccurred(listOf(testUrl))

        // Give coroutines time to complete
        advanceUntilIdle()

        // Verify beacon attempt was made
        coVerify { okHttpService.getString(testUrl) }

        // Verify event log was still created despite beacon failure
        verify { mockEventLogListener.onEvent(any()) }
    }
}
