package com.sanket_satpute_20.ironmind.domain.model.observation

/**
 * A strict factual aggregation of [ObservationType.CALENDAR_CONTEXT_CHANGED] observations
 * over an explicitly defined time window.
 *
 * NOTE: The [startTimeMs, endTimeMs) window refers to when the calendar snapshots were CAPTURED
 * (Observation.occurredAt), NOT when the scheduled calendar events actually occur.
 *
 * Titles and private event data are intentionally excluded from this output model to preserve privacy.
 */
data class CalendarObservationAggregation(
    val userId: String,
    val startTimeMs: Long,
    val endTimeMs: Long,
    val observationCount: Int,
    val uniqueEventCount: Int,
    val sumOfScheduledDurationMs: Long,
    val earliestScheduledEventStartMs: Long?,
    val latestScheduledEventEndMs: Long?
)
