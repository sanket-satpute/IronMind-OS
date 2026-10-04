package com.sanket_satpute_20.ironmind.domain.usecase.ai

import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.intervention.InterventionRecommendationResult
import com.sanket_satpute_20.ironmind.domain.usecase.intervention.AssembleRecommendationContextUseCase

/**
 * Sprint V2.13: Domain orchestration boundary for intervention recommendation.
 * Wires the context assembly to the AI recommendation generation without adding redundant policy.
 */
class OrchestrateInterventionGenerationUseCase(
    private val assembleContextUseCase: AssembleRecommendationContextUseCase,
    private val recommendInterventionUseCase: RecommendInterventionUseCase
) {
    suspend operator fun invoke(userId: String): Result<InterventionRecommendationResult, Exception> {
        return when (val contextResult = assembleContextUseCase(userId)) {
            is Result.Failure -> Result.Failure(contextResult.error)
            is Result.Success -> recommendInterventionUseCase(contextResult.data)
        }
    }
}
