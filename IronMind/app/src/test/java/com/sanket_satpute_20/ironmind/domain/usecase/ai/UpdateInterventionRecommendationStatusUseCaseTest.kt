package com.sanket_satpute_20.ironmind.domain.usecase.ai

import com.sanket_satpute_20.ironmind.domain.ai.InterventionType
import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.intervention.InterventionRecommendation
import com.sanket_satpute_20.ironmind.domain.model.intervention.InterventionRecommendationStatus
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateInterventionRecommendationStatusUseCaseTest {

    private val repository = FakeInterventionRecommendationRepository()
    private val useCase = UpdateInterventionRecommendationStatusUseCase(repository)

    @Test
    fun `updateStatus persists exactly the requested status`() = runTest {
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
        repository.saveRecommendation(rec)

        val result = useCase("rec-1", InterventionRecommendationStatus.ACCEPTED)
        assertTrue(result is Result.Success)

        val saved = repository.getRecommendation("rec-1")
        assertEquals(InterventionRecommendationStatus.ACCEPTED, (saved as Result.Success).data?.status)
    }
}
