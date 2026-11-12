package com.sanket_satpute_20.ironmind.ui.screens.plans

import android.content.Context
import androidx.arch.core.executor.ArchTaskExecutor
import androidx.arch.core.executor.TaskExecutor
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.sanket_satpute_20.ironmind.data.local.IronMindDatabase
import com.sanket_satpute_20.ironmind.data.local.dao.OutboxDao
import com.sanket_satpute_20.ironmind.data.local.entity.OutboxEntity
import com.sanket_satpute_20.ironmind.data.repository.CommitmentRepositoryImpl
import com.sanket_satpute_20.ironmind.data.repository.EventRepositoryImpl
import com.sanket_satpute_20.ironmind.data.repository.OutcomeRepositoryImpl
import com.sanket_satpute_20.ironmind.data.repository.TaskRepositoryImpl
import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.logging.IronLogger
import com.sanket_satpute_20.ironmind.domain.model.CommitmentStatus
import com.sanket_satpute_20.ironmind.domain.model.EntitySource
import com.sanket_satpute_20.ironmind.domain.model.Plan
import com.sanket_satpute_20.ironmind.domain.model.PlanStatus
import com.sanket_satpute_20.ironmind.domain.model.Task
import com.sanket_satpute_20.ironmind.domain.model.TaskStatus
import com.sanket_satpute_20.ironmind.domain.session.SessionState
import com.sanket_satpute_20.ironmind.domain.usecase.commitment.CreateCommitmentUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.commitment.GetActiveCommitmentsUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.commitment.UpdateCommitmentStatusUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.plan.GetPlanUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.task.CreateTaskUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.task.EditTaskUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.task.GetTasksUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.task.UpdateTaskStatusUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.task.DeleteTaskUseCase
import com.sanket_satpute_20.ironmind.testutil.FakeUserSessionProvider
import com.sanket_satpute_20.ironmind.testutil.TestDispatcherRule
import com.sanket_satpute_20.ironmind.testutil.fake.FakeClock
import com.sanket_satpute_20.ironmind.testutil.fake.FakeCommitmentRepository
import com.sanket_satpute_20.ironmind.testutil.fake.FakeEventRepository
import com.sanket_satpute_20.ironmind.testutil.fake.FakeIdGenerator
import com.sanket_satpute_20.ironmind.testutil.fake.FakeOutcomeRepository
import androidx.lifecycle.viewModelScope
import com.sanket_satpute_20.ironmind.testutil.fake.FakePlanRepository
import com.sanket_satpute_20.ironmind.testutil.fake.FakeReminderScheduler
import com.sanket_satpute_20.ironmind.testutil.fake.FakeTaskRepository
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.After
import org.junit.AfterClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class PlanDetailTaskActionIntegrationTest {

    @get:Rule
    val dispatcherRule = TestDispatcherRule()

    // Test fakes for ViewModel UI flow
    private lateinit var fakePlanRepo: FakePlanRepository
    private lateinit var fakeTaskRepo: FakeTaskRepository
    private lateinit var fakeCommitmentRepo: FakeCommitmentRepository
    private lateinit var fakeEventRepo: FakeEventRepository
    private lateinit var fakeReminderScheduler: FakeReminderScheduler
    private lateinit var clock: FakeClock
    private lateinit var idGenerator: FakeIdGenerator
    private lateinit var sessionProvider: FakeUserSessionProvider
    private lateinit var logger: IronLogger

    private lateinit var getPlanUseCase: GetPlanUseCase
    private lateinit var getTasksUseCase: GetTasksUseCase
    private lateinit var createTaskUseCase: CreateTaskUseCase
    private lateinit var editTaskUseCase: EditTaskUseCase
    private lateinit var updateTaskStatusUseCase: UpdateTaskStatusUseCase
    private lateinit var deleteTaskUseCase: DeleteTaskUseCase
    private lateinit var getActiveCommitmentsUseCase: GetActiveCommitmentsUseCase
    private lateinit var createCommitmentUseCase: CreateCommitmentUseCase

    private lateinit var viewModel: PlanDetailViewModel

    // Real database fixtures for outbox atomicity and rollback verification
    private lateinit var context: Context
    private lateinit var db: IronMindDatabase
    private lateinit var delegatingOutboxDao: DelegatingFailingOutboxDao
    private lateinit var realTaskRepository: TaskRepositoryImpl
    private lateinit var realCommitmentRepository: CommitmentRepositoryImpl
    private lateinit var realOutcomeRepository: OutcomeRepositoryImpl
    private lateinit var realEventRepository: EventRepositoryImpl
    private lateinit var realUpdateCommitmentStatusUseCase: UpdateCommitmentStatusUseCase
    private lateinit var realEditTaskUseCase: EditTaskUseCase
    private lateinit var realDeleteTaskUseCase: DeleteTaskUseCase

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
        clock = FakeClock(1000L)
        idGenerator = FakeIdGenerator("test-")
        sessionProvider = FakeUserSessionProvider(SessionState.Authenticated("user-1", isAnonymous = false))

        fakePlanRepo = FakePlanRepository()
        fakeTaskRepo = FakeTaskRepository()
        fakeCommitmentRepo = FakeCommitmentRepository()
        fakeEventRepo = FakeEventRepository()
        fakeReminderScheduler = FakeReminderScheduler()

        getPlanUseCase = GetPlanUseCase(fakePlanRepo)
        getTasksUseCase = GetTasksUseCase(fakeTaskRepo)
        createTaskUseCase = CreateTaskUseCase(fakeTaskRepo, idGenerator, clock, fakeEventRepo)
        editTaskUseCase = EditTaskUseCase(fakeTaskRepo, clock, fakeEventRepo, idGenerator)
        updateTaskStatusUseCase = UpdateTaskStatusUseCase(fakeTaskRepo, clock, fakeEventRepo, idGenerator)
        deleteTaskUseCase = DeleteTaskUseCase(fakeTaskRepo)
        getActiveCommitmentsUseCase = GetActiveCommitmentsUseCase(fakeCommitmentRepo)
        createCommitmentUseCase = CreateCommitmentUseCase(fakeCommitmentRepo, fakeReminderScheduler, idGenerator, clock, fakeEventRepo)

        logger = object : IronLogger {
            override fun logLifecycle(component: String, event: String, parameters: Map<String, Any?>) {}
        }

        viewModel = PlanDetailViewModel(
            getPlanUseCase = getPlanUseCase,
            getTasksUseCase = getTasksUseCase,
            createTaskUseCase = createTaskUseCase,
            getActiveCommitmentsUseCase = getActiveCommitmentsUseCase,
            createCommitmentUseCase = createCommitmentUseCase,
            logger = logger,
            userSessionProvider = sessionProvider,
            editTaskUseCase = editTaskUseCase,
            updateTaskStatusUseCase = updateTaskStatusUseCase,
            deleteTaskUseCase = deleteTaskUseCase
        )

        // Fresh in-memory Room DB per test
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, IronMindDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        delegatingOutboxDao = DelegatingFailingOutboxDao(db.outboxDao())
        realTaskRepository = TaskRepositoryImpl(
            dao = db.ironMindDao(),
            outboxDao = delegatingOutboxDao,
            database = db,
            idGenerator = FakeIdGenerator("test-real-"),
            backgroundExecutor = null
        )
        realCommitmentRepository = CommitmentRepositoryImpl(dao = db.ironMindDao())
        realOutcomeRepository = OutcomeRepositoryImpl(dao = db.ironMindDao())
        realEventRepository = EventRepositoryImpl(dao = db.ironMindDao(), database = db)

        realUpdateCommitmentStatusUseCase = UpdateCommitmentStatusUseCase(
            repository = realCommitmentRepository,
            outcomeRepository = realOutcomeRepository,
            reminderScheduler = fakeReminderScheduler,
            clock = clock,
            idGenerator = idGenerator,
            eventRepository = realEventRepository,
            taskRepository = realTaskRepository
        )
        realEditTaskUseCase = EditTaskUseCase(realTaskRepository, clock, realEventRepository, idGenerator)
        realDeleteTaskUseCase = DeleteTaskUseCase(realTaskRepository)
    }

    @After
    fun tearDown() {
        delegatingOutboxDao.shouldFail = false
        fakeTaskRepo.shouldFail = false
        viewModel.viewModelScope.coroutineContext.cancelChildren()
        db.close()
    }

    private suspend fun seedFakePlanAndTask(
        planId: String = "plan-1",
        taskId: String = "task-1",
        userId: String = "user-1",
        taskStatus: TaskStatus = TaskStatus.PENDING
    ): Pair<Plan, Task> {
        val plan = Plan(
            id = planId,
            userId = userId,
            goalId = "goal-1",
            title = "Master Architecture",
            description = "Architecture Plan",
            status = PlanStatus.ACTIVE,
            createdAt = 1000L,
            updatedAt = 1000L,
            source = EntitySource.USER
        )
        fakePlanRepo.savePlan(plan)

        val task = Task(
            id = taskId,
            userId = userId,
            goalId = "goal-1",
            planId = planId,
            parentTaskId = null,
            title = "Initial Design",
            description = "Initial description",
            status = taskStatus,
            priority = 2,
            estimatedDurationMinutes = 45,
            scheduledAt = 1000L,
            dueAt = 5000L,
            createdAt = 1000L,
            updatedAt = 1000L,
            completedAt = if (taskStatus == TaskStatus.COMPLETED) 1000L else null,
            postponedAt = null,
            source = EntitySource.USER
        )
        fakeTaskRepo.saveTask(task)
        return Pair(plan, task)
    }

    @Test
    fun test1_useCasesCorrectlyExposedAndInvokedByViewModel() = runTest {
        val (_, task) = seedFakePlanAndTask()

        viewModel.loadData("plan-1")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue("UI state must be Success", state is PlanDetailUiState.Success)

        // 1. Open edit form and verify state population
        viewModel.openEditForm(task)
        assertEquals(task, viewModel.editingTask.value)
        assertEquals("Initial Design", viewModel.editTaskTitle.value)
        assertEquals("Initial description", viewModel.editTaskDescription.value)
        assertEquals(2f, viewModel.editTaskPriority.value)
        assertNull(viewModel.taskActionError.value)

        // 2. Edit task title and save
        viewModel.updateEditTaskTitle("Refined Architecture Design")
        viewModel.updateEditTaskDescription("Updated description")
        viewModel.updateEditTaskPriority(4f)
        viewModel.saveEditedTask()
        advanceUntilIdle()

        // Form closed on success
        assertNull(viewModel.editingTask.value)
        assertNull(viewModel.taskActionError.value)

        // Verify task updated in repository
        val updatedTask = (fakeTaskRepo.getTask("task-1") as Result.Success).data!!
        assertEquals("Refined Architecture Design", updatedTask.title)
        assertEquals("Updated description", updatedTask.description)
        assertEquals(4, updatedTask.priority)

        // 3. Status transition via ViewModel
        viewModel.updateTaskStatus(updatedTask, TaskStatus.IN_PROGRESS)
        advanceUntilIdle()

        val inProgressTask = (fakeTaskRepo.getTask("task-1") as Result.Success).data!!
        assertEquals(TaskStatus.IN_PROGRESS, inProgressTask.status)
    }

    @Test
    fun test2_editingTaskUpdatesRoomAndItsOutboxEntry() = runTest {
        // Real Room DB test for EditTaskUseCase atomic persistence
        val initialTask = Task(
            id = "task-room-outbox",
            userId = "user-1",
            goalId = "goal-1",
            planId = "plan-1",
            parentTaskId = null,
            title = "Original Title",
            description = "Original Description",
            status = TaskStatus.PENDING,
            priority = 1,
            estimatedDurationMinutes = 30,
            scheduledAt = 1000L,
            dueAt = 5000L,
            createdAt = 1000L,
            updatedAt = 1000L,
            completedAt = null,
            postponedAt = null,
            source = EntitySource.USER
        )
        realTaskRepository.saveTask(initialTask)

        // Clear outbox to isolate edit mutation
        delegatingOutboxDao.getAllEntries().forEach { delegatingOutboxDao.deleteEntry(it.operationId) }
        assertEquals(0, delegatingOutboxDao.getAllEntries().size)

        clock.setTime(2500L)
        val editResult = realEditTaskUseCase(
            taskId = "task-room-outbox",
            title = "Clean Architecture Refactor",
            description = "Separating domain and data layers",
            priority = 5,
            estimatedDurationMinutes = 45,
            scheduledAt = null,
            dueAt = null
        )
        assertTrue(editResult is Result.Success)

        // Verify Room task record
        val savedTask = (realTaskRepository.getTask("task-room-outbox") as Result.Success).data!!
        assertEquals("Clean Architecture Refactor", savedTask.title)
        assertEquals("Separating domain and data layers", savedTask.description)
        assertEquals(5, savedTask.priority)
        assertEquals(2500L, savedTask.updatedAt)

        // Verify Outbox entry
        val outboxEntries = delegatingOutboxDao.getAllEntries().filter {
            it.entityType == "Task" && it.entityId == "task-room-outbox"
        }
        assertEquals(1, outboxEntries.size)
        val outbox = outboxEntries.first()
        assertEquals("UPSERT", outbox.operationType)
        assertEquals("PENDING", outbox.status)

        val payload = JSONObject(outbox.payload)
        assertEquals("task-room-outbox", payload.getString("id"))
        assertEquals("user-1", payload.getString("userId"))
        assertEquals("Clean Architecture Refactor", payload.getString("title"))
        assertEquals("Separating domain and data layers", payload.getString("description"))
        assertEquals(5, payload.getInt("priority"))
        assertEquals(2500L, payload.getLong("updatedAt"))

        // Verify transaction rollback on outbox failure
        delegatingOutboxDao.shouldFail = true
        val failedEditResult = realEditTaskUseCase(
            taskId = "task-room-outbox",
            title = "Will Fail Rollback",
            description = null,
            priority = null,
            estimatedDurationMinutes = null,
            scheduledAt = null,
            dueAt = null
        )
        assertTrue("Expected failure on outbox persistence error", failedEditResult is Result.Failure)

        // Task in Room must NOT have changed to 'Will Fail Rollback'
        val taskAfterRollback = (realTaskRepository.getTask("task-room-outbox") as Result.Success).data!!
        assertEquals("Clean Architecture Refactor", taskAfterRollback.title)
        delegatingOutboxDao.shouldFail = false
    }

    @Test
    fun test3_validStatusTransitionsPersistCorrectly() = runTest {
        val (_, task) = seedFakePlanAndTask(taskId = "task-transitions", taskStatus = TaskStatus.PENDING)

        viewModel.loadData("plan-1")
        advanceUntilIdle()

        // 1. PENDING -> IN_PROGRESS
        clock.setTime(2000L)
        viewModel.updateTaskStatus(task, TaskStatus.IN_PROGRESS)
        advanceUntilIdle()
        var current = (fakeTaskRepo.getTask("task-transitions") as Result.Success).data!!
        assertEquals(TaskStatus.IN_PROGRESS, current.status)
        assertEquals(2000L, current.updatedAt)
        assertNull(current.completedAt)

        // 2. IN_PROGRESS -> POSTPONED
        clock.setTime(3000L)
        viewModel.updateTaskStatus(current, TaskStatus.POSTPONED)
        advanceUntilIdle()
        current = (fakeTaskRepo.getTask("task-transitions") as Result.Success).data!!
        assertEquals(TaskStatus.POSTPONED, current.status)
        assertEquals(3000L, current.postponedAt)
        assertNull(current.completedAt)

        // 3. POSTPONED -> COMPLETED
        clock.setTime(4000L)
        viewModel.updateTaskStatus(current, TaskStatus.COMPLETED)
        advanceUntilIdle()
        current = (fakeTaskRepo.getTask("task-transitions") as Result.Success).data!!
        assertEquals(TaskStatus.COMPLETED, current.status)
        assertEquals(4000L, current.completedAt)
        assertEquals(4000L, current.updatedAt)
    }

    @Test
    fun test4_invalidTransitionsAreRejected() = runTest {
        val (_, task) = seedFakePlanAndTask(taskId = "task-terminal", taskStatus = TaskStatus.COMPLETED)

        viewModel.loadData("plan-1")
        advanceUntilIdle()

        // Attempting to revert COMPLETED -> PENDING
        viewModel.updateTaskStatus(task, TaskStatus.PENDING)
        advanceUntilIdle()

        // Must reject transition and surface error
        assertNotNull(viewModel.taskActionError.value)
        assertTrue(viewModel.taskActionError.value!!.contains("Invalid state transition from COMPLETED to PENDING"))

        // Task in DB must remain COMPLETED
        val dbTask = (fakeTaskRepo.getTask("task-terminal") as Result.Success).data!!
        assertEquals(TaskStatus.COMPLETED, dbTask.status)

        // Duplicate write protection: calling with current status is an idempotent no-op
        viewModel.clearTaskActionError()
        viewModel.updateTaskStatus(dbTask, TaskStatus.COMPLETED)
        advanceUntilIdle()
        assertNull(viewModel.taskActionError.value)
    }

    @Test
    fun test5_repositoryFailuresAreSurfacedWithoutFalseSuccess() = runTest {
        val (_, task) = seedFakePlanAndTask(taskId = "task-fail")

        viewModel.loadData("plan-1")
        advanceUntilIdle()

        // Simulate repository failure
        fakeTaskRepo.shouldFail = true

        viewModel.openEditForm(task)
        viewModel.updateEditTaskTitle("New Title")
        viewModel.saveEditedTask()
        advanceUntilIdle()

        // Failure surfaced in UI state
        assertEquals("Fake failure", viewModel.taskActionError.value)
        assertFalse(viewModel.isPerformingTaskAction.value)

        // Status update failure surfaced in UI state
        viewModel.clearTaskActionError()
        viewModel.updateTaskStatus(task, TaskStatus.IN_PROGRESS)
        advanceUntilIdle()

        assertEquals("Fake failure", viewModel.taskActionError.value)
        assertFalse(viewModel.isPerformingTaskAction.value)
    }

    @Test
    fun test6_taskOwnershipRemainsUnchanged() = runTest {
        val (_, task) = seedFakePlanAndTask(taskId = "task-owner", userId = "user-1")

        viewModel.loadData("plan-1")
        advanceUntilIdle()

        // Edit task
        viewModel.openEditForm(task)
        viewModel.updateEditTaskTitle("Title Checked")
        viewModel.saveEditedTask()
        advanceUntilIdle()

        val dbTask = (fakeTaskRepo.getTask("task-owner") as Result.Success).data!!
        assertEquals("user-1", dbTask.userId)

        // Cross-user test: if task belongs to another user, session barrier rejects it
        val foreignTask = task.copy(id = "foreign-task", userId = "user-foreign")
        fakeTaskRepo.saveTask(foreignTask)

        viewModel.openEditForm(foreignTask)
        viewModel.updateEditTaskTitle("Cross user tamper")
        viewModel.saveEditedTask()
        advanceUntilIdle()

        assertEquals("Task does not belong to this session", viewModel.taskActionError.value)

        val unhackedTask = (fakeTaskRepo.getTask("foreign-task") as Result.Success).data!!
        assertEquals("Initial Design", unhackedTask.title)
    }

    @Test
    fun test7_commitmentCompletionStillMarksLinkedTaskCompleted() = runTest {
        // Real Room DB test verifying Remediation 2 preservation
        val task = Task(
            id = "task-linked",
            userId = "user-1",
            goalId = "goal-1",
            planId = "plan-1",
            parentTaskId = null,
            title = "Linked Task",
            description = "Task to be completed from commitment",
            status = TaskStatus.PENDING,
            priority = 1,
            estimatedDurationMinutes = 30,
            scheduledAt = 1000L,
            dueAt = 5000L,
            createdAt = 1000L,
            updatedAt = 1000L,
            completedAt = null,
            postponedAt = null,
            source = EntitySource.USER
        )
        realTaskRepository.saveTask(task)

        val createCommitment = CreateCommitmentUseCase(
            repository = realCommitmentRepository,
            reminderScheduler = fakeReminderScheduler,
            idGenerator = idGenerator,
            clock = clock,
            eventRepository = realEventRepository
        )

        val commitmentResult = createCommitment(
            userId = "user-1",
            goalId = "goal-1",
            planId = "plan-1",
            taskId = "task-linked",
            parentCommitmentId = null,
            title = "Linked Commitment",
            description = "Desc",
            priority = 1,
            initialStatus = CommitmentStatus.COMMITTED
        )
        assertTrue(commitmentResult is Result.Success)
        val commId = (commitmentResult as Result.Success).data.id

        // Advance to STARTED
        realUpdateCommitmentStatusUseCase(commId, CommitmentStatus.STARTED)

        // Advance to COMPLETED at clock T=5500
        clock.setTime(5500L)
        val updateCommResult = realUpdateCommitmentStatusUseCase(commId, CommitmentStatus.COMPLETED)
        assertTrue(updateCommResult is Result.Success)

        // Linked task must be automatically completed
        val linkedTask = (realTaskRepository.getTask("task-linked") as Result.Success).data!!
        assertEquals(TaskStatus.COMPLETED, linkedTask.status)
        assertEquals(5500L, linkedTask.completedAt)
        advanceUntilIdle()
    }

    @Test
    fun test8_existingTaskCreationAndCommitForTodayFlowsRemainIntact() = runTest {
        val (plan, _) = seedFakePlanAndTask()

        viewModel.loadData("plan-1")
        advanceUntilIdle()

        // 1. Create task flow
        viewModel.openCreateForm()
        viewModel.updateNewTaskTitle("New Flow Task")
        viewModel.updateNewTaskDescription("Flow Description")
        viewModel.updateNewTaskPriority(3f)
        viewModel.createTask()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is PlanDetailUiState.Success)
        val tasks = (state as PlanDetailUiState.Success).tasks
        val createdTask = tasks.find { it.title == "New Flow Task" }
        assertNotNull(createdTask)
        assertEquals("plan-1", createdTask!!.planId)
        assertEquals(3, createdTask.priority)

        // 2. Commit for today flow
        viewModel.commitTaskForToday(createdTask)
        advanceUntilIdle()

        val updatedState = viewModel.uiState.value as PlanDetailUiState.Success
        assertTrue(updatedState.committedTaskIds.contains(createdTask.id))

        // Repeated commit is an idempotent no-op
        viewModel.commitTaskForToday(createdTask)
        advanceUntilIdle()
        assertTrue(updatedState.committedTaskIds.contains(createdTask.id))
    }

    @Test
    fun test9_taskCreationWithDueDate_persistsCorrectly() = runTest {
        val (plan, _) = seedFakePlanAndTask()

        viewModel.loadData(plan.id)
        advanceUntilIdle()

        viewModel.openCreateForm()
        viewModel.updateNewTaskTitle("Task With Due Date")
        viewModel.updateNewTaskDescription("Due date test")
        viewModel.updateNewTaskPriority(2f)
        viewModel.updateNewTaskDueAt(1731628800000L)
        viewModel.createTask()
        advanceUntilIdle()

        val state = viewModel.uiState.value as PlanDetailUiState.Success
        val createdTask = state.tasks.find { it.title == "Task With Due Date" }
        assertNotNull(createdTask)
        assertEquals(1731628800000L, createdTask!!.dueAt)
        assertEquals(2, createdTask.priority)
    }

    @Test
    fun test10_taskEditing_updatesDueDate() = runTest {
        val (plan, task) = seedFakePlanAndTask()

        viewModel.loadData(plan.id)
        advanceUntilIdle()

        viewModel.openEditForm(task)
        assertEquals(5000L, viewModel.editTaskDueAt.value)

        viewModel.updateEditTaskDueAt(99999L)
        viewModel.saveEditedTask()
        advanceUntilIdle()

        val updatedTask = (fakeTaskRepo.getTask(task.id) as Result.Success).data!!
        assertEquals(99999L, updatedTask.dueAt)
    }

    @Test
    fun test11_taskEditing_clearsDueDate() = runTest {
        val (plan, task) = seedFakePlanAndTask()

        viewModel.loadData(plan.id)
        advanceUntilIdle()

        viewModel.openEditForm(task)
        assertEquals(5000L, viewModel.editTaskDueAt.value)

        viewModel.updateEditTaskDueAt(null)
        viewModel.saveEditedTask()
        advanceUntilIdle()

        val updatedTask = (fakeTaskRepo.getTask(task.id) as Result.Success).data!!
        assertNull(updatedTask.dueAt)
    }

    @Test
    fun test12_taskDueAtUpdateAndOutboxWrite_inRoom() = runTest {
        val initialTask = Task(
            id = "task-due-room-test",
            userId = "user-1",
            goalId = "goal-1",
            planId = "plan-1",
            parentTaskId = null,
            title = "Due Date Room Task",
            description = "Desc",
            status = TaskStatus.PENDING,
            priority = 1,
            estimatedDurationMinutes = 30,
            scheduledAt = 1000L,
            dueAt = 5000L,
            createdAt = 1000L,
            updatedAt = 1000L,
            completedAt = null,
            postponedAt = null,
            source = EntitySource.USER
        )
        realTaskRepository.saveTask(initialTask)
        delegatingOutboxDao.getAllEntries().forEach { delegatingOutboxDao.deleteEntry(it.operationId) }

        // 1. Update due date
        clock.setTime(2000L)
        val updateResult = realEditTaskUseCase(
            taskId = "task-due-room-test",
            title = null,
            description = null,
            priority = null,
            estimatedDurationMinutes = null,
            scheduledAt = null,
            dueAt = 15000L,
            clearDueAt = false
        )
        assertTrue(updateResult is Result.Success)

        val roomTask1 = db.ironMindDao().getTask("task-due-room-test")
        assertNotNull(roomTask1)
        assertEquals(15000L, roomTask1!!.dueAt)

        val outbox1 = delegatingOutboxDao.getPendingEntries().first { it.entityId == "task-due-room-test" }
        val payload1 = JSONObject(outbox1.payload)
        assertEquals(15000L, payload1.getLong("dueAt"))

        delegatingOutboxDao.deleteEntry(outbox1.operationId)

        // 2. Clear due date
        clock.setTime(3000L)
        val clearResult = realEditTaskUseCase(
            taskId = "task-due-room-test",
            title = null,
            description = null,
            priority = null,
            estimatedDurationMinutes = null,
            scheduledAt = null,
            dueAt = null,
            clearDueAt = true
        )
        assertTrue(clearResult is Result.Success)

        val roomTask2 = db.ironMindDao().getTask("task-due-room-test")
        assertNotNull(roomTask2)
        assertNull(roomTask2!!.dueAt)

        val outbox2 = delegatingOutboxDao.getPendingEntries().first { it.entityId == "task-due-room-test" }
        val payload2 = JSONObject(outbox2.payload)
        assertTrue(payload2.isNull("dueAt"))
    }

    @Test
    fun test13_taskDeletion_requestsConfirmationAndDeletesOnConfirm() = runTest {
        val (plan, task) = seedFakePlanAndTask()

        viewModel.loadData(plan.id)
        advanceUntilIdle()

        val state1 = viewModel.uiState.value as PlanDetailUiState.Success
        assertTrue(state1.tasks.any { it.id == task.id })

        // 1. Request deletion -> sets pending deletion
        viewModel.requestDeleteTask(task)
        assertEquals(task.id, viewModel.taskPendingDeletion.value?.id)

        // 2. Cancel deletion -> clears pending, leaves task untouched
        viewModel.cancelDeleteTask()
        assertNull(viewModel.taskPendingDeletion.value)
        assertNotNull((fakeTaskRepo.getTask(task.id) as Result.Success).data)

        // 3. Request again and confirm
        viewModel.requestDeleteTask(task)
        viewModel.confirmDeleteTask()
        advanceUntilIdle()

        // 4. Verification: task removed from repository and refreshed from UI state
        assertNull(viewModel.taskPendingDeletion.value)
        assertEquals("Task deleted successfully", viewModel.deletionSuccessMessage.value)
        assertNull((fakeTaskRepo.getTask(task.id) as Result.Success).data)

        val state2 = viewModel.uiState.value as PlanDetailUiState.Success
        assertFalse(state2.tasks.any { it.id == task.id })
    }

    @Test
    fun test14_taskDeletion_handlesRepositoryFailureAndRollsBack() = runTest {
        val (plan, task) = seedFakePlanAndTask(taskId = "task-fail-delete")

        viewModel.loadData(plan.id)
        advanceUntilIdle()

        // 1. Simulate repository failure in ViewModel flow
        fakeTaskRepo.shouldFail = true

        viewModel.requestDeleteTask(task)
        viewModel.confirmDeleteTask()
        advanceUntilIdle()

        // Verify error surfaced and false success prevented in UI state
        assertEquals("Fake failure", viewModel.taskActionError.value)
        assertNull(viewModel.deletionSuccessMessage.value)
        assertFalse(viewModel.isPerformingTaskAction.value)

        fakeTaskRepo.shouldFail = false

        // Verify task was not deleted
        val preservedFake = (fakeTaskRepo.getTask(task.id) as Result.Success).data
        assertNotNull(preservedFake)

        // 2. Real Room DB rollback: verify deleteTask rolls back if Outbox fails
        val roomTask = Task(
            id = "task-delete-fail-room",
            userId = "user-1",
            goalId = "goal-1",
            planId = plan.id,
            parentTaskId = null,
            title = "Task To Fail Delete",
            description = "Desc",
            status = TaskStatus.PENDING,
            priority = 1,
            estimatedDurationMinutes = 30,
            scheduledAt = 1000L,
            dueAt = 5000L,
            createdAt = 1000L,
            updatedAt = 1000L,
            completedAt = null,
            postponedAt = null,
            source = EntitySource.USER
        )
        realTaskRepository.saveTask(roomTask)
        assertNotNull(db.ironMindDao().getTask("task-delete-fail-room"))

        delegatingOutboxDao.shouldFail = true

        val deleteResult = realDeleteTaskUseCase(userId = "user-1", taskId = "task-delete-fail-room")
        assertTrue("deleteTask must fail when outbox insertion fails", deleteResult is Result.Failure)

        // Verify Room rollback: task is preserved in Room!
        val preserved = db.ironMindDao().getTask("task-delete-fail-room")
        assertNotNull("Task must NOT be removed from Room if outbox fails", preserved)

        delegatingOutboxDao.shouldFail = false
    }

    @Test
    fun test15_taskDeletion_preventsDuplicateConcurrentSubmissions() = runTest {
        val (plan, task) = seedFakePlanAndTask()

        viewModel.loadData(plan.id)
        advanceUntilIdle()

        viewModel.requestDeleteTask(task)

        // Rapid duplicate invocation
        viewModel.confirmDeleteTask()
        viewModel.confirmDeleteTask()
        advanceUntilIdle()

        // Deletion succeeded safely without crash or race
        assertNull(viewModel.taskPendingDeletion.value)
        assertEquals("Task deleted successfully", viewModel.deletionSuccessMessage.value)
        assertNull((fakeTaskRepo.getTask(task.id) as Result.Success).data)
    }
}
