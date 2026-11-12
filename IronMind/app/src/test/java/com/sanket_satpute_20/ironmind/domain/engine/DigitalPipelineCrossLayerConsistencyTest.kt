package com.sanket_satpute_20.ironmind.domain.engine

import com.sanket_satpute_20.ironmind.domain.common.Clock
import com.sanket_satpute_20.ironmind.domain.common.IdGenerator
import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.*
import com.sanket_satpute_20.ironmind.domain.model.barrier.BarrierConfirmationState
import com.sanket_satpute_20.ironmind.domain.model.barrier.BarrierHypothesis
import com.sanket_satpute_20.ironmind.domain.model.barrier.BarrierStatus
import com.sanket_satpute_20.ironmind.domain.model.context.*
import com.sanket_satpute_20.ironmind.domain.model.insight.InsightCategory
import com.sanket_satpute_20.ironmind.domain.model.observation.Observation
import com.sanket_satpute_20.ironmind.domain.model.observation.ObservationProvenance
import com.sanket_satpute_20.ironmind.domain.model.observation.ObservationSource
import com.sanket_satpute_20.ironmind.domain.model.observation.ObservationType
import com.sanket_satpute_20.ironmind.domain.model.pattern.PatternLifecycleState
import com.sanket_satpute_20.ironmind.domain.provider.ActiveForegroundSession
import com.sanket_satpute_20.ironmind.domain.provider.AppUsageEvent
import com.sanket_satpute_20.ironmind.domain.provider.AppUsageObservationProvider
import com.sanket_satpute_20.ironmind.domain.provider.AppUsageReconstructionResult
import com.sanket_satpute_20.ironmind.domain.repository.*
import com.sanket_satpute_20.ironmind.domain.session.SessionState
import com.sanket_satpute_20.ironmind.domain.usecase.digital.GetDailyAppUsageIntelligenceUseCase
import com.sanket_satpute_20.ironmind.testutil.FakeUserSessionProvider
import com.sanket_satpute_20.ironmind.testutil.TestDispatcherRule
import com.sanket_satpute_20.ironmind.testutil.fake.FakeClock
import com.sanket_satpute_20.ironmind.testutil.fake.FakeObservationRepository
import com.sanket_satpute_20.ironmind.ui.screens.digital.DigitalBehaviourViewModel
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/**
 * Phase 3.6.6.4: Workstream G — Cross-Layer Consistency Validation
 *
 * Verifies that the exact same persisted Room observation evidence produces
 * mathematically and semantically identical results across all consumer layers:
 * 1. GetDailyAppUsageIntelligenceUseCase
 * 2. ContextEngineImpl
 * 3. DigitalBehaviourViewModel
 * 4. BehaviourPatternDiscoveryEngine / PersonalInsightDiscoveryEngine / BarrierHypothesisDiscoveryEngine
 *
 * Guarantees:
 * - Identical durations and package ranks.
 * - Same availability status (AVAILABLE).
 * - Room_Observation provenance maintained.
 * - Strict epistemic separation (FACT vs OBSERVATION vs HYPOTHESIS).
 */
class DigitalPipelineCrossLayerConsistencyTest {

    @get:Rule
    val dispatcherRule = TestDispatcherRule()

    private lateinit var observationRepository: FakeObservationRepository
    private lateinit var checkpointStore: FakeCrossLayerCheckpointStore
    private lateinit var settingsRepository: FakeCrossLayerSettingsRepo
    private lateinit var provider: FakeCrossLayerProvider
    private lateinit var clock: FakeClock

    private lateinit var dailyIntelligenceUseCase: GetDailyAppUsageIntelligenceUseCase
    private lateinit var contextEngine: ContextEngineImpl
    private lateinit var viewModel: DigitalBehaviourViewModel

    private val fixedZone = ZoneId.of("UTC")
    // Base time: 2024-06-15T12:00:00Z
    private val dayStartMs = LocalDate.of(2024, 6, 15).atStartOfDay(fixedZone).toInstant().toEpochMilli()
    private val dayEndMs = LocalDate.of(2024, 6, 16).atStartOfDay(fixedZone).toInstant().toEpochMilli()
    private val middayMs = dayStartMs + 12 * 3600_000L

    private val userId = SessionState.DEFAULT_LEGACY_USER_ID

    @Before
    fun setup() {
        clock = FakeClock().apply { setTime(middayMs) }
        observationRepository = FakeObservationRepository()
        checkpointStore = FakeCrossLayerCheckpointStore()
        settingsRepository = FakeCrossLayerSettingsRepo()
        provider = FakeCrossLayerProvider()

        dailyIntelligenceUseCase = GetDailyAppUsageIntelligenceUseCase(
            appUsageObservationProvider = provider,
            appUsageSettingsRepository = settingsRepository,
            observationRepository = observationRepository,
            clock = clock
        )

        contextEngine = ContextEngineImpl(
            clock = clock,
            commitmentRepository = FakeCommitmentRepository(),
            eventRepository = FakeEventRepository(),
            buildFactualContextSnapshotUseCase = mockk(relaxed = true),
            reflectionRepository = FakeReflectionRepository(),
            protectionRepository = FakeProtectionRepository(),
            goalRepository = FakeGoalRepository(),
            patternRepository = mockk(relaxed = true),
            appUsageObservationProvider = provider,
            appUsageSettingsRepository = settingsRepository,
            observationRepository = observationRepository,
            checkpointStore = checkpointStore
        )

        viewModel = DigitalBehaviourViewModel(
            getDailyAppUsageIntelligenceUseCase = dailyIntelligenceUseCase,
            appUsageObservationProvider = provider,
            appUsageSettingsRepository = settingsRepository,
            protectionRepository = FakeProtectionRepository(),
            userSessionProvider = null,
            appNameResolver = { pkg ->
                when (pkg) {
                    "com.productive.docs" -> "Docs"
                    "com.social.media" -> "Social"
                    "com.video.stream" -> "Video"
                    else -> pkg
                }
            },
            clock = clock,
            zoneId = fixedZone,
            executeObservationCollectionUseCase = null,
            ioDispatcher = Dispatchers.Unconfined
        )
    }

    @Test
    fun testCrossLayerConsistency_identicalPersistedEvidenceYieldsIdenticalMetricsAcrossAllLayers() = runBlocking {
        // =====================================================================
        // 1. Seed Shared Controlled Evidence in Room Observation Repository
        // =====================================================================
        val obs1 = Observation(
            id = "obs-docs",
            userId = userId,
            type = ObservationType.APP_USAGE_SESSION,
            source = ObservationSource.APP_USAGE,
            occurredAt = dayStartMs + 2 * 3600_000L,
            recordedAt = middayMs,
            subjectId = null,
            value = "com.productive.docs",
            context = "duration_ms=3600000", // 1h 00m = 3,600,000 ms
            confidence = null,
            provenance = ObservationProvenance(ObservationSource.APP_USAGE, "UsageStatsManager", middayMs)
        )
        val obs2 = Observation(
            id = "obs-social",
            userId = userId,
            type = ObservationType.APP_USAGE_SESSION,
            source = ObservationSource.APP_USAGE,
            occurredAt = dayStartMs + 4 * 3600_000L,
            recordedAt = middayMs,
            subjectId = null,
            value = "com.social.media",
            context = "duration_ms=1800000", // 30m = 1,800,000 ms
            confidence = null,
            provenance = ObservationProvenance(ObservationSource.APP_USAGE, "UsageStatsManager", middayMs)
        )
        val obs3 = Observation(
            id = "obs-video",
            userId = userId,
            type = ObservationType.APP_USAGE_SESSION,
            source = ObservationSource.APP_USAGE,
            occurredAt = dayStartMs + 6 * 3600_000L,
            recordedAt = middayMs,
            subjectId = null,
            value = "com.video.stream",
            context = "duration_ms=900000", // 15m = 900,000 ms
            confidence = null,
            provenance = ObservationProvenance(ObservationSource.APP_USAGE, "UsageStatsManager", middayMs)
        )

        observationRepository.insertObservation(obs1)
        observationRepository.insertObservation(obs2)
        observationRepository.insertObservation(obs3)

        val totalExpectedDuration = 3600_000L + 1800_000L + 900_000L // 6,300,000 ms (1h 45m)

        // Checkpoint set at dayEndMs (complete coverage)
        checkpointStore.saveCheckpoint(
            AppUsageCheckpoint(
                userId = userId,
                lastCollectedIntervalEndMs = dayEndMs,
                activePackageName = null,
                activeSessionStartTimeMs = null,
                updatedAt = dayEndMs
            )
        )

        // =====================================================================
        // Layer 1: GetDailyAppUsageIntelligenceUseCase
        // =====================================================================
        val dailyResult = dailyIntelligenceUseCase(
            userId = userId,
            targetDayMillis = middayMs,
            zoneId = fixedZone,
            customWindowStartMs = dayStartMs,
            customWindowEndMs = dayEndMs
        )
        assertTrue(dailyResult is Result.Success)
        val daily = (dailyResult as Result.Success).data

        assertEquals("Layer 1 must report AVAILABLE", SourceAvailability.AVAILABLE, daily.availability)
        assertEquals("Layer 1 total duration must be exactly 6,300,000 ms", totalExpectedDuration, daily.totalObservedDurationMs)
        assertEquals("Layer 1 session count must be 3", 3, daily.totalSessionsCount)
        assertEquals("Layer 1 most used apps size must be 3", 3, daily.mostUsedApps.size)
        // Deterministic rank ordering
        assertEquals("com.productive.docs", daily.mostUsedApps[0].packageName)
        assertEquals(3600_000L, daily.mostUsedApps[0].totalDurationMs)
        assertEquals("com.social.media", daily.mostUsedApps[1].packageName)
        assertEquals(1800_000L, daily.mostUsedApps[1].totalDurationMs)
        assertEquals("com.video.stream", daily.mostUsedApps[2].packageName)
        assertEquals(900_000L, daily.mostUsedApps[2].totalDurationMs)

        // =====================================================================
        // Layer 2: ContextEngineImpl / UnifiedContext
        // =====================================================================
        val ctxResult = contextEngine.getUnifiedContext(userId, dayStartMs, dayEndMs)
        assertTrue(ctxResult is Result.Success)
        val unified = (ctxResult as Result.Success).data
        val digital = unified.digitalBehaviour

        assertEquals("Layer 2 must report AVAILABLE", SourceAvailability.AVAILABLE, digital.availability)
        assertEquals("Layer 2 provenance source must be Room_Observation", "Room_Observation", unified.provenanceMap["AppUsage"]?.sourceName)
        assertEquals("Layer 2 appUsages size must be 3", 3, digital.appUsages.size)
        assertEquals("com.productive.docs", digital.appUsages[0].packageName)
        assertEquals(3600_000L, digital.appUsages[0].totalDurationMs)
        assertEquals("com.social.media", digital.appUsages[1].packageName)
        assertEquals(1800_000L, digital.appUsages[1].totalDurationMs)
        assertEquals("com.video.stream", digital.appUsages[2].packageName)
        assertEquals(900_000L, digital.appUsages[2].totalDurationMs)

        val totalEngineDuration = digital.appUsages.sumOf { it.totalDurationMs }
        assertEquals("Layer 2 total duration must match Layer 1 exactly", totalExpectedDuration, totalEngineDuration)

        // Epistemic Authority: System recorded observation
        for (usage in digital.appUsages) {
            assertEquals("Digital usage must strictly carry SYSTEM_RECORDED_OBSERVATION authority", EpistemicAuthority.SYSTEM_RECORDED_OBSERVATION, usage.authority)
        }

        // =====================================================================
        // Layer 3: DigitalBehaviourViewModel
        // =====================================================================
        viewModel.refresh()
        val uiState = viewModel.uiState.value

        assertEquals("Layer 3 ViewModel must not be in loading state", false, uiState.isLoading)
        assertNotNull("Layer 3 dailyUsage must not be null", uiState.dailyUsage)
        assertEquals("Layer 3 total duration must match exactly", totalExpectedDuration, uiState.dailyUsage!!.totalObservedDurationMs)
        assertEquals("Layer 3 app items must be 3", 3, uiState.dailyUsage!!.appUsages.size)
        assertEquals(3600_000L, uiState.dailyUsage!!.appUsages[0].totalDurationMs)
        assertEquals(1800_000L, uiState.dailyUsage!!.appUsages[1].totalDurationMs)
        assertEquals(900_000L, uiState.dailyUsage!!.appUsages[2].totalDurationMs)
    }

    @Test
    fun testDiscoveryEngine_insufficientEvidenceDoesNotCreateEstablishedPattern() = runBlocking {
        // Evidence: single observation (N=1) on a single day
        val obs = Observation(
            id = "obs-single",
            userId = userId,
            type = ObservationType.APP_USAGE_SESSION,
            source = ObservationSource.APP_USAGE,
            occurredAt = dayStartMs + 3600_000L,
            recordedAt = middayMs,
            subjectId = null,
            value = "com.distracting.app",
            context = "duration_ms=1800000",
            confidence = null,
            provenance = ObservationProvenance(ObservationSource.APP_USAGE, "UsageStatsManager", middayMs)
        )
        observationRepository.insertObservation(obs)
        checkpointStore.saveCheckpoint(
            AppUsageCheckpoint(userId, dayEndMs, null, null, dayEndMs)
        )

        val ctxResult = contextEngine.getUnifiedContext(userId, dayStartMs, dayEndMs)
        assertTrue(ctxResult is Result.Success)
        val unified = (ctxResult as Result.Success).data

        val patternDiscoveryEngine = BehaviourPatternDiscoveryEngine(
            evidenceSufficiencyEvaluator = EvidenceSufficiencyEvaluator(),
            clock = clock
        )

        val patterns = patternDiscoveryEngine.discoverPatterns(
            unifiedContext = unified,
            existingPatterns = emptyList(),
            zoneId = fixedZone
        )

        // Epistemic safety invariant: A single observation (N=1) or single-day evidence
        // NEVER advances to ESTABLISHED. Any discovered candidate pattern must be strictly CANDIDATE.
        for (pattern in patterns) {
            assertTrue(
                "Discovered pattern must NOT be ESTABLISHED on insufficient evidence, but was ${pattern.lifecycleState}",
                pattern.lifecycleState != PatternLifecycleState.ESTABLISHED
            )
            assertTrue(
                "Candidate confidence must not exceed CANDIDATE_CONFIDENCE cap (0.35f)",
                pattern.confidence <= BehaviourPatternDiscoveryEngine.CANDIDATE_CONFIDENCE
            )
        }
    }

    @Test
    fun testBarrierHypothesisDiscoveryEngine_barriersAreStrictlyUnconfirmedHypothesis() = runBlocking {
        // Seed observation and build context
        val obs = Observation(
            id = "obs-barrier",
            userId = userId,
            type = ObservationType.APP_USAGE_SESSION,
            source = ObservationSource.APP_USAGE,
            occurredAt = dayStartMs + 3600_000L,
            recordedAt = middayMs,
            subjectId = null,
            value = "com.distracting.app",
            context = "duration_ms=3600000",
            confidence = null,
            provenance = ObservationProvenance(ObservationSource.APP_USAGE, "UsageStatsManager", middayMs)
        )
        observationRepository.insertObservation(obs)
        checkpointStore.saveCheckpoint(
            AppUsageCheckpoint(userId, dayEndMs, null, null, dayEndMs)
        )

        val ctxResult = contextEngine.getUnifiedContext(userId, dayStartMs, dayEndMs)
        assertTrue(ctxResult is Result.Success)
        val unified = (ctxResult as Result.Success).data

        val barrierEngine = BarrierHypothesisDiscoveryEngine(
            evidenceSufficiencyEvaluator = EvidenceSufficiencyEvaluator(),
            idGenerator = fakeIdGenerator
        )

        val discoveryResult = barrierEngine.discoverBarrierHypotheses(
            userId = userId,
            unifiedContext = unified,
            patterns = emptyList(),
            existingBarriers = emptyList(),
            zoneId = fixedZone,
            evaluationTimeMs = middayMs
        )

        // Epistemic safety invariant: All discovered hypotheses must be strictly UNCONFIRMED
        // with EpistemicAuthority.AI_HYPOTHESIS. No engine ever creates a pre-CONFIRMED barrier.
        for (hypothesis in discoveryResult.discoveredHypotheses) {
            assertEquals(
                "Barrier hypothesis must strictly have UNCONFIRMED state",
                BarrierConfirmationState.UNCONFIRMED,
                hypothesis.confirmationState
            )
            assertEquals(
                "Barrier hypothesis must carry AI_HYPOTHESIS authority",
                EpistemicAuthority.AI_HYPOTHESIS,
                hypothesis.epistemicAuthority
            )
            assertEquals(
                "Barrier status must be ACTIVE until user reviews",
                BarrierStatus.ACTIVE,
                hypothesis.status
            )
        }
    }

    @Test
    fun testPersonalInsightDiscoveryEngine_structuredExplainabilityAndNonCausalDisclaimer() = runBlocking {
        val obs = Observation(
            id = "obs-insight",
            userId = userId,
            type = ObservationType.APP_USAGE_SESSION,
            source = ObservationSource.APP_USAGE,
            occurredAt = dayStartMs + 3600_000L,
            recordedAt = middayMs,
            subjectId = null,
            value = "com.social.network",
            context = "duration_ms=7200000",
            confidence = null,
            provenance = ObservationProvenance(ObservationSource.APP_USAGE, "UsageStatsManager", middayMs)
        )
        observationRepository.insertObservation(obs)
        checkpointStore.saveCheckpoint(
            AppUsageCheckpoint(userId, dayEndMs, null, null, dayEndMs)
        )

        val ctxResult = contextEngine.getUnifiedContext(userId, dayStartMs, dayEndMs)
        assertTrue(ctxResult is Result.Success)
        val unified = (ctxResult as Result.Success).data

        val insightEngine = PersonalInsightDiscoveryEngine(
            clock = clock,
            idGenerator = fakeIdGenerator
        )

        val insightResult = insightEngine.discoverInsights(
            userId = userId,
            unifiedContext = unified,
            patterns = emptyList(),
            barrierHypotheses = emptyList(),
            recommendations = emptyList(),
            outcomes = emptyList(),
            existingInsights = emptyList(),
            zoneId = fixedZone
        )

        // Explainability and epistemic integrity:
        for (insight in insightResult.discoveredInsights) {
            assertNotNull("Insight must contain structured ExplainabilityBlock", insight.explainabilityBlock)
            assertTrue("Explainability whatWasObserved must not be blank", insight.explainabilityBlock.whatWasObserved.isNotBlank())
            assertTrue("Explainability uncertaintyOrLimitations must not be blank", insight.explainabilityBlock.uncertaintyOrLimitations.isNotBlank())
            assertTrue(
                "Personal insights must strictly carry SYSTEM_RECORDED_OBSERVATION or AI_HYPOTHESIS authority",
                insight.epistemicAuthority in setOf(EpistemicAuthority.SYSTEM_RECORDED_OBSERVATION, EpistemicAuthority.AI_HYPOTHESIS)
            )
        }
    }

    @Test
    fun testMissingUsageAccess_suppressesDigitalDiscoveryAndYieldsNullDuration() = runBlocking {
        // Deny usage stats permission
        provider.permissionGranted = false

        // Seed observation in DB (which might exist from a previous grant period)
        val obs = Observation(
            id = "obs-stale",
            userId = userId,
            type = ObservationType.APP_USAGE_SESSION,
            source = ObservationSource.APP_USAGE,
            occurredAt = dayStartMs + 3600_000L,
            recordedAt = middayMs,
            subjectId = null,
            value = "com.social.media",
            context = "duration_ms=3600000",
            confidence = null,
            provenance = ObservationProvenance(ObservationSource.APP_USAGE, "UsageStatsManager", middayMs)
        )
        observationRepository.insertObservation(obs)

        // Layer 1: DailyAppUsageIntelligenceUseCase
        val dailyResult = dailyIntelligenceUseCase(
            userId = userId,
            targetDayMillis = middayMs,
            zoneId = fixedZone,
            customWindowStartMs = dayStartMs,
            customWindowEndMs = dayEndMs
        )
        assertTrue(dailyResult is Result.Success)
        val daily = (dailyResult as Result.Success).data

        assertEquals("Layer 1 must report PERMISSION_DENIED", SourceAvailability.PERMISSION_DENIED, daily.availability)
        assertEquals("Layer 1 permissionStatus must be DENIED", UsagePermissionStatus.DENIED, daily.permissionStatus)
        assertNull("Layer 1 total duration must be strictly NULL when permission is denied, never 0ms", daily.totalObservedDurationMs)
        assertTrue("Layer 1 appUsages must be empty", daily.appUsages.isEmpty())

        // Layer 2: ContextEngineImpl / UnifiedContext
        val ctxResult = contextEngine.getUnifiedContext(userId, dayStartMs, dayEndMs)
        assertTrue(ctxResult is Result.Success)
        val unified = (ctxResult as Result.Success).data
        val digital = unified.digitalBehaviour

        assertEquals("Layer 2 must report PERMISSION_DENIED", SourceAvailability.PERMISSION_DENIED, digital.availability)
        assertEquals("Layer 2 permissionStatus must be DENIED", UsagePermissionStatus.DENIED, digital.permissionStatus)
        assertTrue("Layer 2 appUsages must be empty when permission is denied", digital.appUsages.isEmpty())

        // Layer 3: PersonalInsightDiscoveryEngine suppression
        val insightEngine = PersonalInsightDiscoveryEngine(
            clock = clock,
            idGenerator = fakeIdGenerator
        )
        val insightResult = insightEngine.discoverInsights(
            userId = userId,
            unifiedContext = unified,
            patterns = emptyList(),
            barrierHypotheses = emptyList(),
            recommendations = emptyList(),
            outcomes = emptyList(),
            existingInsights = emptyList(),
            zoneId = fixedZone
        )

        // Digital insights must be completely suppressed
        val digitalInsights = insightResult.discoveredInsights.filter { it.category == InsightCategory.DIGITAL_BEHAVIOUR }
        assertEquals("Digital behaviour insights must be completely suppressed when permission is denied", 0, digitalInsights.size)
        assertTrue("Diagnostic notes must record suppression reason", insightResult.diagnosticNotes.any { it.contains("Usage Access permission not granted") })
    }

    @Test
    fun testMultiUserIsolation_acrossAllLayers() = runBlocking {
        val userAlice = "user_alice"
        val userBob = "user_bob"

        // Seed distinct evidence for Alice (1h Docs) and Bob (2h Game)
        val obsAlice = Observation(
            id = "obs-alice",
            userId = userAlice,
            type = ObservationType.APP_USAGE_SESSION,
            source = ObservationSource.APP_USAGE,
            occurredAt = dayStartMs + 3600_000L,
            recordedAt = middayMs,
            subjectId = null,
            value = "com.alice.docs",
            context = "duration_ms=3600000",
            confidence = null,
            provenance = ObservationProvenance(ObservationSource.APP_USAGE, "UsageStatsManager", middayMs)
        )
        val obsBob = Observation(
            id = "obs-bob",
            userId = userBob,
            type = ObservationType.APP_USAGE_SESSION,
            source = ObservationSource.APP_USAGE,
            occurredAt = dayStartMs + 3600_000L,
            recordedAt = middayMs,
            subjectId = null,
            value = "com.bob.game",
            context = "duration_ms=7200000",
            confidence = null,
            provenance = ObservationProvenance(ObservationSource.APP_USAGE, "UsageStatsManager", middayMs)
        )
        observationRepository.insertObservation(obsAlice)
        observationRepository.insertObservation(obsBob)

        checkpointStore.saveCheckpoint(AppUsageCheckpoint(userAlice, dayEndMs, null, null, dayEndMs))
        checkpointStore.saveCheckpoint(AppUsageCheckpoint(userBob, dayEndMs, null, null, dayEndMs))

        // Layer 1: DailyAppUsageIntelligenceUseCase isolation
        val aliceDaily = (dailyIntelligenceUseCase(userAlice, middayMs, fixedZone, dayStartMs, dayEndMs) as Result.Success).data
        val bobDaily = (dailyIntelligenceUseCase(userBob, middayMs, fixedZone, dayStartMs, dayEndMs) as Result.Success).data

        assertEquals(1, aliceDaily.mostUsedApps.size)
        assertEquals("com.alice.docs", aliceDaily.mostUsedApps[0].packageName)
        assertEquals(3600_000L, aliceDaily.totalObservedDurationMs)

        assertEquals(1, bobDaily.mostUsedApps.size)
        assertEquals("com.bob.game", bobDaily.mostUsedApps[0].packageName)
        assertEquals(7200_000L, bobDaily.totalObservedDurationMs)

        // Layer 2: ContextEngineImpl UnifiedContext isolation
        val aliceCtx = (contextEngine.getUnifiedContext(userAlice, dayStartMs, dayEndMs) as Result.Success).data
        val bobCtx = (contextEngine.getUnifiedContext(userBob, dayStartMs, dayEndMs) as Result.Success).data

        assertEquals("user_alice", aliceCtx.userId)
        assertEquals(1, aliceCtx.digitalBehaviour.appUsages.size)
        assertEquals("com.alice.docs", aliceCtx.digitalBehaviour.appUsages[0].packageName)

        assertEquals("user_bob", bobCtx.userId)
        assertEquals(1, bobCtx.digitalBehaviour.appUsages.size)
        assertEquals("com.bob.game", bobCtx.digitalBehaviour.appUsages[0].packageName)

        // Layer 3: DigitalBehaviourViewModel isolation
        val aliceSessionProvider = FakeUserSessionProvider(SessionState.Authenticated(userAlice, false))
        val aliceViewModel = DigitalBehaviourViewModel(
            getDailyAppUsageIntelligenceUseCase = dailyIntelligenceUseCase,
            appUsageObservationProvider = provider,
            appUsageSettingsRepository = settingsRepository,
            protectionRepository = FakeProtectionRepository(),
            userSessionProvider = aliceSessionProvider,
            appNameResolver = { it },
            clock = clock,
            zoneId = fixedZone,
            executeObservationCollectionUseCase = null,
            ioDispatcher = Dispatchers.Unconfined
        )
        aliceViewModel.refresh()
        val aliceUiState = aliceViewModel.uiState.value
        assertEquals(1, aliceUiState.dailyUsage?.appUsages?.size)
        assertEquals("com.alice.docs", aliceUiState.dailyUsage?.appUsages?.get(0)?.packageName)

        // Layer 4: BehaviourPatternDiscoveryEngine isolation
        val patternEngine = BehaviourPatternDiscoveryEngine(EvidenceSufficiencyEvaluator(), clock)
        val alicePatterns = patternEngine.discoverPatterns(aliceCtx, emptyList(), fixedZone)
        val bobPatterns = patternEngine.discoverPatterns(bobCtx, emptyList(), fixedZone)
        assertTrue(alicePatterns.all { it.userId == userAlice })
        assertTrue(bobPatterns.all { it.userId == userBob })

        // Layer 5: BarrierHypothesisDiscoveryEngine isolation
        val barrierEngine = BarrierHypothesisDiscoveryEngine(EvidenceSufficiencyEvaluator(), fakeIdGenerator)
        val aliceBarriers = barrierEngine.discoverBarrierHypotheses(userAlice, aliceCtx, emptyList(), emptyList(), fixedZone, middayMs)
        val bobBarriers = barrierEngine.discoverBarrierHypotheses(userBob, bobCtx, emptyList(), emptyList(), fixedZone, middayMs)
        assertTrue(aliceBarriers.discoveredHypotheses.all { it.userId == userAlice })
        assertTrue(bobBarriers.discoveredHypotheses.all { it.userId == userBob })

        // Layer 6: PersonalInsightDiscoveryEngine isolation
        val insightEngine = PersonalInsightDiscoveryEngine(clock, fakeIdGenerator)
        val aliceInsights = insightEngine.discoverInsights(userAlice, aliceCtx, emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), fixedZone)
        val bobInsights = insightEngine.discoverInsights(userBob, bobCtx, emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), fixedZone)
        assertTrue(aliceInsights.discoveredInsights.all { it.userId == userAlice })
        assertTrue(bobInsights.discoveredInsights.all { it.userId == userBob })
    }

    // --- Test Doubles ---

    private val fakeIdGenerator = object : IdGenerator {
        private var counter = 0
        override fun generateId(): String = "gen-id-${++counter}"
    }

    class FakeCrossLayerSettingsRepo : AppUsageObservationSettingsRepository {
        override suspend fun getSettings(userId: String) = Result.Success(AppUsageObservationSettings(userId, isEnabled = true))
        override suspend fun setEnabled(userId: String, isEnabled: Boolean) = Result.Success(Unit)
    }

    class FakeCrossLayerProvider : AppUsageObservationProvider {
        var permissionGranted: Boolean = true
        override fun isPermissionGranted() = permissionGranted
        override fun getAppUsageSince(fromTimeMillis: Long) = emptyList<AppUsageEvent>()
        override fun getAppUsageBetween(fromTimeMillis: Long, toTimeMillis: Long, initialActiveSession: ActiveForegroundSession?) =
            AppUsageReconstructionResult(emptyList(), null)
    }

    class FakeCrossLayerCheckpointStore : AppUsageCheckpointStore {
        val checkpoints = mutableMapOf<String, AppUsageCheckpoint>()
        override suspend fun getCheckpoint(userId: String) = Result.Success(checkpoints[userId])
        override suspend fun saveCheckpoint(checkpoint: AppUsageCheckpoint): Result<Unit, Exception> {
            checkpoints[checkpoint.userId] = checkpoint
            return Result.Success(Unit)
        }
        override suspend fun saveObservationsWithCheckpoint(observations: List<Observation>, checkpoint: AppUsageCheckpoint): Result<Unit, Exception> {
            checkpoints[checkpoint.userId] = checkpoint
            return Result.Success(Unit)
        }
        override suspend fun clearCheckpointForUser(userId: String): Result<Unit, Exception> {
            checkpoints.remove(userId)
            return Result.Success(Unit)
        }
    }

    class FakeCommitmentRepository : CommitmentRepository {
        override suspend fun getCommitment(id: String) = Result.Success(null)
        override suspend fun getCommitmentsForUser(userId: String) = Result.Success(emptyList<Commitment>())
        override suspend fun getActiveCommitmentsForUser(userId: String, statuses: List<CommitmentStatus>) = Result.Success(emptyList<Commitment>())
        override suspend fun getActiveCommitmentsForUser(userId: String, statuses: List<CommitmentStatus>, limit: Int) = Result.Success(emptyList<Commitment>())
        override suspend fun getCommitmentsForDateRange(userId: String, startTime: Long, endTime: Long) = Result.Success(emptyList<Commitment>())
        override suspend fun getCommitmentsForGoal(goalId: String) = Result.Success(emptyList<Commitment>())
        override suspend fun getCommitmentsForPlan(planId: String) = Result.Success(emptyList<Commitment>())
        override suspend fun getCommitmentsForTask(taskId: String) = Result.Success(emptyList<Commitment>())
        override suspend fun saveCommitment(commitment: Commitment) = Result.Success(Unit)
        override suspend fun searchCommitments(userId: String, query: String) = Result.Success(emptyList<Commitment>())
    }

    class FakeEventRepository : EventRepository {
        override suspend fun getEventsForUser(userId: String) = Result.Success(emptyList<Event>())
        override suspend fun getEventsForEntity(entityId: String) = Result.Success(emptyList<Event>())
        override suspend fun getEvent(id: String) = Result.Success(null)
        override suspend fun getEventForUser(userId: String, id: String) = Result.Success(null)
        override suspend fun getEventsForDateRange(userId: String, startTime: Long, endTime: Long) = Result.Success(emptyList<Event>())
        override suspend fun saveEvent(event: Event) = Result.Success(event)
        override suspend fun searchEvents(userId: String, query: String) = Result.Success(emptyList<Event>())
        override suspend fun getEventsForTimeWindow(userId: String, startTime: Long, endTime: Long, types: List<EventType>?, limit: Int, orderAsc: Boolean) = Result.Success(emptyList<Event>())
        override suspend fun getEventsByCausationId(userId: String, causationId: String) = Result.Success(emptyList<Event>())
    }

    class FakeReflectionRepository : ReflectionRepository {
        override suspend fun getReflectionsForDateRange(userId: String, startTime: Long, endTime: Long) = Result.Success(emptyList<Reflection>())
        override suspend fun getReflection(id: String) = Result.Success(null)
        override suspend fun getReflectionForUser(userId: String, id: String) = Result.Success(null)
        override suspend fun saveReflection(reflection: Reflection) = Result.Success(Unit)
        override suspend fun searchReflections(userId: String, query: String) = Result.Success(emptyList<Reflection>())
        override suspend fun getReflectionsForTimeWindow(userId: String, startTime: Long, endTime: Long, limit: Int, orderAsc: Boolean) = Result.Success(emptyList<Reflection>())
    }

    class FakeProtectionRepository : ProtectionRepository {
        override suspend fun getProtectionRule(id: String) = Result.Failure(Exception("Not found"))
        override suspend fun getProtectionRulesForUser(userId: String) = Result.Success(emptyList<ProtectionRule>())
        override suspend fun saveProtectionRule(rule: ProtectionRule) = Result.Success(Unit)
        override suspend fun getProtectionSession(id: String) = Result.Failure(Exception("Not found"))
        override suspend fun getActiveProtectionSessionsForUser(userId: String) = Result.Success(emptyList<ProtectionSession>())
        override suspend fun getProtectionSessionsForUser(userId: String) = Result.Success(emptyList<ProtectionSession>())
        override suspend fun saveProtectionSession(session: ProtectionSession) = Result.Success(Unit)
    }

    class FakeGoalRepository : GoalRepository {
        override suspend fun getGoalsForUser(userId: String) = Result.Success(emptyList<Goal>())
        override suspend fun getGoal(id: String) = Result.Success(null)
        override suspend fun saveGoal(goal: Goal) = Result.Success(Unit)
        override suspend fun searchGoals(userId: String, query: String) = Result.Success(emptyList<Goal>())
        override suspend fun getActiveGoalsForUser(userId: String, limit: Int) = Result.Success(emptyList<Goal>())
    }
}
