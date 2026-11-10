package com.sanket_satpute_20.ironmind.ui.screens.today

import com.sanket_satpute_20.ironmind.domain.model.CommitmentStatus
import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.usecase.commitment.CreateCommitmentUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.commitment.GetActiveCommitmentsUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.commitment.UpdateCommitmentStatusUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.commitment.ScheduleCommitmentUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.commitment.CancelCommitmentScheduleUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.task.GetDueTasksUseCase
import com.sanket_satpute_20.ironmind.testutil.fake.FakeTaskRepository
import com.sanket_satpute_20.ironmind.testutil.fake.FakePlanRepository
import com.sanket_satpute_20.ironmind.domain.model.Task
import com.sanket_satpute_20.ironmind.domain.model.TaskStatus
import com.sanket_satpute_20.ironmind.domain.model.EntitySource
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
import com.sanket_satpute_20.ironmind.domain.session.SessionState
import com.sanket_satpute_20.ironmind.testutil.FakeUserSessionProvider
import com.sanket_satpute_20.ironmind.domain.usecase.ai.OrchestrateInterventionGenerationUseCase
import com.sanket_satpute_20.ironmind.domain.model.ResultStatus
import com.sanket_satpute_20.ironmind.domain.model.Outcome
import com.sanket_satpute_20.ironmind.domain.model.action.ActionCategory
import com.sanket_satpute_20.ironmind.domain.model.action.ActionExecutionResult
import com.sanket_satpute_20.ironmind.domain.model.action.ActionReviewStatus
import com.sanket_satpute_20.ironmind.domain.model.action.ProposedAction
import com.sanket_satpute_20.ironmind.domain.model.action.ActionPriority
import com.sanket_satpute_20.ironmind.domain.model.briefing.DailyBriefing
import com.sanket_satpute_20.ironmind.domain.model.briefing.DiscoveredPatternItem
import com.sanket_satpute_20.ironmind.domain.model.briefing.EvidenceBackedBarrierItem
import com.sanket_satpute_20.ironmind.domain.model.context.EpistemicAuthority
import com.sanket_satpute_20.ironmind.domain.model.context.SourceFreshness
import com.sanket_satpute_20.ironmind.domain.model.insight.PersonalInsight
import com.sanket_satpute_20.ironmind.domain.model.insight.InsightCategory
import com.sanket_satpute_20.ironmind.domain.ai.BarrierCategory
import com.sanket_satpute_20.ironmind.domain.repository.ActionRepository
import com.sanket_satpute_20.ironmind.domain.usecase.action.ActionLifecycleUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.briefing.AssembleDailyBriefingUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.insight.ReviewPersonalInsightUseCase
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.every
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.CancellationException
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

    private fun createViewModel(
        userSessionProvider: FakeUserSessionProvider? = null,
        taskRepo: FakeTaskRepository? = null,
        planRepo: FakePlanRepository? = null,
        assembleDailyBriefingUseCase: AssembleDailyBriefingUseCase? = null,
        actionLifecycleUseCase: ActionLifecycleUseCase? = null,
        actionRepository: ActionRepository? = null,
        reviewPersonalInsightUseCase: ReviewPersonalInsightUseCase? = null,
        pendingRecommendationsUseCase: GetPendingInterventionRecommendationsUseCase? = null
    ) {
        val dueTasksUseCase = if (taskRepo != null) {
            GetDueTasksUseCase(taskRepo, planRepo, clock)
        } else null

        viewModel = TodayViewModel(
            getActiveCommitmentsUseCase = getActiveCommitmentsUseCase,
            updateCommitmentStatusUseCase = updateCommitmentStatusUseCase,
            scheduleCommitmentUseCase = scheduleCommitmentUseCase,
            cancelCommitmentScheduleUseCase = cancelCommitmentScheduleUseCase,
            getPendingInterventionRecommendationsUseCase = pendingRecommendationsUseCase ?: getPendingRecommendationsUseCase,
            handleInterventionResultUseCase = handleInterventionResultUseCase,
            orchestrateInterventionGenerationUseCase = orchestrateInterventionGenerationUseCase,
            userSessionProvider = userSessionProvider,
            createCommitmentUseCase = createCommitmentUseCase,
            getDueTasksUseCase = dueTasksUseCase,
            assembleDailyBriefingUseCase = assembleDailyBriefingUseCase,
            actionLifecycleUseCase = actionLifecycleUseCase,
            actionRepository = actionRepository,
            reviewPersonalInsightUseCase = reviewPersonalInsightUseCase
        )
    }

    private fun testProposedAction(
        id: String,
        userId: String = "user-1",
        status: ActionReviewStatus = ActionReviewStatus.PROPOSED,
        category: ActionCategory = ActionCategory.TASK_RESCHEDULE,
        epistemicAuthority: EpistemicAuthority = EpistemicAuthority.AI_HYPOTHESIS,
        outcomeId: String? = null,
        priority: ActionPriority = ActionPriority.MEDIUM,
        createdAt: Long = 1_000L,
        expiresAt: Long? = null
    ) = ProposedAction(
        id = id,
        userId = userId,
        category = category,
        title = "Action $id",
        explanation = "Explanation $id",
        suggestedSubTasks = listOf("Step 1", "Step 2"),
        reviewStatus = status,
        epistemicAuthority = epistemicAuthority,
        outcomeId = outcomeId,
        priority = priority,
        createdAt = createdAt,
        expiresAt = expiresAt
    )

    private fun testDailyBriefing(
        userId: String = "user-1",
        attentionSummary: String = "Focus on your tasks today",
        actions: List<ProposedAction> = emptyList(),
        insights: List<PersonalInsight> = emptyList(),
        patterns: List<DiscoveredPatternItem> = emptyList(),
        barriers: List<EvidenceBackedBarrierItem> = emptyList(),
        note: String? = null
    ) = DailyBriefing(
        userId = userId,
        generatedAt = 1_000L,
        attentionSummary = attentionSummary,
        proposedNextActions = actions,
        relevantInsights = insights,
        discoveredPatterns = patterns,
        evidenceBackedBarriers = barriers,
        dataAvailabilityNote = note
    )

    private fun testRecommendation(id: String, userId: String) = InterventionRecommendation(
        id = id,
        userId = userId,
        interventionType = InterventionType.BREAK_DOWN,
        objective = com.sanket_satpute_20.ironmind.domain.ai.InterventionObjective.INITIATE_ACTION,
        targetEntityId = "task-$id",
        targetEntityType = "Task",
        suggestedAction = "Action",
        rationale = "Reason",
        status = InterventionRecommendationStatus.PENDING,
        createdAt = 1_000L,
        expiresAt = 2_000L
    )

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
    fun `pending recommendation flow switches UID and cancels old subscription`() = runTest {
        val oldRecommendation = testRecommendation(id = "old-rec", userId = "user-1")
        val newRecommendation = testRecommendation(id = "new-rec", userId = "user-2")
        recommendationRepository.recommendations[oldRecommendation.id] = oldRecommendation
        recommendationRepository.recommendations[newRecommendation.id] = newRecommendation
        val provider = FakeUserSessionProvider(SessionState.OfflineFallback("user-1"))

        createViewModel(provider)
        advanceUntilIdle()
        assertTrue("The old UID flow should be collected", "user-1" in recommendationRepository.startedUsers)
        assertEquals(listOf("old-rec"), (viewModel.uiState.value as TodayUiState.Success).pendingRecommendations.map { it.id })

        provider.setState(SessionState.Authenticated("user-2", isAnonymous = true))
        advanceUntilIdle()

        assertTrue("Session transition must cancel the old UID flow", "user-1" in recommendationRepository.cancelledUsers)
        assertEquals(listOf("new-rec"), (viewModel.uiState.value as TodayUiState.Success).pendingRecommendations.map { it.id })
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

    @Test
    fun `quickAddCommitment updates form and saves commitment successfully`() = runTest {
        createViewModel()
        advanceUntilIdle()

        viewModel.openQuickAdd()
        assertTrue(viewModel.showQuickAddDialog.value)

        viewModel.updateQuickAddTitle("Morning Run")
        viewModel.updateQuickAddDescription("5k run")
        viewModel.updateQuickAddPriority(3)

        viewModel.saveQuickAddCommitment()
        advanceUntilIdle()

        org.junit.Assert.assertFalse(viewModel.showQuickAddDialog.value)
        val state = viewModel.uiState.value as TodayUiState.Success
        assertEquals("Commitment added for today", state.userMessage)
        assertEquals(1, state.activeCommitments.size)
        assertEquals("Morning Run", state.activeCommitments[0].title)
    }

    @Test
    fun `quickAddCommitment with blank title sets quickAddError`() = runTest {
        createViewModel()
        advanceUntilIdle()

        viewModel.openQuickAdd()
        viewModel.updateQuickAddTitle("   ")

        viewModel.saveQuickAddCommitment()
        advanceUntilIdle()

        assertTrue(viewModel.showQuickAddDialog.value)
        assertEquals("Title cannot be empty", viewModel.quickAddError.value)
    }

    @Test
    fun `loadData populates dueToday and overdue tasks from getDueTasksUseCase`() = runTest {
        val taskRepo = FakeTaskRepository()
        val planRepo = FakePlanRepository()

        val zoneId = java.time.ZoneId.systemDefault()
        val now = clock.currentTimeMillis()
        val today = java.time.Instant.ofEpochMilli(now).atZone(zoneId).toLocalDate()
        val startOfToday = today.atStartOfDay(zoneId).toInstant().toEpochMilli()

        val overdueTask = Task(
            id = "t-overdue",
            userId = "user-1",
            goalId = "g1",
            planId = null,
            title = "Overdue task",
            description = "Desc",
            status = TaskStatus.PENDING,
            priority = 1,
            dueAt = startOfToday - 10000L,
            createdAt = now,
            updatedAt = now,
            source = EntitySource.USER
        )

        val dueTodayTask = Task(
            id = "t-due-today",
            userId = "user-1",
            goalId = "g1",
            planId = null,
            title = "Due today task",
            description = "Desc",
            status = TaskStatus.PENDING,
            priority = 1,
            dueAt = startOfToday + 5000L,
            createdAt = now,
            updatedAt = now,
            source = EntitySource.USER
        )

        taskRepo.saveTask(overdueTask)
        taskRepo.saveTask(dueTodayTask)

        createViewModel(taskRepo = taskRepo, planRepo = planRepo)
        advanceUntilIdle()

        val state = viewModel.uiState.value as TodayUiState.Success
        assertEquals(1, state.overdueTasks.size)
        assertEquals("t-overdue", state.overdueTasks[0].task.id)
        assertEquals(1, state.dueTodayTasks.size)
        assertEquals("t-due-today", state.dueTodayTasks[0].task.id)
    }

    // =========================================================================
    // Phase 3.6.7.4 Tests: Today Insight Actionability
    // =========================================================================

    @Test
    fun `daily briefing loads successfully and updates ui state`() = runTest {
        val action = testProposedAction("act-1")
        val briefing = testDailyBriefing(actions = listOf(action))
        val mockBriefingUseCase = mockk<AssembleDailyBriefingUseCase>()
        coEvery { mockBriefingUseCase.invoke(any(), any(), any(), any(), any()) } returns Result.Success(briefing)

        createViewModel(assembleDailyBriefingUseCase = mockBriefingUseCase)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is TodayUiState.Success)
        val success = state as TodayUiState.Success
        assertNotNull(success.dailyBriefing)
        assertEquals("Focus on your tasks today", success.dailyBriefing?.attentionSummary)
        assertEquals(1, success.proposedActions.size)
        assertEquals("act-1", success.proposedActions[0].id)
    }

    @Test
    fun `briefing failure does not crash TodayViewModel`() = runTest {
        val mockBriefingUseCase = mockk<AssembleDailyBriefingUseCase>()
        coEvery { mockBriefingUseCase.invoke(any(), any(), any(), any(), any()) } returns Result.Failure(RuntimeException("Briefing assembly failed"))

        createViewModel(assembleDailyBriefingUseCase = mockBriefingUseCase)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is TodayUiState.Success)
        val success = state as TodayUiState.Success
        assertNull(success.dailyBriefing)
        assertFalse(success.isBriefingLoading)
    }

    @Test
    fun `reactive action repository emissions update Today UI state`() = runTest {
        val fakeActionRepo = FakeActionRepository()
        createViewModel(actionRepository = fakeActionRepo)
        advanceUntilIdle()

        val action1 = testProposedAction("act-1", "user-1")
        val action2 = testProposedAction("act-2", "user-1")
        fakeActionRepo.actionsFlow.value = listOf(action1, action2)
        advanceUntilIdle()

        val state = viewModel.uiState.value as TodayUiState.Success
        assertEquals(2, state.proposedActions.size)
        assertEquals("act-1", state.proposedActions[0].id)
        assertEquals("act-2", state.proposedActions[1].id)
    }

    @Test
    fun `empty action state is handled`() = runTest {
        val fakeActionRepo = FakeActionRepository()
        createViewModel(actionRepository = fakeActionRepo)
        advanceUntilIdle()

        fakeActionRepo.actionsFlow.value = listOf(testProposedAction("act-1", "user-1"))
        advanceUntilIdle()
        assertEquals(1, (viewModel.uiState.value as TodayUiState.Success).proposedActions.size)

        fakeActionRepo.actionsFlow.value = emptyList()
        advanceUntilIdle()
        assertTrue((viewModel.uiState.value as TodayUiState.Success).proposedActions.isEmpty())
    }

    @Test
    fun `action observation is cancelled on session reset`() = runTest {
        val sessionProvider = FakeUserSessionProvider(SessionState.Authenticated("user-1", false))
        val fakeActionRepo = FakeActionRepository()
        createViewModel(userSessionProvider = sessionProvider, actionRepository = fakeActionRepo)
        advanceUntilIdle()

        assertTrue(fakeActionRepo.startedUsers.contains("user-1"))

        sessionProvider.setState(SessionState.Initializing)
        advanceUntilIdle()

        assertTrue(fakeActionRepo.cancelledUsers.contains("user-1"))
        assertTrue(viewModel.uiState.value is TodayUiState.Loading)
    }

    @Test
    fun `switching users clears previous user data and observes new user`() = runTest {
        val sessionProvider = FakeUserSessionProvider(SessionState.Authenticated("user-1", false))
        val fakeActionRepo = FakeActionRepository()
        createViewModel(userSessionProvider = sessionProvider, actionRepository = fakeActionRepo)
        advanceUntilIdle()

        fakeActionRepo.actionsFlow.value = listOf(testProposedAction("act-u1", "user-1"))
        advanceUntilIdle()
        assertEquals(1, (viewModel.uiState.value as TodayUiState.Success).proposedActions.size)

        sessionProvider.setState(SessionState.Authenticated("user-2", false))
        advanceUntilIdle()

        assertTrue(fakeActionRepo.startedUsers.contains("user-2"))
        fakeActionRepo.actionsFlow.value = listOf(testProposedAction("act-u2", "user-2"))
        advanceUntilIdle()

        val state = viewModel.uiState.value as TodayUiState.Success
        assertEquals(1, state.proposedActions.size)
        assertEquals("act-u2", state.proposedActions[0].id)
    }

    @Test
    fun `delayed old-user emissions cannot overwrite new-user state`() = runTest {
        val sessionProvider = FakeUserSessionProvider(SessionState.Authenticated("user-1", false))
        val fakeActionRepo = FakeActionRepository()
        createViewModel(userSessionProvider = sessionProvider, actionRepository = fakeActionRepo)
        advanceUntilIdle()

        sessionProvider.setState(SessionState.Authenticated("user-2", false))
        advanceUntilIdle()

        // Old user emission arriving late
        fakeActionRepo.actionsFlow.value = listOf(testProposedAction("act-old", "user-1"))
        advanceUntilIdle()

        val state = viewModel.uiState.value as TodayUiState.Success
        assertTrue(state.proposedActions.none { it.userId == "user-1" })
    }

    @Test
    fun `accept action uses existing lifecycle use case`() = runTest {
        val action = testProposedAction("act-1", "user-1")
        val mockActionLifecycle = mockk<ActionLifecycleUseCase>()
        coEvery { mockActionLifecycle.acceptAction(action, "user-1") } returns Result.Success(
            action.copy(reviewStatus = ActionReviewStatus.ACCEPTED)
        )

        createViewModel(actionLifecycleUseCase = mockActionLifecycle)
        advanceUntilIdle()

        viewModel.acceptAction(action)
        advanceUntilIdle()

        coVerify(exactly = 1) { mockActionLifecycle.acceptAction(action, "user-1") }
        val state = viewModel.uiState.value as TodayUiState.Success
        assertEquals("Action approved: ready for execution", state.userMessage)
    }

    @Test
    fun `reject and dismiss preserve epistemic independence`() = runTest {
        val action = testProposedAction("act-1", "user-1")
        val mockActionLifecycle = mockk<ActionLifecycleUseCase>()
        coEvery { mockActionLifecycle.rejectAction(action, "user-1", "Not applicable") } returns Result.Success(
            action.copy(reviewStatus = ActionReviewStatus.REJECTED)
        )
        coEvery { mockActionLifecycle.dismissAction(action, "user-1") } returns Result.Success(
            action.copy(reviewStatus = ActionReviewStatus.DISMISSED)
        )

        createViewModel(actionLifecycleUseCase = mockActionLifecycle)
        advanceUntilIdle()

        viewModel.rejectAction(action, "Not applicable")
        advanceUntilIdle()
        coVerify(exactly = 1) { mockActionLifecycle.rejectAction(action, "user-1", "Not applicable") }
        assertEquals("Action rejected", (viewModel.uiState.value as TodayUiState.Success).userMessage)

        viewModel.dismissAction(action)
        advanceUntilIdle()
        coVerify(exactly = 1) { mockActionLifecycle.dismissAction(action, "user-1") }
        assertEquals("Action dismissed", (viewModel.uiState.value as TodayUiState.Success).userMessage)
    }

    @Test
    fun `defer action preserves existing semantics`() = runTest {
        val action = testProposedAction("act-1", "user-1")
        val deferMs = 2_000_000L
        val mockActionLifecycle = mockk<ActionLifecycleUseCase>()
        coEvery { mockActionLifecycle.deferAction(action, "user-1", deferMs) } returns Result.Success(
            action.copy(reviewStatus = ActionReviewStatus.DEFERRED, deferredUntilMs = deferMs)
        )

        createViewModel(actionLifecycleUseCase = mockActionLifecycle)
        advanceUntilIdle()

        viewModel.deferAction(action, deferMs)
        advanceUntilIdle()

        coVerify(exactly = 1) { mockActionLifecycle.deferAction(action, "user-1", deferMs) }
        assertEquals("Action deferred", (viewModel.uiState.value as TodayUiState.Success).userMessage)
    }

    @Test
    fun `execute is only possible for ACCEPTED actions`() = runTest {
        val proposedAction = testProposedAction("act-1", "user-1", status = ActionReviewStatus.PROPOSED)
        val acceptedAction = testProposedAction("act-2", "user-1", status = ActionReviewStatus.ACCEPTED)
        val mockActionLifecycle = mockk<ActionLifecycleUseCase>()
        coEvery { mockActionLifecycle.executeAction(acceptedAction, "user-1", any()) } returns Result.Success(
            acceptedAction.copy(
                reviewStatus = ActionReviewStatus.EXECUTED,
                executionResult = ActionExecutionResult(
                    executedAt = 1_000L,
                    success = true,
                    affectedEntityId = "task-1",
                    affectedEntityType = "Task",
                    message = "Executed successfully"
                )
            )
        )

        createViewModel(actionLifecycleUseCase = mockActionLifecycle)
        advanceUntilIdle()

        // Executing PROPOSED action must fail validation
        viewModel.executeAction(proposedAction)
        advanceUntilIdle()

        coVerify(exactly = 0) { mockActionLifecycle.executeAction(proposedAction, any(), any()) }
        assertEquals("Action must be in ACCEPTED state before execution", (viewModel.uiState.value as TodayUiState.Success).userMessage)

        // Executing ACCEPTED action succeeds
        viewModel.executeAction(acceptedAction)
        advanceUntilIdle()

        coVerify(exactly = 1) { mockActionLifecycle.executeAction(acceptedAction, "user-1", any()) }
        assertEquals("Executed successfully", (viewModel.uiState.value as TodayUiState.Success).userMessage)
    }

    @Test
    fun `duplicate execution is prevented by existing lifecycle protection`() = runTest {
        val acceptedAction = testProposedAction("act-1", "user-1", status = ActionReviewStatus.ACCEPTED)
        val mockActionLifecycle = mockk<ActionLifecycleUseCase>()
        coEvery { mockActionLifecycle.executeAction(acceptedAction, "user-1", any()) } returns Result.Failure(
            IllegalStateException("Execution already in progress for action act-1")
        )

        createViewModel(actionLifecycleUseCase = mockActionLifecycle)
        advanceUntilIdle()

        viewModel.executeAction(acceptedAction)
        advanceUntilIdle()

        assertEquals("Execution already in progress for action act-1", (viewModel.uiState.value as TodayUiState.Success).userMessage)
    }

    @Test
    fun `interrupted execution recovery remains compatible with existing reconciliation`() = runTest {
        val fakeActionRepo = FakeActionRepository()
        createViewModel(actionRepository = fakeActionRepo)
        advanceUntilIdle()

        val executingAction = testProposedAction("act-rec", "user-1", status = ActionReviewStatus.EXECUTING)
        fakeActionRepo.actionsFlow.value = listOf(executingAction)
        advanceUntilIdle()

        val state = viewModel.uiState.value as TodayUiState.Success
        assertEquals(1, state.proposedActions.size)
        assertEquals(ActionReviewStatus.EXECUTING, state.proposedActions[0].reviewStatus)
    }

    @Test
    fun `outcome recording does not execute an action again`() = runTest {
        val executedAction = testProposedAction("act-1", "user-1", status = ActionReviewStatus.EXECUTED)
        val mockActionLifecycle = mockk<ActionLifecycleUseCase>()
        val sampleOutcome = Outcome(
            id = "outcome-456",
            userId = "user-1",
            sourceEntityId = executedAction.id,
            sourceEntityType = "PROPOSED_ACTION",
            resultStatus = ResultStatus.POSITIVE,
            actualDurationMinutes = 15,
            completedAt = 1_000L,
            createdAt = 1_000L
        )
        coEvery { mockActionLifecycle.recordActionOutcome(executedAction, "user-1", ResultStatus.POSITIVE, any(), any()) } returns Result.Success(
            sampleOutcome
        )

        createViewModel(actionLifecycleUseCase = mockActionLifecycle)
        advanceUntilIdle()

        viewModel.recordActionOutcome(executedAction, ResultStatus.POSITIVE)
        advanceUntilIdle()

        coVerify(exactly = 1) { mockActionLifecycle.recordActionOutcome(executedAction, "user-1", ResultStatus.POSITIVE, any(), any()) }
        coVerify(exactly = 0) { mockActionLifecycle.executeAction(any(), any(), any()) }
        assertEquals("Outcome recorded", (viewModel.uiState.value as TodayUiState.Success).userMessage)
    }

    @Test
    fun `useful insight review dispatches correctly`() = runTest {
        val mockReviewUseCase = mockk<ReviewPersonalInsightUseCase>()
        coEvery { mockReviewUseCase.markUseful("ins-1", "user-1") } returns Result.Success(Unit)

        createViewModel(reviewPersonalInsightUseCase = mockReviewUseCase)
        advanceUntilIdle()

        viewModel.markInsightUseful("ins-1")
        advanceUntilIdle()

        coVerify(exactly = 1) { mockReviewUseCase.markUseful("ins-1", "user-1") }
        assertEquals("Insight marked as useful", (viewModel.uiState.value as TodayUiState.Success).userMessage)
    }

    @Test
    fun `dismiss insight review dispatches correctly`() = runTest {
        val mockReviewUseCase = mockk<ReviewPersonalInsightUseCase>()
        coEvery { mockReviewUseCase.dismiss("ins-1", "user-1") } returns Result.Success(Unit)

        createViewModel(reviewPersonalInsightUseCase = mockReviewUseCase)
        advanceUntilIdle()

        viewModel.dismissInsight("ins-1")
        advanceUntilIdle()

        coVerify(exactly = 1) { mockReviewUseCase.dismiss("ins-1", "user-1") }
        assertEquals("Insight dismissed", (viewModel.uiState.value as TodayUiState.Success).userMessage)
    }

    @Test
    fun `review later insight review dispatches correctly`() = runTest {
        val mockReviewUseCase = mockk<ReviewPersonalInsightUseCase>()
        coEvery { mockReviewUseCase.reviewLater("ins-1", "user-1") } returns Result.Success(Unit)

        createViewModel(reviewPersonalInsightUseCase = mockReviewUseCase)
        advanceUntilIdle()

        viewModel.reviewInsightLater("ins-1")
        advanceUntilIdle()

        coVerify(exactly = 1) { mockReviewUseCase.reviewLater("ins-1", "user-1") }
        assertEquals("Insight postponed for later review", (viewModel.uiState.value as TodayUiState.Success).userMessage)
    }

    @Test
    fun `insight review failures are handled`() = runTest {
        val mockReviewUseCase = mockk<ReviewPersonalInsightUseCase>()
        coEvery { mockReviewUseCase.markUseful("ins-1", "user-1") } returns Result.Failure(RuntimeException("Database error"))

        createViewModel(reviewPersonalInsightUseCase = mockReviewUseCase)
        advanceUntilIdle()

        viewModel.markInsightUseful("ins-1")
        advanceUntilIdle()

        coVerify(exactly = 1) { mockReviewUseCase.markUseful("ins-1", "user-1") }
        assertEquals("Database error", (viewModel.uiState.value as TodayUiState.Success).userMessage)
    }

    @Test
    fun `briefing patterns and barriers retain correct epistemic labels`() = runTest {
        val pattern = DiscoveredPatternItem(
            patternId = "pat-1",
            patternType = "FOCUS_DRIFT",
            description = "High evening app usage",
            confidence = 0.85f,
            occurrences = 4,
            epistemicAuthority = EpistemicAuthority.AI_HYPOTHESIS,
            freshness = SourceFreshness.CURRENT,
            isUserConfirmed = false
        )
        val barrier = EvidenceBackedBarrierItem(
            barrierId = "bar-1",
            category = BarrierCategory.DISTRACTION,
            description = "Social media before sleep",
            confidence = 0.90f,
            supportingEvidenceCount = 5,
            isUserConfirmed = false,
            epistemicAuthority = EpistemicAuthority.AI_HYPOTHESIS
        )
        val briefing = testDailyBriefing(
            patterns = listOf(pattern),
            barriers = listOf(barrier)
        )
        val mockBriefingUseCase = mockk<AssembleDailyBriefingUseCase>()
        coEvery { mockBriefingUseCase.invoke(any(), any(), any(), any(), any()) } returns Result.Success(briefing)

        createViewModel(assembleDailyBriefingUseCase = mockBriefingUseCase)
        advanceUntilIdle()

        val state = viewModel.uiState.value as TodayUiState.Success
        val db = state.dailyBriefing
        assertNotNull(db)
        assertEquals(EpistemicAuthority.AI_HYPOTHESIS, db!!.discoveredPatterns[0].epistemicAuthority)
        assertFalse(db.discoveredPatterns[0].isUserConfirmed)
        assertEquals(EpistemicAuthority.AI_HYPOTHESIS, db.evidenceBackedBarriers[0].epistemicAuthority)
        assertFalse(db.evidenceBackedBarriers[0].isUserConfirmed)
    }

    // =========================================================================
    // Phase 3.6.7.4R Remediation Tests
    // =========================================================================

    @Test
    fun `session switch during in-flight briefing does not leak previous user briefing to new user`() = runTest {
        val sessionProvider = FakeUserSessionProvider(SessionState.Authenticated("user-1", false))
        val u1Deferred = CompletableDeferred<Result<DailyBriefing, Exception>>()
        val mockBriefingUseCase = mockk<AssembleDailyBriefingUseCase>()
        coEvery { mockBriefingUseCase.invoke(eq("user-1"), any(), any(), any(), any()) } coAnswers { u1Deferred.await() }
        coEvery { mockBriefingUseCase.invoke(eq("user-2"), any(), any(), any(), any()) } returns Result.Success(
            testDailyBriefing(userId = "user-2", attentionSummary = "User 2 Briefing")
        )

        createViewModel(userSessionProvider = sessionProvider, assembleDailyBriefingUseCase = mockBriefingUseCase)
        advanceUntilIdle()

        // Switch user to user-2 while user-1 briefing is still suspended
        sessionProvider.setState(SessionState.Authenticated("user-2", false))
        advanceUntilIdle()

        // Complete user-1 briefing late
        u1Deferred.complete(Result.Success(testDailyBriefing(userId = "user-1", attentionSummary = "User 1 Leaked Briefing")))
        advanceUntilIdle()

        val state = viewModel.uiState.value as TodayUiState.Success
        assertNotNull(state.dailyBriefing)
        assertEquals("User 2 Briefing", state.dailyBriefing?.attentionSummary)
    }

    @Test
    fun `stale briefing completion after a newer refresh cannot overwrite newer state`() = runTest {
        val deferred1 = CompletableDeferred<Result<DailyBriefing, Exception>>()
        val deferred2 = CompletableDeferred<Result<DailyBriefing, Exception>>()
        val mockBriefingUseCase = mockk<AssembleDailyBriefingUseCase>()
        var callCount = 0
        coEvery { mockBriefingUseCase.invoke(any(), any(), any(), any(), any()) } coAnswers {
            callCount++
            if (callCount == 1) deferred1.await() else deferred2.await()
        }

        createViewModel(assembleDailyBriefingUseCase = mockBriefingUseCase)
        advanceUntilIdle()

        // Refresh triggered again (call 2)
        viewModel.refreshDailyBriefing()
        advanceUntilIdle()

        // Complete newer refresh (call 2) first
        deferred2.complete(Result.Success(testDailyBriefing(attentionSummary = "Newer Briefing")))
        advanceUntilIdle()

        // Complete stale refresh (call 1) late
        deferred1.complete(Result.Success(testDailyBriefing(attentionSummary = "Stale Briefing")))
        advanceUntilIdle()

        val state = viewModel.uiState.value as TodayUiState.Success
        assertEquals("Newer Briefing", state.dailyBriefing?.attentionSummary)
    }

    @Test
    fun `room empty emission remains authoritative when briefing generates actions`() = runTest {
        val fakeActionRepo = FakeActionRepository()
        fakeActionRepo.actionsFlow.value = emptyList()
        val briefingWithActions = testDailyBriefing(
            actions = listOf(testProposedAction("act-briefing-1", "user-1"))
        )
        val mockBriefingUseCase = mockk<AssembleDailyBriefingUseCase>()
        coEvery { mockBriefingUseCase.invoke(any(), any(), any(), any(), any()) } returns Result.Success(briefingWithActions)

        createViewModel(actionRepository = fakeActionRepo, assembleDailyBriefingUseCase = mockBriefingUseCase)
        advanceUntilIdle()

        val state = viewModel.uiState.value as TodayUiState.Success
        assertTrue(state.proposedActions.isEmpty())
        assertNotNull(state.dailyBriefing)
    }

    @Test
    fun `accepted and executing actions remain visible ahead of newer proposed actions`() = runTest {
        val fakeActionRepo = FakeActionRepository()
        val execAction = testProposedAction("act-exec", "user-1", status = ActionReviewStatus.EXECUTING, createdAt = 100L)
        val acceptedAction = testProposedAction("act-acc", "user-1", status = ActionReviewStatus.ACCEPTED, createdAt = 200L)
        val propAction1 = testProposedAction("act-prop1", "user-1", status = ActionReviewStatus.PROPOSED, createdAt = 1000L)
        val propAction2 = testProposedAction("act-prop2", "user-1", status = ActionReviewStatus.PROPOSED, createdAt = 1001L)
        val propAction3 = testProposedAction("act-prop3", "user-1", status = ActionReviewStatus.PROPOSED, createdAt = 1002L)

        createViewModel(actionRepository = fakeActionRepo)
        advanceUntilIdle()

        fakeActionRepo.actionsFlow.value = listOf(propAction1, propAction2, propAction3, acceptedAction, execAction)
        advanceUntilIdle()

        val state = viewModel.uiState.value as TodayUiState.Success
        assertEquals(5, state.proposedActions.size)
        assertEquals("act-exec", state.proposedActions[0].id)
        assertEquals(ActionReviewStatus.EXECUTING, state.proposedActions[0].reviewStatus)
        assertEquals("act-acc", state.proposedActions[1].id)
        assertEquals(ActionReviewStatus.ACCEPTED, state.proposedActions[1].reviewStatus)
        val top3Ids = state.proposedActions.take(3).map { it.id }
        assertTrue(top3Ids.contains("act-exec"))
        assertTrue(top3Ids.contains("act-acc"))
    }

    @Test
    fun `insight review guard is cleaned up on failure and session reset`() = runTest {
        val sessionProvider = FakeUserSessionProvider(SessionState.Authenticated("user-1", false))
        val mockReviewUseCase = mockk<ReviewPersonalInsightUseCase>()
        coEvery { mockReviewUseCase.markUseful("ins-fail", "user-1") } returns Result.Failure(RuntimeException("Review failed"))
        coEvery { mockReviewUseCase.markUseful("ins-retry", "user-1") } returns Result.Success(mockk(relaxed = true))

        createViewModel(userSessionProvider = sessionProvider, reviewPersonalInsightUseCase = mockReviewUseCase)
        advanceUntilIdle()

        // 1. Failure cleanup
        viewModel.markInsightUseful("ins-fail")
        advanceUntilIdle()

        val failedState = viewModel.uiState.value as TodayUiState.Success
        assertEquals("Review failed", failedState.userMessage)

        // Retry same insight after failure succeeds
        coEvery { mockReviewUseCase.markUseful("ins-fail", "user-1") } returns Result.Success(mockk(relaxed = true))
        viewModel.markInsightUseful("ins-fail")
        advanceUntilIdle()

        val retryState = viewModel.uiState.value as TodayUiState.Success
        assertEquals("Insight marked as useful", retryState.userMessage)

        // 2. Session reset cleans up guards
        sessionProvider.setState(SessionState.Initializing)
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value is TodayUiState.Loading)
    }

    @Test
    fun `recommendation flow failure preserves core Today content`() = runTest {
        createCommitmentUseCase("user-1", null, null, null, null, "C1", "D1", 1, initialStatus = CommitmentStatus.COMMITTED)
        val failingRecUseCase = mockk<GetPendingInterventionRecommendationsUseCase>()
        every { failingRecUseCase.invoke(any(), any()) } returns flow {
            throw RuntimeException("Recommendation service unavailable")
        }

        createViewModel(pendingRecommendationsUseCase = failingRecUseCase)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue("Expected Success state despite recommendation failure, but got $state", state is TodayUiState.Success)
        val success = state as TodayUiState.Success
        assertEquals(1, success.activeCommitments.size)
        assertEquals("Recommendations temporarily unavailable", success.userMessage)
    }
}

class FakeInterventionRecommendationRepository : InterventionRecommendationRepository {
    val recommendations = mutableMapOf<String, InterventionRecommendation>()
    val startedUsers = mutableSetOf<String>()
    val cancelledUsers = mutableSetOf<String>()
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
            .onStart { startedUsers += userId }
            .onCompletion { cause -> if (cause is CancellationException) cancelledUsers += userId }
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

class FakeActionRepository : ActionRepository {
    val actionsFlow = MutableStateFlow<List<ProposedAction>>(emptyList())
    val savedActions = mutableMapOf<String, ProposedAction>()
    val startedUsers = mutableSetOf<String>()
    val cancelledUsers = mutableSetOf<String>()

    override suspend fun saveAction(action: ProposedAction): Result<ProposedAction, Exception> {
        savedActions[action.id] = action
        actionsFlow.value = savedActions.values.toList()
        return Result.Success(action)
    }

    override suspend fun getActionById(id: String): Result<ProposedAction?, Exception> {
        return Result.Success(savedActions[id])
    }

    override suspend fun getActionsForUser(userId: String): Result<List<ProposedAction>, Exception> {
        return Result.Success(savedActions.values.filter { it.userId == userId })
    }

    override fun observeActionsForUser(userId: String): Flow<List<ProposedAction>> {
        return actionsFlow
            .onStart { startedUsers += userId }
            .onCompletion { cause -> if (cause is CancellationException) cancelledUsers += userId }
    }

    override suspend fun updateActionStatus(id: String, userId: String, status: ActionReviewStatus, timestamp: Long): Result<Unit, Exception> {
        val existing = savedActions[id] ?: return Result.Failure(IllegalArgumentException("Action not found"))
        val updated = existing.copy(reviewStatus = status)
        savedActions[id] = updated
        actionsFlow.value = savedActions.values.toList()
        return Result.Success(Unit)
    }

    override suspend fun deleteAction(id: String, userId: String): Result<Unit, Exception> {
        savedActions.remove(id)
        actionsFlow.value = savedActions.values.toList()
        return Result.Success(Unit)
    }
}


