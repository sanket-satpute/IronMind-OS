package com.sanket_satpute_20.ironmind.domain.model.observation

/**
 * A strict factual aggregation of [ObservationType.NOTIFICATION_RECEIVED] observations
 * over an explicitly defined time window.
 *
 * Package names, titles, and content are aggressively removed from this model before crossing the boundary.
 * No inference of behavior, urgency, or semantic meaning is permitted.
 */
data class NotificationObservationAggregation(
    val userId: String,
    val startTimeMs: Long,
    val endTimeMs: Long,
    val observationCount: Int,
    val uniquePackageCount: Int,
    val firstOccurrenceTimestamp: Long?,
    val lastOccurrenceTimestamp: Long?
)
