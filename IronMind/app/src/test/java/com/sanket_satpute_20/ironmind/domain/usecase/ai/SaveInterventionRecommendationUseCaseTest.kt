package com.sanket_satpute_20.ironmind.domain.usecase.ai

import com.sanket_satpute_20.ironmind.domain.ai.InterventionType
import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.intervention.InterventionRecommendation
import com.sanket_satpute_20.ironmind.domain.model.intervention.InterventionRecommendationStatus
import com.sanket_satpute_20.ironmind.domain.repository.InterventionRecommendationRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FakeInterventionRecommendationRepository : InterventionRecommendationRepository {
    private val recommendations = mutableMapOf<String, InterventionRecommendation>()
    var shouldFail = false

    override suspend fun saveRecommendation(recommendation: InterventionRecommendation): Result<Unit, Exception> {
        if (shouldFail) return Result.Failure(Exception("DB error"))
        recommendations[recommendation.id] = recommendation
        return Result.Success(Unit)
    }

    override suspend fun getRecommendation(id: String): Result<InterventionRecommendation?, Exception> {
        if (shouldFail) return Result.Failure(Exception("DB error"))
        return Result.Success(recommendations[id])
    }

    override fun getPendingRecommendations(userId: String, currentTime: Long): Flow<List<InterventionRecommendation>> {
        val pending = recommendations.values.filter {
            it.userId == userId && 
            it.status == InterventionRecommendationStatus.PENDING && 
            (it.expiresAt == null || it.expiresAt!! > currentTime)
        }
        return flowOf(pending)
    }

    override suspend fun updateRecommendationStatus(
        id: String,
        status: InterventionRecommendationStatus
    ): Result<Unit, Exception> {
        if (shouldFail) return Result.Failure(Exception("DB error"))
        val rec = recommendations[id] ?: return Result.Failure(Exception("Not found"))
        recommendations[id] = rec.copy(status = status)
        return Result.Success(Unit)
    }
}

class SaveInterventionRecommendationUseCaseTest {

    private val repository = FakeInterventionRecommendationRepository()
    private val useCase = SaveInterventionRecommendationUseCase(repository)

    @Test
    fun `save recommendation persists exactly the provided domain model`() = runTest {
        val rec = InterventionRecommendation(
            id = "rec-1",
            userId = "user-1",
            interventionType = InterventionType.BREAK_DOWN,
            objective = com.sanket_satpute_20.ironmind.domain.ai.InterventionObjective.INITIATE_ACTION,
            targetEntityId = "goal-1",
            targetEntityType = "GOAL",
            rationale = "Rationale",
            suggestedAction = "Action",
            status = InterventionRecommendationStatus.PENDING,
            createdAt = 1000L,
            expiresAt = null
        )

        val result = useCase(rec)
        assertTrue(result is Result.Success)

        val saved = repository.getRecommendation("rec-1")
        assertTrue(saved is Result.Success)
        assertEquals(rec, (saved as Result.Success).data)
    }
}
