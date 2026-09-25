package com.sanket_satpute_20.ironmind.ui.screens.goals

import com.sanket_satpute_20.ironmind.domain.logging.IronLogger
import com.sanket_satpute_20.ironmind.domain.model.Goal
import com.sanket_satpute_20.ironmind.domain.model.GoalStatus
import com.sanket_satpute_20.ironmind.domain.model.EntitySource
import com.sanket_satpute_20.ironmind.domain.usecase.goal.GetGoalUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.plan.CreatePlanUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.plan.GetPlansUseCase
import com.sanket_satpute_20.ironmind.testutil.TestDispatcherRule
import com.sanket_satpute_20.ironmind.testutil.fake.FakeClock
import com.sanket_satpute_20.ironmind.testutil.fake.FakeGoalRepository
import com.sanket_satpute_20.ironmind.testutil.fake.FakeIdGenerator
import com.sanket_satpute_20.ironmind.testutil.fake.FakePlanRepository
import com.sanket_satpute_20.ironmind.testutil.fake.FakeEventRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
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
    private lateinit var getGoalUseCase: GetGoalUseCase
    private lateinit var getPlansUseCase: GetPlansUseCase
    private lateinit var createPlanUseCase: CreatePlanUseCase
    private lateinit var logger: IronLogger
    private lateinit var clock: FakeClock
    private lateinit var idGenerator: FakeIdGenerator
    private lateinit var eventRepository: FakeEventRepository

    private lateinit var viewModel: GoalDetailViewModel

    @Before
    fun setup() {
        goalRepository = FakeGoalRepository()
        planRepository = FakePlanRepository()
        getGoalUseCase = GetGoalUseCase(goalRepository)
        getPlansUseCase = GetPlansUseCase(planRepository)
        clock = FakeClock()
        idGenerator = FakeIdGenerator()
        eventRepository = FakeEventRepository()
        createPlanUseCase = CreatePlanUseCase(planRepository, idGenerator, clock, eventRepository)
        
        logger = object : IronLogger {
            override fun logLifecycle(component: String, event: String, parameters: Map<String, Any?>) {}
        }

        viewModel = GoalDetailViewModel(getGoalUseCase, getPlansUseCase, createPlanUseCase, logger)
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
}
