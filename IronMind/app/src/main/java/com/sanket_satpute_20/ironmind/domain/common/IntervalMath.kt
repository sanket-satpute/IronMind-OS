package com.sanket_satpute_20.ironmind.domain.common

/**
 * Pure, deterministic mathematical utilities for temporal interval normalization,
 * boundary clipping, deduplication, union merging, and intersection calculation.
 *
 * Epistemic & Mathematical Contracts:
 * 1. Half-open intervals: [startMs, endMs).
 * 2. An interval is valid if and only if: startMs >= 0L and endMs > startMs.
 *    Zero-length (startMs == endMs) and inverted (endMs < startMs) intervals are rejected.
 * 3. Safe arithmetic: Protects against Long overflow on end-time computation.
 * 4. Union merging ([mergeIntervals]):
 *    - Filters to valid intervals.
 *    - Sorts intervals deterministically by startMs ascending, then endMs ascending.
 *    - Overlapping (next.startMs < currentEnd) and adjacent (next.startMs == currentEnd) intervals
 *      are merged into a single contiguous interval [currentStart, maxOf(currentEnd, next.endMs)).
 *    - Returns canonical disjoint intervals.
 * 5. Union duration ([unionDurationMs]):
 *    - Sum of durations of merged disjoint intervals. Avoids double-counting overlapping intervals.
 * 6. Window clipping ([clipInterval]):
 *    - Clamps startMs to maxOf(startMs, windowStartMs) and endMs to minOf(endMs, windowEndMs).
 *    - Returns null if clippedEnd <= clippedStart or if windowEndMs <= windowStartMs.
 * 7. Two-pointer intersection ([intersectDurationMs]):
 *    - Computes exact temporal overlap between two lists of merged disjoint intervals in O(N + M).
 */
object IntervalMath {

    data class Interval(val startMs: Long, val endMs: Long) {
        val durationMs: Long get() = (endMs - startMs).coerceAtLeast(0L)
        val isValid: Boolean get() = startMs >= 0L && endMs > startMs
    }

    /**
     * Safely computes rawEndMs = startMs + durationMs guarding against Long overflow.
     * Returns null if startMs < 0L or durationMs <= 0L.
     */
    fun safeRawEndMs(startMs: Long, durationMs: Long): Long? {
        if (startMs < 0L || durationMs <= 0L) return null
        return if (startMs > Long.MAX_VALUE - durationMs) {
            Long.MAX_VALUE
        } else {
            startMs + durationMs
        }
    }

    /**
     * Clips an interval to [windowStartMs, windowEndMs).
     * Returns null if the clipped interval has zero or negative duration.
     */
    fun clipInterval(
        startMs: Long,
        endMs: Long,
        windowStartMs: Long,
        windowEndMs: Long
    ): Interval? {
        if (windowEndMs <= windowStartMs) return null
        val clippedStart = maxOf(startMs, windowStartMs)
        val clippedEnd = minOf(endMs, windowEndMs)
        return if (clippedEnd > clippedStart) {
            Interval(clippedStart, clippedEnd)
        } else {
            null
        }
    }

    /**
     * Merges overlapping and adjacent intervals into a canonical list of disjoint intervals.
     * Rejects invalid intervals (duration <= 0 or start < 0).
     */
    fun mergeIntervals(intervals: List<Interval>): List<Interval> {
        val valid = intervals.filter { it.isValid }
        if (valid.isEmpty()) return emptyList()

        val sorted = valid.sortedWith(compareBy<Interval> { it.startMs }.thenBy { it.endMs })
        val merged = mutableListOf<Interval>()
        var currentStart = sorted[0].startMs
        var currentEnd = sorted[0].endMs

        for (i in 1 until sorted.size) {
            val next = sorted[i]
            if (next.startMs <= currentEnd) {
                currentEnd = maxOf(currentEnd, next.endMs)
            } else {
                if (currentEnd > currentStart) {
                    merged.add(Interval(currentStart, currentEnd))
                }
                currentStart = next.startMs
                currentEnd = next.endMs
            }
        }
        if (currentEnd > currentStart) {
            merged.add(Interval(currentStart, currentEnd))
        }
        return merged
    }

    /**
     * Computes the total non-overlapping duration of a collection of intervals.
     */
    fun unionDurationMs(intervals: List<Interval>): Long {
        if (intervals.isEmpty()) return 0L
        return mergeIntervals(intervals).sumOf { it.durationMs }
    }

    /**
     * Convenience helper to compute union duration from pairs of (startMs, endMs).
     */
    fun unionDurationFromPairs(ranges: List<Pair<Long, Long>>): Long {
        if (ranges.isEmpty()) return 0L
        val intervals = ranges.mapNotNull { (start, end) ->
            if (end > start && start >= 0L) Interval(start, end) else null
        }
        return unionDurationMs(intervals)
    }

    /**
     * Computes total overlapping duration between two disjoint (merged) interval lists in O(N + M).
     */
    fun intersectDurationMs(
        firstMerged: List<Interval>,
        secondMerged: List<Interval>
    ): Long {
        if (firstMerged.isEmpty() || secondMerged.isEmpty()) return 0L
        var i = 0
        var j = 0
        var total = 0L

        while (i < firstMerged.size && j < secondMerged.size) {
            val a = firstMerged[i]
            val b = secondMerged[j]
            val overlapStart = maxOf(a.startMs, b.startMs)
            val overlapEnd = minOf(a.endMs, b.endMs)
            if (overlapEnd > overlapStart) {
                total += (overlapEnd - overlapStart)
            }
            if (a.endMs < b.endMs) {
                i++
            } else {
                j++
            }
        }
        return total
    }
}
