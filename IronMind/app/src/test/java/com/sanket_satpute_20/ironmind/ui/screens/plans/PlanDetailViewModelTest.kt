package com.sanket_satpute_20.ironmind.ui.screens.plans

import com.sanket_satpute_20.ironmind.domain.logging.IronLogger
import com.sanket_satpute_20.ironmind.domain.model.Plan
import com.sanket_satpute_20.ironmind.domain.model.PlanStatus
import com.sanket_satpute_20.ironmind.domain.model.EntitySource
import com.sanket_satpute_20.ironmind.domain.usecase.plan.GetPlanUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.task.CreateTaskUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.task.GetTasksUseCase
import com.sanket_satpute_20.ironmind.testutil.TestDispatcherRule
import com.sanket_satpute_20.ironmind.testutil.fake.FakeClock
import com.sanket_satpute_20.ironmind.testutil.fake.FakeIdGenerator
import com.sanket_satpute_20.ironmind.testutil.fake.FakePlanRepository
import com.sanket_satpute_20.ironmind.testutil.fake.FakeTaskRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PlanDetailViewModelTest {

    @get:Rule
    val dispatcherRule = TestDispatcherRule()

    private lateinit var planRepository: FakePlanRepository
    private lateinit var taskRepository: FakeTaskRepository
    private lateinit var getPlanUseCase: GetPlanUseCase
    private lateinit var getTasksUseCase: GetTasksUseCase
    private lateinit var createTaskUseCase: CreateTaskUseCase
    private lateinit var logger: IronLogger
    private lateinit var clock: FakeClock
    private lateinit var idGenerator: FakeIdGenerator

    private lateinit var viewModel: PlanDetailViewModel

    @Before
    fun setup() {
        planRepository = FakePlanRepository()
        taskRepository = FakeTaskRepository()
        getPlanUseCase = GetPlanUseCase(planRepository)
        getTasksUseCase = GetTasksUseCase(taskRepository)
        clock = FakeClock()
        idGenerator = FakeIdGenerator()
        createTaskUseCase = CreateTaskUseCase(taskRepository, idGenerator, clock)
        
        logger = object : IronLogger {
            override fun logLifecycle(component: String, event: String, parameters: Map<String, Any?>) {}
        }

        viewModel = PlanDetailViewModel(getPlanUseCase, getTasksUseCase, createTaskUseCase, logger)
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
}
