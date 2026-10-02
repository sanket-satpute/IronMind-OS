package com.sanket_satpute_20.ironmind.data.repository

import com.sanket_satpute_20.ironmind.data.local.dao.InterventionRecommendationDao
import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.intervention.InterventionRecommendation
import com.sanket_satpute_20.ironmind.domain.model.intervention.InterventionRecommendationStatus
import com.sanket_satpute_20.ironmind.domain.repository.InterventionRecommendationRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class InterventionRecommendationRepositoryImpl(
    private val dao: InterventionRecommendationDao
) : InterventionRecommendationRepository {

    override suspend fun saveRecommendation(recommendation: InterventionRecommendation): Result<Unit, Exception> {
        return try {
            withContext(Dispatchers.IO) {
                dao.insert(recommendation.toEntity())
            }
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }

    override suspend fun getRecommendation(id: String): Result<InterventionRecommendation?, Exception> {
        return try {
            val entity = withContext(Dispatchers.IO) {
                dao.getById(id)
            }
            Result.Success(entity?.toDomain())
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }

    override fun getPendingRecommendations(userId: String, currentTime: Long): Flow<List<InterventionRecommendation>> {
        return dao.getPendingRecommendations(userId, currentTime).map { list ->
            list.map { it.toDomain() }
        }
    }

    override suspend fun updateRecommendationStatus(id: String, status: InterventionRecommendationStatus): Result<Unit, Exception> {
        return try {
            withContext(Dispatchers.IO) {
                dao.updateStatus(id, status.name)
            }
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }
}
