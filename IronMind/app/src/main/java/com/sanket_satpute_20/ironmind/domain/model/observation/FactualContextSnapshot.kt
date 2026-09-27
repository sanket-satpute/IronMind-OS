package com.sanket_satpute_20.ironmind.domain.model.observation

/**
 * A strongly typed, stateless, derived cross-source factual boundary that combines
 * deterministic observation aggregations.
 *
 * This boundary prevents raw privacy-sensitive fields from leaking downstream.
 * The fields are nullable for architectural extensibility, but standard construction
 * must populate them directly with successful aggregation results.
 */
data class FactualContextSnapshot(
    val userId: String,
    val startTimeMs: Long,
    val endTimeMs: Long,
    val appUsage: AppUsageObservationAggregation?,
    val activity: ActivityObservationAggregation?,
    val calendar: CalendarObservationAggregation?,
    val location: LocationObservationAggregation?,
    val notifications: NotificationObservationAggregation?
)
