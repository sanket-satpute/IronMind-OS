package com.sanket_satpute_20.ironmind.domain.model.digital

import com.sanket_satpute_20.ironmind.domain.model.context.EpistemicAuthority
import com.sanket_satpute_20.ironmind.domain.model.context.SourceAvailability
import com.sanket_satpute_20.ironmind.domain.model.context.UsagePermissionStatus

/**
 * Explicit provenance identifying the underlying data source supplying app usage intelligence.
 */
enum class UsageSourceProvenance {
    LIVE_USAGE_STATS,
    ROOM_PERSISTED_OBSERVATIONS,
    COMBINED,
    NONE
}

/**
 * Represents per-app usage metrics for an observed calendar day or time window.
 *
 * Epistemic Rules:
 * - Purely observational metric (which package, duration, session count).
 * - Carries [EpistemicAuthority.SYSTEM_RECORDED_OBSERVATION].
 * - Does NOT assign normative labels (e.g. "distracting" or "unproductive").
 */
data class AppUsageItem(
    val packageName: String,
    val appName: String? = null,
    val totalDurationMs: Long,
    val sessionCount: Int,
    val firstObservedAt: Long? = null,
    val lastObservedAt: Long? = null,
    val percentageOfTotal: Float = 0f,
    val epistemicAuthority: EpistemicAuthority = EpistemicAuthority.SYSTEM_RECORDED_OBSERVATION,
    val sourceProvenance: UsageSourceProvenance = UsageSourceProvenance.NONE
)

/**
 * Factual day-over-day comparison between the target day and the immediately preceding day.
 * Only present when both days have observed data ([SourceAvailability.AVAILABLE]).
 *
 * Epistemic Rules:
 * - Strictly factual numeric delta.
 * - Never infers laziness, addiction, motivation, or mental-health conditions.
 */
data class DailyUsageComparison(
    val previousDayDurationMs: Long,
    val currentDayDurationMs: Long,
    val durationDeltaMs: Long,
    val percentageDelta: Float? = null,
    val comparisonNote: String
)

/**
 * Factual, evidence-grounded summary of daily app usage intelligence.
 *
 * Epistemic & Privacy Guarantees (Phase 3.6.1):
 * 1. Missing Usage Access is explicitly flagged as [SourceAvailability.PERMISSION_DENIED]
 *    or [SourceAvailability.PERMISSION_NOT_REQUESTED], NEVER reported as zero usage.
 * 2. When usage is unavailable or denied, [totalObservedDurationMs] is null, not 0L.
 * 3. Distinguishes UNAVAILABLE, PERMISSION_DENIED, EMPTY_NO_DATA, and AVAILABLE.
 * 4. Does not infer addiction, laziness, motivation, or mental-health conditions.
 * 5. Does not label high-usage applications as distracting.
 * 6. Reflects on-demand sampled statistics, not real-time streaming telemetry.
 * 7. Day-over-day comparison is only populated when sufficient prior baseline data exists.
 */
data class DailyAppUsageIntelligence(
    val userId: String,
    val targetDayStartMs: Long,
    val targetDayEndMs: Long,
    val permissionStatus: UsagePermissionStatus,
    val availability: SourceAvailability,
    val totalObservedDurationMs: Long?,
    val totalSessionsCount: Int?,
    val appUsages: List<AppUsageItem> = emptyList(),
    val mostUsedApps: List<AppUsageItem> = emptyList(),
    val comparisonWithPreviousDay: DailyUsageComparison? = null,
    val dataFreshnessMs: Long? = null,
    val isFresh: Boolean = true,
    val isRealTime: Boolean = false,
    val sourceProvenance: UsageSourceProvenance = UsageSourceProvenance.NONE,
    val notes: String? = null
)
