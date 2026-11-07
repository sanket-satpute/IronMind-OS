package com.sanket_satpute_20.ironmind.domain.model.action

/**
 * Phase 3.5: Execution result record produced when a user explicitly executes an approved action.
 *
 * Invariant: Execution does NOT automatically mean success. An Outcome record with
 * ResultStatus.POSITIVE / NEGATIVE / NEUTRAL is required to measure actual success.
 */
data class ActionExecutionResult(
    val executedAt: Long,
    val success: Boolean,
    val affectedEntityId: String?,
    val affectedEntityType: String?,
    val message: String,
    val resultingSubTaskIds: List<String> = emptyList(),
    val previousDueAt: Long? = null,
    val newDueAt: Long? = null
)
