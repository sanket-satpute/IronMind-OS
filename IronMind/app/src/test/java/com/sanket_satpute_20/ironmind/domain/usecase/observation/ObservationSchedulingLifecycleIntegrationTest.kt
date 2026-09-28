package com.sanket_satpute_20.ironmind.domain.usecase.observation

import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.ActivityObservationSettings
import com.sanket_satpute_20.ironmind.domain.model.AppUsageObservationSettings
import com.sanket_satpute_20.ironmind.domain.model.CalendarObservationSettings
import com.sanket_satpute_20.ironmind.domain.model.LocationObservationSettings
import com.sanket_satpute_20.ironmind.domain.repository.ActivityObservationSettingsRepository
import com.sanket_satpute_20.ironmind.domain.repository.AppUsageObservationSettingsRepository
import com.sanket_satpute_20.ironmind.domain.repository.AutonomySettingsRepository
import com.sanket_satpute_20.ironmind.domain.repository.CalendarObservationSettingsRepository
import com.sanket_satpute_20.ironmind.domain.repository.LocationObservationSettingsRepository
import com.sanket_satpute_20.ironmind.domain.usecase.autonomy.ToggleGlobalPauseUseCase
import com.sanket_satpute_20.ironmind.domain.repository.AuthRepository
import com.sanket_satpute_20.ironmind.domain.model.AuthUser
import com.sanket_satpute_20.ironmind.testutil.fake.FakeAuthRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ObservationSchedulingLifecycleIntegrationTest {

    private val coordinator = mockk<ObservationSchedulingCoordinator>(relaxed = true)
    
    // Repositories
    private val appUsageRepo = mockk<AppUsageObservationSettingsRepository>()
    private val activityRepo = mockk<ActivityObservationSettingsRepository>()
    private val calendarRepo = mockk<CalendarObservationSettingsRepository>()
    private val locationRepo = mockk<LocationObservationSettingsRepository>()
    private val autonomyRepo = mockk<AutonomySettingsRepository>()
    private val authRepo = FakeAuthRepository()
    
    // Get Use Cases
    private val getAppUsageSettingsUseCase = mockk<GetAppUsageObservationSettingsUseCase>()
    private val getActivitySettingsUseCase = mockk<GetActivityObservationSettingsUseCase>()
    private val getCalendarSettingsUseCase = mockk<GetCalendarObservationSettingsUseCase>()
    private val getLocationSettingsUseCase = mockk<GetLocationObservationSettingsUseCase>()

    // Use Cases to Test
    private val setAppUsageUseCase = SetAppUsageObservationEnabledUseCase(appUsageRepo, getAppUsageSettingsUseCase, coordinator)
    private val setActivityUseCase = SetActivityObservationEnabledUseCase(activityRepo, getActivitySettingsUseCase, coordinator)
    private val setCalendarUseCase = SetCalendarObservationEnabledUseCase(calendarRepo, getCalendarSettingsUseCase, coordinator)
    private val setLocationUseCase = SetLocationObservationEnabledUseCase(locationRepo, getLocationSettingsUseCase, coordinator)
    private val toggleGlobalPauseUseCase = ToggleGlobalPauseUseCase(autonomyRepo, coordinator)

    // --- PART 2: APPLICATION/AUTH TESTS ---
    
    @Test
    fun `1 ApplicationStarted causes exactly one coordinator invocation`() = runTest {
        val testScope = TestScope(StandardTestDispatcher(testScheduler))
        authRepo.setTestUser(null)
        
        val adapter = ObservationSchedulingAppLifecycleAdapter(coordinator, authRepo, testScope)
        adapter.start()
        
        advanceUntilIdle()
        
        coVerify(exactly = 1) { coordinator.handle(ObservationSchedulingLifecycleEvent.ApplicationStarted) }
    }

    @Test
    fun `2 AuthenticationAvailable causes coordinator invocation`() = runTest {
        val testScope = TestScope(StandardTestDispatcher(testScheduler))
        authRepo.setTestUser(AuthUser("user1", false, "user@example.com", "User"))
        
        val adapter = ObservationSchedulingAppLifecycleAdapter(coordinator, authRepo, testScope)
        adapter.start()
        
        advanceUntilIdle()
        
        coVerify(exactly = 1) { coordinator.handle(ObservationSchedulingLifecycleEvent.AuthenticationAvailable) }
    }

    @Test
    fun `3 AuthenticationUnavailable causes coordinator invocation`() = runTest {
        val testScope = TestScope(StandardTestDispatcher(testScheduler))
        authRepo.setTestUser(null)
        
        val adapter = ObservationSchedulingAppLifecycleAdapter(coordinator, authRepo, testScope)
        adapter.start()
        
        advanceUntilIdle()
        
        coVerify(exactly = 1) { coordinator.handle(ObservationSchedulingLifecycleEvent.AuthenticationUnavailable) }
    }

    @Test
    fun `4 Repeated authenticated state does not emit duplicate AuthenticationAvailable`() = runTest {
        val testScope = TestScope(StandardTestDispatcher(testScheduler))
        authRepo.setTestUser(AuthUser("user1", false, "user@example.com", "User"))
        
        val adapter = ObservationSchedulingAppLifecycleAdapter(coordinator, authRepo, testScope)
        adapter.start()
        advanceUntilIdle()
        
        authRepo.setTestUser(AuthUser("user2", false, "user2@example.com", "User2")) // Still authenticated
        advanceUntilIdle()
        
        coVerify(exactly = 1) { coordinator.handle(ObservationSchedulingLifecycleEvent.AuthenticationAvailable) }
    }

    @Test
    fun `5 Repeated unauthenticated state does not emit duplicate AuthenticationUnavailable`() = runTest {
        val testScope = TestScope(StandardTestDispatcher(testScheduler))
        authRepo.setTestUser(null)
        
        val adapter = ObservationSchedulingAppLifecycleAdapter(coordinator, authRepo, testScope)
        adapter.start()
        advanceUntilIdle()
        
        authRepo.setTestUser(null) // Still unauthenticated
        advanceUntilIdle()
        
        coVerify(exactly = 1) { coordinator.handle(ObservationSchedulingLifecycleEvent.AuthenticationUnavailable) }
    }

    @Test
    fun `6 Authentication transition null to user emits AuthenticationAvailable exactly once`() = runTest {
        val testScope = TestScope(StandardTestDispatcher(testScheduler))
        authRepo.setTestUser(null)
        
        val adapter = ObservationSchedulingAppLifecycleAdapter(coordinator, authRepo, testScope)
        adapter.start()
        advanceUntilIdle()
        
        coVerify(exactly = 1) { coordinator.handle(ObservationSchedulingLifecycleEvent.AuthenticationUnavailable) }
        
        authRepo.setTestUser(AuthUser("user1", false, "user@example.com", "User"))
        advanceUntilIdle()
        
        coVerify(exactly = 1) { coordinator.handle(ObservationSchedulingLifecycleEvent.AuthenticationAvailable) }
    }

    @Test
    fun `7 Authentication transition user to null emits AuthenticationUnavailable exactly once`() = runTest {
        val testScope = TestScope(StandardTestDispatcher(testScheduler))
        authRepo.setTestUser(AuthUser("user1", false, "user@example.com", "User"))
        
        val adapter = ObservationSchedulingAppLifecycleAdapter(coordinator, authRepo, testScope)
        adapter.start()
        advanceUntilIdle()
        
        coVerify(exactly = 1) { coordinator.handle(ObservationSchedulingLifecycleEvent.AuthenticationAvailable) }
        
        authRepo.setTestUser(null)
        advanceUntilIdle()
        
        coVerify(exactly = 1) { coordinator.handle(ObservationSchedulingLifecycleEvent.AuthenticationUnavailable) }
    }

    // --- PART 3: CONSENT TESTS ---
    // IDEMPOTENCY
    @Test
    fun `A App Usage already enabled - no coordinator invocation`() = runTest {
        coEvery { getAppUsageSettingsUseCase("user1") } returns Result.Success(AppUsageObservationSettings("user1", true))
        
        setAppUsageUseCase("user1", true)
        
        coVerify(exactly = 0) { coordinator.handle(ObservationSchedulingLifecycleEvent.ObservationConsentChanged) }
    }

    @Test
    fun `B Activity already enabled - no coordinator invocation`() = runTest {
        coEvery { getActivitySettingsUseCase("user1") } returns Result.Success(ActivityObservationSettings("user1", true, 0L))
        
        setActivityUseCase("user1", true)
        
        coVerify(exactly = 0) { coordinator.handle(ObservationSchedulingLifecycleEvent.ObservationConsentChanged) }
    }

    @Test
    fun `C Calendar already enabled - no coordinator invocation`() = runTest {
        coEvery { getCalendarSettingsUseCase("user1") } returns Result.Success(CalendarObservationSettings("user1", true, 0L))
        
        setCalendarUseCase("user1", true)
        
        coVerify(exactly = 0) { coordinator.handle(ObservationSchedulingLifecycleEvent.ObservationConsentChanged) }
    }

    @Test
    fun `D Location already enabled - no coordinator invocation`() = runTest {
        coEvery { getLocationSettingsUseCase("user1") } returns Result.Success(LocationObservationSettings("user1", true, 0L))
        
        setLocationUseCase("user1", true)
        
        coVerify(exactly = 0) { coordinator.handle(ObservationSchedulingLifecycleEvent.ObservationConsentChanged) }
    }

    // ACTUAL STATE CHANGE
    @Test
    fun `E App Usage actual state change - coordinator invoked once`() = runTest {
        coEvery { getAppUsageSettingsUseCase("user1") } returns Result.Success(AppUsageObservationSettings("user1", false))
        coEvery { appUsageRepo.setEnabled("user1", true) } returns Result.Success(Unit)
        
        setAppUsageUseCase("user1", true)
        
        coVerify(exactly = 1) { coordinator.handle(ObservationSchedulingLifecycleEvent.ObservationConsentChanged) }
    }

    @Test
    fun `F Activity actual state change - coordinator invoked once`() = runTest {
        coEvery { getActivitySettingsUseCase("user1") } returns Result.Success(ActivityObservationSettings("user1", false, 0L))
        coEvery { activityRepo.setEnabled("user1", true) } returns Result.Success(Unit)
        
        setActivityUseCase("user1", true)
        
        coVerify(exactly = 1) { coordinator.handle(ObservationSchedulingLifecycleEvent.ObservationConsentChanged) }
    }

    @Test
    fun `G Calendar actual state change - coordinator invoked once`() = runTest {
        coEvery { getCalendarSettingsUseCase("user1") } returns Result.Success(CalendarObservationSettings("user1", false, 0L))
        coEvery { calendarRepo.updateSettings(any()) } returns Result.Success(Unit)
        
        setCalendarUseCase("user1", true)
        
        coVerify(exactly = 1) { coordinator.handle(ObservationSchedulingLifecycleEvent.ObservationConsentChanged) }
    }

    @Test
    fun `H Location actual state change - coordinator invoked once`() = runTest {
        coEvery { getLocationSettingsUseCase("user1") } returns Result.Success(LocationObservationSettings("user1", false, 0L))
        coEvery { locationRepo.updateSettings(any()) } returns Result.Success(Unit)
        
        setLocationUseCase("user1", true)
        
        coVerify(exactly = 1) { coordinator.handle(ObservationSchedulingLifecycleEvent.ObservationConsentChanged) }
    }

    // MUTATION FAILURES
    @Test
    fun `I App Usage failure - no coordinator`() = runTest {
        coEvery { getAppUsageSettingsUseCase("user1") } returns Result.Success(AppUsageObservationSettings("user1", false))
        coEvery { appUsageRepo.setEnabled("user1", true) } returns Result.Failure(Exception("DB error"))
        
        setAppUsageUseCase("user1", true)
        
        coVerify(exactly = 0) { coordinator.handle(ObservationSchedulingLifecycleEvent.ObservationConsentChanged) }
    }

    @Test
    fun `J Activity failure - no coordinator`() = runTest {
        coEvery { getActivitySettingsUseCase("user1") } returns Result.Success(ActivityObservationSettings("user1", false, 0L))
        coEvery { activityRepo.setEnabled("user1", true) } returns Result.Failure(Exception("error"))
        
        setActivityUseCase("user1", true)
        
        coVerify(exactly = 0) { coordinator.handle(ObservationSchedulingLifecycleEvent.ObservationConsentChanged) }
    }

    @Test
    fun `K Calendar failure - no coordinator`() = runTest {
        coEvery { getCalendarSettingsUseCase("user1") } returns Result.Success(CalendarObservationSettings("user1", false, 0L))
        coEvery { calendarRepo.updateSettings(any()) } returns Result.Failure(Exception("error"))
        
        setCalendarUseCase("user1", true)
        
        coVerify(exactly = 0) { coordinator.handle(ObservationSchedulingLifecycleEvent.ObservationConsentChanged) }
    }

    @Test
    fun `L Location failure - no coordinator`() = runTest {
        coEvery { getLocationSettingsUseCase("user1") } returns Result.Success(LocationObservationSettings("user1", false, 0L))
        coEvery { locationRepo.updateSettings(any()) } returns Result.Failure(Exception("error"))
        
        setLocationUseCase("user1", true)
        
        coVerify(exactly = 0) { coordinator.handle(ObservationSchedulingLifecycleEvent.ObservationConsentChanged) }
    }

    // --- PART 4: GLOBAL PAUSE ---
    @Test
    fun `successful global pause mutation triggers GlobalAutonomyPauseChanged`() = runTest {
        coEvery { autonomyRepo.setGlobalPause("user1", true) } returns Result.Success(Unit)
        
        toggleGlobalPauseUseCase("user1", true)
        
        coVerify { coordinator.handle(ObservationSchedulingLifecycleEvent.GlobalAutonomyPauseChanged) }
    }

    @Test
    fun `failed global pause mutation does NOT trigger GlobalAutonomyPauseChanged`() = runTest {
        coEvery { autonomyRepo.setGlobalPause("user1", true) } returns Result.Failure(Exception("error"))
        
        toggleGlobalPauseUseCase("user1", true)
        
        coVerify(exactly = 0) { coordinator.handle(any()) }
    }
}
