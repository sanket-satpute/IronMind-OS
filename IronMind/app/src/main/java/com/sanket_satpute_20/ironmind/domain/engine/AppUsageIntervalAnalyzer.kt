package com.sanket_satpute_20.ironmind.domain.engine

import com.sanket_satpute_20.ironmind.domain.model.ProtectionSession
import com.sanket_satpute_20.ironmind.domain.model.ProtectionSessionStatus
import com.sanket_satpute_20.ironmind.domain.model.context.AppSwitchTransition
import com.sanket_satpute_20.ironmind.domain.model.context.AppUsageInterval
import com.sanket_satpute_20.ironmind.domain.model.context.AppUsagePackageSummary
import com.sanket_satpute_20.ironmind.domain.model.context.AppUsageSummary
import com.sanket_satpute_20.ironmind.domain.model.context.AppUsageTemporalProvenance
import com.sanket_satpute_20.ironmind.domain.model.context.AppUsageTemporalSummary
import com.sanket_satpute_20.ironmind.domain.model.context.EpistemicAuthority
import com.sanket_satpute_20.ironmind.domain.model.context.FocusOverlapAvailability
import com.sanket_satpute_20.ironmind.domain.model.context.FocusProtectionOverlapSummary
import com.sanket_satpute_20.ironmind.domain.model.context.SourceAvailability
import com.sanket_satpute_20.ironmind.domain.model.observation.Observation
import com.sanket_satpute_20.ironmind.domain.model.observation.ObservationType

/**
 * Pure, deterministic analyzer for timestamped [ObservationType.APP_USAGE_SESSION] intervals,
 * chronological package transitions, short bursts, and temporal intersections with
 * [ProtectionSession] intervals.
 *
 * Semantic invariants:
 * - Enforces a bounded 7-day maximum historical lookback from [windowEndMs].
 * - Clips overlapping usage and protection intervals strictly to `[effectiveStartMs, windowEndMs]`.
 * - Deduplicates repeated observations deterministically by observation ID and exact `(packageName, rawStart, rawEnd)`.
 * - Unions overlapping intervals before computing aggregate usage duration and protection overlap so
 *   no millisecond is counted twice.
 * - Counts short bursts using unclipped `rawDurationMs` (when `clippedDurationMs > 0`) so a long
 *   session trimmed by the window boundary is not misclassified as a brief burst.
 * - Skips ambiguous simultaneous package transitions sharing the exact same `startTimeMs`.
 */
object AppUsageIntervalAnalyzer {

    const val DEFAULT_SHORT_BURST_THRESHOLD_MS: Long = 60_000L
    const val MAX_LOOKBACK_MS: Long = 7L * 24L * 60L * 60L * 1000L
    private const val MAX_RETAINED_TRANSITIONS: Int = 50

    private val TRACKABLE_PROTECTION_STATUSES = setOf(
        ProtectionSessionStatus.ACTIVE,
        ProtectionSessionStatus.COMPLETED,
        ProtectionSessionStatus.CANCELLED,
        ProtectionSessionStatus.OVERRIDDEN,
        ProtectionSessionStatus.EXPIRED
    )

    data class AnalysisOutput(
        val intervals: List<AppUsageInterval>,
        val appSummaries: List<AppUsageSummary>,
        val temporalSummary: AppUsageTemporalSummary,
        val lastObservedAt: Long?
    )

    private data class TimeRange(val startMs: Long, val endMs: Long) {
        val durationMs: Long get() = (endMs - startMs).coerceAtLeast(0L)
    }

    fun analyze(
        userId: String,
        observations: List<Observation>,
        protectionSessions: List<ProtectionSession>,
        windowStartMs: Long,
        windowEndMs: Long,
        nowMs: Long,
        usageAvailability: SourceAvailability,
        isUsageSourceReadable: Boolean,
        isProtectionHistoryAvailable: Boolean,
        isTruncated: Boolean = false,
        shortBurstThresholdMs: Long = DEFAULT_SHORT_BURST_THRESHOLD_MS,
        maxLookbackMs: Long = MAX_LOOKBACK_MS
    ): AnalysisOutput {
        val normalizedThresholdMs = shortBurstThresholdMs.coerceAtLeast(1L)
        val lookbackFloorMs = (windowEndMs - maxLookbackMs).coerceAtLeast(0L)
        val isLookbackClamped = windowStartMs < lookbackFloorMs
        val effectiveStartMs = maxOf(windowStartMs, lookbackFloorMs)

        val focusOverlapWhenUnavailable = FocusProtectionOverlapSummary(
            availability = if (isProtectionHistoryAvailable) {
                val validProt = extractClippedProtectionRanges(
                    userId = userId,
                    sessions = protectionSessions,
                    windowStartMs = effectiveStartMs,
                    windowEndMs = windowEndMs,
                    nowMs = nowMs
                )
                if (validProt.isEmpty()) FocusOverlapAvailability.EMPTY_NO_PROTECTION_SESSIONS
                else FocusOverlapAvailability.AVAILABLE
            } else {
                FocusOverlapAvailability.UNAVAILABLE
            },
            protectionSessionCount = if (isProtectionHistoryAvailable) {
                extractClippedProtectionRanges(
                    userId = userId,
                    sessions = protectionSessions,
                    windowStartMs = effectiveStartMs,
                    windowEndMs = windowEndMs,
                    nowMs = nowMs
                ).size
            } else 0,
            notes = if (!isProtectionHistoryAvailable) {
                "Protection session history unavailable; overlap cannot be determined."
            } else null
        )

        if (!isUsageSourceReadable || windowEndMs <= effectiveStartMs) {
            val unavailableSummary = AppUsageTemporalSummary(
                hasUsageData = false,
                windowStartTimeMs = windowStartMs,
                windowEndTimeMs = windowEndMs,
                effectiveStartTimeMs = effectiveStartMs,
                totalIntervalCount = 0,
                totalObservedDurationMs = null,
                distinctPackageCount = null,
                appSwitchCount = null,
                shortBurstCount = null,
                shortBurstThresholdMs = normalizedThresholdMs,
                focusOverlap = focusOverlapWhenUnavailable,
                provenance = AppUsageTemporalProvenance(
                    usageSourceAvailability = usageAvailability,
                    isProtectionHistoryAvailable = isProtectionHistoryAvailable,
                    maxLookbackMs = maxLookbackMs,
                    shortBurstThresholdMs = normalizedThresholdMs,
                    isLookbackClamped = isLookbackClamped,
                    isTruncated = isTruncated,
                    notes = "App usage telemetry unavailable or unreadable ($usageAvailability)."
                )
            )
            return AnalysisOutput(
                intervals = emptyList(),
                appSummaries = emptyList(),
                temporalSummary = unavailableSummary,
                lastObservedAt = null
            )
        }

        // 1. Filter by user, type, lookback floor, validity, then deduplicate and clip
        var droppedInvalid = 0
        var droppedBeyondLookback = 0
        var deduplicated = 0

        val idSeen = mutableSetOf<String>()
        val semanticSeen = mutableSetOf<Triple<String, Long, Long>>()
        val candidateIntervals = mutableListOf<AppUsageInterval>()

        val sortedObs = observations
            .asSequence()
            .filter { it.userId == userId && it.type == ObservationType.APP_USAGE_SESSION }
            .sortedWith(compareBy<Observation> { it.occurredAt }.thenBy { it.value }.thenBy { it.id })
            .toList()

        for (obs in sortedObs) {
            if (!idSeen.add(obs.id)) {
                deduplicated++
                continue
            }

            if (obs.occurredAt < lookbackFloorMs) {
                droppedBeyondLookback++
                continue
            }

            val pkg = obs.value.trim()
            val rawDurationMs = parseDurationMs(obs.context)
            if (pkg.isEmpty() || obs.occurredAt < 0L || rawDurationMs == null || rawDurationMs <= 0L) {
                droppedInvalid++
                continue
            }

            // Guard Long addition overflow cleanly
            val rawEndMs = if (obs.occurredAt > Long.MAX_VALUE - rawDurationMs) {
                Long.MAX_VALUE
            } else {
                obs.occurredAt + rawDurationMs
            }

            if (!semanticSeen.add(Triple(pkg, obs.occurredAt, rawEndMs))) {
                deduplicated++
                continue
            }

            // Clip to [effectiveStartMs, windowEndMs]
            val clippedStart = maxOf(obs.occurredAt, effectiveStartMs)
            val clippedEnd = minOf(rawEndMs, windowEndMs)
            if (clippedEnd <= clippedStart) {
                continue
            }

            val clippedDuration = clippedEnd - clippedStart
            candidateIntervals.add(
                AppUsageInterval(
                    observationId = obs.id,
                    userId = userId,
                    packageName = pkg,
                    rawStartTimeMs = obs.occurredAt,
                    rawEndTimeMs = rawEndMs,
                    rawDurationMs = rawDurationMs,
                    startTimeMs = clippedStart,
                    endTimeMs = clippedEnd,
                    durationMs = clippedDuration,
                    isClippedToWindow = clippedStart != obs.occurredAt || clippedEnd != rawEndMs,
                    recordedAt = obs.recordedAt,
                    source = obs.provenance.source,
                    sourceReference = obs.provenance.sourceReference,
                    authority = EpistemicAuthority.SYSTEM_RECORDED_OBSERVATION
                )
            )
        }

        val intervals = candidateIntervals.sortedWith(
            compareBy<AppUsageInterval> { it.startTimeMs }
                .thenBy { it.endTimeMs }
                .thenBy { it.packageName }
                .thenBy { it.observationId }
        )

        // 2. App switching analysis (chronological start groups, skipping simultaneous-start ambiguity)
        val switchAnalysis = computeSwitches(intervals)

        // 3. Short burst analysis (using rawDurationMs so boundary clipping doesn't create false bursts)
        val shortBurstCount = intervals.count { it.rawDurationMs <= normalizedThresholdMs }

        // 4. Union duration calculation (prevents double counting overlapping usage intervals)
        val mergedUsageRanges = mergeRanges(intervals.map { TimeRange(it.startTimeMs, it.endTimeMs) })
        val totalObservedDurationMs = mergedUsageRanges.sumOf { it.durationMs }

        // 5. True Focus Protection overlap calculation
        val clippedProtectionSessions = if (isProtectionHistoryAvailable) {
            extractClippedProtectionRanges(
                userId = userId,
                sessions = protectionSessions,
                windowStartMs = effectiveStartMs,
                windowEndMs = windowEndMs,
                nowMs = nowMs
            )
        } else {
            emptyList()
        }
        val mergedProtectionRanges = mergeRanges(clippedProtectionSessions)
        val mergedProtectionDurationMs = mergedProtectionRanges.sumOf { it.durationMs }

        val overlappingUsageDurationMs: Long?
        val outsideProtectionUsageDurationMs: Long?
        val intersectingIntervalCount: Int?
        val overlapAvailability: FocusOverlapAvailability

        if (!isProtectionHistoryAvailable) {
            overlapAvailability = FocusOverlapAvailability.UNAVAILABLE
            overlappingUsageDurationMs = null
            outsideProtectionUsageDurationMs = null
            intersectingIntervalCount = null
        } else if (clippedProtectionSessions.isEmpty()) {
            overlapAvailability = FocusOverlapAvailability.EMPTY_NO_PROTECTION_SESSIONS
            overlappingUsageDurationMs = 0L
            outsideProtectionUsageDurationMs = totalObservedDurationMs
            intersectingIntervalCount = 0
        } else {
            overlapAvailability = FocusOverlapAvailability.AVAILABLE
            val overlapMs = intersectTotalDuration(mergedUsageRanges, mergedProtectionRanges)
            overlappingUsageDurationMs = overlapMs
            outsideProtectionUsageDurationMs = (totalObservedDurationMs - overlapMs).coerceAtLeast(0L)
            intersectingIntervalCount = intervals.count { interval ->
                intersectTotalDuration(
                    listOf(TimeRange(interval.startTimeMs, interval.endTimeMs)),
                    mergedProtectionRanges
                ) > 0L
            }
        }

        // 6. Per-package summaries & authoritative AppUsageSummary mapping
        val groupedByPackage = intervals.groupBy { it.packageName }
        val packageSummaries = mutableListOf<AppUsagePackageSummary>()
        val appSummaries = mutableListOf<AppUsageSummary>()

        for ((pkg, pkgIntervals) in groupedByPackage) {
            val pkgMerged = mergeRanges(pkgIntervals.map { TimeRange(it.startTimeMs, it.endTimeMs) })
            val pkgDuration = pkgMerged.sumOf { it.durationMs }
            val pkgFirst = pkgIntervals.minOf { it.startTimeMs }
            val pkgLast = pkgIntervals.maxOf { it.endTimeMs }
            val pkgBursts = pkgIntervals.count { it.rawDurationMs <= normalizedThresholdMs }
            val pkgOverlap = if (!isProtectionHistoryAvailable) {
                null
            } else if (mergedProtectionRanges.isEmpty()) {
                0L
            } else {
                intersectTotalDuration(pkgMerged, mergedProtectionRanges)
            }

            packageSummaries.add(
                AppUsagePackageSummary(
                    packageName = pkg,
                    intervalCount = pkgIntervals.size,
                    totalDurationMs = pkgDuration,
                    shortBurstCount = pkgBursts,
                    firstObservedAt = pkgFirst,
                    lastObservedAt = pkgLast,
                    overlapWithProtectionDurationMs = pkgOverlap
                )
            )
            appSummaries.add(
                AppUsageSummary(
                    packageName = pkg,
                    totalDurationMs = pkgDuration,
                    sessionCount = pkgIntervals.size,
                    firstObservedAt = pkgFirst,
                    lastObservedAt = pkgLast,
                    authority = EpistemicAuthority.SYSTEM_RECORDED_OBSERVATION
                )
            )
        }

        packageSummaries.sortWith(
            compareByDescending<AppUsagePackageSummary> { it.totalDurationMs }
                .thenBy { it.packageName }
        )
        appSummaries.sortWith(
            compareByDescending<AppUsageSummary> { it.totalDurationMs }
                .thenBy { it.packageName }
        )

        val lastObservedAt = intervals.maxOfOrNull { it.endTimeMs }
        val focusOverlap = FocusProtectionOverlapSummary(
            availability = overlapAvailability,
            overlappingUsageDurationMs = overlappingUsageDurationMs,
            outsideProtectionUsageDurationMs = outsideProtectionUsageDurationMs,
            intersectingIntervalCount = intersectingIntervalCount,
            protectionSessionCount = clippedProtectionSessions.size,
            mergedProtectionDurationMs = if (isProtectionHistoryAvailable) mergedProtectionDurationMs else null,
            notes = when (overlapAvailability) {
                FocusOverlapAvailability.UNAVAILABLE -> "Protection session history unavailable; overlap cannot be determined."
                FocusOverlapAvailability.EMPTY_NO_PROTECTION_SESSIONS -> "No protection sessions recorded during the effective window."
                FocusOverlapAvailability.AVAILABLE -> null
            }
        )

        val noteParts = buildList {
            if (isLookbackClamped) {
                add("Requested start clamped to $maxLookbackMs ms lookback ceiling.")
            }
            if (isTruncated) {
                add("Observation query hit safety cap; most recent observations retained.")
            }
            if (droppedInvalid > 0) {
                add("Excluded $droppedInvalid observation(s) with missing or non-positive duration.")
            }
            if (deduplicated > 0) {
                add("Deduplicated $deduplicated repeated observation(s).")
            }
            if (switchAnalysis.ambiguousSimultaneousSkipped > 0) {
                add("Skipped ${switchAnalysis.ambiguousSimultaneousSkipped} ambiguous simultaneous-start transition(s).")
            }
        }

        val summary = AppUsageTemporalSummary(
            hasUsageData = intervals.isNotEmpty(),
            windowStartTimeMs = windowStartMs,
            windowEndTimeMs = windowEndMs,
            effectiveStartTimeMs = effectiveStartMs,
            totalIntervalCount = intervals.size,
            totalObservedDurationMs = totalObservedDurationMs,
            distinctPackageCount = groupedByPackage.size,
            appSwitchCount = switchAnalysis.switchCount,
            shortBurstCount = shortBurstCount,
            shortBurstThresholdMs = normalizedThresholdMs,
            packageSummaries = packageSummaries,
            recentTransitions = switchAnalysis.transitions.takeLast(MAX_RETAINED_TRANSITIONS),
            focusOverlap = focusOverlap,
            provenance = AppUsageTemporalProvenance(
                usageSourceAvailability = usageAvailability,
                isProtectionHistoryAvailable = isProtectionHistoryAvailable,
                maxLookbackMs = maxLookbackMs,
                shortBurstThresholdMs = normalizedThresholdMs,
                isLookbackClamped = isLookbackClamped,
                isTruncated = isTruncated,
                retainedRawObservationCount = sortedObs.size,
                validIntervalCount = intervals.size,
                droppedInvalidOrNonPositiveCount = droppedInvalid,
                droppedBeyondLookbackCount = droppedBeyondLookback,
                deduplicatedCount = deduplicated,
                ambiguousSimultaneousTransitionsSkipped = switchAnalysis.ambiguousSimultaneousSkipped,
                notes = noteParts.takeIf { it.isNotEmpty() }?.joinToString(" ")
            )
        )

        return AnalysisOutput(
            intervals = intervals,
            appSummaries = appSummaries,
            temporalSummary = summary,
            lastObservedAt = lastObservedAt
        )
    }

    private data class SwitchAnalysis(
        val switchCount: Int,
        val ambiguousSimultaneousSkipped: Int,
        val transitions: List<AppSwitchTransition>
    )

    private fun computeSwitches(sortedIntervals: List<AppUsageInterval>): SwitchAnalysis {
        if (sortedIntervals.isEmpty()) return SwitchAnalysis(0, 0, emptyList())

        var switchCount = 0
        var ambiguousSkipped = 0
        val transitions = mutableListOf<AppSwitchTransition>()

        var idx = 0
        var prevStartMs: Long? = null
        var prevEndMs: Long? = null
        var prevPackages: Set<String> = emptySet()

        while (idx < sortedIntervals.size) {
            val groupStartMs = sortedIntervals[idx].startTimeMs
            val groupPackages = linkedSetOf<String>()
            var groupMaxEndMs = sortedIntervals[idx].endTimeMs

            while (idx < sortedIntervals.size && sortedIntervals[idx].startTimeMs == groupStartMs) {
                groupPackages.add(sortedIntervals[idx].packageName)
                groupMaxEndMs = maxOf(groupMaxEndMs, sortedIntervals[idx].endTimeMs)
                idx++
            }

            if (groupPackages.size > 1) {
                ambiguousSkipped += groupPackages.size - 1
            }

            if (prevStartMs != null) {
                if (groupPackages.size == 1) {
                    val toPkg = groupPackages.first()
                    if (toPkg !in prevPackages) {
                        switchCount++
                        if (prevPackages.size == 1) {
                            val fromPkg = prevPackages.first()
                            transitions.add(
                                AppSwitchTransition(
                                    fromPackageName = fromPkg,
                                    toPackageName = toPkg,
                                    fromIntervalStartMs = prevStartMs,
                                    toIntervalStartMs = groupStartMs,
                                    gapMs = (groupStartMs - (prevEndMs ?: groupStartMs)).coerceAtLeast(0L)
                                )
                            )
                        }
                    }
                } else if (groupPackages.intersect(prevPackages).isEmpty()) {
                    // Simultaneous starts after previous package(s) clearly indicate leaving prevPackages,
                    // though exact ordering among the new simultaneous starts remains ambiguous.
                    switchCount++
                }
            }

            prevStartMs = groupStartMs
            prevEndMs = groupMaxEndMs
            prevPackages = groupPackages
        }

        return SwitchAnalysis(
            switchCount = switchCount,
            ambiguousSimultaneousSkipped = ambiguousSkipped,
            transitions = transitions
        )
    }

    private fun extractClippedProtectionRanges(
        userId: String,
        sessions: List<ProtectionSession>,
        windowStartMs: Long,
        windowEndMs: Long,
        nowMs: Long
    ): List<TimeRange> {
        if (windowEndMs <= windowStartMs) return emptyList()
        val result = mutableListOf<TimeRange>()
        for (session in sessions) {
            if (session.userId != userId || session.status !in TRACKABLE_PROTECTION_STATUSES) continue
            val rawEnd = when (session.status) {
                ProtectionSessionStatus.ACTIVE -> minOf(nowMs, session.scheduledEndAt ?: nowMs)
                ProtectionSessionStatus.COMPLETED,
                ProtectionSessionStatus.CANCELLED,
                ProtectionSessionStatus.OVERRIDDEN -> session.endedAt?.coerceAtLeast(session.startedAt) ?: continue
                ProtectionSessionStatus.EXPIRED -> {
                    session.endedAt?.coerceAtLeast(session.startedAt)
                        ?: session.scheduledEndAt?.let { minOf(nowMs, it) }
                        ?: continue
                }
                ProtectionSessionStatus.SCHEDULED -> continue
            }
            val clippedStart = maxOf(session.startedAt, windowStartMs)
            val clippedEnd = minOf(rawEnd.coerceAtLeast(session.startedAt), windowEndMs)
            if (clippedEnd > clippedStart) {
                result.add(TimeRange(clippedStart, clippedEnd))
            }
        }
        return result
    }

    private fun mergeRanges(ranges: List<TimeRange>): List<TimeRange> {
        if (ranges.isEmpty()) return emptyList()
        val sorted = ranges.sortedWith(compareBy<TimeRange> { it.startMs }.thenBy { it.endMs })
        val merged = mutableListOf<TimeRange>()
        var currentStart = sorted[0].startMs
        var currentEnd = sorted[0].endMs

        for (i in 1 until sorted.size) {
            val next = sorted[i]
            if (next.startMs <= currentEnd) {
                currentEnd = maxOf(currentEnd, next.endMs)
            } else {
                if (currentEnd > currentStart) {
                    merged.add(TimeRange(currentStart, currentEnd))
                }
                currentStart = next.startMs
                currentEnd = next.endMs
            }
        }
        if (currentEnd > currentStart) {
            merged.add(TimeRange(currentStart, currentEnd))
        }
        return merged
    }

    private fun intersectTotalDuration(
        firstMerged: List<TimeRange>,
        secondMerged: List<TimeRange>
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

    internal fun parseDurationMs(context: String): Long? {
        if (context.isBlank()) return null
        val durationPart = context.split(';')
            .firstOrNull { it.trim().startsWith("duration_ms=") }
            ?.trim()
            ?.removePrefix("duration_ms=")
            ?: return null
        return durationPart.toLongOrNull()
    }
}
