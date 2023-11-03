package com.harmonicinc.clientsideadtracking.tracking.client

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.view.ViewGroup
import com.harmonicinc.clientsideadtracking.player.MockPlayerAdapter
import com.harmonicinc.clientsideadtracking.tracking.AdMetadataTracker
import com.harmonicinc.clientsideadtracking.tracking.AdProgressListener
import com.harmonicinc.clientsideadtracking.tracking.EventLogListener
import com.harmonicinc.clientsideadtracking.tracking.client.omsdk.AdSessionUtil
import com.harmonicinc.clientsideadtracking.tracking.model.EventLog
import com.harmonicinc.clientsideadtracking.tracking.model.EventManifest
import com.harmonicinc.clientsideadtracking.tracking.model.Tracking
import com.harmonicinc.clientsideadtracking.tracking.util.Constants
import com.iab.omid.library.harmonicinc.adsession.AdEvents
import com.iab.omid.library.harmonicinc.adsession.AdSession
import com.iab.omid.library.harmonicinc.adsession.media.MediaEvents
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.spyk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class OMSDKClientTest {
    @MockK
    private lateinit var tracker: AdMetadataTracker
    @MockK
    private lateinit var playerView: ViewGroup

    private lateinit var activity: Activity
    private lateinit var context: Context

    private val mockPlayerAdapter = MockPlayerAdapter()
    @Before
    fun setUpBeforeEach() {
        MockKAnnotations.init(this)
        activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        context = RuntimeEnvironment.getApplication().applicationContext
    }

    @Test
    fun testMediaEvents() = runTest(timeout = 10.seconds) {
        val adProgressListenerSlot = slot<AdProgressListener>()
        val eventLogSlot = slot<EventLog>()
        val intentSlot = slot<Intent>()
        val mockEventLogListener = mockk<EventLogListener>()
        val mockAdSession = getMockAdSession()
        val mockMediaEvents = getMockMediaEvents()
        val mockAdEvents = getMockAdEvents()
        mockkObject(AdSessionUtil)
        mockkStatic(MediaEvents::class)
        mockkStatic(AdEvents::class)

        context = spyk(context)
        justRun { tracker.addAdProgressListener(capture(adProgressListenerSlot)) }
        justRun { mockEventLogListener.onEvent(capture(eventLogSlot)) }
        justRun { context.sendBroadcast(capture(intentSlot)) }
        every { AdSessionUtil.getNativeAdSession(any(), any(), any(), any()) } returns mockAdSession
        every { MediaEvents.createMediaEvents(any()) } returns mockMediaEvents
        every { AdEvents.createAdEvents(any()) } returns mockAdEvents

        val client = OMSDKClient(context, mockPlayerAdapter, playerView, tracker, null, this)
        client.addEventLogListener(mockEventLogListener)

        verify { tracker.addAdProgressListener(adProgressListenerSlot.captured)}

        // Mock metadata response
        val jsonStr = javaClass.classLoader!!.getResourceAsStream("metadata/normal.json").bufferedReader().use { it.readText() }
        val expectedMetadata = EventManifest()
        expectedMetadata.parse(jsonStr)
        val expectAdBreak = expectedMetadata.adBreaks[0]
        val expectAd = expectAdBreak.ads[0]

        adProgressListenerSlot.captured.onAdProgress(expectAdBreak, expectAd, Tracking(Tracking.Event.IMPRESSION))

        verify { AdSessionUtil.getNativeAdSession(any(), any(), any(), any()) }
        verify { MediaEvents.createMediaEvents(mockAdSession) }
        verify { AdEvents.createAdEvents(mockAdSession) }
        verify { mockAdSession.registerAdView(playerView) }
        verify { mockAdSession.start() }
        verify { mockAdEvents.loaded() }

        // Verify fired OMSDK ad events
        verify { mockAdEvents.impressionOccurred() }

        // Verify log has been sent
        runCurrent()
        verify { mockEventLogListener.onEvent(eventLogSlot.captured) }
        assertEquals(eventLogSlot.captured.event, Tracking.Event.IMPRESSION)
        assertEquals(eventLogSlot.captured.adBreakId, expectAdBreak.id)
        assertEquals(eventLogSlot.captured.adId, expectAd.id)
        verify { context.sendBroadcast(intentSlot.captured) }
        assertEquals(intentSlot.captured.action, Constants.CSAT_INTENT_LOG_ACTION)

        // Test other events
        adProgressListenerSlot.captured.onAdProgress(expectAdBreak, expectAd, Tracking(Tracking.Event.START))
        adProgressListenerSlot.captured.onAdProgress(expectAdBreak, expectAd, Tracking(Tracking.Event.FIRST_QUARTILE))
        adProgressListenerSlot.captured.onAdProgress(expectAdBreak, expectAd, Tracking(Tracking.Event.MIDPOINT))
        adProgressListenerSlot.captured.onAdProgress(expectAdBreak, expectAd, Tracking(Tracking.Event.THIRD_QUARTILE))
        verify { mockMediaEvents.start(mockPlayerAdapter.getDuration().toFloat(), mockPlayerAdapter.getAudioVolume()) }
        verify { mockMediaEvents.firstQuartile() }
        verify { mockMediaEvents.midpoint() }
        verify { mockMediaEvents.thirdQuartile() }

        // Test destroy session
        adProgressListenerSlot.captured.onAdProgress(expectAdBreak, expectAd, Tracking(Tracking.Event.COMPLETE))
        verify { mockMediaEvents.complete() }
        verify { mockAdSession.finish() }
    }

    @Test
    fun testNonTrackingEvents() = runTest(timeout = 10.seconds) {
        context = spyk(context)
        val adProgressListenerSlot = slot<AdProgressListener>()
        val mockAdSession = getMockAdSession()
        val mockMediaEvents = getMockMediaEvents()
        val mockAdEvents = getMockAdEvents()
        mockkObject(AdSessionUtil)
        mockkStatic(MediaEvents::class)
        mockkStatic(AdEvents::class)

        // Mock metadata response
        val jsonStr = javaClass.classLoader!!.getResourceAsStream("metadata/normal.json").bufferedReader().use { it.readText() }
        val expectedMetadata = EventManifest()
        expectedMetadata.parse(jsonStr)
        val expectAdBreak = expectedMetadata.adBreaks[0]
        val expectAd = expectAdBreak.ads[0]

        justRun { tracker.addAdProgressListener(capture(adProgressListenerSlot)) }
        every { AdSessionUtil.getNativeAdSession(any(), any(), any(), any()) } returns mockAdSession
        every { MediaEvents.createMediaEvents(any()) } returns mockMediaEvents
        every { AdEvents.createAdEvents(any()) } returns mockAdEvents

        val client = OMSDKClient(context, mockPlayerAdapter, playerView, tracker, null, this)
        adProgressListenerSlot.captured.onAdProgress(expectAdBreak, expectAd, Tracking(Tracking.Event.IMPRESSION))
        client.pause()
        client.resume()
        client.bufferStart()
        client.bufferEnd()
        client.volumeChange(mockPlayerAdapter.getAudioVolume())

        verify { mockMediaEvents.pause() }
        verify { mockMediaEvents.resume() }
        verify { mockMediaEvents.bufferStart() }
        verify { mockMediaEvents.bufferFinish() }
        verify { mockMediaEvents.volumeChange(mockPlayerAdapter.getAudioVolume()) }
    }

    private fun getMockAdSession(): AdSession {
        val mockAdSession = mockk<AdSession>()
        justRun { mockAdSession.registerAdView(any()) }
        justRun { mockAdSession.start() }
        justRun { mockAdSession.finish() }
        return mockAdSession
    }

    private fun getMockMediaEvents(): MediaEvents {
        val mockMediaEvents = mockk<MediaEvents>()
        justRun { mockMediaEvents.pause() }
        justRun { mockMediaEvents.resume() }
        justRun { mockMediaEvents.bufferStart() }
        justRun { mockMediaEvents.bufferFinish() }
        justRun { mockMediaEvents.volumeChange(any(Float::class)) }
        justRun { mockMediaEvents.start(any(Float::class), any(Float::class)) }
        justRun { mockMediaEvents.firstQuartile() }
        justRun { mockMediaEvents.midpoint() }
        justRun { mockMediaEvents.thirdQuartile() }
        justRun { mockMediaEvents.complete() }
        justRun { mockMediaEvents.skipped() }
        return mockMediaEvents
    }

    private fun getMockAdEvents(): AdEvents {
        val mockAdEvents = mockk<AdEvents>()
        justRun { mockAdEvents.loaded() }
        justRun { mockAdEvents.impressionOccurred() }
        return mockAdEvents
    }
}