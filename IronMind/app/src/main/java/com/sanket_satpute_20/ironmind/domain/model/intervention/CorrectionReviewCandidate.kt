package com.sanket_satpute_20.ironmind.domain.model.intervention

/**
 * Ephemeral in-memory projection representing a correction candidate eligible for
 * human consideration during causal hypothesis review (Sprint V2.16-C3.8C).
 *
 * Epistemic & Autonomy Invariants (DATA_CONTRACT Sec 334, AUTONOMY_POLICY Sec 308, AI_BEHAVIOR_CONTRACT Sec 282):
 * 1. Dynamic In-Memory Computation: This is an in-memory projection computed on-the-fly from event history
 *    and current prerequisite evaluation; it is NOT a persisted entity, durable queue item, or completed review.
 * 2. Stage 3 Deferral Only: Eligibility means solely "deferred for consideration".
 *    A candidate is NOT a confirmed contradiction, disproven hypothesis, accepted correction,
 *    or authorization for learner-state mutation.
 * 3. Both [actionTaken] and [hypothesisDeclaredFalse] remain strictly false.
 */
data class CorrectionReviewCandidate(
    val userId: String,
    val eventId: String,
    val recommendationId: String,
    val causalHypothesis: CausalHypothesisReference,
    val correctionScope: CorrectionScope,
    val correctedText: String,
    val occurredAt: Long,
    val decision: CorrectionReviewDecision.DeferredForConsideration
) {
    val actionTaken: Boolean get() = decision.actionTaken
    val hypothesisDeclaredFalse: Boolean get() = decision.hypothesisDeclaredFalse
}
