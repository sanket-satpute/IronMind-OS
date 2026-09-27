package com.sanket_satpute_20.ironmind.domain.model.observation

/**
 * A strict factual aggregation of [ObservationType.APP_USAGE_SESSION] observations
 * over an explicitly defined time window.
 *
 * This contract does NOT include interpretive metrics such as productivity, distraction,
 * or app categorization.
 */
data class AppUsageObservationAggregation(
    val userId: String,
    val startTimeMs: Long,
    val endTimeMs: Long,
    val observationCount: Int,
    val totalDurationMs: Long,
    val uniquePackageCount: Int
)
