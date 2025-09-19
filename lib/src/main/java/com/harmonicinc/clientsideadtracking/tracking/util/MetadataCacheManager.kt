package com.harmonicinc.clientsideadtracking.tracking.util

import android.util.Log
import com.harmonicinc.clientsideadtracking.tracking.model.Ad
import com.harmonicinc.clientsideadtracking.tracking.model.AdBreak
import com.harmonicinc.clientsideadtracking.tracking.model.EventManifest
import com.harmonicinc.clientsideadtracking.tracking.model.Tracking
import com.harmonicinc.clientsideadtracking.tracking.util.Constants.DEFAULT_CACHE_RETENTION_TIME_MS
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap

/**
 * Manages metadata caching and tracking event state across metadata updates.
 * Ensures that beacon firing state is preserved when users seek back to previously played ads.
 * 
 * Cache expiration is based on when ad breaks were first cached.
 */
class MetadataCacheManager(
    private val cacheRetentionTimeMs: Long = DEFAULT_CACHE_RETENTION_TIME_MS,
    private val timeProvider: () -> Long = { System.currentTimeMillis() } // Injectable time provider for testing
) {
    private val TAG = "MetadataCacheManager"
    
    // Mutex for all cache operations to prevent race conditions
    private val cacheMutex = Mutex()
    private val timeRangeMutex = Mutex()
    
    // Store all seen ad breaks by ID
    private val adBreakCache = ConcurrentHashMap<String, AdBreak>()
    
    // Store when each ad break was first cached (for expiration)
    private val adBreakCacheTimestamps = ConcurrentHashMap<String, Long>()
    
    // Track the complete time range we've seen
    private var minSeenTime: Long = Long.MAX_VALUE
    private var maxSeenTime: Long = Long.MIN_VALUE
    
    /**
     * Merge new metadata with cached metadata while preserving fired tracking state
     */
    suspend fun mergeEventManifest(newManifest: EventManifest): EventManifest = cacheMutex.withLock {
        val newAdBreakIds = newManifest.adBreaks.map { it.id }.toSet()
        Log.d(TAG, "Merging new manifest with ${newManifest.adBreaks.size} ad breaks: $newAdBreakIds")
        
        // Clean up expired cache entries before merging
        cleanupExpiredEntries()
        
        // Update time range
        timeRangeMutex.withLock {
            if (newManifest.dataRange.start != 0L && newManifest.dataRange.end != 0L) {
                Log.d(TAG, "Previous time range: $minSeenTime - $maxSeenTime")
                minSeenTime = minOf(minSeenTime, newManifest.dataRange.start)
                maxSeenTime = maxOf(maxSeenTime, newManifest.dataRange.end)
                Log.d(TAG, "Updated time range: $minSeenTime - $maxSeenTime")
            }
        }
        
        // Merge ad breaks
        val currentTime = timeProvider()
        newManifest.adBreaks.forEach { newAdBreak ->
            val cachedAdBreak = adBreakCache[newAdBreak.id]
            if (cachedAdBreak != null) {
                Log.d(TAG, "Merging existing ad break: ${newAdBreak.id}")
                mergeAdBreak(cachedAdBreak, newAdBreak)
            } else {
                Log.d(TAG, "Adding new ad break: ${newAdBreak.id}")
                // Store the timestamp when we first cache this ad break
                adBreakCacheTimestamps[newAdBreak.id] = currentTime
            }
            adBreakCache[newAdBreak.id] = newAdBreak
        }
        
        // Create merged manifest with all cached ad breaks
        EventManifest().apply {
            adBreaks.addAll(adBreakCache.values.sortedBy { it.startTime })
            timeRangeMutex.withLock {
                if (minSeenTime != Long.MAX_VALUE) {
                    dataRange.start = minSeenTime
                    dataRange.end = maxSeenTime
                }
            }
        }
    }
    
    /**
     * Merge tracking state from cached ad break to new ad break
     */
    private fun mergeAdBreak(cached: AdBreak, new: AdBreak) {
        // Build a map from ad ID to cached Ad
        val cachedAdMap = cached.ads.associateBy { it.id }
        // For each new ad, try to find the cached ad and merge tracking state
        new.ads.forEach { newAd ->
            val cachedAd = cachedAdMap[newAd.id]
            if (cachedAd != null) {
                // Build a map from tracking unique ID to cached tracking
                val cachedTrackingMap = cachedAd.tracking.associateBy { it.getUniqueId() }
                newAd.tracking.forEach { newTracking ->
                    val cachedTracking = cachedTrackingMap[newTracking.getUniqueId()]
                    if (cachedTracking != null && cachedTracking.fired) {
                        newTracking.fired = true
                    }
                }
            }
        }
    }
    
    /**
     * Mark a tracking event as fired and persist the state in the cached ad break
     */
    suspend fun markTrackingAsFired(tracking: Tracking, currentAdBreak: AdBreak, currentAd: Ad) = cacheMutex.withLock {
        // Find the cached ad break and update the tracking state there
        val cachedAdBreak = adBreakCache[currentAdBreak.id]
        if (cachedAdBreak != null) {
            val cachedAd = cachedAdBreak.ads.find { it.id == currentAd.id }
            if (cachedAd != null) {
                val trackingMap = cachedAd.tracking.associateBy { it.getUniqueId() }
                val cachedTracking = trackingMap[tracking.getUniqueId()]
                if (cachedTracking != null) {
                    cachedTracking.fired = true
                    Log.d(TAG, "Marked tracking as fired in cache: ${tracking.event} at ${tracking.startTime}")
                } else {
                    Log.w(TAG, "Could not find cached tracking event to mark as fired")
                }
            } else {
                Log.w(TAG, "Could not find cached ad to mark tracking as fired")
            }
        } else {
            Log.w(TAG, "Could not find cached ad break to mark tracking as fired")
        }
    }

    /**
     * Clean up expired cache entries based on when they were first cached
     */
    private fun cleanupExpiredEntries() {
        val currentTime = timeProvider()
        val adBreaksToRemove = mutableListOf<String>()
        
        // Remove ad breaks that were cached longer than retention time ago
        adBreakCacheTimestamps.forEach { (adBreakId, cacheTimestamp) ->
            val cacheAge = currentTime - cacheTimestamp
            if (cacheAge > cacheRetentionTimeMs) {
                adBreaksToRemove.add(adBreakId)
            }
        }
        
        adBreaksToRemove.forEach { adBreakId ->
            adBreakCache.remove(adBreakId)
            adBreakCacheTimestamps.remove(adBreakId)
        }
        
        if (adBreaksToRemove.isNotEmpty()) {
            Log.d(TAG, "Cleaned up ${adBreaksToRemove.size} expired ad breaks")
        }
        
        // Recalculate time range if we removed ad breaks
        if (adBreaksToRemove.isNotEmpty() && adBreakCache.isNotEmpty()) {
            val remainingAdBreaks = adBreakCache.values
            minSeenTime = remainingAdBreaks.minOfOrNull { it.startTime } ?: Long.MAX_VALUE
            maxSeenTime = remainingAdBreaks.maxOfOrNull { it.startTime + it.duration.toLong() } ?: Long.MIN_VALUE
        } else if (adBreakCache.isEmpty()) {
            minSeenTime = Long.MAX_VALUE
            maxSeenTime = Long.MIN_VALUE
        }
    }

    /**
     * Clear all cached data and reset state
     */
    suspend fun clear() {
        cacheMutex.withLock {
            Log.d(TAG, "Clearing metadata cache")
            adBreakCache.clear()
            adBreakCacheTimestamps.clear()
        }
        timeRangeMutex.withLock {
            minSeenTime = Long.MAX_VALUE
            maxSeenTime = Long.MIN_VALUE
        }
    }
    
    /**
     * Get statistics about the cache for debugging
     */
    fun getCacheStats(): String {
        val totalFiredEvents = adBreakCache.values.sumOf { adBreak ->
            adBreak.ads.sumOf { ad ->
                ad.tracking.count { it.fired }
            }
        }
        val oldestCacheTime = adBreakCacheTimestamps.values.minOfOrNull { it } ?: 0L
        val cacheAgeMs = if (oldestCacheTime > 0) timeProvider() - oldestCacheTime else 0L
        return "AdBreaks: ${adBreakCache.size}, FiredEvents: $totalFiredEvents, Range: $minSeenTime-$maxSeenTime, OldestCacheAge: ${cacheAgeMs}ms"
    }
}
