package com.sanket_satpute_20.ironmind.domain.usecase.intervention

import com.sanket_satpute_20.ironmind.domain.model.intervention.*

/**
 * Deterministic, read-only use case that evaluates a correction review decision (Sprint V2.16-C3.8B).
 *
 * Epistemic boundaries & contracts:
 * - DATA_CONTRACT Section 334: Four-stage separation (Stage 3 Review Decision).
 * - AUTONOMY_POLICY Section 308: Zero autonomous mutation authority.
 * - AI_BEHAVIOR_CONTRACT Section 282: Eligible correction != hypothesis refutation.
 * - Consumes already validated CorrectionEligibilityResult; does NOT trust unvalidated caller identifiers.
 * - Fails closed on null, malformed, or unsupported inputs.
 * - Purely read-only: zero database writes, zero repository mutations, zero event persistence.
 */
class EvaluateCorrectionReviewDecisionUseCase(
    private val evaluateCorrectionEligibilityUseCase: EvaluateCorrectionEligibilityUseCase? = null
) {

    /**
     * Evaluates a review decision from a pre-computed CorrectionEligibilityResult.
     */
    operator fun invoke(eligibilityResult: CorrectionEligibilityResult?): CorrectionReviewDecision {
        if (eligibilityResult == null) {
            return CorrectionReviewDecision.NotReviewable(
                ineligibilityReason = null,
                detail = "Eligibility result must not be null"
            )
        }

        return when (eligibilityResult) {
            is CorrectionEligibilityResult.Ineligible -> {
                CorrectionReviewDecision.NotReviewable(
                    ineligibilityReason = eligibilityResult.reason,
                    detail = eligibilityResult.detail
                )
            }
            is CorrectionEligibilityResult.Eligible -> {
                evaluateEligible(eligibilityResult)
            }
        }
    }

    /**
     * Convenience typed overload for an Eligible input.
     */
    operator fun invoke(eligible: CorrectionEligibilityResult.Eligible?): CorrectionReviewDecision {
        if (eligible == null) {
            return CorrectionReviewDecision.NotReviewable(
                ineligibilityReason = null,
                detail = "Eligible result must not be null"
            )
        }
        return evaluateEligible(eligible)
    }

    /**
     * Convenience typed overload for an Ineligible input.
     */
    operator fun invoke(ineligible: CorrectionEligibilityResult.Ineligible?): CorrectionReviewDecision {
        if (ineligible == null) {
            return CorrectionReviewDecision.NotReviewable(
                ineligibilityReason = null,
                detail = "Ineligible result must not be null"
            )
        }
        return CorrectionReviewDecision.NotReviewable(
            ineligibilityReason = ineligible.reason,
            detail = ineligible.detail
        )
    }

    /**
     * Optional pipeline integration: evaluates eligibility first via the configured use case,
     * then determines the review decision.
     */
    suspend fun evaluate(
        userId: String,
        eventId: String,
        recommendationId: String? = null
    ): CorrectionReviewDecision {
        val eligibilityUseCase = evaluateCorrectionEligibilityUseCase
            ?: return CorrectionReviewDecision.NotReviewable(
                ineligibilityReason = CorrectionIneligibilityReason.DATA_RETRIEVAL_FAILED,
                detail = "EvaluateCorrectionEligibilityUseCase dependency is not configured"
            )
        val eligibilityResult = eligibilityUseCase(userId, eventId, recommendationId)
        return invoke(eligibilityResult)
    }

    private fun evaluateEligible(eligible: CorrectionEligibilityResult.Eligible): CorrectionReviewDecision {
        // Fail closed on malformed fields
        if (eligible.userId.isBlank()) {
            return CorrectionReviewDecision.NotReviewable(
                ineligibilityReason = CorrectionIneligibilityReason.EVENT_OWNERSHIP_MISMATCH,
                detail = "Eligible result contains blank userId"
            )
        }
        if (eligible.eventId.isBlank()) {
            return CorrectionReviewDecision.NotReviewable(
                ineligibilityReason = CorrectionIneligibilityReason.EVENT_NOT_FOUND,
                detail = "Eligible result contains blank eventId"
            )
        }
        if (eligible.recommendationId.isBlank()) {
            return CorrectionReviewDecision.NotReviewable(
                ineligibilityReason = CorrectionIneligibilityReason.RECOMMENDATION_NOT_FOUND,
                detail = "Eligible result contains blank recommendationId"
            )
        }
        if (eligible.causalHypothesis.id.isBlank()) {
            return CorrectionReviewDecision.NotReviewable(
                ineligibilityReason = CorrectionIneligibilityReason.NO_CAUSAL_HYPOTHESIS_REFERENCE,
                detail = "Eligible result contains blank causalHypothesis id"
            )
        }
        if (eligible.correctionScope != CorrectionScope.INFERENCE_CORRECTION) {
            return CorrectionReviewDecision.NotReviewable(
                ineligibilityReason = CorrectionIneligibilityReason.IRRELEVANT_CORRECTION_SCOPE,
                detail = "Correction scope ${eligible.correctionScope} is not reviewable (requires INFERENCE_CORRECTION)"
            )
        }
        if (eligible.correctedText.isBlank()) {
            return CorrectionReviewDecision.NotReviewable(
                ineligibilityReason = CorrectionIneligibilityReason.BLANK_CORRECTION_TEXT,
                detail = "Correction text is blank"
            )
        }
        if (EvaluateCorrectionEligibilityUseCase.isBoilerplateText(eligible.correctedText)) {
            return CorrectionReviewDecision.NotReviewable(
                ineligibilityReason = CorrectionIneligibilityReason.GENERIC_BOILERPLATE_TEXT,
                detail = "Correction text is generic boilerplate"
            )
        }

        // Under V2.16-C3.8A policy, return neutral/deferred consideration outcome without hypothesis mutation.
        return CorrectionReviewDecision.DeferredForConsideration(
            userId = eligible.userId,
            eventId = eligible.eventId,
            recommendationId = eligible.recommendationId,
            causalHypothesis = eligible.causalHypothesis,
            correctionScope = eligible.correctionScope,
            correctedText = eligible.correctedText,
            actionTaken = false,
            hypothesisDeclaredFalse = false
        )
    }
}
