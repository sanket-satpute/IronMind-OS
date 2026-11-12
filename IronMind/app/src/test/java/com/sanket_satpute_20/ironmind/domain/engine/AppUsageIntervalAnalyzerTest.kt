package com.sanket_satpute_20.ironmind.domain.engine

import com.sanket_satpute_20.ironmind.domain.model.EntitySource
import com.sanket_satpute_20.ironmind.domain.model.ProtectionSession
import com.sanket_satpute_20.ironmind.domain.model.ProtectionSessionStatus
import com.sanket_satpute_20.ironmind.domain.model.context.FocusOverlapAvailability
import com.sanket_satpute_20.ironmind.domain.model.context.SourceAvailability
import com.sanket_satpute_20.ironmind.domain.model.observation.Observation
import com.sanket_satpute_20.ironmind.domain.model.observation.ObservationProvenance
import com.sanket_satpute_20.ironmind.domain.model.observation.ObservationSource
import com.sanket_satpute_20.ironmind.domain.model.observation.ObservationType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUsageIntervalAnalyzerTest {

    private val userId = "user-a"
    private val windowStart = 1_700_000_000_000L
    private val windowEnd = windowStart + 86_400_000L

    private fun appObs(
        id: String,
        pkg: String,
        startMs: Long,
        durationMs: Long,
        userId: String = this.userId,
        rawContext: String? = null,
        type: ObservationType = ObservationType.APP_USAGE_SESSION
    ) = Observation(
        id = id,
        userId = userId,
        type = type,
        source = ObservationSource.APP_USAGE,
        occurredAt = startMs,
        recordedAt = startMs + durationMs.coerceAtLeast(0L),
        subjectId = null,
        value = pkg,
        context = rawContext ?: "duration_ms=$durationMs",
        confidence = null,
        provenance = ObservationProvenance(
            source = ObservationSource.APP_USAGE,
            sourceReference = "UsageStatsManager",
            capturedAt = startMs + durationMs.coerceAtLeast(0L)
        )
    )

    private fun protection(
        id: String,
        startedAt: Long,
        endedAt: Long?,
        scheduledEndAt: Long? = null,
        status: ProtectionSessionStatus = ProtectionSessionStatus.COMPLETED,
        userId: String = this.userId
    ) = ProtectionSession(
        id = id,
        userId = userId,
        startedAt = startedAt,
        scheduledEndAt = scheduledEndAt,
        endedAt = endedAt,
        status = status,
        source = EntitySource.USER,
        overrideAllowed = false,
        createdAt = startedAt,
        updatedAt = endedAt ?: startedAt
    )

    @Test
    fun `interval extraction clips boundaries rejects invalid durations deduplicates and enforces user isolation and lookback`() {
        val preWindowOverlap = appObs("obs-pre", "com.app.a", windowStart - 600_000L, 1_200_000L) // clips to [windowStart, windowStart + 600s]
        val postWindowOverlap = appObs("obs-post", "com.app.b", windowEnd - 300_000L, 900_000L) // clips to [windowEnd - 300s, windowEnd]
        val duplicateById = preWindowOverlap.copy()
        val duplicateSemantic = appObs("obs-post-dup", "com.app.b", windowEnd - 300_000L, 900_000L)
        val zeroDuration = appObs("obs-zero", "com.app.c", windowStart + 1_000_000L, 0L)
        val negativeDuration = appObs("obs-neg", "com.app.c", windowStart + 2_000_000L, -500L)
        val malformedDuration = appObs("obs-bad", "com.app.c", windowStart + 3_000_000L, 0L, rawContext = "malformed")
        val otherUser = appObs("obs-other", "com.app.other", windowStart + 10_000L, 500_000L, userId = "user-b")
        val beyondLookback = appObs("obs-old", "com.app.old", windowEnd - AppUsageIntervalAnalyzer.MAX_LOOKBACK_MS - 1_000L, 50_000L)

        // Wide 10-day requested window triggers 7-day lookback clamp
        val wideStart = windowEnd - (10L * 86_400_000L)
        val expectedLookbackFloor = windowEnd - AppUsageIntervalAnalyzer.MAX_LOOKBACK_MS

        val out = AppUsageIntervalAnalyzer.analyze(
            userId = userId,
            observations = listOf(
                postWindowOverlap, preWindowOverlap, duplicateById, duplicateSemantic,
                zeroDuration, negativeDuration, malformedDuration, otherUser, beyondLookback
            ),
            protectionSessions = emptyList(),
            windowStartMs = wideStart,
            windowEndMs = windowEnd,
            nowMs = windowEnd,
            usageAvailability = SourceAvailability.AVAILABLE,
            isUsageSourceReadable = true,
            isProtectionHistoryAvailable = true
        )

        assertEquals(2, out.intervals.size)
        // Chronological order preserved
        assertEquals("obs-pre", out.intervals[0].observationId)
        assertEquals(windowStart - 600_000L, out.intervals[0].rawStartTimeMs)
        assertEquals(1_200_000L, out.intervals[0].rawDurationMs)
        assertEquals(1_200_000L, out.intervals[0].durationMs) // within 7d clamped wideStart
        assertFalse(out.intervals[0].isClippedToWindow)

        assertEquals("obs-post", out.intervals[1].observationId)
        assertEquals(300_000L, out.intervals[1].durationMs)
        assertTrue(out.intervals[1].isClippedToWindow)

        assertEquals(expectedLookbackFloor, out.temporalSummary.effectiveStartTimeMs)
        assertTrue(out.temporalSummary.provenance.isLookbackClamped)
        assertEquals(1, out.temporalSummary.provenance.droppedBeyondLookbackCount)
        assertEquals(3, out.temporalSummary.provenance.droppedInvalidOrNonPositiveCount)
        assertEquals(2, out.temporalSummary.provenance.deduplicatedCount)
        assertEquals(1_500_000L, out.temporalSummary.totalObservedDurationMs)
        assertEquals(2, out.temporalSummary.distinctPackageCount)

        // Narrow window clips preWindowOverlap strictly to [windowStart, windowStart + 600_000L]
        val clippedOut = AppUsageIntervalAnalyzer.analyze(
            userId = userId,
            observations = listOf(preWindowOverlap),
            protectionSessions = emptyList(),
            windowStartMs = windowStart,
            windowEndMs = windowEnd,
            nowMs = windowEnd,
            usageAvailability = SourceAvailability.AVAILABLE,
            isUsageSourceReadable = true,
            isProtectionHistoryAvailable = true
        )
        assertEquals(1, clippedOut.intervals.size)
        assertEquals(windowStart, clippedOut.intervals[0].startTimeMs)
        assertEquals(600_000L, clippedOut.intervals[0].durationMs)
        assertTrue(clippedOut.intervals[0].isClippedToWindow)
        assertEquals(600_000L, clippedOut.temporalSummary.totalObservedDurationMs)
    }

    @Test
    fun `app switching handles sequential repeats gaps overlaps and skips ambiguous simultaneous starts`() {
        val base = windowStart + 10_000L
        val obs = listOf(
            // Input intentionally out of chronological order
            appObs("5", "com.app.b", base + 14_400_000L, 45_000L), // 4h large gap -> switch c->b (unless c was in simultaneous start!)
            appObs("1", "com.app.a", base, 30_000L),
            appObs("2", "com.app.a", base + 40_000L, 30_000L), // same pkg repeat -> 0 switch
            appObs("3", "com.app.b", base + 80_000L, 120_000L), // a -> b (switch 1)
            appObs("4a", "com.app.c", base + 210_000L, 50_000L), // b -> c (switch 2)
            appObs("4b", "com.app.d", base + 210_000L, 40_000L) // same startTimeMs as 4a -> simultaneous ambiguity skipped, switches from {b} -> {c, d} counted once cleanly!
        )

        val out = AppUsageIntervalAnalyzer.analyze(
            userId = userId,
            observations = obs,
            protectionSessions = emptyList(),
            windowStartMs = windowStart,
            windowEndMs = windowEnd,
            nowMs = windowEnd,
            usageAvailability = SourceAvailability.AVAILABLE,
            isUsageSourceReadable = true,
            isProtectionHistoryAvailable = true
        )

        // Transitions: {a}->{a}(0), {a}->{b}(+1), {b}->{c,d}(+1 disjoint unambiguous exit from b), {c,d}->{b}(+1 unambiguous return to b) => 3
        // Inside {c, d} simultaneous millisecond: 0 fabricated switch, skipped=1
        assertEquals(3, out.temporalSummary.appSwitchCount)
        assertEquals(1, out.temporalSummary.provenance.ambiguousSimultaneousTransitionsSkipped)
        assertEquals("com.app.a", out.intervals[0].packageName)
        assertEquals("com.app.b", out.intervals.last().packageName)

        // Two overlapping intervals of DIFFERENT packages without simultaneous start:
        // A [0..100s], B [50..90s], B_dup [50..90s] -> B_dup deduplicated, union duration = 100s (no double count), switches = 1
        val overlappingOut = AppUsageIntervalAnalyzer.analyze(
            userId = userId,
            observations = listOf(
                appObs("o1", "com.app.a", base, 100_000L),
                appObs("o2", "com.app.b", base + 50_000L, 40_000L),
                appObs("o3", "com.app.b", base + 50_000L, 40_000L)
            ),
            protectionSessions = emptyList(),
            windowStartMs = windowStart,
            windowEndMs = windowEnd,
            nowMs = windowEnd,
            usageAvailability = SourceAvailability.AVAILABLE,
            isUsageSourceReadable = true,
            isProtectionHistoryAvailable = true
        )
        assertEquals(2, overlappingOut.intervals.size)
        assertEquals(1, overlappingOut.temporalSummary.appSwitchCount)
        assertEquals(100_000L, overlappingOut.temporalSummary.totalObservedDurationMs)
        assertEquals(1, overlappingOut.temporalSummary.provenance.deduplicatedCount)
    }

    @Test
    fun `short bursts respect threshold boundaries ignore invalid or clipped long sessions and stay separate from switches`() {
        val base = windowStart + 60_000L
        val obs = listOf(
            appObs("exact", "com.app.a", base, 60_000L),          // == threshold -> burst
            appObs("below", "com.app.a", base + 100_000L, 25_000L), // < threshold -> burst (same pkg repeat: 0 switches!)
            appObs("above", "com.app.a", base + 200_000L, 60_001L), // > threshold -> NOT burst
            appObs("dup", "com.app.a", base + 100_000L, 25_000L),   // duplicate -> ignored
            appObs("invalid", "com.app.a", base + 300_000L, 0L),    // 0ms invalid -> ignored
            // Long 20m session crossing windowStart leaving 10s inside window -> rawDurationMs=20m -> NOT burst!
            appObs("longClip", "com.app.b", windowStart - 1_190_000L, 1_200_000L)
        )

        val out = AppUsageIntervalAnalyzer.analyze(
            userId = userId,
            observations = obs,
            protectionSessions = emptyList(),
            windowStartMs = windowStart,
            windowEndMs = windowEnd,
            nowMs = windowEnd,
            usageAvailability = SourceAvailability.AVAILABLE,
            isUsageSourceReadable = true,
            isProtectionHistoryAvailable = true,
            shortBurstThresholdMs = 60_000L
        )

        assertEquals(4, out.temporalSummary.totalIntervalCount)
        assertEquals(2, out.temporalSummary.shortBurstCount)
        assertEquals(1, out.temporalSummary.appSwitchCount) // b -> a once; 2 bursts inside a didn't inflate switches!
    }

    @Test
    fun `focus protection overlap computes exact union intersections across all session lifecycle statuses`() {
        val t0 = windowStart + 1_000_000L
        val now = t0 + 1_000_000L

        val obs = listOf(
            // 1. Fully contained in COMPLETED protection [t0..t0+100s] (scheduledEnd was +500s; endedAt +100s MUST win)
            appObs("u1", "com.app.focus", t0 + 10_000L, 40_000L), // overlap = 40s
            // 2. Overlapping app usage interval with u1 on [t0+20s..t0+60s] (40s total, +30s overlapping u1 -> union adds only 10s!)
            appObs("u2", "com.app.other", t0 + 20_000L, 40_000L),
            // 3. Partial overlap at beginning & end across CANCELLED [t0+200s..t0+250s] + overlapping OVERRIDDEN [t0+230s..t0+280s]
            // Merged protection segment = [t0+200s..t0+280s]. Usage [t0+180s..t0+300s] (120s): 80s inside, 40s outside!
            appObs("u3", "com.app.focus", t0 + 180_000L, 120_000L),
            // 4. ACTIVE session [t0+400s..scheduledEnd t0+460s] vs now (t0+1000s) -> effective end t0+460s.
            // Usage [t0+430s..t0+500s] (70s): 30s overlap with ACTIVE, 40s outside!
            appObs("u4", "com.app.focus", t0 + 430_000L, 70_000L),
            // 5. EXPIRED session ended before usage [t0+600s..t0+620s], usage at [t0+700s..t0+750s] (50s, 0 overlap)
            appObs("u5", "com.app.clean", t0 + 700_000L, 50_000L),
            // 6. Another user's usage & protection must be ignored
            appObs("u-other", "com.app.clean", t0 + 430_000L, 50_000L, userId = "user-b")
        )

        val sessions = listOf(
            protection("p1", t0, endedAt = t0 + 100_000L, scheduledEndAt = t0 + 500_000L, status = ProtectionSessionStatus.COMPLETED),
            protection("p2", t0 + 200_000L, endedAt = t0 + 250_000L, scheduledEndAt = t0 + 400_000L, status = ProtectionSessionStatus.CANCELLED),
            protection("p3", t0 + 230_000L, endedAt = t0 + 280_000L, scheduledEndAt = t0 + 400_000L, status = ProtectionSessionStatus.OVERRIDDEN),
            protection("p4", t0 + 400_000L, endedAt = null, scheduledEndAt = t0 + 460_000L, status = ProtectionSessionStatus.ACTIVE),
            protection("p5", t0 + 600_000L, endedAt = t0 + 620_000L, scheduledEndAt = t0 + 620_000L, status = ProtectionSessionStatus.EXPIRED),
            protection("p-other", t0 + 700_000L, endedAt = t0 + 800_000L, status = ProtectionSessionStatus.COMPLETED, userId = "user-b")
        )

        val out = AppUsageIntervalAnalyzer.analyze(
            userId = userId,
            observations = obs,
            protectionSessions = sessions,
            windowStartMs = windowStart,
            windowEndMs = windowEnd,
            nowMs = now,
            usageAvailability = SourceAvailability.AVAILABLE,
            isUsageSourceReadable = true,
            isProtectionHistoryAvailable = true
        )

        // Total merged usage = [10..60](50s) + [180..300](120s) + [430..500](70s) + [700..750](50s) = 290s
        assertEquals(290_000L, out.temporalSummary.totalObservedDurationMs)
        // Overlap = [10..60](50s) + [200..280](80s) + [430..460](30s) = 160s
        val overlap = out.temporalSummary.focusOverlap
        assertEquals(FocusOverlapAvailability.AVAILABLE, overlap.availability)
        assertEquals(160_000L, overlap.overlappingUsageDurationMs)
        assertEquals(130_000L, overlap.outsideProtectionUsageDurationMs)
        assertEquals(4, overlap.intersectingIntervalCount) // u1, u2, u3, u4 (u5 did not intersect)
        assertEquals(5, overlap.protectionSessionCount) // user-b ignored

        // Verify per-package overlap matches exact non-double-counted per-package union intersections:
        val focusPkg = out.temporalSummary.packageSummaries.first { it.packageName == "com.app.focus" }
        assertEquals(150_000L, focusPkg.overlapWithProtectionDurationMs) // 40s + 80s + 30s
    }

    @Test
    fun `empty vs unavailable protection history and unreadable usage are cleanly distinguished`() {
        val obs = listOf(appObs("u1", "com.app.a", windowStart + 10_000L, 90_000L))

        val emptyProtOut = AppUsageIntervalAnalyzer.analyze(
            userId = userId,
            observations = obs,
            protectionSessions = emptyList(),
            windowStartMs = windowStart,
            windowEndMs = windowEnd,
            nowMs = windowEnd,
            usageAvailability = SourceAvailability.AVAILABLE,
            isUsageSourceReadable = true,
            isProtectionHistoryAvailable = true
        )
        assertEquals(FocusOverlapAvailability.EMPTY_NO_PROTECTION_SESSIONS, emptyProtOut.temporalSummary.focusOverlap.availability)
        assertEquals(0L, emptyProtOut.temporalSummary.focusOverlap.overlappingUsageDurationMs)
        assertEquals(90_000L, emptyProtOut.temporalSummary.focusOverlap.outsideProtectionUsageDurationMs)
        assertEquals(0, emptyProtOut.temporalSummary.focusOverlap.intersectingIntervalCount)

        val unavailableProtOut = AppUsageIntervalAnalyzer.analyze(
            userId = userId,
            observations = obs,
            protectionSessions = emptyList(),
            windowStartMs = windowStart,
            windowEndMs = windowEnd,
            nowMs = windowEnd,
            usageAvailability = SourceAvailability.AVAILABLE,
            isUsageSourceReadable = true,
            isProtectionHistoryAvailable = false
        )
        assertEquals(FocusOverlapAvailability.UNAVAILABLE, unavailableProtOut.temporalSummary.focusOverlap.availability)
        assertNull(unavailableProtOut.temporalSummary.focusOverlap.overlappingUsageDurationMs)
        assertNull(unavailableProtOut.temporalSummary.focusOverlap.outsideProtectionUsageDurationMs)
        assertNull(unavailableProtOut.temporalSummary.focusOverlap.intersectingIntervalCount)

        val deniedUsageOut = AppUsageIntervalAnalyzer.analyze(
            userId = userId,
            observations = obs,
            protectionSessions = emptyList(),
            windowStartMs = windowStart,
            windowEndMs = windowEnd,
            nowMs = windowEnd,
            usageAvailability = SourceAvailability.PERMISSION_DENIED,
            isUsageSourceReadable = false,
            isProtectionHistoryAvailable = true
        )
        assertFalse(deniedUsageOut.temporalSummary.hasUsageData)
        assertNull(deniedUsageOut.temporalSummary.totalObservedDurationMs)
        assertNull(deniedUsageOut.temporalSummary.appSwitchCount)
        assertNull(deniedUsageOut.temporalSummary.shortBurstCount)
        assertTrue(deniedUsageOut.intervals.isEmpty())
    }
}
