package com.sanket_satpute_20.ironmind.domain.repository

import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.intervention.InterventionRecommendation
import com.sanket_satpute_20.ironmind.domain.model.intervention.InterventionRecommendationStatus
import kotlinx.coroutines.flow.Flow

interface InterventionRecommendationRepository {
    suspend fun saveRecommendation(recommendation: InterventionRecommendation): Result<Unit, Exception>
    suspend fun getRecommendation(id: String): Result<InterventionRecommendation?, Exception>
    fun getPendingRecommendations(userId: String, currentTime: Long): Flow<List<InterventionRecommendation>>
    suspend fun updateRecommendationStatus(id: String, status: InterventionRecommendationStatus): Result<Unit, Exception>
}
