package com.sanket_satpute_20.ironmind.domain.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IntervalMathTest {

    // =========================================================================
    // 1. Interval Model & Validation
    // =========================================================================

    @Test
    fun interval_validityChecks() {
        assertTrue(IntervalMath.Interval(0L, 100L).isValid)
        assertTrue(IntervalMath.Interval(1_000L, 2_000L).isValid)

        // Zero-length
        assertFalse(IntervalMath.Interval(100L, 100L).isValid)
        assertEquals(0L, IntervalMath.Interval(100L, 100L).durationMs)

        // Inverted / negative duration
        assertFalse(IntervalMath.Interval(200L, 100L).isValid)
        assertEquals(0L, IntervalMath.Interval(200L, 100L).durationMs)

        // Negative start
        assertFalse(IntervalMath.Interval(-50L, 100L).isValid)
    }

    // =========================================================================
    // 2. Safe Raw End Calculation (Overflow Protection)
    // =========================================================================

    @Test
    fun safeRawEndMs_guardsAgainstOverflowAndInvalidInputs() {
        // Normal addition
        assertEquals(1_500L, IntervalMath.safeRawEndMs(1_000L, 500L))

        // Long overflow clamped to Long.MAX_VALUE
        assertEquals(Long.MAX_VALUE, IntervalMath.safeRawEndMs(Long.MAX_VALUE - 50L, 100L))

        // Invalid inputs return null
        assertNull(IntervalMath.safeRawEndMs(-1L, 100L))
        assertNull(IntervalMath.safeRawEndMs(1_000L, 0L))
        assertNull(IntervalMath.safeRawEndMs(1_000L, -50L))
    }

    // =========================================================================
    // 3. Window Boundary Clipping
    // =========================================================================

    @Test
    fun clipInterval_clipsCorrectlyToWindow() {
        val wStart = 10_000L
        val wEnd = 20_000L

        // Fully inside
        val inside = IntervalMath.clipInterval(12_000L, 15_000L, wStart, wEnd)
        assertNotNull(inside)
        assertEquals(12_000L, inside!!.startMs)
        assertEquals(15_000L, inside.endMs)
        assertEquals(3_000L, inside.durationMs)

        // Straddling start
        val straddleStart = IntervalMath.clipInterval(8_000L, 14_000L, wStart, wEnd)
        assertNotNull(straddleStart)
        assertEquals(10_000L, straddleStart!!.startMs)
        assertEquals(14_000L, straddleStart.endMs)
        assertEquals(4_000L, straddleStart.durationMs)

        // Straddling end
        val straddleEnd = IntervalMath.clipInterval(17_000L, 25_000L, wStart, wEnd)
        assertNotNull(straddleEnd)
        assertEquals(17_000L, straddleEnd!!.startMs)
        assertEquals(20_000L, straddleEnd.endMs)
        assertEquals(3_000L, straddleEnd.durationMs)

        // Spanning entire window
        val spanning = IntervalMath.clipInterval(5_000L, 25_000L, wStart, wEnd)
        assertNotNull(spanning)
        assertEquals(10_000L, spanning!!.startMs)
        assertEquals(20_000L, spanning.endMs)
        assertEquals(10_000L, spanning.durationMs)

        // Completely before window
        assertNull(IntervalMath.clipInterval(1_000L, 9_000L, wStart, wEnd))
        assertNull(IntervalMath.clipInterval(1_000L, 10_000L, wStart, wEnd)) // Touches start boundary: 0 duration

        // Completely after window
        assertNull(IntervalMath.clipInterval(20_000L, 25_000L, wStart, wEnd)) // Touches end boundary: 0 duration
        assertNull(IntervalMath.clipInterval(21_000L, 25_000L, wStart, wEnd))

        // Inverted or zero window
        assertNull(IntervalMath.clipInterval(12_000L, 15_000L, 20_000L, 10_000L))
        assertNull(IntervalMath.clipInterval(12_000L, 15_000L, 10_000L, 10_000L))
    }

    // =========================================================================
    // 4. Interval Merging & Union Semantics
    // =========================================================================

    @Test
    fun mergeIntervals_overlappingIntervalsMerged() {
        val intervals = listOf(
            IntervalMath.Interval(100L, 300L),
            IntervalMath.Interval(200L, 400L)
        )
        val merged = IntervalMath.mergeIntervals(intervals)
        assertEquals(1, merged.size)
        assertEquals(100L, merged[0].startMs)
        assertEquals(400L, merged[0].endMs)
        assertEquals(300L, IntervalMath.unionDurationMs(intervals))
    }

    @Test
    fun mergeIntervals_adjacentIntervalsMerged() {
        // [100..200) and [200..300) are adjacent and merge into [100..300)
        val intervals = listOf(
            IntervalMath.Interval(100L, 200L),
            IntervalMath.Interval(200L, 300L)
        )
        val merged = IntervalMath.mergeIntervals(intervals)
        assertEquals(1, merged.size)
        assertEquals(100L, merged[0].startMs)
        assertEquals(300L, merged[0].endMs)
        assertEquals(200L, IntervalMath.unionDurationMs(intervals))
    }

    @Test
    fun mergeIntervals_disjointIntervalsRetained() {
        val intervals = listOf(
            IntervalMath.Interval(100L, 200L),
            IntervalMath.Interval(300L, 400L)
        )
        val merged = IntervalMath.mergeIntervals(intervals)
        assertEquals(2, merged.size)
        assertEquals(100L, merged[0].startMs)
        assertEquals(200L, merged[0].endMs)
        assertEquals(300L, merged[1].startMs)
        assertEquals(400L, merged[1].endMs)
        assertEquals(200L, IntervalMath.unionDurationMs(intervals))
    }

    @Test
    fun mergeIntervals_nestedIntervalsAbsorbed() {
        val intervals = listOf(
            IntervalMath.Interval(100L, 500L),
            IntervalMath.Interval(200L, 300L)
        )
        val merged = IntervalMath.mergeIntervals(intervals)
        assertEquals(1, merged.size)
        assertEquals(100L, merged[0].startMs)
        assertEquals(500L, merged[0].endMs)
        assertEquals(400L, IntervalMath.unionDurationMs(intervals))
    }

    @Test
    fun mergeIntervals_duplicatesCollapsed() {
        val intervals = listOf(
            IntervalMath.Interval(100L, 200L),
            IntervalMath.Interval(100L, 200L),
            IntervalMath.Interval(100L, 200L)
        )
        val merged = IntervalMath.mergeIntervals(intervals)
        assertEquals(1, merged.size)
        assertEquals(100L, merged[0].startMs)
        assertEquals(200L, merged[0].endMs)
        assertEquals(100L, IntervalMath.unionDurationMs(intervals))
    }

    @Test
    fun mergeIntervals_invalidIntervalsDiscarded() {
        val intervals = listOf(
            IntervalMath.Interval(100L, 200L),
            IntervalMath.Interval(150L, 150L), // zero length
            IntervalMath.Interval(300L, 250L), // inverted
            IntervalMath.Interval(-50L, 50L)   // negative start
        )
        val merged = IntervalMath.mergeIntervals(intervals)
        assertEquals(1, merged.size)
        assertEquals(100L, merged[0].startMs)
        assertEquals(200L, merged[0].endMs)
    }

    @Test
    fun unionDurationFromPairs_handlesEmptyAndValidPairs() {
        assertEquals(0L, IntervalMath.unionDurationFromPairs(emptyList()))
        val pairs = listOf(Pair(100L, 200L), Pair(150L, 250L))
        assertEquals(150L, IntervalMath.unionDurationFromPairs(pairs))
    }

    // =========================================================================
    // 5. Intersection Calculation
    // =========================================================================

    @Test
    fun intersectDurationMs_computesExactOverlap() {
        val first = listOf(IntervalMath.Interval(100L, 300L), IntervalMath.Interval(500L, 700L))
        val second = listOf(IntervalMath.Interval(200L, 600L))

        // Overlaps:
        // [100..300) with [200..600) -> [200..300) = 100ms
        // [500..700) with [200..600) -> [500..600) = 100ms
        // Total overlap = 200ms
        val overlap = IntervalMath.intersectDurationMs(first, second)
        assertEquals(200L, overlap)
    }

    @Test
    fun intersectDurationMs_disjointSetsReturnZero() {
        val first = listOf(IntervalMath.Interval(100L, 200L))
        val second = listOf(IntervalMath.Interval(300L, 400L))
        assertEquals(0L, IntervalMath.intersectDurationMs(first, second))
    }
}
