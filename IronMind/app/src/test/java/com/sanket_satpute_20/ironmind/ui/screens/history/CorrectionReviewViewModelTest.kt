package com.sanket_satpute_20.ironmind.ui.screens.history

import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.intervention.CausalHypothesisReference
import com.sanket_satpute_20.ironmind.domain.model.intervention.CausalHypothesisType
import com.sanket_satpute_20.ironmind.domain.model.intervention.CorrectionReviewItem
import com.sanket_satpute_20.ironmind.domain.model.intervention.CorrectionReviewStatus
import com.sanket_satpute_20.ironmind.domain.model.intervention.CorrectionScope
import com.sanket_satpute_20.ironmind.domain.session.SessionState
import com.sanket_satpute_20.ironmind.domain.usecase.intervention.ApproveCorrectionReviewUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.intervention.DismissCorrectionReviewUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.intervention.GetPendingCorrectionReviewsUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.intervention.RefuteCorrectionReviewUseCase
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
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CorrectionReviewViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var getPendingUseCase: GetPendingCorrectionReviewsUseCase
    private lateinit var approveUseCase: ApproveCorrectionReviewUseCase
    private lateinit var refuteUseCase: RefuteCorrectionReviewUseCase
    private lateinit var dismissUseCase: DismissCorrectionReviewUseCase
    private lateinit var sessionProvider: FakeUserSessionProvider

    private val reviewsFlow = MutableStateFlow<List<CorrectionReviewItem>>(emptyList())
    private val userId = "user-test-777"

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        getPendingUseCase = mockk(relaxed = true)
        approveUseCase = mockk(relaxed = true)
        refuteUseCase = mockk(relaxed = true)
        dismissUseCase = mockk(relaxed = true)
        sessionProvider = FakeUserSessionProvider(SessionState.Authenticated(userId, false))

        every { getPendingUseCase.observe(userId) } returns reviewsFlow
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createReview(id: String = "rev-1") = CorrectionReviewItem(
        id = id,
        userId = userId,
        sourceEventId = "ev-$id",
        recommendationId = "rec-1",
        causalHypothesis = CausalHypothesisReference("pat-1", CausalHypothesisType.PATTERN),
        correctionScope = CorrectionScope.INFERENCE_CORRECTION,
        correctedText = "Walk in evening",
        decisionState = "DEFERRED_FOR_CONSIDERATION",
        reviewStatus = CorrectionReviewStatus.PENDING,
        createdAt = 1000L,
        updatedAt = 1000L
    )

    @Test
    fun `initial state collects reviews from session user`() = runTest {
        val viewModel = CorrectionReviewViewModel(
            getPendingCorrectionReviewsUseCase = getPendingUseCase,
            approveCorrectionReviewUseCase = approveUseCase,
            refuteCorrectionReviewUseCase = refuteUseCase,
            dismissCorrectionReviewUseCase = dismissUseCase,
            userSessionProvider = sessionProvider
        )

        advanceUntilIdle()

        assertTrue(viewModel.uiState.value is CorrectionReviewUiState.Success)
        val state = viewModel.uiState.value as CorrectionReviewUiState.Success
        assertTrue(state.reviews.isEmpty())

        // Emit new review item
        reviewsFlow.value = listOf(createReview("rev-100"))
        advanceUntilIdle()

        val updatedState = viewModel.uiState.value as CorrectionReviewUiState.Success
        assertEquals(1, updatedState.reviews.size)
        assertEquals("rev-100", updatedState.reviews.first().id)
    }

    @Test
    fun `approve invokes ApproveCorrectionReviewUseCase under active session`() = runTest {
        coEvery { approveUseCase(userId, "rev-1", any()) } returns Result.Success(Unit)

        val viewModel = CorrectionReviewViewModel(
            getPendingCorrectionReviewsUseCase = getPendingUseCase,
            approveCorrectionReviewUseCase = approveUseCase,
            refuteCorrectionReviewUseCase = refuteUseCase,
            dismissCorrectionReviewUseCase = dismissUseCase,
            userSessionProvider = sessionProvider
        )
        advanceUntilIdle()

        viewModel.approve("rev-1", "Looks good")
        advanceUntilIdle()

        coVerify(exactly = 1) { approveUseCase(userId, "rev-1", "Looks good") }
        val state = viewModel.uiState.value as CorrectionReviewUiState.Success
        assertFalse(state.isSubmitting)
        assertNull(state.submitError)
    }

    @Test
    fun `refute invokes RefuteCorrectionReviewUseCase under active session`() = runTest {
        coEvery { refuteUseCase(userId, "rev-2", any()) } returns Result.Success(Unit)

        val viewModel = CorrectionReviewViewModel(
            getPendingCorrectionReviewsUseCase = getPendingUseCase,
            approveCorrectionReviewUseCase = approveUseCase,
            refuteCorrectionReviewUseCase = refuteUseCase,
            dismissCorrectionReviewUseCase = dismissUseCase,
            userSessionProvider = sessionProvider
        )
        advanceUntilIdle()

        viewModel.refute("rev-2", "Not true")
        advanceUntilIdle()

        coVerify(exactly = 1) { refuteUseCase(userId, "rev-2", "Not true") }
    }

    @Test
    fun `dismiss invokes DismissCorrectionReviewUseCase under active session`() = runTest {
        coEvery { dismissUseCase(userId, "rev-3", any()) } returns Result.Success(Unit)

        val viewModel = CorrectionReviewViewModel(
            getPendingCorrectionReviewsUseCase = getPendingUseCase,
            approveCorrectionReviewUseCase = approveUseCase,
            refuteCorrectionReviewUseCase = refuteUseCase,
            dismissCorrectionReviewUseCase = dismissUseCase,
            userSessionProvider = sessionProvider
        )
        advanceUntilIdle()

        viewModel.dismiss("rev-3")
        advanceUntilIdle()

        coVerify(exactly = 1) { dismissUseCase(userId, "rev-3", null) }
    }

    @Test
    fun `action failure exposes submitError in uiState`() = runTest {
        coEvery { approveUseCase(userId, "rev-err", any()) } returns Result.Failure(IllegalStateException("Invalid state transition"))

        val viewModel = CorrectionReviewViewModel(
            getPendingCorrectionReviewsUseCase = getPendingUseCase,
            approveCorrectionReviewUseCase = approveUseCase,
            refuteCorrectionReviewUseCase = refuteUseCase,
            dismissCorrectionReviewUseCase = dismissUseCase,
            userSessionProvider = sessionProvider
        )
        advanceUntilIdle()

        viewModel.approve("rev-err")
        advanceUntilIdle()

        val state = viewModel.uiState.value as CorrectionReviewUiState.Success
        assertFalse(state.isSubmitting)
        assertEquals("Invalid state transition", state.submitError)
    }
}

