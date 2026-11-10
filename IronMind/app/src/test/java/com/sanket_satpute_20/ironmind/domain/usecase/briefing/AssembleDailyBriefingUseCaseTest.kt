package com.sanket_satpute_20.ironmind.domain.usecase.briefing

import com.sanket_satpute_20.ironmind.domain.ai.BarrierCategory
import com.sanket_satpute_20.ironmind.domain.ai.InterventionObjective
import com.sanket_satpute_20.ironmind.domain.ai.InterventionType
import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.engine.ActionPlanningEngine
import com.sanket_satpute_20.ironmind.domain.engine.BarrierUnderstandingAndInterventionOrchestrator
import com.sanket_satpute_20.ironmind.domain.engine.DailyBriefingEngine
import com.sanket_satpute_20.ironmind.domain.engine.PersonalInsightDiscoveryEngine
import com.sanket_satpute_20.ironmind.domain.engine.PersonalIntelligenceOrchestrator
import com.sanket_satpute_20.ironmind.domain.engine.PersonalIntelligenceResult
import com.sanket_satpute_20.ironmind.domain.engine.PipelineExecutionResult
import com.sanket_satpute_20.ironmind.domain.model.Commitment
import com.sanket_satpute_20.ironmind.domain.model.CommitmentStatus
import com.sanket_satpute_20.ironmind.domain.model.EntitySource
import com.sanket_satpute_20.ironmind.domain.model.Goal
import com.sanket_satpute_20.ironmind.domain.model.GoalStatus
import com.sanket_satpute_20.ironmind.domain.model.MemoryConfirmationState
import com.sanket_satpute_20.ironmind.domain.model.Outcome
import com.sanket_satpute_20.ironmind.domain.model.Plan
import com.sanket_satpute_20.ironmind.domain.model.PlanStatus
import com.sanket_satpute_20.ironmind.domain.model.Task
import com.sanket_satpute_20.ironmind.domain.model.TaskStatus
import com.sanket_satpute_20.ironmind.domain.model.action.ActionCategory
import com.sanket_satpute_20.ironmind.domain.model.action.ActionReviewStatus
import com.sanket_satpute_20.ironmind.domain.model.action.ProposedAction
import com.sanket_satpute_20.ironmind.domain.model.barrier.BarrierConfirmationState
import com.sanket_satpute_20.ironmind.domain.model.barrier.BarrierHypothesis
import com.sanket_satpute_20.ironmind.domain.model.barrier.BarrierStatus
import com.sanket_satpute_20.ironmind.domain.model.briefing.AttentionReason
import com.sanket_satpute_20.ironmind.domain.model.context.CrossDomainCorrelation
import com.sanket_satpute_20.ironmind.domain.model.context.CrossDomainCorrelationType
import com.sanket_satpute_20.ironmind.domain.model.context.DigitalBehaviourContext
import com.sanket_satpute_20.ironmind.domain.model.context.EpistemicAuthority
import com.sanket_satpute_20.ironmind.domain.model.context.ProductivityContext
import com.sanket_satpute_20.ironmind.domain.model.context.SourceAvailability
import com.sanket_satpute_20.ironmind.domain.model.context.SourceFreshness
import com.sanket_satpute_20.ironmind.domain.model.context.UnifiedContext
import com.sanket_satpute_20.ironmind.domain.model.context.UsagePermissionStatus
import com.sanket_satpute_20.ironmind.domain.model.insight.ExplainabilityBlock
import com.sanket_satpute_20.ironmind.domain.model.insight.InsightCategory
import com.sanket_satpute_20.ironmind.domain.model.insight.InsightReviewStatus
import com.sanket_satpute_20.ironmind.domain.model.insight.PersonalInsight
import com.sanket_satpute_20.ironmind.domain.model.intervention.InterventionRecommendation
import com.sanket_satpute_20.ironmind.domain.model.intervention.InterventionRecommendationStatus
import com.sanket_satpute_20.ironmind.domain.model.pattern.Pattern
import com.sanket_satpute_20.ironmind.domain.model.pattern.PatternStatus
import com.sanket_satpute_20.ironmind.domain.model.pattern.PatternType
import com.sanket_satpute_20.ironmind.domain.repository.ActionRepository
import com.sanket_satpute_20.ironmind.domain.repository.InsightRepository
import com.sanket_satpute_20.ironmind.domain.repository.InterventionRecommendationRepository
import com.sanket_satpute_20.ironmind.domain.repository.OutcomeRepository
import com.sanket_satpute_20.ironmind.domain.repository.PlanRepository
import com.sanket_satpute_20.ironmind.domain.usecase.action.ActionLifecycleUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.commitment.GetActiveCommitmentsUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.task.CreateTaskUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.task.EditTaskUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.task.GetDueTasksUseCase
import com.sanket_satpute_20.ironmind.testutil.fake.FakeClock
import com.sanket_satpute_20.ironmind.testutil.fake.FakeCommitmentRepository
import com.sanket_satpute_20.ironmind.testutil.fake.FakeEventRepository
import com.sanket_satpute_20.ironmind.testutil.fake.FakeGoalRepository
import com.sanket_satpute_20.ironmind.testutil.fake.FakeIdGenerator
import com.sanket_satpute_20.ironmind.testutil.fake.FakeTaskRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * Dedicated unit and pipeline integration tests for [AssembleDailyBriefingUseCase].
 *
 * Verifies validation, window/zone propagation, dependency aggregation, crash-recovery
 * reconciliation ordering, graceful degradation for optional inputs, failure/cancellation
 * propagation, action persistence idempotency, read-only guarantee on upstream entities,
 * cross-user isolation, and real orchestrator-to-briefing integration.
 */
class AssembleDailyBriefingUseCaseTest {

    private val userId = "user-usecase-test"
    private val now = 1_700_000_000_000L
    private val sevenDaysMs = 7L * 24 * 60 * 60 * 1000L
    private val zoneId: ZoneId = ZoneId.systemDefault()
    private val startOfTodayMs: Long = Instant.ofEpochMilli(now)
        .atZone(zoneId)
        .toLocalDate()
        .atStartOfDay(zoneId)
        .toInstant()
        .toEpochMilli()

    private lateinit var clock: FakeClock
    private lateinit var idGenerator: FakeIdGenerator
    private lateinit var actionPlanningEngine: ActionPlanningEngine
    private lateinit var dailyBriefingEngine: DailyBriefingEngine

    private lateinit var actionRepository: InMemoryActionRepository
    private lateinit var taskRepository: FakeTaskRepository
    private lateinit var commitmentRepository: FakeCommitmentRepository
    private lateinit var goalRepository: FakeGoalRepository
    private lateinit var planRepository: InMemoryPlanRepository
    private lateinit var recommendationRepository: InMemoryRecommendationRepository
    private lateinit var insightRepository: InMemoryInsightRepository
    private lateinit var outcomeRepository: InMemoryOutcomeRepository
    private lateinit var eventRepository: FakeEventRepository

    private lateinit var getDueTasksUseCase: GetDueTasksUseCase
    private lateinit var getActiveCommitmentsUseCase: GetActiveCommitmentsUseCase
    private lateinit var actionLifecycleUseCase: ActionLifecycleUseCase

    @Before
    fun setUp() {
        clock = FakeClock(now)
        idGenerator = FakeIdGenerator("uid_")
        actionPlanningEngine = ActionPlanningEngine(clock, idGenerator)
        dailyBriefingEngine = DailyBriefingEngine(actionPlanningEngine, clock)

        actionRepository = InMemoryActionRepository()
        taskRepository = FakeTaskRepository()
        commitmentRepository = FakeCommitmentRepository()
        goalRepository = FakeGoalRepository()
        planRepository = InMemoryPlanRepository()
        recommendationRepository = InMemoryRecommendationRepository()
        insightRepository = InMemoryInsightRepository()
        outcomeRepository = InMemoryOutcomeRepository()
        eventRepository = FakeEventRepository()

        getDueTasksUseCase = GetDueTasksUseCase(taskRepository, planRepository, clock)
        getActiveCommitmentsUseCase = GetActiveCommitmentsUseCase(commitmentRepository)

        val editTaskUseCase = EditTaskUseCase(taskRepository, clock)
        val createTaskUseCase = CreateTaskUseCase(taskRepository, idGenerator, clock, eventRepository)
        actionLifecycleUseCase = ActionLifecycleUseCase(
            actionRepository = actionRepository,
            recommendationRepository = recommendationRepository,
            outcomeRepository = outcomeRepository,
            eventRepository = eventRepository,
            taskRepository = taskRepository,
            editTaskUseCase = editTaskUseCase,
            createTaskUseCase = createTaskUseCase,
            clock = clock,
            idGenerator = idGenerator
        )
    }

    @Test
    fun `blank or whitespace userId fails immediately without calling dependencies`() = runBlocking {
        val mockOrchestrator = mockk<PersonalIntelligenceOrchestrator>()
        val mockActionRepo = mockk<ActionRepository>()
        val mockLifecycle = mockk<ActionLifecycleUseCase>()

        val useCase = AssembleDailyBriefingUseCase(
            actionRepository = mockActionRepo,
            dailyBriefingEngine = dailyBriefingEngine,
            personalIntelligenceOrchestrator = mockOrchestrator,
            getDueTasksUseCase = getDueTasksUseCase,
            getActiveCommitmentsUseCase = getActiveCommitmentsUseCase,
            goalRepository = goalRepository,
            planRepository = planRepository,
            insightRepository = insightRepository,
            recommendationRepository = recommendationRepository,
            clock = clock,
            actionLifecycleUseCase = mockLifecycle
        )

        for (invalidId in listOf("", "   ", "\t\n")) {
            val result = useCase(invalidId)
            assertTrue("Expected Failure for blank userId '$invalidId'", result is Result.Failure)
            assertTrue((result as Result.Failure).error is IllegalArgumentException)
        }

        coVerify(exactly = 0) { mockLifecycle.reconcileInterruptedActions(any()) }
        coVerify(exactly = 0) { mockActionRepo.getActionsForUser(any()) }
        coVerify(exactly = 0) { mockOrchestrator.orchestrate(any(), any(), any(), any()) }
    }

    @Test
    fun `default 7-day window and custom window or zoneId propagate to orchestrator and recommendationRepository`() = runBlocking {
        val customZone = ZoneOffset.UTC
        val mockOrchestrator = mockk<PersonalIntelligenceOrchestrator>()
        coEvery { mockOrchestrator.orchestrate(any(), any(), any(), any()) } returns Result.Success(
            buildIntelResult(buildPipelineResult())
        )

        val useCase = buildUseCase(mockOrchestrator)

        // Default window [now - 7d, now]
        val defaultRes = useCase(userId = userId, zoneId = customZone)
        assertTrue(defaultRes is Result.Success)
        coVerify(exactly = 1) {
            mockOrchestrator.orchestrate(userId, now - sevenDaysMs, now, customZone)
        }
        assertEquals(now - sevenDaysMs, recommendationRepository.lastStartTime)
        assertEquals(now, recommendationRepository.lastEndTime)
        assertEquals(50, recommendationRepository.lastLimit)

        // Custom explicit window
        val customStart = now - 86_400_000L
        val customEnd = now - 3_600_000L
        val customRes = useCase(
            userId = userId,
            windowStartTimeMs = customStart,
            windowEndTimeMs = customEnd,
            zoneId = customZone
        )
        assertTrue(customRes is Result.Success)
        coVerify(exactly = 1) {
            mockOrchestrator.orchestrate(userId, customStart, customEnd, customZone)
        }
        assertEquals(customStart, recommendationRepository.lastStartTime)
        assertEquals(customEnd, recommendationRepository.lastEndTime)
    }

    @Test
    fun `aggregates all domain sources, delegates to engine, and durably persists newly generated actions`() = runBlocking {
        val stalledPlan = buildPlan(
            id = "plan-stalled",
            title = "Deep Work Plan",
            updatedAt = now - sevenDaysMs - 5_000L
        )
        val overdueTask = buildTask(
            id = "t-over",
            planId = stalledPlan.id,
            dueAt = startOfTodayMs - 60_000L
        )
        val dueTodayTask = buildTask(
            id = "t-today",
            planId = null,
            dueAt = startOfTodayMs + 60_000L
        )
        val commitment = buildCommitment("com-active")
        val rec = buildRecommendation("rec-repo", targetEntityId = commitment.id)
        val pattern = buildPattern("pat-1")
        val barrier = buildBarrier("bar-1", evidenceCount = 2)
        val insight = buildInsight("ins-1", confidence = 0.85f)

        planRepository.savePlan(stalledPlan)
        taskRepository.saveTask(overdueTask)
        taskRepository.saveTask(dueTodayTask)
        commitmentRepository.saveCommitment(commitment)
        goalRepository.saveGoal(buildGoal("goal-1"))
        recommendationRepository.saveRecommendation(rec)

        val mockOrchestrator = mockk<PersonalIntelligenceOrchestrator>()
        coEvery { mockOrchestrator.orchestrate(userId, any(), any(), any()) } returns Result.Success(
            buildIntelResult(
                pipelineResult = buildPipelineResult(
                    patterns = listOf(pattern),
                    barriers = listOf(barrier)
                ),
                insights = listOf(insight)
            )
        )

        val useCase = buildUseCase(mockOrchestrator)
        val result = useCase(userId)

        assertTrue(result is Result.Success)
        val briefing = (result as Result.Success).data
        assertEquals(userId, briefing.userId)
        assertEquals(listOf(overdueTask.id), briefing.overdueTasks.map { it.task.id })
        assertEquals(listOf(dueTodayTask.id), briefing.dueTodayTasks.map { it.task.id })
        assertEquals(listOf(commitment.id), briefing.activeCommitments.map { it.id })
        assertEquals(listOf(stalledPlan.id), briefing.goalsAndPlansNeedingAttention.map { it.entityId })
        assertEquals(AttentionReason.OVERDUE_TASKS_PRESENT, briefing.goalsAndPlansNeedingAttention[0].reason)
        assertEquals(listOf("pat-1"), briefing.discoveredPatterns.map { it.patternId })
        assertEquals(listOf("bar-1"), briefing.evidenceBackedBarriers.map { it.barrierId })
        assertEquals(listOf("ins-1"), briefing.relevantInsights.map { it.id })

        // Actions from overdue task, stalled plan, repo recommendation, and insight are all planned and saved
        assertTrue(briefing.proposedNextActions.isNotEmpty())
        val persisted = (actionRepository.getActionsForUser(userId) as Result.Success).data
        assertEquals(briefing.proposedNextActions.map { it.id }.toSet(), persisted.map { it.id }.toSet())
        assertTrue(persisted.all { it.reviewStatus == ActionReviewStatus.PROPOSED })
    }

    @Test
    fun `reconciles interrupted EXECUTING actions before loading persisted actions into briefing`() = runBlocking {
        val originalDue = startOfTodayMs - 10_000L
        val targetNewDue = now + 86_400_000L
        // Task mutation was NOT committed before crash -> reconcileInterruptedActions should revert EXECUTING to ACCEPTED
        val task = buildTask("t-crash", dueAt = originalDue)
        taskRepository.saveTask(task)

        val interruptedAction = ProposedAction(
            id = "act_reschedule_${task.id}",
            userId = userId,
            category = ActionCategory.TASK_RESCHEDULE,
            title = "Reschedule Overdue Task",
            explanation = "Interrupted during crash window",
            targetEntityId = task.id,
            targetEntityType = "TASK",
            reviewStatus = ActionReviewStatus.EXECUTING,
            createdAt = now - 5_000L,
            suggestedNewDueAt = targetNewDue
        )
        actionRepository.saveAction(interruptedAction)

        val mockOrchestrator = mockk<PersonalIntelligenceOrchestrator>()
        coEvery { mockOrchestrator.orchestrate(userId, any(), any(), any()) } returns Result.Success(
            buildIntelResult(buildPipelineResult())
        )

        val useCase = buildUseCase(mockOrchestrator)
        val result = useCase(userId)

        assertTrue(result is Result.Success)
        val briefing = (result as Result.Success).data
        val reconciledInBriefing = briefing.proposedNextActions.firstOrNull { it.id == interruptedAction.id }
        assertNotNull(reconciledInBriefing)
        assertEquals(ActionReviewStatus.ACCEPTED, reconciledInBriefing!!.reviewStatus)

        // Task remains unmutated because crash happened before commit
        val persistedTask = (taskRepository.getTask(task.id) as Result.Success).data
        assertEquals(originalDue, persistedTask!!.dueAt)
    }

    @Test
    fun `degrades gracefully when optional dependencies are null or return failures`() = runBlocking {
        val pipelineRec = buildRecommendation("rec-pipeline-fallback")
        val mockOrchestrator = mockk<PersonalIntelligenceOrchestrator>()
        coEvery { mockOrchestrator.orchestrate(userId, any(), any(), any()) } returns Result.Success(
            buildIntelResult(buildPipelineResult(recommendations = listOf(pipelineRec)))
        )

        // 1. All optional dependencies null
        val minimalUseCase = AssembleDailyBriefingUseCase(
            actionRepository = null,
            dailyBriefingEngine = dailyBriefingEngine,
            personalIntelligenceOrchestrator = mockOrchestrator,
            getDueTasksUseCase = null,
            getActiveCommitmentsUseCase = null,
            goalRepository = null,
            planRepository = null,
            insightRepository = null,
            recommendationRepository = null,
            clock = clock,
            actionLifecycleUseCase = null
        )
        val minimalResult = minimalUseCase(userId)
        assertTrue(minimalResult is Result.Success)
        val minimalBriefing = (minimalResult as Result.Success).data
        assertTrue(minimalBriefing.overdueTasks.isEmpty())
        assertTrue(minimalBriefing.dueTodayTasks.isEmpty())
        assertTrue(minimalBriefing.activeCommitments.isEmpty())
        assertEquals(listOf("act_rec_rec-pipeline-fallback"), minimalBriefing.proposedNextActions.map { it.id })

        // 2. Configured optional repositories/use cases return Result.Failure
        taskRepository.shouldFail = true
        commitmentRepository.shouldFail = true
        goalRepository.shouldFail = true
        planRepository.shouldFail = true
        recommendationRepository.shouldFail = true
        actionRepository.shouldFailGet = true

        val failingOptionalUseCase = buildUseCase(mockOrchestrator)
        val degradedResult = failingOptionalUseCase(userId)
        assertTrue(degradedResult is Result.Success)
        val degradedBriefing = (degradedResult as Result.Success).data
        assertTrue(degradedBriefing.overdueTasks.isEmpty())
        assertTrue(degradedBriefing.activeCommitments.isEmpty())
        // Recommendation failure falls back to orchestrator's pipeline recommendations
        assertEquals(listOf("act_rec_rec-pipeline-fallback"), degradedBriefing.proposedNextActions.map { it.id })
    }

    @Test
    fun `uses recommendationRepository results instead of pipeline recommendations when repository succeeds`() = runBlocking {
        val pipelineRec = buildRecommendation("rec-from-pipeline")
        val repoRec = buildRecommendation("rec-from-repo")
        recommendationRepository.saveRecommendation(repoRec)

        val mockOrchestrator = mockk<PersonalIntelligenceOrchestrator>()
        coEvery { mockOrchestrator.orchestrate(userId, any(), any(), any()) } returns Result.Success(
            buildIntelResult(buildPipelineResult(recommendations = listOf(pipelineRec)))
        )

        val useCase = buildUseCase(mockOrchestrator)
        val result = useCase(userId)

        assertTrue(result is Result.Success)
        val actionIds = (result as Result.Success).data.proposedNextActions.map { it.id }
        assertTrue(actionIds.contains("act_rec_rec-from-repo"))
        assertFalse(actionIds.contains("act_rec_rec-from-pipeline"))
    }

    @Test
    fun `orchestrator failure propagates Result Failure and short-circuits downstream reads and writes`() = runBlocking {
        val expectedError = IllegalStateException("Orchestration failure")
        val mockOrchestrator = mockk<PersonalIntelligenceOrchestrator>()
        coEvery { mockOrchestrator.orchestrate(userId, any(), any(), any()) } returns Result.Failure(expectedError)

        val mockDueTasks = mockk<GetDueTasksUseCase>()
        val mockCommitments = mockk<GetActiveCommitmentsUseCase>()
        val mockGoals = mockk<com.sanket_satpute_20.ironmind.domain.repository.GoalRepository>()
        val mockPlans = mockk<PlanRepository>()
        val mockRecs = mockk<InterventionRecommendationRepository>()

        val useCase = AssembleDailyBriefingUseCase(
            actionRepository = actionRepository,
            dailyBriefingEngine = dailyBriefingEngine,
            personalIntelligenceOrchestrator = mockOrchestrator,
            getDueTasksUseCase = mockDueTasks,
            getActiveCommitmentsUseCase = mockCommitments,
            goalRepository = mockGoals,
            planRepository = mockPlans,
            recommendationRepository = mockRecs,
            clock = clock
        )

        val result = useCase(userId)
        assertTrue(result is Result.Failure)
        assertEquals("Orchestration failure", (result as Result.Failure).error.message)

        coVerify(exactly = 0) { mockDueTasks.invoke(any(), any(), any()) }
        coVerify(exactly = 0) { mockCommitments.invoke(any()) }
        coVerify(exactly = 0) { mockGoals.getGoalsForUser(any()) }
        coVerify(exactly = 0) { mockPlans.getPlansForUser(any()) }
        coVerify(exactly = 0) { mockRecs.getRecommendationsForUser(any(), any(), any(), any()) }
        assertEquals(0, actionRepository.saveCalls)
    }

    @Test(expected = CancellationException::class)
    fun `CancellationException from orchestrator propagates without wrapping or saving`(): Unit = runBlocking {
        val mockOrchestrator = mockk<PersonalIntelligenceOrchestrator>()
        coEvery { mockOrchestrator.orchestrate(userId, any(), any(), any()) } throws CancellationException("Cancelled")

        val useCase = buildUseCase(mockOrchestrator)
        try {
            useCase(userId)
        } finally {
            assertEquals(0, actionRepository.saveCalls)
        }
    }

    @Test
    fun `repeated invocations are idempotent, preserve persisted status over stale existingActions, and do not re-save`() = runBlocking {
        val overdueTask = buildTask("t-idem", dueAt = startOfTodayMs - 60_000L)
        taskRepository.saveTask(overdueTask)

        val mockOrchestrator = mockk<PersonalIntelligenceOrchestrator>()
        coEvery { mockOrchestrator.orchestrate(userId, any(), any(), any()) } returns Result.Success(
            buildIntelResult(buildPipelineResult())
        )
        val useCase = buildUseCase(mockOrchestrator)

        // First call: plans 1 action and saves it
        val first = (useCase(userId) as Result.Success).data
        assertEquals(1, first.proposedNextActions.size)
        assertEquals(1, actionRepository.saveCalls)
        val actionId = first.proposedNextActions[0].id

        // User accepts the persisted action in repository
        val acceptedPersisted = first.proposedNextActions[0].copy(reviewStatus = ActionReviewStatus.ACCEPTED)
        actionRepository.saveAction(acceptedPersisted)
        val saveCountBeforeRepeat = actionRepository.saveCalls

        // Caller passes an older PROPOSED copy in existingActions; persisted ACCEPTED state must win and not re-save
        val staleCallerCopy = first.proposedNextActions[0].copy(reviewStatus = ActionReviewStatus.PROPOSED)
        val second = (useCase(userId, existingActions = listOf(staleCallerCopy)) as Result.Success).data

        assertEquals(saveCountBeforeRepeat, actionRepository.saveCalls)
        assertEquals(1, second.proposedNextActions.size)
        assertEquals(actionId, second.proposedNextActions[0].id)
        assertEquals(ActionReviewStatus.ACCEPTED, second.proposedNextActions[0].reviewStatus)

        // Caller passes a genuinely new active action not yet in repository; use case persists it once
        val extraCallerAction = ProposedAction(
            id = "act-caller-new",
            userId = userId,
            category = ActionCategory.COMMITMENT_REVIEW,
            title = "Caller Action",
            explanation = "Extra",
            reviewStatus = ActionReviewStatus.PROPOSED,
            createdAt = now
        )
        val third = (useCase(userId, existingActions = listOf(extraCallerAction)) as Result.Success).data
        assertEquals(saveCountBeforeRepeat + 1, actionRepository.saveCalls)
        assertTrue(third.proposedNextActions.any { it.id == "act-caller-new" })
    }

    @Test
    fun `briefing assembly does not mutate upstream tasks, commitments, goals, or plans`() = runBlocking {
        val task = buildTask("t-ro", planId = "p-ro", dueAt = startOfTodayMs - 10_000L)
        val commitment = buildCommitment("c-ro")
        val goal = buildGoal("g-ro")
        val plan = buildPlan("p-ro", updatedAt = now - sevenDaysMs - 10_000L)

        val mockTaskRepo = mockk<com.sanket_satpute_20.ironmind.domain.repository.TaskRepository>()
        coEvery { mockTaskRepo.getTasksForUser(userId) } returns Result.Success(listOf(task))

        val mockCommitmentRepo = mockk<com.sanket_satpute_20.ironmind.domain.repository.CommitmentRepository>()
        coEvery { mockCommitmentRepo.getActiveCommitmentsForUser(userId, any()) } returns Result.Success(listOf(commitment))

        val mockGoalRepo = mockk<com.sanket_satpute_20.ironmind.domain.repository.GoalRepository>()
        coEvery { mockGoalRepo.getGoalsForUser(userId) } returns Result.Success(listOf(goal))

        val mockPlanRepo = mockk<PlanRepository>()
        coEvery { mockPlanRepo.getPlansForUser(userId) } returns Result.Success(listOf(plan))
        coEvery { mockPlanRepo.getPlan(plan.id) } returns Result.Success(plan)

        val mockOrchestrator = mockk<PersonalIntelligenceOrchestrator>()
        coEvery { mockOrchestrator.orchestrate(userId, any(), any(), any()) } returns Result.Success(
            buildIntelResult(buildPipelineResult())
        )

        val useCase = AssembleDailyBriefingUseCase(
            actionRepository = actionRepository,
            dailyBriefingEngine = dailyBriefingEngine,
            personalIntelligenceOrchestrator = mockOrchestrator,
            getDueTasksUseCase = GetDueTasksUseCase(mockTaskRepo, mockPlanRepo, clock),
            getActiveCommitmentsUseCase = GetActiveCommitmentsUseCase(mockCommitmentRepo),
            goalRepository = mockGoalRepo,
            planRepository = mockPlanRepo,
            recommendationRepository = recommendationRepository,
            clock = clock
        )

        val res = useCase(userId)
        assertTrue(res is Result.Success)

        coVerify(exactly = 0) { mockTaskRepo.saveTask(any()) }
        coVerify(exactly = 0) { mockTaskRepo.deleteTask(any(), any()) }
        coVerify(exactly = 0) { mockCommitmentRepo.saveCommitment(any()) }
        coVerify(exactly = 0) { mockGoalRepo.saveGoal(any()) }
        coVerify(exactly = 0) { mockPlanRepo.savePlan(any()) }
    }

    @Test
    fun `cross-user isolation keeps User A and User B tasks, plans, recommendations, insights, and actions separate`() = runBlocking {
        val userA = "user-a"
        val userB = "user-b"

        taskRepository.saveTask(buildTask("t-a", ownerId = userA, planId = "p-a", dueAt = startOfTodayMs - 10_000L))
        taskRepository.saveTask(buildTask("t-b", ownerId = userB, planId = "p-b", dueAt = startOfTodayMs - 10_000L))
        commitmentRepository.saveCommitment(buildCommitment("c-a", ownerId = userA))
        commitmentRepository.saveCommitment(buildCommitment("c-b", ownerId = userB))
        planRepository.savePlan(buildPlan("p-a", ownerId = userA, updatedAt = now - sevenDaysMs - 10_000L))
        planRepository.savePlan(buildPlan("p-b", ownerId = userB, updatedAt = now - sevenDaysMs - 10_000L))
        recommendationRepository.saveRecommendation(buildRecommendation("rec-a", ownerId = userA, targetEntityId = "c-a"))
        recommendationRepository.saveRecommendation(buildRecommendation("rec-b", ownerId = userB, targetEntityId = "c-b"))

        val mockOrchestrator = mockk<PersonalIntelligenceOrchestrator>()
        coEvery { mockOrchestrator.orchestrate(userA, any(), any(), any()) } returns Result.Success(
            buildIntelResult(buildPipelineResult(ownerId = userA), insights = listOf(buildInsight("ins-a", ownerId = userA)))
        )
        coEvery { mockOrchestrator.orchestrate(userB, any(), any(), any()) } returns Result.Success(
            buildIntelResult(buildPipelineResult(ownerId = userB), insights = listOf(buildInsight("ins-b", ownerId = userB)))
        )

        val useCase = buildUseCase(mockOrchestrator)
        val briefingA = (useCase(userA) as Result.Success).data
        val briefingB = (useCase(userB) as Result.Success).data

        assertEquals(userA, briefingA.userId)
        assertEquals(listOf("t-a"), briefingA.overdueTasks.map { it.task.id })
        assertEquals(listOf("c-a"), briefingA.activeCommitments.map { it.id })
        assertEquals(listOf("p-a"), briefingA.goalsAndPlansNeedingAttention.map { it.entityId })
        assertEquals(listOf("ins-a"), briefingA.relevantInsights.map { it.id })
        assertTrue(briefingA.proposedNextActions.all { it.userId == userA })
        assertTrue(briefingA.proposedNextActions.none { it.id.contains("-b") || it.id.contains("_b") })

        assertEquals(userB, briefingB.userId)
        assertEquals(listOf("t-b"), briefingB.overdueTasks.map { it.task.id })
        assertEquals(listOf("c-b"), briefingB.activeCommitments.map { it.id })
        assertEquals(listOf("p-b"), briefingB.goalsAndPlansNeedingAttention.map { it.entityId })
        assertEquals(listOf("ins-b"), briefingB.relevantInsights.map { it.id })
        assertTrue(briefingB.proposedNextActions.all { it.userId == userB })
        assertTrue(briefingB.proposedNextActions.none { it.id.contains("-a") || it.id.contains("_a") })

        val persistedA = (actionRepository.getActionsForUser(userA) as Result.Success).data
        val persistedB = (actionRepository.getActionsForUser(userB) as Result.Success).data
        assertTrue(persistedA.isNotEmpty() && persistedA.all { it.userId == userA })
        assertTrue(persistedB.isNotEmpty() && persistedB.all { it.userId == userB })
    }

    @Test
    fun `integration with real PersonalIntelligenceOrchestrator and ActionLifecycleUseCase preserves closed-loop state`() = runBlocking {
        // Real PersonalInsightDiscoveryEngine + Real PersonalIntelligenceOrchestrator + Real DailyBriefingEngine
        // + Real ActionPlanningEngine + Real ActionLifecycleUseCase over in-memory repositories.
        val overdueTask = buildTask("t-int", dueAt = startOfTodayMs - 3_600_000L)
        taskRepository.saveTask(overdueTask)

        val correlation = CrossDomainCorrelation(
            id = "corr-int",
            title = "App usage during pending task window",
            description = "Observed app usage alongside pending task",
            productivityEntityId = overdueTask.id,
            productivityEntityType = "TASK",
            productivityEntityTitle = overdueTask.title,
            digitalAppPackage = "com.example.feed",
            digitalUsageDurationMs = 1_200_000L,
            observedWindowStartMs = now - 3_600_000L,
            observedWindowEndMs = now,
            correlationType = CrossDomainCorrelationType.CONCURRENT_DIGITAL_ACTIVITY_DURING_PENDING_TASK,
            confidence = 0.80f
        )
        val unconfirmedBarrier = buildBarrier("bar-int", evidenceCount = 2)

        val mockUpstreamOrchestrator = mockk<BarrierUnderstandingAndInterventionOrchestrator>()
        coEvery { mockUpstreamOrchestrator.executePipeline(userId, any(), any(), any()) } returns Result.Success(
            buildPipelineResult(
                correlations = listOf(correlation),
                barriers = listOf(unconfirmedBarrier)
            )
        )

        val realInsightDiscovery = PersonalInsightDiscoveryEngine(clock, idGenerator)
        val realIntelligenceOrchestrator = PersonalIntelligenceOrchestrator(
            barrierUnderstandingOrchestrator = mockUpstreamOrchestrator,
            personalInsightDiscoveryEngine = realInsightDiscovery,
            insightRepository = insightRepository,
            outcomeRepository = outcomeRepository,
            clock = clock,
            recommendationRepository = recommendationRepository
        )

        val useCase = buildUseCase(realIntelligenceOrchestrator)

        // 1. First assembly discovers insights from correlation/barriers and plans PROPOSED action
        val firstBriefing = (useCase(userId) as Result.Success).data
        assertTrue(firstBriefing.relevantInsights.isNotEmpty())
        assertEquals(1, firstBriefing.evidenceBackedBarriers.size)
        assertFalse(firstBriefing.evidenceBackedBarriers[0].isUserConfirmed)
        assertEquals(EpistemicAuthority.AI_HYPOTHESIS, firstBriefing.evidenceBackedBarriers[0].epistemicAuthority)

        val rescheduleAction = firstBriefing.proposedNextActions.first {
            it.category == ActionCategory.TASK_RESCHEDULE && it.targetEntityId == overdueTask.id
        }
        assertEquals(ActionReviewStatus.PROPOSED, rescheduleAction.reviewStatus)

        // 2. User accepts and executes the action explicitly via ActionLifecycleUseCase
        val accepted = (actionLifecycleUseCase.acceptAction(rescheduleAction, userId) as Result.Success).data
        assertEquals(ActionReviewStatus.ACCEPTED, accepted.reviewStatus)
        val executed = (actionLifecycleUseCase.executeAction(accepted, userId) as Result.Success).data
        assertEquals(ActionReviewStatus.EXECUTED, executed.reviewStatus)

        // Task was updated only on explicit execution
        val updatedTask = (taskRepository.getTask(overdueTask.id) as Result.Success).data!!
        assertEquals(executed.suggestedNewDueAt, updatedTask.dueAt)

        // 3. Re-running AssembleDailyBriefingUseCase keeps EXECUTED (outcomeId == null) for review and never re-proposes
        val secondBriefing = (useCase(userId) as Result.Success).data
        val actionsForTask = secondBriefing.proposedNextActions.filter { it.targetEntityId == overdueTask.id }
        assertEquals(1, actionsForTask.size)
        assertEquals(ActionReviewStatus.EXECUTED, actionsForTask[0].reviewStatus)
    }

    // -------------------------------------------------------------------------
    // Local Builders & In-Memory Doubles
    // -------------------------------------------------------------------------

    private fun buildUseCase(orchestrator: PersonalIntelligenceOrchestrator) = AssembleDailyBriefingUseCase(
        actionRepository = actionRepository,
        dailyBriefingEngine = dailyBriefingEngine,
        personalIntelligenceOrchestrator = orchestrator,
        getDueTasksUseCase = getDueTasksUseCase,
        getActiveCommitmentsUseCase = getActiveCommitmentsUseCase,
        goalRepository = goalRepository,
        planRepository = planRepository,
        insightRepository = insightRepository,
        recommendationRepository = recommendationRepository,
        clock = clock,
        actionLifecycleUseCase = actionLifecycleUseCase
    )

    private fun buildPipelineResult(
        ownerId: String = userId,
        patterns: List<Pattern> = emptyList(),
        barriers: List<BarrierHypothesis> = emptyList(),
        recommendations: List<InterventionRecommendation> = emptyList(),
        correlations: List<CrossDomainCorrelation> = emptyList()
    ) = PipelineExecutionResult(
        unifiedContext = UnifiedContext(
            userId = ownerId,
            generatedAt = now,
            windowStartTimeMs = now - sevenDaysMs,
            windowEndTimeMs = now,
            productivity = ProductivityContext(),
            digitalBehaviour = DigitalBehaviourContext(
                permissionStatus = UsagePermissionStatus.GRANTED,
                availability = SourceAvailability.AVAILABLE
            ),
            correlations = correlations
        ),
        discoveredPatterns = emptyList(),
        activePatterns = patterns,
        barrierHypotheses = barriers,
        interventionRecommendations = recommendations,
        insufficientEvidenceBarriers = emptyList(),
        diagnosticNotes = emptyList()
    )

    private fun buildIntelResult(
        pipelineResult: PipelineExecutionResult,
        insights: List<PersonalInsight> = emptyList()
    ) = PersonalIntelligenceResult(
        pipelineResult = pipelineResult,
        newlyDiscoveredInsights = emptyList(),
        allActiveInsights = insights,
        suppressedCount = 0,
        diagnosticNotes = emptyList()
    )

    private fun buildTask(
        id: String,
        ownerId: String = userId,
        planId: String? = null,
        dueAt: Long? = null
    ) = Task(
        id = id,
        userId = ownerId,
        goalId = "goal-1",
        planId = planId,
        title = "Task $id",
        description = "Desc $id",
        status = TaskStatus.PENDING,
        priority = 2,
        estimatedDurationMinutes = 30,
        scheduledAt = null,
        dueAt = dueAt,
        createdAt = now - 86_400_000L,
        updatedAt = now - 86_400_000L,
        source = EntitySource.USER
    )

    private fun buildCommitment(id: String, ownerId: String = userId) = Commitment(
        id = id,
        userId = ownerId,
        goalId = null,
        planId = null,
        taskId = null,
        title = "Commitment $id",
        description = "Desc",
        status = CommitmentStatus.COMMITTED,
        priority = 2,
        committedAt = now,
        source = EntitySource.USER,
        createdAt = now,
        updatedAt = now
    )

    private fun buildGoal(id: String, ownerId: String = userId) = Goal(
        id = id,
        userId = ownerId,
        title = "Goal $id",
        description = "Desc",
        why = "Why",
        importance = 4,
        status = GoalStatus.ACTIVE,
        createdAt = now - 100_000L,
        updatedAt = now - 10_000L
    )

    private fun buildPlan(
        id: String,
        ownerId: String = userId,
        title: String = "Plan $id",
        updatedAt: Long = now - 1_000L
    ) = Plan(
        id = id,
        userId = ownerId,
        goalId = "goal-1",
        title = title,
        description = "Desc",
        status = PlanStatus.ACTIVE,
        createdAt = now - 30L * 86_400_000L,
        updatedAt = updatedAt,
        source = EntitySource.USER
    )

    private fun buildPattern(id: String, ownerId: String = userId) = Pattern(
        id = id,
        userId = ownerId,
        fingerprint = "fp-$id",
        type = PatternType.FOCUS_PATTERN,
        description = "Pattern $id",
        conditions = null,
        predictedBehavior = null,
        confidence = 0.80f,
        evidenceCount = 3,
        evidenceReferences = emptyList(),
        firstObservedAt = now - sevenDaysMs,
        lastObservedAt = now,
        status = PatternStatus.ACTIVE,
        confirmationState = MemoryConfirmationState.UNCONFIRMED,
        createdAt = now - sevenDaysMs,
        updatedAt = now
    )

    private fun buildBarrier(
        id: String,
        ownerId: String = userId,
        evidenceCount: Int
    ) = BarrierHypothesis(
        id = id,
        userId = ownerId,
        category = BarrierCategory.DISTRACTION,
        description = "Barrier $id",
        confirmationState = BarrierConfirmationState.UNCONFIRMED,
        status = BarrierStatus.ACTIVE,
        sourceReflectionId = "ref-1",
        firstObservedAt = now - sevenDaysMs,
        lastObservedAt = now,
        evidenceCount = evidenceCount,
        confidence = 0.78f
    )

    private fun buildInsight(
        id: String,
        ownerId: String = userId,
        confidence: Float = 0.85f
    ) = PersonalInsight(
        id = id,
        userId = ownerId,
        category = InsightCategory.FOCUS,
        title = "Insight $id",
        explanation = "Explanation $id",
        explainabilityBlock = ExplainabilityBlock(
            whatWasObserved = "Focus patterns",
            observationPeriod = "7 days",
            supportingEvidenceDescription = "Evidence",
            whyItMatters = "Efficiency",
            uncertaintyOrLimitations = "Hypothesis",
            suggestedNextSteps = "Protect focus"
        ),
        supportingEvidenceIds = listOf("ev-1", "ev-2"),
        observationWindowStartMs = now - sevenDaysMs,
        observationWindowEndMs = now,
        firstObservedAt = now - sevenDaysMs,
        lastObservedAt = now,
        confidence = confidence,
        epistemicAuthority = EpistemicAuthority.DERIVED_CALCULATION,
        sourceProvenance = listOf("Room:task"),
        sourceFreshness = SourceFreshness.CURRENT,
        reviewStatus = InsightReviewStatus.UNREVIEWED,
        createdAt = now,
        updatedAt = now
    )

    private fun buildRecommendation(
        id: String,
        ownerId: String = userId,
        targetEntityId: String = "com-1"
    ) = InterventionRecommendation(
        id = id,
        userId = ownerId,
        interventionType = InterventionType.PROTECT,
        objective = InterventionObjective.REDUCE_FRICTION,
        targetEntityId = targetEntityId,
        targetEntityType = "COMMITMENT",
        rationale = "Rationale $id",
        suggestedAction = "Protect focus",
        status = InterventionRecommendationStatus.PENDING,
        createdAt = now
    )

    private class InMemoryActionRepository : ActionRepository {
        private val actions = linkedMapOf<String, ProposedAction>()
        var shouldFailGet = false
        var saveCalls = 0

        override suspend fun saveAction(action: ProposedAction): Result<ProposedAction, Exception> {
            saveCalls++
            actions[action.id] = action
            return Result.Success(action)
        }

        override suspend fun getActionById(id: String): Result<ProposedAction?, Exception> {
            return Result.Success(actions[id])
        }

        override suspend fun getActionsForUser(userId: String): Result<List<ProposedAction>, Exception> {
            if (shouldFailGet) return Result.Failure(RuntimeException("Action repo failure"))
            return Result.Success(actions.values.filter { it.userId == userId })
        }

        override fun observeActionsForUser(userId: String): Flow<List<ProposedAction>> {
            return flowOf(actions.values.filter { it.userId == userId })
        }

        override suspend fun updateActionStatus(
            id: String,
            userId: String,
            status: ActionReviewStatus,
            timestamp: Long
        ): Result<Unit, Exception> {
            val existing = actions[id] ?: return Result.Failure(IllegalArgumentException("Missing"))
            actions[id] = existing.copy(reviewStatus = status, reviewedAt = timestamp)
            return Result.Success(Unit)
        }

        override suspend fun deleteAction(id: String, userId: String): Result<Unit, Exception> {
            actions.remove(id)
            return Result.Success(Unit)
        }
    }

    private class InMemoryPlanRepository : PlanRepository {
        private val plans = linkedMapOf<String, Plan>()
        var shouldFail = false

        override suspend fun savePlan(plan: Plan): Result<Unit, Exception> {
            if (shouldFail) return Result.Failure(RuntimeException("Plan failure"))
            plans[plan.id] = plan
            return Result.Success(Unit)
        }

        override suspend fun getPlan(id: String): Result<Plan?, Exception> {
            if (shouldFail) return Result.Failure(RuntimeException("Plan failure"))
            return Result.Success(plans[id])
        }

        override suspend fun getPlansForGoal(goalId: String): Result<List<Plan>, Exception> {
            if (shouldFail) return Result.Failure(RuntimeException("Plan failure"))
            return Result.Success(plans.values.filter { it.goalId == goalId })
        }

        override suspend fun getPlansForUser(userId: String): Result<List<Plan>, Exception> {
            if (shouldFail) return Result.Failure(RuntimeException("Plan failure"))
            return Result.Success(plans.values.filter { it.userId == userId })
        }
    }

    private class InMemoryRecommendationRepository : InterventionRecommendationRepository {
        private val recs = linkedMapOf<String, InterventionRecommendation>()
        var shouldFail = false
        var lastStartTime: Long? = null
        var lastEndTime: Long? = null
        var lastLimit: Int? = null

        override suspend fun saveRecommendation(recommendation: InterventionRecommendation): Result<Unit, Exception> {
            recs[recommendation.id] = recommendation
            return Result.Success(Unit)
        }

        override suspend fun getRecommendation(id: String): Result<InterventionRecommendation?, Exception> {
            return Result.Success(recs[id])
        }

        override fun getPendingRecommendations(userId: String, currentTime: Long): Flow<List<InterventionRecommendation>> {
            return flowOf(recs.values.filter { it.userId == userId && it.status == InterventionRecommendationStatus.PENDING })
        }

        override suspend fun updateRecommendationStatus(
            id: String,
            status: InterventionRecommendationStatus
        ): Result<Unit, Exception> {
            val existing = recs[id] ?: return Result.Failure(IllegalArgumentException("Not found"))
            recs[id] = existing.copy(status = status)
            return Result.Success(Unit)
        }

        override suspend fun getEquivalentRecommendations(
            userId: String,
            objective: String,
            targetEntityType: String?,
            targetEntityId: String?
        ): Result<List<InterventionRecommendation>, Exception> = Result.Success(emptyList())

        override suspend fun getRecommendationsForUser(
            userId: String,
            startTime: Long,
            endTime: Long,
            limit: Int
        ): Result<List<InterventionRecommendation>, Exception> {
            lastStartTime = startTime
            lastEndTime = endTime
            lastLimit = limit
            if (shouldFail) return Result.Failure(RuntimeException("Recommendation repo failure"))
            return Result.Success(recs.values.filter { it.userId == userId }.take(limit))
        }
    }

    private class InMemoryInsightRepository : InsightRepository {
        private val insights = linkedMapOf<String, PersonalInsight>()
        private val flow = MutableStateFlow<List<PersonalInsight>>(emptyList())

        override suspend fun saveInsight(insight: PersonalInsight): Result<PersonalInsight, Exception> {
            insights[insight.id] = insight
            flow.value = insights.values.toList()
            return Result.Success(insight)
        }

        override suspend fun getInsightById(id: String): Result<PersonalInsight?, Exception> {
            return Result.Success(insights[id])
        }

        override suspend fun getInsightsForUser(userId: String): Result<List<PersonalInsight>, Exception> {
            return Result.Success(insights.values.filter { it.userId == userId })
        }

        override fun observeInsightsForUser(userId: String): Flow<List<PersonalInsight>> {
            return flowOf(insights.values.filter { it.userId == userId })
        }

        override suspend fun updateReviewStatus(
            id: String,
            userId: String,
            status: InsightReviewStatus,
            feedback: String?,
            reviewedAt: Long
        ): Result<Unit, Exception> {
            val existing = insights[id] ?: return Result.Failure(IllegalArgumentException("Not found"))
            insights[id] = existing.copy(reviewStatus = status, userFeedback = feedback, reviewedAt = reviewedAt)
            return Result.Success(Unit)
        }

        override suspend fun updateFactualConfirmation(
            id: String,
            userId: String,
            confirmationState: MemoryConfirmationState,
            correction: String?,
            timestamp: Long
        ): Result<Unit, Exception> {
            val existing = insights[id] ?: return Result.Failure(IllegalArgumentException("Not found"))
            insights[id] = existing.copy(confirmationState = confirmationState)
            return Result.Success(Unit)
        }
    }

    private class InMemoryOutcomeRepository : OutcomeRepository {
        private val outcomes = mutableListOf<Outcome>()

        override suspend fun saveOutcome(outcome: Outcome): Result<Unit, Exception> {
            outcomes.add(outcome)
            return Result.Success(Unit)
        }

        override suspend fun getOutcomeForSource(sourceEntityId: String): Result<Outcome?, Exception> {
            return Result.Success(outcomes.firstOrNull { it.sourceEntityId == sourceEntityId })
        }

        override suspend fun getOutcomesForUser(userId: String): Result<List<Outcome>, Exception> {
            return Result.Success(outcomes.filter { it.userId == userId })
        }
    }
}
