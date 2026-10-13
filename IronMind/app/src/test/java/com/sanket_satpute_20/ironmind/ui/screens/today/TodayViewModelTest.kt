package com.sanket_satpute_20.ironmind.ui.screens.today

import com.sanket_satpute_20.ironmind.domain.model.CommitmentStatus
import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.usecase.commitment.CreateCommitmentUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.commitment.GetActiveCommitmentsUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.commitment.UpdateCommitmentStatusUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.commitment.ScheduleCommitmentUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.commitment.CancelCommitmentScheduleUseCase
import com.sanket_satpute_20.ironmind.testutil.TestDispatcherRule
import com.sanket_satpute_20.ironmind.testutil.fake.FakeClock
import com.sanket_satpute_20.ironmind.testutil.fake.FakeCommitmentRepository
import com.sanket_satpute_20.ironmind.testutil.fake.FakeIdGenerator
import com.sanket_satpute_20.ironmind.testutil.fake.FakeOutcomeRepository
import com.sanket_satpute_20.ironmind.testutil.fake.FakeReminderScheduler
import com.sanket_satpute_20.ironmind.testutil.fake.FakeEventRepository
import com.sanket_satpute_20.ironmind.domain.usecase.ai.GetPendingInterventionRecommendationsUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.ai.HandleInterventionResultUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.ai.UpdateInterventionRecommendationStatusUseCase
import com.sanket_satpute_20.ironmind.domain.repository.InterventionRecommendationRepository
import com.sanket_satpute_20.ironmind.domain.model.intervention.InterventionRecommendation
import com.sanket_satpute_20.ironmind.domain.model.intervention.InterventionRecommendationStatus
import com.sanket_satpute_20.ironmind.domain.ai.InterventionType
import com.sanket_satpute_20.ironmind.domain.model.EventType
import com.sanket_satpute_20.ironmind.domain.model.intervention.CorrectionScope
import com.sanket_satpute_20.ironmind.domain.model.intervention.InterventionRecommendationResult
import com.sanket_satpute_20.ironmind.domain.usecase.ai.OrchestrateInterventionGenerationUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.every
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TodayViewModelTest {

    @get:Rule
    val dispatcherRule = TestDispatcherRule()

    private lateinit var repository: FakeCommitmentRepository
    private lateinit var outcomeRepository: FakeOutcomeRepository
    private lateinit var getActiveCommitmentsUseCase: GetActiveCommitmentsUseCase
    private lateinit var updateCommitmentStatusUseCase: UpdateCommitmentStatusUseCase
    private lateinit var createCommitmentUseCase: CreateCommitmentUseCase
    private lateinit var scheduleCommitmentUseCase: ScheduleCommitmentUseCase
    private lateinit var cancelCommitmentScheduleUseCase: CancelCommitmentScheduleUseCase
    private lateinit var clock: FakeClock
    private lateinit var idGenerator: FakeIdGenerator
    private lateinit var reminderScheduler: FakeReminderScheduler
    private lateinit var eventRepository: FakeEventRepository
    
    private lateinit var recommendationRepository: FakeInterventionRecommendationRepository
    private lateinit var getPendingRecommendationsUseCase: GetPendingInterventionRecommendationsUseCase
    private lateinit var updateRecommendationStatusUseCase: UpdateInterventionRecommendationStatusUseCase
    private lateinit var handleInterventionResultUseCase: HandleInterventionResultUseCase
    private lateinit var orchestrateInterventionGenerationUseCase: OrchestrateInterventionGenerationUseCase

    private lateinit var viewModel: TodayViewModel

    @Before
    fun setup() {
        repository = FakeCommitmentRepository()
        outcomeRepository = FakeOutcomeRepository()
        clock = FakeClock()
        idGenerator = FakeIdGenerator()
        reminderScheduler = FakeReminderScheduler()
        eventRepository = FakeEventRepository()

        getActiveCommitmentsUseCase = GetActiveCommitmentsUseCase(repository)
        updateCommitmentStatusUseCase = UpdateCommitmentStatusUseCase(repository, outcomeRepository, reminderScheduler, clock, idGenerator, eventRepository)
        createCommitmentUseCase = CreateCommitmentUseCase(repository, reminderScheduler, idGenerator, clock, eventRepository)
        scheduleCommitmentUseCase = ScheduleCommitmentUseCase(repository, reminderScheduler, clock, idGenerator, eventRepository)
        cancelCommitmentScheduleUseCase = CancelCommitmentScheduleUseCase(repository, reminderScheduler, clock, idGenerator, eventRepository)
        
        recommendationRepository = FakeInterventionRecommendationRepository()
        getPendingRecommendationsUseCase = GetPendingInterventionRecommendationsUseCase(recommendationRepository)
        updateRecommendationStatusUseCase = UpdateInterventionRecommendationStatusUseCase(recommendationRepository)
        handleInterventionResultUseCase = HandleInterventionResultUseCase(eventRepository, updateRecommendationStatusUseCase, clock, idGenerator)
        orchestrateInterventionGenerationUseCase = mockk()
    }

    private fun createViewModel() {
        viewModel = TodayViewModel(
            getActiveCommitmentsUseCase,
            updateCommitmentStatusUseCase,
            scheduleCommitmentUseCase,
            cancelCommitmentScheduleUseCase,
            getPendingRecommendationsUseCase,
            handleInterventionResultUseCase,
            orchestrateInterventionGenerationUseCase
        )
    }

    @Test
    fun `loadCommitments success updates state with active commitments`() = runTest {
        createCommitmentUseCase("user-1", null, null, null, null, "C1", "D1", 1, initialStatus = CommitmentStatus.COMMITTED)
        
        createViewModel()
        
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is TodayUiState.Success)
        
        val successState = state as TodayUiState.Success
        assertEquals(1, successState.activeCommitments.size)
        assertEquals("C1", successState.activeCommitments[0].title)
    }

    @Test
    fun `loadCommitments error updates state to Error`() = runTest {
        repository.shouldFail = true
        
        createViewModel()
        
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is TodayUiState.Error)
    }

    @Test
    fun `updateCommitmentStatus refreshes commitments after success`() = runTest {
        createCommitmentUseCase("user-1", null, null, null, null, "C1", "D1", 1, initialStatus = CommitmentStatus.COMMITTED)
        
        createViewModel()
        advanceUntilIdle()

        val initialState = viewModel.uiState.value as TodayUiState.Success
        val commitmentId = initialState.activeCommitments[0].id

        viewModel.updateCommitmentStatus(commitmentId, CommitmentStatus.STARTED)
        advanceUntilIdle()

        val updatedState = viewModel.uiState.value as TodayUiState.Success
        assertEquals(CommitmentStatus.STARTED, updatedState.activeCommitments[0].status)
    }

    @Test
    fun `updateCommitmentStatus completing commitment removes it from active`() = runTest {
        val c1Result = createCommitmentUseCase("user-1", null, null, null, null, "C1", "D1", 1, initialStatus = CommitmentStatus.COMMITTED)
        updateCommitmentStatusUseCase((c1Result as Result.Success).data.id, CommitmentStatus.STARTED)
        
        createViewModel()
        advanceUntilIdle()

        val initialState = viewModel.uiState.value as TodayUiState.Success
        val commitmentId = initialState.activeCommitments[0].id

        viewModel.updateCommitmentStatus(commitmentId, CommitmentStatus.COMPLETED)
        advanceUntilIdle()

        val updatedState = viewModel.uiState.value as TodayUiState.Success
        assertEquals(0, updatedState.activeCommitments.size)
    }

    @Test
    fun `loadCommitments success updates state with pending recommendations`() = runTest {
        val rec = InterventionRecommendation(
            id = "rec-1",
            userId = "user-1",
            interventionType = InterventionType.BREAK_DOWN,
            objective = com.sanket_satpute_20.ironmind.domain.ai.InterventionObjective.INITIATE_ACTION,
            targetEntityId = "task-1",
            targetEntityType = "Task",
            suggestedAction = "Take a break",
            rationale = "You have been working for 2 hours.",
            status = InterventionRecommendationStatus.PENDING,
            createdAt = 1000L,
            expiresAt = 2000L
        )
        recommendationRepository.recommendations["rec-1"] = rec
        
        createViewModel()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is TodayUiState.Success)
        
        val successState = state as TodayUiState.Success
        assertEquals(1, successState.pendingRecommendations.size)
        assertEquals("Take a break", successState.pendingRecommendations[0].suggestedAction)
    }

    @Test
    fun `askIronMind executes when pending recommendations exist`() = runTest {
        val recommendation = InterventionRecommendation(
            id = "rec-1",
            userId = "user-1",
            interventionType = InterventionType.REFLECT,
            objective = com.sanket_satpute_20.ironmind.domain.ai.InterventionObjective.INITIATE_ACTION,
            targetEntityId = null,
            targetEntityType = null,
            rationale = "High task load detected",
            suggestedAction = "Take a break",
            status = InterventionRecommendationStatus.PENDING,
            createdAt = 1000L,
            expiresAt = 3600L
        )
        recommendationRepository.recommendations["rec-1"] = recommendation

        val mockRecommendation = InterventionRecommendation(
            id = "mock-rec",
            userId = "user-1",
            interventionType = InterventionType.REFLECT,
            objective = com.sanket_satpute_20.ironmind.domain.ai.InterventionObjective.INITIATE_ACTION,
            targetEntityId = null,
            targetEntityType = null,
            rationale = "Mock reasoning",
            suggestedAction = "Mock action",
            status = InterventionRecommendationStatus.PENDING,
            createdAt = 1000L,
            expiresAt = 3600L
        )
        coEvery { orchestrateInterventionGenerationUseCase(any()) } returns Result.Success(InterventionRecommendationResult.Recommended(mockRecommendation))

        createViewModel()
        advanceUntilIdle()
        
        var state = viewModel.uiState.value as TodayUiState.Success
        assertEquals(1, state.pendingRecommendations.size)

        viewModel.askIronMind()
        advanceUntilIdle()

        state = viewModel.uiState.value as TodayUiState.Success
        assertEquals(false, state.isGeneratingRecommendation)
        coVerify(exactly = 1) { orchestrateInterventionGenerationUseCase("user-1") }
    }

    @Test
    fun `askIronMind with NoRecommendation sets transient message`() = runTest {
        coEvery { orchestrateInterventionGenerationUseCase(any()) } returns Result.Success(InterventionRecommendationResult.NoRecommendation)

        createViewModel()
        advanceUntilIdle()

        viewModel.askIronMind()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is TodayUiState.Success)
        val successState = state as TodayUiState.Success
        assertEquals(false, successState.isGeneratingRecommendation)
        assertEquals("No new suggestions at this time.", successState.userMessage)
        
        coVerify(exactly = 1) { orchestrateInterventionGenerationUseCase("user-1") }
    }

    @Test
    fun `askIronMind with Recommended resets generating state without error message`() = runTest {
        val mockRecommendation = InterventionRecommendation(
            id = "mock-rec-2",
            userId = "user-1",
            interventionType = InterventionType.REFLECT,
            objective = com.sanket_satpute_20.ironmind.domain.ai.InterventionObjective.INITIATE_ACTION,
            targetEntityId = null,
            targetEntityType = null,
            rationale = "Mock reasoning",
            suggestedAction = "Mock action",
            status = InterventionRecommendationStatus.PENDING,
            createdAt = 1000L,
            expiresAt = 3600L
        )
        coEvery { orchestrateInterventionGenerationUseCase(any()) } returns Result.Success(InterventionRecommendationResult.Recommended(mockRecommendation))

        createViewModel()
        advanceUntilIdle()

        viewModel.askIronMind()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is TodayUiState.Success)
        val successState = state as TodayUiState.Success
        assertEquals(false, successState.isGeneratingRecommendation)
        assertEquals(null, successState.userMessage) // Handled by Flow, no error message
        
        coVerify(exactly = 1) { orchestrateInterventionGenerationUseCase("user-1") }
    }

    @Test
    fun `askIronMind with Failure sets error message`() = runTest {
        coEvery { orchestrateInterventionGenerationUseCase(any()) } returns Result.Failure(Exception("Network Error"))

        createViewModel()
        advanceUntilIdle()

        viewModel.askIronMind()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is TodayUiState.Success)
        val successState = state as TodayUiState.Success
        assertEquals(false, successState.isGeneratingRecommendation)
        assertEquals("Could not reach IronMind. Try again later.", successState.userMessage)
    }

    @Test
    fun `clearUserMessage clears the message`() = runTest {
        coEvery { orchestrateInterventionGenerationUseCase(any()) } returns Result.Success(InterventionRecommendationResult.NoRecommendation)

        createViewModel()
        advanceUntilIdle()
        viewModel.askIronMind()
        advanceUntilIdle()

        var state = viewModel.uiState.value as TodayUiState.Success
        assertEquals("No new suggestions at this time.", state.userMessage)

        viewModel.clearUserMessage()
        
        state = viewModel.uiState.value as TodayUiState.Success
        assertEquals(null, state.userMessage)
    }

    @Test
    fun `handleInterventionResponse with CORRECT action persists INTERVENTION_OVERRIDDEN with scope and text`() = runTest {
        val rec = InterventionRecommendation(
            id = "rec-corr-1",
            userId = "user-1",
            interventionType = InterventionType.BREAK_DOWN,
            objective = com.sanket_satpute_20.ironmind.domain.ai.InterventionObjective.INITIATE_ACTION,
            targetEntityId = "task-1",
            targetEntityType = "Task",
            suggestedAction = "Take a short walk",
            rationale = "You have been sedentary for 3 hours",
            status = InterventionRecommendationStatus.PENDING,
            createdAt = 1000L,
            expiresAt = 3600L
        )
        recommendationRepository.recommendations["rec-corr-1"] = rec

        createViewModel()
        advanceUntilIdle()

        var state = viewModel.uiState.value as TodayUiState.Success
        assertEquals(1, state.pendingRecommendations.size)

        viewModel.handleInterventionResponse(
            recommendation = rec,
            action = HandleInterventionResultUseCase.Action.CORRECT,
            correctedText = "I injured my knee, walking is not possible",
            scope = CorrectionScope.CONSTRAINT
        )
        advanceUntilIdle()

        state = viewModel.uiState.value as TodayUiState.Success
        assertEquals(0, state.pendingRecommendations.size)
        assertEquals("Correction recorded", state.userMessage)

        val savedEvents = eventRepository.events.values.toList()
        assertEquals(1, savedEvents.size)
        val event = savedEvents.first()
        assertEquals(EventType.INTERVENTION_OVERRIDDEN, event.type)
        assertEquals("rec-corr-1", event.causationId)
        assertTrue(event.metadata!!.contains("scope=CONSTRAINT"))
        assertTrue(event.metadata!!.contains("correctedText=I injured my knee, walking is not possible"))

        val updatedRec = recommendationRepository.recommendations["rec-corr-1"]
        assertEquals(InterventionRecommendationStatus.OVERRIDDEN, updatedRec?.status)

        // Recreation verification: recreated ViewModel must not reload corrected recommendation
        createViewModel()
        advanceUntilIdle()

        val recreatedState = viewModel.uiState.value as TodayUiState.Success
        assertEquals(0, recreatedState.pendingRecommendations.size)
    }

    @Test
    fun `handleInterventionResponse with default scope persists INTERVENTION_CORRECTION`() = runTest {
        val rec = InterventionRecommendation(
            id = "rec-corr-2",
            userId = "user-1",
            interventionType = InterventionType.RESCHEDULE,
            objective = com.sanket_satpute_20.ironmind.domain.ai.InterventionObjective.INITIATE_ACTION,
            targetEntityId = "task-2",
            targetEntityType = "Task",
            suggestedAction = "Reschedule to 5 PM",
            rationale = "Conflicts with current calendar",
            status = InterventionRecommendationStatus.PENDING,
            createdAt = 1000L,
            expiresAt = 3600L
        )
        recommendationRepository.recommendations["rec-corr-2"] = rec

        createViewModel()
        advanceUntilIdle()

        viewModel.handleInterventionResponse(
            recommendation = rec,
            action = HandleInterventionResultUseCase.Action.CORRECT,
            correctedText = "Just moving it myself"
        )
        advanceUntilIdle()

        val state = viewModel.uiState.value as TodayUiState.Success
        assertEquals(0, state.pendingRecommendations.size)
        assertEquals("Correction recorded", state.userMessage)

        val event = eventRepository.events.values.first()
        assertEquals(EventType.INTERVENTION_OVERRIDDEN, event.type)
        assertTrue(event.metadata!!.contains("scope=INTERVENTION_CORRECTION"))
        assertTrue(event.metadata!!.contains("correctedText=Just moving it myself"))
    }

    @Test
    fun `handleInterventionResponse on failure displays error message and retains recommendation`() = runTest {
        val rec = InterventionRecommendation(
            id = "rec-fail-1",
            userId = "user-1",
            interventionType = InterventionType.REFLECT,
            objective = com.sanket_satpute_20.ironmind.domain.ai.InterventionObjective.INITIATE_ACTION,
            targetEntityId = null,
            targetEntityType = null,
            suggestedAction = "Reflect on today",
            rationale = "Day is wrapping up",
            status = InterventionRecommendationStatus.PENDING,
            createdAt = 1000L,
            expiresAt = 3600L
        )
        recommendationRepository.recommendations["rec-fail-1"] = rec
        eventRepository.shouldFail = true

        createViewModel()
        advanceUntilIdle()

        viewModel.handleInterventionResponse(
            recommendation = rec,
            action = HandleInterventionResultUseCase.Action.CORRECT,
            correctedText = "Already reflected"
        )
        advanceUntilIdle()

        val state = viewModel.uiState.value as TodayUiState.Success
        assertEquals(1, state.pendingRecommendations.size)
        assertTrue(state.userMessage!!.contains("Failed to record response"))
    }

    @Test
    fun `handleInterventionResponse on status update failure leaves recommendation pending and retryable`() = runTest {
        val rec = InterventionRecommendation(
            id = "rec-fail-retry",
            userId = "user-1",
            interventionType = InterventionType.BREAK_DOWN,
            objective = com.sanket_satpute_20.ironmind.domain.ai.InterventionObjective.INITIATE_ACTION,
            targetEntityId = "task-r",
            targetEntityType = "Task",
            suggestedAction = "Try a quick start",
            rationale = "Stuck on task",
            status = InterventionRecommendationStatus.PENDING,
            createdAt = 1000L,
            expiresAt = 3600L
        )
        recommendationRepository.recommendations["rec-fail-retry"] = rec
        recommendationRepository.shouldFailUpdateStatus = true

        createViewModel()
        advanceUntilIdle()

        // Attempt correction - should fail due to repository error
        viewModel.handleInterventionResponse(
            recommendation = rec,
            action = HandleInterventionResultUseCase.Action.CORRECT,
            correctedText = "I need a break instead",
            scope = CorrectionScope.INTERVENTION_CORRECTION
        )
        advanceUntilIdle()

        var state = viewModel.uiState.value as TodayUiState.Success
        assertEquals(1, state.pendingRecommendations.size)
        assertTrue(state.userMessage!!.contains("Failed to record response"))
        assertEquals(InterventionRecommendationStatus.PENDING, recommendationRepository.recommendations["rec-fail-retry"]?.status)

        // Retry after transient failure resolves
        recommendationRepository.shouldFailUpdateStatus = false
        viewModel.handleInterventionResponse(
            recommendation = rec,
            action = HandleInterventionResultUseCase.Action.CORRECT,
            correctedText = "I need a break instead",
            scope = CorrectionScope.INTERVENTION_CORRECTION
        )
        advanceUntilIdle()

        state = viewModel.uiState.value as TodayUiState.Success
        assertEquals(0, state.pendingRecommendations.size)
        assertEquals("Correction recorded", state.userMessage)
        assertEquals(InterventionRecommendationStatus.OVERRIDDEN, recommendationRepository.recommendations["rec-fail-retry"]?.status)

        // Recreate ViewModel - durable verification across reload
        createViewModel()
        advanceUntilIdle()

        val recreatedState = viewModel.uiState.value as TodayUiState.Success
        assertEquals(0, recreatedState.pendingRecommendations.size)
    }

    @Test
    fun `handleInterventionResponse with ACCEPT, REJECT, and IGNORE sets appropriate feedback`() = runTest {
        val rec1 = InterventionRecommendation(
            id = "rec-act-1",
            userId = "user-1",
            interventionType = InterventionType.BREAK_DOWN,
            objective = com.sanket_satpute_20.ironmind.domain.ai.InterventionObjective.INITIATE_ACTION,
            targetEntityId = "t1",
            targetEntityType = "Task",
            suggestedAction = "Action 1",
            rationale = "Reason 1",
            status = InterventionRecommendationStatus.PENDING,
            createdAt = 1000L,
            expiresAt = 3600L
        )
        val rec2 = rec1.copy(id = "rec-act-2")
        val rec3 = rec1.copy(id = "rec-act-3")

        recommendationRepository.recommendations["rec-act-1"] = rec1
        recommendationRepository.recommendations["rec-act-2"] = rec2
        recommendationRepository.recommendations["rec-act-3"] = rec3

        createViewModel()
        advanceUntilIdle()

        viewModel.handleInterventionResponse(rec1, HandleInterventionResultUseCase.Action.ACCEPT, null)
        advanceUntilIdle()
        var state = viewModel.uiState.value as TodayUiState.Success
        assertEquals("Suggestion accepted", state.userMessage)

        viewModel.handleInterventionResponse(rec2, HandleInterventionResultUseCase.Action.REJECT, null)
        advanceUntilIdle()
        state = viewModel.uiState.value as TodayUiState.Success
        assertEquals("Suggestion dismissed", state.userMessage)

        viewModel.handleInterventionResponse(rec3, HandleInterventionResultUseCase.Action.IGNORE, null)
        advanceUntilIdle()
        state = viewModel.uiState.value as TodayUiState.Success
        assertEquals("Suggestion ignored", state.userMessage)
    }
}

class FakeInterventionRecommendationRepository : InterventionRecommendationRepository {
    val recommendations = mutableMapOf<String, InterventionRecommendation>()
    var shouldFailUpdateStatus = false
    
    override suspend fun saveRecommendation(recommendation: InterventionRecommendation): Result<Unit, Exception> {
        recommendations[recommendation.id] = recommendation
        return Result.Success(Unit)
    }

    override suspend fun getRecommendation(id: String): Result<InterventionRecommendation?, Exception> {
        return Result.Success(recommendations[id])
    }

    override fun getPendingRecommendations(userId: String, currentTime: Long): Flow<List<InterventionRecommendation>> {
        return MutableStateFlow(recommendations.values.filter { it.userId == userId && it.status == InterventionRecommendationStatus.PENDING })
    }

    override suspend fun updateRecommendationStatus(id: String, status: InterventionRecommendationStatus): Result<Unit, Exception> {
        if (shouldFailUpdateStatus) {
            return Result.Failure(Exception("Database write failed"))
        }
        val rec = recommendations[id] ?: return Result.Failure(Exception("Not found"))
        recommendations[id] = rec.copy(status = status)
        // Refresh Flow if it was a real DB. We'll just update map.
        return Result.Success(Unit)
    }

    override suspend fun getEquivalentRecommendations(
        userId: String,
        objective: String,
        targetEntityType: String?,
        targetEntityId: String?
    ): Result<List<InterventionRecommendation>, Exception> {
        return Result.Success(recommendations.values.filter { 
            it.userId == userId && 
            it.objective.name == objective && 
            it.targetEntityType == targetEntityType && 
            it.targetEntityId == targetEntityId 
        }.sortedByDescending { it.createdAt })
    }

    override suspend fun getRecommendationsForUser(
        userId: String,
        startTime: Long,
        endTime: Long,
        limit: Int
    ): Result<List<InterventionRecommendation>, Exception> {
        return Result.Success(emptyList())
    }
}


