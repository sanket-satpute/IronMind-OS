package com.sanket_satpute_20.ironmind.domain.model.context

import com.sanket_satpute_20.ironmind.domain.model.observation.ObservationSource

/**
 * Individual timestamped app usage interval preserved from [ObservationType.APP_USAGE_SESSION],
 * retaining both raw observed boundaries and boundaries clipped to the requested context window.
 */
data class AppUsageInterval(
    val observationId: String,
    val userId: String,
    val packageName: String,
    val rawStartTimeMs: Long,
    val rawEndTimeMs: Long,
    val rawDurationMs: Long,
    val startTimeMs: Long,
    val endTimeMs: Long,
    val durationMs: Long,
    val isClippedToWindow: Boolean,
    val recordedAt: Long,
    val source: ObservationSource = ObservationSource.APP_USAGE,
    val sourceReference: String? = null,
    val authority: EpistemicAuthority = EpistemicAuthority.SYSTEM_RECORDED_OBSERVATION
)

/**
 * Observed chronological transition from one distinct package to another across preserved intervals.
 * Represents a factual sequence transition, not inferred distraction or loss of focus.
 */
data class AppSwitchTransition(
    val fromPackageName: String,
    val toPackageName: String,
    val fromIntervalStartMs: Long,
    val toIntervalStartMs: Long,
    val gapMs: Long
)

/**
 * Windowed per-package interval and duration summary derived from preserved intervals.
 */
data class AppUsagePackageSummary(
    val packageName: String,
    val intervalCount: Int,
    val totalDurationMs: Long,
    val shortBurstCount: Int,
    val firstObservedAt: Long,
    val lastObservedAt: Long,
    val overlapWithProtectionDurationMs: Long? = null
)

/**
 * Distinguishes whether protection session history was available for overlap computation.
 */
enum class FocusOverlapAvailability {
    AVAILABLE,
    EMPTY_NO_PROTECTION_SESSIONS,
    UNAVAILABLE
}

/**
 * Temporal overlap between preserved app usage intervals and trackable focus protection sessions
 * clipped to the requested observation window. Represents temporal intersection only.
 */
data class FocusProtectionOverlapSummary(
    val availability: FocusOverlapAvailability = FocusOverlapAvailability.UNAVAILABLE,
    val overlappingUsageDurationMs: Long? = null,
    val outsideProtectionUsageDurationMs: Long? = null,
    val intersectingIntervalCount: Int? = null,
    val protectionSessionCount: Int = 0,
    val mergedProtectionDurationMs: Long? = null,
    val notes: String? = null
)

/**
 * Explicit retrieval coverage, deduplication, validation, and truncation provenance for
 * timestamped app usage interval analysis.
 */
data class AppUsageTemporalProvenance(
    val usageSourceAvailability: SourceAvailability = SourceAvailability.EMPTY_NO_DATA,
    val isProtectionHistoryAvailable: Boolean = true,
    val maxLookbackMs: Long = 7L * 24L * 60L * 60L * 1000L,
    val shortBurstThresholdMs: Long = 60_000L,
    val isLookbackClamped: Boolean = false,
    val isTruncated: Boolean = false,
    val retainedRawObservationCount: Int = 0,
    val validIntervalCount: Int = 0,
    val droppedInvalidOrNonPositiveCount: Int = 0,
    val droppedBeyondLookbackCount: Int = 0,
    val deduplicatedCount: Int = 0,
    val ambiguousSimultaneousTransitionsSkipped: Int = 0,
    val notes: String? = null
)

/**
 * Factual windowed summary of app usage intervals, package switching, short bursts, and
 * temporal overlap with protection sessions. Nullable counts/durations distinguish unavailable
 * or permission-denied telemetry from measured zero activity.
 */
data class AppUsageTemporalSummary(
    val hasUsageData: Boolean = false,
    val windowStartTimeMs: Long = 0L,
    val windowEndTimeMs: Long = 0L,
    val effectiveStartTimeMs: Long = 0L,
    val totalIntervalCount: Int = 0,
    val totalObservedDurationMs: Long? = null,
    val distinctPackageCount: Int? = null,
    val appSwitchCount: Int? = null,
    val shortBurstCount: Int? = null,
    val shortBurstThresholdMs: Long = 60_000L,
    val packageSummaries: List<AppUsagePackageSummary> = emptyList(),
    val recentTransitions: List<AppSwitchTransition> = emptyList(),
    val focusOverlap: FocusProtectionOverlapSummary = FocusProtectionOverlapSummary(),
    val provenance: AppUsageTemporalProvenance = AppUsageTemporalProvenance()
)
