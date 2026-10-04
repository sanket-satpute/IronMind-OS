package com.sanket_satpute_20.ironmind.domain.usecase.ai

import com.sanket_satpute_20.ironmind.domain.ai.InterventionType
import com.sanket_satpute_20.ironmind.domain.model.intervention.InterventionRecommendation
import com.sanket_satpute_20.ironmind.domain.model.intervention.InterventionRecommendationStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class GetPendingInterventionRecommendationsUseCaseTest {

    private val repository = FakeInterventionRecommendationRepository()
    private val useCase = GetPendingInterventionRecommendationsUseCase(repository)

    @Test
    fun `getPending returns correct items filtered by time`() = runTest {
        val rec1 = InterventionRecommendation(
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
        val rec2 = rec1.copy(id = "rec-2", expiresAt = 2000L) // expired
        val rec3 = rec1.copy(id = "rec-3", expiresAt = 4000L) // valid

        repository.saveRecommendation(rec1)
        repository.saveRecommendation(rec2)
        repository.saveRecommendation(rec3)

        val pending = useCase("user-1", 3000L).first()
        assertEquals(2, pending.size)
        assertEquals(setOf("rec-1", "rec-3"), pending.map { it.id }.toSet())
    }
}
