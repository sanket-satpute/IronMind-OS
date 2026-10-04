package com.sanket_satpute_20.ironmind.domain.usecase.ai

import com.sanket_satpute_20.ironmind.domain.ai.AIOutput
import com.sanket_satpute_20.ironmind.domain.ai.AIRequest
import com.sanket_satpute_20.ironmind.domain.ai.AIRequestType
import com.sanket_satpute_20.ironmind.domain.ai.InterventionType
import com.sanket_satpute_20.ironmind.domain.ai.IronMindAI
import com.sanket_satpute_20.ironmind.domain.ai.isValid
import com.sanket_satpute_20.ironmind.domain.common.Clock
import com.sanket_satpute_20.ironmind.domain.common.IdGenerator
import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.AutonomyCapability
import com.sanket_satpute_20.ironmind.domain.model.AutonomyLevel
import com.sanket_satpute_20.ironmind.domain.model.intervention.InterventionEquivalencePolicy
import com.sanket_satpute_20.ironmind.domain.model.intervention.InterventionRecommendation
import com.sanket_satpute_20.ironmind.domain.model.intervention.InterventionRecommendationResult
import com.sanket_satpute_20.ironmind.domain.model.intervention.InterventionRecommendationStatus
import com.sanket_satpute_20.ironmind.domain.model.intervention.RecommendationContext
import com.sanket_satpute_20.ironmind.domain.model.intervention.SuppressionResult
import com.sanket_satpute_20.ironmind.domain.model.intervention.TargetCompletionResolver
import com.sanket_satpute_20.ironmind.domain.model.intervention.TargetCompletionResult
import com.sanket_satpute_20.ironmind.domain.model.intervention.toCandidate
import com.sanket_satpute_20.ironmind.domain.repository.AutonomySettingsRepository
import com.sanket_satpute_20.ironmind.domain.repository.InterventionRecommendationRepository

/**
 * V2.12: Intervention Recommendation Engine with integrated suppression policies.
 *
 * Composes:
 * - Autonomy capability check (INTERVENTION_GENERATION)
 * - AI reasoning layer
 * - Structural AI validation
 * - Deterministic objective/target validation
 * - Target completion resolution
 * - Semantic equivalence and suppression policy
 * - Recommendation persistence (only on Allowed)
 */
class RecommendInterventionUseCase(
    private val ironMindAI: IronMindAI,
    private val clock: Clock,
    private val idGenerator: IdGenerator,
    private val autonomySettingsRepository: AutonomySettingsRepository,
    private val recommendationRepository: InterventionRecommendationRepository,
    private val targetCompletionResolver: TargetCompletionResolver,
    private val equivalencePolicy: InterventionEquivalencePolicy
) {
    suspend operator fun invoke(
        context: RecommendationContext
    ): Result<InterventionRecommendationResult, Exception> {

        // 1. Autonomy capability check
        val autonomyResult = autonomySettingsRepository.getSettings(context.userId)
        when (autonomyResult) {
            is Result.Success -> {
                val level = autonomyResult.data.getLevel(AutonomyCapability.INTERVENTION_GENERATION)
                if (level == AutonomyLevel.OFF) {
                    return Result.Success(InterventionRecommendationResult.NoRecommendation)
                }
                // SUGGEST_ONLY, ASK_BEFORE_ACTION, FULL_AUTO all generate recommendations in V2
            }
            is Result.Failure -> return Result.Failure(autonomyResult.error)
        }

        // 2. Build AI request and call AI
        val request = AIRequest(
            userId = context.userId,
            input = formatContextForAi(context),
            requestType = AIRequestType.INTERVENTION_SUGGESTION
        )

        val aiResult = ironMindAI.process(request)

        return when (aiResult) {
            is Result.Success -> {
                val output = aiResult.data

                // 3. Handle AI NoAction
                if (output is AIOutput.NoAction) {
                    return Result.Success(InterventionRecommendationResult.NoRecommendation)
                }

                // 4. Validate AI output type
                if (output !is AIOutput.InterventionRecommendation) {
                    return Result.Failure(Exception("AI returned unexpected output type: ${output.javaClass.simpleName}"))
                }

                // 5. Structural AI validation
                if (!output.isValid()) {
                    return Result.Success(InterventionRecommendationResult.NoRecommendation)
                }

                // 6. Map to candidate
                val candidate = output.toCandidate()

                // 7. STAY_SILENT check
                if (candidate.interventionType == InterventionType.STAY_SILENT) {
                    return Result.Success(InterventionRecommendationResult.NoRecommendation)
                }

                // 8. Content validation
                if (candidate.suggestedAction.isBlank() || candidate.rationale.isBlank()) {
                    return Result.Success(InterventionRecommendationResult.NoRecommendation)
                }

                // 9. Target validation
                if (candidate.targetEntityId == null) {
                    if (candidate.targetEntityType != null) {
                        return Result.Success(InterventionRecommendationResult.NoRecommendation)
                    }
                    if (candidate.interventionType == InterventionType.PROTECT) {
                        return Result.Success(InterventionRecommendationResult.NoRecommendation)
                    }
                } else {
                    if (candidate.targetEntityType == null) {
                        return Result.Success(InterventionRecommendationResult.NoRecommendation)
                    }
                    val targetId = candidate.targetEntityId
                    val isValid = when (candidate.targetEntityType) {
                        "GOAL" -> context.activeGoals.any { it.id == targetId }
                        "COMMITMENT" -> context.activeCommitments.any { it.id == targetId }
                        "REFLECTION" -> context.recentReflections.any { it.id == targetId }
                        else -> false
                    }
                    if (!isValid) {
                        return Result.Success(InterventionRecommendationResult.NoRecommendation)
                    }
                }

                // 10. Target completion resolution
                val completionResult = targetCompletionResolver.resolve(
                    userId = context.userId,
                    targetEntityType = candidate.targetEntityType,
                    targetEntityId = candidate.targetEntityId
                )
                val isTargetCompleted = completionResult == TargetCompletionResult.COMPLETED

                // 11. Load historical recommendations for equivalence evaluation
                val historyResult = recommendationRepository.getEquivalentRecommendations(
                    userId = context.userId,
                    objective = candidate.objective.name,
                    targetEntityType = candidate.targetEntityType,
                    targetEntityId = candidate.targetEntityId
                )
                val historyList = when (historyResult) {
                    is Result.Success -> historyResult.data
                    is Result.Failure -> {
                        // Repository failure: cannot safely evaluate suppression.
                        // Fail the operation rather than silently skipping suppression.
                        return Result.Failure(historyResult.error)
                    }
                }

                // 12. Evaluate suppression policy
                val currentTimeMs = clock.currentTimeMillis()
                val suppressionResult = equivalencePolicy.evaluateSuppression(
                    candidate = candidate,
                    historyList = historyList,
                    isTargetCompleted = isTargetCompleted,
                    currentTimeMs = currentTimeMs
                )

                // 13. Apply suppression result
                when (suppressionResult) {
                    is SuppressionResult.Allowed -> {
                        // Policy allows — construct and persist recommendation
                        val recommendation = InterventionRecommendation(
                            id = idGenerator.generateId(),
                            userId = context.userId,
                            interventionType = candidate.interventionType,
                            objective = candidate.objective,
                            targetEntityId = candidate.targetEntityId,
                            targetEntityType = candidate.targetEntityType,
                            rationale = candidate.rationale,
                            suggestedAction = candidate.suggestedAction,
                            status = InterventionRecommendationStatus.PENDING,
                            createdAt = currentTimeMs
                        )

                        val saveResult = recommendationRepository.saveRecommendation(recommendation)
                        when (saveResult) {
                            is Result.Success -> Result.Success(InterventionRecommendationResult.Recommended(recommendation))
                            is Result.Failure -> Result.Failure(saveResult.error)
                        }
                    }
                    // All suppression cases result in NoRecommendation
                    is SuppressionResult.SuppressedAntiStacking,
                    is SuppressionResult.SuppressedRecentRejection,
                    is SuppressionResult.SuppressedRecentIgnored,
                    is SuppressionResult.SuppressedTargetCompleted,
                    is SuppressionResult.UnresolvedPrecedence -> {
                        Result.Success(InterventionRecommendationResult.NoRecommendation)
                    }
                }
            }
            is Result.Failure -> Result.Failure(aiResult.error)
        }
    }

    private fun formatContextForAi(context: RecommendationContext): String {
        return """
            Context Summary:
            Active Goals: ${context.activeGoals.size}
            Active Commitments: ${context.activeCommitments.size}
            Recent Observations: ${context.recentObservations.size}
            Active Patterns: ${context.activePatterns.size}
            Active Barriers: ${context.activeBarriers.size}
            Recent Reflections: ${context.recentReflections.size}
            Recent Corrections: ${context.recentCorrections.size}
            
            ${context.activeGoals.joinToString("\n") { "GOAL [${it.id}]: ${it.title} (${it.status})" }}
            ${context.activeCommitments.joinToString("\n") { "COMMITMENT [${it.id}]: ${it.title} (${it.status})" }}
            ${context.recentObservations.joinToString("\n") { "OBSERVATION [${it.id}]: ${it.type} - ${it.value}" }}
            ${context.activePatterns.joinToString("\n") { "PATTERN [${it.id}]: ${it.type} - ${it.description}" }}
            ${context.activeBarriers.joinToString("\n") { "BARRIER [${it.id}]: ${it.category} - ${it.description}" }}
            ${context.recentReflections.joinToString("\n") { "REFLECTION [${it.id}]: ${it.content}" }}
            ${context.recentCorrections.joinToString("\n") { "CORRECTION [${it.eventId}]: User explicitly corrected recommendation '${it.recommendation}' (Target: ${it.targetEntityId ?: "None"}, Type: ${it.interventionType}). Corrected Text: ${it.correctedText ?: "None provided"}" }}
        """.trimIndent()
    }
}
