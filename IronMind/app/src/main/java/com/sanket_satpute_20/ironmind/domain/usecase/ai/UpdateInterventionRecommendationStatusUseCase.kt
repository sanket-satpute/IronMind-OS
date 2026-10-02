package com.sanket_satpute_20.ironmind.domain.usecase.ai

import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.intervention.InterventionRecommendationStatus
import com.sanket_satpute_20.ironmind.domain.repository.InterventionRecommendationRepository

class UpdateInterventionRecommendationStatusUseCase(
    private val repository: InterventionRecommendationRepository
) {
    suspend operator fun invoke(id: String, status: InterventionRecommendationStatus): Result<Unit, Exception> {
        return repository.updateRecommendationStatus(id, status)
    }
}
