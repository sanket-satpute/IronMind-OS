package com.sanket_satpute_20.ironmind.ui.screens.goals

import com.sanket_satpute_20.ironmind.domain.logging.IronLogger
import com.sanket_satpute_20.ironmind.domain.model.Goal
import com.sanket_satpute_20.ironmind.domain.model.GoalStatus
import com.sanket_satpute_20.ironmind.domain.model.EntitySource
import com.sanket_satpute_20.ironmind.domain.usecase.goal.EditGoalUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.goal.GetGoalUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.goal.UpdateGoalStatusUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.goal.GetGoalProgressUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.plan.CreatePlanUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.plan.GetPlansUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.plan.GetPlanProgressUseCase
import com.sanket_satpute_20.ironmind.domain.model.Task
import com.sanket_satpute_20.ironmind.domain.model.TaskStatus
import com.sanket_satpute_20.ironmind.testutil.TestDispatcherRule
import com.sanket_satpute_20.ironmind.testutil.fake.FakeClock
import com.sanket_satpute_20.ironmind.testutil.fake.FakeGoalRepository
import com.sanket_satpute_20.ironmind.testutil.fake.FakeIdGenerator
import com.sanket_satpute_20.ironmind.testutil.fake.FakePlanRepository
import com.sanket_satpute_20.ironmind.testutil.fake.FakeTaskRepository
import com.sanket_satpute_20.ironmind.testutil.fake.FakeEventRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class GoalDetailViewModelTest {

    @get:Rule
    val dispatcherRule = TestDispatcherRule()

    private lateinit var goalRepository: FakeGoalRepository
    private lateinit var planRepository: FakePlanRepository
    private lateinit var taskRepository: FakeTaskRepository
    private lateinit var getGoalUseCase: GetGoalUseCase
    private lateinit var getPlansUseCase: GetPlansUseCase
    private lateinit var getGoalProgressUseCase: GetGoalProgressUseCase
    private lateinit var getPlanProgressUseCase: GetPlanProgressUseCase
    private lateinit var createPlanUseCase: CreatePlanUseCase
    private lateinit var editGoalUseCase: EditGoalUseCase
    private lateinit var updateGoalStatusUseCase: UpdateGoalStatusUseCase
    private lateinit var logger: IronLogger
    private lateinit var clock: FakeClock
    private lateinit var idGenerator: FakeIdGenerator
    private lateinit var eventRepository: FakeEventRepository

    private lateinit var viewModel: GoalDetailViewModel

    @Before
    fun setup() {
        goalRepository = FakeGoalRepository()
        planRepository = FakePlanRepository()
        taskRepository = FakeTaskRepository()
        getGoalUseCase = GetGoalUseCase(goalRepository)
        getPlansUseCase = GetPlansUseCase(planRepository)
        getGoalProgressUseCase = GetGoalProgressUseCase(planRepository, taskRepository)
        getPlanProgressUseCase = GetPlanProgressUseCase(taskRepository)
        clock = FakeClock()
        idGenerator = FakeIdGenerator()
        eventRepository = FakeEventRepository()
        createPlanUseCase = CreatePlanUseCase(planRepository, idGenerator, clock, eventRepository)
        editGoalUseCase = EditGoalUseCase(goalRepository, clock, idGenerator, eventRepository)
        updateGoalStatusUseCase = UpdateGoalStatusUseCase(goalRepository, clock)
        
        logger = object : IronLogger {
            override fun logLifecycle(component: String, event: String, parameters: Map<String, Any?>) {}
        }

        viewModel = GoalDetailViewModel(
            getGoalUseCase = getGoalUseCase,
            getPlansUseCase = getPlansUseCase,
            createPlanUseCase = createPlanUseCase,
            logger = logger,
            userSessionProvider = null,
            editGoalUseCase = editGoalUseCase,
            updateGoalStatusUseCase = updateGoalStatusUseCase,
            getGoalProgressUseCase = getGoalProgressUseCase,
            getPlanProgressUseCase = getPlanProgressUseCase
        )
    }

    @Test
    fun `loadData loads goal and plans successfully`() = runTest {
        val testGoal = Goal(
            id = "goal1",
            userId = "user-1",
            title = "Test Goal",
            description = "Desc",
            why = "Why",
            importance = 5,
            status = GoalStatus.ACTIVE,
            createdAt = clock.currentTimeMillis(),
            updatedAt = clock.currentTimeMillis()
        )
        goalRepository.saveGoal(testGoal)

        viewModel.loadData("goal1")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is GoalDetailUiState.Success)
        val successState = state as GoalDetailUiState.Success
        assertEquals("Test Goal", successState.goal.title)
        assertTrue(successState.plans.isEmpty())
    }

    @Test
    fun `createPlan successfully adds plan and refreshes list`() = runTest {
        val testGoal = Goal(
            id = "goal1",
            userId = "user-1",
            title = "Test Goal",
            description = "Desc",
            why = "Why",
            importance = 5,
            status = GoalStatus.ACTIVE,
            createdAt = clock.currentTimeMillis(),
            updatedAt = clock.currentTimeMillis()
        )
        goalRepository.saveGoal(testGoal)

        viewModel.loadData("goal1")
        advanceUntilIdle()

        viewModel.updateNewPlanTitle("New Plan")
        viewModel.updateNewPlanDescription("Plan Desc")
        viewModel.createPlan()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is GoalDetailUiState.Success)
        val plans = (state as GoalDetailUiState.Success).plans
        assertEquals(1, plans.size)
        assertEquals("New Plan", plans[0].title)
        assertEquals("goal1", plans[0].goalId)
    }

    @Test
    fun `createPlan rejects blank title`() = runTest {
        viewModel.loadData("goal1")
        advanceUntilIdle()

        viewModel.updateNewPlanTitle("   ")
        viewModel.createPlan()
        advanceUntilIdle()

        val saveError = viewModel.saveError.value
        assertEquals("Title cannot be empty", saveError)
    }

    @Test
    fun `createPlan failure produces error state`() = runTest {
        val testGoal = Goal(
            id = "goal1",
            userId = "user-1",
            title = "Test Goal",
            description = "Desc",
            why = "Why",
            importance = 5,
            status = GoalStatus.ACTIVE,
            createdAt = clock.currentTimeMillis(),
            updatedAt = clock.currentTimeMillis()
        )
        goalRepository.saveGoal(testGoal)

        viewModel.loadData("goal1")
        advanceUntilIdle()

        planRepository.shouldFail = true

        viewModel.updateNewPlanTitle("New Plan")
        viewModel.createPlan()
        advanceUntilIdle()

        val saveError = viewModel.saveError.value
        assertEquals("Fake failure", saveError)
    }

    @Test
    fun `loadData handles plan load failure`() = runTest {
        val testGoal = Goal(
            id = "goal1",
            userId = "user-1",
            title = "Test Goal",
            description = "Desc",
            why = "Why",
            importance = 5,
            status = GoalStatus.ACTIVE,
            createdAt = clock.currentTimeMillis(),
            updatedAt = clock.currentTimeMillis()
        )
        goalRepository.saveGoal(testGoal)

        planRepository.shouldFail = true

        viewModel.loadData("goal1")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is GoalDetailUiState.Error)
        assertEquals("Fake failure", (state as GoalDetailUiState.Error).message)
    }

    @Test
    fun `openEditForm prepopulates current goal fields correctly`() = runTest {
        val testGoal = Goal(
            id = "goal1",
            userId = "user-1",
            title = "Test Goal",
            description = "Desc",
            why = "Why it matters",
            importance = 4,
            status = GoalStatus.ACTIVE,
            createdAt = clock.currentTimeMillis(),
            updatedAt = clock.currentTimeMillis()
        )
        goalRepository.saveGoal(testGoal)

        viewModel.loadData("goal1")
        advanceUntilIdle()

        viewModel.openEditForm()

        assertTrue(viewModel.showEditForm.value)
        assertEquals("Test Goal", viewModel.editGoalTitle.value)
        assertEquals("Desc", viewModel.editGoalDescription.value)
        assertEquals("Why it matters", viewModel.editGoalWhy.value)
        assertEquals(4f, viewModel.editGoalImportance.value)
        assertEquals(GoalStatus.ACTIVE, viewModel.editGoalStatus.value)
        assertNull(viewModel.editError.value)
    }

    @Test
    fun `saveGoalEdit updates title, description, why, importance and reflects immediately`() = runTest {
        val testGoal = Goal(
            id = "goal1",
            userId = "user-1",
            title = "Original Title",
            description = "Original Desc",
            why = "Original Why",
            importance = 3,
            status = GoalStatus.ACTIVE,
            createdAt = clock.currentTimeMillis(),
            updatedAt = clock.currentTimeMillis()
        )
        goalRepository.saveGoal(testGoal)

        viewModel.loadData("goal1")
        advanceUntilIdle()

        viewModel.openEditForm()
        viewModel.updateEditGoalTitle("Updated Title")
        viewModel.updateEditGoalDescription("Updated Desc")
        viewModel.updateEditGoalWhy("Updated Why")
        viewModel.updateEditGoalImportance(8f)

        clock.advanceTimeBy(1000)
        viewModel.saveGoalEdit()
        advanceUntilIdle()

        assertFalse(viewModel.showEditForm.value)
        assertEquals("Goal updated successfully", viewModel.userMessage.value)

        val state = viewModel.uiState.value
        assertTrue(state is GoalDetailUiState.Success)
        val goal = (state as GoalDetailUiState.Success).goal
        assertEquals("Updated Title", goal.title)
        assertEquals("Updated Desc", goal.description)
        assertEquals("Updated Why", goal.why)
        assertEquals(8, goal.importance)
        assertEquals(GoalStatus.ACTIVE, goal.status)
    }

    @Test
    fun `saveGoalEdit rejects blank title`() = runTest {
        val testGoal = Goal(
            id = "goal1",
            userId = "user-1",
            title = "Valid Title",
            description = "Desc",
            why = "Why",
            importance = 5,
            status = GoalStatus.ACTIVE,
            createdAt = clock.currentTimeMillis(),
            updatedAt = clock.currentTimeMillis()
        )
        goalRepository.saveGoal(testGoal)

        viewModel.loadData("goal1")
        advanceUntilIdle()

        viewModel.openEditForm()
        viewModel.updateEditGoalTitle("   ")
        viewModel.saveGoalEdit()
        advanceUntilIdle()

        assertTrue(viewModel.showEditForm.value)
        assertEquals("Title cannot be empty", viewModel.editError.value)

        // Goal in state remains untouched
        val state = viewModel.uiState.value
        assertTrue(state is GoalDetailUiState.Success)
        assertEquals("Valid Title", (state as GoalDetailUiState.Success).goal.title)
    }

    @Test
    fun `saveGoalEdit handles repository failure gracefully`() = runTest {
        val testGoal = Goal(
            id = "goal1",
            userId = "user-1",
            title = "Original Title",
            description = "Desc",
            why = "Why",
            importance = 5,
            status = GoalStatus.ACTIVE,
            createdAt = clock.currentTimeMillis(),
            updatedAt = clock.currentTimeMillis()
        )
        goalRepository.saveGoal(testGoal)

        viewModel.loadData("goal1")
        advanceUntilIdle()

        goalRepository.shouldFail = true

        viewModel.openEditForm()
        viewModel.updateEditGoalTitle("New Title")
        viewModel.saveGoalEdit()
        advanceUntilIdle()

        assertEquals("Fake failure", viewModel.editError.value)
        assertFalse(viewModel.isEditing.value)
    }

    @Test
    fun `updateStatus from ACTIVE to PAUSED updates status and preserves goal on screen`() = runTest {
        val testGoal = Goal(
            id = "goal1",
            userId = "user-1",
            title = "Test Goal",
            description = "Desc",
            why = "Why",
            importance = 5,
            status = GoalStatus.ACTIVE,
            createdAt = clock.currentTimeMillis(),
            updatedAt = clock.currentTimeMillis()
        )
        goalRepository.saveGoal(testGoal)

        viewModel.loadData("goal1")
        advanceUntilIdle()

        viewModel.updateStatus(GoalStatus.PAUSED)
        advanceUntilIdle()

        assertEquals("Goal status updated to PAUSED", viewModel.userMessage.value)

        val state = viewModel.uiState.value
        assertTrue(state is GoalDetailUiState.Success)
        val goal = (state as GoalDetailUiState.Success).goal
        assertEquals(GoalStatus.PAUSED, goal.status)
        assertNull(goal.completedAt)
    }

    @Test
    fun `updateStatus from ACTIVE to COMPLETED updates status, sets completedAt, and preserves goal on screen`() = runTest {
        val testGoal = Goal(
            id = "goal1",
            userId = "user-1",
            title = "Test Goal",
            description = "Desc",
            why = "Why",
            importance = 5,
            status = GoalStatus.ACTIVE,
            createdAt = clock.currentTimeMillis(),
            updatedAt = clock.currentTimeMillis()
        )
        goalRepository.saveGoal(testGoal)

        viewModel.loadData("goal1")
        advanceUntilIdle()

        clock.advanceTimeBy(5000)
        viewModel.updateStatus(GoalStatus.COMPLETED)
        advanceUntilIdle()

        assertEquals("Goal status updated to COMPLETED", viewModel.userMessage.value)

        val state = viewModel.uiState.value
        assertTrue(state is GoalDetailUiState.Success)
        val goal = (state as GoalDetailUiState.Success).goal
        assertEquals(GoalStatus.COMPLETED, goal.status)
        assertNotNull(goal.completedAt)
        assertEquals(clock.currentTimeMillis(), goal.completedAt)
    }

    @Test
    fun `updateStatus from COMPLETED back to ACTIVE clears completedAt`() = runTest {
        val testGoal = Goal(
            id = "goal1",
            userId = "user-1",
            title = "Test Goal",
            description = "Desc",
            why = "Why",
            importance = 5,
            status = GoalStatus.COMPLETED,
            completedAt = 1000L,
            createdAt = 1000L,
            updatedAt = 1000L
        )
        goalRepository.saveGoal(testGoal)

        viewModel.loadData("goal1")
        advanceUntilIdle()

        clock.advanceTimeBy(2000)
        viewModel.updateStatus(GoalStatus.ACTIVE)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is GoalDetailUiState.Success)
        val goal = (state as GoalDetailUiState.Success).goal
        assertEquals(GoalStatus.ACTIVE, goal.status)
        assertNull(goal.completedAt)
    }

    @Test
    fun `loadData populates goalProgress and planProgressMap for associated plans`() = runTest {
        val testGoal = Goal(
            id = "goal1",
            userId = "user-1",
            title = "Test Goal",
            description = "Desc",
            why = "Why",
            importance = 5,
            status = GoalStatus.ACTIVE,
            createdAt = 1000L,
            updatedAt = 1000L
        )
        goalRepository.saveGoal(testGoal)

        val plan1 = com.sanket_satpute_20.ironmind.domain.model.Plan(
            id = "p1",
            userId = "user-1",
            goalId = "goal1",
            title = "Plan 1",
            description = "Desc",
            status = com.sanket_satpute_20.ironmind.domain.model.PlanStatus.ACTIVE,
            createdAt = 1000L,
            updatedAt = 1000L,
            source = EntitySource.USER
        )
        planRepository.savePlan(plan1)

        taskRepository.saveTask(
            Task(
                id = "t1",
                userId = "user-1",
                goalId = "goal1",
                planId = "p1",
                title = "Task 1",
                description = "Desc",
                status = TaskStatus.COMPLETED,
                priority = 1,
                createdAt = 1000L,
                updatedAt = 1000L,
                source = EntitySource.USER
            )
        )
        taskRepository.saveTask(
            Task(
                id = "t2",
                userId = "user-1",
                goalId = "goal1",
                planId = "p1",
                title = "Task 2",
                description = "Desc",
                status = TaskStatus.PENDING,
                priority = 1,
                createdAt = 1000L,
                updatedAt = 1000L,
                source = EntitySource.USER
            )
        )

        viewModel.loadData("goal1")
        advanceUntilIdle()

        val state = viewModel.uiState.value as GoalDetailUiState.Success
        assertEquals(2, state.goalProgress.totalTasks)
        assertEquals(1, state.goalProgress.completedTasks)
        assertEquals(1, state.goalProgress.pendingTasks)
        assertEquals(50, state.goalProgress.percentage)

        val planProgress = state.planProgressMap["p1"]
        assertNotNull(planProgress)
        assertEquals(2, planProgress!!.totalTasks)
        assertEquals(1, planProgress.completedTasks)
        assertEquals(50, planProgress.percentage)
    }
}
