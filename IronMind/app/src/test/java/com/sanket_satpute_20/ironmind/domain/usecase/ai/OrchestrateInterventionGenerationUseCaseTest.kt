package com.sanket_satpute_20.ironmind.domain.usecase.ai

import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.intervention.InterventionRecommendationResult
import com.sanket_satpute_20.ironmind.domain.model.intervention.RecommendationContext
import com.sanket_satpute_20.ironmind.domain.usecase.intervention.AssembleRecommendationContextUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class OrchestrateInterventionGenerationUseCaseTest {

    private lateinit var assembleContextUseCase: AssembleRecommendationContextUseCase
    private lateinit var recommendInterventionUseCase: RecommendInterventionUseCase
    private lateinit var orchestrateUseCase: OrchestrateInterventionGenerationUseCase

    @Before
    fun setup() {
        assembleContextUseCase = mockk()
        recommendInterventionUseCase = mockk()
        orchestrateUseCase = OrchestrateInterventionGenerationUseCase(
            assembleContextUseCase,
            recommendInterventionUseCase
        )
    }

    @Test
    fun `invoke should propagate context assembly failure`() = runBlocking {
        val exception = Exception("Context error")
        coEvery { assembleContextUseCase("user1") } returns Result.Failure(exception)

        val result = orchestrateUseCase("user1")

        assertTrue(result is Result.Failure)
        assertEquals(exception, (result as Result.Failure).error)
        coVerify(exactly = 0) { recommendInterventionUseCase(any()) }
    }

    @Test
    fun `invoke should pass successfully assembled context to recommender and return its result`() = runBlocking {
        val mockContext = mockk<RecommendationContext>()
        val mockRecommendationResult = InterventionRecommendationResult.NoRecommendation

        coEvery { assembleContextUseCase("user1") } returns Result.Success(mockContext)
        coEvery { recommendInterventionUseCase(mockContext) } returns Result.Success(mockRecommendationResult)

        val result = orchestrateUseCase("user1")

        assertTrue(result is Result.Success)
        assertEquals(mockRecommendationResult, (result as Result.Success).data)
        coVerify(exactly = 1) { recommendInterventionUseCase(mockContext) }
    }

    @Test
    fun `invoke should propagate recommender engine failure`() = runBlocking {
        val mockContext = mockk<RecommendationContext>()
        val exception = Exception("AI failure")

        coEvery { assembleContextUseCase("user1") } returns Result.Success(mockContext)
        coEvery { recommendInterventionUseCase(mockContext) } returns Result.Failure(exception)

        val result = orchestrateUseCase("user1")

        assertTrue(result is Result.Failure)
        assertEquals(exception, (result as Result.Failure).error)
    }
}
