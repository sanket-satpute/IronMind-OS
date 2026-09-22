package com.sanket_satpute_20.ironmind.ui.screens.today

import com.sanket_satpute_20.ironmind.domain.model.CommitmentStatus
import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.usecase.commitment.CreateCommitmentUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.commitment.GetActiveCommitmentsUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.commitment.UpdateCommitmentStatusUseCase
import com.sanket_satpute_20.ironmind.testutil.TestDispatcherRule
import com.sanket_satpute_20.ironmind.testutil.fake.FakeClock
import com.sanket_satpute_20.ironmind.testutil.fake.FakeCommitmentRepository
import com.sanket_satpute_20.ironmind.testutil.fake.FakeIdGenerator
import com.sanket_satpute_20.ironmind.testutil.fake.FakeOutcomeRepository
import com.sanket_satpute_20.ironmind.testutil.fake.FakeReminderScheduler
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
class TodayViewModelTest {

    @get:Rule
    val dispatcherRule = TestDispatcherRule()

    private lateinit var repository: FakeCommitmentRepository
    private lateinit var outcomeRepository: FakeOutcomeRepository
    private lateinit var getActiveCommitmentsUseCase: GetActiveCommitmentsUseCase
    private lateinit var updateCommitmentStatusUseCase: UpdateCommitmentStatusUseCase
    private lateinit var createCommitmentUseCase: CreateCommitmentUseCase
    private lateinit var clock: FakeClock
    private lateinit var idGenerator: FakeIdGenerator
    private lateinit var reminderScheduler: FakeReminderScheduler
    private lateinit var eventRepository: FakeEventRepository

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
    }

    private fun createViewModel() {
        viewModel = TodayViewModel(
            getActiveCommitmentsUseCase,
            updateCommitmentStatusUseCase
        )
    }

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
}


