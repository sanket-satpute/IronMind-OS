package com.sanket_satpute_20.ironmind.domain.usecase.ai

import com.sanket_satpute_20.ironmind.domain.ai.AIOutput
import com.sanket_satpute_20.ironmind.domain.ai.AIRequest
import com.sanket_satpute_20.ironmind.domain.ai.InterventionObjective
import com.sanket_satpute_20.ironmind.domain.ai.InterventionType
import com.sanket_satpute_20.ironmind.domain.ai.IronMindAI
import com.sanket_satpute_20.ironmind.domain.common.Clock
import com.sanket_satpute_20.ironmind.domain.common.IdGenerator
import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.AutonomyCapability
import com.sanket_satpute_20.ironmind.domain.model.AutonomyLevel
import com.sanket_satpute_20.ironmind.domain.model.AutonomySettings
import com.sanket_satpute_20.ironmind.domain.model.intervention.InterventionEquivalencePolicy
import com.sanket_satpute_20.ironmind.domain.model.intervention.InterventionRecommendation
import com.sanket_satpute_20.ironmind.domain.model.intervention.InterventionRecommendationResult
import com.sanket_satpute_20.ironmind.domain.model.intervention.InterventionRecommendationStatus
import com.sanket_satpute_20.ironmind.domain.model.intervention.RecommendationContext
import com.sanket_satpute_20.ironmind.domain.model.intervention.ContextGoal
import com.sanket_satpute_20.ironmind.domain.model.intervention.TargetCompletionResolver
import com.sanket_satpute_20.ironmind.domain.model.intervention.TargetCompletionResult
import com.sanket_satpute_20.ironmind.domain.model.GoalStatus
import com.sanket_satpute_20.ironmind.domain.repository.AutonomySettingsRepository
import com.sanket_satpute_20.ironmind.domain.repository.InterventionRecommendationRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class RecommendInterventionUseCaseTest {

    private lateinit var ironMindAI: IronMindAI
    private lateinit var clock: Clock
    private lateinit var idGenerator: IdGenerator
    private lateinit var autonomySettingsRepository: AutonomySettingsRepository
    private lateinit var recommendationRepository: InterventionRecommendationRepository
    private lateinit var targetCompletionResolver: TargetCompletionResolver
    private lateinit var equivalencePolicy: InterventionEquivalencePolicy
    private lateinit var useCase: RecommendInterventionUseCase

    private val userId = "user-1"
    private val currentTime = 100_000L
    private val generatedId = "rec-1"

    private fun defaultContext(
        goals: List<ContextGoal> = listOf(
            ContextGoal(id = "goal-1", title = "Test Goal", description = "desc", status = GoalStatus.ACTIVE, targetAt = null)
        )
    ) = RecommendationContext(
        userId = userId,
        activeGoals = goals,
        activeCommitments = emptyList(),
        recentObservations = emptyList(),
        activePatterns = emptyList(),
        activeBarriers = emptyList(),
        recentReflections = emptyList()
    )

    private fun validAIOutput(
        interventionType: InterventionType = InterventionType.BREAK_DOWN,
        objective: InterventionObjective? = InterventionObjective.INITIATE_ACTION,
        targetEntityType: String? = "GOAL",
        targetEntityId: String? = "goal-1",
        confidence: Float = 0.8f
    ) = AIOutput.InterventionRecommendation(
        interventionType = interventionType,
        objective = objective,
        targetEntityType = targetEntityType,
        targetEntityId = targetEntityId,
        reason = "Test rationale",
        recommendation = "Test action",
        confidence = confidence
    )

    private fun historicalRecommendation(
        status: InterventionRecommendationStatus,
        createdAt: Long,
        objective: InterventionObjective = InterventionObjective.INITIATE_ACTION,
        targetEntityType: String? = "GOAL",
        targetEntityId: String? = "goal-1"
    ) = InterventionRecommendation(
        id = "hist-${status.name}-$createdAt",
        userId = userId,
        interventionType = InterventionType.BREAK_DOWN,
        objective = objective,
        targetEntityId = targetEntityId,
        targetEntityType = targetEntityType,
        rationale = "Historical",
        suggestedAction = "Historical action",
        status = status,
        createdAt = createdAt
    )

    private fun setupAutonomy(level: AutonomyLevel) {
        val settings = AutonomySettings(
            userId = userId,
            levels = mapOf(AutonomyCapability.INTERVENTION_GENERATION to level)
        )
        coEvery { autonomySettingsRepository.getSettings(userId) } returns Result.Success(settings)
    }

    private fun setupAI(output: AIOutput) {
        coEvery { ironMindAI.process(any()) } returns Result.Success(output)
    }

    private fun setupHistory(vararg history: InterventionRecommendation) {
        coEvery { 
            recommendationRepository.getEquivalentRecommendations(
                userId = userId, 
                objective = any(), 
                targetEntityType = any(), 
                targetEntityId = any()
            ) 
        } returns Result.Success(history.toList())
    }

    private fun setupTargetCompletion(result: TargetCompletionResult) {
        coEvery { targetCompletionResolver.resolve(any(), any(), any()) } returns result
    }

    private fun setupSaveSuccess() {
        coEvery { recommendationRepository.saveRecommendation(any()) } returns Result.Success(Unit)
    }

    @Before
    fun setup() {
        ironMindAI = mockk()
        clock = mockk()
        idGenerator = mockk()
        autonomySettingsRepository = mockk()
        recommendationRepository = mockk()
        targetCompletionResolver = mockk()
        equivalencePolicy = InterventionEquivalencePolicy() // real policy, not mocked

        every { clock.currentTimeMillis() } returns currentTime
        every { idGenerator.generateId() } returns generatedId

        useCase = RecommendInterventionUseCase(
            ironMindAI = ironMindAI,
            clock = clock,
            idGenerator = idGenerator,
            autonomySettingsRepository = autonomySettingsRepository,
            recommendationRepository = recommendationRepository,
            targetCompletionResolver = targetCompletionResolver,
            equivalencePolicy = equivalencePolicy
        )
    }

    // ============================================================
    // 1. Autonomy OFF -> NoRecommendation
    // ============================================================
    @Test
    fun `autonomy OFF returns NoRecommendation`() = runBlocking {
        setupAutonomy(AutonomyLevel.OFF)

        val result = useCase(defaultContext())

        assertTrue(result is Result.Success)
        assertEquals(InterventionRecommendationResult.NoRecommendation, (result as Result.Success).data)
    }

    // ============================================================
    // 2. Valid AI recommendation + no suppression -> Recommended
    // ============================================================
    @Test
    fun `valid recommendation with no history produces Recommended`() = runBlocking {
        setupAutonomy(AutonomyLevel.SUGGEST_ONLY)
        setupAI(validAIOutput())
        setupTargetCompletion(TargetCompletionResult.NOT_COMPLETED)
        setupHistory() // empty
        setupSaveSuccess()

        val result = useCase(defaultContext())

        assertTrue(result is Result.Success)
        val data = (result as Result.Success).data
        assertTrue(data is InterventionRecommendationResult.Recommended)
        val rec = (data as InterventionRecommendationResult.Recommended).recommendation
        assertEquals(generatedId, rec.id)
        assertEquals(userId, rec.userId)
        assertEquals(InterventionRecommendationStatus.PENDING, rec.status)
    }

    // ============================================================
    // 3. Valid AI + completed target -> NoRecommendation
    // ============================================================
    @Test
    fun `completed target returns NoRecommendation`() = runBlocking {
        setupAutonomy(AutonomyLevel.SUGGEST_ONLY)
        setupAI(validAIOutput())
        setupTargetCompletion(TargetCompletionResult.COMPLETED)
        setupHistory()
        setupSaveSuccess()

        val result = useCase(defaultContext())

        assertTrue(result is Result.Success)
        assertEquals(InterventionRecommendationResult.NoRecommendation, (result as Result.Success).data)
    }

    // ============================================================
    // 4. Valid AI + equivalent PENDING -> NoRecommendation
    // ============================================================
    @Test
    fun `equivalent PENDING suppresses recommendation`() = runBlocking {
        setupAutonomy(AutonomyLevel.SUGGEST_ONLY)
        setupAI(validAIOutput())
        setupTargetCompletion(TargetCompletionResult.NOT_COMPLETED)
        setupHistory(
            historicalRecommendation(InterventionRecommendationStatus.PENDING, createdAt = 50_000L)
        )

        val result = useCase(defaultContext())

        assertTrue(result is Result.Success)
        assertEquals(InterventionRecommendationResult.NoRecommendation, (result as Result.Success).data)
    }

    // ============================================================
    // 5. Equivalent REJECTED within 24h -> NoRecommendation
    // ============================================================
    @Test
    fun `equivalent REJECTED within 24h suppresses recommendation`() = runBlocking {
        val rejectedAt = currentTime - (12L * 60 * 60 * 1000) // 12h ago
        setupAutonomy(AutonomyLevel.SUGGEST_ONLY)
        setupAI(validAIOutput())
        setupTargetCompletion(TargetCompletionResult.NOT_COMPLETED)
        setupHistory(
            historicalRecommendation(InterventionRecommendationStatus.REJECTED, createdAt = rejectedAt)
        )

        val result = useCase(defaultContext())

        assertTrue(result is Result.Success)
        assertEquals(InterventionRecommendationResult.NoRecommendation, (result as Result.Success).data)
    }

    // ============================================================
    // 6. Equivalent REJECTED outside 24h -> Recommended
    // ============================================================
    @Test
    fun `equivalent REJECTED outside 24h allows recommendation`() = runBlocking {
        val rejectedAt = currentTime - (25L * 60 * 60 * 1000) // 25h ago
        setupAutonomy(AutonomyLevel.SUGGEST_ONLY)
        setupAI(validAIOutput())
        setupTargetCompletion(TargetCompletionResult.NOT_COMPLETED)
        setupHistory(
            historicalRecommendation(InterventionRecommendationStatus.REJECTED, createdAt = rejectedAt)
        )
        setupSaveSuccess()

        val result = useCase(defaultContext())

        assertTrue(result is Result.Success)
        assertTrue((result as Result.Success).data is InterventionRecommendationResult.Recommended)
    }

    // ============================================================
    // 7. Equivalent IGNORED within 6h -> NoRecommendation
    // ============================================================
    @Test
    fun `equivalent IGNORED within 6h suppresses recommendation`() = runBlocking {
        val ignoredAt = currentTime - (3L * 60 * 60 * 1000) // 3h ago
        setupAutonomy(AutonomyLevel.SUGGEST_ONLY)
        setupAI(validAIOutput())
        setupTargetCompletion(TargetCompletionResult.NOT_COMPLETED)
        setupHistory(
            historicalRecommendation(InterventionRecommendationStatus.IGNORED, createdAt = ignoredAt)
        )

        val result = useCase(defaultContext())

        assertTrue(result is Result.Success)
        assertEquals(InterventionRecommendationResult.NoRecommendation, (result as Result.Success).data)
    }

    // ============================================================
    // 8. Equivalent IGNORED outside 6h -> Recommended
    // ============================================================
    @Test
    fun `equivalent IGNORED outside 6h allows recommendation`() = runBlocking {
        val ignoredAt = currentTime - (7L * 60 * 60 * 1000) // 7h ago
        setupAutonomy(AutonomyLevel.SUGGEST_ONLY)
        setupAI(validAIOutput())
        setupTargetCompletion(TargetCompletionResult.NOT_COMPLETED)
        setupHistory(
            historicalRecommendation(InterventionRecommendationStatus.IGNORED, createdAt = ignoredAt)
        )
        setupSaveSuccess()

        val result = useCase(defaultContext())

        assertTrue(result is Result.Success)
        assertTrue((result as Result.Success).data is InterventionRecommendationResult.Recommended)
    }

    // ============================================================
    // 9. Equivalent ACCEPTED -> Recommended
    // ============================================================
    @Test
    fun `equivalent ACCEPTED allows recommendation`() = runBlocking {
        setupAutonomy(AutonomyLevel.SUGGEST_ONLY)
        setupAI(validAIOutput())
        setupTargetCompletion(TargetCompletionResult.NOT_COMPLETED)
        setupHistory(
            historicalRecommendation(InterventionRecommendationStatus.ACCEPTED, createdAt = 50_000L)
        )
        setupSaveSuccess()

        val result = useCase(defaultContext())

        assertTrue(result is Result.Success)
        assertTrue((result as Result.Success).data is InterventionRecommendationResult.Recommended)
    }

    // ============================================================
    // 10. Older REJECTED + newer ACCEPTED -> Recommended
    // ============================================================
    @Test
    fun `older REJECTED then newer ACCEPTED allows recommendation`() = runBlocking {
        setupAutonomy(AutonomyLevel.SUGGEST_ONLY)
        setupAI(validAIOutput())
        setupTargetCompletion(TargetCompletionResult.NOT_COMPLETED)
        setupHistory(
            historicalRecommendation(InterventionRecommendationStatus.REJECTED, createdAt = currentTime - 1000L),
            historicalRecommendation(InterventionRecommendationStatus.ACCEPTED, createdAt = currentTime - 500L)
        )
        setupSaveSuccess()

        val result = useCase(defaultContext())

        assertTrue(result is Result.Success)
        assertTrue((result as Result.Success).data is InterventionRecommendationResult.Recommended)
    }

    // ============================================================
    // 11. Older ACCEPTED + newer REJECTED -> NoRecommendation
    // ============================================================
    @Test
    fun `older ACCEPTED then newer REJECTED suppresses recommendation`() = runBlocking {
        setupAutonomy(AutonomyLevel.SUGGEST_ONLY)
        setupAI(validAIOutput())
        setupTargetCompletion(TargetCompletionResult.NOT_COMPLETED)
        setupHistory(
            historicalRecommendation(InterventionRecommendationStatus.ACCEPTED, createdAt = currentTime - 1000L),
            historicalRecommendation(InterventionRecommendationStatus.REJECTED, createdAt = currentTime - 500L)
        )

        val result = useCase(defaultContext())

        assertTrue(result is Result.Success)
        assertEquals(InterventionRecommendationResult.NoRecommendation, (result as Result.Success).data)
    }

    // ============================================================
    // 12. Older REJECTED + newer IGNORED -> NoRecommendation (within 6h)
    // ============================================================
    @Test
    fun `older REJECTED then newer IGNORED within 6h suppresses`() = runBlocking {
        setupAutonomy(AutonomyLevel.SUGGEST_ONLY)
        setupAI(validAIOutput())
        setupTargetCompletion(TargetCompletionResult.NOT_COMPLETED)
        setupHistory(
            historicalRecommendation(InterventionRecommendationStatus.REJECTED, createdAt = currentTime - 2000L),
            historicalRecommendation(InterventionRecommendationStatus.IGNORED, createdAt = currentTime - 1000L)
        )

        val result = useCase(defaultContext())

        assertTrue(result is Result.Success)
        assertEquals(InterventionRecommendationResult.NoRecommendation, (result as Result.Success).data)
    }

    // ============================================================
    // 13. Equal createdAt with conflicting statuses -> NoRecommendation
    // ============================================================
    @Test
    fun `equal createdAt with conflicting statuses returns NoRecommendation`() = runBlocking {
        setupAutonomy(AutonomyLevel.SUGGEST_ONLY)
        setupAI(validAIOutput())
        setupTargetCompletion(TargetCompletionResult.NOT_COMPLETED)
        setupHistory(
            historicalRecommendation(InterventionRecommendationStatus.ACCEPTED, createdAt = 50_000L),
            historicalRecommendation(InterventionRecommendationStatus.REJECTED, createdAt = 50_000L)
        )

        val result = useCase(defaultContext())

        assertTrue(result is Result.Success)
        assertEquals(InterventionRecommendationResult.NoRecommendation, (result as Result.Success).data)
    }

    // ============================================================
    // 14. Non-equivalent objective -> Recommended
    // ============================================================
    @Test
    fun `non-equivalent objective does not suppress`() = runBlocking {
        setupAutonomy(AutonomyLevel.SUGGEST_ONLY)
        setupAI(validAIOutput())
        setupTargetCompletion(TargetCompletionResult.NOT_COMPLETED)
        setupHistory(
            historicalRecommendation(
                InterventionRecommendationStatus.REJECTED,
                createdAt = currentTime - 1000L,
                objective = InterventionObjective.REDUCE_FRICTION // different objective
            )
        )
        setupSaveSuccess()

        val result = useCase(defaultContext())

        assertTrue(result is Result.Success)
        assertTrue((result as Result.Success).data is InterventionRecommendationResult.Recommended)
    }

    // ============================================================
    // 15. Same objective but different target ID -> Recommended
    // ============================================================
    @Test
    fun `same objective different target ID does not suppress`() = runBlocking {
        setupAutonomy(AutonomyLevel.SUGGEST_ONLY)
        setupAI(validAIOutput())
        setupTargetCompletion(TargetCompletionResult.NOT_COMPLETED)
        setupHistory(
            historicalRecommendation(
                InterventionRecommendationStatus.REJECTED,
                createdAt = currentTime - 1000L,
                targetEntityId = "goal-999" // different target
            )
        )
        setupSaveSuccess()

        val result = useCase(defaultContext())

        assertTrue(result is Result.Success)
        assertTrue((result as Result.Success).data is InterventionRecommendationResult.Recommended)
    }

    // ============================================================
    // 16. Same objective + target type but different target ID -> Recommended
    // ============================================================
    @Test
    fun `same objective and target type but different target ID does not suppress`() = runBlocking {
        setupAutonomy(AutonomyLevel.SUGGEST_ONLY)
        setupAI(validAIOutput())
        setupTargetCompletion(TargetCompletionResult.NOT_COMPLETED)
        setupHistory(
            historicalRecommendation(
                InterventionRecommendationStatus.PENDING,
                createdAt = currentTime - 1000L,
                targetEntityType = "GOAL",
                targetEntityId = "goal-other"
            )
        )
        setupSaveSuccess()

        val result = useCase(defaultContext())

        assertTrue(result is Result.Success)
        assertTrue((result as Result.Success).data is InterventionRecommendationResult.Recommended)
    }

    // ============================================================
    // 18. STAY_SILENT -> NoRecommendation
    // ============================================================
    @Test
    fun `STAY_SILENT returns NoRecommendation`() = runBlocking {
        setupAutonomy(AutonomyLevel.SUGGEST_ONLY)
        setupAI(validAIOutput(interventionType = InterventionType.STAY_SILENT))

        val result = useCase(defaultContext())

        assertTrue(result is Result.Success)
        assertEquals(InterventionRecommendationResult.NoRecommendation, (result as Result.Success).data)
    }

    // ============================================================
    // 19. AI NoAction -> NoRecommendation
    // ============================================================
    @Test
    fun `AI NoAction returns NoRecommendation`() = runBlocking {
        setupAutonomy(AutonomyLevel.SUGGEST_ONLY)
        coEvery { ironMindAI.process(any()) } returns Result.Success(AIOutput.NoAction())

        val result = useCase(defaultContext())

        assertTrue(result is Result.Success)
        assertEquals(InterventionRecommendationResult.NoRecommendation, (result as Result.Success).data)
    }

    // ============================================================
    // 20. Low confidence but structurally valid -> NOT rejected by confidence
    // ============================================================
    @Test
    fun `low confidence does not reject structurally valid recommendation`() = runBlocking {
        setupAutonomy(AutonomyLevel.SUGGEST_ONLY)
        setupAI(validAIOutput(confidence = 0.1f))
        setupTargetCompletion(TargetCompletionResult.NOT_COMPLETED)
        setupHistory()
        setupSaveSuccess()

        val result = useCase(defaultContext())

        assertTrue(result is Result.Success)
        assertTrue((result as Result.Success).data is InterventionRecommendationResult.Recommended)
    }

    // ============================================================
    // 21. Valid recommendation is persisted exactly once
    // ============================================================
    @Test
    fun `valid recommendation is persisted exactly once`() = runBlocking {
        setupAutonomy(AutonomyLevel.SUGGEST_ONLY)
        setupAI(validAIOutput())
        setupTargetCompletion(TargetCompletionResult.NOT_COMPLETED)
        setupHistory()
        setupSaveSuccess()

        useCase(defaultContext())

        coVerify(exactly = 1) { recommendationRepository.saveRecommendation(any()) }
    }

    // ============================================================
    // 22. Suppressed recommendation is NOT persisted
    // ============================================================
    @Test
    fun `suppressed recommendation is not persisted`() = runBlocking {
        setupAutonomy(AutonomyLevel.SUGGEST_ONLY)
        setupAI(validAIOutput())
        setupTargetCompletion(TargetCompletionResult.NOT_COMPLETED)
        setupHistory(
            historicalRecommendation(InterventionRecommendationStatus.PENDING, createdAt = 50_000L)
        )

        useCase(defaultContext())

        coVerify(exactly = 0) { recommendationRepository.saveRecommendation(any()) }
    }

    // ============================================================
    // 23. Completed target does NOT result in persistence
    // ============================================================
    @Test
    fun `completed target does not persist recommendation`() = runBlocking {
        setupAutonomy(AutonomyLevel.SUGGEST_ONLY)
        setupAI(validAIOutput())
        setupTargetCompletion(TargetCompletionResult.COMPLETED)
        setupHistory()

        useCase(defaultContext())

        coVerify(exactly = 0) { recommendationRepository.saveRecommendation(any()) }
    }

    // ============================================================
    // 24. AI provider failure preserves failure semantics
    // ============================================================
    @Test
    fun `AI provider failure returns Result Failure`() = runBlocking {
        setupAutonomy(AutonomyLevel.SUGGEST_ONLY)
        coEvery { ironMindAI.process(any()) } returns Result.Failure(Exception("Provider down"))

        val result = useCase(defaultContext())

        assertTrue(result is Result.Failure)
    }

    // ============================================================
    // UNKNOWN target completion does not suppress
    // ============================================================
    @Test
    fun `UNKNOWN target completion does not suppress`() = runBlocking {
        setupAutonomy(AutonomyLevel.SUGGEST_ONLY)
        setupAI(validAIOutput())
        setupTargetCompletion(TargetCompletionResult.UNKNOWN)
        setupHistory()
        setupSaveSuccess()

        val result = useCase(defaultContext())

        assertTrue(result is Result.Success)
        assertTrue((result as Result.Success).data is InterventionRecommendationResult.Recommended)
    }

    // ============================================================
    // COMPLETION_UNDEFINED does not suppress
    // ============================================================
    @Test
    fun `COMPLETION_UNDEFINED does not suppress`() = runBlocking {
        setupAutonomy(AutonomyLevel.SUGGEST_ONLY)
        setupAI(validAIOutput())
        setupTargetCompletion(TargetCompletionResult.COMPLETION_UNDEFINED)
        setupHistory()
        setupSaveSuccess()

        val result = useCase(defaultContext())

        assertTrue(result is Result.Success)
        assertTrue((result as Result.Success).data is InterventionRecommendationResult.Recommended)
    }

    // ============================================================
    // ASK_BEFORE_ACTION generates recommendations in V2
    // ============================================================
    @Test
    fun `ASK_BEFORE_ACTION generates recommendations in V2`() = runBlocking {
        setupAutonomy(AutonomyLevel.ASK_BEFORE_ACTION)
        setupAI(validAIOutput())
        setupTargetCompletion(TargetCompletionResult.NOT_COMPLETED)
        setupHistory()
        setupSaveSuccess()

        val result = useCase(defaultContext())

        assertTrue(result is Result.Success)
        assertTrue((result as Result.Success).data is InterventionRecommendationResult.Recommended)
    }

    // ============================================================
    // FULL_AUTO generates recommendations in V2
    // ============================================================
    @Test
    fun `FULL_AUTO generates recommendations in V2`() = runBlocking {
        setupAutonomy(AutonomyLevel.FULL_AUTO)
        setupAI(validAIOutput())
        setupTargetCompletion(TargetCompletionResult.NOT_COMPLETED)
        setupHistory()
        setupSaveSuccess()

        val result = useCase(defaultContext())

        assertTrue(result is Result.Success)
        assertTrue((result as Result.Success).data is InterventionRecommendationResult.Recommended)
    }
}
