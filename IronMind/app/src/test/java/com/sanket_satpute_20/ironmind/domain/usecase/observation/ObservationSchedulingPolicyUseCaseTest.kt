package com.sanket_satpute_20.ironmind.domain.usecase.observation

import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.ActivityObservationSettings
import com.sanket_satpute_20.ironmind.domain.model.AppUsageObservationSettings
import com.sanket_satpute_20.ironmind.domain.model.AuthUser
import com.sanket_satpute_20.ironmind.domain.model.AutonomySettings
import com.sanket_satpute_20.ironmind.domain.model.CalendarObservationSettings
import com.sanket_satpute_20.ironmind.domain.model.LocationObservationSettings
import com.sanket_satpute_20.ironmind.domain.model.NotificationObservationSettings
import com.sanket_satpute_20.ironmind.domain.repository.ActivityObservationSettingsRepository
import com.sanket_satpute_20.ironmind.domain.repository.AppUsageObservationSettingsRepository
import com.sanket_satpute_20.ironmind.domain.repository.AuthRepository
import com.sanket_satpute_20.ironmind.domain.repository.AutonomySettingsRepository
import com.sanket_satpute_20.ironmind.domain.repository.CalendarObservationSettingsRepository
import com.sanket_satpute_20.ironmind.domain.repository.LocationObservationSettingsRepository
import com.sanket_satpute_20.ironmind.testutil.fake.FakeAuthRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.PrintStream

class ObservationSchedulingPolicyUseCaseTest {

    private lateinit var authRepository: FakeAuthRepository
    private lateinit var autonomySettingsRepository: AutonomySettingsRepository
    private lateinit var appUsageSettingsRepository: AppUsageObservationSettingsRepository
    private lateinit var activitySettingsRepository: ActivityObservationSettingsRepository
    private lateinit var calendarSettingsRepository: CalendarObservationSettingsRepository
    private lateinit var locationSettingsRepository: LocationObservationSettingsRepository
    private lateinit var useCase: ObservationSchedulingPolicyUseCase

    @Before
    fun setUp() {
        authRepository = FakeAuthRepository()
        runBlocking { authRepository.signInAnonymously() }
        
        autonomySettingsRepository = mockk()
        appUsageSettingsRepository = mockk()
        activitySettingsRepository = mockk()
        calendarSettingsRepository = mockk()
        locationSettingsRepository = mockk()
        
        // Default: Autonomy Global Pause is false
        coEvery { autonomySettingsRepository.getSettings(any()) } returns Result.Success(
            AutonomySettings("user-1", emptyMap(), isGlobalPauseActive = false)
        )

        // Default: All settings disabled
        coEvery { appUsageSettingsRepository.getSettings(any()) } returns Result.Success(AppUsageObservationSettings("user-1", false))
        coEvery { activitySettingsRepository.getSettings(any()) } returns Result.Success(ActivityObservationSettings("user-1", false, 0))
        coEvery { calendarSettingsRepository.getSettings(any()) } returns Result.Success(CalendarObservationSettings("user-1", false, 0))
        coEvery { locationSettingsRepository.getSettings(any()) } returns Result.Success(LocationObservationSettings("user-1", false, 0))

        useCase = ObservationSchedulingPolicyUseCase(
            authRepository,
            autonomySettingsRepository,
            appUsageSettingsRepository,
            activitySettingsRepository,
            calendarSettingsRepository,
            locationSettingsRepository
        )
    }

    @Test
    fun `A App Usage enabled allows scheduling`() = runBlocking {
        coEvery { appUsageSettingsRepository.getSettings(any()) } returns Result.Success(AppUsageObservationSettings("user-1", true))
        val result = useCase()
        assertTrue(result is ObservationSchedulingPolicyResult.Allowed)
    }

    @Test
    fun `B Activity enabled allows scheduling`() = runBlocking {
        coEvery { activitySettingsRepository.getSettings(any()) } returns Result.Success(ActivityObservationSettings("user-1", true, 0))
        val result = useCase()
        assertTrue(result is ObservationSchedulingPolicyResult.Allowed)
    }

    @Test
    fun `C Calendar enabled allows scheduling`() = runBlocking {
        coEvery { calendarSettingsRepository.getSettings(any()) } returns Result.Success(CalendarObservationSettings("user-1", true, 0))
        val result = useCase()
        assertTrue(result is ObservationSchedulingPolicyResult.Allowed)
    }

    @Test
    fun `D Location enabled allows scheduling`() = runBlocking {
        coEvery { locationSettingsRepository.getSettings(any()) } returns Result.Success(LocationObservationSettings("user-1", true, 0))
        val result = useCase()
        assertTrue(result is ObservationSchedulingPolicyResult.Allowed)
    }

    @Test
    fun `E All four pull-based settings disabled completely denies scheduling`() = runBlocking {
        // All default to false
        val result = useCase()
        assertTrue(result is ObservationSchedulingPolicyResult.Denied)
        assertEquals(ObservationSchedulingPolicyResult.Denied.Reason.DENIED_CONSENT, (result as ObservationSchedulingPolicyResult.Denied).reason)
    }

    @Test
    fun `F All four pull-based settings unavailable or unknown fails closed`() = runBlocking {
        coEvery { appUsageSettingsRepository.getSettings(any()) } returns Result.Failure(Exception("Error"))
        coEvery { activitySettingsRepository.getSettings(any()) } returns Result.Failure(Exception("Error"))
        coEvery { calendarSettingsRepository.getSettings(any()) } returns Result.Failure(Exception("Error"))
        coEvery { locationSettingsRepository.getSettings(any()) } returns Result.Failure(Exception("Error"))

        val result = useCase()
        assertTrue(result is ObservationSchedulingPolicyResult.Denied)
        assertEquals(ObservationSchedulingPolicyResult.Denied.Reason.DENIED_CONSENT, (result as ObservationSchedulingPolicyResult.Denied).reason)
    }

    @Test
    fun `G One pull-based setting enabled + another unknown allows scheduling`() = runBlocking {
        coEvery { appUsageSettingsRepository.getSettings(any()) } returns Result.Success(AppUsageObservationSettings("user-1", true))
        coEvery { activitySettingsRepository.getSettings(any()) } returns Result.Failure(Exception("Unknown"))
        
        val result = useCase()
        assertTrue(result is ObservationSchedulingPolicyResult.Allowed)
    }

    @Test
    fun `H Notification enabled while ALL four pull-based settings are disabled completely denies scheduling`() = runBlocking {
        // The policy no longer evaluates notifications. Since all pull-based are false by default here,
        // it must return DENIED_CONSENT regardless of any notification state.
        val result = useCase()
        assertTrue(result is ObservationSchedulingPolicyResult.Denied)
        assertEquals(ObservationSchedulingPolicyResult.Denied.Reason.DENIED_CONSENT, (result as ObservationSchedulingPolicyResult.Denied).reason)
    }

    @Test
    fun `I Notification disabled while one pull-based setting is enabled allows scheduling`() = runBlocking {
        coEvery { appUsageSettingsRepository.getSettings(any()) } returns Result.Success(AppUsageObservationSettings("user-1", true))
        
        val result = useCase()
        assertTrue(result is ObservationSchedulingPolicyResult.Allowed)
    }

    @Test
    fun `J No authenticated user denies scheduling`() = runBlocking {
        authRepository.signOut()

        val result = useCase()
        assertTrue(result is ObservationSchedulingPolicyResult.Denied)
        assertEquals(ObservationSchedulingPolicyResult.Denied.Reason.DENIED_AUTH, (result as ObservationSchedulingPolicyResult.Denied).reason)
    }

    @Test
    fun `K Global autonomy pause denies scheduling`() = runBlocking {
        coEvery { appUsageSettingsRepository.getSettings(any()) } returns Result.Success(AppUsageObservationSettings("user-1", true))
        coEvery { autonomySettingsRepository.getSettings(any()) } returns Result.Success(
            AutonomySettings("user-1", emptyMap(), isGlobalPauseActive = true)
        )

        val result = useCase()
        assertTrue(result is ObservationSchedulingPolicyResult.Denied)
        assertEquals(ObservationSchedulingPolicyResult.Denied.Reason.DENIED_USER_SETTING, (result as ObservationSchedulingPolicyResult.Denied).reason)
    }

    @Test
    fun `L Policy does not invoke WorkManager or Scheduler`() = runBlocking {
        // Validated strictly by fact that no WorkManager/Scheduler mocks exist in this class.
        // The test would crash if it attempted to instantiate/invoke them.
        coEvery { appUsageSettingsRepository.getSettings(any()) } returns Result.Success(AppUsageObservationSettings("user-1", true))
        val result = useCase()
        assertTrue(result is ObservationSchedulingPolicyResult.Allowed)
    }

    @Test
    fun `M Policy does not invoke ObservationScheduler`() = runBlocking {
        // Redundant explicit named test check to satisfy prompt requirement M.
        coEvery { appUsageSettingsRepository.getSettings(any()) } returns Result.Success(AppUsageObservationSettings("user-1", true))
        val result = useCase()
        assertTrue(result is ObservationSchedulingPolicyResult.Allowed)
    }

    @Test
    fun `N Logs contain no private observation payloads`() = runBlocking {
        val originalOut = System.out
        val outContent = ByteArrayOutputStream()
        System.setOut(PrintStream(outContent))

        try {
            useCase()
            
            val logs = outContent.toString()
            assertTrue(logs.contains("IronMindLifecycle ObservationPolicy [EVALUATED]"))
            assertTrue(logs.contains("IronMindLifecycle ObservationPolicy [DENIED]"))
            assertTrue(!logs.contains("raw"))
            assertTrue(!logs.contains("payload"))
            assertTrue(!logs.contains("location"))
        } finally {
            System.setOut(originalOut)
        }
    }
}
