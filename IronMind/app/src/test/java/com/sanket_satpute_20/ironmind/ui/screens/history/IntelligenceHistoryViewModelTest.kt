package com.sanket_satpute_20.ironmind.ui.screens.history

import com.sanket_satpute_20.ironmind.domain.ai.InterventionObjective
import com.sanket_satpute_20.ironmind.domain.ai.InterventionType
import com.sanket_satpute_20.ironmind.domain.common.Clock
import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.intervention.InterventionRecommendation
import com.sanket_satpute_20.ironmind.domain.model.intervention.InterventionRecommendationStatus
import com.sanket_satpute_20.ironmind.domain.repository.InterventionRecommendationRepository
import com.sanket_satpute_20.ironmind.domain.usecase.ai.GetIntelligenceHistoryUseCase
import com.sanket_satpute_20.ironmind.testutil.fake.FakeEventRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class IntelligenceHistoryViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var dummyInterventionRepo: FakeInterventionRecommendationRepository
    private lateinit var dummyEventRepo: FakeEventRepository
    private lateinit var realUseCase: GetIntelligenceHistoryUseCase
    private lateinit var fakeClock: FakeClock

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        dummyInterventionRepo = FakeInterventionRecommendationRepository()
        dummyEventRepo = FakeEventRepository()
        realUseCase = GetIntelligenceHistoryUseCase(dummyInterventionRepo, dummyEventRepo)
        fakeClock = FakeClock()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): IntelligenceHistoryViewModel {
        return IntelligenceHistoryViewModel(realUseCase, fakeClock)
    }

    private fun createRecommendation(
        timestamp: Long,
        rationale: String = "Because."
    ) = InterventionRecommendation(
        id = UUID.randomUUID().toString(),
        userId = "user-1",
        targetEntityType = null,
        targetEntityId = null,
        interventionType = InterventionType.BREAK_DOWN,
        objective = InterventionObjective.INITIATE_ACTION,
        rationale = rationale,
        suggestedAction = "Do something.",
        status = InterventionRecommendationStatus.ACCEPTED,
        createdAt = timestamp
    )

    @Test
    fun `Initial state is Loading`() = runTest {
        val viewModel = createViewModel()
        assertEquals(IntelligenceHistoryUiState.Loading, viewModel.uiState.value)
    }

    @Test
    fun `Successful load exposes items unchanged`() = runTest {
        dummyInterventionRepo.itemsToReturn = listOf(
            createRecommendation(timestamp = 2000L),
            createRecommendation(timestamp = 1000L)
        )

        val viewModel = createViewModel()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is IntelligenceHistoryUiState.Success)
        val successState = state as IntelligenceHistoryUiState.Success
        assertEquals(2, successState.items.size)
        // Verify items are passed through unchanged
        assertEquals(2000L, successState.items[0].recommendationTimestamp)
        assertEquals(1000L, successState.items[1].recommendationTimestamp)
    }

    @Test
    fun `Empty result is success with empty list, not error`() = runTest {
        dummyInterventionRepo.itemsToReturn = emptyList()

        val viewModel = createViewModel()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is IntelligenceHistoryUiState.Success)
        assertEquals(0, (state as IntelligenceHistoryUiState.Success).items.size)
    }

    @Test
    fun `Failure exposes error state with message`() = runTest {
        dummyInterventionRepo.exceptionToThrow = Exception("Database error")

        val viewModel = createViewModel()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is IntelligenceHistoryUiState.Error)
        assertEquals("Database error", (state as IntelligenceHistoryUiState.Error).message)
    }

    @Test
    fun `Failure with null message uses fallback`() = runTest {
        dummyInterventionRepo.exceptionToThrow = Exception()

        val viewModel = createViewModel()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is IntelligenceHistoryUiState.Error)
        assertEquals("Failed to load intelligence history", (state as IntelligenceHistoryUiState.Error).message)
    }

    @Test
    fun `User ID user-1 is passed to use case`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        assertEquals("user-1", dummyInterventionRepo.lastUserId)
    }

    @Test
    fun `Time window is 30 days before clock now`() = runTest {
        fakeClock.fixedTime = 100_000_000L
        
        val viewModel = createViewModel()
        advanceUntilIdle()

        val thirtyDaysMs = 30L * 24 * 60 * 60 * 1000
        assertEquals(100_000_000L - thirtyDaysMs, dummyInterventionRepo.lastStartTime)
        assertEquals(100_000_000L, dummyInterventionRepo.lastEndTime)
    }

    @Test
    fun `Limit is 50`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        assertEquals(50, dummyInterventionRepo.lastLimit)
    }

    @Test
    fun `Reload re-invokes use case and updates state`() = runTest {
        dummyInterventionRepo.itemsToReturn = emptyList()
        val viewModel = createViewModel()
        advanceUntilIdle()

        // Now change the result and reload
        dummyInterventionRepo.itemsToReturn = listOf(createRecommendation(1000L))
        viewModel.loadIntelligenceHistory()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is IntelligenceHistoryUiState.Success)
        assertEquals(1, (state as IntelligenceHistoryUiState.Success).items.size)
    }

    @Test
    fun `ViewModel does not transform or reorder returned items`() = runTest {
        dummyInterventionRepo.itemsToReturn = listOf(
            createRecommendation(timestamp = 3000L, rationale = "First"),
            createRecommendation(timestamp = 1000L, rationale = "Second"),
            createRecommendation(timestamp = 2000L, rationale = "Third")
        )

        val viewModel = createViewModel()
        advanceUntilIdle()

        val state = viewModel.uiState.value as IntelligenceHistoryUiState.Success
        assertEquals("First", state.items[0].rationale)
        assertEquals("Second", state.items[1].rationale)
        assertEquals("Third", state.items[2].rationale)
    }
}

private class FakeInterventionRecommendationRepository : InterventionRecommendationRepository {
    var itemsToReturn: List<InterventionRecommendation> = emptyList()
    var exceptionToThrow: Exception? = null
    
    var lastUserId: String? = null
    var lastStartTime: Long? = null
    var lastEndTime: Long? = null
    var lastLimit: Int? = null

    override suspend fun saveRecommendation(recommendation: InterventionRecommendation): Result<Unit, Exception> = Result.Success(Unit)
    override suspend fun getRecommendation(id: String): Result<InterventionRecommendation?, Exception> = Result.Success(null)
    override fun getPendingRecommendations(userId: String, currentTime: Long): Flow<List<InterventionRecommendation>> = emptyFlow()
    override suspend fun updateRecommendationStatus(id: String, status: InterventionRecommendationStatus): Result<Unit, Exception> = Result.Success(Unit)
    override suspend fun getEquivalentRecommendations(userId: String, objective: String, targetEntityType: String?, targetEntityId: String?): Result<List<InterventionRecommendation>, Exception> = Result.Success(emptyList())
    
    override suspend fun getRecommendationsForUser(userId: String, startTime: Long, endTime: Long, limit: Int): Result<List<InterventionRecommendation>, Exception> {
        lastUserId = userId
        lastStartTime = startTime
        lastEndTime = endTime
        lastLimit = limit
        
        exceptionToThrow?.let { return Result.Failure(it) }
        return Result.Success(itemsToReturn)
    }
}

private class FakeClock : Clock {
    var fixedTime: Long = System.currentTimeMillis()

    override fun currentTimeMillis(): Long = fixedTime
}
