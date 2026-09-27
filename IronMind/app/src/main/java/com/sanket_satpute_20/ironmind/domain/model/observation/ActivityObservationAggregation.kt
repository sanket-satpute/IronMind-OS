package com.sanket_satpute_20.ironmind.domain.model.observation

/**
 * A strict factual aggregation of [ObservationType.ACTIVITY_CONTEXT_CHANGED] observations
 * over an explicitly defined time window.
 *
 * This contract does NOT include interpretive metrics, nor does it infer duration from
 * timestamp snapshots, as duration is not guaranteed by the underlying observation provider.
 */
data class ActivityObservationAggregation(
    val userId: String,
    val startTimeMs: Long,
    val endTimeMs: Long,
    val observationCount: Int,
    val distinctActivityStateCount: Int,
    val perStateObservationCounts: Map<String, Int>,
    val firstOccurrenceTimestamp: Long?,
    val lastOccurrenceTimestamp: Long?
)
