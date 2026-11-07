package com.sanket_satpute_20.ironmind.ui.screens.digital

import com.sanket_satpute_20.ironmind.domain.common.Clock
import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.AppUsageObservationSettings
import com.sanket_satpute_20.ironmind.domain.model.EntitySource
import com.sanket_satpute_20.ironmind.domain.model.ProtectionRule
import com.sanket_satpute_20.ironmind.domain.model.ProtectionSession
import com.sanket_satpute_20.ironmind.domain.model.ProtectionSessionStatus
import com.sanket_satpute_20.ironmind.domain.model.context.SourceAvailability
import com.sanket_satpute_20.ironmind.domain.model.observation.Observation
import com.sanket_satpute_20.ironmind.domain.model.observation.ObservationType
import com.sanket_satpute_20.ironmind.domain.provider.AppUsageEvent
import com.sanket_satpute_20.ironmind.domain.provider.AppUsageObservationProvider
import com.sanket_satpute_20.ironmind.domain.repository.AppUsageObservationSettingsRepository
import com.sanket_satpute_20.ironmind.domain.repository.ObservationRepository
import com.sanket_satpute_20.ironmind.domain.repository.ProtectionRepository
import com.sanket_satpute_20.ironmind.domain.session.SessionState
import com.sanket_satpute_20.ironmind.domain.usecase.digital.GetDailyAppUsageIntelligenceUseCase
import com.sanket_satpute_20.ironmind.testutil.FakeUserSessionProvider
import com.sanket_satpute_20.ironmind.testutil.TestDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
class DigitalBehaviourViewModelTest {

    @get:Rule
    val dispatcherRule = TestDispatcherRule()

    private val zoneId = ZoneId.of("UTC")
    private val today = LocalDate.of(2026, 11, 7)
    private val yesterday = LocalDate.of(2026, 11, 6)
    private val todayStartMs = today.atStartOfDay(zoneId).toInstant().toEpochMilli()
    private val testClock = FakeTestClock(todayStartMs + 12 * 3600 * 1000L) // noon

    private lateinit var appUsageProvider: FakeAppUsageProvider
    private lateinit var settingsRepository: FakeAppUsageSettingsRepo
    private lateinit var observationRepository: FakeObservationRepo
    private lateinit var protectionRepository: FakeProtectionRepository
    private lateinit var userSessionProvider: FakeUserSessionProvider
    private lateinit var useCase: GetDailyAppUsageIntelligenceUseCase

    private val appNameResolver: (String) -> String? = { pkg ->
        when (pkg) {
            "com.youtube" -> "YouTube"
            "com.twitter" -> "Twitter"
            "com.ide" -> "Android Studio"
            else -> null
        }
    }

    @Before
    fun setUp() {
        appUsageProvider = FakeAppUsageProvider(permissionGranted = true)
        settingsRepository = FakeAppUsageSettingsRepo()
        observationRepository = FakeObservationRepo()
        protectionRepository = FakeProtectionRepository()
        userSessionProvider = FakeUserSessionProvider(SessionState.Authenticated("user_test", isAnonymous = false))

        useCase = GetDailyAppUsageIntelligenceUseCase(
            appUsageObservationProvider = appUsageProvider,
            appUsageSettingsRepository = settingsRepository,
            observationRepository = observationRepository,
            clock = testClock
        )
    }

    @Test
    fun `successful daily usage rendering with deterministic ranking and app name resolution`() = runTest {
        appUsageProvider.events = listOf(
            AppUsageEvent("com.youtube", todayStartMs + 1000, todayStartMs + 61000, 60000), // 60s
            AppUsageEvent("com.ide", todayStartMs + 100000, todayStartMs + 220000, 120000)   // 120s
        )

        val viewModel = DigitalBehaviourViewModel(
            getDailyAppUsageIntelligenceUseCase = useCase,
            appUsageObservationProvider = appUsageProvider,
            appUsageSettingsRepository = settingsRepository,
            protectionRepository = protectionRepository,
            userSessionProvider = userSessionProvider,
            appNameResolver = appNameResolver,
            clock = testClock,
            zoneId = zoneId
        )

        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertNull(state.errorMessage)
        assertTrue(state.isUsageAccessPermissionGranted)
        assertTrue(state.isUsageObservationEnabled)
        assertEquals(SourceAvailability.AVAILABLE, state.effectiveAvailability)

        val usage = state.dailyUsage
        assertNotNull(usage)
        assertEquals(180_000L, usage!!.totalObservedDurationMs)
        assertEquals(2, usage.mostUsedApps.size)

        // Deterministic order: com.ide (120s) before com.youtube (60s)
        assertEquals("com.ide", usage.mostUsedApps[0].packageName)
        assertEquals("Android Studio", usage.mostUsedApps[0].appName)
        assertEquals(120_000L, usage.mostUsedApps[0].totalDurationMs)

        assertEquals("com.youtube", usage.mostUsedApps[1].packageName)
        assertEquals("YouTube", usage.mostUsedApps[1].appName)
        assertEquals(60_000L, usage.mostUsedApps[1].totalDurationMs)
    }

    @Test
    fun `permission denied state yields explicit null duration and PERMISSION_DENIED`() = runTest {
        appUsageProvider.permissionGranted = false

        val viewModel = DigitalBehaviourViewModel(
            getDailyAppUsageIntelligenceUseCase = useCase,
            appUsageObservationProvider = appUsageProvider,
            appUsageSettingsRepository = settingsRepository,
            protectionRepository = protectionRepository,
            userSessionProvider = userSessionProvider,
            appNameResolver = appNameResolver,
            clock = testClock,
            zoneId = zoneId
        )

        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isUsageAccessPermissionGranted)
        assertEquals(SourceAvailability.PERMISSION_DENIED, state.effectiveAvailability)
        assertNull(state.dailyUsage?.totalObservedDurationMs)
    }

    @Test
    fun `permission not requested state when user consent disabled in settings`() = runTest {
        settingsRepository.setEnabled("user_test", false)

        val viewModel = DigitalBehaviourViewModel(
            getDailyAppUsageIntelligenceUseCase = useCase,
            appUsageObservationProvider = appUsageProvider,
            appUsageSettingsRepository = settingsRepository,
            protectionRepository = protectionRepository,
            userSessionProvider = userSessionProvider,
            appNameResolver = appNameResolver,
            clock = testClock,
            zoneId = zoneId
        )

        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isUsageObservationEnabled)
        assertEquals(SourceAvailability.PERMISSION_NOT_REQUESTED, state.effectiveAvailability)
        assertNull(state.dailyUsage?.totalObservedDurationMs)
    }

    @Test
    fun `empty observations returns 0ms total duration and EMPTY_NO_DATA`() = runTest {
        appUsageProvider.events = emptyList()

        val viewModel = DigitalBehaviourViewModel(
            getDailyAppUsageIntelligenceUseCase = useCase,
            appUsageObservationProvider = appUsageProvider,
            appUsageSettingsRepository = settingsRepository,
            protectionRepository = protectionRepository,
            userSessionProvider = userSessionProvider,
            appNameResolver = appNameResolver,
            clock = testClock,
            zoneId = zoneId
        )

        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(SourceAvailability.EMPTY_NO_DATA, state.effectiveAvailability)
        assertEquals(0L, state.dailyUsage?.totalObservedDurationMs)
        assertTrue(state.dailyUsage?.mostUsedApps.orEmpty().isEmpty())
    }

    @Test
    fun `previous day comparison calculates valid delta when baseline exists`() = runTest {
        val yStart = yesterday.atStartOfDay(zoneId).toInstant().toEpochMilli()
        val tStart = today.atStartOfDay(zoneId).toInstant().toEpochMilli()

        // Yesterday 60s, Today 120s
        appUsageProvider.events = listOf(
            AppUsageEvent("com.youtube", yStart + 1000, yStart + 61000, 60000),
            AppUsageEvent("com.youtube", tStart + 1000, tStart + 121000, 120000)
        )

        val viewModel = DigitalBehaviourViewModel(
            getDailyAppUsageIntelligenceUseCase = useCase,
            appUsageObservationProvider = appUsageProvider,
            appUsageSettingsRepository = settingsRepository,
            protectionRepository = protectionRepository,
            userSessionProvider = userSessionProvider,
            appNameResolver = appNameResolver,
            clock = testClock,
            zoneId = zoneId
        )

        advanceUntilIdle()

        val state = viewModel.uiState.value
        val comparison = state.dailyUsage?.comparisonWithPreviousDay
        assertNotNull(comparison)
        assertEquals(60000L, comparison!!.previousDayDurationMs)
        assertEquals(120000L, comparison.currentDayDurationMs)
        assertEquals(60000L, comparison.durationDeltaMs)
        assertEquals(100f, comparison.percentageDelta)
    }

    @Test
    fun `refresh reloads data after permission transition`() = runTest {
        appUsageProvider.permissionGranted = false

        val viewModel = DigitalBehaviourViewModel(
            getDailyAppUsageIntelligenceUseCase = useCase,
            appUsageObservationProvider = appUsageProvider,
            appUsageSettingsRepository = settingsRepository,
            protectionRepository = protectionRepository,
            userSessionProvider = userSessionProvider,
            appNameResolver = appNameResolver,
            clock = testClock,
            zoneId = zoneId
        )

        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.isUsageAccessPermissionGranted)

        // Permission granted dynamically
        appUsageProvider.permissionGranted = true
        appUsageProvider.events = listOf(
            AppUsageEvent("com.youtube", todayStartMs + 1000, todayStartMs + 31000, 30000)
        )

        viewModel.refresh()
        advanceUntilIdle()

        val refreshedState = viewModel.uiState.value
        assertTrue(refreshedState.isUsageAccessPermissionGranted)
        assertEquals(SourceAvailability.AVAILABLE, refreshedState.effectiveAvailability)
        assertEquals(30000L, refreshedState.dailyUsage?.totalObservedDurationMs)
    }

    @Test
    fun `session transition isolates user data without stale bleed`() = runTest {
        appUsageProvider.events = listOf(
            AppUsageEvent("com.youtube", todayStartMs + 1000, todayStartMs + 51000, 50000)
        )

        // User 1 active protection
        protectionRepository.saveProtectionSession(
            ProtectionSession(
                id = "prot_1",
                userId = "user_test",
                startedAt = todayStartMs,
                status = ProtectionSessionStatus.ACTIVE,
                source = EntitySource.USER,
                overrideAllowed = false,
                createdAt = todayStartMs,
                updatedAt = todayStartMs
            )
        )

        val viewModel = DigitalBehaviourViewModel(
            getDailyAppUsageIntelligenceUseCase = useCase,
            appUsageObservationProvider = appUsageProvider,
            appUsageSettingsRepository = settingsRepository,
            protectionRepository = protectionRepository,
            userSessionProvider = userSessionProvider,
            appNameResolver = appNameResolver,
            clock = testClock,
            zoneId = zoneId
        )

        advanceUntilIdle()
        assertEquals(1, viewModel.uiState.value.activeProtectionSessions.size)

        // Transition to Initializing -> resets loading state and clears data
        userSessionProvider.setState(SessionState.Initializing)
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.isLoading)
        assertTrue(viewModel.uiState.value.activeProtectionSessions.isEmpty())

        // Switch to User 2 (no protection sessions)
        userSessionProvider.setState(SessionState.Authenticated("user_2", isAnonymous = false))
        advanceUntilIdle()

        val user2State = viewModel.uiState.value
        assertFalse(user2State.isLoading)
        assertTrue(user2State.activeProtectionSessions.isEmpty())
    }

    @Test
    fun `protection information is shown separately from usage metrics`() = runTest {
        appUsageProvider.events = listOf(
            AppUsageEvent("com.ide", todayStartMs + 1000, todayStartMs + 41000, 40000)
        )

        protectionRepository.saveProtectionSession(
            ProtectionSession(
                id = "prot_session_99",
                userId = "user_test",
                startedAt = todayStartMs,
                scheduledEndAt = todayStartMs + 1800000,
                status = ProtectionSessionStatus.ACTIVE,
                source = EntitySource.SYSTEM,
                overrideAllowed = true,
                createdAt = todayStartMs,
                updatedAt = todayStartMs
            )
        )

        val viewModel = DigitalBehaviourViewModel(
            getDailyAppUsageIntelligenceUseCase = useCase,
            appUsageObservationProvider = appUsageProvider,
            appUsageSettingsRepository = settingsRepository,
            protectionRepository = protectionRepository,
            userSessionProvider = userSessionProvider,
            appNameResolver = appNameResolver,
            clock = testClock,
            zoneId = zoneId
        )

        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(40000L, state.dailyUsage?.totalObservedDurationMs)
        assertEquals(1, state.activeProtectionSessions.size)
        assertEquals("prot_session_99", state.activeProtectionSessions[0].id)
        assertEquals(ProtectionSessionStatus.ACTIVE, state.activeProtectionSessions[0].status)
    }

    @Test
    fun `error handling captures failure and clears on acknowledge`() = runTest {
        protectionRepository.shouldFail = true

        val viewModel = DigitalBehaviourViewModel(
            getDailyAppUsageIntelligenceUseCase = useCase,
            appUsageObservationProvider = appUsageProvider,
            appUsageSettingsRepository = settingsRepository,
            protectionRepository = protectionRepository,
            userSessionProvider = userSessionProvider,
            appNameResolver = appNameResolver,
            clock = testClock,
            zoneId = zoneId
        )

        advanceUntilIdle()

        // Active protection sessions fall back to emptyList on Result.Failure without crashing
        assertEquals(emptyList<ProtectionSession>(), viewModel.uiState.value.activeProtectionSessions)

        // Now test acknowledgeError
        viewModel.acknowledgeError()
        advanceUntilIdle()
        assertNull(viewModel.uiState.value.errorMessage)
    }

    // --- Fakes ---

    private class FakeTestClock(var time: Long) : Clock {
        override fun currentTimeMillis(): Long = time
    }

    private class FakeAppUsageProvider(var permissionGranted: Boolean = true) : AppUsageObservationProvider {
        var events: List<AppUsageEvent> = emptyList()

        override fun isPermissionGranted(): Boolean = permissionGranted

        override fun getAppUsageSince(fromTimeMillis: Long): List<AppUsageEvent> {
            return events.filter { it.startTimeMillis >= fromTimeMillis || it.endTimeMillis >= fromTimeMillis }
        }
    }

    private class FakeAppUsageSettingsRepo : AppUsageObservationSettingsRepository {
        private var isEnabled: Boolean = true

        override suspend fun getSettings(userId: String): Result<AppUsageObservationSettings, Exception> {
            return Result.Success(AppUsageObservationSettings(userId, isEnabled = isEnabled))
        }

        override suspend fun setEnabled(userId: String, isEnabled: Boolean): Result<Unit, Exception> {
            this.isEnabled = isEnabled
            return Result.Success(Unit)
        }
    }

    private class FakeObservationRepo : ObservationRepository {
        var observations: List<Observation> = emptyList()

        override suspend fun insertObservation(observation: Observation): Result<Unit, Exception> = Result.Success(Unit)
        override suspend fun getObservations(userId: String, limit: Int, offset: Int): Result<List<Observation>, Exception> = Result.Success(observations)
        override suspend fun getObservationsByType(userId: String, type: ObservationType, limit: Int, offset: Int): Result<List<Observation>, Exception> = Result.Success(observations.filter { it.type == type })
        override suspend fun getObservationById(id: String): Result<Observation, Exception> = Result.Failure(NoSuchElementException())
        override suspend fun getObservation(userId: String, id: String): Result<Observation, Exception> = Result.Failure(NoSuchElementException())
        override suspend fun deleteObservation(id: String): Result<Unit, Exception> = Result.Success(Unit)
        override suspend fun getObservationsForTimeWindow(userId: String, type: ObservationType, startTimeMs: Long, endTimeMs: Long): Result<List<Observation>, Exception> {
            return Result.Success(
                observations.filter { it.userId == userId && it.type == type && it.occurredAt in startTimeMs..endTimeMs }
            )
        }
        override suspend fun getObservationsForTimeWindow(userId: String, startTimeMs: Long, endTimeMs: Long, types: List<ObservationType>?, limit: Int, orderAsc: Boolean): Result<List<Observation>, Exception> {
            return Result.Success(
                observations.filter { it.userId == userId && (types == null || it.type in types) && it.occurredAt in startTimeMs..endTimeMs }
            )
        }
    }

    private class FakeProtectionRepository : ProtectionRepository {
        private val rules = mutableListOf<ProtectionRule>()
        private val sessions = mutableListOf<ProtectionSession>()
        var shouldFail = false

        override suspend fun saveProtectionRule(rule: ProtectionRule): Result<Unit, Exception> {
            if (shouldFail) return Result.Failure(RuntimeException("Rule error"))
            rules.add(rule)
            return Result.Success(Unit)
        }

        override suspend fun getProtectionRule(id: String): Result<ProtectionRule, Exception> {
            val r = rules.find { it.id == id }
            return if (r != null) Result.Success(r) else Result.Failure(NoSuchElementException())
        }

        override suspend fun getProtectionRulesForUser(userId: String): Result<List<ProtectionRule>, Exception> {
            return Result.Success(rules.filter { it.userId == userId })
        }

        override suspend fun saveProtectionSession(session: ProtectionSession): Result<Unit, Exception> {
            if (shouldFail) return Result.Failure(RuntimeException("Save session error"))
            sessions.removeIf { it.id == session.id }
            sessions.add(session)
            return Result.Success(Unit)
        }

        override suspend fun getProtectionSession(id: String): Result<ProtectionSession, Exception> {
            val s = sessions.find { it.id == id }
            return if (s != null) Result.Success(s) else Result.Failure(NoSuchElementException())
        }

        override suspend fun getActiveProtectionSessionsForUser(userId: String): Result<List<ProtectionSession>, Exception> {
            if (shouldFail) return Result.Failure(RuntimeException("Load active sessions error"))
            return Result.Success(sessions.filter { it.userId == userId && it.status == ProtectionSessionStatus.ACTIVE })
        }
    }
}
