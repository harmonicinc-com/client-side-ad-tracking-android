package com.harmonicinc.clientsideadtracking.tracking.util

import com.harmonicinc.clientsideadtracking.tracking.model.Ad
import com.harmonicinc.clientsideadtracking.tracking.model.AdBreak
import com.harmonicinc.clientsideadtracking.tracking.model.EventManifest
import com.harmonicinc.clientsideadtracking.tracking.model.Tracking
import kotlinx.coroutines.test.runTest
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.time.Duration.Companion.seconds

@RunWith(RobolectricTestRunner::class)
class MetadataCacheManagerTest {

    private lateinit var cacheManager: MetadataCacheManager
    private val cacheRetentionTimeMs = 1000L // 1 second for testing

    @Before
    fun setup() {
        cacheManager = MetadataCacheManager(cacheRetentionTimeMs)
    }

    @Test
    fun testMergeEventManifest_preservesFiredState() = runTest(timeout = 10.seconds) {
        // Create initial manifest with one ad break
        val initialManifest = createTestManifest(
            adBreakId = "adbreak1",
            adId = "ad1",
            startTime = 1000L,
            trackingEvents = listOf(
                createTracking(Tracking.Event.START, 1000L, listOf("http://start.com")),
                createTracking(Tracking.Event.MIDPOINT, 1500L, listOf("http://midpoint.com"))
            )
        )

        // Merge initial manifest
        val mergedInitial = cacheManager.mergeEventManifest(initialManifest)
        assertEquals(1, mergedInitial.adBreaks.size)
        assertEquals("adbreak1", mergedInitial.adBreaks[0].id)

        // Mark START event as fired
        val adBreak = mergedInitial.adBreaks[0]
        val ad = adBreak.ads[0]
        val startTracking = ad.tracking.find { it.event == Tracking.Event.START }!!
        startTracking.fired = true
        cacheManager.markTrackingAsFired(startTracking, adBreak, ad)

        // Create updated manifest with same ad break (simulating metadata refresh)
        val updatedManifest = createTestManifest(
            adBreakId = "adbreak1",
            adId = "ad1", 
            startTime = 1000L,
            trackingEvents = listOf(
                createTracking(Tracking.Event.START, 1000L, listOf("http://start.com")),
                createTracking(Tracking.Event.MIDPOINT, 1500L, listOf("http://midpoint.com")),
                createTracking(Tracking.Event.COMPLETE, 2000L, listOf("http://complete.com")) // New event
            )
        )

        // Merge updated manifest
        val mergedUpdated = cacheManager.mergeEventManifest(updatedManifest)

        // Verify fired state is preserved
        val updatedAdBreak = mergedUpdated.adBreaks[0]
        val updatedAd = updatedAdBreak.ads[0]
        val updatedStartTracking = updatedAd.tracking.find { it.event == Tracking.Event.START }!!
        val updatedMidpointTracking = updatedAd.tracking.find { it.event == Tracking.Event.MIDPOINT }!!
        val newCompleteTracking = updatedAd.tracking.find { it.event == Tracking.Event.COMPLETE }!!

        assertTrue("START event should remain fired", updatedStartTracking.fired)
        assertFalse("MIDPOINT event should not be fired", updatedMidpointTracking.fired)
        assertFalse("COMPLETE event should not be fired", newCompleteTracking.fired)
        assertEquals(3, updatedAd.tracking.size)
    }

    @Test
    fun testMergeEventManifest_multipleAdBreaks() = runTest(timeout = 10.seconds) {
        // Create first manifest with adbreak1
        val manifest1 = createTestManifest(
            adBreakId = "adbreak1",
            adId = "ad1",
            startTime = 1000L,
            trackingEvents = listOf(createTracking(Tracking.Event.START, 1000L, listOf("http://start1.com")))
        )

        // Create second manifest with adbreak2
        val manifest2 = createTestManifest(
            adBreakId = "adbreak2", 
            adId = "ad2",
            startTime = 2000L,
            trackingEvents = listOf(createTracking(Tracking.Event.START, 2000L, listOf("http://start2.com")))
        )

        // Merge both manifests
        val merged1 = cacheManager.mergeEventManifest(manifest1)
        val merged2 = cacheManager.mergeEventManifest(manifest2)

        // Should have both ad breaks
        assertEquals(2, merged2.adBreaks.size)
        
        val adBreak1 = merged2.adBreaks.find { it.id == "adbreak1" }!!
        val adBreak2 = merged2.adBreaks.find { it.id == "adbreak2" }!!
        
        assertEquals(1000L, adBreak1.startTime)
        assertEquals(2000L, adBreak2.startTime)
    }

    @Test
    fun testCacheExpiration() = runTest(timeout = 10.seconds) {
        // Use a controllable time provider
        var currentTestTime = 0L
        val timeProvider = { currentTestTime }
        
        // Use a very short cache retention time for this test
        val shortRetentionCacheManager = MetadataCacheManager(100L, timeProvider) // 100ms
        
        // Create initial manifest
        val manifest1 = createTestManifest(
            adBreakId = "adbreak1",
            adId = "ad1",
            startTime = 1000L,
            trackingEvents = listOf(createTracking(Tracking.Event.START, 1000L, listOf("http://start.com")))
        )

        val merged1 = shortRetentionCacheManager.mergeEventManifest(manifest1)
        assertEquals(1, merged1.adBreaks.size)

        // Advance time beyond cache retention time
        currentTestTime += 200L // Simulate passage of time longer than retention time

        // Create new manifest with different ad break
        val manifest2 = createTestManifest(
            adBreakId = "adbreak2",
            adId = "ad2", 
            startTime = 2000L,
            trackingEvents = listOf(createTracking(Tracking.Event.START, 2000L, listOf("http://start2.com")))
        )

        val merged2 = shortRetentionCacheManager.mergeEventManifest(manifest2)

        // Should only have the new ad break (old one expired)
        assertEquals(1, merged2.adBreaks.size)
        assertEquals("adbreak2", merged2.adBreaks[0].id)
    }

    @Test
    fun testClear() = runTest(timeout = 10.seconds) {
        // Add some data to cache
        val manifest = createTestManifest(
            adBreakId = "adbreak1",
            adId = "ad1",
            startTime = 1000L,
            trackingEvents = listOf(createTracking(Tracking.Event.START, 1000L, listOf("http://start.com")))
        )

        val merged = cacheManager.mergeEventManifest(manifest)
        assertEquals(1, merged.adBreaks.size)

        // Clear cache
        cacheManager.clear()

        // Create new manifest - should not have any cached data
        val newManifest = createTestManifest(
            adBreakId = "adbreak2",
            adId = "ad2",
            startTime = 2000L,
            trackingEvents = listOf(createTracking(Tracking.Event.START, 2000L, listOf("http://start2.com")))
        )

        val newMerged = cacheManager.mergeEventManifest(newManifest)
        assertEquals(1, newMerged.adBreaks.size)
        assertEquals("adbreak2", newMerged.adBreaks[0].id)
    }

    @Test
    fun testGetCacheStats() = runTest(timeout = 10.seconds) {
        // Initially empty
        val emptyStats = cacheManager.getCacheStats()
        assertTrue("Should show 0 ad breaks initially", emptyStats.contains("AdBreaks: 0"))

        // Add some data
        val manifest = createTestManifest(
            adBreakId = "adbreak1",
            adId = "ad1",
            startTime = 1000L,
            trackingEvents = listOf(
                createTracking(Tracking.Event.START, 1000L, listOf("http://start.com")),
                createTracking(Tracking.Event.MIDPOINT, 1500L, listOf("http://midpoint.com"))
            )
        )

        val merged = cacheManager.mergeEventManifest(manifest)
        val adBreak = merged.adBreaks[0]
        val ad = adBreak.ads[0]
        val startTracking = ad.tracking.find { it.event == Tracking.Event.START }!!

        // Mark one event as fired
        startTracking.fired = true
        cacheManager.markTrackingAsFired(startTracking, adBreak, ad)

        val statsWithData = cacheManager.getCacheStats()
        assertTrue("Should show 1 ad break", statsWithData.contains("AdBreaks: 1"))
        assertTrue("Should show 1 fired event", statsWithData.contains("FiredEvents: 1"))
    }

    private fun createTestManifest(
        adBreakId: String,
        adId: String,
        startTime: Long,
        trackingEvents: List<Tracking>
    ): EventManifest {
        val manifest = EventManifest()
        val adBreak = AdBreak(adBreakId, 30.0, ArrayList(), startTime)
        
        // Create ad JSON
        val adJson = JSONObject().apply {
            put("id", adId)
            put("duration", 30.0)
            put("startTime", startTime)
            
            val trackingArray = JSONArray()
            trackingEvents.forEach { tracking ->
                val trackingJson = JSONObject().apply {
                    put("event", tracking.event.name.lowercase())
                    put("startTime", tracking.startTime)
                    val urlArray = JSONArray()
                    tracking.url.forEach { urlArray.put(it) }
                    put("signalingUrls", urlArray)
                }
                trackingArray.put(trackingJson)
            }
            put("trackingEvents", trackingArray)
        }
        
        val ad = Ad(adJson)
        adBreak.ads.add(ad)
        manifest.adBreaks.add(adBreak)
        
        // Set data range
        manifest.dataRange.start = startTime
        manifest.dataRange.end = startTime + 30000L
        
        return manifest
    }

    private fun createTracking(event: Tracking.Event, startTime: Long, urls: List<String>): Tracking {
        return Tracking(event, urls, startTime)
    }
}
