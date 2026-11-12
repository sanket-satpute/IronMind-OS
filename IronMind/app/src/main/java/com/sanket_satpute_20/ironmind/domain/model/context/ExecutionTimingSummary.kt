package com.sanket_satpute_20.ironmind.domain.model.context

/**
 * Coarse local time-of-day buckets derived from explicit [java.time.ZoneId].
 */
enum class ExecutionTimeBucket(val displayLabel: String) {
    MORNING("Morning"),     // 06:00 - 11:59
    AFTERNOON("Afternoon"), // 12:00 - 16:59
    EVENING("Evening"),     // 17:00 - 21:59
    NIGHT("Night")          // 22:00 - 05:59
}

/**
 * Distinguishes timing evidence derived from lifecycle events vs legacy Task timestamp fallbacks.
 */
enum class TemporalSampleSource {
    EVENT_DERIVED,
    TASK_TIMESTAMP_FALLBACK
}

/**
 * Measured delta between a task's planned [scheduledAt] and observed `TASK_STARTED` [startedAt].
 * Negative values indicate starting earlier than scheduled; positive values indicate starting late.
 */
data class TaskStartDelaySample(
    val taskId: String,
    val scheduledAt: Long,
    val startedAt: Long,
    val delayMs: Long,
    val eventId: String,
    val authority: EpistemicAuthority = EpistemicAuthority.DERIVED_CALCULATION
)

/**
 * Measured delta between a task's planned [dueAt] and observed completion [completedAt].
 * Negative values indicate early completion; positive values indicate late completion.
 */
data class TaskCompletionDelaySample(
    val taskId: String,
    val dueAt: Long,
    val completedAt: Long,
    val delayMs: Long,
    val source: TemporalSampleSource,
    val eventId: String? = null,
    val authority: EpistemicAuthority = EpistemicAuthority.DERIVED_CALCULATION
)

/**
 * Explicit coverage, fallback, and truncation metadata for temporal execution calculations.
 */
data class ExecutionTimingProvenance(
    val eventRepositoryAvailable: Boolean = true,
    val eventDerivedStartCount: Int = 0,
    val eventDerivedCompletionCount: Int = 0,
    val timestampFallbackCompletionCount: Int = 0,
    val eventDerivedPostponementCount: Int = 0,
    val timestampFallbackPostponementCount: Int = 0,
    val unmeasuredStartsMissingScheduleCount: Int = 0,
    val unmeasuredCompletionsMissingDueDateCount: Int = 0,
    val legacyTasksMissingLifecycleEventsCount: Int = 0,
    val isEventHistoryTruncated: Boolean = false,
    val retainedWindowEventCount: Int = 0,
    val notes: String? = null
)

/**
 * Factual, window-scoped temporal execution summary computed from lifecycle events and
 * historical Task timestamp fallbacks without speculative behavioral or psychological claims.
 */
data class ExecutionTimingSummary(
    val hasSufficientData: Boolean = false,
    val windowStartTimeMs: Long = 0L,
    val windowEndTimeMs: Long = 0L,
    val timeZoneId: String = "UTC",
    val startDelaySamples: List<TaskStartDelaySample> = emptyList(),
    val averageStartDelayMs: Long? = null,
    val medianStartDelayMs: Long? = null,
    val completionDelaySamples: List<TaskCompletionDelaySample> = emptyList(),
    val averageCompletionDelayMs: Long? = null,
    val medianCompletionDelayMs: Long? = null,
    val forwardPostponementCount: Int = 0,
    val postponedTaskIds: List<String> = emptyList(),
    val startTimeDistribution: Map<ExecutionTimeBucket, Int> = emptyMap(),
    val completionTimeDistribution: Map<ExecutionTimeBucket, Int> = emptyMap(),
    val totalExecutionTimeDistribution: Map<ExecutionTimeBucket, Int> = emptyMap(),
    val provenance: ExecutionTimingProvenance = ExecutionTimingProvenance()
)
