package com.sanket_satpute_20.ironmind.domain.model.intervention

/**
 * Deterministic structured outcome for EvaluateCorrectionReviewDecisionUseCase (Sprint V2.16-C3.8B).
 *
 * Epistemic & Autonomy Invariants (DATA_CONTRACT Sec 334, AUTONOMY_POLICY Sec 308, AI_BEHAVIOR_CONTRACT Sec 282):
 * 1. Stage 3 (Review Decision) is strictly read-only and decoupled from Stage 4 (Lifecycle Action).
 * 2. An eligible correction represents permission for review consideration, NOT proof that a hypothesis is false.
 * 3. No automatic lifecycle action (rejection, confidence decay, retirement, or confirmation state mutation) is taken.
 * 4. All eligible reviews produce a neutral/deferred consideration outcome without guessing intent or applying arbitrary heuristics.
 */
sealed class CorrectionReviewDecision {
    abstract val actionTaken: Boolean
    abstract val hypothesisDeclaredFalse: Boolean

    /**
     * The input correction satisfied all eligibility prerequisites and is retained for deferred consideration.
     * In accordance with V2.16-C3.8A policy, NO lifecycle action was taken, and the hypothesis is NOT declared false.
     */
    data class DeferredForConsideration(
        val userId: String,
        val eventId: String,
        val recommendationId: String,
        val causalHypothesis: CausalHypothesisReference,
        val correctionScope: CorrectionScope,
        val correctedText: String,
        override val actionTaken: Boolean = false,
        override val hypothesisDeclaredFalse: Boolean = false,
        val rationale: String = DEFAULT_RATIONALE
    ) : CorrectionReviewDecision() {
        companion object {
            const val DEFAULT_RATIONALE =
                "Prerequisites satisfied; retained for consideration without hypothesis mutation per V2.16-C3.8A policy."
        }
    }

    /**
     * The input was not eligible for review, was malformed, or failed closed.
     * Preserves the underlying ineligibility reason where available.
     * No review action is performed.
     */
    data class NotReviewable(
        val ineligibilityReason: CorrectionIneligibilityReason? = null,
        val detail: String? = null,
        override val actionTaken: Boolean = false,
        override val hypothesisDeclaredFalse: Boolean = false
    ) : CorrectionReviewDecision()
}
