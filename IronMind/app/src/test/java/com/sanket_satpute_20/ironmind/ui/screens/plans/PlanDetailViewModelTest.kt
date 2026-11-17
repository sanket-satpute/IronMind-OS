package com.sanket_satpute_20.ironmind.ui.screens.plans

import com.sanket_satpute_20.ironmind.domain.logging.IronLogger
import com.sanket_satpute_20.ironmind.domain.model.Plan
import com.sanket_satpute_20.ironmind.domain.model.PlanStatus
import com.sanket_satpute_20.ironmind.domain.model.EntitySource
import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.session.SessionState
import com.sanket_satpute_20.ironmind.domain.usecase.plan.GetPlanUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.plan.EditPlanUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.plan.UpdatePlanStatusUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.task.CreateTaskUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.task.GetTasksUseCase
import com.sanket_satpute_20.ironmind.testutil.TestDispatcherRule
import com.sanket_satpute_20.ironmind.testutil.fake.FakeClock
import com.sanket_satpute_20.ironmind.testutil.fake.FakeIdGenerator
import com.sanket_satpute_20.ironmind.testutil.fake.FakePlanRepository
import com.sanket_satpute_20.ironmind.testutil.fake.FakeTaskRepository
import com.sanket_satpute_20.ironmind.domain.usecase.commitment.CreateCommitmentUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.commitment.GetActiveCommitmentsUseCase
import com.sanket_satpute_20.ironmind.testutil.fake.FakeCommitmentRepository
import com.sanket_satpute_20.ironmind.testutil.fake.FakeEventRepository
import com.sanket_satpute_20.ironmind.testutil.fake.FakeReminderScheduler
import com.sanket_satpute_20.ironmind.testutil.FakeUserSessionProvider
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import io.mockk.coEvery
import io.mockk.mockk

@OptIn(ExperimentalCoroutinesApi::class)
class PlanDetailViewModelTest {

    @get:Rule
    val dispatcherRule = TestDispatcherRule()

    private lateinit var planRepository: FakePlanRepository
    private lateinit var taskRepository: FakeTaskRepository
    private lateinit var commitmentRepository: FakeCommitmentRepository
    private lateinit var eventRepository: FakeEventRepository
    private lateinit var reminderScheduler: FakeReminderScheduler
    private lateinit var getPlanUseCase: GetPlanUseCase
    private lateinit var getTasksUseCase: GetTasksUseCase
    private lateinit var createTaskUseCase: CreateTaskUseCase
    private lateinit var getActiveCommitmentsUseCase: GetActiveCommitmentsUseCase
    private lateinit var createCommitmentUseCase: CreateCommitmentUseCase
    private lateinit var logger: IronLogger
    private lateinit var clock: FakeClock
    private lateinit var idGenerator: FakeIdGenerator
    private lateinit var editPlanUseCase: EditPlanUseCase
    private lateinit var updatePlanStatusUseCase: UpdatePlanStatusUseCase

    private lateinit var viewModel: PlanDetailViewModel

    @Before
    fun setup() {
        planRepository = FakePlanRepository()
        taskRepository = FakeTaskRepository()
        commitmentRepository = FakeCommitmentRepository()
        eventRepository = FakeEventRepository()
        reminderScheduler = FakeReminderScheduler()
        
        getPlanUseCase = GetPlanUseCase(planRepository)
        getTasksUseCase = GetTasksUseCase(taskRepository)
        clock = FakeClock()
        idGenerator = FakeIdGenerator()
        createTaskUseCase = CreateTaskUseCase(taskRepository, idGenerator, clock, eventRepository)
        getActiveCommitmentsUseCase = GetActiveCommitmentsUseCase(commitmentRepository)
        createCommitmentUseCase = CreateCommitmentUseCase(commitmentRepository, reminderScheduler, idGenerator, clock, eventRepository)
        editPlanUseCase = EditPlanUseCase(planRepository, clock, idGenerator, eventRepository)
        updatePlanStatusUseCase = UpdatePlanStatusUseCase(planRepository, clock, idGenerator, eventRepository)
        
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
            editPlanUseCase = editPlanUseCase,
            updatePlanStatusUseCase = updatePlanStatusUseCase
        )
    }

    @Test
    fun `loadData loads plan and tasks successfully`() = runTest {
        val testPlan = Plan(
            id = "plan1",
            userId = "user-1",
            goalId = "goal1",
            title = "Test Plan",
            description = "Desc",
            status = PlanStatus.ACTIVE,
            createdAt = clock.currentTimeMillis(),
            updatedAt = clock.currentTimeMillis(),
            source = EntitySource.USER
        )
        planRepository.savePlan(testPlan)

        viewModel.loadData("plan1")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is PlanDetailUiState.Success)
        val successState = state as PlanDetailUiState.Success
        assertEquals("Test Plan", successState.plan.title)
        assertTrue(successState.tasks.isEmpty())
    }

    @Test
    fun `createTask successfully adds task and refreshes list`() = runTest {
        val testPlan = Plan(
            id = "plan1",
            userId = "user-1",
            goalId = "goal1",
            title = "Test Plan",
            description = "Desc",
            status = PlanStatus.ACTIVE,
            createdAt = clock.currentTimeMillis(),
            updatedAt = clock.currentTimeMillis(),
            source = EntitySource.USER
        )
        planRepository.savePlan(testPlan)

        viewModel.loadData("plan1")
        advanceUntilIdle()

        viewModel.updateNewTaskTitle("New Task")
        viewModel.updateNewTaskDescription("Task Desc")
        viewModel.updateNewTaskPriority(1f)
        viewModel.createTask()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is PlanDetailUiState.Success)
        val tasks = (state as PlanDetailUiState.Success).tasks
        assertEquals(1, tasks.size)
        assertEquals("New Task", tasks[0].title)
        assertEquals("plan1", tasks[0].planId)
        assertEquals("goal1", tasks[0].goalId)
    }

    @Test
    fun `createTask rejects blank title`() = runTest {
        viewModel.loadData("plan1")
        advanceUntilIdle()

        viewModel.updateNewTaskTitle("   ")
        viewModel.createTask()
        advanceUntilIdle()

        val saveError = viewModel.saveError.value
        assertEquals("Title cannot be empty", saveError)
    }

    @Test
    fun `createTask failure produces error state`() = runTest {
        val testPlan = Plan(
            id = "plan1",
            userId = "user-1",
            goalId = "goal1",
            title = "Test Plan",
            description = "Desc",
            status = PlanStatus.ACTIVE,
            createdAt = clock.currentTimeMillis(),
            updatedAt = clock.currentTimeMillis(),
            source = EntitySource.USER
        )
        planRepository.savePlan(testPlan)

        viewModel.loadData("plan1")
        advanceUntilIdle()

        taskRepository.shouldFail = true

        viewModel.updateNewTaskTitle("New Task")
        viewModel.createTask()
        advanceUntilIdle()

        val saveError = viewModel.saveError.value
        assertEquals("Fake failure", saveError)
    }

    @Test
    fun `commitTaskForToday creates commitment and updates UI state`() = runTest {
        val testPlan = Plan(
            id = "plan1",
            userId = "user-1",
            goalId = "goal1",
            title = "Test Plan",
            description = "Desc",
            status = PlanStatus.ACTIVE,
            createdAt = clock.currentTimeMillis(),
            updatedAt = clock.currentTimeMillis(),
            source = EntitySource.USER
        )
        planRepository.savePlan(testPlan)

        viewModel.loadData("plan1")
        advanceUntilIdle()

        viewModel.updateNewTaskTitle("Task to Commit")
        viewModel.createTask()
        advanceUntilIdle()

        val state = viewModel.uiState.value as PlanDetailUiState.Success
        val task = state.tasks[0]
        assertTrue(state.committedTaskIds.isEmpty())

        viewModel.commitTaskForToday(task)
        advanceUntilIdle()

        val updatedState = viewModel.uiState.value as PlanDetailUiState.Success
        assertEquals(1, updatedState.committedTaskIds.size)
        assertTrue(updatedState.committedTaskIds.contains(task.id))
    }

    @Test
    fun `loadData handles task load failure`() = runTest {
        val testPlan = Plan(
            id = "plan1",
            userId = "user-1",
            goalId = "goal1",
            title = "Test Plan",
            description = "Desc",
            status = PlanStatus.ACTIVE,
            createdAt = clock.currentTimeMillis(),
            updatedAt = clock.currentTimeMillis(),
            source = EntitySource.USER
        )
        planRepository.savePlan(testPlan)

        taskRepository.shouldFail = true

        viewModel.loadData("plan1")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is PlanDetailUiState.Error)
        assertEquals("Fake failure", (state as PlanDetailUiState.Error).message)
    }

    @Test
    fun `session change cancels old plan load and reloads navigation plan for new UID`() = runTest {
        val provider = FakeUserSessionProvider(SessionState.OfflineFallback("user-1"))
        val oldLoadStarted = CompletableDeferred<Unit>()
        val finishOldLoad = CompletableDeferred<Unit>()
        val oldPlan = Plan(
            id = "plan1", userId = "user-1", goalId = "goal1", title = "Old user plan",
            description = "Desc", status = PlanStatus.ACTIVE, createdAt = clock.currentTimeMillis(),
            updatedAt = clock.currentTimeMillis(), source = EntitySource.USER
        )
        val newPlan = oldPlan.copy(userId = "user-2", title = "New user plan")
        val getPlan = mockk<GetPlanUseCase>()
        val getTasks = mockk<GetTasksUseCase>()
        val getCommitments = mockk<GetActiveCommitmentsUseCase>()
        coEvery { getPlan("plan1") } coAnswers {
            if (provider.getActiveUserId() == "user-1") {
                oldLoadStarted.complete(Unit)
                finishOldLoad.await()
                Result.Success(oldPlan)
            } else {
                Result.Success(newPlan)
            }
        }
        coEvery { getTasks("plan1") } returns Result.Success(emptyList())
        coEvery { getCommitments(any()) } returns Result.Success(emptyList())

        viewModel = PlanDetailViewModel(
            getPlanUseCase = getPlan,
            getTasksUseCase = getTasks,
            createTaskUseCase = createTaskUseCase,
            getActiveCommitmentsUseCase = getCommitments,
            createCommitmentUseCase = createCommitmentUseCase,
            logger = logger,
            userSessionProvider = provider
        )
        runCurrent()
        viewModel.loadData("plan1")
        runCurrent()
        oldLoadStarted.await()

        provider.setState(SessionState.Authenticated("user-2", isAnonymous = true))
        runCurrent()
        assertTrue(viewModel.uiState.value is PlanDetailUiState.Success)
        assertEquals("New user plan", (viewModel.uiState.value as PlanDetailUiState.Success).plan.title)

        finishOldLoad.complete(Unit)
        advanceUntilIdle()
        assertEquals("user-2", (viewModel.uiState.value as PlanDetailUiState.Success).plan.userId)
    }

    @Test
    fun `openEditPlanForm populates form fields from loaded plan`() = runTest {
        val testPlan = Plan(
            id = "plan1",
            userId = "user-1",
            goalId = "goal1",
            title = "Test Plan",
            description = "Test Description",
            status = PlanStatus.ACTIVE,
            createdAt = clock.currentTimeMillis(),
            updatedAt = clock.currentTimeMillis(),
            source = EntitySource.USER
        )
        planRepository.savePlan(testPlan)

        viewModel.loadData("plan1")
        advanceUntilIdle()

        viewModel.openEditPlanForm()

        assertTrue(viewModel.showEditPlanForm.value)
        assertEquals("Test Plan", viewModel.editPlanTitle.value)
        assertEquals("Test Description", viewModel.editPlanDescription.value)
        assertEquals(PlanStatus.ACTIVE, viewModel.editPlanStatus.value)
    }

    @Test
    fun `saveEditedPlan updates plan and refreshes uiState`() = runTest {
        val testPlan = Plan(
            id = "plan1",
            userId = "user-1",
            goalId = "goal1",
            title = "Old Title",
            description = "Old Desc",
            status = PlanStatus.ACTIVE,
            createdAt = clock.currentTimeMillis(),
            updatedAt = clock.currentTimeMillis(),
            source = EntitySource.USER
        )
        planRepository.savePlan(testPlan)

        viewModel.loadData("plan1")
        advanceUntilIdle()

        viewModel.openEditPlanForm()
        viewModel.updateEditPlanTitle("Updated Title")
        viewModel.updateEditPlanDescription("Updated Desc")

        viewModel.saveEditedPlan()
        advanceUntilIdle()

        org.junit.Assert.assertFalse(viewModel.showEditPlanForm.value)
        assertEquals("Plan updated successfully", viewModel.planUserMessage.value)

        val state = viewModel.uiState.value as PlanDetailUiState.Success
        assertEquals("Updated Title", state.plan.title)
        assertEquals("Updated Desc", state.plan.description)
    }

    @Test
    fun `saveEditedPlan with blank title sets planActionError`() = runTest {
        val testPlan = Plan(
            id = "plan1",
            userId = "user-1",
            goalId = "goal1",
            title = "Old Title",
            description = "Old Desc",
            status = PlanStatus.ACTIVE,
            createdAt = clock.currentTimeMillis(),
            updatedAt = clock.currentTimeMillis(),
            source = EntitySource.USER
        )
        planRepository.savePlan(testPlan)

        viewModel.loadData("plan1")
        advanceUntilIdle()

        viewModel.openEditPlanForm()
        viewModel.updateEditPlanTitle("   ")

        viewModel.saveEditedPlan()
        advanceUntilIdle()

        assertTrue(viewModel.showEditPlanForm.value)
        assertEquals("Plan title cannot be empty", viewModel.planActionError.value)
    }

    @Test
    fun `requestCompletePlan, cancelCompletePlan, confirmCompletePlan manage confirmation state`() = runTest {
        val testPlan = Plan(
            id = "plan1",
            userId = "user-1",
            goalId = "goal1",
            title = "Active Plan",
            description = "Desc",
            status = PlanStatus.ACTIVE,
            createdAt = clock.currentTimeMillis(),
            updatedAt = clock.currentTimeMillis(),
            source = EntitySource.USER
        )
        planRepository.savePlan(testPlan)

        viewModel.loadData("plan1")
        advanceUntilIdle()

        viewModel.requestCompletePlan()
        assertTrue(viewModel.showCompletePlanConfirmation.value)

        viewModel.cancelCompletePlan()
        org.junit.Assert.assertFalse(viewModel.showCompletePlanConfirmation.value)

        viewModel.requestCompletePlan()
        viewModel.confirmCompletePlan()
        advanceUntilIdle()

        org.junit.Assert.assertFalse(viewModel.showCompletePlanConfirmation.value)
        val state = viewModel.uiState.value as PlanDetailUiState.Success
        assertEquals(PlanStatus.COMPLETED, state.plan.status)
        assertEquals("Plan completed successfully", viewModel.planUserMessage.value)
    }

    @Test
    fun `updatePlanStatus transitions plan status to PAUSED and updates uiState`() = runTest {
        val testPlan = Plan(
            id = "plan1",
            userId = "user-1",
            goalId = "goal1",
            title = "Active Plan",
            description = "Desc",
            status = PlanStatus.ACTIVE,
            createdAt = clock.currentTimeMillis(),
            updatedAt = clock.currentTimeMillis(),
            source = EntitySource.USER
        )
        planRepository.savePlan(testPlan)

        viewModel.loadData("plan1")
        advanceUntilIdle()

        viewModel.updatePlanStatus(PlanStatus.PAUSED)
        advanceUntilIdle()

        val state = viewModel.uiState.value as PlanDetailUiState.Success
        assertEquals(PlanStatus.PAUSED, state.plan.status)
        assertEquals("Plan paused successfully", viewModel.planUserMessage.value)
    }

    @Test
    fun `updatePlanStatus surfaces error when transition is invalid`() = runTest {
        val testPlan = Plan(
            id = "plan1",
            userId = "user-1",
            goalId = "goal1",
            title = "Completed Plan",
            description = "Desc",
            status = PlanStatus.COMPLETED,
            createdAt = clock.currentTimeMillis(),
            updatedAt = clock.currentTimeMillis(),
            source = EntitySource.USER
        )
        planRepository.savePlan(testPlan)

        viewModel.loadData("plan1")
        advanceUntilIdle()

        // Invalid: COMPLETED -> PAUSED
        viewModel.updatePlanStatus(PlanStatus.PAUSED)
        advanceUntilIdle()

        val state = viewModel.uiState.value as PlanDetailUiState.Success
        assertEquals(PlanStatus.COMPLETED, state.plan.status)
        assertTrue(viewModel.planActionError.value?.contains("Invalid plan status transition") == true)
    }

    @Test
    fun `loadData populates reactive progress and updates after task mutation`() = runTest {
        val testPlan = Plan(
            id = "plan1",
            userId = "user-1",
            goalId = "goal1",
            title = "Test Plan",
            description = "Desc",
            status = PlanStatus.ACTIVE,
            createdAt = clock.currentTimeMillis(),
            updatedAt = clock.currentTimeMillis(),
            source = EntitySource.USER
        )
        planRepository.savePlan(testPlan)

        taskRepository.saveTask(
            com.sanket_satpute_20.ironmind.domain.model.Task(
                id = "t1",
                userId = "user-1",
                goalId = "goal1",
                planId = "plan1",
                title = "Task 1",
                description = "Desc",
                status = com.sanket_satpute_20.ironmind.domain.model.TaskStatus.COMPLETED,
                priority = 1,
                createdAt = clock.currentTimeMillis(),
                updatedAt = clock.currentTimeMillis(),
                source = EntitySource.USER
            )
        )
        taskRepository.saveTask(
            com.sanket_satpute_20.ironmind.domain.model.Task(
                id = "t2",
                userId = "user-1",
                goalId = "goal1",
                planId = "plan1",
                title = "Task 2",
                description = "Desc",
                status = com.sanket_satpute_20.ironmind.domain.model.TaskStatus.PENDING,
                priority = 1,
                createdAt = clock.currentTimeMillis(),
                updatedAt = clock.currentTimeMillis(),
                source = EntitySource.USER
            )
        )

        viewModel.loadData("plan1")
        advanceUntilIdle()

        val state = viewModel.uiState.value as PlanDetailUiState.Success
        assertEquals(2, state.progress.totalTasks)
        assertEquals(1, state.progress.completedTasks)
        assertEquals(1, state.progress.pendingTasks)
        assertEquals(50, state.progress.percentage)
        assertEquals(0.5f, state.progress.progressFraction, 0.001f)
    }
}
