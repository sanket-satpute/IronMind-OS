package com.sanket_satpute_20.ironmind.domain.model.action

import com.sanket_satpute_20.ironmind.domain.model.context.EpistemicAuthority
import com.sanket_satpute_20.ironmind.domain.model.context.SourceFreshness

/**
 * Phase 3.5 & 3.5.1: A deterministic, evidence-backed next action proposed to the user.
 *
 * Invariants:
 * 1. User remains in full control: no autonomous execution occurs without explicit user action.
 * 2. Acceptance (ACCEPTED) is strictly decoupled from Execution (EXECUTED).
 * 3. Rejection (REJECTED) never refutes the underlying source insight or confirms/refutes barrier hypotheses.
 * 4. Epistemic authority is strictly maintained: AI hypotheses are clearly labeled and distinct from facts.
 * 5. Durably persisted with execution result and linked outcome reference.
 */
data class ProposedAction(
    val id: String,
    val userId: String,
    val category: ActionCategory,
    val title: String,
    val explanation: String,
    val sourceRecommendationId: String? = null,
    val sourceInsightId: String? = null,
    val targetEntityId: String? = null,
    val targetEntityType: String? = null, // "TASK", "PLAN", "GOAL", "COMMITMENT"
    val priority: ActionPriority = ActionPriority.MEDIUM,
    val urgency: ActionUrgency = ActionUrgency.TODAY,
    val supportingEvidenceIds: List<String> = emptyList(),
    val confidence: Float = 0.5f,
    val epistemicAuthority: EpistemicAuthority = EpistemicAuthority.AI_HYPOTHESIS,
    val sourceProvenance: List<String> = emptyList(),
    val sourceFreshness: SourceFreshness = SourceFreshness.CURRENT,
    val reviewStatus: ActionReviewStatus = ActionReviewStatus.PROPOSED,
    val createdAt: Long,
    val expiresAt: Long? = null,
    val reviewedAt: Long? = null,
    val deferredUntilMs: Long? = null,
    val executedAt: Long? = null,
    val lastAttemptedAt: Long? = null,
    val executionResult: ActionExecutionResult? = null,
    val outcomeId: String? = null,
    val suggestedSubTasks: List<String> = emptyList(),
    val suggestedNewDueAt: Long? = null
)
