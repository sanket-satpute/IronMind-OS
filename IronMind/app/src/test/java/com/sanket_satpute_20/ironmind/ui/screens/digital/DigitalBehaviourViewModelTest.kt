package com.sanket_satpute_20.ironmind.ui.screens.digital

import com.sanket_satpute_20.ironmind.domain.ai.BarrierCategory
import com.sanket_satpute_20.ironmind.domain.common.Clock
import com.sanket_satpute_20.ironmind.domain.common.IntelligenceMetadataCodec
import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.AppUsageObservationSettings
import com.sanket_satpute_20.ironmind.domain.model.EntitySource
import com.sanket_satpute_20.ironmind.domain.model.MemoryConfirmationState
import com.sanket_satpute_20.ironmind.domain.model.ProtectionRule
import com.sanket_satpute_20.ironmind.domain.model.ProtectionSession
import com.sanket_satpute_20.ironmind.domain.model.ProtectionSessionStatus
import com.sanket_satpute_20.ironmind.domain.model.barrier.BarrierConfirmationState
import com.sanket_satpute_20.ironmind.domain.model.barrier.BarrierHypothesis
import com.sanket_satpute_20.ironmind.domain.model.barrier.BarrierStatus
import com.sanket_satpute_20.ironmind.domain.model.context.EpistemicAuthority
import com.sanket_satpute_20.ironmind.domain.model.context.SourceAvailability
import com.sanket_satpute_20.ironmind.domain.model.context.SourceFreshness
import com.sanket_satpute_20.ironmind.domain.model.insight.ExplainabilityBlock
import com.sanket_satpute_20.ironmind.domain.model.insight.InsightCategory
import com.sanket_satpute_20.ironmind.domain.model.insight.InsightReviewStatus
import com.sanket_satpute_20.ironmind.domain.model.insight.PersonalInsight
import com.sanket_satpute_20.ironmind.domain.model.observation.Observation
import com.sanket_satpute_20.ironmind.domain.model.observation.ObservationType
import com.sanket_satpute_20.ironmind.domain.model.pattern.Pattern
import com.sanket_satpute_20.ironmind.domain.model.pattern.PatternCategory
import com.sanket_satpute_20.ironmind.domain.model.pattern.PatternLifecycleState
import com.sanket_satpute_20.ironmind.domain.model.pattern.PatternStatus
import com.sanket_satpute_20.ironmind.domain.model.pattern.PatternType
import com.sanket_satpute_20.ironmind.domain.provider.AppUsageEvent
import com.sanket_satpute_20.ironmind.domain.provider.AppUsageObservationProvider
import com.sanket_satpute_20.ironmind.domain.repository.AppUsageObservationSettingsRepository
import com.sanket_satpute_20.ironmind.domain.repository.ObservationRepository
import com.sanket_satpute_20.ironmind.domain.repository.ProtectionRepository
import com.sanket_satpute_20.ironmind.domain.session.SessionState
import com.sanket_satpute_20.ironmind.domain.usecase.barrier.ConfirmBarrierUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.barrier.DismissBarrierUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.barrier.GetBarriersUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.barrier.RefuteBarrierUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.digital.GetDailyAppUsageIntelligenceUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.insight.ObservePersonalInsightsUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.insight.ReviewPersonalInsightUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.observation.CollectObservationsUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.observation.ExecuteObservationCollectionUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.observation.ObservationCollector
import com.sanket_satpute_20.ironmind.domain.usecase.pattern.ConfirmPatternUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.pattern.DismissPatternUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.pattern.GetPatternsUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.pattern.RefutePatternUseCase
import com.sanket_satpute_20.ironmind.testutil.FakeUserSessionProvider
import com.sanket_satpute_20.ironmind.testutil.TestDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
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

    // Phase 3.6.7.3: Mocked Use Cases & Flows
    private lateinit var getPatternsUseCase: GetPatternsUseCase
    private lateinit var confirmPatternUseCase: ConfirmPatternUseCase
    private lateinit var dismissPatternUseCase: DismissPatternUseCase
    private lateinit var refutePatternUseCase: RefutePatternUseCase
    private lateinit var observePersonalInsightsUseCase: ObservePersonalInsightsUseCase
    private lateinit var reviewPersonalInsightUseCase: ReviewPersonalInsightUseCase
    private lateinit var getBarriersUseCase: GetBarriersUseCase
    private lateinit var confirmBarrierUseCase: ConfirmBarrierUseCase
    private lateinit var dismissBarrierUseCase: DismissBarrierUseCase
    private lateinit var refuteBarrierUseCase: RefuteBarrierUseCase

    private val patternsFlow = MutableStateFlow<List<Pattern>>(emptyList())
    private val insightsFlow = MutableStateFlow<List<PersonalInsight>>(emptyList())
    private val barriersFlow = MutableStateFlow<List<BarrierHypothesis>>(emptyList())

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

        getPatternsUseCase = mockk(relaxed = true)
        confirmPatternUseCase = mockk(relaxed = true)
        dismissPatternUseCase = mockk(relaxed = true)
        refutePatternUseCase = mockk(relaxed = true)
        observePersonalInsightsUseCase = mockk(relaxed = true)
        reviewPersonalInsightUseCase = mockk(relaxed = true)
        getBarriersUseCase = mockk(relaxed = true)
        confirmBarrierUseCase = mockk(relaxed = true)
        dismissBarrierUseCase = mockk(relaxed = true)
        refuteBarrierUseCase = mockk(relaxed = true)

        patternsFlow.value = emptyList()
        insightsFlow.value = emptyList()
        barriersFlow.value = emptyList()

        every { getPatternsUseCase.observe(any()) } answers { patternsFlow }
        every { observePersonalInsightsUseCase(any()) } answers { insightsFlow }
        every { getBarriersUseCase.observe(any()) } answers { barriersFlow }
    }

    private fun createViewModelWithIntelligence(
        patternsUc: GetPatternsUseCase? = getPatternsUseCase,
        confirmPatUc: ConfirmPatternUseCase? = confirmPatternUseCase,
        dismissPatUc: DismissPatternUseCase? = dismissPatternUseCase,
        refutePatUc: RefutePatternUseCase? = refutePatternUseCase,
        observeInsUc: ObservePersonalInsightsUseCase? = observePersonalInsightsUseCase,
        reviewInsUc: ReviewPersonalInsightUseCase? = reviewPersonalInsightUseCase,
        getBarUc: GetBarriersUseCase? = getBarriersUseCase,
        confirmBarUc: ConfirmBarrierUseCase? = confirmBarrierUseCase,
        dismissBarUc: DismissBarrierUseCase? = dismissBarrierUseCase,
        refuteBarUc: RefuteBarrierUseCase? = refuteBarrierUseCase,
        sessionProvider: FakeUserSessionProvider = userSessionProvider,
        executeUseCase: ExecuteObservationCollectionUseCase? = null
    ): DigitalBehaviourViewModel {
        return DigitalBehaviourViewModel(
            getDailyAppUsageIntelligenceUseCase = useCase,
            appUsageObservationProvider = appUsageProvider,
            appUsageSettingsRepository = settingsRepository,
            protectionRepository = protectionRepository,
            userSessionProvider = sessionProvider,
            appNameResolver = appNameResolver,
            clock = testClock,
            zoneId = zoneId,
            executeObservationCollectionUseCase = executeUseCase,
            ioDispatcher = dispatcherRule.testDispatcher,
            getPatternsUseCase = patternsUc,
            confirmPatternUseCase = confirmPatUc,
            dismissPatternUseCase = dismissPatUc,
            refutePatternUseCase = refutePatUc,
            observePersonalInsightsUseCase = observeInsUc,
            reviewPersonalInsightUseCase = reviewInsUc,
            getBarriersUseCase = getBarUc,
            confirmBarrierUseCase = confirmBarUc,
            dismissBarrierUseCase = dismissBarUc,
            refuteBarrierUseCase = refuteBarUc
        )
    }

    private fun createPattern(
        id: String,
        userId: String = "user_test",
        category: PatternCategory = PatternCategory.DIGITAL_BEHAVIOUR,
        lifecycleState: PatternLifecycleState = PatternLifecycleState.ESTABLISHED,
        status: PatternStatus = PatternStatus.ACTIVE,
        confirmationState: MemoryConfirmationState = MemoryConfirmationState.UNCONFIRMED,
        confidence: Float = 0.85f,
        evidenceCount: Int = 5,
        description: String = "High evening app usage"
    ): Pattern {
        val conditions = IntelligenceMetadataCodec.encodePatternConditions(
            category = category,
            lifecycleState = lifecycleState,
            sourceProvenance = listOf("AppUsageObserver")
        )
        return Pattern(
            id = id,
            userId = userId,
            fingerprint = "fp_$id",
            type = PatternType.TIME_PATTERN,
            description = description,
            conditions = conditions,
            predictedBehavior = "Extended screen session",
            confidence = confidence,
            evidenceCount = evidenceCount,
            evidenceReferences = emptyList(),
            firstObservedAt = todayStartMs - 86400000L,
            lastObservedAt = todayStartMs,
            status = status,
            confirmationState = confirmationState,
            createdAt = todayStartMs - 86400000L,
            updatedAt = todayStartMs
        )
    }

    private fun createInsight(
        id: String,
        userId: String = "user_test",
        category: InsightCategory = InsightCategory.DIGITAL_BEHAVIOUR,
        title: String = "Late Night Screen Time",
        explanation: String = "App usage spikes after 11 PM.",
        authority: EpistemicAuthority = EpistemicAuthority.AI_HYPOTHESIS,
        freshness: SourceFreshness = SourceFreshness.CURRENT,
        reviewStatus: InsightReviewStatus = InsightReviewStatus.UNREVIEWED,
        supportingPatternIds: List<String> = emptyList(),
        confidence: Float = 0.78f
    ): PersonalInsight {
        return PersonalInsight(
            id = id,
            userId = userId,
            category = category,
            title = title,
            explanation = explanation,
            explainabilityBlock = ExplainabilityBlock(
                whatWasObserved = "Observed 90m usage between 11 PM and 1 AM",
                observationPeriod = "Last 7 days",
                supportingEvidenceDescription = "7 daily app usage checkpoints",
                whyItMatters = "Associated with delayed sleep and reduced morning focus",
                uncertaintyOrLimitations = "Passive background collection with +/- 15m granularity",
                suggestedNextSteps = "Enable scheduled wind-down protection at 10:30 PM"
            ),
            supportingPatternIds = supportingPatternIds,
            supportingBarrierIds = emptyList(),
            supportingEvidenceIds = listOf("obs_1", "obs_2"),
            linkedRecommendationId = null,
            observationWindowStartMs = todayStartMs - 7 * 86400000L,
            observationWindowEndMs = todayStartMs,
            firstObservedAt = todayStartMs - 7 * 86400000L,
            lastObservedAt = todayStartMs,
            confidence = confidence,
            epistemicAuthority = authority,
            sourceProvenance = listOf("RoomAppUsageCheckpointStore"),
            sourceFreshness = freshness,
            reviewStatus = reviewStatus,
            confirmationState = MemoryConfirmationState.UNCONFIRMED,
            userFeedback = null,
            reviewedAt = null,
            createdAt = todayStartMs - 86400000L,
            updatedAt = todayStartMs
        )
    }

    private fun createBarrier(
        id: String,
        userId: String = "user_test",
        category: BarrierCategory = BarrierCategory.DISTRACTION,
        title: String = "Social media loop",
        description: String = "Unplanned app switching during work blocks",
        status: BarrierStatus = BarrierStatus.ACTIVE,
        confirmationState: BarrierConfirmationState = BarrierConfirmationState.UNCONFIRMED,
        supportingPatternIds: List<String> = emptyList(),
        confidence: Float = 0.75f,
        authority: EpistemicAuthority = EpistemicAuthority.AI_HYPOTHESIS,
        freshness: SourceFreshness = SourceFreshness.CURRENT
    ): BarrierHypothesis {
        return BarrierHypothesis(
            id = id,
            userId = userId,
            category = category,
            description = description,
            confirmationState = confirmationState,
            status = status,
            sourceReflectionId = "refl_$id",
            firstObservedAt = todayStartMs - 3 * 86400000L,
            lastObservedAt = todayStartMs,
            title = title,
            supportingPatternIds = supportingPatternIds,
            evidenceReferences = emptyList(),
            evidenceCount = 3,
            distinctDays = 3,
            confidence = confidence,
            epistemicAuthority = authority,
            sourceProvenance = listOf("AppUsageObservation"),
            sourceFreshness = freshness
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

    // =========================================================================
    // Phase 3.6.6.3: Lifecycle Catchup Tests
    // =========================================================================

    @Test
    fun `lifecycle catchup triggers on authenticated session and refreshes data on completion`() = runTest {
        val collector = FakeObservationCollector(countToReturn = 3)
        val executeUseCase = ExecuteObservationCollectionUseCase(
            CollectObservationsUseCase(listOf(collector))
        )

        appUsageProvider.events = listOf(
            AppUsageEvent("com.google.android.apps.translate", todayStartMs + 1000, todayStartMs + 61000, 60000)
        )

        val viewModel = DigitalBehaviourViewModel(
            getDailyAppUsageIntelligenceUseCase = useCase,
            appUsageObservationProvider = appUsageProvider,
            appUsageSettingsRepository = settingsRepository,
            protectionRepository = protectionRepository,
            userSessionProvider = userSessionProvider,
            appNameResolver = appNameResolver,
            clock = testClock,
            zoneId = zoneId,
            executeObservationCollectionUseCase = executeUseCase,
            ioDispatcher = dispatcherRule.testDispatcher
        )

        advanceUntilIdle()

        // Collector was invoked with active user ID
        assertEquals(1, collector.invocations)
        assertEquals("user_test", collector.lastUserId)

        // UI state reflects loaded intelligence
        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertNotNull(state.dailyUsage)
        assertEquals(60_000L, state.dailyUsage?.totalObservedDurationMs)
    }

    @Test
    fun `refresh triggers lifecycle catchup and updates displayed intelligence`() = runTest {
        val collector = FakeObservationCollector(countToReturn = 2)
        val executeUseCase = ExecuteObservationCollectionUseCase(
            CollectObservationsUseCase(listOf(collector))
        )

        val viewModel = DigitalBehaviourViewModel(
            getDailyAppUsageIntelligenceUseCase = useCase,
            appUsageObservationProvider = appUsageProvider,
            appUsageSettingsRepository = settingsRepository,
            protectionRepository = protectionRepository,
            userSessionProvider = userSessionProvider,
            appNameResolver = appNameResolver,
            clock = testClock,
            zoneId = zoneId,
            executeObservationCollectionUseCase = executeUseCase,
            ioDispatcher = dispatcherRule.testDispatcher
        )

        advanceUntilIdle()
        assertEquals(1, collector.invocations)

        // Add new events and call refresh() (which simulates ON_RESUME or pull-to-refresh)
        appUsageProvider.events = listOf(
            AppUsageEvent("com.google.android.apps.translate", todayStartMs + 1000, todayStartMs + 121000, 120000)
        )

        viewModel.refresh()
        advanceUntilIdle()

        // Collector was invoked a second time
        assertEquals(2, collector.invocations)

        // Intelligence data was reloaded
        val state = viewModel.uiState.value
        assertEquals(120_000L, state.dailyUsage?.totalObservedDurationMs)
    }

    @Test
    fun `catchup is safely skipped when permission is not granted`() = runTest {
        val collector = FakeObservationCollector()
        val executeUseCase = ExecuteObservationCollectionUseCase(
            CollectObservationsUseCase(listOf(collector))
        )

        appUsageProvider.permissionGranted = false

        val viewModel = DigitalBehaviourViewModel(
            getDailyAppUsageIntelligenceUseCase = useCase,
            appUsageObservationProvider = appUsageProvider,
            appUsageSettingsRepository = settingsRepository,
            protectionRepository = protectionRepository,
            userSessionProvider = userSessionProvider,
            appNameResolver = appNameResolver,
            clock = testClock,
            zoneId = zoneId,
            executeObservationCollectionUseCase = executeUseCase,
            ioDispatcher = dispatcherRule.testDispatcher
        )

        advanceUntilIdle()

        // Collector must NOT run if permission is denied
        assertEquals(0, collector.invocations)

        val state = viewModel.uiState.value
        assertEquals(SourceAvailability.PERMISSION_DENIED, state.effectiveAvailability)
        assertNull(state.dailyUsage?.totalObservedDurationMs)
    }

    @Test
    fun `catchup is safely skipped when user consent setting is disabled`() = runTest {
        val collector = FakeObservationCollector()
        val executeUseCase = ExecuteObservationCollectionUseCase(
            CollectObservationsUseCase(listOf(collector))
        )

        settingsRepository.isEnabled = false

        val viewModel = DigitalBehaviourViewModel(
            getDailyAppUsageIntelligenceUseCase = useCase,
            appUsageObservationProvider = appUsageProvider,
            appUsageSettingsRepository = settingsRepository,
            protectionRepository = protectionRepository,
            userSessionProvider = userSessionProvider,
            appNameResolver = appNameResolver,
            clock = testClock,
            zoneId = zoneId,
            executeObservationCollectionUseCase = executeUseCase,
            ioDispatcher = dispatcherRule.testDispatcher
        )

        advanceUntilIdle()

        // Collector must NOT run if user consent setting is disabled
        assertEquals(0, collector.invocations)

        val state = viewModel.uiState.value
        assertEquals(SourceAvailability.PERMISSION_NOT_REQUESTED, state.effectiveAvailability)
        assertNull(state.dailyUsage?.totalObservedDurationMs)
    }

    @Test
    fun `catchup failure does not crash ViewModel or wipe existing rendered usage`() = runTest {
        val collector = FakeObservationCollector(shouldFail = true)
        val executeUseCase = ExecuteObservationCollectionUseCase(
            CollectObservationsUseCase(listOf(collector))
        )

        appUsageProvider.events = listOf(
            AppUsageEvent("com.google.android.apps.translate", todayStartMs + 1000, todayStartMs + 31000, 30000)
        )

        val viewModel = DigitalBehaviourViewModel(
            getDailyAppUsageIntelligenceUseCase = useCase,
            appUsageObservationProvider = appUsageProvider,
            appUsageSettingsRepository = settingsRepository,
            protectionRepository = protectionRepository,
            userSessionProvider = userSessionProvider,
            appNameResolver = appNameResolver,
            clock = testClock,
            zoneId = zoneId,
            executeObservationCollectionUseCase = executeUseCase,
            ioDispatcher = dispatcherRule.testDispatcher
        )

        advanceUntilIdle()

        // Collection was attempted and failed gracefully
        assertEquals(1, collector.invocations)

        // Existing loaded data is preserved, no crash
        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertEquals(30_000L, state.dailyUsage?.totalObservedDurationMs)
    }

    // =========================================================================
    // Phase 3.6.7.3: Digital Behaviour Intelligence UI Integration Tests
    // =========================================================================

    @Test
    fun `reactive pattern loading updates uiState with observed patterns`() = runTest {
        patternsFlow.value = listOf(
            createPattern("p1", category = PatternCategory.DIGITAL_BEHAVIOUR)
        )

        val viewModel = createViewModelWithIntelligence()
        advanceUntilIdle()

        assertEquals(1, viewModel.uiState.value.patterns.size)
        assertEquals(1, viewModel.uiState.value.digitalPatterns.size)
        assertEquals("p1", viewModel.uiState.value.digitalPatterns[0].id)

        // Reactively emit an additional pattern
        patternsFlow.value = listOf(
            createPattern("p1", category = PatternCategory.DIGITAL_BEHAVIOUR),
            createPattern("p2", category = PatternCategory.FOCUS)
        )
        advanceUntilIdle()

        assertEquals(2, viewModel.uiState.value.patterns.size)
        assertEquals(2, viewModel.uiState.value.digitalPatterns.size)
    }

    @Test
    fun `reactive insight loading updates uiState with observed insights`() = runTest {
        insightsFlow.value = listOf(
            createInsight("ins_1", category = InsightCategory.DIGITAL_BEHAVIOUR)
        )

        val viewModel = createViewModelWithIntelligence()
        advanceUntilIdle()

        assertEquals(1, viewModel.uiState.value.insights.size)
        assertEquals(1, viewModel.uiState.value.digitalInsights.size)
        assertEquals("ins_1", viewModel.uiState.value.digitalInsights[0].id)

        // Reactively emit an updated list
        insightsFlow.value = listOf(
            createInsight("ins_1", category = InsightCategory.DIGITAL_BEHAVIOUR),
            createInsight("ins_2", category = InsightCategory.CROSS_DOMAIN)
        )
        advanceUntilIdle()

        assertEquals(2, viewModel.uiState.value.insights.size)
        assertEquals(2, viewModel.uiState.value.digitalInsights.size)
    }

    @Test
    fun `reactive barrier loading updates uiState with observed barriers`() = runTest {
        barriersFlow.value = listOf(
            createBarrier("bar_1", category = BarrierCategory.DISTRACTION)
        )

        val viewModel = createViewModelWithIntelligence()
        advanceUntilIdle()

        assertEquals(1, viewModel.uiState.value.barriers.size)
        assertEquals(1, viewModel.uiState.value.digitalBarriers.size)
        assertEquals("bar_1", viewModel.uiState.value.digitalBarriers[0].id)

        // Reactively emit an updated list
        barriersFlow.value = listOf(
            createBarrier("bar_1", category = BarrierCategory.DISTRACTION),
            createBarrier("bar_2", category = BarrierCategory.ENVIRONMENTAL_FRICTION)
        )
        advanceUntilIdle()

        assertEquals(2, viewModel.uiState.value.barriers.size)
        assertEquals(2, viewModel.uiState.value.digitalBarriers.size)
    }

    @Test
    fun `correct digital category filtering includes digital categories and excludes non-digital`() = runTest {
        patternsFlow.value = listOf(
            createPattern("p_dig", category = PatternCategory.DIGITAL_BEHAVIOUR),
            createPattern("p_focus", category = PatternCategory.FOCUS),
            createPattern("p_cross", category = PatternCategory.CROSS_DOMAIN),
            createPattern("p_prod", category = PatternCategory.PRODUCTIVITY),
            createPattern("p_del", category = PatternCategory.DIGITAL_BEHAVIOUR, status = PatternStatus.DELETED)
        )
        insightsFlow.value = listOf(
            createInsight("i_dig", category = InsightCategory.DIGITAL_BEHAVIOUR),
            createInsight("i_focus", category = InsightCategory.FOCUS),
            createInsight("i_cross", category = InsightCategory.CROSS_DOMAIN),
            createInsight("i_prod", category = InsightCategory.PRODUCTIVITY),
            createInsight("i_task", category = InsightCategory.TASK_SCHEDULING)
        )
        barriersFlow.value = listOf(
            createBarrier("b_dist", category = BarrierCategory.DISTRACTION),
            createBarrier("b_friction", category = BarrierCategory.ENVIRONMENTAL_FRICTION),
            createBarrier("b_linked", category = BarrierCategory.UNCERTAINTY, supportingPatternIds = listOf("p_dig")),
            createBarrier("b_unlinked", category = BarrierCategory.UNCERTAINTY, supportingPatternIds = emptyList()),
            createBarrier("b_exp", category = BarrierCategory.DISTRACTION, status = BarrierStatus.EXPIRED)
        )

        val viewModel = createViewModelWithIntelligence()
        advanceUntilIdle()

        val state = viewModel.uiState.value

        // Total unfiltered
        assertEquals(5, state.patterns.size)
        assertEquals(5, state.insights.size)
        assertEquals(5, state.barriers.size)

        // Filtered digital patterns: DIGITAL_BEHAVIOUR, CROSS_DOMAIN, FOCUS (non-deleted)
        val digPatIds = state.digitalPatterns.map { it.id }
        assertEquals(3, digPatIds.size)
        assertTrue(digPatIds.containsAll(listOf("p_dig", "p_focus", "p_cross")))

        // Filtered digital insights: DIGITAL_BEHAVIOUR, FOCUS, CROSS_DOMAIN
        val digInsIds = state.digitalInsights.map { it.id }
        assertEquals(3, digInsIds.size)
        assertTrue(digInsIds.containsAll(listOf("i_dig", "i_focus", "i_cross")))

        // Filtered digital barriers: DISTRACTION, ENVIRONMENTAL_FRICTION, or supporting digital patterns (non-expired)
        val digBarIds = state.digitalBarriers.map { it.id }
        assertEquals(3, digBarIds.size)
        assertTrue(digBarIds.containsAll(listOf("b_dist", "b_friction", "b_linked")))
    }

    @Test
    fun `empty states are handled gracefully with empty lists and no errors`() = runTest {
        patternsFlow.value = emptyList()
        insightsFlow.value = emptyList()
        barriersFlow.value = emptyList()

        val viewModel = createViewModelWithIntelligence()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.patterns.isEmpty())
        assertTrue(state.digitalPatterns.isEmpty())
        assertTrue(state.insights.isEmpty())
        assertTrue(state.digitalInsights.isEmpty())
        assertTrue(state.barriers.isEmpty())
        assertTrue(state.digitalBarriers.isEmpty())
        assertNull(state.errorMessage)
        assertNull(state.actionError)
        assertFalse(state.isSubmittingAction)
    }

    @Test
    fun `loading and repository failure does not crash ViewModel and keeps UI stable`() = runTest {
        every { getPatternsUseCase.observe(any()) } returns flow {
            throw RuntimeException("Database error in patterns")
        }

        val viewModel = createViewModelWithIntelligence()
        advanceUntilIdle()

        // Does not crash, patterns remains empty, UI is usable
        val state = viewModel.uiState.value
        assertTrue(state.patterns.isEmpty())
        assertFalse(state.isLoading)
    }

    @Test
    fun `permission denied gating yields PERMISSION_DENIED without false zero claims`() = runTest {
        appUsageProvider.permissionGranted = false
        insightsFlow.value = listOf(createInsight("ins_1"))

        val viewModel = createViewModelWithIntelligence()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isUsageAccessPermissionGranted)
        assertEquals(SourceAvailability.PERMISSION_DENIED, state.effectiveAvailability)
        assertNull(state.dailyUsage?.totalObservedDurationMs)
        // Intelligence lists can still be observed independently
        assertEquals(1, state.digitalInsights.size)
    }

    @Test
    fun `partial and stale data representation correctly reflects source freshness`() = runTest {
        insightsFlow.value = listOf(
            createInsight("ins_stale", freshness = SourceFreshness.STALE),
            createInsight("ins_current", freshness = SourceFreshness.CURRENT)
        )

        val viewModel = createViewModelWithIntelligence()
        advanceUntilIdle()

        val insights = viewModel.uiState.value.digitalInsights
        assertEquals(2, insights.size)
        assertEquals(SourceFreshness.STALE, insights.find { it.id == "ins_stale" }?.sourceFreshness)
        assertEquals(SourceFreshness.CURRENT, insights.find { it.id == "ins_current" }?.sourceFreshness)
    }

    @Test
    fun `session switching to Initializing or new user clears previous-user intelligence`() = runTest {
        patternsFlow.value = listOf(createPattern("p_user1"))
        insightsFlow.value = listOf(createInsight("i_user1"))
        barriersFlow.value = listOf(createBarrier("b_user1"))

        val viewModel = createViewModelWithIntelligence()
        advanceUntilIdle()

        assertEquals(1, viewModel.uiState.value.patterns.size)
        assertEquals(1, viewModel.uiState.value.insights.size)
        assertEquals(1, viewModel.uiState.value.barriers.size)

        // Session transitions to Initializing
        userSessionProvider.setState(SessionState.Initializing)
        advanceUntilIdle()

        // Intelligence must be cleared immediately
        assertTrue(viewModel.uiState.value.patterns.isEmpty())
        assertTrue(viewModel.uiState.value.insights.isEmpty())
        assertTrue(viewModel.uiState.value.barriers.isEmpty())
    }

    @Test
    fun `previous user Flow emissions cannot overwrite new user state`() = runTest {
        val user1Patterns = MutableStateFlow<List<Pattern>>(listOf(createPattern("p_u1", userId = "user_test")))
        val user2Patterns = MutableStateFlow<List<Pattern>>(listOf(createPattern("p_u2", userId = "user_2")))

        every { getPatternsUseCase.observe("user_test") } returns user1Patterns
        every { getPatternsUseCase.observe("user_2") } returns user2Patterns

        val viewModel = createViewModelWithIntelligence()
        advanceUntilIdle()

        assertEquals(1, viewModel.uiState.value.patterns.size)
        assertEquals("p_u1", viewModel.uiState.value.patterns[0].id)

        // Switch to user 2
        userSessionProvider.setState(SessionState.Authenticated("user_2", isAnonymous = false))
        advanceUntilIdle()

        assertEquals(1, viewModel.uiState.value.patterns.size)
        assertEquals("p_u2", viewModel.uiState.value.patterns[0].id)

        // Delayed emission from user 1's old flow
        user1Patterns.value = listOf(createPattern("p_u1_delayed", userId = "user_test"))
        advanceUntilIdle()

        // User 2's state is preserved; old user's emission was discarded
        assertEquals(1, viewModel.uiState.value.patterns.size)
        assertEquals("p_u2", viewModel.uiState.value.patterns[0].id)
    }

    @Test
    fun `pattern confirmation calls confirmPatternUseCase with active userId`() = runTest {
        coEvery { confirmPatternUseCase("user_test", "pat_100") } returns Result.Success(Unit)

        val viewModel = createViewModelWithIntelligence()
        advanceUntilIdle()

        viewModel.confirmPattern("pat_100")
        advanceUntilIdle()

        coVerify(exactly = 1) { confirmPatternUseCase("user_test", "pat_100") }
        assertFalse(viewModel.uiState.value.isSubmittingAction)
        assertNull(viewModel.uiState.value.actionError)
    }

    @Test
    fun `pattern dismissal and refutation dispatch with active userId`() = runTest {
        coEvery { dismissPatternUseCase("user_test", "pat_200") } returns Result.Success(Unit)
        coEvery { refutePatternUseCase("user_test", "pat_300") } returns Result.Success(Unit)

        val viewModel = createViewModelWithIntelligence()
        advanceUntilIdle()

        viewModel.dismissPattern("pat_200")
        advanceUntilIdle()
        coVerify(exactly = 1) { dismissPatternUseCase("user_test", "pat_200") }

        viewModel.refutePattern("pat_300")
        advanceUntilIdle()
        coVerify(exactly = 1) { refutePatternUseCase("user_test", "pat_300") }
    }

    @Test
    fun `insight mark useful calls reviewPersonalInsightUseCase`() = runTest {
        coEvery { reviewPersonalInsightUseCase.markUseful("ins_100", "user_test") } returns Result.Success(Unit)

        val viewModel = createViewModelWithIntelligence()
        advanceUntilIdle()

        viewModel.markInsightUseful("ins_100")
        advanceUntilIdle()

        coVerify(exactly = 1) { reviewPersonalInsightUseCase.markUseful("ins_100", "user_test") }
        assertFalse(viewModel.uiState.value.isSubmittingAction)
        assertNull(viewModel.uiState.value.actionError)
    }

    @Test
    fun `insight dismiss and review later dispatch to reviewPersonalInsightUseCase`() = runTest {
        coEvery { reviewPersonalInsightUseCase.dismiss("ins_200", "user_test") } returns Result.Success(Unit)
        coEvery { reviewPersonalInsightUseCase.reviewLater("ins_300", "user_test") } returns Result.Success(Unit)

        val viewModel = createViewModelWithIntelligence()
        advanceUntilIdle()

        viewModel.dismissInsight("ins_200")
        advanceUntilIdle()
        coVerify(exactly = 1) { reviewPersonalInsightUseCase.dismiss("ins_200", "user_test") }

        viewModel.reviewInsightLater("ins_300")
        advanceUntilIdle()
        coVerify(exactly = 1) { reviewPersonalInsightUseCase.reviewLater("ins_300", "user_test") }
    }

    @Test
    fun `barrier confirmation calls confirmBarrierUseCase with active userId`() = runTest {
        coEvery { confirmBarrierUseCase("user_test", "bar_100") } returns Result.Success(Unit)

        val viewModel = createViewModelWithIntelligence()
        advanceUntilIdle()

        viewModel.confirmBarrier("bar_100")
        advanceUntilIdle()

        coVerify(exactly = 1) { confirmBarrierUseCase("user_test", "bar_100") }
        assertFalse(viewModel.uiState.value.isSubmittingAction)
        assertNull(viewModel.uiState.value.actionError)
    }

    @Test
    fun `barrier dismissal and refutation dispatch with active userId`() = runTest {
        coEvery { dismissBarrierUseCase("user_test", "bar_200") } returns Result.Success(Unit)
        coEvery { refuteBarrierUseCase("user_test", "bar_300") } returns Result.Success(Unit)

        val viewModel = createViewModelWithIntelligence()
        advanceUntilIdle()

        viewModel.dismissBarrier("bar_200")
        advanceUntilIdle()
        coVerify(exactly = 1) { dismissBarrierUseCase("user_test", "bar_200") }

        viewModel.refuteBarrier("bar_300")
        advanceUntilIdle()
        coVerify(exactly = 1) { refuteBarrierUseCase("user_test", "bar_300") }
    }

    @Test
    fun `action failure sets actionError and resets isSubmittingAction, clearActionError clears it`() = runTest {
        coEvery { confirmPatternUseCase("user_test", "fail_pat") } returns Result.Failure(IllegalStateException("Network offline"))

        val viewModel = createViewModelWithIntelligence()
        advanceUntilIdle()

        viewModel.confirmPattern("fail_pat")
        advanceUntilIdle()

        val stateWithError = viewModel.uiState.value
        assertFalse(stateWithError.isSubmittingAction)
        assertEquals("Network offline", stateWithError.actionError)

        // Clear error
        viewModel.clearActionError()
        assertNull(viewModel.uiState.value.actionError)
    }

    @Test
    fun `duplicate concurrent action submissions are rejected while action is in progress`() = runTest {
        val gate = CompletableDeferred<Unit>()
        coEvery { confirmPatternUseCase("user_test", "slow_pat") } coAnswers {
            gate.await()
            Result.Success(Unit)
        }

        val viewModel = createViewModelWithIntelligence()
        advanceUntilIdle()

        // First action starts and suspends at gate
        viewModel.confirmPattern("slow_pat")
        // Check submitting action is true
        assertTrue(viewModel.uiState.value.isSubmittingAction)

        // Second action call while first is in flight
        viewModel.confirmPattern("another_pat")

        // Release first action
        gate.complete(Unit)
        advanceUntilIdle()

        // First action completed
        coVerify(exactly = 1) { confirmPatternUseCase("user_test", "slow_pat") }
        // Second action was skipped
        coVerify(exactly = 0) { confirmPatternUseCase("user_test", "another_pat") }
        assertFalse(viewModel.uiState.value.isSubmittingAction)
    }

    @Test
    fun `marking insight useful does not alter underlying epistemic authority`() = runTest {
        val initialInsight = createInsight(
            id = "ins_hypo",
            authority = EpistemicAuthority.AI_HYPOTHESIS,
            reviewStatus = InsightReviewStatus.UNREVIEWED
        )
        insightsFlow.value = listOf(initialInsight)

        val viewModel = createViewModelWithIntelligence()
        advanceUntilIdle()

        assertEquals(EpistemicAuthority.AI_HYPOTHESIS, viewModel.uiState.value.digitalInsights[0].epistemicAuthority)
        assertEquals(InsightReviewStatus.UNREVIEWED, viewModel.uiState.value.digitalInsights[0].reviewStatus)

        // Simulate markUseful repository update: reviewStatus becomes USEFUL, authority remains AI_HYPOTHESIS
        val updatedInsight = initialInsight.copy(
            reviewStatus = InsightReviewStatus.USEFUL,
            userFeedback = null
        )
        insightsFlow.value = listOf(updatedInsight)
        advanceUntilIdle()

        val displayed = viewModel.uiState.value.digitalInsights[0]
        assertEquals(InsightReviewStatus.USEFUL, displayed.reviewStatus)
        // Invariant: User feedback does NOT upgrade AI_HYPOTHESIS to USER_CONFIRMED_FACT or SYSTEM_RECORDED_OBSERVATION
        assertEquals(EpistemicAuthority.AI_HYPOTHESIS, displayed.epistemicAuthority)
    }

    // --- Fakes ---

    private class FakeObservationCollector(
        override val collectorName: String = "FakeAppUsage",
        var countToReturn: Int = 5,
        var shouldFail: Boolean = false
    ) : ObservationCollector {
        var invocations = 0
        var lastUserId: String? = null

        override suspend fun invoke(userId: String): Result<Int, Exception> {
            invocations++
            lastUserId = userId
            return if (shouldFail) {
                Result.Failure(Exception("Collection failed"))
            } else {
                Result.Success(countToReturn)
            }
        }
    }

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
        var isEnabled: Boolean = true

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
