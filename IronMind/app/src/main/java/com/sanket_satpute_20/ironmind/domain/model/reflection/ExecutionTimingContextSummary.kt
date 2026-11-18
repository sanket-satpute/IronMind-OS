package com.sanket_satpute_20.ironmind.domain.model.reflection

import com.sanket_satpute_20.ironmind.domain.model.context.EpistemicAuthority
import com.sanket_satpute_20.ironmind.domain.model.context.ExecutionTimeBucket

/**
 * Factual, window-scoped summary of execution timing metrics for end-of-day reflection review.
 * Derived deterministically from lifecycle events and task schedules without moralizing language.
 */
data class ExecutionTimingContextSummary(
    val hasSufficientData: Boolean = false,
    val startedTaskCount: Int = 0,
    val completedTaskCount: Int = 0,
    val postponedCount: Int = 0,
    val averageStartDelayMs: Long? = null,
    val averageCompletionDelayMs: Long? = null,
    val mostActiveBucket: ExecutionTimeBucket? = null,
    val authority: EpistemicAuthority = EpistemicAuthority.DERIVED_CALCULATION,
    val notes: String? = null
)
