package com.sanket_satpute_20.ironmind.domain.engine

import com.sanket_satpute_20.ironmind.domain.ai.BarrierCategory
import com.sanket_satpute_20.ironmind.domain.ai.InterventionObjective
import com.sanket_satpute_20.ironmind.domain.ai.InterventionType
import com.sanket_satpute_20.ironmind.domain.common.IdGenerator
import com.sanket_satpute_20.ironmind.domain.model.barrier.BarrierConfirmationState
import com.sanket_satpute_20.ironmind.domain.model.barrier.BarrierHypothesis
import com.sanket_satpute_20.ironmind.domain.model.barrier.BarrierStatus
import com.sanket_satpute_20.ironmind.domain.model.context.EpistemicAuthority
import com.sanket_satpute_20.ironmind.domain.model.intervention.*

/**
 * Phase 3.3: Pure deterministic engine for generating evidence-grounded Intervention Recommendations.
 *
 * Rules:
 * - Recommendations are SUGGESTIONS, never autonomous actions.
 * - Every recommendation connects directly to a valid barrier hypothesis via [CausalHypothesisReference].
 * - Respects previous user review responses via [InterventionEquivalencePolicy] (24h rejection suppression, anti-stacking).
 * - Distinguishes epistemic authority (AI_HYPOTHESIS).
 */
class InterventionReasoningEngine(
    private val equivalencePolicy: InterventionEquivalencePolicy,
    private val idGenerator: IdGenerator
) {

    companion object {
        const val RECOMMENDATION_TTL_MS = 24L * 60 * 60 * 1000 // 24 hours
    }

    fun generateRecommendations(
        userId: String,
        barrierHypotheses: List<BarrierHypothesis>,
        historicalRecommendations: List<InterventionRecommendation>,
        currentTimeMs: Long
    ): List<InterventionRecommendation> {
        require(userId.isNotBlank()) { "userId must not be blank" }

        val activeBarriers = barrierHypotheses.filter {
            it.userId == userId &&
            it.status == BarrierStatus.ACTIVE &&
            it.confirmationState != BarrierConfirmationState.REJECTED &&
            it.confirmationState != BarrierConfirmationState.DISMISSED
        }

        val userHistory = historicalRecommendations.filter { it.userId == userId }
        val generated = mutableListOf<InterventionRecommendation>()

        for (barrier in activeBarriers) {
            val recommendationTemplate = createRecommendationTemplate(barrier) ?: continue

            val candidate = InterventionRecommendationCandidate(
                interventionType = recommendationTemplate.type,
                objective = recommendationTemplate.objective,
                targetEntityId = barrier.id,
                targetEntityType = "BARRIER",
                rationale = recommendationTemplate.rationale,
                suggestedAction = recommendationTemplate.suggestedAction,
                confidence = barrier.confidence,
                causalHypothesisId = barrier.id,
                causalHypothesisType = CausalHypothesisType.BARRIER.name
            )

            // Check suppression against user review history
            val suppression = equivalencePolicy.evaluateSuppression(
                candidate = candidate,
                historyList = userHistory,
                isTargetCompleted = false,
                currentTimeMs = currentTimeMs
            )

            if (suppression is SuppressionResult.Allowed) {
                val recommendation = InterventionRecommendation(
                    id = idGenerator.generateId(),
                    userId = userId,
                    interventionType = candidate.interventionType,
                    objective = candidate.objective,
                    targetEntityId = candidate.targetEntityId,
                    targetEntityType = candidate.targetEntityType,
                    rationale = candidate.rationale,
                    suggestedAction = candidate.suggestedAction,
                    status = InterventionRecommendationStatus.PENDING,
                    createdAt = currentTimeMs,
                    expiresAt = currentTimeMs + RECOMMENDATION_TTL_MS,
                    causalHypothesis = CausalHypothesisReference(id = barrier.id, type = CausalHypothesisType.BARRIER),
                    expectedOutcome = recommendationTemplate.expectedOutcome,
                    suggestedMeasurement = recommendationTemplate.suggestedMeasurement,
                    epistemicAuthority = EpistemicAuthority.AI_HYPOTHESIS
                )
                generated.add(recommendation)
            }
        }

        return generated
    }

    private data class RecommendationTemplate(
        val type: InterventionType,
        val objective: InterventionObjective,
        val suggestedAction: String,
        val rationale: String,
        val expectedOutcome: String,
        val suggestedMeasurement: String
    )

    private fun createRecommendationTemplate(barrier: BarrierHypothesis): RecommendationTemplate? {
        return when (barrier.category) {
            BarrierCategory.EXCESSIVE_TASK_SIZE -> RecommendationTemplate(
                type = InterventionType.BREAK_DOWN,
                objective = InterventionObjective.REDUCE_FRICTION,
                suggestedAction = "Break postponed tasks into smaller subtasks (15–30 minutes each).",
                rationale = "Repeated postponements suggest high initiation friction. Smaller concrete steps lower the barrier to beginning.",
                expectedOutcome = "Reduced initiation hesitation and prompt start on the first actionable step.",
                suggestedMeasurement = "First subtask completed within 24 hours of decomposition."
            )
            BarrierCategory.COMPETING_PRIORITIES -> RecommendationTemplate(
                type = InterventionType.REDIRECT,
                objective = InterventionObjective.RECOVER_COMMITMENT,
                suggestedAction = "Review active commitments and pause secondary tasks to protect your top priority.",
                rationale = "Multiple missed commitments indicate competing demands for your attention.",
                expectedOutcome = "Clarified focus on the essential commitment without cognitive overload.",
                suggestedMeasurement = "Successful completion of the primary commitment over the next 2 days."
            )
            BarrierCategory.SCHEDULING_MISMATCH -> RecommendationTemplate(
                type = InterventionType.RESCHEDULE,
                objective = InterventionObjective.REDUCE_FRICTION,
                suggestedAction = "Adjust task deadlines or schedule high-focus work during morning peak windows.",
                rationale = "Tasks are consistently reaching overdue status, indicating that scheduled times clash with actual availability.",
                expectedOutcome = "Realistic task scheduling aligned with your highest-focus hours.",
                suggestedMeasurement = "Task completed before the adjusted deadline."
            )
            BarrierCategory.DISTRACTION -> RecommendationTemplate(
                type = InterventionType.REFLECT,
                objective = InterventionObjective.EXTRACT_INSIGHT,
                suggestedAction = "Review app usage during focus intervals and consider pausing notification-heavy apps.",
                rationale = "Elevated phone usage was observed during scheduled focus sessions. Note: Correlation does not imply causation.",
                expectedOutcome = "Heightened awareness of attention pull during committed work blocks.",
                suggestedMeasurement = "Increase in uninterrupted focus time during the next session."
            )
            BarrierCategory.ENVIRONMENTAL_FRICTION -> RecommendationTemplate(
                type = InterventionType.PROTECT,
                objective = InterventionObjective.PREVENT_DISRUPTION,
                suggestedAction = "Try a shorter 15–20 minute focus session with Do Not Disturb enabled.",
                rationale = "Recent focus sessions were interrupted before completion. Shorter blocks make uninterrupted deep work easier.",
                expectedOutcome = "Successful completion of a full focus session without premature cancellation.",
                suggestedMeasurement = "Uninterrupted completion of the next two focus sessions."
            )
            BarrierCategory.LOW_ENERGY -> RecommendationTemplate(
                type = InterventionType.RESCHEDULE,
                objective = InterventionObjective.REDUCE_FRICTION,
                suggestedAction = "Shift demanding tasks earlier in the day when cognitive energy is highest.",
                rationale = "Execution drops noticeably during late-night windows, indicating depleted energy.",
                expectedOutcome = "Matching demanding cognitive work to high-energy morning or afternoon hours.",
                suggestedMeasurement = "Completion of scheduled tasks before 18:00."
            )
            else -> null
        }
    }
}
