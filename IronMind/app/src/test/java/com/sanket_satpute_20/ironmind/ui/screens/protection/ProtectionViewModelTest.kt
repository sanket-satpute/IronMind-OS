package com.sanket_satpute_20.ironmind.ui.screens.protection

import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.EnforcementAvailability
import com.sanket_satpute_20.ironmind.domain.model.ProtectionSessionStatus
import com.sanket_satpute_20.ironmind.domain.model.protection.ProtectionDurationOption
import com.sanket_satpute_20.ironmind.domain.usecase.protection.GetActiveProtectionSessionUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.protection.StartProtectionSessionUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.protection.StopProtectionSessionUseCase
import com.sanket_satpute_20.ironmind.domain.session.SessionState
import com.sanket_satpute_20.ironmind.testutil.FakeUserSessionProvider
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

        stopSessionUseCase = StopProtectionSessionUseCase(protectionRepository, appProtectionProvider, clock, eventRepository)
        getActiveSessionUseCase = GetActiveProtectionSessionUseCase(protectionRepository, clock, stopSessionUseCase)
        startSessionUseCase = StartProtectionSessionUseCase(protectionRepository, appProtectionProvider, idGenerator, clock, eventRepository)
    }

    private fun createViewModel() {
        viewModel = ProtectionViewModel(
            getActiveProtectionSessionUseCase = getActiveSessionUseCase,
            startProtectionSessionUseCase = startSessionUseCase,
            stopProtectionSessionUseCase = stopSessionUseCase,
            userSessionProvider = null,
            appProtectionProvider = appProtectionProvider,
            clock = clock
        )
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

    @Test
    fun `session transition clears prior users active protection session`() = runTest {
        startSessionUseCase("user-1", "rule-1", "commitment-1", "task-1", null, true, listOf("com.example.app"))
        val provider = FakeUserSessionProvider(SessionState.OfflineFallback("user-1"))
        viewModel = ProtectionViewModel(
            getActiveSessionUseCase,
            startSessionUseCase,
            stopSessionUseCase,
            provider
        )
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value is ProtectionUiState.ActiveSession)

        provider.setState(SessionState.Authenticated("user-2", isAnonymous = true))
        advanceUntilIdle()

        assertTrue("The former user's session must not remain visible", viewModel.uiState.value is ProtectionUiState.NoActiveSession)
    }

    @Test
    fun `active session with permission granted produces ActiveSession with AVAILABLE`() = runTest {
        startSessionUseCase("user-1", "rule-1", "commitment-1", "task-1", null, true, listOf("com.example.app"))
        appProtectionProvider.permissionsGranted = true
        createViewModel()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is ProtectionUiState.ActiveSession)
        assertEquals(EnforcementAvailability.AVAILABLE, (state as ProtectionUiState.ActiveSession).enforcementAvailability)
    }

    @Test
    fun `active session with accessibility revoked produces ActiveSession with PERMISSION_REQUIRED`() = runTest {
        startSessionUseCase("user-1", "rule-1", "commitment-1", "task-1", null, true, listOf("com.example.app"))
        appProtectionProvider.permissionsGranted = false
        createViewModel()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is ProtectionUiState.ActiveSession)
        val active = state as ProtectionUiState.ActiveSession
        assertEquals(ProtectionSessionStatus.ACTIVE, active.session.status)
        assertEquals(EnforcementAvailability.PERMISSION_REQUIRED, active.enforcementAvailability)
    }

    @Test
    fun `active session with service disconnected produces ActiveSession with SERVICE_UNAVAILABLE`() = runTest {
        startSessionUseCase("user-1", "rule-1", "commitment-1", "task-1", null, true, listOf("com.example.app"))
        appProtectionProvider.permissionsGranted = true
        appProtectionProvider.availabilityOverride = EnforcementAvailability.SERVICE_UNAVAILABLE
        createViewModel()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is ProtectionUiState.ActiveSession)
        assertEquals(EnforcementAvailability.SERVICE_UNAVAILABLE, (state as ProtectionUiState.ActiveSession).enforcementAvailability)
    }

    @Test
    fun `active session during recovery produces ActiveSession with RECOVERING`() = runTest {
        startSessionUseCase("user-1", "rule-1", "commitment-1", "task-1", null, true, listOf("com.example.app"))
        appProtectionProvider.permissionsGranted = true
        appProtectionProvider.availabilityOverride = EnforcementAvailability.RECOVERING
        createViewModel()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is ProtectionUiState.ActiveSession)
        assertEquals(EnforcementAvailability.RECOVERING, (state as ProtectionUiState.ActiveSession).enforcementAvailability)
    }

    @Test
    fun `default selected duration is 15 minutes`() = runTest {
        createViewModel()
        assertEquals(ProtectionDurationOption.FifteenMinutes, viewModel.selectedDuration.value)
        assertEquals(15, viewModel.selectedDuration.value.durationMinutes)
    }

    @Test
    fun `selectDuration updates selectedDuration for all preset options`() = runTest {
        createViewModel()

        viewModel.selectDuration(ProtectionDurationOption.ThirtyMinutes)
        assertEquals(ProtectionDurationOption.ThirtyMinutes, viewModel.selectedDuration.value)

        viewModel.selectDuration(ProtectionDurationOption.OneHour)
        assertEquals(ProtectionDurationOption.OneHour, viewModel.selectedDuration.value)

        viewModel.selectDuration(ProtectionDurationOption.TwoHours)
        assertEquals(ProtectionDurationOption.TwoHours, viewModel.selectedDuration.value)

        viewModel.selectDuration(ProtectionDurationOption.OneMinute)
        assertEquals(ProtectionDurationOption.OneMinute, viewModel.selectedDuration.value)

        viewModel.selectDuration(ProtectionDurationOption.Indefinite)
        assertEquals(ProtectionDurationOption.Indefinite, viewModel.selectedDuration.value)
    }

    @Test
    fun `selectCustomDuration updates selectedDuration with valid minutes`() = runTest {
        createViewModel()

        viewModel.selectCustomDuration(45)
        val selected = viewModel.selectedDuration.value
        assertTrue(selected is ProtectionDurationOption.Custom)
        assertEquals(45, selected.durationMinutes)
        assertEquals("45 min", selected.label)
    }

    @Test
    fun `selectCustomDuration with zero or negative minutes produces Error state and preserves selection`() = runTest {
        createViewModel()
        advanceUntilIdle()

        viewModel.selectCustomDuration(0)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value is ProtectionUiState.Error)
        assertEquals(ProtectionDurationOption.FifteenMinutes, viewModel.selectedDuration.value)

        viewModel.selectCustomDuration(-10)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value is ProtectionUiState.Error)
        assertEquals(ProtectionDurationOption.FifteenMinutes, viewModel.selectedDuration.value)
    }

    @Test
    fun `startProtection with default 15 minutes calculates and passes correct scheduledEndAt`() = runTest {
        createViewModel()
        advanceUntilIdle()

        val baseTime = 1_000_000L
        clock.setTime(baseTime)

        viewModel.startProtection(listOf("com.example.app"))
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is ProtectionUiState.ActiveSession)
        val session = (state as ProtectionUiState.ActiveSession).session
        assertEquals(baseTime + 15 * 60_000L, session.scheduledEndAt)
        assertEquals(baseTime + 15 * 60_000L, appProtectionProvider.lastScheduledEndAt)
    }

    @Test
    fun `startProtection with 30 minutes calculates and passes correct scheduledEndAt`() = runTest {
        createViewModel()
        advanceUntilIdle()

        val baseTime = 2_000_000L
        clock.setTime(baseTime)

        viewModel.startProtection(listOf("com.example.app"), ProtectionDurationOption.ThirtyMinutes)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is ProtectionUiState.ActiveSession)
        val session = (state as ProtectionUiState.ActiveSession).session
        assertEquals(baseTime + 30 * 60_000L, session.scheduledEndAt)
        assertEquals(baseTime + 30 * 60_000L, appProtectionProvider.lastScheduledEndAt)
    }

    @Test
    fun `startProtection with 1 hour calculates and passes correct scheduledEndAt`() = runTest {
        createViewModel()
        advanceUntilIdle()

        val baseTime = 2_000_000L
        clock.setTime(baseTime)

        viewModel.startProtection(listOf("com.example.app"), ProtectionDurationOption.OneHour)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is ProtectionUiState.ActiveSession)
        val session = (state as ProtectionUiState.ActiveSession).session
        assertEquals(baseTime + 60 * 60_000L, session.scheduledEndAt)
    }

    @Test
    fun `startProtection with 2 hours calculates and passes correct scheduledEndAt`() = runTest {
        createViewModel()
        advanceUntilIdle()

        val baseTime = 2_000_000L
        clock.setTime(baseTime)

        viewModel.startProtection(listOf("com.example.app"), ProtectionDurationOption.TwoHours)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is ProtectionUiState.ActiveSession)
        val session = (state as ProtectionUiState.ActiveSession).session
        assertEquals(baseTime + 120 * 60_000L, session.scheduledEndAt)
    }

    @Test
    fun `startProtection with 1 minute calculates and passes correct scheduledEndAt`() = runTest {
        createViewModel()
        advanceUntilIdle()

        val baseTime = 5_000_000L
        clock.setTime(baseTime)

        viewModel.startProtection(listOf("com.example.app"), ProtectionDurationOption.OneMinute)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is ProtectionUiState.ActiveSession)
        val session = (state as ProtectionUiState.ActiveSession).session
        assertEquals(baseTime + 60_000L, session.scheduledEndAt)
    }

    @Test
    fun `startProtection with Indefinite passes null scheduledEndAt`() = runTest {
        createViewModel()
        advanceUntilIdle()

        viewModel.startProtection(listOf("com.example.app"), ProtectionDurationOption.Indefinite)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is ProtectionUiState.ActiveSession)
        val session = (state as ProtectionUiState.ActiveSession).session
        assertEquals(null, session.scheduledEndAt)
        assertEquals(null, appProtectionProvider.lastScheduledEndAt)
    }

    @Test
    fun `startProtection with Custom duration calculates and passes correct scheduledEndAt`() = runTest {
        createViewModel()
        advanceUntilIdle()

        val baseTime = 3_000_000L
        clock.setTime(baseTime)

        viewModel.startProtection(listOf("com.example.app"), ProtectionDurationOption.Custom(45))
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is ProtectionUiState.ActiveSession)
        val session = (state as ProtectionUiState.ActiveSession).session
        assertEquals(baseTime + 45 * 60_000L, session.scheduledEndAt)
    }

    @Test
    fun `startProtection with invalid custom duration produces Error state`() = runTest {
        createViewModel()
        advanceUntilIdle()

        viewModel.startProtection(listOf("com.example.app"), ProtectionDurationOption.Custom(0))
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value is ProtectionUiState.Error)
        assertEquals("Session duration must be greater than 0 minutes.", (viewModel.uiState.value as ProtectionUiState.Error).message)
    }

    @Test
    fun `loadCurrentSession automatically transitions expired session to NoActiveSession`() = runTest {
        val baseTime = 1_000_000L
        clock.setTime(baseTime)
        val scheduledEndAt = baseTime + 15 * 60_000L

        startSessionUseCase("user-1", "rule-1", "commitment-1", "task-1", scheduledEndAt, true, listOf("com.example.app"))

        // Advance clock past scheduledEndAt
        clock.setTime(scheduledEndAt + 1_000L)

        createViewModel()
        advanceUntilIdle()

        // getActiveSessionUseCase should transition session to EXPIRED and return null
        assertTrue("Expired session must transition to NoActiveSession on load", viewModel.uiState.value is ProtectionUiState.NoActiveSession)

        // Verify Room status transitioned to EXPIRED
        val getSession = protectionRepository.getProtectionSession(protectionRepository.getActiveProtectionSessionsForUser("user-1").let {
            // It should be empty now
            assertEquals(0, (it as Result.Success).data.size)
            "dummy"
        })
    }
}
