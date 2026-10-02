package com.sanket_satpute_20.ironmind.domain.usecase.ai

import com.sanket_satpute_20.ironmind.domain.model.intervention.InterventionRecommendation
import com.sanket_satpute_20.ironmind.domain.repository.InterventionRecommendationRepository
import kotlinx.coroutines.flow.Flow

class GetPendingInterventionRecommendationsUseCase(
    private val repository: InterventionRecommendationRepository
) {
    operator fun invoke(userId: String, currentTime: Long): Flow<List<InterventionRecommendation>> {
        return repository.getPendingRecommendations(userId, currentTime)
    }
}
