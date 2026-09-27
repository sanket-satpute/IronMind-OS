package com.sanket_satpute_20.ironmind.domain.model.observation

/**
 * A strict factual aggregation of [ObservationType.LOCATION_CONTEXT_CHANGED] observations
 * over an explicitly defined time window.
 *
 * Coordinates are aggressively removed from this model before crossing the boundary.
 * No inference of travel, duration, or semantic place identity is permitted.
 */
data class LocationObservationAggregation(
    val userId: String,
    val startTimeMs: Long,
    val endTimeMs: Long,
    val observationCount: Int,
    val uniqueCoordinateCount: Int,
    val coordinateChangeCount: Int,
    val firstOccurrenceTimestamp: Long?,
    val lastOccurrenceTimestamp: Long?
)
