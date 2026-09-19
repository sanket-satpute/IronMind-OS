package com.sanket_satpute_20.ironmind.ui.screens.reflection

import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.Commitment
import com.sanket_satpute_20.ironmind.domain.model.CommitmentStatus
import com.sanket_satpute_20.ironmind.domain.model.EntitySource
import com.sanket_satpute_20.ironmind.domain.model.Reflection
import com.sanket_satpute_20.ironmind.domain.usecase.commitment.GetCommitmentsForDateRangeUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.reflection.SaveReflectionUseCase
import com.sanket_satpute_20.ironmind.testutil.TestDispatcherRule
import com.sanket_satpute_20.ironmind.testutil.fake.FakeClock
import com.sanket_satpute_20.ironmind.testutil.fake.FakeCommitmentRepository
import com.sanket_satpute_20.ironmind.testutil.fake.FakeIdGenerator
import com.sanket_satpute_20.ironmind.testutil.fake.FakeEventRepository
import com.sanket_satpute_20.ironmind.testutil.fake.FakeSpeechToTextProvider
import com.sanket_satpute_20.ironmind.domain.logging.IronLogger
import com.sanket_satpute_20.ironmind.domain.usecase.reflection.FakeReflectionRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NightReflectionViewModelTest {

    @get:Rule
    val dispatcherRule = TestDispatcherRule()

    private lateinit var commitmentRepository: FakeCommitmentRepository
    private lateinit var reflectionRepository: FakeReflectionRepository
    private lateinit var clock: FakeClock
    private lateinit var idGenerator: FakeIdGenerator
    private lateinit var eventRepository: FakeEventRepository
    
    private lateinit var getCommitmentsForDateRangeUseCase: GetCommitmentsForDateRangeUseCase
    private lateinit var saveReflectionUseCase: SaveReflectionUseCase
    private lateinit var speechToTextProvider: FakeSpeechToTextProvider
    private lateinit var logger: IronLogger

    private lateinit var viewModel: NightReflectionViewModel

    @Before
    fun setup() {
        commitmentRepository = FakeCommitmentRepository()
        reflectionRepository = FakeReflectionRepository()
        clock = FakeClock()
        idGenerator = FakeIdGenerator()
        eventRepository = FakeEventRepository()
        speechToTextProvider = FakeSpeechToTextProvider()
        logger = object : IronLogger {
            override fun logLifecycle(component: String, event: String, parameters: Map<String, Any?>) {}
        }

        getCommitmentsForDateRangeUseCase = GetCommitmentsForDateRangeUseCase(commitmentRepository)
        saveReflectionUseCase = SaveReflectionUseCase(reflectionRepository, idGenerator, clock, eventRepository)
    }

    private suspend fun createCommitment(id: String, status: CommitmentStatus) {
        val commitment = Commitment(
            id = id,
            userId = "user-1",
            title = "Test",
            description = "",
            priority = 1,
            source = EntitySource.USER,
            status = status,
            committedAt = clock.currentTimeMillis(),
            createdAt = clock.currentTimeMillis(),
            updatedAt = clock.currentTimeMillis()
        )
        commitmentRepository.saveCommitment(commitment)
    }

    @Test
    fun `loadSummary computes stats correctly`() = runTest {
        createCommitment("1", CommitmentStatus.COMPLETED)
        createCommitment("2", CommitmentStatus.COMPLETED)
        createCommitment("3", CommitmentStatus.MISSED)
        createCommitment("4", CommitmentStatus.POSTPONED)
        createCommitment("5", CommitmentStatus.STARTED) // Active, but included in total

        viewModel = NightReflectionViewModel(getCommitmentsForDateRangeUseCase, saveReflectionUseCase, reflectionRepository, clock, speechToTextProvider, logger)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is NightReflectionUiState.Success)
        
        val summary = (state as NightReflectionUiState.Success).summary
        assertEquals(2, summary.completed.size)
        assertEquals(1, summary.missed.size)
        assertEquals(1, summary.postponed.size)
    }

    @Test
    fun `saveReflection succeeds and updates state`() = runTest {
        viewModel = NightReflectionViewModel(getCommitmentsForDateRangeUseCase, saveReflectionUseCase, reflectionRepository, clock, speechToTextProvider, logger)
        advanceUntilIdle()
        
        viewModel.saveReflection(content = "Good day")
        advanceUntilIdle()
        
        val state = viewModel.uiState.value
        assertTrue(state is NightReflectionUiState.Success)
        assertTrue((state as NightReflectionUiState.Success).isSaved)
    }
}
