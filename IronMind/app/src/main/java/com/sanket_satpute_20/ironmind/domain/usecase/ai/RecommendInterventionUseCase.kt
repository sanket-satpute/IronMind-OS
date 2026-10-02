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
import com.sanket_satpute_20.ironmind.domain.model.intervention.InterventionRecommendation
import com.sanket_satpute_20.ironmind.domain.model.intervention.InterventionRecommendationResult
import com.sanket_satpute_20.ironmind.domain.model.intervention.InterventionRecommendationStatus
import com.sanket_satpute_20.ironmind.domain.model.intervention.RecommendationContext
import com.sanket_satpute_20.ironmind.domain.model.intervention.toCandidate

/**
 * Sprint V2.10: Intervention Recommendation Engine (Sprint 3)
 *
 * Uses the AI reasoning layer to generate a typed intervention candidate
 * from the assembled RecommendationContext, and deterministically resolves it
 * into an InterventionRecommendationResult.
 */
class RecommendInterventionUseCase(
    private val ironMindAI: IronMindAI,
    private val clock: Clock,
    private val idGenerator: IdGenerator
) {
    suspend operator fun invoke(
        context: RecommendationContext
    ): Result<InterventionRecommendationResult, Exception> {
        val request = AIRequest(
            userId = context.userId,
            input = formatContextForAi(context),
            requestType = AIRequestType.INTERVENTION_SUGGESTION
        )

        val aiResult = ironMindAI.process(request)

        return when (aiResult) {
            is Result.Success -> {
                val output = aiResult.data

                if (output is AIOutput.NoAction) {
                    return Result.Success(InterventionRecommendationResult.NoRecommendation)
                }

                if (output !is AIOutput.InterventionRecommendation) {
                    return Result.Failure(Exception("AI returned unexpected output type: ${output.javaClass.simpleName}"))
                }

                if (!output.isValid()) {
                    return Result.Success(InterventionRecommendationResult.NoRecommendation)
                }

                val candidate = output.toCandidate()

                if (candidate.interventionType == InterventionType.STAY_SILENT) {
                    return Result.Success(InterventionRecommendationResult.NoRecommendation)
                }

                if (candidate.suggestedAction.isBlank() || candidate.rationale.isBlank()) {
                    return Result.Success(InterventionRecommendationResult.NoRecommendation)
                }

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
                        else -> false // Tasks and other types fall through here
                    }
                    if (!isValid) {
                        return Result.Success(InterventionRecommendationResult.NoRecommendation)
                    }
                }

                val recommendation = InterventionRecommendation(
                    id = idGenerator.generateId(),
                    userId = context.userId,
                    interventionType = candidate.interventionType,
                    targetEntityId = candidate.targetEntityId,
                    targetEntityType = candidate.targetEntityType,
                    rationale = candidate.rationale,
                    suggestedAction = candidate.suggestedAction,
                    status = InterventionRecommendationStatus.PENDING,
                    createdAt = clock.currentTimeMillis()
                )

                Result.Success(InterventionRecommendationResult.Recommended(recommendation))
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
            
            ${context.activeGoals.joinToString("\n") { "GOAL [${it.id}]: ${it.title} (${it.status})" }}
            ${context.activeCommitments.joinToString("\n") { "COMMITMENT [${it.id}]: ${it.title} (${it.status})" }}
            ${context.recentObservations.joinToString("\n") { "OBSERVATION [${it.id}]: ${it.type} - ${it.value}" }}
            ${context.activePatterns.joinToString("\n") { "PATTERN [${it.id}]: ${it.type} - ${it.description}" }}
            ${context.activeBarriers.joinToString("\n") { "BARRIER [${it.id}]: ${it.category} - ${it.description}" }}
            ${context.recentReflections.joinToString("\n") { "REFLECTION [${it.id}]: ${it.content}" }}
        """.trimIndent()
    }
}
