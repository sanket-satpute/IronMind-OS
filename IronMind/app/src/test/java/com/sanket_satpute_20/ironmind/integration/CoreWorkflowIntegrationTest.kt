package com.sanket_satpute_20.ironmind.integration

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.sanket_satpute_20.ironmind.data.local.IronMindDatabase
import com.sanket_satpute_20.ironmind.data.local.dao.IronMindDao
import com.sanket_satpute_20.ironmind.data.local.dao.OutboxDao
import com.sanket_satpute_20.ironmind.data.local.entity.GoalEntity
import com.sanket_satpute_20.ironmind.data.local.entity.OutboxEntity
import com.sanket_satpute_20.ironmind.data.repository.CommitmentRepositoryImpl
import com.sanket_satpute_20.ironmind.data.repository.EventRepositoryImpl
import com.sanket_satpute_20.ironmind.data.repository.GoalRepositoryImpl
import com.sanket_satpute_20.ironmind.data.repository.OutcomeRepositoryImpl
import com.sanket_satpute_20.ironmind.data.repository.PlanRepositoryImpl
import com.sanket_satpute_20.ironmind.data.repository.ReflectionRepositoryImpl
import com.sanket_satpute_20.ironmind.data.repository.TaskRepositoryImpl
import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.logging.IronLogger
import com.sanket_satpute_20.ironmind.domain.model.Commitment
import com.sanket_satpute_20.ironmind.domain.model.CommitmentStatus
import com.sanket_satpute_20.ironmind.domain.model.EntitySource
import com.sanket_satpute_20.ironmind.domain.model.EventType
import com.sanket_satpute_20.ironmind.domain.model.Goal
import com.sanket_satpute_20.ironmind.domain.model.GoalStatus
import com.sanket_satpute_20.ironmind.domain.model.OutboxOperationType
import com.sanket_satpute_20.ironmind.domain.model.Plan
import com.sanket_satpute_20.ironmind.domain.model.PlanStatus
import com.sanket_satpute_20.ironmind.domain.model.SyncStatus
import com.sanket_satpute_20.ironmind.domain.model.Task
import com.sanket_satpute_20.ironmind.domain.model.TaskStatus
import com.sanket_satpute_20.ironmind.domain.model.TimelineItem
import com.sanket_satpute_20.ironmind.domain.session.SessionState
import com.sanket_satpute_20.ironmind.domain.usecase.ai.GetPendingInterventionRecommendationsUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.ai.HandleInterventionResultUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.ai.OrchestrateInterventionGenerationUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.commitment.CancelCommitmentScheduleUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.commitment.CreateCommitmentUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.commitment.GetActiveCommitmentsUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.commitment.ScheduleCommitmentUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.commitment.UpdateCommitmentStatusUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.goal.CreateGoalUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.goal.EditGoalUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.goal.GetGoalProgressUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.goal.GetGoalUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.goal.UpdateGoalStatusUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.history.GetTimelineUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.plan.CreatePlanUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.plan.EditPlanUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.plan.GetPlanProgressUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.plan.GetPlanUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.plan.GetPlansUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.plan.UpdatePlanStatusUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.task.CreateTaskUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.task.DeleteTaskUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.task.EditTaskUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.task.GetDueTasksUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.task.GetTasksUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.task.UpdateTaskStatusUseCase
import com.sanket_satpute_20.ironmind.testutil.FakeUserSessionProvider
import com.sanket_satpute_20.ironmind.testutil.TestDispatcherRule
import com.sanket_satpute_20.ironmind.testutil.fake.FakeClock
import com.sanket_satpute_20.ironmind.testutil.fake.FakeIdGenerator
import com.sanket_satpute_20.ironmind.testutil.fake.FakeReminderScheduler
import com.sanket_satpute_20.ironmind.ui.screens.goals.GoalDetailUiState
import com.sanket_satpute_20.ironmind.ui.screens.goals.GoalDetailViewModel
import com.sanket_satpute_20.ironmind.ui.screens.plans.PlanDetailUiState
import com.sanket_satpute_20.ironmind.ui.screens.plans.PlanDetailViewModel
import com.sanket_satpute_20.ironmind.ui.screens.today.TodayUiState
import com.sanket_satpute_20.ironmind.ui.screens.today.TodayViewModel
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class CoreWorkflowIntegrationTest {

    @get:Rule
    val dispatcherRule = TestDispatcherRule()

    private lateinit var context: Context
    private lateinit var db: IronMindDatabase
    private lateinit var delegatingOutboxDao: DelegatingFailingOutboxDao

    // Real Repositories
    private lateinit var goalRepository: GoalRepositoryImpl
    private lateinit var planRepository: PlanRepositoryImpl
    private lateinit var taskRepository: TaskRepositoryImpl
    private lateinit var commitmentRepository: CommitmentRepositoryImpl
    private lateinit var eventRepository: EventRepositoryImpl
    private lateinit var outcomeRepository: OutcomeRepositoryImpl
    private lateinit var reflectionRepository: ReflectionRepositoryImpl

    // Fakes and Infrastructure
    private lateinit var clock: FakeClock
    private lateinit var idGenerator: FakeIdGenerator
    private lateinit var reminderScheduler: FakeReminderScheduler
    private lateinit var sessionProvider: FakeUserSessionProvider
    private lateinit var logger: IronLogger

    // Use Cases
    private lateinit var createGoalUseCase: CreateGoalUseCase
    private lateinit var getGoalUseCase: GetGoalUseCase
    private lateinit var editGoalUseCase: EditGoalUseCase
    private lateinit var updateGoalStatusUseCase: UpdateGoalStatusUseCase
    private lateinit var getGoalProgressUseCase: GetGoalProgressUseCase

    private lateinit var createPlanUseCase: CreatePlanUseCase
    private lateinit var getPlanUseCase: GetPlanUseCase
    private lateinit var getPlansUseCase: GetPlansUseCase
    private lateinit var editPlanUseCase: EditPlanUseCase
    private lateinit var updatePlanStatusUseCase: UpdatePlanStatusUseCase
    private lateinit var getPlanProgressUseCase: GetPlanProgressUseCase

    private lateinit var createTaskUseCase: CreateTaskUseCase
    private lateinit var getTasksUseCase: GetTasksUseCase
    private lateinit var editTaskUseCase: EditTaskUseCase
    private lateinit var updateTaskStatusUseCase: UpdateTaskStatusUseCase
    private lateinit var deleteTaskUseCase: DeleteTaskUseCase
    private lateinit var getDueTasksUseCase: GetDueTasksUseCase

    private lateinit var createCommitmentUseCase: CreateCommitmentUseCase
    private lateinit var getActiveCommitmentsUseCase: GetActiveCommitmentsUseCase
    private lateinit var updateCommitmentStatusUseCase: UpdateCommitmentStatusUseCase
    private lateinit var getTimelineUseCase: GetTimelineUseCase

    private val zoneId = ZoneId.of("UTC")
    private val fixedDate = LocalDate.of(2026, 11, 6)
    private val referenceTime = fixedDate.atTime(12, 0, 0).atZone(zoneId).toInstant().toEpochMilli()
    private val startOfToday = fixedDate.atStartOfDay(zoneId).toInstant().toEpochMilli()
    private val startOfTomorrow = fixedDate.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli()

    private class DelegatingFailingOutboxDao(private val delegate: OutboxDao) : OutboxDao by delegate {
        var shouldFail: Boolean = false
        override fun insertEntry(entry: OutboxEntity) {
            if (shouldFail) {
                throw android.database.sqlite.SQLiteException("Simulated outbox persistence failure")
            }
            delegate.insertEntry(entry)
        }
    }

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        val directExecutor = java.util.concurrent.Executor { it.run() }
        db = Room.inMemoryDatabaseBuilder(context, IronMindDatabase::class.java)
            .allowMainThreadQueries()
            .setQueryExecutor(directExecutor)
            .setTransactionExecutor(directExecutor)
            .build()

        delegatingOutboxDao = DelegatingFailingOutboxDao(db.outboxDao())
        clock = FakeClock(referenceTime)
        idGenerator = FakeIdGenerator("test-")
        reminderScheduler = FakeReminderScheduler()
        sessionProvider = FakeUserSessionProvider(SessionState.Authenticated("user-1", isAnonymous = false))

        logger = object : IronLogger {
            override fun logLifecycle(component: String, event: String, parameters: Map<String, Any?>) {}
        }

        goalRepository = GoalRepositoryImpl(
            dao = db.ironMindDao(),
            outboxDao = delegatingOutboxDao,
            database = db,
            idGenerator = idGenerator
        )
        planRepository = PlanRepositoryImpl(
            dao = db.ironMindDao(),
            outboxDao = delegatingOutboxDao,
            database = db,
            idGenerator = idGenerator
        )
        taskRepository = TaskRepositoryImpl(
            dao = db.ironMindDao(),
            outboxDao = delegatingOutboxDao,
            database = db,
            idGenerator = idGenerator
        )
        commitmentRepository = CommitmentRepositoryImpl(
            dao = db.ironMindDao(),
            outboxDao = delegatingOutboxDao,
            database = db,
            idGenerator = idGenerator
        )
        eventRepository = EventRepositoryImpl(
            dao = db.ironMindDao(),
            database = db
        )
        outcomeRepository = OutcomeRepositoryImpl(dao = db.ironMindDao())
        reflectionRepository = ReflectionRepositoryImpl(
            dao = db.ironMindDao(),
            outboxDao = delegatingOutboxDao,
            database = db,
            idGenerator = idGenerator
        )

        // Wire Use Cases
        createGoalUseCase = CreateGoalUseCase(goalRepository, idGenerator, clock, eventRepository)
        getGoalUseCase = GetGoalUseCase(goalRepository)
        editGoalUseCase = EditGoalUseCase(goalRepository, clock, idGenerator, eventRepository)
        updateGoalStatusUseCase = UpdateGoalStatusUseCase(goalRepository, clock)
        getGoalProgressUseCase = GetGoalProgressUseCase(planRepository, taskRepository)

        createPlanUseCase = CreatePlanUseCase(planRepository, idGenerator, clock, eventRepository)
        getPlanUseCase = GetPlanUseCase(planRepository)
        getPlansUseCase = GetPlansUseCase(planRepository)
        editPlanUseCase = EditPlanUseCase(planRepository, clock, idGenerator, eventRepository)
        updatePlanStatusUseCase = UpdatePlanStatusUseCase(planRepository, clock, idGenerator, eventRepository)
        getPlanProgressUseCase = GetPlanProgressUseCase(taskRepository)

        createTaskUseCase = CreateTaskUseCase(taskRepository, idGenerator, clock, eventRepository)
        getTasksUseCase = GetTasksUseCase(taskRepository)
        editTaskUseCase = EditTaskUseCase(taskRepository, clock, eventRepository, idGenerator)
        updateTaskStatusUseCase = UpdateTaskStatusUseCase(taskRepository, clock, eventRepository, idGenerator)
        deleteTaskUseCase = DeleteTaskUseCase(taskRepository)
        getDueTasksUseCase = GetDueTasksUseCase(taskRepository, planRepository, clock)

        createCommitmentUseCase = CreateCommitmentUseCase(
            repository = commitmentRepository,
            reminderScheduler = reminderScheduler,
            idGenerator = idGenerator,
            clock = clock,
            eventRepository = eventRepository
        )
        getActiveCommitmentsUseCase = GetActiveCommitmentsUseCase(commitmentRepository)
        updateCommitmentStatusUseCase = UpdateCommitmentStatusUseCase(
            repository = commitmentRepository,
            outcomeRepository = outcomeRepository,
            reminderScheduler = reminderScheduler,
            clock = clock,
            idGenerator = idGenerator,
            eventRepository = eventRepository,
            taskRepository = taskRepository
        )
        getTimelineUseCase = GetTimelineUseCase(
            eventRepository = eventRepository,
            commitmentRepository = commitmentRepository,
            reflectionRepository = reflectionRepository,
            goalRepository = goalRepository,
            planRepository = planRepository,
            taskRepository = taskRepository,
            logger = logger
        )
    }

    @After
    fun tearDown() {
        delegatingOutboxDao.shouldFail = false
        db.close()
    }

    // =========================================================================
    // Journey A — Goal to Task
    // =========================================================================

    @Test
    fun testJourneyA_goalToTaskCreationAndProgressVerification() = kotlinx.coroutines.runBlocking {
        // 1. Create a Goal
        val goalResult = createGoalUseCase(
            userId = "user-1",
            title = "Run Half Marathon",
            description = "Train for upcoming half marathon",
            why = "Build endurance and health",
            importance = 8,
            targetAt = referenceTime + 30L * 86400000L
        )
        assertTrue("Goal creation must succeed", goalResult is Result.Success)
        val goal = (goalResult as Result.Success).data
        val goalId = goal.id

        // Verify Room persistence & Outbox staging
        val savedGoalInDb = db.ironMindDao().getGoal(goalId)
        assertNotNull(savedGoalInDb)
        assertEquals("Run Half Marathon", savedGoalInDb!!.title)
        val goalOutbox = db.outboxDao().getPendingEntries().find { it.entityId == goalId }
        assertNotNull(goalOutbox)
        assertEquals("Goal", goalOutbox!!.entityType)
        assertEquals("UPSERT", goalOutbox.operationType)

        // 2. Create a Plan under the Goal
        val planResult = createPlanUseCase(
            userId = "user-1",
            goalId = goalId,
            title = "12-Week Half Marathon Training",
            description = "Progressive mileage buildup"
        )
        assertTrue("Plan creation must succeed", planResult is Result.Success)
        val plan = (planResult as Result.Success).data
        val planId = plan.id

        // Verify Plan Room persistence & Outbox staging
        val savedPlanInDb = db.ironMindDao().getPlan(planId)
        assertNotNull(savedPlanInDb)
        assertEquals(goalId, savedPlanInDb!!.goalId)
        val planOutbox = db.outboxDao().getPendingEntries().find { it.entityId == planId }
        assertNotNull(planOutbox)
        assertEquals("Plan", planOutbox!!.entityType)

        // 3. Create multiple Tasks
        // Task 1: Due today
        val task1Result = createTaskUseCase(
            userId = "user-1",
            goalId = goalId,
            planId = planId,
            title = "Easy 5K Base Run",
            description = "Zone 2 aerobic run",
            priority = 2,
            estimatedDurationMinutes = 35,
            dueAt = startOfToday + 3600_000L
        )
        assertTrue(task1Result is Result.Success)
        val task1 = (task1Result as Result.Success).data

        // Task 2: Due tomorrow
        val task2Result = createTaskUseCase(
            userId = "user-1",
            goalId = goalId,
            planId = planId,
            title = "Interval Speed Session",
            description = "6x 400m intervals",
            priority = 4,
            estimatedDurationMinutes = 45,
            dueAt = startOfTomorrow + 3600_000L
        )
        assertTrue(task2Result is Result.Success)
        val task2 = (task2Result as Result.Success).data

        // Task 3: No due date
        val task3Result = createTaskUseCase(
            userId = "user-1",
            goalId = goalId,
            planId = planId,
            title = "Recovery Foam Rolling",
            description = "Leg mobility and recovery",
            priority = 1,
            estimatedDurationMinutes = 20,
            dueAt = null
        )
        assertTrue(task3Result is Result.Success)
        val task3 = (task3Result as Result.Success).data

        // 4. Verify tasks appear correctly in PlanDetailViewModel
        val planDetailViewModel = PlanDetailViewModel(
            getPlanUseCase = getPlanUseCase,
            getTasksUseCase = getTasksUseCase,
            createTaskUseCase = createTaskUseCase,
            getActiveCommitmentsUseCase = getActiveCommitmentsUseCase,
            createCommitmentUseCase = createCommitmentUseCase,
            logger = logger,
            userSessionProvider = sessionProvider,
            editTaskUseCase = editTaskUseCase,
            updateTaskStatusUseCase = updateTaskStatusUseCase,
            deleteTaskUseCase = deleteTaskUseCase,
            getPlanProgressUseCase = getPlanProgressUseCase
        )

        planDetailViewModel.loadData(planId)
        val uiState = withTimeout(3000L) {
            planDetailViewModel.uiState.first { it !is PlanDetailUiState.Loading }
        }
        assertTrue("UI state must be Success, but was: ${(uiState as? PlanDetailUiState.Error)?.message ?: uiState}", uiState is PlanDetailUiState.Success)
        val successState = uiState as PlanDetailUiState.Success
        assertEquals(3, successState.tasks.size)
        assertEquals(startOfToday + 3600_000L, successState.tasks.find { it.id == task1.id }?.dueAt)
        assertEquals(startOfTomorrow + 3600_000L, successState.tasks.find { it.id == task2.id }?.dueAt)
        assertNull(successState.tasks.find { it.id == task3.id }?.dueAt)

        // 5. Verify Goal and Plan progress
        val planProgress = (getPlanProgressUseCase(planId, "user-1") as Result.Success).data
        assertEquals(0, planProgress.completedTasks)
        assertEquals(3, planProgress.totalTasks)
        assertEquals(3, planProgress.pendingTasks)
        assertEquals(0, planProgress.percentage)
        assertEquals(0f, planProgress.progressFraction, 0.001f)

        val goalProgress = (getGoalProgressUseCase(goalId, "user-1") as Result.Success).data
        assertEquals(0, goalProgress.completedTasks)
        assertEquals(3, goalProgress.totalTasks)
        assertEquals(3, goalProgress.pendingTasks)
        assertEquals(0, goalProgress.percentage)
    }

    // =========================================================================
    // Journey B — Task Lifecycle
    // =========================================================================

    @Test
    fun testJourneyB_taskLifecycleAndTombstoneStaging() = runTest {
        // Setup: Goal, Plan, 3 Tasks
        val goal = (createGoalUseCase("user-1", "Fitness", "Desc", "Why", 5, null) as Result.Success).data
        val plan = (createPlanUseCase("user-1", goal.id, "Plan 1", "Desc") as Result.Success).data
        val t1 = (createTaskUseCase("user-1", goal.id, plan.id, "Task 1", "D1", 1, dueAt = startOfToday) as Result.Success).data
        val t2 = (createTaskUseCase("user-1", goal.id, plan.id, "Task 2", "D2", 2, dueAt = startOfTomorrow) as Result.Success).data
        val t3 = (createTaskUseCase("user-1", goal.id, plan.id, "Task 3", "D3", 3) as Result.Success).data

        // 1. Edit a Task
        clock.setTime(referenceTime + 1000L)
        val editResult = editTaskUseCase(
            taskId = t1.id,
            title = "Task 1 Renamed",
            description = "Updated description",
            priority = 4,
            dueAt = startOfToday + 7200_000L
        )
        assertTrue(editResult is Result.Success)
        val editedT1 = (taskRepository.getTask(t1.id) as Result.Success).data!!
        assertEquals("Task 1 Renamed", editedT1.title)
        assertEquals(4, editedT1.priority)
        assertEquals(startOfToday + 7200_000L, editedT1.dueAt)

        // 2. Complete a Task
        clock.setTime(referenceTime + 2000L)
        val completeResult = updateTaskStatusUseCase(t1.id, TaskStatus.COMPLETED)
        assertTrue(completeResult is Result.Success)
        val completedT1 = (taskRepository.getTask(t1.id) as Result.Success).data!!
        assertEquals(TaskStatus.COMPLETED, completedT1.status)
        assertEquals(referenceTime + 2000L, completedT1.completedAt)

        // Verify progress recalculation: 1/3 (33%)
        val progressAfterComplete = (getPlanProgressUseCase(plan.id, "user-1") as Result.Success).data
        assertEquals(1, progressAfterComplete.completedTasks)
        assertEquals(3, progressAfterComplete.totalTasks)
        assertEquals(2, progressAfterComplete.pendingTasks)
        assertEquals(33, progressAfterComplete.percentage)

        // 3. Reopen or change status of active task
        clock.setTime(referenceTime + 3000L)
        val inProgressResult = updateTaskStatusUseCase(t2.id, TaskStatus.IN_PROGRESS)
        assertTrue(inProgressResult is Result.Success)
        val inProgT2 = (taskRepository.getTask(t2.id) as Result.Success).data!!
        assertEquals(TaskStatus.IN_PROGRESS, inProgT2.status)

        // Invalid transition on terminal COMPLETED task must be rejected
        val invalidTransition = updateTaskStatusUseCase(t1.id, TaskStatus.PENDING)
        assertTrue("Transition from COMPLETED to PENDING must be rejected", invalidTransition is Result.Failure)

        // 4. Delete a Task (Task 3)
        // Clear earlier outbox entries to specifically inspect delete tombstone
        db.outboxDao().getAllEntries().forEach { db.outboxDao().deleteEntry(it.operationId) }
        assertEquals(0, db.outboxDao().getAllEntries().size)

        val deleteResult = deleteTaskUseCase("user-1", t3.id)
        assertTrue("deleteTask must return Success", deleteResult is Result.Success)

        // 5. Verify progress recalculates correctly: now 1 completed out of 2 total tasks = 50%
        val progressAfterDelete = (getPlanProgressUseCase(plan.id, "user-1") as Result.Success).data
        assertEquals(1, progressAfterDelete.completedTasks)
        assertEquals(2, progressAfterDelete.totalTasks)
        assertEquals(1, progressAfterDelete.pendingTasks)
        assertEquals(50, progressAfterDelete.percentage)

        // 6. Verify deleted Task does not reappear in active UI
        val activeTasksResult = getTasksUseCase(plan.id)
        assertTrue(activeTasksResult is Result.Success)
        val activeTasks = (activeTasksResult as Result.Success).data
        assertEquals(2, activeTasks.size)
        assertFalse("Deleted task 3 must not appear in active tasks", activeTasks.any { it.id == t3.id })

        // 7. Verify deletion Outbox tombstone is staged atomically
        val outboxEntries = db.outboxDao().getPendingEntries()
        assertEquals(1, outboxEntries.size)
        val deleteOutbox = outboxEntries.first()
        assertEquals("Task", deleteOutbox.entityType)
        assertEquals(t3.id, deleteOutbox.entityId)
        assertEquals("user-1", deleteOutbox.userId)
        assertEquals(OutboxOperationType.DELETE.name, deleteOutbox.operationType)
        assertEquals(SyncStatus.PENDING.name, deleteOutbox.status)

        val payload = JSONObject(deleteOutbox.payload)
        assertEquals(t3.id, payload.getString("id"))
        assertEquals("user-1", payload.getString("userId"))
        assertTrue("Tombstone must have isDeleted true", payload.getBoolean("isDeleted"))
        assertTrue("Tombstone must have valid deletedAt", payload.getLong("deletedAt") > 0)
    }

    // =========================================================================
    // Journey C — Plan and Goal Lifecycle
    // =========================================================================

    @Test
    fun testJourneyC_planAndGoalLifecyclePreservesTasksAndProgress() = runTest {
        // Setup Goal, Plan, and 2 Tasks (one completed, one pending = 50% progress)
        val goal = (createGoalUseCase("user-1", "Mastery", "Desc", "Why", 9, null) as Result.Success).data
        val plan = (createPlanUseCase("user-1", goal.id, "Core Plan", "Desc") as Result.Success).data
        val t1 = (createTaskUseCase("user-1", goal.id, plan.id, "Task A", "Desc", 1) as Result.Success).data
        val t2 = (createTaskUseCase("user-1", goal.id, plan.id, "Task B", "Desc", 2) as Result.Success).data
        updateTaskStatusUseCase(t1.id, TaskStatus.COMPLETED)

        // Verify baseline progress is 50%
        val baselineProgress = (getPlanProgressUseCase(plan.id, "user-1") as Result.Success).data
        assertEquals(50, baselineProgress.percentage)

        // 1. Pause a Plan
        clock.setTime(referenceTime + 1000L)
        val pausePlanResult = updatePlanStatusUseCase("user-1", plan.id, PlanStatus.PAUSED)
        assertTrue(pausePlanResult is Result.Success)
        val pausedPlan = (planRepository.getPlan(plan.id) as Result.Success).data!!
        assertEquals(PlanStatus.PAUSED, pausedPlan.status)

        // Verify tasks and progress are preserved when plan is paused
        val progressWhilePaused = (getPlanProgressUseCase(plan.id, "user-1") as Result.Success).data
        assertEquals(1, progressWhilePaused.completedTasks)
        assertEquals(2, progressWhilePaused.totalTasks)
        assertEquals(50, progressWhilePaused.percentage)

        // 2. Reactivate Plan
        clock.setTime(referenceTime + 2000L)
        val reactivatePlanResult = updatePlanStatusUseCase("user-1", plan.id, PlanStatus.ACTIVE)
        assertTrue(reactivatePlanResult is Result.Success)
        val activePlan = (planRepository.getPlan(plan.id) as Result.Success).data!!
        assertEquals(PlanStatus.ACTIVE, activePlan.status)

        // 3. Complete Plan
        clock.setTime(referenceTime + 3000L)
        val completePlanResult = updatePlanStatusUseCase("user-1", plan.id, PlanStatus.COMPLETED)
        assertTrue(completePlanResult is Result.Success)
        val completedPlan = (planRepository.getPlan(plan.id) as Result.Success).data!!
        assertEquals(PlanStatus.COMPLETED, completedPlan.status)
        assertEquals(referenceTime + 3000L, completedPlan.completedAt)

        // 4. Reactivate Plan from COMPLETED
        clock.setTime(referenceTime + 4000L)
        val reactivateFromCompletedResult = updatePlanStatusUseCase("user-1", plan.id, PlanStatus.ACTIVE)
        assertTrue(reactivateFromCompletedResult is Result.Success)
        val reactivatedPlan = (planRepository.getPlan(plan.id) as Result.Success).data!!
        assertEquals(PlanStatus.ACTIVE, reactivatedPlan.status)
        assertNull(reactivatedPlan.completedAt)

        // 5. Repeat applicable lifecycle actions for Goal
        // Pause Goal
        clock.setTime(referenceTime + 5000L)
        val pauseGoalResult = updateGoalStatusUseCase(goal.id, GoalStatus.PAUSED)
        assertTrue(pauseGoalResult is Result.Success)
        val pausedGoal = (goalRepository.getGoal(goal.id) as Result.Success).data!!
        assertEquals(GoalStatus.PAUSED, pausedGoal.status)

        // Reactivate Goal
        clock.setTime(referenceTime + 6000L)
        val reactivateGoalResult = updateGoalStatusUseCase(goal.id, GoalStatus.ACTIVE)
        assertTrue(reactivateGoalResult is Result.Success)
        val activeGoal = (goalRepository.getGoal(goal.id) as Result.Success).data!!
        assertEquals(GoalStatus.ACTIVE, activeGoal.status)

        // Complete Goal
        clock.setTime(referenceTime + 7000L)
        val completeGoalResult = updateGoalStatusUseCase(goal.id, GoalStatus.COMPLETED)
        assertTrue(completeGoalResult is Result.Success)
        val completedGoal = (goalRepository.getGoal(goal.id) as Result.Success).data!!
        assertEquals(GoalStatus.COMPLETED, completedGoal.status)
        assertEquals(referenceTime + 7000L, completedGoal.completedAt)

        // Reactivate Goal from COMPLETED
        clock.setTime(referenceTime + 8000L)
        val reactivateGoalFromCompleted = updateGoalStatusUseCase(goal.id, GoalStatus.ACTIVE)
        assertTrue(reactivateGoalFromCompleted is Result.Success)
        val reactivatedGoal = (goalRepository.getGoal(goal.id) as Result.Success).data!!
        assertEquals(GoalStatus.ACTIVE, reactivatedGoal.status)
        assertNull(reactivatedGoal.completedAt)

        // 6. Verify associated tasks and progress remain preserved across all transitions
        val finalPlanProgress = (getPlanProgressUseCase(plan.id, "user-1") as Result.Success).data
        assertEquals(1, finalPlanProgress.completedTasks)
        assertEquals(2, finalPlanProgress.totalTasks)
        assertEquals(1, finalPlanProgress.pendingTasks)
        assertEquals(50, finalPlanProgress.percentage)

        val finalGoalProgress = (getGoalProgressUseCase(goal.id, "user-1") as Result.Success).data
        assertEquals(1, finalGoalProgress.completedTasks)
        assertEquals(2, finalGoalProgress.totalTasks)
        assertEquals(1, finalGoalProgress.pendingTasks)
        assertEquals(50, finalGoalProgress.percentage)
    }

    // =========================================================================
    // Journey D — Today Commitments
    // =========================================================================

    @Test
    fun testJourneyD_todayCommitmentsAndHistoryEvents() = runTest {
        // 1. Create a standalone commitment directly from Today
        val comm1Result = createCommitmentUseCase(
            userId = "user-1",
            goalId = null,
            planId = null,
            taskId = null,
            parentCommitmentId = null,
            title = "Morning Meditation",
            description = "15 mins mindful breath",
            priority = 2,
            initialStatus = CommitmentStatus.COMMITTED
        )
        assertTrue(comm1Result is Result.Success)
        val comm1 = (comm1Result as Result.Success).data

        // 2. Verify Room persistence and Outbox staging
        val savedComm1InDb = db.ironMindDao().getCommitment(comm1.id)
        assertNotNull(savedComm1InDb)
        assertEquals("Morning Meditation", savedComm1InDb!!.title)
        assertNull(savedComm1InDb.goalId)
        assertNull(savedComm1InDb.planId)
        assertNull(savedComm1InDb.taskId)

        val comm1Outbox = db.outboxDao().getPendingEntries().find { it.entityId == comm1.id }
        assertNotNull(comm1Outbox)
        assertEquals("Commitment", comm1Outbox!!.entityType)
        assertEquals(OutboxOperationType.UPSERT.name, comm1Outbox.operationType)

        // 3. Mark it COMPLETED (advance to STARTED first as required by lifecycle)
        clock.setTime(referenceTime + 500L)
        val startResult = updateCommitmentStatusUseCase(comm1.id, CommitmentStatus.STARTED)
        assertTrue(startResult is Result.Success)

        clock.setTime(referenceTime + 1000L)
        val completeResult = updateCommitmentStatusUseCase(comm1.id, CommitmentStatus.COMPLETED)
        assertTrue(completeResult is Result.Success)
        val completedComm1 = (commitmentRepository.getCommitment(comm1.id) as Result.Success).data!!
        assertEquals(CommitmentStatus.COMPLETED, completedComm1.status)
        assertEquals(referenceTime + 1000L, completedComm1.completedAt)

        // Verify Event recorded
        val eventsForComm1 = (eventRepository.getEventsForEntity(comm1.id) as Result.Success).data
        assertTrue(eventsForComm1.any { it.type == EventType.COMMITMENT_COMPLETED })

        // 4. Create another commitment and mark it MISSED
        val comm2Result = createCommitmentUseCase(
            userId = "user-1",
            goalId = null,
            planId = null,
            taskId = null,
            parentCommitmentId = null,
            title = "Gym Session",
            description = "Weights workout",
            priority = 3,
            initialStatus = CommitmentStatus.COMMITTED
        )
        assertTrue(comm2Result is Result.Success)
        val comm2 = (comm2Result as Result.Success).data

        clock.setTime(referenceTime + 2000L)
        val missedResult = updateCommitmentStatusUseCase(comm2.id, CommitmentStatus.MISSED)
        assertTrue(missedResult is Result.Success)
        val missedComm2 = (commitmentRepository.getCommitment(comm2.id) as Result.Success).data!!
        assertEquals(CommitmentStatus.MISSED, missedComm2.status)
        assertEquals(referenceTime + 2000L, missedComm2.missedAt)

        // 5. Create another commitment and POSTPONE it
        val comm3Result = createCommitmentUseCase(
            userId = "user-1",
            goalId = null,
            planId = null,
            taskId = null,
            parentCommitmentId = null,
            title = "Read 20 pages",
            description = "Architecture book",
            priority = 1,
            initialStatus = CommitmentStatus.COMMITTED
        )
        assertTrue(comm3Result is Result.Success)
        val comm3 = (comm3Result as Result.Success).data

        clock.setTime(referenceTime + 3000L)
        val postponeResult = updateCommitmentStatusUseCase(comm3.id, CommitmentStatus.POSTPONED)
        assertTrue(postponeResult is Result.Success)
        val postponedComm3 = (commitmentRepository.getCommitment(comm3.id) as Result.Success).data!!
        assertEquals(CommitmentStatus.POSTPONED, postponedComm3.status)
        assertEquals(referenceTime + 3000L, postponedComm3.postponedAt)

        // 6. Verify Today UI and History events consistency
        val activeCommitments = (getActiveCommitmentsUseCase("user-1") as Result.Success).data
        // Active commitments include COMMITTED, STARTED, but NOT COMPLETED, MISSED, or POSTPONED
        assertTrue("No active commitments remain after completion, missed, and postponement", activeCommitments.isEmpty())

        // Create an active commitment and verify it appears in active commitments
        val comm4 = (createCommitmentUseCase(
            userId = "user-1",
            goalId = null,
            planId = null,
            taskId = null,
            parentCommitmentId = null,
            title = "Active Standby",
            description = "Desc",
            priority = 1,
            initialStatus = CommitmentStatus.COMMITTED
        ) as Result.Success).data

        val activeNow = (getActiveCommitmentsUseCase("user-1") as Result.Success).data
        assertEquals(1, activeNow.size)
        assertEquals(comm4.id, activeNow.first().id)

        // Verify History timeline reflects the events
        val timelineResult = getTimelineUseCase("user-1")
        assertTrue(timelineResult is Result.Success)
        val timelineItems = (timelineResult as Result.Success).data
        // Verify all 3 commitments are represented in timeline items
        val commitmentEvents = timelineItems.filterIsInstance<TimelineItem.CommitmentEvent>()
        assertTrue(commitmentEvents.any { it.commitmentTitle == "Morning Meditation" })
        assertTrue(commitmentEvents.any { it.commitmentTitle == "Gym Session" })
        assertTrue(commitmentEvents.any { it.commitmentTitle == "Read 20 pages" })
    }

    // =========================================================================
    // Journey E — Due and Overdue Tasks
    // =========================================================================

    @Test
    fun testJourneyE_dueAndOverdueTasksCategorizationAndNavigation() = runTest {
        val plan = (createPlanUseCase("user-1", null, "Master Plan", "Plan Desc") as Result.Success).data

        // 1. Overdue task (due yesterday)
        val overdueTask = (createTaskUseCase(
            userId = "user-1",
            goalId = null,
            planId = plan.id,
            title = "Overdue Report",
            description = "Past due",
            priority = 3,
            dueAt = startOfToday - 3600_000L // 1 hour before start of today
        ) as Result.Success).data

        // 2. Task due today
        val dueTodayTask = (createTaskUseCase(
            userId = "user-1",
            goalId = null,
            planId = plan.id,
            title = "Due Today Task",
            description = "Due today afternoon",
            priority = 2,
            dueAt = startOfToday + 3600_000L
        ) as Result.Success).data

        // 3. Future task (due tomorrow)
        val futureTask = (createTaskUseCase(
            userId = "user-1",
            goalId = null,
            planId = plan.id,
            title = "Future Task",
            description = "Due tomorrow",
            priority = 1,
            dueAt = startOfTomorrow + 3600_000L
        ) as Result.Success).data

        // 4. Completed task with past due date (must be excluded)
        val completedPastTask = (createTaskUseCase(
            userId = "user-1",
            goalId = null,
            planId = plan.id,
            title = "Completed Past Task",
            description = "Already done",
            priority = 1,
            dueAt = startOfToday - 7200_000L
        ) as Result.Success).data
        updateTaskStatusUseCase(completedPastTask.id, TaskStatus.COMPLETED)

        // 5. Cancelled task with today's due date (must be excluded)
        val cancelledTask = (createTaskUseCase(
            userId = "user-1",
            goalId = null,
            planId = plan.id,
            title = "Cancelled Task",
            description = "Won't do",
            priority = 1,
            dueAt = startOfToday + 1800_000L
        ) as Result.Success).data
        updateTaskStatusUseCase(cancelledTask.id, TaskStatus.CANCELLED)

        // Query GetDueTasksUseCase with test reference time and UTC zone
        val dueTasksResult = getDueTasksUseCase("user-1", referenceTime, zoneId)
        assertTrue(dueTasksResult is Result.Success)
        val dueResult = (dueTasksResult as Result.Success).data

        // Verify Overdue list: contains only the active overdue task
        assertEquals(1, dueResult.overdue.size)
        assertEquals(overdueTask.id, dueResult.overdue[0].task.id)
        assertEquals("Master Plan", dueResult.overdue[0].planTitle)
        assertEquals(plan.id, dueResult.overdue[0].task.planId)

        // Verify Due Today list: contains only the active due today task
        assertEquals(1, dueResult.dueToday.size)
        assertEquals(dueTodayTask.id, dueResult.dueToday[0].task.id)
        assertEquals("Master Plan", dueResult.dueToday[0].planTitle)
        assertEquals(plan.id, dueResult.dueToday[0].task.planId)

        // Verify Future, Completed, and Cancelled tasks are excluded
        assertFalse(dueResult.overdue.any { it.task.id == futureTask.id })
        assertFalse(dueResult.dueToday.any { it.task.id == futureTask.id })
        assertFalse(dueResult.overdue.any { it.task.id == completedPastTask.id })
        assertFalse(dueResult.dueToday.any { it.task.id == completedPastTask.id })
        assertFalse(dueResult.overdue.any { it.task.id == cancelledTask.id })
        assertFalse(dueResult.dueToday.any { it.task.id == cancelledTask.id })
    }

    // =========================================================================
    // Data Integrity Checks
    // =========================================================================

    @Test
    fun testDataIntegrity_atomicityAndRollbackOnOutboxFailure() = runTest {
        delegatingOutboxDao.shouldFail = true

        // 1. Task creation rolls back
        val taskResult = createTaskUseCase("user-1", null, null, "Will Fail", "Desc", 1)
        assertTrue("Task creation must fail on outbox error", taskResult is Result.Failure)
        assertTrue("No task persisted in Room", db.ironMindDao().getTasksForUser("user-1").isEmpty())

        // 2. Goal creation rolls back
        val goalResult = createGoalUseCase("user-1", "Will Fail Goal", "Desc", "Why", 5, null)
        assertTrue("Goal creation must fail on outbox error", goalResult is Result.Failure)
        assertTrue("No goal persisted in Room", db.ironMindDao().getGoalsForUser("user-1").isEmpty())

        // 3. Plan creation rolls back
        val planResult = createPlanUseCase("user-1", null, "Will Fail Plan", "Desc")
        assertTrue("Plan creation must fail on outbox error", planResult is Result.Failure)
        assertTrue("No plan persisted in Room", db.ironMindDao().getPlansForUser("user-1").isEmpty())

        // 4. Commitment creation rolls back
        val commResult = createCommitmentUseCase(
            userId = "user-1",
            goalId = null,
            planId = null,
            taskId = null,
            parentCommitmentId = null,
            title = "Will Fail Comm",
            description = "Desc",
            priority = 1
        )
        assertTrue("Commitment creation must fail on outbox error", commResult is Result.Failure)
        assertTrue("No commitment persisted in Room", db.ironMindDao().getCommitmentsForUser("user-1").isEmpty())

        // Re-enable outbox for subsequent steps
        delegatingOutboxDao.shouldFail = false

        // Create an existing task in Room
        val existingTask = (createTaskUseCase("user-1", null, null, "Existing Task", "Original", 2) as Result.Success).data
        db.outboxDao().getAllEntries().forEach { db.outboxDao().deleteEntry(it.operationId) }

        // 5. Task deletion rolls back on outbox failure
        delegatingOutboxDao.shouldFail = true
        val deleteResult = taskRepository.deleteTask("user-1", existingTask.id)
        assertTrue("deleteTask must fail on outbox error", deleteResult is Result.Failure)
        assertNotNull("Task must NOT be deleted from Room", db.ironMindDao().getTask(existingTask.id))

        delegatingOutboxDao.shouldFail = false
    }

    @Test
    fun testDataIntegrity_taskStatusChangePreservesUnrelatedFields() = runTest {
        val task = (createTaskUseCase(
            userId = "user-1",
            goalId = "goal-x",
            planId = "plan-y",
            title = "Core Architecture",
            description = "Detailed specifications",
            priority = 5,
            estimatedDurationMinutes = 90,
            scheduledAt = 5000L,
            dueAt = 10000L
        ) as Result.Success).data

        // Advance to IN_PROGRESS
        clock.setTime(2000L)
        updateTaskStatusUseCase(task.id, TaskStatus.IN_PROGRESS)

        val inProgressTask = (taskRepository.getTask(task.id) as Result.Success).data!!
        assertEquals("Core Architecture", inProgressTask.title)
        assertEquals("Detailed specifications", inProgressTask.description)
        assertEquals(5, inProgressTask.priority)
        assertEquals(90, inProgressTask.estimatedDurationMinutes)
        assertEquals(5000L, inProgressTask.scheduledAt)
        assertEquals(10000L, inProgressTask.dueAt)
        assertEquals("goal-x", inProgressTask.goalId)
        assertEquals("plan-y", inProgressTask.planId)
        assertEquals(task.createdAt, inProgressTask.createdAt)

        // Advance to COMPLETED
        clock.setTime(3000L)
        updateTaskStatusUseCase(task.id, TaskStatus.COMPLETED)

        val completedTask = (taskRepository.getTask(task.id) as Result.Success).data!!
        assertEquals("Core Architecture", completedTask.title)
        assertEquals("Detailed specifications", completedTask.description)
        assertEquals(5, completedTask.priority)
        assertEquals(90, completedTask.estimatedDurationMinutes)
        assertEquals(5000L, completedTask.scheduledAt)
        assertEquals(10000L, completedTask.dueAt)
        assertEquals("goal-x", completedTask.goalId)
        assertEquals("plan-y", completedTask.planId)
        assertEquals(task.createdAt, completedTask.createdAt)
        assertEquals(3000L, completedTask.completedAt)
    }

    @Test
    fun testDataIntegrity_goalProgressAggregatesDirectAndPlanTasksWithoutDuplicates() = runTest {
        val goal = (createGoalUseCase("user-1", "Holistic Health", "Desc", "Why", 10, null) as Result.Success).data
        val plan1 = (createPlanUseCase("user-1", goal.id, "Workout Plan", "P1") as Result.Success).data
        val plan2 = (createPlanUseCase("user-1", goal.id, "Nutrition Plan", "P2") as Result.Success).data

        // Plan 1 has Task A and Task B
        // Task A has both planId and goalId populated
        val taskA = (createTaskUseCase("user-1", goal.id, plan1.id, "Task A", "D", 1) as Result.Success).data
        val taskB = (createTaskUseCase("user-1", null, plan1.id, "Task B", "D", 1) as Result.Success).data

        // Plan 2 has Task C
        val taskC = (createTaskUseCase("user-1", null, plan2.id, "Task C", "D", 1) as Result.Success).data

        // Goal has direct Task D (linked directly to goal, no plan)
        val taskD = (createTaskUseCase("user-1", goal.id, null, "Task D", "D", 1) as Result.Success).data

        // Mark Task A and Task D as completed
        updateTaskStatusUseCase(taskA.id, TaskStatus.COMPLETED)
        updateTaskStatusUseCase(taskD.id, TaskStatus.COMPLETED)

        // Compute Goal progress
        val goalProgress = (getGoalProgressUseCase(goal.id, "user-1") as Result.Success).data

        // Total distinct tasks: 4 (A, B, C, D). Completed: 2 (A, D). Pending: 2 (B, C). Percentage: 50%
        assertEquals(2, goalProgress.completedTasks)
        assertEquals(4, goalProgress.totalTasks)
        assertEquals(2, goalProgress.pendingTasks)
        assertEquals(50, goalProgress.percentage)
        assertEquals(0.5f, goalProgress.progressFraction, 0.001f)
    }

    @Test
    fun testDataIntegrity_userOwnershipIsolationAndSessionStaleness() = kotlinx.coroutines.runBlocking {
        // User Alice data
        val goalAlice = (createGoalUseCase("user-alice", "Alice Goal", "Desc", "Why", 5, null) as Result.Success).data
        val planAlice = (createPlanUseCase("user-alice", goalAlice.id, "Alice Plan", "Desc") as Result.Success).data
        val taskAlice = (createTaskUseCase("user-alice", goalAlice.id, planAlice.id, "Alice Task", "Desc", 1) as Result.Success).data
        val commAlice = (createCommitmentUseCase("user-alice", null, null, null, null, "Alice Comm", "Desc", 1, initialStatus = CommitmentStatus.COMMITTED) as Result.Success).data

        // User Bob data
        val goalBob = (createGoalUseCase("user-bob", "Bob Goal", "Desc", "Why", 5, null) as Result.Success).data
        val planBob = (createPlanUseCase("user-bob", goalBob.id, "Bob Plan", "Desc") as Result.Success).data
        val taskBob = (createTaskUseCase("user-bob", goalBob.id, planBob.id, "Bob Task", "Desc", 1) as Result.Success).data
        val commBob = (createCommitmentUseCase("user-bob", null, null, null, null, "Bob Comm", "Desc", 1, initialStatus = CommitmentStatus.COMMITTED) as Result.Success).data

        // Verify queries for Alice return only Alice's records
        val aliceGoals = (goalRepository.getGoalsForUser("user-alice") as Result.Success).data
        assertEquals(1, aliceGoals.size)
        assertEquals(goalAlice.id, aliceGoals.first().id)

        val alicePlans = (planRepository.getPlansForGoal(goalAlice.id) as Result.Success).data
        assertEquals(1, alicePlans.size)
        assertEquals(planAlice.id, alicePlans.first().id)

        val aliceTasks = (taskRepository.getTasksForPlan(planAlice.id) as Result.Success).data
        assertEquals(1, aliceTasks.size)
        assertEquals(taskAlice.id, aliceTasks.first().id)

        val aliceCommitments = (commitmentRepository.getActiveCommitmentsForUser("user-alice", listOf(CommitmentStatus.COMMITTED)) as Result.Success).data
        assertEquals(1, aliceCommitments.size)
        assertEquals(commAlice.id, aliceCommitments.first().id)

        // Verify queries for Bob return only Bob's records
        val bobGoals = (goalRepository.getGoalsForUser("user-bob") as Result.Success).data
        assertEquals(1, bobGoals.size)
        assertEquals(goalBob.id, bobGoals.first().id)

        val bobCommitments = (commitmentRepository.getActiveCommitmentsForUser("user-bob", listOf(CommitmentStatus.COMMITTED)) as Result.Success).data
        assertEquals(1, bobCommitments.size)
        assertEquals(commBob.id, bobCommitments.first().id)

        // Test TodayViewModel session switching: Alice -> Bob
        val getPendingRecommendations = mockk<GetPendingInterventionRecommendationsUseCase>(relaxed = true)
        io.mockk.every { getPendingRecommendations(any(), any()) } returns flowOf(emptyList())

        val sessionProvider = FakeUserSessionProvider(SessionState.Authenticated("user-alice", isAnonymous = false))
        val todayViewModel = TodayViewModel(
            getActiveCommitmentsUseCase = getActiveCommitmentsUseCase,
            updateCommitmentStatusUseCase = updateCommitmentStatusUseCase,
            scheduleCommitmentUseCase = mockk(relaxed = true),
            cancelCommitmentScheduleUseCase = mockk(relaxed = true),
            getPendingInterventionRecommendationsUseCase = getPendingRecommendations,
            handleInterventionResultUseCase = mockk(relaxed = true),
            orchestrateInterventionGenerationUseCase = mockk(relaxed = true),
            userSessionProvider = sessionProvider,
            createCommitmentUseCase = createCommitmentUseCase,
            getDueTasksUseCase = getDueTasksUseCase
        )

        val todayStateAliceRaw = withTimeout(3000L) {
            todayViewModel.uiState.first { it !is TodayUiState.Loading }
        }
        assertTrue("Alice UI state must be Success, but was: ${(todayStateAliceRaw as? TodayUiState.Error)?.message ?: todayStateAliceRaw}", todayStateAliceRaw is TodayUiState.Success)
        val todayStateAlice = todayStateAliceRaw as TodayUiState.Success
        assertEquals(1, todayStateAlice.activeCommitments.size)
        assertEquals("Alice Comm", todayStateAlice.activeCommitments.first().title)

        // Switch session to Bob
        sessionProvider.setState(SessionState.Authenticated("user-bob", isAnonymous = false))
        val todayStateBobRaw = withTimeout(3000L) {
            todayViewModel.uiState.first { it !is TodayUiState.Loading && (it as? TodayUiState.Success)?.activeCommitments?.any { c -> c.userId == "user-bob" } == true }
        }
        assertTrue("Bob UI state must be Success, but was: ${(todayStateBobRaw as? TodayUiState.Error)?.message ?: todayStateBobRaw}", todayStateBobRaw is TodayUiState.Success)
        val todayStateBob = todayStateBobRaw as TodayUiState.Success
        assertEquals(1, todayStateBob.activeCommitments.size)
        assertEquals("Bob Comm", todayStateBob.activeCommitments.first().title)
        assertFalse("Bob's state must not contain Alice's commitment", todayStateBob.activeCommitments.any { it.userId == "user-alice" })
    }

    @Test
    fun testPhase3676A_roomBackedTaskEventsAndTransactionRollbackAndProductionDi() = runTest {
        val task = (createTaskUseCase(
            userId = "user-1",
            goalId = null,
            planId = null,
            title = "Room Event Task",
            description = "Desc",
            priority = 2,
            scheduledAt = 10_000L,
            dueAt = 20_000L
        ) as Result.Success).data

        clock.advanceTimeBy(1_000L)
        assertTrue(updateTaskStatusUseCase(task.id, TaskStatus.IN_PROGRESS) is Result.Success)

        clock.advanceTimeBy(1_000L)
        assertTrue(editTaskUseCase(taskId = task.id, dueAt = 30_000L) is Result.Success)

        clock.advanceTimeBy(1_000L)
        assertTrue(updateTaskStatusUseCase(task.id, TaskStatus.COMPLETED) is Result.Success)

        val persistedEvents = (eventRepository.getEventsForEntity(task.id) as Result.Success).data
        assertEquals(
            setOf(EventType.TASK_CREATED, EventType.TASK_STARTED, EventType.TASK_POSTPONED, EventType.TASK_COMPLETED),
            persistedEvents.map { it.type }.toSet()
        )

        // Room transaction rollback when event save fails
        val failingEventRepo = object : com.sanket_satpute_20.ironmind.domain.repository.EventRepository by eventRepository {
            override suspend fun saveEvent(event: com.sanket_satpute_20.ironmind.domain.model.Event): Result<com.sanket_satpute_20.ironmind.domain.model.Event, Exception> {
                return Result.Failure(RuntimeException("Simulated event write failure"))
            }
        }
        val atomicTask2 = (createTaskUseCase("user-1", null, null, "Atomic Rollback", "Desc", 1, dueAt = 40_000L) as Result.Success).data
        val atomicEditUseCase = EditTaskUseCase(taskRepository, clock, failingEventRepo, idGenerator)
        val atomicStatusUseCase = UpdateTaskStatusUseCase(taskRepository, clock, failingEventRepo, idGenerator)

        assertTrue(atomicEditUseCase(taskId = atomicTask2.id, dueAt = 80_000L) is Result.Failure)
        assertEquals(40_000L, (taskRepository.getTask(atomicTask2.id) as Result.Success).data!!.dueAt)

        assertTrue(atomicStatusUseCase(atomicTask2.id, TaskStatus.COMPLETED) is Result.Failure)
        assertEquals(TaskStatus.PENDING, (taskRepository.getTask(atomicTask2.id) as Result.Success).data!!.status)

        // Verify production DefaultAppContainer provides non-null task & stop protection dependencies
        try {
            androidx.work.WorkManager.initialize(
                context,
                androidx.work.Configuration.Builder().build()
            )
        } catch (_: IllegalStateException) {
            // Already initialized in this Robolectric process
        }
        val appContainer = com.sanket_satpute_20.ironmind.di.DefaultAppContainer(context)
        try {
            val editEventRepoField = EditTaskUseCase::class.java.getDeclaredField("eventRepository").apply { isAccessible = true }
            val editIdGenField = EditTaskUseCase::class.java.getDeclaredField("idGenerator").apply { isAccessible = true }
            assertNotNull(editEventRepoField.get(appContainer.editTaskUseCase))
            assertNotNull(editIdGenField.get(appContainer.editTaskUseCase))

            val statusEventRepoField = UpdateTaskStatusUseCase::class.java.getDeclaredField("eventRepository").apply { isAccessible = true }
            val statusIdGenField = UpdateTaskStatusUseCase::class.java.getDeclaredField("idGenerator").apply { isAccessible = true }
            assertNotNull(statusEventRepoField.get(appContainer.updateTaskStatusUseCase))
            assertNotNull(statusIdGenField.get(appContainer.updateTaskStatusUseCase))

            val stopEventRepoField = com.sanket_satpute_20.ironmind.domain.usecase.protection.StopProtectionSessionUseCase::class.java.getDeclaredField("eventRepository").apply { isAccessible = true }
            val stopIdGenField = com.sanket_satpute_20.ironmind.domain.usecase.protection.StopProtectionSessionUseCase::class.java.getDeclaredField("idGenerator").apply { isAccessible = true }
            assertNotNull(stopEventRepoField.get(appContainer.stopProtectionSessionUseCase))
            assertNotNull(stopIdGenField.get(appContainer.stopProtectionSessionUseCase))
        } finally {
            appContainer.database.close()
        }
    }
}
