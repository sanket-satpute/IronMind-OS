package com.sanket_satpute_20.ironmind.ui.screens.history

import com.sanket_satpute_20.ironmind.domain.ai.BarrierCategory
import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.barrier.BarrierConfirmationState
import com.sanket_satpute_20.ironmind.domain.model.barrier.BarrierHypothesis
import com.sanket_satpute_20.ironmind.domain.model.barrier.BarrierStatus
import com.sanket_satpute_20.ironmind.domain.session.SessionState
import com.sanket_satpute_20.ironmind.domain.usecase.barrier.ConfirmBarrierUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.barrier.DismissBarrierUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.barrier.GetBarriersUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.barrier.RefuteBarrierUseCase
import com.sanket_satpute_20.ironmind.testutil.FakeUserSessionProvider
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
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
class BarrierReviewViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var getBarriersUseCase: GetBarriersUseCase
    private lateinit var confirmBarrierUseCase: ConfirmBarrierUseCase
    private lateinit var refuteBarrierUseCase: RefuteBarrierUseCase
    private lateinit var dismissBarrierUseCase: DismissBarrierUseCase
    private lateinit var sessionProvider: FakeUserSessionProvider

    private val barriersFlow = MutableStateFlow<List<BarrierHypothesis>>(emptyList())
    private val userId = "user-test-barrier"

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        getBarriersUseCase = mockk(relaxed = true)
        confirmBarrierUseCase = mockk(relaxed = true)
        refuteBarrierUseCase = mockk(relaxed = true)
        dismissBarrierUseCase = mockk(relaxed = true)
        sessionProvider = FakeUserSessionProvider(SessionState.Authenticated(userId, false))

        every { getBarriersUseCase.observe(userId) } returns barriersFlow
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createSampleBarrier(id: String) = BarrierHypothesis(
        id = id,
        userId = userId,
        category = BarrierCategory.DISTRACTION,
        description = "Prone to phone notifications distraction",
        confirmationState = BarrierConfirmationState.UNCONFIRMED,
        status = BarrierStatus.ACTIVE,
        sourceReflectionId = "refl-1",
        firstObservedAt = 1000L,
        lastObservedAt = 2000L
    )

    @Test
    fun `emits Success when barriers are observed`() = runTest {
        val viewModel = BarrierReviewViewModel(
            getBarriersUseCase = getBarriersUseCase,
            confirmBarrierUseCase = confirmBarrierUseCase,
            refuteBarrierUseCase = refuteBarrierUseCase,
            dismissBarrierUseCase = dismissBarrierUseCase,
            userSessionProvider = sessionProvider
        )

        advanceUntilIdle()
        assertTrue(viewModel.uiState.value is BarrierReviewUiState.Success)
        assertEquals(0, (viewModel.uiState.value as BarrierReviewUiState.Success).barriers.size)

        barriersFlow.value = listOf(createSampleBarrier("b1"))
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is BarrierReviewUiState.Success)
        assertEquals(1, (state as BarrierReviewUiState.Success).barriers.size)
        assertEquals("b1", state.barriers[0].id)
    }

    @Test
    fun `confirm calls confirmBarrierUseCase with active userId`() = runTest {
        coEvery { confirmBarrierUseCase(userId, "b1") } returns Result.Success(Unit)

        val viewModel = BarrierReviewViewModel(
            getBarriersUseCase = getBarriersUseCase,
            confirmBarrierUseCase = confirmBarrierUseCase,
            refuteBarrierUseCase = refuteBarrierUseCase,
            dismissBarrierUseCase = dismissBarrierUseCase,
            userSessionProvider = sessionProvider
        )
        advanceUntilIdle()

        viewModel.confirm("b1")
        advanceUntilIdle()

        coVerify(exactly = 1) { confirmBarrierUseCase(userId, "b1") }
    }

    @Test
    fun `refute calls refuteBarrierUseCase with active userId`() = runTest {
        coEvery { refuteBarrierUseCase(userId, "b2") } returns Result.Success(Unit)

        val viewModel = BarrierReviewViewModel(
            getBarriersUseCase = getBarriersUseCase,
            confirmBarrierUseCase = confirmBarrierUseCase,
            refuteBarrierUseCase = refuteBarrierUseCase,
            dismissBarrierUseCase = dismissBarrierUseCase,
            userSessionProvider = sessionProvider
        )
        advanceUntilIdle()

        viewModel.refute("b2")
        advanceUntilIdle()

        coVerify(exactly = 1) { refuteBarrierUseCase(userId, "b2") }
    }

    @Test
    fun `dismiss calls dismissBarrierUseCase with active userId`() = runTest {
        coEvery { dismissBarrierUseCase(userId, "b3") } returns Result.Success(Unit)

        val viewModel = BarrierReviewViewModel(
            getBarriersUseCase = getBarriersUseCase,
            confirmBarrierUseCase = confirmBarrierUseCase,
            refuteBarrierUseCase = refuteBarrierUseCase,
            dismissBarrierUseCase = dismissBarrierUseCase,
            userSessionProvider = sessionProvider
        )
        advanceUntilIdle()

        viewModel.dismiss("b3")
        advanceUntilIdle()

        coVerify(exactly = 1) { dismissBarrierUseCase(userId, "b3") }
    }
}

