package com.sanket_satpute_20.ironmind.domain.model.action

/**
 * Phase 3.5: User review and lifecycle states for a proposed action.
 *
 * Invariants:
 * - PROPOSED: Action presented to user; awaiting review.
 * - ACCEPTED: User approved the recommendation, but it is NOT executed yet.
 * - REJECTED: User decided not to accept the proposed action.
 * - DISMISSED: User dismissed the proposed action from the active view.
 * - DEFERRED: User chose to reconsider or postpone the action until later.
 * - EXECUTING: Execution in progress or interrupted during crash window.
 * - EXECUTED: User explicitly executed the approved action via domain use cases.
 * - EXECUTION_FAILED: User triggered execution, but the underlying use case failed.
 */
enum class ActionReviewStatus {
    PROPOSED,
    ACCEPTED,
    REJECTED,
    DISMISSED,
    DEFERRED,
    EXECUTING,
    EXECUTED,
    EXECUTION_FAILED
}
