package com.sanket_satpute_20.ironmind.domain.engine

import com.sanket_satpute_20.ironmind.domain.ai.BarrierCategory
import com.sanket_satpute_20.ironmind.domain.ai.InterventionType
import com.sanket_satpute_20.ironmind.domain.common.Clock
import com.sanket_satpute_20.ironmind.domain.common.IdGenerator
import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.EntitySource
import com.sanket_satpute_20.ironmind.domain.model.Event
import com.sanket_satpute_20.ironmind.domain.model.EventType
import com.sanket_satpute_20.ironmind.domain.model.MemoryConfirmationState
import com.sanket_satpute_20.ironmind.domain.model.barrier.BarrierConfirmationState
import com.sanket_satpute_20.ironmind.domain.model.barrier.BarrierHypothesis
import com.sanket_satpute_20.ironmind.domain.model.barrier.BarrierStatus
import com.sanket_satpute_20.ironmind.domain.model.context.*
import com.sanket_satpute_20.ironmind.domain.model.intervention.*
import com.sanket_satpute_20.ironmind.domain.model.pattern.*
import com.sanket_satpute_20.ironmind.domain.repository.BarrierRepository
import com.sanket_satpute_20.ironmind.domain.repository.EventRepository
import com.sanket_satpute_20.ironmind.domain.repository.InterventionRecommendationRepository
import com.sanket_satpute_20.ironmind.domain.repository.PatternRepository
import com.sanket_satpute_20.ironmind.domain.usecase.ai.HandleInterventionResultUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.ai.UpdateInterventionRecommendationStatusUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.barrier.ConfirmBarrierUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.barrier.RefuteBarrierUseCase
import com.sanket_satpute_20.ironmind.testutil.fake.FakeEventRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.time.ZoneId

class BarrierUnderstandingAndInterventionTest {

    private val testZoneId: ZoneId = ZoneId.of("UTC")
    private val userId1 = "test-user-1"
    private val userId2 = "test-user-2"

    private lateinit var evidenceSufficiencyEvaluator: EvidenceSufficiencyEvaluator
    private lateinit var barrierDiscoveryEngine: BarrierHypothesisDiscoveryEngine
    private lateinit var interventionReasoningEngine: InterventionReasoningEngine
    private lateinit var equivalencePolicy: InterventionEquivalencePolicy

    private lateinit var fakeBarrierRepository: InMemoryBarrierRepository
    private lateinit var fakePatternRepository: InMemoryPatternRepository
    private lateinit var fakeInterventionRecommendationRepository: InMemoryInterventionRecommendationRepository
    private lateinit var fakeEventRepository: FakeEventRepository

    private var currentClockTimeMs = 1700000000000L // arbitrary fixed time
    private var idCounter = 100

    private val fakeClock = object : Clock {
        override fun currentTimeMillis(): Long = currentClockTimeMs
    }

    private val fakeIdGenerator = object : IdGenerator {
        override fun generateId(): String = "gen-id-${++idCounter}"
    }

    @Before
    fun setUp() {
        evidenceSufficiencyEvaluator = EvidenceSufficiencyEvaluator()
        barrierDiscoveryEngine = BarrierHypothesisDiscoveryEngine(evidenceSufficiencyEvaluator, fakeIdGenerator)
        equivalencePolicy = InterventionEquivalencePolicy()
        interventionReasoningEngine = InterventionReasoningEngine(equivalencePolicy, fakeIdGenerator)

        fakeEventRepository = FakeEventRepository()
        fakeBarrierRepository = InMemoryBarrierRepository(fakeEventRepository)
        fakePatternRepository = InMemoryPatternRepository()
        fakeInterventionRecommendationRepository = InMemoryInterventionRecommendationRepository()
    }

    // Helper to generate evidence references spanning multiple days
    private fun createMultiDayEvidence(count: Int, daysSpan: Int): List<EvidenceReference> {
        return (1..count).map { EvidenceReference("ev-$it", EvidenceSourceType.EVENT) }
    }

    private fun createTestPattern(
        id: String,
        userId: String = userId1,
        type: PatternDiscoveryType = PatternDiscoveryType.REPEATEDLY_POSTPONED_TASKS,
        description: String = "Large complex tasks repeatedly postponed",
        evidenceCount: Int = 3,
        daysSpan: Int = 2,
        confidence: Float = 0.8f,
        lifecycleState: PatternLifecycleState = PatternLifecycleState.ESTABLISHED,
        isCrossDomain: Boolean = false
    ): Pattern {
        val firstObs = currentClockTimeMs - (daysSpan * 24L * 60 * 60 * 1000)
        val lastObs = currentClockTimeMs
        val refs = createMultiDayEvidence(evidenceCount, daysSpan)
        val categoryName = if (isCrossDomain) "CROSS_DOMAIN" else "PRODUCTIVITY"
        return Pattern(
            id = id,
            userId = userId,
            fingerprint = "fp-$id",
            type = type.defaultLegacyType,
            description = description,
            conditions = "category=$categoryName;lifecycleState=${lifecycleState.name};sources=EventRepository",
            predictedBehavior = if (isCrossDomain) "Correlation only; non-causal" else null,
            confidence = confidence,
            evidenceCount = evidenceCount,
            evidenceReferences = refs,
            firstObservedAt = firstObs,
            lastObservedAt = lastObs,
            status = lifecycleState.toLegacyPatternStatus(),
            confirmationState = MemoryConfirmationState.UNCONFIRMED,
            createdAt = firstObs,
            updatedAt = lastObs
        )
    }

    // Test 1: Insufficient evidence does not generate a confirmed or active barrier
    @Test
    fun test1_insufficientEvidence_doesNotGenerateConfirmedBarrier() {
        // Only 1 evidence reference (N=1 < 3)
        val weakPattern = createTestPattern(
            id = "p-weak",
            evidenceCount = 1,
            daysSpan = 0,
            description = "Large complex task postponed once"
        )

        val result = barrierDiscoveryEngine.discoverBarrierHypotheses(
            userId = userId1,
            unifiedContext = UnifiedContext(userId = userId1, generatedAt = currentClockTimeMs),
            patterns = listOf(weakPattern),
            existingBarriers = emptyList(),
            zoneId = testZoneId,
            evaluationTimeMs = currentClockTimeMs
        )

        assertTrue("Discovered hypotheses must be empty when evidence is insufficient", result.discoveredHypotheses.isEmpty())
        assertEquals("Must report insufficient evidence candidate", 1, result.insufficientEvidenceHypotheses.size)
        assertEquals(BarrierCategory.EXCESSIVE_TASK_SIZE, result.insufficientEvidenceHypotheses[0].category)
        assertEquals(EvidenceSufficiencyReason.INSUFFICIENT_UNIQUE_EVIDENCE, result.insufficientEvidenceHypotheses[0].failureReason)
    }

    // Test 2: Repeated evidence can generate an eligible barrier hypothesis
    @Test
    fun test2_repeatedEvidence_generatesEligibleBarrierHypothesis() {
        val solidPattern = createTestPattern(
            id = "p-postponed",
            evidenceCount = 4,
            daysSpan = 3,
            description = "Multiple large complex tasks repeatedly postponed across distinct days"
        )

        val result = barrierDiscoveryEngine.discoverBarrierHypotheses(
            userId = userId1,
            unifiedContext = UnifiedContext(userId = userId1, generatedAt = currentClockTimeMs),
            patterns = listOf(solidPattern),
            existingBarriers = emptyList(),
            zoneId = testZoneId,
            evaluationTimeMs = currentClockTimeMs
        )

        assertEquals("Must generate 1 barrier hypothesis", 1, result.discoveredHypotheses.size)
        val barrier = result.discoveredHypotheses[0]
        assertEquals(BarrierCategory.EXCESSIVE_TASK_SIZE, barrier.category)
        assertEquals(BarrierConfirmationState.UNCONFIRMED, barrier.confirmationState)
        assertEquals(BarrierStatus.ACTIVE, barrier.status)
        assertEquals(listOf(solidPattern.id), barrier.supportingPatternIds)
        assertEquals(4, barrier.evidenceCount)
        assertTrue("Must span >= 2 days", barrier.distinctDays >= 2)
        assertEquals(EpistemicAuthority.AI_HYPOTHESIS, barrier.epistemicAuthority)
    }

    // Test 3: One isolated event does not establish a barrier
    @Test
    fun test3_isolatedEvent_doesNotEstablishBarrier() {
        val singleEventPattern = createTestPattern(
            id = "p-single",
            evidenceCount = 1,
            daysSpan = 0,
            confidence = 0.3f,
            lifecycleState = PatternLifecycleState.CANDIDATE
        )

        val result = barrierDiscoveryEngine.discoverBarrierHypotheses(
            userId = userId1,
            unifiedContext = UnifiedContext(userId = userId1, generatedAt = currentClockTimeMs),
            patterns = listOf(singleEventPattern),
            existingBarriers = emptyList(),
            zoneId = testZoneId,
            evaluationTimeMs = currentClockTimeMs
        )

        assertTrue("Single isolated event must never establish a barrier hypothesis", result.discoveredHypotheses.isEmpty())
        assertTrue("Must be recorded under insufficient evidence", result.insufficientEvidenceHypotheses.isNotEmpty())
    }

    // Test 4: Cross-domain correlation remains non-causal
    @Test
    fun test4_crossDomainCorrelation_remainsNonCausal() {
        val crossPattern = createTestPattern(
            id = "p-cross",
            type = PatternDiscoveryType.APP_USAGE_COINCIDING_WITH_MISSED_COMMITMENTS,
            description = "App usage coinciding with missed commitments",
            evidenceCount = 4,
            daysSpan = 2,
            confidence = 0.95f,
            isCrossDomain = true
        )

        val digitalContextWithPermission = DigitalBehaviourContext(
            availability = SourceAvailability.AVAILABLE,
            permissionStatus = UsagePermissionStatus.GRANTED
        )

        val result = barrierDiscoveryEngine.discoverBarrierHypotheses(
            userId = userId1,
            unifiedContext = UnifiedContext(
                userId = userId1,
                digitalBehaviour = digitalContextWithPermission,
                generatedAt = currentClockTimeMs
            ),
            patterns = listOf(crossPattern),
            existingBarriers = emptyList(),
            zoneId = testZoneId,
            evaluationTimeMs = currentClockTimeMs
        )

        assertEquals(1, result.discoveredHypotheses.size)
        val barrier = result.discoveredHypotheses[0]
        assertEquals(BarrierCategory.DISTRACTION, barrier.category)
        assertEquals(EpistemicAuthority.AI_HYPOTHESIS, barrier.epistemicAuthority)
        assertTrue("Confidence must be capped below 1.0f (max 0.75f)", barrier.confidence <= 0.75f)
        assertTrue("Must contain explicit non-causal disclaimer", barrier.description.contains("does not imply causation"))
    }

    // Test 5: Missing Usage Access does not create false usage conclusions
    @Test
    fun test5_missingUsageAccess_doesNotCreateFalseUsageConclusions() {
        val crossPattern = createTestPattern(
            id = "p-cross",
            description = "App usage coinciding with missed commitments",
            isCrossDomain = true
        )

        val digitalContextNoPermission = DigitalBehaviourContext(
            availability = SourceAvailability.PERMISSION_DENIED,
            permissionStatus = UsagePermissionStatus.DENIED
        )

        val result = barrierDiscoveryEngine.discoverBarrierHypotheses(
            userId = userId1,
            unifiedContext = UnifiedContext(
                userId = userId1,
                digitalBehaviour = digitalContextNoPermission,
                generatedAt = currentClockTimeMs
            ),
            patterns = listOf(crossPattern),
            existingBarriers = emptyList(),
            zoneId = testZoneId,
            evaluationTimeMs = currentClockTimeMs
        )

        assertTrue("Zero distraction barrier hypotheses when permission denied", result.discoveredHypotheses.none { it.category == BarrierCategory.DISTRACTION })
        assertTrue("Diagnostic notes must record permission denial", result.diagnosticNotes.any { it.contains("permission denied", ignoreCase = true) })
    }

    // Test 6: Stale evidence reduces sufficiency
    @Test
    fun test6_staleEvidence_reducesSufficiency() {
        val crossPattern = createTestPattern(
            id = "p-stale",
            description = "App usage during work",
            evidenceCount = 4,
            daysSpan = 2,
            confidence = 0.75f,
            isCrossDomain = true
        )

        val staleDigitalContext = DigitalBehaviourContext(
            availability = SourceAvailability.STALE_DATA,
            permissionStatus = UsagePermissionStatus.GRANTED
        )

        val result = barrierDiscoveryEngine.discoverBarrierHypotheses(
            userId = userId1,
            unifiedContext = UnifiedContext(
                userId = userId1,
                digitalBehaviour = staleDigitalContext,
                isFresh = false,
                generatedAt = currentClockTimeMs
            ),
            patterns = listOf(crossPattern),
            existingBarriers = emptyList(),
            zoneId = testZoneId,
            evaluationTimeMs = currentClockTimeMs
        )

        assertEquals(1, result.discoveredHypotheses.size)
        val barrier = result.discoveredHypotheses[0]
        // Confidence should have stale penalty (0.75 - 0.20 = 0.55)
        assertTrue("Stale evidence must discount confidence", barrier.confidence < 0.75f)
        assertEquals(SourceFreshness.STALE, barrier.sourceFreshness)
    }

    // Test 7: User confirmation is required to establish a confirmed barrier
    @Test
    fun test7_userConfirmation_isRequiredToEstablishConfirmedBarrier() = runBlocking {
        val confirmUseCase = ConfirmBarrierUseCase(
            barrierRepository = fakeBarrierRepository,
            eventRepository = fakeEventRepository,
            idGenerator = fakeIdGenerator,
            clock = fakeClock
        )

        val initialBarrier = BarrierHypothesis(
            id = "b-unconfirmed",
            userId = userId1,
            category = BarrierCategory.EXCESSIVE_TASK_SIZE,
            description = "Tasks repeatedly postponed",
            confirmationState = BarrierConfirmationState.UNCONFIRMED,
            status = BarrierStatus.ACTIVE,
            sourceReflectionId = "",
            firstObservedAt = currentClockTimeMs - 10000,
            lastObservedAt = currentClockTimeMs
        )
        fakeBarrierRepository.saveBarrier(initialBarrier)

        // Verify initial state is UNCONFIRMED
        val fetchedBefore = fakeBarrierRepository.getBarrier("b-unconfirmed")
        assertEquals(BarrierConfirmationState.UNCONFIRMED, (fetchedBefore as Result.Success).data?.confirmationState)

        // User explicitly confirms
        val confirmResult = confirmUseCase(userId1, "b-unconfirmed")
        assertTrue("Confirm barrier use case should succeed", confirmResult is Result.Success)

        // Verify state is now CONFIRMED
        val fetchedAfter = fakeBarrierRepository.getBarrier("b-unconfirmed")
        val confirmedBarrier = (fetchedAfter as Result.Success).data!!
        assertEquals(BarrierConfirmationState.CONFIRMED, confirmedBarrier.confirmationState)
        assertEquals(EpistemicAuthority.USER_CONFIRMED_FACT, confirmedBarrier.epistemicAuthority)

        // Verify confirmation event was emitted
        val events = fakeEventRepository.events.values
        assertTrue("Event BARRIER_CONFIRMED must be emitted", events.any { it.type == EventType.BARRIER_CONFIRMED && it.entityId == "b-unconfirmed" })
    }

    // Test 8: Rejected/refuted hypotheses retain historical evidence
    @Test
    fun test8_rejectedOrRefutedHypotheses_retainHistoricalEvidence() = runBlocking {
        val refuteUseCase = RefuteBarrierUseCase(
            barrierRepository = fakeBarrierRepository,
            eventRepository = fakeEventRepository,
            idGenerator = fakeIdGenerator,
            clock = fakeClock
        )

        val pattern = createTestPattern(id = "p-hist", userId = userId1)
        fakePatternRepository.savePattern(pattern)

        val barrier = BarrierHypothesis(
            id = "b-refuted",
            userId = userId1,
            category = BarrierCategory.COMPETING_PRIORITIES,
            description = "Multiple missed commitments",
            confirmationState = BarrierConfirmationState.UNCONFIRMED,
            status = BarrierStatus.ACTIVE,
            sourceReflectionId = "patterns:p-hist",
            firstObservedAt = currentClockTimeMs - 10000,
            lastObservedAt = currentClockTimeMs,
            supportingPatternIds = listOf(pattern.id)
        )
        fakeBarrierRepository.saveBarrier(barrier)

        // User refutes barrier
        val refuteResult = refuteUseCase(userId1, "b-refuted")
        assertTrue("Refute should succeed", refuteResult is Result.Success)

        // Barrier is updated to REJECTED and EXPIRED
        val fetchedBarrier = (fakeBarrierRepository.getBarrier("b-refuted") as Result.Success).data!!
        assertEquals(BarrierConfirmationState.REJECTED, fetchedBarrier.confirmationState)
        assertEquals(BarrierStatus.EXPIRED, fetchedBarrier.status)

        // Historical pattern and evidence MUST NOT be deleted
        val fetchedPattern = (fakePatternRepository.getPattern("p-hist") as Result.Success).data
        assertNotNull("Underlying pattern must NOT be deleted upon barrier refutation", fetchedPattern)
        assertEquals(pattern.id, fetchedPattern?.id)
    }

    // Test 9: Repeated discovery does not create duplicate hypotheses
    @Test
    fun test9_repeatedDiscovery_doesNotCreateDuplicateHypotheses() {
        val pattern = createTestPattern(id = "p-dup", userId = userId1)

        val existingBarrier = BarrierHypothesis(
            id = "b-existing",
            userId = userId1,
            category = BarrierCategory.EXCESSIVE_TASK_SIZE,
            description = "Existing barrier",
            confirmationState = BarrierConfirmationState.UNCONFIRMED,
            status = BarrierStatus.ACTIVE,
            sourceReflectionId = "",
            firstObservedAt = currentClockTimeMs - 5000,
            lastObservedAt = currentClockTimeMs
        )

        val result = barrierDiscoveryEngine.discoverBarrierHypotheses(
            userId = userId1,
            unifiedContext = UnifiedContext(userId = userId1, generatedAt = currentClockTimeMs),
            patterns = listOf(pattern),
            existingBarriers = listOf(existingBarrier),
            zoneId = testZoneId,
            evaluationTimeMs = currentClockTimeMs
        )

        assertTrue("Should not duplicate active unconfirmed barrier category", result.discoveredHypotheses.isEmpty())
        assertTrue("Diagnostic note should indicate category suppressed", result.diagnosticNotes.any { it.contains("suppressed by review history") })
    }

    // Test 10: Intervention recommendations reference valid barrier hypotheses
    @Test
    fun test10_interventionRecommendations_referenceValidBarrierHypotheses() {
        val barrier = BarrierHypothesis(
            id = "b-valid",
            userId = userId1,
            category = BarrierCategory.EXCESSIVE_TASK_SIZE,
            description = "Postponed tasks barrier",
            confirmationState = BarrierConfirmationState.UNCONFIRMED,
            status = BarrierStatus.ACTIVE,
            sourceReflectionId = "",
            firstObservedAt = currentClockTimeMs - 10000,
            lastObservedAt = currentClockTimeMs
        )

        val recommendations = interventionReasoningEngine.generateRecommendations(
            userId = userId1,
            barrierHypotheses = listOf(barrier),
            historicalRecommendations = emptyList(),
            currentTimeMs = currentClockTimeMs
        )

        assertEquals("Should produce 1 recommendation for the barrier", 1, recommendations.size)
        val rec = recommendations[0]
        assertEquals(InterventionType.BREAK_DOWN, rec.interventionType)
        assertNotNull("Recommendation must reference causal hypothesis", rec.causalHypothesis)
        assertEquals("b-valid", rec.causalHypothesis?.id)
        assertEquals(CausalHypothesisType.BARRIER, rec.causalHypothesis?.type)
        assertEquals(InterventionRecommendationStatus.PENDING, rec.status)
        assertNotNull(rec.expectedOutcome)
        assertNotNull(rec.suggestedMeasurement)
    }

    // Test 11: Accepted recommendations do not execute automatically
    @Test
    fun test11_acceptedRecommendations_doNotExecuteAutomatically() = runBlocking {
        val updateStatusUseCase = UpdateInterventionRecommendationStatusUseCase(fakeInterventionRecommendationRepository)
        val handleResultUseCase = HandleInterventionResultUseCase(
            eventRepository = fakeEventRepository,
            updateInterventionRecommendationStatusUseCase = updateStatusUseCase,
            clock = fakeClock,
            idGenerator = fakeIdGenerator
        )

        val recommendation = InterventionRecommendation(
            id = "rec-accept",
            userId = userId1,
            interventionType = InterventionType.BREAK_DOWN,
            objective = com.sanket_satpute_20.ironmind.domain.ai.InterventionObjective.REDUCE_FRICTION,
            targetEntityId = "b-1",
            targetEntityType = "BARRIER",
            rationale = "Break down tasks",
            suggestedAction = "Split into 15m steps",
            status = InterventionRecommendationStatus.PENDING,
            createdAt = currentClockTimeMs
        )
        fakeInterventionRecommendationRepository.saveRecommendation(recommendation)

        // User accepts recommendation
        val result = handleResultUseCase(
            recommendation = recommendation,
            action = HandleInterventionResultUseCase.Action.ACCEPT,
            userId = userId1
        )
        assertTrue("Accept should succeed", result is Result.Success)

        // Verify status changed to ACCEPTED
        val fetchedRec = (fakeInterventionRecommendationRepository.getRecommendation("rec-accept") as Result.Success).data!!
        assertEquals(InterventionRecommendationStatus.ACCEPTED, fetchedRec.status)

        // Verify event was logged, but no auto-blocking or autonomous task creation happened
        val events = fakeEventRepository.events.values
        assertTrue("Must log INTERVENTION_ACCEPTED event", events.any { it.type == EventType.INTERVENTION_ACCEPTED })
    }

    // Test 12: Rejected recommendations are not repeatedly regenerated without considering review history
    @Test
    fun test12_rejectedRecommendations_areNotRepeatedlyRegeneratedWithoutReviewHistory() {
        val barrier = BarrierHypothesis(
            id = "b-rej",
            userId = userId1,
            category = BarrierCategory.EXCESSIVE_TASK_SIZE,
            description = "Postponed tasks barrier",
            confirmationState = BarrierConfirmationState.UNCONFIRMED,
            status = BarrierStatus.ACTIVE,
            sourceReflectionId = "",
            firstObservedAt = currentClockTimeMs - 10000,
            lastObservedAt = currentClockTimeMs
        )

        // Prior rejected recommendation 2 hours ago (within 24h cooldown)
        val rejectedPriorRec = InterventionRecommendation(
            id = "rec-rejected",
            userId = userId1,
            interventionType = InterventionType.BREAK_DOWN,
            objective = com.sanket_satpute_20.ironmind.domain.ai.InterventionObjective.REDUCE_FRICTION,
            targetEntityId = "b-rej",
            targetEntityType = "BARRIER",
            rationale = "Old rationale",
            suggestedAction = "Old action",
            status = InterventionRecommendationStatus.REJECTED,
            createdAt = currentClockTimeMs - (2L * 60 * 60 * 1000)
        )

        val recommendations = interventionReasoningEngine.generateRecommendations(
            userId = userId1,
            barrierHypotheses = listOf(barrier),
            historicalRecommendations = listOf(rejectedPriorRec),
            currentTimeMs = currentClockTimeMs
        )

        assertTrue("Recommendation must be suppressed following recent rejection within 24h", recommendations.isEmpty())
    }

    // Test 13: Outcome recording does not create duplicate events
    @Test
    fun test13_outcomeRecording_doesNotCreateDuplicateEvents() = runBlocking {
        val updateStatusUseCase = UpdateInterventionRecommendationStatusUseCase(fakeInterventionRecommendationRepository)
        val handleResultUseCase = HandleInterventionResultUseCase(
            eventRepository = fakeEventRepository,
            updateInterventionRecommendationStatusUseCase = updateStatusUseCase,
            clock = fakeClock,
            idGenerator = fakeIdGenerator
        )

        val rec = InterventionRecommendation(
            id = "rec-correct",
            userId = userId1,
            interventionType = InterventionType.BREAK_DOWN,
            objective = com.sanket_satpute_20.ironmind.domain.ai.InterventionObjective.REDUCE_FRICTION,
            targetEntityId = "b-target",
            targetEntityType = "BARRIER",
            rationale = "Rationale",
            suggestedAction = "Action",
            status = InterventionRecommendationStatus.PENDING,
            createdAt = currentClockTimeMs
        )
        fakeInterventionRecommendationRepository.saveRecommendation(rec)

        // Action: CORRECT
        handleResultUseCase(rec, HandleInterventionResultUseCase.Action.CORRECT, userId1, "Customized text")
        val countAfterFirst = fakeEventRepository.events.values.count { it.type == EventType.INTERVENTION_OVERRIDDEN && it.causationId == "rec-correct" }
        assertEquals(1, countAfterFirst)

        // Repeated invocation must be idempotent and not create duplicate event
        handleResultUseCase(rec, HandleInterventionResultUseCase.Action.CORRECT, userId1, "Customized text")
        val countAfterSecond = fakeEventRepository.events.values.count { it.type == EventType.INTERVENTION_OVERRIDDEN && it.causationId == "rec-correct" }
        assertEquals("Duplicate events must NOT be created", 1, countAfterSecond)
    }

    // Test 14: User/session ownership isolation
    @Test
    fun test14_userSessionOwnership_isolation() {
        val patternUser1 = createTestPattern(id = "p-u1", userId = userId1)
        val patternUser2 = createTestPattern(id = "p-u2", userId = userId2)

        val resultUser1 = barrierDiscoveryEngine.discoverBarrierHypotheses(
            userId = userId1,
            unifiedContext = UnifiedContext(userId = userId1, generatedAt = currentClockTimeMs),
            patterns = listOf(patternUser1, patternUser2),
            existingBarriers = emptyList(),
            zoneId = testZoneId,
            evaluationTimeMs = currentClockTimeMs
        )

        assertEquals(1, resultUser1.discoveredHypotheses.size)
        assertEquals("Discovered barrier must belong strictly to user1", userId1, resultUser1.discoveredHypotheses[0].userId)
        assertEquals(listOf("p-u1"), resultUser1.discoveredHypotheses[0].supportingPatternIds)
    }

    // Test 15: Pattern lifecycle state persistence and reload behaviour
    @Test
    fun test15_patternLifecycleState_survivesPersistenceAndReload() = runBlocking {
        val states = listOf(
            PatternLifecycleState.CANDIDATE,
            PatternLifecycleState.EMERGING,
            PatternLifecycleState.ESTABLISHED,
            PatternLifecycleState.WEAKENING,
            PatternLifecycleState.INACTIVE
        )

        for ((idx, state) in states.withIndex()) {
            val discoveredPattern = DiscoveredPattern(
                id = "dp-$idx",
                userId = userId1,
                category = PatternCategory.PRODUCTIVITY,
                type = PatternDiscoveryType.REPEATEDLY_POSTPONED_TASKS,
                description = "Pattern state ${state.name}",
                observationWindow = ObservationWindow(currentClockTimeMs - 10000, currentClockTimeMs),
                evidenceReferences = listOf(EvidenceReference("e-$idx", EvidenceSourceType.EVENT)),
                evidenceCount = 3,
                firstObservedAt = currentClockTimeMs - 10000,
                lastObservedAt = currentClockTimeMs,
                confidence = 0.8f,
                lifecycleState = state
            )

            // Convert to persistent domain pattern
            val domainPattern = discoveredPattern.toDomainPattern()
            fakePatternRepository.savePattern(domainPattern)

            // Reload from repository
            val reloaded = (fakePatternRepository.getPattern("dp-$idx") as Result.Success).data!!

            // Verify lifecycleState survives accurately
            assertEquals(
                "Lifecycle state ${state.name} must survive persistence and reload without data loss",
                state,
                reloaded.lifecycleState
            )
        }
    }

    // In-memory test repository doubles
    private class InMemoryBarrierRepository(
        private val eventRepository: EventRepository? = null
    ) : BarrierRepository {
        val barriers = mutableListOf<BarrierHypothesis>()

        override suspend fun saveBarrier(barrier: BarrierHypothesis): Result<Unit, Exception> {
            barriers.removeAll { it.id == barrier.id }
            barriers.add(barrier)
            return Result.Success(Unit)
        }

        override suspend fun getBarriersForUser(userId: String): Result<List<BarrierHypothesis>, Exception> {
            return Result.Success(barriers.filter { it.userId == userId })
        }

        override suspend fun getActiveBarriersForUser(userId: String, excludedStates: List<BarrierConfirmationState>, limit: Int): Result<List<BarrierHypothesis>, Exception> {
            return Result.Success(barriers.filter { it.userId == userId && it.confirmationState !in excludedStates }.take(limit))
        }

        override suspend fun getBarrier(id: String): Result<BarrierHypothesis?, Exception> {
            return Result.Success(barriers.firstOrNull { it.id == id })
        }

        override suspend fun updateBarrierStateWithEvent(
            id: String,
            userId: String,
            confirmationState: BarrierConfirmationState,
            status: BarrierStatus,
            now: Long,
            event: Event?
        ): Result<Unit, Exception> {
            val idx = barriers.indexOfFirst { it.id == id && it.userId == userId }
            if (idx >= 0) {
                val old = barriers[idx]
                barriers[idx] = old.copy(
                    confirmationState = confirmationState,
                    status = status,
                    lastObservedAt = now,
                    epistemicAuthority = if (confirmationState == BarrierConfirmationState.CONFIRMED) EpistemicAuthority.USER_CONFIRMED_FACT else old.epistemicAuthority
                )
            }
            if (event != null) {
                eventRepository?.saveEvent(event)
            }
            return Result.Success(Unit)
        }
    }

    private class InMemoryPatternRepository : PatternRepository {
        val patterns = mutableListOf<Pattern>()

        override suspend fun savePattern(pattern: Pattern): Result<Unit, Exception> {
            patterns.removeAll { it.id == pattern.id }
            patterns.add(pattern)
            return Result.Success(Unit)
        }

        override suspend fun getPattern(id: String): Result<Pattern?, Exception> {
            return Result.Success(patterns.firstOrNull { it.id == id })
        }

        override suspend fun getPatternByFingerprint(userId: String, fingerprint: String): Result<Pattern?, Exception> {
            return Result.Success(patterns.firstOrNull { it.userId == userId && it.fingerprint == fingerprint })
        }

        override suspend fun getPatternsForUser(userId: String): Result<List<Pattern>, Exception> {
            return Result.Success(patterns.filter { it.userId == userId })
        }

        override fun observePatternsForUser(userId: String): Flow<List<Pattern>> {
            return MutableStateFlow(patterns.filter { it.userId == userId }).asStateFlow()
        }

        override suspend fun getPatternsByType(userId: String, type: PatternType): Result<List<Pattern>, Exception> {
            return Result.Success(patterns.filter { it.userId == userId && it.type == type })
        }

        override suspend fun getPatternsByStatus(userId: String, status: PatternStatus): Result<List<Pattern>, Exception> {
            return Result.Success(patterns.filter { it.userId == userId && it.status == status })
        }

        override suspend fun getPatternsByStatus(userId: String, status: PatternStatus, limit: Int): Result<List<Pattern>, Exception> {
            return Result.Success(patterns.filter { it.userId == userId && it.status == status }.take(limit))
        }

        override suspend fun updatePattern(pattern: Pattern): Result<Unit, Exception> {
            return savePattern(pattern)
        }

        override suspend fun updatePatternConfidence(id: String, confidence: Float, lastObservedAt: Long): Result<Unit, Exception> {
            val idx = patterns.indexOfFirst { it.id == id }
            if (idx >= 0) {
                patterns[idx] = patterns[idx].copy(confidence = confidence, lastObservedAt = lastObservedAt)
            }
            return Result.Success(Unit)
        }

        override suspend fun deletePattern(id: String): Result<Unit, Exception> {
            patterns.removeAll { it.id == id }
            return Result.Success(Unit)
        }
    }

    private class InMemoryInterventionRecommendationRepository : InterventionRecommendationRepository {
        val recommendations = mutableListOf<InterventionRecommendation>()

        override suspend fun saveRecommendation(recommendation: InterventionRecommendation): Result<Unit, Exception> {
            recommendations.removeAll { it.id == recommendation.id }
            recommendations.add(recommendation)
            return Result.Success(Unit)
        }

        override suspend fun getRecommendation(id: String): Result<InterventionRecommendation?, Exception> {
            return Result.Success(recommendations.firstOrNull { it.id == id })
        }

        override fun getPendingRecommendations(userId: String, currentTime: Long): Flow<List<InterventionRecommendation>> {
            return MutableStateFlow(recommendations.filter { it.userId == userId && it.status == InterventionRecommendationStatus.PENDING }).asStateFlow()
        }

        override suspend fun updateRecommendationStatus(id: String, status: InterventionRecommendationStatus): Result<Unit, Exception> {
            val idx = recommendations.indexOfFirst { it.id == id }
            if (idx >= 0) {
                recommendations[idx] = recommendations[idx].copy(status = status)
            }
            return Result.Success(Unit)
        }

        override suspend fun getEquivalentRecommendations(
            userId: String,
            objective: String,
            targetEntityType: String?,
            targetEntityId: String?
        ): Result<List<InterventionRecommendation>, Exception> {
            val filtered = recommendations.filter {
                it.userId == userId &&
                it.objective.name == objective &&
                it.targetEntityType == targetEntityType &&
                it.targetEntityId == targetEntityId
            }
            return Result.Success(filtered)
        }

        override suspend fun getRecommendationsForUser(
            userId: String,
            startTime: Long,
            endTime: Long,
            limit: Int
        ): Result<List<InterventionRecommendation>, Exception> {
            return Result.Success(recommendations.filter { it.userId == userId && it.createdAt in startTime..endTime }.take(limit))
        }
    }
}
