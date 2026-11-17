package com.sanket_satpute_20.ironmind.ui.screens.history

import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.MemoryConfirmationState
import com.sanket_satpute_20.ironmind.domain.model.pattern.Pattern
import com.sanket_satpute_20.ironmind.domain.model.pattern.PatternStatus
import com.sanket_satpute_20.ironmind.domain.model.pattern.PatternType
import com.sanket_satpute_20.ironmind.domain.session.SessionState
import com.sanket_satpute_20.ironmind.domain.usecase.pattern.ConfirmPatternUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.pattern.DismissPatternUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.pattern.GetPatternsUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.pattern.RefutePatternUseCase
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
class PatternReviewViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var getPatternsUseCase: GetPatternsUseCase
    private lateinit var confirmPatternUseCase: ConfirmPatternUseCase
    private lateinit var refutePatternUseCase: RefutePatternUseCase
    private lateinit var dismissPatternUseCase: DismissPatternUseCase
    private lateinit var sessionProvider: FakeUserSessionProvider

    private val patternsFlow = MutableStateFlow<List<Pattern>>(emptyList())
    private val userId = "user-test-pattern"

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        getPatternsUseCase = mockk(relaxed = true)
        confirmPatternUseCase = mockk(relaxed = true)
        refutePatternUseCase = mockk(relaxed = true)
        dismissPatternUseCase = mockk(relaxed = true)
        sessionProvider = FakeUserSessionProvider(SessionState.Authenticated(userId, false))

        every { getPatternsUseCase.observe(userId) } returns patternsFlow
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createSamplePattern(id: String) = Pattern(
        id = id,
        userId = userId,
        fingerprint = "fp-$id",
        type = PatternType.TIME_PATTERN,
        description = "Works best around 10am",
        conditions = null,
        predictedBehavior = "High productivity",
        confidence = 0.82f,
        evidenceCount = 4,
        evidenceReferences = emptyList(),
        firstObservedAt = 1000L,
        lastObservedAt = 2000L,
        status = PatternStatus.ACTIVE,
        confirmationState = MemoryConfirmationState.UNCONFIRMED,
        createdAt = 1000L,
        updatedAt = 2000L
    )

    @Test
    fun `emits Success when patterns are observed`() = runTest {
        val viewModel = PatternReviewViewModel(
            getPatternsUseCase = getPatternsUseCase,
            confirmPatternUseCase = confirmPatternUseCase,
            refutePatternUseCase = refutePatternUseCase,
            dismissPatternUseCase = dismissPatternUseCase,
            userSessionProvider = sessionProvider
        )

        advanceUntilIdle()
        assertTrue(viewModel.uiState.value is PatternReviewUiState.Success)
        assertEquals(0, (viewModel.uiState.value as PatternReviewUiState.Success).patterns.size)

        patternsFlow.value = listOf(createSamplePattern("p1"))
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is PatternReviewUiState.Success)
        assertEquals(1, (state as PatternReviewUiState.Success).patterns.size)
        assertEquals("p1", state.patterns[0].id)
    }

    @Test
    fun `confirm calls confirmPatternUseCase with active userId`() = runTest {
        coEvery { confirmPatternUseCase(userId, "p1") } returns Result.Success(Unit)

        val viewModel = PatternReviewViewModel(
            getPatternsUseCase = getPatternsUseCase,
            confirmPatternUseCase = confirmPatternUseCase,
            refutePatternUseCase = refutePatternUseCase,
            dismissPatternUseCase = dismissPatternUseCase,
            userSessionProvider = sessionProvider
        )
        advanceUntilIdle()

        viewModel.confirm("p1")
        advanceUntilIdle()

        coVerify(exactly = 1) { confirmPatternUseCase(userId, "p1") }
    }

    @Test
    fun `refute calls refutePatternUseCase with active userId`() = runTest {
        coEvery { refutePatternUseCase(userId, "p2") } returns Result.Success(Unit)

        val viewModel = PatternReviewViewModel(
            getPatternsUseCase = getPatternsUseCase,
            confirmPatternUseCase = confirmPatternUseCase,
            refutePatternUseCase = refutePatternUseCase,
            dismissPatternUseCase = dismissPatternUseCase,
            userSessionProvider = sessionProvider
        )
        advanceUntilIdle()

        viewModel.refute("p2")
        advanceUntilIdle()

        coVerify(exactly = 1) { refutePatternUseCase(userId, "p2") }
    }

    @Test
    fun `dismiss calls dismissPatternUseCase with active userId`() = runTest {
        coEvery { dismissPatternUseCase(userId, "p3") } returns Result.Success(Unit)

        val viewModel = PatternReviewViewModel(
            getPatternsUseCase = getPatternsUseCase,
            confirmPatternUseCase = confirmPatternUseCase,
            refutePatternUseCase = refutePatternUseCase,
            dismissPatternUseCase = dismissPatternUseCase,
            userSessionProvider = sessionProvider
        )
        advanceUntilIdle()

        viewModel.dismiss("p3")
        advanceUntilIdle()

        coVerify(exactly = 1) { dismissPatternUseCase(userId, "p3") }
    }
}

