package com.sanket_satpute_20.ironmind.ui.screens.history

import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.TimelineItem
import com.sanket_satpute_20.ironmind.domain.usecase.history.GetTimelineUseCase
import com.sanket_satpute_20.ironmind.testutil.fake.FakeCommitmentRepository
import com.sanket_satpute_20.ironmind.testutil.fake.FakeEventRepository
import com.sanket_satpute_20.ironmind.testutil.fake.FakeGoalRepository
import com.sanket_satpute_20.ironmind.testutil.fake.FakeReflectionRepository
import com.sanket_satpute_20.ironmind.testutil.fake.FakePlanRepository
import com.sanket_satpute_20.ironmind.testutil.fake.FakeTaskRepository
import com.sanket_satpute_20.ironmind.testutil.fake.FakeIronLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HistoryViewModelTest {

    private lateinit var eventRepository: FakeEventRepository
    private lateinit var commitmentRepository: FakeCommitmentRepository
    private lateinit var reflectionRepository: FakeReflectionRepository
    private lateinit var goalRepository: FakeGoalRepository
    private lateinit var planRepository: FakePlanRepository
    private lateinit var taskRepository: FakeTaskRepository
    private lateinit var logger: FakeIronLogger
    private lateinit var getTimelineUseCase: GetTimelineUseCase
    private lateinit var viewModel: HistoryViewModel

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)

        eventRepository = FakeEventRepository()
        commitmentRepository = FakeCommitmentRepository()
        reflectionRepository = FakeReflectionRepository()
        goalRepository = FakeGoalRepository()
        planRepository = FakePlanRepository()
        taskRepository = FakeTaskRepository()
        logger = FakeIronLogger()

        getTimelineUseCase = GetTimelineUseCase(
            eventRepository,
            commitmentRepository,
            reflectionRepository,
            goalRepository,
            planRepository,
            taskRepository,
            logger
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `loadTimeline loads data successfully`() = runTest {
        viewModel = HistoryViewModel(getTimelineUseCase)

        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is HistoryUiState.Success)
        assertEquals(0, (state as HistoryUiState.Success).timelineItems.size)
    }
}
