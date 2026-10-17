package com.sanket_satpute_20.ironmind.domain.model.intervention

/**
 * Deterministic reasons why a correction event or its linked causal hypothesis
 * is not eligible for further causal hypothesis review.
 */
enum class CorrectionIneligibilityReason {
    EVENT_NOT_FOUND,
    EVENT_OWNERSHIP_MISMATCH,
    EVENT_TYPE_NOT_OVERRIDDEN,
    IRRELEVANT_CORRECTION_SCOPE,
    BLANK_CORRECTION_TEXT,
    GENERIC_BOILERPLATE_TEXT,
    RECOMMENDATION_NOT_FOUND,
    RECOMMENDATION_CAUSATION_MISMATCH,
    NO_CAUSAL_HYPOTHESIS_REFERENCE,
    UNSUPPORTED_OR_INCONSISTENT_HYPOTHESIS_TYPE,
    REFERENCED_HYPOTHESIS_NOT_FOUND,
    HYPOTHESIS_OWNERSHIP_MISMATCH,
    HYPOTHESIS_NOT_IN_REVIEWABLE_STATE,
    DATA_RETRIEVAL_FAILED
}

/**
 * Deterministic structured outcome for EvaluateCorrectionEligibilityUseCase.
 *
 * Epistemic boundary:
 * Eligibility means the correction meets all documented prerequisites for further review
 * of the referenced causal hypothesis. It does NOT assert that the hypothesis is false,
 * contradicted, disproven, or invalid.
 */
sealed class CorrectionEligibilityResult {
    data class Eligible(
        val userId: String,
        val eventId: String,
        val recommendationId: String,
        val causalHypothesis: CausalHypothesisReference,
        val correctionScope: CorrectionScope,
        val correctedText: String
    ) : CorrectionEligibilityResult()

    data class Ineligible(
        val reason: CorrectionIneligibilityReason,
        val detail: String? = null
    ) : CorrectionEligibilityResult()
}
