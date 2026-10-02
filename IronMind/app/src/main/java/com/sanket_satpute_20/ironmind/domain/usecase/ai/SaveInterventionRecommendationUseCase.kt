package com.sanket_satpute_20.ironmind.domain.usecase.ai

import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.intervention.InterventionRecommendation
import com.sanket_satpute_20.ironmind.domain.repository.InterventionRecommendationRepository

class SaveInterventionRecommendationUseCase(
    private val repository: InterventionRecommendationRepository
) {
    suspend operator fun invoke(recommendation: InterventionRecommendation): Result<Unit, Exception> {
        return repository.saveRecommendation(recommendation)
    }
}
