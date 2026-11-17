package com.sanket_satpute_20.ironmind.domain.engine

import com.sanket_satpute_20.ironmind.domain.ai.BarrierCategory
import com.sanket_satpute_20.ironmind.domain.ai.InterventionObjective
import com.sanket_satpute_20.ironmind.domain.ai.InterventionType
import com.sanket_satpute_20.ironmind.domain.common.Clock
import com.sanket_satpute_20.ironmind.domain.common.IdGenerator
import com.sanket_satpute_20.ironmind.domain.common.IntelligenceMetadataCodec
import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.MemoryConfirmationState
import com.sanket_satpute_20.ironmind.domain.model.barrier.BarrierConfirmationState
import com.sanket_satpute_20.ironmind.domain.model.barrier.BarrierHypothesis
import com.sanket_satpute_20.ironmind.domain.model.barrier.BarrierStatus
import com.sanket_satpute_20.ironmind.domain.model.context.AppUsageSummary
import com.sanket_satpute_20.ironmind.domain.model.context.DigitalBehaviourContext
import com.sanket_satpute_20.ironmind.domain.model.context.EpistemicAuthority
import com.sanket_satpute_20.ironmind.domain.model.context.SourceAvailability
import com.sanket_satpute_20.ironmind.domain.model.context.SourceFreshness
import com.sanket_satpute_20.ironmind.domain.model.context.UnifiedContext
import com.sanket_satpute_20.ironmind.domain.model.context.UsagePermissionStatus
import com.sanket_satpute_20.ironmind.domain.model.intervention.CausalHypothesisReference
import com.sanket_satpute_20.ironmind.domain.model.intervention.CausalHypothesisType
import com.sanket_satpute_20.ironmind.domain.model.intervention.InterventionEquivalencePolicy
import com.sanket_satpute_20.ironmind.domain.model.intervention.InterventionRecommendation
import com.sanket_satpute_20.ironmind.domain.model.intervention.InterventionRecommendationStatus
import com.sanket_satpute_20.ironmind.domain.model.pattern.DiscoveredPattern
import com.sanket_satpute_20.ironmind.domain.model.pattern.EvidenceReference
import com.sanket_satpute_20.ironmind.domain.model.pattern.EvidenceSourceType
import com.sanket_satpute_20.ironmind.domain.model.pattern.ObservationWindow
import com.sanket_satpute_20.ironmind.domain.model.pattern.Pattern
import com.sanket_satpute_20.ironmind.domain.model.pattern.PatternCategory
import com.sanket_satpute_20.ironmind.domain.model.pattern.PatternDiscoveryType
import com.sanket_satpute_20.ironmind.domain.model.pattern.PatternLifecycleState
import com.sanket_satpute_20.ironmind.domain.model.pattern.PatternStatus
import com.sanket_satpute_20.ironmind.domain.model.pattern.category
import com.sanket_satpute_20.ironmind.domain.model.pattern.lifecycleState
import com.sanket_satpute_20.ironmind.domain.repository.BarrierRepository
import com.sanket_satpute_20.ironmind.domain.repository.InterventionRecommendationRepository
import com.sanket_satpute_20.ironmind.domain.usecase.ai.HandleInterventionResultUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.ai.UpdateInterventionRecommendationStatusUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.barrier.ConfirmBarrierUseCase
import com.sanket_satpute_20.ironmind.testutil.fake.FakeEventRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.ZoneId

/**
 * Phase 3.3.1: Intelligence Foundation Hardening Test Suite.
 *
 * Covers:
 * - Concern A: Persistence Serialization Safety & Versioning
 * - Concern B: Barrier Category Evidence Quality & False-Positive Prevention
 * - Concern C: Canonical Epistemic Authority Consistency & Transition Guardrails
 */
class IntelligenceFoundationHardeningTest {

    private val testZoneId = ZoneId.of("UTC")
    private val userId = "user-hardening-1"
    private var now = 1700000000000L
    private var idCounter = 1000

    private val fakeClock = object : Clock {
        override fun currentTimeMillis(): Long = now
    }

    private val fakeIdGenerator = object : IdGenerator {
        override fun generateId(): String = "id-${++idCounter}"
    }

    private lateinit var evidenceSufficiencyEvaluator: EvidenceSufficiencyEvaluator
    private lateinit var barrierDiscoveryEngine: BarrierHypothesisDiscoveryEngine
    private lateinit var interventionReasoningEngine: InterventionReasoningEngine
    private lateinit var fakeBarrierRepository: InMemoryBarrierRepository
    private lateinit var fakeRecommendationRepository: InMemoryRecommendationRepository
    private lateinit var fakeEventRepository: FakeEventRepository

    @Before
    fun setUp() {
        evidenceSufficiencyEvaluator = EvidenceSufficiencyEvaluator()
        barrierDiscoveryEngine = BarrierHypothesisDiscoveryEngine(evidenceSufficiencyEvaluator, fakeIdGenerator)
        interventionReasoningEngine = InterventionReasoningEngine(InterventionEquivalencePolicy(), fakeIdGenerator)
        fakeEventRepository = FakeEventRepository()
        fakeBarrierRepository = InMemoryBarrierRepository()
        fakeRecommendationRepository = InMemoryRecommendationRepository()
    }

    private fun createMultiDayRefs(count: Int): List<EvidenceReference> {
        return (1..count).map { EvidenceReference("ev-$it", EvidenceSourceType.EVENT) }
    }

    private fun createPatternWithEvidence(
        id: String,
        description: String,
        category: PatternCategory = PatternCategory.PRODUCTIVITY,
        discoveryType: PatternDiscoveryType = PatternDiscoveryType.REPEATEDLY_POSTPONED_TASKS,
        evidenceCount: Int = 3,
        daysSpan: Int = 2,
        confidence: Float = 0.8f,
        lifecycleState: PatternLifecycleState = PatternLifecycleState.ESTABLISHED,
        conditions: String? = null
    ): Pattern {
        val firstObs = now - (daysSpan * 24L * 60 * 60 * 1000)
        val defaultConditions = conditions ?: IntelligenceMetadataCodec.encodePatternConditions(
            category = category,
            lifecycleState = lifecycleState,
            sourceProvenance = listOf("EventRepository")
        )
        return Pattern(
            id = id,
            userId = userId,
            fingerprint = "fp-$id",
            type = discoveryType.defaultLegacyType,
            description = description,
            conditions = defaultConditions,
            predictedBehavior = if (category == PatternCategory.CROSS_DOMAIN) "Non-causal correlation" else null,
            confidence = confidence,
            evidenceCount = evidenceCount,
            evidenceReferences = createMultiDayRefs(evidenceCount),
            firstObservedAt = firstObs,
            lastObservedAt = now,
            status = lifecycleState.toLegacyPatternStatus(),
            confirmationState = MemoryConfirmationState.UNCONFIRMED,
            createdAt = firstObs,
            updatedAt = now
        )
    }

    // =========================================================================
    // Concern A: Persistence Serialization Safety & Versioning
    // =========================================================================

    @Test
    fun test1_patternLifecycleState_v2Serialization_roundtrip() {
        val dp = DiscoveredPattern(
            id = "dp-roundtrip",
            userId = userId,
            category = PatternCategory.PRODUCTIVITY,
            type = PatternDiscoveryType.REPEATEDLY_POSTPONED_TASKS,
            description = "Recurring postponement",
            observationWindow = ObservationWindow(now - 1000, now),
            evidenceReferences = createMultiDayRefs(3),
            evidenceCount = 3,
            firstObservedAt = now - 1000,
            lastObservedAt = now,
            confidence = 0.85f,
            lifecycleState = PatternLifecycleState.EMERGING,
            sourceProvenance = listOf("Repo1", "Repo2")
        )

        val domainPattern = dp.toDomainPattern()
        assertTrue("Conditions must have v2 prefix", domainPattern.conditions?.startsWith(IntelligenceMetadataCodec.PATTERN_V2_PREFIX) == true)

        // Verify getters decode accurately
        assertEquals(PatternLifecycleState.EMERGING, domainPattern.lifecycleState)
        assertEquals(PatternCategory.PRODUCTIVITY, domainPattern.category)
    }

    @Test
    fun test2_barrierMetadata_v2Serialization_roundtrip() {
        val original = BarrierHypothesis(
            id = "b-roundtrip",
            userId = userId,
            category = BarrierCategory.EXCESSIVE_TASK_SIZE,
            description = "Tasks with complex scope repeatedly postponed",
            confirmationState = BarrierConfirmationState.UNCONFIRMED,
            status = BarrierStatus.ACTIVE,
            sourceReflectionId = "refl-99",
            firstObservedAt = now - 10000,
            lastObservedAt = now,
            title = "Task Scope & Breakdown",
            supportingPatternIds = listOf("p-1", "p-2"),
            evidenceReferences = emptyList(),
            evidenceCount = 5,
            distinctDays = 3,
            confidence = 0.72f,
            epistemicAuthority = EpistemicAuthority.AI_HYPOTHESIS,
            sourceProvenance = listOf("Pattern:p-1", "Pattern:p-2"),
            sourceFreshness = SourceFreshness.CURRENT
        )

        val encoded = IntelligenceMetadataCodec.encodeBarrierMetadata(original)
        assertTrue("Must start with v2 prefix", encoded.startsWith(IntelligenceMetadataCodec.BARRIER_V2_PREFIX))

        val decoded = IntelligenceMetadataCodec.decodeBarrierMetadata(encoded, BarrierConfirmationState.UNCONFIRMED.name)
        assertTrue(decoded.isPatternDerived)
        assertEquals(original.title, decoded.title)
        assertEquals(original.supportingPatternIds, decoded.supportingPatternIds)
        assertEquals(original.evidenceCount, decoded.evidenceCount)
        assertEquals(original.distinctDays, decoded.distinctDays)
        assertEquals(original.confidence, decoded.confidence, 0.001f)
        assertEquals(original.epistemicAuthority, decoded.epistemicAuthority)
        assertEquals(original.sourceProvenance, decoded.sourceProvenance)
        assertEquals(original.sourceFreshness, decoded.sourceFreshness)
        assertEquals(original.sourceReflectionId, decoded.originalRef)
    }

    @Test
    fun test3_specialCharactersAndDelimiters_doNotCorruptMetadata() {
        val complexTitle = "Focus, Work & Sleep | Energy = High; [Special] \"Quotes\" \n Newline"
        val barrierWithSpecialChars = BarrierHypothesis(
            id = "b-special",
            userId = userId,
            category = BarrierCategory.ENVIRONMENTAL_FRICTION,
            description = "Disruption in workspace",
            confirmationState = BarrierConfirmationState.UNCONFIRMED,
            status = BarrierStatus.ACTIVE,
            sourceReflectionId = "ref|with=delimiters&and;special,chars",
            firstObservedAt = now - 5000,
            lastObservedAt = now,
            title = complexTitle,
            supportingPatternIds = listOf("p|1", "p,2;3"),
            evidenceReferences = emptyList(),
            evidenceCount = 3,
            distinctDays = 2,
            confidence = 0.65f,
            epistemicAuthority = EpistemicAuthority.AI_HYPOTHESIS,
            sourceProvenance = listOf("Source=A|B", "Source;C,D")
        )

        val encoded = IntelligenceMetadataCodec.encodeBarrierMetadata(barrierWithSpecialChars)
        val decoded = IntelligenceMetadataCodec.decodeBarrierMetadata(encoded, BarrierConfirmationState.UNCONFIRMED.name)

        assertEquals("Special characters in title must be preserved verbatim", complexTitle, decoded.title)
        assertEquals("Delimiters in pattern IDs must survive", listOf("p|1", "p,2;3"), decoded.supportingPatternIds)
        assertEquals("Delimiters in ref must survive", "ref|with=delimiters&and;special,chars", decoded.originalRef)
        assertEquals("Delimiters in source provenance must survive", listOf("Source=A|B", "Source;C,D"), decoded.sourceProvenance)
    }

    @Test
    fun test4_legacyReflectionId_isPreservedAsActualReflection() {
        val legacyRefId = "refl-uuid-12345-actual-reflection"
        val decoded = IntelligenceMetadataCodec.decodeBarrierMetadata(legacyRefId, BarrierConfirmationState.UNCONFIRMED.name)

        assertFalse("Must not be identified as pattern derived", decoded.isPatternDerived)
        assertEquals("Exact reflection ID must be preserved without distortion", legacyRefId, decoded.originalRef)
        assertTrue("Pattern IDs must be empty", decoded.supportingPatternIds.isEmpty())
        assertEquals(0, decoded.evidenceCount)
        assertEquals(EpistemicAuthority.AI_HYPOTHESIS, decoded.epistemicAuthority)
    }

    @Test
    fun test5_legacyPhase33UnversionedFormat_isDeserializedAccurately() {
        val legacyPhase33String = "patterns:p1,p2|title=Old Title|count=4|days=3|conf=0.7|auth=AI_HYPOTHESIS|sources=Pattern:p1,Pattern:p2|freshness=CURRENT|ref=old-ref-id"
        val decoded = IntelligenceMetadataCodec.decodeBarrierMetadata(legacyPhase33String, BarrierConfirmationState.UNCONFIRMED.name)

        assertTrue(decoded.isPatternDerived)
        assertEquals(listOf("p1", "p2"), decoded.supportingPatternIds)
        assertEquals("Old Title", decoded.title)
        assertEquals(4, decoded.evidenceCount)
        assertEquals(3, decoded.distinctDays)
        assertEquals(0.7f, decoded.confidence, 0.001f)
        assertEquals(EpistemicAuthority.AI_HYPOTHESIS, decoded.epistemicAuthority)
        assertEquals("old-ref-id", decoded.originalRef)
    }

    @Test
    fun test6_legacyPhase32PatternConditions_isDeserializedAccurately() {
        val legacyConditions = "category=PRODUCTIVITY;lifecycleState=WEAKENING;sources=Repo1,Repo2"
        val decoded = IntelligenceMetadataCodec.decodePatternConditions(legacyConditions, PatternStatus.ACTIVE)

        assertEquals(PatternCategory.PRODUCTIVITY, decoded.category)
        assertEquals(PatternLifecycleState.WEAKENING, decoded.lifecycleState)
        assertEquals(listOf("Repo1", "Repo2"), decoded.sources)
    }

    @Test
    fun test7_malformedOrUnknownVersion_failsSafelyWithFallback() {
        val futureVersionString = "v99:barrier_meta:v=99|title=FutureTitle|ref=some-ref"
        val decodedFuture = IntelligenceMetadataCodec.decodeBarrierMetadata(futureVersionString, BarrierConfirmationState.UNCONFIRMED.name)

        // Must not throw, should safely fallback
        assertNotNull(decodedFuture)
        assertEquals("FutureTitle", decodedFuture.title)
        assertEquals("some-ref", decodedFuture.originalRef)

        val malformedPatternConditions = "completely_broken_payload_no_delimiters"
        val decodedPattern = IntelligenceMetadataCodec.decodePatternConditions(malformedPatternConditions, PatternStatus.ACTIVE)
        assertNull(decodedPattern.category)
        assertEquals(PatternLifecycleState.ESTABLISHED, decodedPattern.lifecycleState)
    }

    @Test
    fun test8_plainLegacyConditionsString_fallbackBasedOnStatus() {
        val legacyCondition = "AFTER_WORK_HOURS"
        val decodedActive = IntelligenceMetadataCodec.decodePatternConditions(legacyCondition, PatternStatus.ACTIVE)
        assertNull(decodedActive.category)
        assertEquals(PatternLifecycleState.ESTABLISHED, decodedActive.lifecycleState)

        val decodedInactive = IntelligenceMetadataCodec.decodePatternConditions(legacyCondition, PatternStatus.INACTIVE)
        assertNull(decodedInactive.category)
        assertEquals(PatternLifecycleState.INACTIVE, decodedInactive.lifecycleState)
    }

    // =========================================================================
    // Concern B: Barrier Category Evidence Quality & False-Positive Prevention
    // =========================================================================

    @Test
    fun test9_lowEnergy_lateDayMissedCommitmentsAlone_doesNotEstablishLowEnergy() {
        // Late-day missed commitments pattern with NO fatigue or energy evidence
        val lateDayMissedPattern = createPatternWithEvidence(
            id = "p-late-missed",
            description = "Late evening missed commitments across multiple days"
        )

        val result = barrierDiscoveryEngine.discoverBarrierHypotheses(
            userId = userId,
            unifiedContext = UnifiedContext(userId = userId, generatedAt = now),
            patterns = listOf(lateDayMissedPattern),
            existingBarriers = emptyList(),
            zoneId = testZoneId,
            evaluationTimeMs = now
        )

        assertTrue("Must NOT establish LOW_ENERGY from late-day missed commitments alone",
            result.discoveredHypotheses.none { it.category == BarrierCategory.LOW_ENERGY })
        assertTrue("Must record insufficient energy evidence",
            result.insufficientEvidenceHypotheses.any {
                it.category == BarrierCategory.LOW_ENERGY &&
                it.failureReason == EvidenceSufficiencyReason.INSUFFICIENT_ENERGY_EVIDENCE
            })
    }

    @Test
    fun test10_lowEnergy_withFatigueOrCompletionDropEvidence_establishesLowEnergy() {
        // Pattern with explicit completion drop & fatigue link
        val energyDropPattern = createPatternWithEvidence(
            id = "p-energy-drop",
            description = "Late-day task completion drop with depleted energy"
        )

        val result = barrierDiscoveryEngine.discoverBarrierHypotheses(
            userId = userId,
            unifiedContext = UnifiedContext(userId = userId, generatedAt = now),
            patterns = listOf(energyDropPattern),
            existingBarriers = emptyList(),
            zoneId = testZoneId,
            evaluationTimeMs = now
        )

        assertEquals("Must establish 1 barrier hypothesis", 1, result.discoveredHypotheses.size)
        val barrier = result.discoveredHypotheses[0]
        assertEquals(BarrierCategory.LOW_ENERGY, barrier.category)
        assertEquals("Energy Window Mismatch", barrier.title)
        assertFalse("Must remain descriptive and non-diagnostic", barrier.description.contains("burnout") || barrier.description.contains("ADHD"))
    }

    @Test
    fun test11_environmentalFriction_repeatedTaskAbandonmentAlone_doesNotEstablishEnvironmentalFriction() {
        // Abandoned tasks with NO focus interruption or environmental disruption evidence
        val abandonmentPattern = createPatternWithEvidence(
            id = "p-abandon",
            description = "Tasks abandoned and cancelled repeatedly"
        )

        val result = barrierDiscoveryEngine.discoverBarrierHypotheses(
            userId = userId,
            unifiedContext = UnifiedContext(userId = userId, generatedAt = now),
            patterns = listOf(abandonmentPattern),
            existingBarriers = emptyList(),
            zoneId = testZoneId,
            evaluationTimeMs = now
        )

        assertTrue("Must NOT establish ENVIRONMENTAL_FRICTION from task abandonment alone",
            result.discoveredHypotheses.none { it.category == BarrierCategory.ENVIRONMENTAL_FRICTION })
        assertTrue("Must record insufficient environmental disruption evidence",
            result.insufficientEvidenceHypotheses.any {
                it.category == BarrierCategory.ENVIRONMENTAL_FRICTION &&
                it.failureReason == EvidenceSufficiencyReason.INSUFFICIENT_ENVIRONMENTAL_DISRUPTION_EVIDENCE
            })
    }

    @Test
    fun test12_environmentalFriction_withInterruptedFocusSessions_establishesEnvironmentalFriction() {
        // Interrupted focus session pattern
        val focusDisruptionPattern = createPatternWithEvidence(
            id = "p-focus-interrupted",
            description = "Focus session interrupted frequently by external notifications",
            discoveryType = PatternDiscoveryType.REPEATED_FOCUS_INTERRUPTIONS
        )

        val result = barrierDiscoveryEngine.discoverBarrierHypotheses(
            userId = userId,
            unifiedContext = UnifiedContext(userId = userId, generatedAt = now),
            patterns = listOf(focusDisruptionPattern),
            existingBarriers = emptyList(),
            zoneId = testZoneId,
            evaluationTimeMs = now
        )

        assertEquals(1, result.discoveredHypotheses.size)
        assertEquals(BarrierCategory.ENVIRONMENTAL_FRICTION, result.discoveredHypotheses[0].category)
    }

    @Test
    fun test13_excessiveTaskSize_postponedTasksAlone_doesNotEstablishExcessiveTaskSize() {
        // Simple postponement without scope or complexity
        val plainPostponedPattern = createPatternWithEvidence(
            id = "p-plain-postpone",
            description = "Tasks repeatedly postponed"
        )

        val result = barrierDiscoveryEngine.discoverBarrierHypotheses(
            userId = userId,
            unifiedContext = UnifiedContext(userId = userId, generatedAt = now),
            patterns = listOf(plainPostponedPattern),
            existingBarriers = emptyList(),
            zoneId = testZoneId,
            evaluationTimeMs = now
        )

        assertTrue("Must NOT establish EXCESSIVE_TASK_SIZE on simple postponement alone",
            result.discoveredHypotheses.none { it.category == BarrierCategory.EXCESSIVE_TASK_SIZE })
        assertTrue("Must record insufficient task complexity evidence",
            result.insufficientEvidenceHypotheses.any {
                it.category == BarrierCategory.EXCESSIVE_TASK_SIZE &&
                it.failureReason == EvidenceSufficiencyReason.INSUFFICIENT_TASK_COMPLEXITY_EVIDENCE
            })
    }

    @Test
    fun test14_excessiveTaskSize_withTaskScopeOrComplexityEvidence_establishesExcessiveTaskSize() {
        // Pattern carries scope/complexity evidence
        val complexTaskPattern = createPatternWithEvidence(
            id = "p-complex-task",
            description = "Large multi-step complex tasks repeatedly postponed across days"
        )

        val result = barrierDiscoveryEngine.discoverBarrierHypotheses(
            userId = userId,
            unifiedContext = UnifiedContext(userId = userId, generatedAt = now),
            patterns = listOf(complexTaskPattern),
            existingBarriers = emptyList(),
            zoneId = testZoneId,
            evaluationTimeMs = now
        )

        assertEquals(1, result.discoveredHypotheses.size)
        assertEquals(BarrierCategory.EXCESSIVE_TASK_SIZE, result.discoveredHypotheses[0].category)
    }

    @Test
    fun test15_competingPriorities_multipleCommitmentsAlone_doesNotEstablishCompetingPriorities() {
        // Missed commitments without conflict or concurrency evidence
        val plainMissedPattern = createPatternWithEvidence(
            id = "p-plain-missed",
            description = "Missed commitments"
        )

        val result = barrierDiscoveryEngine.discoverBarrierHypotheses(
            userId = userId,
            unifiedContext = UnifiedContext(userId = userId, generatedAt = now),
            patterns = listOf(plainMissedPattern),
            existingBarriers = emptyList(),
            zoneId = testZoneId,
            evaluationTimeMs = now
        )

        assertTrue("Must NOT establish COMPETING_PRIORITIES without actual conflict evidence",
            result.discoveredHypotheses.none { it.category == BarrierCategory.COMPETING_PRIORITIES })
        assertTrue("Must record insufficient concurrency conflict evidence",
            result.insufficientEvidenceHypotheses.any {
                it.category == BarrierCategory.COMPETING_PRIORITIES &&
                it.failureReason == EvidenceSufficiencyReason.INSUFFICIENT_CONCURRENCY_CONFLICT_EVIDENCE
            })
    }

    @Test
    fun test16_competingPriorities_withOverlappingScheduleOrGoalContention_establishesCompetingPriorities() {
        // Pattern explicitly identifies competing / overlapping commitments
        val conflictPattern = createPatternWithEvidence(
            id = "p-conflict-commitments",
            discoveryType = PatternDiscoveryType.REPEATEDLY_MISSED_COMMITMENTS,
            description = "Competing concurrent missed commitments across multiple goals"
        )

        val result = barrierDiscoveryEngine.discoverBarrierHypotheses(
            userId = userId,
            unifiedContext = UnifiedContext(userId = userId, generatedAt = now),
            patterns = listOf(conflictPattern),
            existingBarriers = emptyList(),
            zoneId = testZoneId,
            evaluationTimeMs = now
        )

        assertEquals(1, result.discoveredHypotheses.size)
        assertEquals(BarrierCategory.COMPETING_PRIORITIES, result.discoveredHypotheses[0].category)
    }

    @Test
    fun test17_schedulingMismatch_overdueTasksAlone_doesNotEstablishSchedulingMismatch() {
        // Overdue tasks without time-window or execution alignment link
        val plainOverduePattern = createPatternWithEvidence(
            id = "p-plain-overdue",
            description = "Overdue tasks"
        )

        val result = barrierDiscoveryEngine.discoverBarrierHypotheses(
            userId = userId,
            unifiedContext = UnifiedContext(userId = userId, generatedAt = now),
            patterns = listOf(plainOverduePattern),
            existingBarriers = emptyList(),
            zoneId = testZoneId,
            evaluationTimeMs = now
        )

        assertTrue("Must NOT establish SCHEDULING_MISMATCH without execution alignment link",
            result.discoveredHypotheses.none { it.category == BarrierCategory.SCHEDULING_MISMATCH })
        assertTrue("Must record insufficient scheduling alignment evidence",
            result.insufficientEvidenceHypotheses.any {
                it.category == BarrierCategory.SCHEDULING_MISMATCH &&
                it.failureReason == EvidenceSufficiencyReason.INSUFFICIENT_SCHEDULING_ALIGNMENT_EVIDENCE
            })
    }

    @Test
    fun test18_schedulingMismatch_withSchedulingVsExecutionAlignmentEvidence_establishesSchedulingMismatch() {
        // Overdue tasks linked to morning deadline vs evening peak alignment
        val schedulingPattern = createPatternWithEvidence(
            id = "p-scheduling-window",
            description = "Morning tasks overdue due to morning time window mismatch",
            discoveryType = PatternDiscoveryType.TASK_COMPLETION_BY_TIME_OF_DAY
        )

        val result = barrierDiscoveryEngine.discoverBarrierHypotheses(
            userId = userId,
            unifiedContext = UnifiedContext(userId = userId, generatedAt = now),
            patterns = listOf(schedulingPattern),
            existingBarriers = emptyList(),
            zoneId = testZoneId,
            evaluationTimeMs = now
        )

        assertEquals(1, result.discoveredHypotheses.size)
        assertEquals(BarrierCategory.SCHEDULING_MISMATCH, result.discoveredHypotheses[0].category)
    }

    @Test
    fun test19_distraction_requiresValidDigitalUsageEvidenceAndPermission() {
        val crossPattern = createPatternWithEvidence(
            id = "p-distraction",
            description = "App usage during scheduled work",
            category = PatternCategory.CROSS_DOMAIN,
            discoveryType = PatternDiscoveryType.APP_USAGE_COINCIDING_WITH_MISSED_COMMITMENTS
        )

        // Case A: Permission denied
        val contextDenied = UnifiedContext(
            userId = userId,
            digitalBehaviour = DigitalBehaviourContext(
                permissionStatus = UsagePermissionStatus.DENIED,
                availability = SourceAvailability.PERMISSION_DENIED
            ),
            generatedAt = now
        )
        val resultDenied = barrierDiscoveryEngine.discoverBarrierHypotheses(
            userId = userId,
            unifiedContext = contextDenied,
            patterns = listOf(crossPattern),
            existingBarriers = emptyList(),
            zoneId = testZoneId,
            evaluationTimeMs = now
        )
        assertTrue(resultDenied.discoveredHypotheses.none { it.category == BarrierCategory.DISTRACTION })
        assertTrue(resultDenied.insufficientEvidenceHypotheses.any {
            it.category == BarrierCategory.DISTRACTION &&
            it.failureReason == EvidenceSufficiencyReason.PERMISSION_OR_DIGITAL_DATA_MISSING
        })

        // Case B: Permission granted and app usage present
        val contextGranted = UnifiedContext(
            userId = userId,
            digitalBehaviour = DigitalBehaviourContext(
                permissionStatus = UsagePermissionStatus.GRANTED,
                availability = SourceAvailability.AVAILABLE,
                appUsages = listOf(AppUsageSummary("com.social.app", "Social", 3600000L, 5, now - 10000, now))
            ),
            generatedAt = now
        )
        val resultGranted = barrierDiscoveryEngine.discoverBarrierHypotheses(
            userId = userId,
            unifiedContext = contextGranted,
            patterns = listOf(crossPattern),
            existingBarriers = emptyList(),
            zoneId = testZoneId,
            evaluationTimeMs = now
        )
        assertEquals(1, resultGranted.discoveredHypotheses.size)
        val barrier = resultGranted.discoveredHypotheses[0]
        assertEquals(BarrierCategory.DISTRACTION, barrier.category)
        assertEquals(EpistemicAuthority.AI_HYPOTHESIS, barrier.epistemicAuthority)
        assertTrue(barrier.confidence <= 0.75f)
        assertTrue(barrier.description.contains("does not imply causation"))
    }

    // =========================================================================
    // Concern C: Epistemic Authority Consistency & Transitions
    // =========================================================================

    @Test
    fun test20_highConfidence_neverPromotesHypothesisToConfirmedFact() {
        val patternMaxConfidence = createPatternWithEvidence(
            id = "p-max-conf",
            description = "Large multi-step complex tasks repeatedly postponed",
            confidence = 1.0f
        )

        val result = barrierDiscoveryEngine.discoverBarrierHypotheses(
            userId = userId,
            unifiedContext = UnifiedContext(userId = userId, generatedAt = now),
            patterns = listOf(patternMaxConfidence),
            existingBarriers = emptyList(),
            zoneId = testZoneId,
            evaluationTimeMs = now
        )

        assertEquals(1, result.discoveredHypotheses.size)
        val barrier = result.discoveredHypotheses[0]
        assertEquals(1.0f, barrier.confidence, 0.001f)
        assertEquals("Even 1.0 confidence must remain strictly AI_HYPOTHESIS",
            EpistemicAuthority.AI_HYPOTHESIS, barrier.epistemicAuthority)
    }

    @Test
    fun test21_acceptingInterventionRecommendation_doesNotConfirmBarrier() = runBlocking {
        val barrier = BarrierHypothesis(
            id = "b-unconf",
            userId = userId,
            category = BarrierCategory.EXCESSIVE_TASK_SIZE,
            description = "Large tasks postponed",
            confirmationState = BarrierConfirmationState.UNCONFIRMED,
            status = BarrierStatus.ACTIVE,
            sourceReflectionId = "",
            firstObservedAt = now - 1000,
            lastObservedAt = now,
            epistemicAuthority = EpistemicAuthority.AI_HYPOTHESIS
        )
        fakeBarrierRepository.saveBarrier(barrier)

        val rec = InterventionRecommendation(
            id = "rec-1",
            userId = userId,
            interventionType = InterventionType.BREAK_DOWN,
            objective = InterventionObjective.REDUCE_FRICTION,
            targetEntityId = "b-unconf",
            targetEntityType = "BARRIER",
            rationale = "Break into smaller subtasks",
            suggestedAction = "Create 3 subtasks",
            status = InterventionRecommendationStatus.PENDING,
            createdAt = now,
            causalHypothesis = CausalHypothesisReference(id = "b-unconf", type = CausalHypothesisType.BARRIER)
        )
        fakeRecommendationRepository.saveRecommendation(rec)

        val handleUseCase = HandleInterventionResultUseCase(
            eventRepository = fakeEventRepository,
            updateInterventionRecommendationStatusUseCase = UpdateInterventionRecommendationStatusUseCase(fakeRecommendationRepository),
            clock = fakeClock,
            idGenerator = fakeIdGenerator
        )

        // User accepts intervention suggestion
        val actionResult = handleUseCase(rec, HandleInterventionResultUseCase.Action.ACCEPT, userId)
        assertTrue(actionResult is Result.Success)

        // Verify recommendation is ACCEPTED
        val updatedRec = (fakeRecommendationRepository.getRecommendation("rec-1") as Result.Success).data!!
        assertEquals(InterventionRecommendationStatus.ACCEPTED, updatedRec.status)

        // CRITICAL CHECK: Underlying barrier MUST REMAIN UNCONFIRMED with AI_HYPOTHESIS
        val barrierAfter = (fakeBarrierRepository.getBarrier("b-unconf") as Result.Success).data!!
        assertEquals(BarrierConfirmationState.UNCONFIRMED, barrierAfter.confirmationState)
        assertEquals(EpistemicAuthority.AI_HYPOTHESIS, barrierAfter.epistemicAuthority)
    }

    @Test
    fun test22_overridingInterventionRecommendation_doesNotConfirmBarrier() = runBlocking {
        val barrier = BarrierHypothesis(
            id = "b-target-2",
            userId = userId,
            category = BarrierCategory.SCHEDULING_MISMATCH,
            description = "Scheduling window mismatch",
            confirmationState = BarrierConfirmationState.UNCONFIRMED,
            status = BarrierStatus.ACTIVE,
            sourceReflectionId = "",
            firstObservedAt = now - 1000,
            lastObservedAt = now,
            epistemicAuthority = EpistemicAuthority.AI_HYPOTHESIS
        )
        fakeBarrierRepository.saveBarrier(barrier)

        val rec = InterventionRecommendation(
            id = "rec-override",
            userId = userId,
            interventionType = InterventionType.RESCHEDULE,
            objective = InterventionObjective.REDUCE_FRICTION,
            targetEntityId = "b-target-2",
            targetEntityType = "BARRIER",
            rationale = "Move to afternoon",
            suggestedAction = "Reschedule to 2pm",
            status = InterventionRecommendationStatus.PENDING,
            createdAt = now
        )
        fakeRecommendationRepository.saveRecommendation(rec)

        val handleUseCase = HandleInterventionResultUseCase(
            eventRepository = fakeEventRepository,
            updateInterventionRecommendationStatusUseCase = UpdateInterventionRecommendationStatusUseCase(fakeRecommendationRepository),
            clock = fakeClock,
            idGenerator = fakeIdGenerator
        )

        // User overrides suggestion
        handleUseCase(rec, HandleInterventionResultUseCase.Action.CORRECT, userId, "Move to 8pm instead")

        // Underlying barrier remains UNCONFIRMED
        val barrierAfter = (fakeBarrierRepository.getBarrier("b-target-2") as Result.Success).data!!
        assertEquals(BarrierConfirmationState.UNCONFIRMED, barrierAfter.confirmationState)
        assertEquals(EpistemicAuthority.AI_HYPOTHESIS, barrierAfter.epistemicAuthority)
    }

    @Test
    fun test23_onlyExplicitUserConfirmation_establishesConfirmedBarrier() = runBlocking {
        val barrier = BarrierHypothesis(
            id = "b-confirm-explicit",
            userId = userId,
            category = BarrierCategory.EXCESSIVE_TASK_SIZE,
            description = "Complex task size",
            confirmationState = BarrierConfirmationState.UNCONFIRMED,
            status = BarrierStatus.ACTIVE,
            sourceReflectionId = "",
            firstObservedAt = now - 1000,
            lastObservedAt = now,
            epistemicAuthority = EpistemicAuthority.AI_HYPOTHESIS
        )
        fakeBarrierRepository.saveBarrier(barrier)

        val confirmUseCase = ConfirmBarrierUseCase(
            barrierRepository = fakeBarrierRepository,
            eventRepository = fakeEventRepository,
            idGenerator = fakeIdGenerator,
            clock = fakeClock
        )

        val confirmResult = confirmUseCase(userId, "b-confirm-explicit")
        assertTrue(confirmResult is Result.Success)

        val confirmedBarrier = (fakeBarrierRepository.getBarrier("b-confirm-explicit") as Result.Success).data!!
        assertEquals(BarrierConfirmationState.CONFIRMED, confirmedBarrier.confirmationState)
        assertEquals(EpistemicAuthority.USER_CONFIRMED_FACT, confirmedBarrier.epistemicAuthority)
    }

    @Test
    fun test24_refutedOrDismissedBarrier_cannotBeConfirmedDirectly() = runBlocking {
        val refutedBarrier = BarrierHypothesis(
            id = "b-refuted",
            userId = userId,
            category = BarrierCategory.DISTRACTION,
            description = "Distraction",
            confirmationState = BarrierConfirmationState.REJECTED,
            status = BarrierStatus.EXPIRED,
            sourceReflectionId = "",
            firstObservedAt = now - 1000,
            lastObservedAt = now
        )
        fakeBarrierRepository.saveBarrier(refutedBarrier)

        val confirmUseCase = ConfirmBarrierUseCase(
            barrierRepository = fakeBarrierRepository,
            eventRepository = fakeEventRepository,
            idGenerator = fakeIdGenerator,
            clock = fakeClock
        )

        val result = confirmUseCase(userId, "b-refuted")
        assertTrue("Attempting to confirm a refuted barrier must fail", result is Result.Failure)
    }

    @Test
    fun test25_evidenceProvenance_isPreservedThroughPipeline() {
        val pattern = createPatternWithEvidence(
            id = "p-prov-1",
            description = "Large multi-step complex tasks repeatedly postponed across days",
            category = PatternCategory.PRODUCTIVITY
        )

        val result = barrierDiscoveryEngine.discoverBarrierHypotheses(
            userId = userId,
            unifiedContext = UnifiedContext(userId = userId, generatedAt = now),
            patterns = listOf(pattern),
            existingBarriers = emptyList(),
            zoneId = testZoneId,
            evaluationTimeMs = now
        )

        assertEquals(1, result.discoveredHypotheses.size)
        val barrier = result.discoveredHypotheses[0]
        assertEquals(listOf("Pattern:p-prov-1"), barrier.sourceProvenance)
        assertEquals(listOf("p-prov-1"), barrier.supportingPatternIds)

        val recommendations = interventionReasoningEngine.generateRecommendations(
            userId = userId,
            barrierHypotheses = listOf(barrier),
            historicalRecommendations = emptyList(),
            currentTimeMs = now
        )

        assertEquals(1, recommendations.size)
        val rec = recommendations[0]
        assertEquals(barrier.id, rec.causalHypothesis?.id)
        assertEquals(CausalHypothesisType.BARRIER, rec.causalHypothesis?.type)
    }

    // In-memory test doubles
    private class InMemoryBarrierRepository : BarrierRepository {
        val store = mutableMapOf<String, BarrierHypothesis>()

        override suspend fun saveBarrier(barrier: BarrierHypothesis): Result<Unit, Exception> {
            store[barrier.id] = barrier
            return Result.Success(Unit)
        }

        override suspend fun getBarrier(id: String): Result<BarrierHypothesis?, Exception> {
            return Result.Success(store[id])
        }

        override suspend fun getBarriersForUser(userId: String): Result<List<BarrierHypothesis>, Exception> {
            return Result.Success(store.values.filter { it.userId == userId })
        }

        override suspend fun getActiveBarriersForUser(userId: String, excludedStates: List<BarrierConfirmationState>, limit: Int): Result<List<BarrierHypothesis>, Exception> {
            return Result.Success(store.values.filter { it.userId == userId && it.confirmationState !in excludedStates }.take(limit))
        }

        override fun observeBarriersForUser(userId: String): Flow<List<BarrierHypothesis>> {
            return flowOf(store.values.filter { it.userId == userId })
        }

        override suspend fun updateBarrierState(id: String, userId: String, confirmationState: BarrierConfirmationState, status: BarrierStatus, now: Long): Result<Unit, Exception> {
            val existing = store[id] ?: return Result.Failure(IllegalArgumentException("Not found"))
            val newAuthority = if (confirmationState == BarrierConfirmationState.CONFIRMED) {
                EpistemicAuthority.USER_CONFIRMED_FACT
            } else existing.epistemicAuthority
            store[id] = existing.copy(confirmationState = confirmationState, status = status, epistemicAuthority = newAuthority)
            return Result.Success(Unit)
        }

        override suspend fun updateBarrierStateWithEvent(id: String, userId: String, confirmationState: BarrierConfirmationState, status: BarrierStatus, now: Long, event: com.sanket_satpute_20.ironmind.domain.model.Event?): Result<Unit, Exception> {
            return updateBarrierState(id, userId, confirmationState, status, now)
        }
    }

    private class InMemoryRecommendationRepository : InterventionRecommendationRepository {
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
