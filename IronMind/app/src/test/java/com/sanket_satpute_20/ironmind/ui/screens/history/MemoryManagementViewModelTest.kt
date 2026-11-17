package com.sanket_satpute_20.ironmind.ui.screens.history

import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.EntitySource
import com.sanket_satpute_20.ironmind.domain.model.Memory
import com.sanket_satpute_20.ironmind.domain.model.MemoryConfirmationState
import com.sanket_satpute_20.ironmind.domain.model.MemoryStatus
import com.sanket_satpute_20.ironmind.domain.session.SessionState
import com.sanket_satpute_20.ironmind.domain.usecase.memory.ConfirmMemoryUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.memory.CorrectMemoryUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.memory.DeleteMemoryUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.memory.GetMemoriesUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.memory.RejectMemoryUseCase
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
class MemoryManagementViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var getMemoriesUseCase: GetMemoriesUseCase
    private lateinit var confirmMemoryUseCase: ConfirmMemoryUseCase
    private lateinit var correctMemoryUseCase: CorrectMemoryUseCase
    private lateinit var rejectMemoryUseCase: RejectMemoryUseCase
    private lateinit var deleteMemoryUseCase: DeleteMemoryUseCase
    private lateinit var sessionProvider: FakeUserSessionProvider

    private val memoriesFlow = MutableStateFlow<List<Memory>>(emptyList())
    private val userId = "user-test-memory"

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        getMemoriesUseCase = mockk(relaxed = true)
        confirmMemoryUseCase = mockk(relaxed = true)
        correctMemoryUseCase = mockk(relaxed = true)
        rejectMemoryUseCase = mockk(relaxed = true)
        deleteMemoryUseCase = mockk(relaxed = true)
        sessionProvider = FakeUserSessionProvider(SessionState.Authenticated(userId, false))

        every { getMemoriesUseCase.observe(userId) } returns memoriesFlow
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createSampleMemory(id: String) = Memory(
        id = id,
        userId = userId,
        type = "habit",
        content = "Prefers evening runs",
        source = EntitySource.AI,
        confidence = 0.8f,
        evidenceCount = 2,
        firstObservedAt = 1000L,
        lastObservedAt = 2000L,
        confirmationState = MemoryConfirmationState.UNCONFIRMED,
        status = MemoryStatus.ACTIVE,
        expiresAt = null,
        createdAt = 1000L,
        updatedAt = 2000L
    )

    @Test
    fun `emits Success when memories are observed`() = runTest {
        val viewModel = MemoryManagementViewModel(
            getMemoriesUseCase = getMemoriesUseCase,
            confirmMemoryUseCase = confirmMemoryUseCase,
            correctMemoryUseCase = correctMemoryUseCase,
            rejectMemoryUseCase = rejectMemoryUseCase,
            deleteMemoryUseCase = deleteMemoryUseCase,
            userSessionProvider = sessionProvider
        )

        advanceUntilIdle()
        assertTrue(viewModel.uiState.value is MemoryManagementUiState.Success)
        assertEquals(0, (viewModel.uiState.value as MemoryManagementUiState.Success).memories.size)

        memoriesFlow.value = listOf(createSampleMemory("m1"))
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is MemoryManagementUiState.Success)
        assertEquals(1, (state as MemoryManagementUiState.Success).memories.size)
        assertEquals("m1", state.memories[0].id)
    }

    @Test
    fun `confirm calls confirmMemoryUseCase with active userId`() = runTest {
        val mem = createSampleMemory("m1")
        coEvery { confirmMemoryUseCase(userId, "m1") } returns Result.Success(mem)

        val viewModel = MemoryManagementViewModel(
            getMemoriesUseCase = getMemoriesUseCase,
            confirmMemoryUseCase = confirmMemoryUseCase,
            correctMemoryUseCase = correctMemoryUseCase,
            rejectMemoryUseCase = rejectMemoryUseCase,
            deleteMemoryUseCase = deleteMemoryUseCase,
            userSessionProvider = sessionProvider
        )
        advanceUntilIdle()

        viewModel.confirm("m1")
        advanceUntilIdle()

        coVerify(exactly = 1) { confirmMemoryUseCase(userId, "m1") }
    }

    @Test
    fun `correct calls correctMemoryUseCase with active userId`() = runTest {
        val mem = createSampleMemory("m2")
        coEvery { correctMemoryUseCase(userId, "m2", "Prefers morning runs") } returns Result.Success(mem)

        val viewModel = MemoryManagementViewModel(
            getMemoriesUseCase = getMemoriesUseCase,
            confirmMemoryUseCase = confirmMemoryUseCase,
            correctMemoryUseCase = correctMemoryUseCase,
            rejectMemoryUseCase = rejectMemoryUseCase,
            deleteMemoryUseCase = deleteMemoryUseCase,
            userSessionProvider = sessionProvider
        )
        advanceUntilIdle()

        viewModel.correct("m2", "Prefers morning runs")
        advanceUntilIdle()

        coVerify(exactly = 1) { correctMemoryUseCase(userId, "m2", "Prefers morning runs") }
    }

    @Test
    fun `reject calls rejectMemoryUseCase with active userId`() = runTest {
        coEvery { rejectMemoryUseCase(userId, "m3") } returns Result.Success(Unit)

        val viewModel = MemoryManagementViewModel(
            getMemoriesUseCase = getMemoriesUseCase,
            confirmMemoryUseCase = confirmMemoryUseCase,
            correctMemoryUseCase = correctMemoryUseCase,
            rejectMemoryUseCase = rejectMemoryUseCase,
            deleteMemoryUseCase = deleteMemoryUseCase,
            userSessionProvider = sessionProvider
        )
        advanceUntilIdle()

        viewModel.reject("m3")
        advanceUntilIdle()

        coVerify(exactly = 1) { rejectMemoryUseCase(userId, "m3") }
    }

    @Test
    fun `delete calls deleteMemoryUseCase with active userId`() = runTest {
        coEvery { deleteMemoryUseCase(userId, "m4") } returns Result.Success(Unit)

        val viewModel = MemoryManagementViewModel(
            getMemoriesUseCase = getMemoriesUseCase,
            confirmMemoryUseCase = confirmMemoryUseCase,
            correctMemoryUseCase = correctMemoryUseCase,
            rejectMemoryUseCase = rejectMemoryUseCase,
            deleteMemoryUseCase = deleteMemoryUseCase,
            userSessionProvider = sessionProvider
        )
        advanceUntilIdle()

        viewModel.delete("m4")
        advanceUntilIdle()

        coVerify(exactly = 1) { deleteMemoryUseCase(userId, "m4") }
    }
}

