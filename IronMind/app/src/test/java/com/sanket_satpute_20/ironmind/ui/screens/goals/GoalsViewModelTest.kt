package com.sanket_satpute_20.ironmind.ui.screens.goals

import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.logging.IronLogger
import com.sanket_satpute_20.ironmind.domain.model.Goal
import com.sanket_satpute_20.ironmind.domain.model.GoalStatus
import com.sanket_satpute_20.ironmind.domain.usecase.goal.CreateGoalUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.goal.GetGoalsUseCase
import com.sanket_satpute_20.ironmind.testutil.TestDispatcherRule
import com.sanket_satpute_20.ironmind.testutil.fake.FakeClock
import com.sanket_satpute_20.ironmind.testutil.fake.FakeIdGenerator
import com.sanket_satpute_20.ironmind.testutil.fake.FakeEventRepository
import com.sanket_satpute_20.ironmind.domain.repository.GoalRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class FakeGoalRepository : GoalRepository {
    private val goals = mutableListOf<Goal>()
    var shouldFail = false

    override suspend fun saveGoal(goal: Goal): Result<Unit, Exception> {
        if (shouldFail) return Result.Failure(Exception("Simulated save failure"))
        goals.removeIf { it.id == goal.id }
        goals.add(goal)
        return Result.Success(Unit)
    }

    override suspend fun getGoal(id: String): Result<Goal?, Exception> {
        return Result.Success(goals.find { it.id == id })
    }

    override suspend fun getGoalsForUser(userId: String): Result<List<Goal>, Exception> {
        if (shouldFail) return Result.Failure(Exception("Simulated load failure"))
        return Result.Success(goals.filter { it.userId == userId })
    }

    override suspend fun searchGoals(userId: String, query: String): Result<List<Goal>, Exception> {
        return Result.Success(goals.filter { it.userId == userId && it.title.contains(query, ignoreCase = true) })
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class GoalsViewModelTest {

    @get:Rule
    val dispatcherRule = TestDispatcherRule()

    private lateinit var goalRepository: FakeGoalRepository
    private lateinit var getGoalsUseCase: GetGoalsUseCase
    private lateinit var createGoalUseCase: CreateGoalUseCase
    private lateinit var logger: IronLogger
    private lateinit var clock: FakeClock
    private lateinit var idGenerator: FakeIdGenerator
    private lateinit var eventRepository: FakeEventRepository

    private lateinit var viewModel: GoalsViewModel

    @Before
    fun setup() {
        goalRepository = FakeGoalRepository()
        getGoalsUseCase = GetGoalsUseCase(goalRepository)
        clock = FakeClock()
        idGenerator = FakeIdGenerator()
        eventRepository = FakeEventRepository()
        createGoalUseCase = CreateGoalUseCase(goalRepository, idGenerator, clock, eventRepository)
        
        logger = object : IronLogger {
            override fun logLifecycle(component: String, event: String, parameters: Map<String, Any?>) {}
        }
    }

    @Test
    fun `loadGoals loads empty list successfully`() = runTest {
        viewModel = GoalsViewModel(getGoalsUseCase, createGoalUseCase, logger)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is GoalsUiState.Success)
        assertTrue((state as GoalsUiState.Success).goals.isEmpty())
    }

    @Test
    fun `createGoal successfully adds goal and refreshes list`() = runTest {
        viewModel = GoalsViewModel(getGoalsUseCase, createGoalUseCase, logger)
        advanceUntilIdle()

        viewModel.updateNewGoalTitle("Learn Compose")
        viewModel.updateNewGoalDescription("Master UI toolkit")
        viewModel.updateNewGoalWhy("For career growth")
        viewModel.updateNewGoalImportance(9f)

        viewModel.createGoal()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is GoalsUiState.Success)
        val goals = (state as GoalsUiState.Success).goals
        assertEquals(1, goals.size)
        assertEquals("Learn Compose", goals[0].title)
        
        // Assert form was cleared and closed
        assertEquals(false, viewModel.showCreateForm.value)
        assertEquals("", viewModel.newGoalTitle.value)
    }

    @Test
    fun `createGoal validates blank title`() = runTest {
        viewModel = GoalsViewModel(getGoalsUseCase, createGoalUseCase, logger)
        advanceUntilIdle()

        viewModel.updateNewGoalTitle("") // Blank
        
        viewModel.createGoal()
        advanceUntilIdle()

        val saveError = viewModel.saveError.value
        assertEquals("Title cannot be empty", saveError)
        
        val state = viewModel.uiState.value
        assertTrue(state is GoalsUiState.Success)
        assertTrue((state as GoalsUiState.Success).goals.isEmpty())
    }

    @Test
    fun `loadGoals handles repository failure`() = runTest {
        goalRepository.shouldFail = true
        viewModel = GoalsViewModel(getGoalsUseCase, createGoalUseCase, logger)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is GoalsUiState.Error)
        assertEquals("Simulated load failure", (state as GoalsUiState.Error).message)
    }

    @Test
    fun `createGoal handles repository save failure`() = runTest {
        viewModel = GoalsViewModel(getGoalsUseCase, createGoalUseCase, logger)
        advanceUntilIdle()

        goalRepository.shouldFail = true
        viewModel.updateNewGoalTitle("Learn Compose")
        
        viewModel.createGoal()
        advanceUntilIdle()

        val saveError = viewModel.saveError.value
        assertEquals("Simulated save failure", saveError)
    }
}
