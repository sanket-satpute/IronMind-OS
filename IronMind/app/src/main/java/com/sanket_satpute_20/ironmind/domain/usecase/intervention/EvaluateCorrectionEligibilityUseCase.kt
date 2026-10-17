package com.sanket_satpute_20.ironmind.domain.usecase.intervention

import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.Event
import com.sanket_satpute_20.ironmind.domain.model.EventType
import com.sanket_satpute_20.ironmind.domain.model.MemoryConfirmationState
import com.sanket_satpute_20.ironmind.domain.model.barrier.BarrierConfirmationState
import com.sanket_satpute_20.ironmind.domain.model.barrier.BarrierHypothesis
import com.sanket_satpute_20.ironmind.domain.model.barrier.BarrierStatus
import com.sanket_satpute_20.ironmind.domain.model.intervention.*
import com.sanket_satpute_20.ironmind.domain.model.pattern.Pattern
import com.sanket_satpute_20.ironmind.domain.model.pattern.PatternStatus
import com.sanket_satpute_20.ironmind.domain.repository.BarrierRepository
import com.sanket_satpute_20.ironmind.domain.repository.EventRepository
import com.sanket_satpute_20.ironmind.domain.repository.InterventionRecommendationRepository
import com.sanket_satpute_20.ironmind.domain.repository.PatternRepository

/**
 * Deterministic, read-only evaluator that assesses whether an existing user correction
 * is eligible for further review of an underlying causal hypothesis.
 *
 * Epistemic boundaries:
 * - A nonblank INFERENCE_CORRECTION is at most a candidate for review; it is not proof of causal refutation.
 * - Never classifies a hypothesis as false, contradicted, disproven, or invalid solely because the correction is eligible.
 * - Purely read-only: does not mutate events, recommendations, patterns, or barriers.
 */
class EvaluateCorrectionEligibilityUseCase(
    private val eventRepository: EventRepository,
    private val recommendationRepository: InterventionRecommendationRepository,
    private val patternRepository: PatternRepository,
    private val barrierRepository: BarrierRepository
) {

    suspend operator fun invoke(
        userId: String,
        eventId: String,
        recommendationId: String? = null
    ): CorrectionEligibilityResult {
        val eventResult = eventRepository.getEvent(eventId)
        if (eventResult is Result.Failure) {
            return CorrectionEligibilityResult.Ineligible(
                CorrectionIneligibilityReason.DATA_RETRIEVAL_FAILED,
                "Failed to retrieve event: ${eventResult.error.message}"
            )
        }
        val event = (eventResult as Result.Success).data
            ?: return CorrectionEligibilityResult.Ineligible(
                CorrectionIneligibilityReason.EVENT_NOT_FOUND,
                "Event $eventId not found"
            )

        return evaluateEvent(userId, event, recommendationId)
    }

    suspend operator fun invoke(
        userId: String,
        event: Event,
        recommendationId: String? = null
    ): CorrectionEligibilityResult {
        return evaluateEvent(userId, event, recommendationId)
    }

    private suspend fun evaluateEvent(
        userId: String,
        event: Event,
        explicitRecommendationId: String?
    ): CorrectionEligibilityResult {
        // 1. Verify Event Ownership
        if (event.userId != userId) {
            return CorrectionEligibilityResult.Ineligible(
                CorrectionIneligibilityReason.EVENT_OWNERSHIP_MISMATCH,
                "Event ${event.id} belongs to user ${event.userId}, expected $userId"
            )
        }

        // 2. Verify Event Type is INTERVENTION_OVERRIDDEN (Rule 1)
        if (event.type != EventType.INTERVENTION_OVERRIDDEN) {
            return CorrectionEligibilityResult.Ineligible(
                CorrectionIneligibilityReason.EVENT_TYPE_NOT_OVERRIDDEN,
                "Event type ${event.type} is not INTERVENTION_OVERRIDDEN"
            )
        }

        // 3. Parse Metadata and Validate Correction Scope (Rule 2)
        val metadata = CorrectionMetadataParser.parse(event.metadata)
        if (metadata.scope != CorrectionScope.INFERENCE_CORRECTION) {
            return CorrectionEligibilityResult.Ineligible(
                CorrectionIneligibilityReason.IRRELEVANT_CORRECTION_SCOPE,
                "Correction scope ${metadata.scope} is not relevant for hypothesis review (requires INFERENCE_CORRECTION)"
            )
        }

        // 4. Validate Correction Text is Non-Blank (Rule 3)
        val text = metadata.correctedText?.trim()
        if (text.isNullOrBlank()) {
            return CorrectionEligibilityResult.Ineligible(
                CorrectionIneligibilityReason.BLANK_CORRECTION_TEXT,
                "Correction text is blank"
            )
        }

        // 5. Validate Correction Text is Not Generic Boilerplate (Rule 4)
        if (isBoilerplateText(text)) {
            return CorrectionEligibilityResult.Ineligible(
                CorrectionIneligibilityReason.GENERIC_BOILERPLATE_TEXT,
                "Correction text is generic boilerplate: \"$text\""
            )
        }

        // 6. Resolve Linked Recommendation with Causation Integrity (Rule 5)
        val eventCausationId = event.causationId?.trim()?.takeIf { it.isNotEmpty() }
        if (eventCausationId == null) {
            return CorrectionEligibilityResult.Ineligible(
                CorrectionIneligibilityReason.RECOMMENDATION_NOT_FOUND,
                "No linked recommendation ID found (event.causationId is null, empty, or blank)"
            )
        }

        val explicitId = explicitRecommendationId?.trim()?.takeIf { it.isNotEmpty() }
        if (explicitId != null && explicitId != eventCausationId) {
            return CorrectionEligibilityResult.Ineligible(
                CorrectionIneligibilityReason.RECOMMENDATION_CAUSATION_MISMATCH,
                "Explicit recommendation ID '$explicitId' does not match authoritative event causation ID '$eventCausationId'"
            )
        }

        val recId = eventCausationId

        val recResult = recommendationRepository.getRecommendation(recId)
        if (recResult is Result.Failure) {
            return CorrectionEligibilityResult.Ineligible(
                CorrectionIneligibilityReason.DATA_RETRIEVAL_FAILED,
                "Failed to retrieve recommendation: ${recResult.error.message}"
            )
        }
        val recommendation = (recResult as Result.Success).data
        if (recommendation == null) {
            return CorrectionEligibilityResult.Ineligible(
                CorrectionIneligibilityReason.RECOMMENDATION_NOT_FOUND,
                "Recommendation $recId not found"
            )
        }
        if (recommendation.userId != userId) {
            return CorrectionEligibilityResult.Ineligible(
                CorrectionIneligibilityReason.RECOMMENDATION_NOT_FOUND,
                "Recommendation ${recommendation.id} belongs to different user ${recommendation.userId}"
            )
        }

        // 7. Validate Causal Hypothesis Reference (Rules 6 & 7)
        val causalRef = recommendation.causalHypothesis
        if (causalRef == null || causalRef.id.isBlank()) {
            return CorrectionEligibilityResult.Ineligible(
                CorrectionIneligibilityReason.NO_CAUSAL_HYPOTHESIS_REFERENCE,
                "Recommendation ${recommendation.id} has no causal hypothesis reference"
            )
        }

        return when (causalRef.type) {
            CausalHypothesisType.PATTERN -> {
                evaluatePatternHypothesis(userId, event, recommendation, causalRef, text)
            }
            CausalHypothesisType.BARRIER -> {
                evaluateBarrierHypothesis(userId, event, recommendation, causalRef, text)
            }
        }
    }

    private suspend fun evaluatePatternHypothesis(
        userId: String,
        event: Event,
        recommendation: InterventionRecommendation,
        causalRef: CausalHypothesisReference,
        correctedText: String
    ): CorrectionEligibilityResult {
        // Rule 8: Referenced hypothesis must exist
        val patternResult = patternRepository.getPattern(causalRef.id)
        if (patternResult is Result.Failure) {
            return CorrectionEligibilityResult.Ineligible(
                CorrectionIneligibilityReason.DATA_RETRIEVAL_FAILED,
                "Failed to retrieve pattern: ${patternResult.error.message}"
            )
        }
        val pattern = (patternResult as Result.Success).data
            ?: return CorrectionEligibilityResult.Ineligible(
                CorrectionIneligibilityReason.REFERENCED_HYPOTHESIS_NOT_FOUND,
                "Pattern hypothesis ${causalRef.id} not found"
            )

        // Rule 9: Ownership validation
        if (pattern.userId != userId) {
            return CorrectionEligibilityResult.Ineligible(
                CorrectionIneligibilityReason.HYPOTHESIS_OWNERSHIP_MISMATCH,
                "Pattern hypothesis ${pattern.id} belongs to user ${pattern.userId}, expected $userId"
            )
        }

        // Rule 10: Lifecycle reviewable state
        if (!isPatternInReviewableState(pattern)) {
            return CorrectionEligibilityResult.Ineligible(
                CorrectionIneligibilityReason.HYPOTHESIS_NOT_IN_REVIEWABLE_STATE,
                "Pattern hypothesis ${pattern.id} is not in reviewable state (status=${pattern.status}, confirmationState=${pattern.confirmationState})"
            )
        }

        // Rule 12: All prerequisites satisfied
        return CorrectionEligibilityResult.Eligible(
            userId = userId,
            eventId = event.id,
            recommendationId = recommendation.id,
            causalHypothesis = causalRef,
            correctionScope = CorrectionScope.INFERENCE_CORRECTION,
            correctedText = correctedText
        )
    }

    private suspend fun evaluateBarrierHypothesis(
        userId: String,
        event: Event,
        recommendation: InterventionRecommendation,
        causalRef: CausalHypothesisReference,
        correctedText: String
    ): CorrectionEligibilityResult {
        // Rule 8: Referenced hypothesis must exist
        val barrierResult = barrierRepository.getBarrier(causalRef.id)
        if (barrierResult is Result.Failure) {
            return CorrectionEligibilityResult.Ineligible(
                CorrectionIneligibilityReason.DATA_RETRIEVAL_FAILED,
                "Failed to retrieve barrier: ${barrierResult.error.message}"
            )
        }
        val barrier = (barrierResult as Result.Success).data
            ?: return CorrectionEligibilityResult.Ineligible(
                CorrectionIneligibilityReason.REFERENCED_HYPOTHESIS_NOT_FOUND,
                "Barrier hypothesis ${causalRef.id} not found"
            )

        // Rule 9: Ownership validation
        if (barrier.userId != userId) {
            return CorrectionEligibilityResult.Ineligible(
                CorrectionIneligibilityReason.HYPOTHESIS_OWNERSHIP_MISMATCH,
                "Barrier hypothesis ${barrier.id} belongs to user ${barrier.userId}, expected $userId"
            )
        }

        // Rule 10: Lifecycle reviewable state
        if (!isBarrierInReviewableState(barrier)) {
            return CorrectionEligibilityResult.Ineligible(
                CorrectionIneligibilityReason.HYPOTHESIS_NOT_IN_REVIEWABLE_STATE,
                "Barrier hypothesis ${barrier.id} is not in reviewable state (status=${barrier.status}, confirmationState=${barrier.confirmationState})"
            )
        }

        // Rule 12: All prerequisites satisfied
        return CorrectionEligibilityResult.Eligible(
            userId = userId,
            eventId = event.id,
            recommendationId = recommendation.id,
            causalHypothesis = causalRef,
            correctionScope = CorrectionScope.INFERENCE_CORRECTION,
            correctedText = correctedText
        )
    }

    companion object {
        private val BOILERPLATE_CORRECTION_TEXTS = setOf(
            "user marked as corrected",
            "marked as corrected",
            "corrected",
            "user corrected"
        )

        fun isBoilerplateText(text: String?): Boolean {
            if (text.isNullOrBlank()) return false
            return BOILERPLATE_CORRECTION_TEXTS.contains(text.trim().lowercase())
        }

        fun isPatternInReviewableState(pattern: Pattern): Boolean {
            if (pattern.status != PatternStatus.ACTIVE) return false
            return when (pattern.confirmationState) {
                MemoryConfirmationState.UNCONFIRMED,
                MemoryConfirmationState.USER_CONFIRMED -> true
                MemoryConfirmationState.USER_REJECTED,
                MemoryConfirmationState.SYSTEM_CONFIRMED -> false
            }
        }

        fun isBarrierInReviewableState(barrier: BarrierHypothesis): Boolean {
            if (barrier.status != BarrierStatus.ACTIVE) return false
            return when (barrier.confirmationState) {
                BarrierConfirmationState.UNCONFIRMED,
                BarrierConfirmationState.CONFIRMED -> true
                BarrierConfirmationState.REJECTED,
                BarrierConfirmationState.DISMISSED -> false
            }
        }
    }
}
