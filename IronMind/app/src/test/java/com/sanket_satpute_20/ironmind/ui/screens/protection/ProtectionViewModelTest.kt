package com.sanket_satpute_20.ironmind.ui.screens.protection

import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.ProtectionSessionStatus
import com.sanket_satpute_20.ironmind.domain.usecase.protection.GetActiveProtectionSessionUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.protection.StartProtectionSessionUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.protection.StopProtectionSessionUseCase
import com.sanket_satpute_20.ironmind.testutil.TestDispatcherRule
import com.sanket_satpute_20.ironmind.testutil.fake.FakeAppProtectionProvider
import com.sanket_satpute_20.ironmind.testutil.fake.FakeClock
import com.sanket_satpute_20.ironmind.testutil.fake.FakeEventRepository
import com.sanket_satpute_20.ironmind.testutil.fake.FakeIdGenerator
import com.sanket_satpute_20.ironmind.testutil.fake.FakeProtectionRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProtectionViewModelTest {

    @get:Rule
    val dispatcherRule = TestDispatcherRule()

    private lateinit var protectionRepository: FakeProtectionRepository
    private lateinit var appProtectionProvider: FakeAppProtectionProvider
    private lateinit var clock: FakeClock
    private lateinit var idGenerator: FakeIdGenerator
    private lateinit var eventRepository: FakeEventRepository

    private lateinit var getActiveSessionUseCase: GetActiveProtectionSessionUseCase
    private lateinit var startSessionUseCase: StartProtectionSessionUseCase
    private lateinit var stopSessionUseCase: StopProtectionSessionUseCase
    private lateinit var viewModel: ProtectionViewModel

    @Before
    fun setup() {
        protectionRepository = FakeProtectionRepository()
        appProtectionProvider = FakeAppProtectionProvider()
        clock = FakeClock()
        idGenerator = FakeIdGenerator()
        eventRepository = FakeEventRepository()

        getActiveSessionUseCase = GetActiveProtectionSessionUseCase(protectionRepository)
        startSessionUseCase = StartProtectionSessionUseCase(protectionRepository, appProtectionProvider, idGenerator, clock, eventRepository)
        stopSessionUseCase = StopProtectionSessionUseCase(protectionRepository, appProtectionProvider, clock, eventRepository)
    }

    private fun createViewModel() {
        viewModel = ProtectionViewModel(getActiveSessionUseCase, startSessionUseCase, stopSessionUseCase)
    }

    @Test
    fun `initial load with no active session produces NoActiveSession state`() = runTest {
        createViewModel()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value is ProtectionUiState.NoActiveSession)
    }

    @Test
    fun `initial load with active session produces ActiveSession state`() = runTest {
        startSessionUseCase("user-1", "rule-1", "commitment-1", "task-1", null, true, listOf("com.example.app"))
        createViewModel()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is ProtectionUiState.ActiveSession)
        assertEquals(ProtectionSessionStatus.ACTIVE, (state as ProtectionUiState.ActiveSession).session.status)
    }

    @Test
    fun `startProtection success changes state to ActiveSession`() = runTest {
        createViewModel()
        advanceUntilIdle()

        viewModel.startProtection(listOf("com.example.app"))
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is ProtectionUiState.ActiveSession)
        val session = (state as ProtectionUiState.ActiveSession).session
        assertEquals(ProtectionSessionStatus.ACTIVE, session.status)
        assertEquals("user-1", session.userId)
    }

    @Test
    fun `startProtection failure produces Error state and does not show active`() = runTest {
        appProtectionProvider.permissionsGranted = false
        createViewModel()
        advanceUntilIdle()

        viewModel.startProtection(listOf("com.example.app"))
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is ProtectionUiState.Error)
    }

    @Test
    fun `stopProtection success changes state to NoActiveSession`() = runTest {
        startSessionUseCase("user-1", "rule-1", "commitment-1", "task-1", null, true, listOf("com.example.app"))
        createViewModel()
        advanceUntilIdle()

        viewModel.stopProtection()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value is ProtectionUiState.NoActiveSession)
    }

    @Test
    fun `startProtection validation fails on empty targets`() = runTest {
        createViewModel()
        advanceUntilIdle()

        viewModel.startProtection(emptyList())
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is ProtectionUiState.Error)
        assertEquals("Please select at least one application to protect.", (state as ProtectionUiState.Error).message)
    }

    @Test
    fun `stopProtection failure produces Error state`() = runTest {
        startSessionUseCase("user-1", "rule-1", "commitment-1", "task-1", null, true, listOf("com.example.app"))
        createViewModel()
        advanceUntilIdle()

        appProtectionProvider.shouldFailRemoval = true
        viewModel.stopProtection()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is ProtectionUiState.Error)
        assertEquals("Failed to remove protection: Simulated removal failure", (state as ProtectionUiState.Error).message)
    }
}
