package com.harmonicinc.clientsideadtracking.tracking.util

import android.util.Log
import androidx.annotation.VisibleForTesting
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.max
import kotlin.math.min

/**
 * Tracks time ranges that have been played.
 * Handles merging overlapping ranges, querying if a timestamp was played,
 * and periodic cleanup of old ranges based on retention time.
 */
class PlayedRangeTracker(
    private val cacheRetentionTimeMs: Long,
    private val positionTrackingIntervalMs: Long = 500L // 500 ms tolerance for continuity
) {
    companion object {
        private const val TAG = "PlayedRangeTracker"
    }
    
    data class TimeRange(var start: Long, var end: Long) {
        fun contains(time: Long) = time in start..end
        
        fun isPositionNear(position: Long, tolerance: Long): Boolean {
            // Position is near if it's within the range or within tolerance of the edges
            return position >= start - tolerance && position <= end + tolerance
        }
    }
    
    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    val playedRanges = mutableListOf<TimeRange>()
    private val mutex = Mutex()
    
    private var lastCleanupPosition: Long? = null
    
    /**
     * Track the current playback position.
     * 
     * @param position Current playback position in milliseconds
     */
    suspend fun trackPosition(position: Long) = mutex.withLock {
        if (position < 0) return@withLock
        
        if (playedRanges.isEmpty()) {
            // First range
            playedRanges.add(TimeRange(position, position))
            Log.d(TAG, "Started tracking at position: $position")
            lastCleanupPosition = position
            return@withLock
        }
        
        // Check if this position continues from or overlaps with any existing range
        var merged = false
        for (range in playedRanges) {
            if (isPositionNearRange(position, range)) {
                // Extend the range
                range.start = min(range.start, position)
                range.end = max(range.end, position)
                merged = true
                break
            }
        }
        
        if (!merged) {
            // Position doesn't connect to any existing range - create new range
            playedRanges.add(TimeRange(position, position))
            Log.d(TAG, "Created new range at position: $position")
        }
        
        // Sort and merge adjacent ranges
        playedRanges.sortBy { it.start }
        mergeAdjacentRanges()
        
        // Periodic cleanup
        cleanupOldRangesIfNeeded(position)
    }
    
    /**
     * Check if a position is near enough to a range to extend it.
     */
    private fun isPositionNearRange(position: Long, range: TimeRange): Boolean {
        val tolerance = positionTrackingIntervalMs * 2
        return range.isPositionNear(position, tolerance)
    }
    
    /**
     * Check if a specific position was played by the user.
     * 
     * @param position Playback position in milliseconds to check
     * @return true if the time was played, false otherwise
     */
    suspend fun wasTimePlayed(position: Long): Boolean = mutex.withLock {
        return playedRanges.any { it.contains(position) }
    }
    
    /**
     * Merge overlapping or adjacent ranges.
     */
    private fun mergeAdjacentRanges() {
        if (playedRanges.size <= 1) return
        
        val merged = mutableListOf<TimeRange>()
        var current = playedRanges[0]
        
        for (i in 1 until playedRanges.size) {
            val nextRange = playedRanges[i]
            
            // Check if ranges overlap or are adjacent (within tolerance)
            val tolerance = positionTrackingIntervalMs * 2
            if (current.end + tolerance >= nextRange.start) {
                // Merge ranges
                current.end = max(current.end, nextRange.end)
                current.start = min(current.start, nextRange.start)
            } else {
                // No overlap, save current and move to next
                merged.add(current)
                current = nextRange
            }
        }
        
        // Add the last range
        merged.add(current)
        
        playedRanges.clear()
        playedRanges.addAll(merged)
    }
    
    /**
     * Clean up ranges that are too far behind the current position.
     * Called periodically during trackPosition().
     */
    private fun cleanupOldRangesIfNeeded(currentPosition: Long) {
        val last = lastCleanupPosition
        
        // Only cleanup every minute to avoid excessive processing
        if (last != null && currentPosition - last < 60_000) {
            return
        }
        
        val cutoffPosition = currentPosition - cacheRetentionTimeMs
        val sizeBefore = playedRanges.size
        
        playedRanges.removeAll { it.end < cutoffPosition }
        
        lastCleanupPosition = currentPosition
        
        if (sizeBefore != playedRanges.size) {
            Log.d(TAG, "Cleaned up ${sizeBefore - playedRanges.size} old ranges. Remaining: ${playedRanges.size}")
        }
    }
    
    /**
     * Clear all tracked ranges.
     */
    suspend fun clear() = mutex.withLock {
        playedRanges.clear()
        lastCleanupPosition = null
        Log.d(TAG, "Cleared all played ranges")
    }
}
