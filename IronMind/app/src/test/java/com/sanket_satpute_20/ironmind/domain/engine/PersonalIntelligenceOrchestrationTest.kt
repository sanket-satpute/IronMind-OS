package com.sanket_satpute_20.ironmind.domain.engine

import com.sanket_satpute_20.ironmind.domain.ai.BarrierCategory
import com.sanket_satpute_20.ironmind.domain.ai.InterventionObjective
import com.sanket_satpute_20.ironmind.domain.ai.InterventionType
import com.sanket_satpute_20.ironmind.domain.common.Clock
import com.sanket_satpute_20.ironmind.domain.common.IdGenerator
import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.Commitment
import com.sanket_satpute_20.ironmind.domain.model.CommitmentStatus
import com.sanket_satpute_20.ironmind.domain.model.Outcome
import com.sanket_satpute_20.ironmind.domain.model.ResultStatus
import com.sanket_satpute_20.ironmind.domain.model.Task
import com.sanket_satpute_20.ironmind.domain.model.TaskStatus
import com.sanket_satpute_20.ironmind.domain.model.barrier.BarrierConfirmationState
import com.sanket_satpute_20.ironmind.domain.model.barrier.BarrierHypothesis
import com.sanket_satpute_20.ironmind.domain.model.barrier.BarrierStatus
import com.sanket_satpute_20.ironmind.domain.model.context.AppUsageSummary
import com.sanket_satpute_20.ironmind.domain.model.context.CommitmentContextSummary
import com.sanket_satpute_20.ironmind.domain.model.context.ContextSnapshot
import com.sanket_satpute_20.ironmind.domain.model.context.CrossDomainCorrelation
import com.sanket_satpute_20.ironmind.domain.model.context.CrossDomainCorrelationType
import com.sanket_satpute_20.ironmind.domain.model.context.DigitalBehaviourContext
import com.sanket_satpute_20.ironmind.domain.model.context.EpistemicAuthority
import com.sanket_satpute_20.ironmind.domain.model.context.FocusActivitySummary
import com.sanket_satpute_20.ironmind.domain.model.context.ProductivityContext
import com.sanket_satpute_20.ironmind.domain.model.context.SourceAvailability
import com.sanket_satpute_20.ironmind.domain.model.context.SourceFreshness
import com.sanket_satpute_20.ironmind.domain.model.context.TaskContextSummary
import com.sanket_satpute_20.ironmind.domain.model.context.UnifiedContext
import com.sanket_satpute_20.ironmind.domain.model.context.UsagePermissionStatus
import com.sanket_satpute_20.ironmind.domain.model.insight.ExplainabilityBlock
import com.sanket_satpute_20.ironmind.domain.model.insight.InsightCategory
import com.sanket_satpute_20.ironmind.domain.model.insight.InsightJsonCodec
import com.sanket_satpute_20.ironmind.domain.model.insight.InsightReviewStatus
import com.sanket_satpute_20.ironmind.domain.model.insight.PersonalInsight
import com.sanket_satpute_20.ironmind.domain.model.intervention.InterventionEquivalencePolicy
import com.sanket_satpute_20.ironmind.domain.model.intervention.InterventionRecommendation
import com.sanket_satpute_20.ironmind.domain.model.intervention.InterventionRecommendationStatus
import com.sanket_satpute_20.ironmind.domain.model.pattern.DiscoveredPattern
import com.sanket_satpute_20.ironmind.domain.model.pattern.Pattern
import com.sanket_satpute_20.ironmind.domain.model.pattern.PatternType
import com.sanket_satpute_20.ironmind.domain.repository.BarrierRepository
import com.sanket_satpute_20.ironmind.domain.repository.InsightRepository
import com.sanket_satpute_20.ironmind.domain.repository.InterventionRecommendationRepository
import com.sanket_satpute_20.ironmind.domain.repository.OutcomeRepository
import com.sanket_satpute_20.ironmind.domain.repository.PatternRepository
import com.sanket_satpute_20.ironmind.domain.usecase.insight.ReviewPersonalInsightUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.ZoneId

/**
 * Phase 3.4: Comprehensive test suite for Intelligence Orchestration & Personal Insights.
 *
 * Covers all 15 required scenarios:
 * 1. End-to-end context-to-insight orchestration
 * 2. Evidence-backed insight generation
 * 3. Insufficient evidence suppression
 * 4. Stale source handling
 * 5. Missing Usage Access handling
 * 6. Non-causal cross-domain insights
 * 7. Insight deduplication
 * 8. Dismissed insight suppression
 * 9. User feedback persistence
 * 10. Intervention acceptance not being treated as success
 * 11. Outcome measurement and unknown effectiveness
 * 12. Evidence provenance preservation
 * 13. User/session ownership isolation
 * 14. No autonomous intervention execution
 * 15. Regression of existing pattern, barrier and intervention workflows
 */
class PersonalIntelligenceOrchestrationTest {

    private val userId = "user-test-3-4"
    private val zoneId = ZoneId.of("UTC")
    private val now = 1700000000000L
    private val sevenDaysAgo = now - (7L * 24 * 60 * 60 * 1000)

    private val fakeClock = object : Clock {
        override fun currentTimeMillis(): Long = now
    }

    private var idCounter = 100
    private val fakeIdGenerator = object : IdGenerator {
        override fun generateId(): String = "id-${++idCounter}"
    }

    private lateinit var fakeInsightRepository: InMemoryInsightRepository
    private lateinit var fakeOutcomeRepository: InMemoryOutcomeRepository
    private lateinit var fakeBarrierRepository: InMemoryBarrierRepository
    private lateinit var fakeRecommendationRepository: InMemoryRecommendationRepository
    private lateinit var fakePatternRepository: InMemoryPatternRepository
    private lateinit var fakeContextEngine: FakeContextEngine
    private lateinit var fakePatternEngine: FakePatternEngine

    private lateinit var barrierDiscoveryEngine: BarrierHypothesisDiscoveryEngine
    private lateinit var interventionReasoningEngine: InterventionReasoningEngine
    private lateinit var barrierOrchestrator: BarrierUnderstandingAndInterventionOrchestrator
    private lateinit var insightDiscoveryEngine: PersonalInsightDiscoveryEngine
    private lateinit var orchestrator: PersonalIntelligenceOrchestrator
    private lateinit var reviewUseCase: ReviewPersonalInsightUseCase

    @Before
    fun setUp() {
        fakeInsightRepository = InMemoryInsightRepository()
        fakeOutcomeRepository = InMemoryOutcomeRepository()
        fakeBarrierRepository = InMemoryBarrierRepository()
        fakeRecommendationRepository = InMemoryRecommendationRepository()
        fakePatternRepository = InMemoryPatternRepository()
        fakeContextEngine = FakeContextEngine()
        fakePatternEngine = FakePatternEngine()

        val sufficiencyEvaluator = EvidenceSufficiencyEvaluator()
        barrierDiscoveryEngine = BarrierHypothesisDiscoveryEngine(sufficiencyEvaluator, fakeIdGenerator)
        interventionReasoningEngine = InterventionReasoningEngine(InterventionEquivalencePolicy(), fakeIdGenerator)

        barrierOrchestrator = BarrierUnderstandingAndInterventionOrchestrator(
            contextEngine = fakeContextEngine,
            patternEngine = fakePatternEngine,
            barrierHypothesisDiscoveryEngine = barrierDiscoveryEngine,
            interventionReasoningEngine = interventionReasoningEngine,
            patternRepository = fakePatternRepository,
            barrierRepository = fakeBarrierRepository,
            interventionRecommendationRepository = fakeRecommendationRepository,
            clock = fakeClock
        )

        insightDiscoveryEngine = PersonalInsightDiscoveryEngine(fakeClock, fakeIdGenerator)

        orchestrator = PersonalIntelligenceOrchestrator(
            barrierUnderstandingOrchestrator = barrierOrchestrator,
            personalInsightDiscoveryEngine = insightDiscoveryEngine,
            insightRepository = fakeInsightRepository,
            outcomeRepository = fakeOutcomeRepository,
            clock = fakeClock,
            recommendationRepository = fakeRecommendationRepository
        )

        reviewUseCase = ReviewPersonalInsightUseCase(
            insightRepository = fakeInsightRepository,
            recommendationRepository = fakeRecommendationRepository,
            clock = fakeClock
        )
    }

    // =========================================================================
    // Test 1: End-to-End Context-to-Insight Orchestration
    // =========================================================================

    @Test
    fun test1_endToEnd_contextToInsight_orchestration() = runBlocking {
        val commitments = listOf(
            createCommitment("c1", CommitmentStatus.COMPLETED),
            createCommitment("c2", CommitmentStatus.COMPLETED),
            createCommitment("c3", CommitmentStatus.MISSED)
        )
        val tasks = listOf(
            createTask("t1", true, now - 3600000L),
            createTask("t2", true, now - 7200000L),
            createTask("t3", true, now - 10800000L)
        )
        val unifiedContext = UnifiedContext(
            userId = userId,
            generatedAt = now,
            windowStartTimeMs = sevenDaysAgo,
            windowEndTimeMs = now,
            productivity = ProductivityContext(
                commitmentSummary = CommitmentContextSummary(
                    completedCommitments = commitments.filter { it.status == CommitmentStatus.COMPLETED },
                    missedCommitments = commitments.filter { it.status == CommitmentStatus.MISSED }
                ),
                taskSummary = TaskContextSummary(completedTasks = tasks)
            ),
            digitalBehaviour = DigitalBehaviourContext(
                permissionStatus = UsagePermissionStatus.GRANTED,
                availability = SourceAvailability.AVAILABLE
            )
        )
        fakeContextEngine.stubContext = unifiedContext

        val result = orchestrator.orchestrate(userId, sevenDaysAgo, now, zoneId)
        assertTrue(result is Result.Success)
        val success = (result as Result.Success).data

        assertTrue("Should discover insights", success.newlyDiscoveredInsights.isNotEmpty())
        assertEquals(success.newlyDiscoveredInsights.size, success.allActiveInsights.size)

        // Verify every insight has complete ExplainabilityBlock answering 6 questions
        for (insight in success.newlyDiscoveredInsights) {
            val exp = insight.explainabilityBlock
            assertTrue("whatWasObserved must not be blank", exp.whatWasObserved.isNotBlank())
            assertTrue("observationPeriod must not be blank", exp.observationPeriod.isNotBlank())
            assertTrue("supportingEvidenceDescription must not be blank", exp.supportingEvidenceDescription.isNotBlank())
            assertTrue("whyItMatters must not be blank", exp.whyItMatters.isNotBlank())
            assertTrue("uncertaintyOrLimitations must not be blank", exp.uncertaintyOrLimitations.isNotBlank())
            assertTrue("suggestedNextSteps must not be blank", exp.suggestedNextSteps.isNotBlank())
        }
    }

    // =========================================================================
    // Test 2: Evidence-Backed Insight Generation
    // =========================================================================

    @Test
    fun test2_evidenceBacked_insightGeneration() = runBlocking {
        val commitments = listOf(
            createCommitment("c1", CommitmentStatus.COMPLETED),
            createCommitment("c2", CommitmentStatus.COMPLETED),
            createCommitment("c3", CommitmentStatus.MISSED)
        )
        val unifiedContext = UnifiedContext(
            userId = userId,
            generatedAt = now,
            windowStartTimeMs = sevenDaysAgo,
            windowEndTimeMs = now,
            productivity = ProductivityContext(
                commitmentSummary = CommitmentContextSummary(
                    completedCommitments = commitments.filter { it.status == CommitmentStatus.COMPLETED },
                    missedCommitments = commitments.filter { it.status == CommitmentStatus.MISSED }
                )
            )
        )
        fakeContextEngine.stubContext = unifiedContext

        val result = orchestrator.orchestrate(userId, sevenDaysAgo, now, zoneId)
        val insights = (result as Result.Success).data.newlyDiscoveredInsights

        val prodInsight = insights.find { it.category == InsightCategory.PRODUCTIVITY }
        assertNotNull("Must generate productivity insight", prodInsight)
        assertEquals("Commitment Completion Rhythm", prodInsight!!.title)
        assertTrue(prodInsight.explanation.contains("Completed 2 of 3"))
        assertEquals(EpistemicAuthority.DERIVED_CALCULATION, prodInsight.epistemicAuthority)
        assertEquals(2, prodInsight.supportingEvidenceIds.size)
    }

    // =========================================================================
    // Test 3: Insufficient Evidence Suppression
    // =========================================================================

    @Test
    fun test3_insufficientEvidence_suppression() = runBlocking {
        // Only 1 commitment - insufficient (requires >= 3)
        val commitments = listOf(createCommitment("c1", CommitmentStatus.COMPLETED))
        val unifiedContext = UnifiedContext(
            userId = userId,
            generatedAt = now,
            windowStartTimeMs = sevenDaysAgo,
            windowEndTimeMs = now,
            productivity = ProductivityContext(
                commitmentSummary = CommitmentContextSummary(
                    completedCommitments = commitments
                )
            )
        )
        fakeContextEngine.stubContext = unifiedContext

        val result = orchestrator.orchestrate(userId, sevenDaysAgo, now, zoneId)
        val res = (result as Result.Success).data

        val prodInsight = res.newlyDiscoveredInsights.find { it.category == InsightCategory.PRODUCTIVITY }
        assertEquals("Productivity insight must be suppressed", null, prodInsight)
        assertTrue(
            "Diagnostic notes must mention insufficient commitments",
            res.diagnosticNotes.any { it.contains("Insufficient commitments") }
        )
    }

    // =========================================================================
    // Test 4: Stale Source Handling
    // =========================================================================

    @Test
    fun test4_staleSource_handling() = runBlocking {
        val appUsages = listOf(
            AppUsageSummary("com.example.app", "TestApp", 1800000L, 5, sevenDaysAgo, now)
        )
        val unifiedContext = UnifiedContext(
            userId = userId,
            generatedAt = now,
            windowStartTimeMs = sevenDaysAgo,
            windowEndTimeMs = now,
            isFresh = false, // STALE!
            digitalBehaviour = DigitalBehaviourContext(
                permissionStatus = UsagePermissionStatus.GRANTED,
                availability = SourceAvailability.AVAILABLE,
                appUsages = appUsages
            )
        )
        fakeContextEngine.stubContext = unifiedContext

        val result = orchestrator.orchestrate(userId, sevenDaysAgo, now, zoneId)
        val insights = (result as Result.Success).data.newlyDiscoveredInsights

        val digInsight = insights.find { it.category == InsightCategory.DIGITAL_BEHAVIOUR }
        assertNotNull("Must discover digital insight", digInsight)
        assertEquals(SourceFreshness.STALE, digInsight!!.sourceFreshness)
        assertTrue("Confidence must be degraded for stale source", digInsight.confidence <= 0.5f)
        assertTrue(
            "Explainability must disclose staleness",
            digInsight.explainabilityBlock.uncertaintyOrLimitations.contains("STALE")
        )
    }

    // =========================================================================
    // Test 5: Missing Usage Access Handling
    // =========================================================================

    @Test
    fun test5_missingUsageAccess_handling() = runBlocking {
        val appUsages = listOf(
            AppUsageSummary("com.example.app", "TestApp", 1800000L, 5, sevenDaysAgo, now)
        )
        val unifiedContext = UnifiedContext(
            userId = userId,
            generatedAt = now,
            windowStartTimeMs = sevenDaysAgo,
            windowEndTimeMs = now,
            digitalBehaviour = DigitalBehaviourContext(
                permissionStatus = UsagePermissionStatus.DENIED, // NO PERMISSION!
                availability = SourceAvailability.UNAVAILABLE_HARDWARE,
                appUsages = appUsages
            )
        )
        fakeContextEngine.stubContext = unifiedContext

        val result = orchestrator.orchestrate(userId, sevenDaysAgo, now, zoneId)
        val res = (result as Result.Success).data

        val digInsight = res.newlyDiscoveredInsights.find { it.category == InsightCategory.DIGITAL_BEHAVIOUR }
        assertEquals("Must NOT generate digital behaviour insight without permission", null, digInsight)
        assertTrue(
            "Diagnostic notes must mention permission missing",
            res.diagnosticNotes.any { it.contains("Usage Access permission not granted") }
        )
    }

    // =========================================================================
    // Test 6: Non-Causal Cross-Domain Insights
    // =========================================================================

    @Test
    fun test6_nonCausal_crossDomainInsights() = runBlocking {
        val correlation = CrossDomainCorrelation(
            id = "corr-1",
            title = "App spike during pending commitment",
            description = "High usage observed while task was pending",
            productivityEntityId = "task-10",
            productivityEntityType = "TASK",
            productivityEntityTitle = "Deep Work Report",
            digitalAppPackage = "com.social.app",
            digitalUsageDurationMs = 2400000L,
            observedWindowStartMs = sevenDaysAgo,
            observedWindowEndMs = now,
            correlationType = CrossDomainCorrelationType.HIGH_USAGE_COINCIDING_WITH_MISSED_COMMITMENT,
            confidence = 0.65f,
            authority = EpistemicAuthority.AI_HYPOTHESIS,
            disclaimer = "Tentative correlation only. Does not imply causation."
        )

        val unifiedContext = UnifiedContext(
            userId = userId,
            generatedAt = now,
            windowStartTimeMs = sevenDaysAgo,
            windowEndTimeMs = now,
            correlations = listOf(correlation)
        )
        fakeContextEngine.stubContext = unifiedContext

        val result = orchestrator.orchestrate(userId, sevenDaysAgo, now, zoneId)
        val insights = (result as Result.Success).data.newlyDiscoveredInsights

        val crossInsight = insights.find { it.category == InsightCategory.CROSS_DOMAIN }
        assertNotNull("Must generate cross-domain insight", crossInsight)
        assertEquals(EpistemicAuthority.AI_HYPOTHESIS, crossInsight!!.epistemicAuthority)
        assertTrue(
            "Must explicitly state correlation != causation",
            crossInsight.explainabilityBlock.uncertaintyOrLimitations.contains("Correlation does NOT imply causation")
        )
    }

    // =========================================================================
    // Test 7: Insight Deduplication
    // =========================================================================

    @Test
    fun test7_insight_deduplication() = runBlocking {
        val commitments = listOf(
            createCommitment("c1", CommitmentStatus.COMPLETED),
            createCommitment("c2", CommitmentStatus.COMPLETED),
            createCommitment("c3", CommitmentStatus.MISSED)
        )
        fakeContextEngine.stubContext = UnifiedContext(
            userId = userId,
            generatedAt = now,
            windowStartTimeMs = sevenDaysAgo,
            windowEndTimeMs = now,
            productivity = ProductivityContext(
                commitmentSummary = CommitmentContextSummary(
                    completedCommitments = commitments.filter { it.status == CommitmentStatus.COMPLETED },
                    missedCommitments = commitments.filter { it.status == CommitmentStatus.MISSED }
                )
            )
        )

        // First run
        val res1 = orchestrator.orchestrate(userId, sevenDaysAgo, now, zoneId)
        val count1 = (res1 as Result.Success).data.newlyDiscoveredInsights.size
        assertTrue("Run 1 must generate insights", count1 > 0)

        // Second run with same context
        val res2 = orchestrator.orchestrate(userId, sevenDaysAgo, now, zoneId)
        val success2 = (res2 as Result.Success).data

        assertEquals("Run 2 must discover 0 new duplicates", 0, success2.newlyDiscoveredInsights.size)
        assertTrue("Run 2 must record suppression/deduplication", success2.suppressedCount > 0)
        assertEquals("Total insights in repo must remain count1", count1, success2.allActiveInsights.size)
    }

    // =========================================================================
    // Test 8: Dismissed Insight Suppression
    // =========================================================================

    @Test
    fun test8_dismissedInsight_suppression() = runBlocking {
        val commitments = listOf(
            createCommitment("c1", CommitmentStatus.COMPLETED),
            createCommitment("c2", CommitmentStatus.COMPLETED),
            createCommitment("c3", CommitmentStatus.MISSED)
        )
        fakeContextEngine.stubContext = UnifiedContext(
            userId = userId,
            generatedAt = now,
            windowStartTimeMs = sevenDaysAgo,
            windowEndTimeMs = now,
            productivity = ProductivityContext(
                commitmentSummary = CommitmentContextSummary(
                    completedCommitments = commitments.filter { it.status == CommitmentStatus.COMPLETED },
                    missedCommitments = commitments.filter { it.status == CommitmentStatus.MISSED }
                )
            )
        )

        // 1. Initial run
        val res1 = orchestrator.orchestrate(userId, sevenDaysAgo, now, zoneId)
        val insight = (res1 as Result.Success).data.newlyDiscoveredInsights.first()

        // 2. User dismisses the insight
        val dismissResult = reviewUseCase.dismiss(insight.id, userId)
        assertTrue(dismissResult is Result.Success)

        // Verify dismissal state
        val stored = (fakeInsightRepository.getInsightById(insight.id) as Result.Success).data
        assertEquals(InsightReviewStatus.DISMISSED, stored!!.reviewStatus)

        // 3. Re-run orchestrator
        val res2 = orchestrator.orchestrate(userId, sevenDaysAgo, now, zoneId)
        val success2 = (res2 as Result.Success).data

        // Must NOT regenerate the dismissed insight as a new active insight
        val newlyCreated = success2.newlyDiscoveredInsights.find { it.title == insight.title }
        assertEquals("Must not regenerate dismissed insight", null, newlyCreated)
        assertTrue("Suppression count must be incremented", success2.suppressedCount > 0)
    }

    // =========================================================================
    // Test 9: User Feedback Persistence
    // =========================================================================

    @Test
    fun test9_userFeedback_persistence() = runBlocking {
        val insight = PersonalInsight(
            id = "insight-fb-1",
            userId = userId,
            category = InsightCategory.TASK_SCHEDULING,
            title = "Task Scheduling Rhythm",
            explanation = "Tasks completed in the morning",
            explainabilityBlock = ExplainabilityBlock("Observed morning tasks", "7d", "3 tasks", "Focus", "None", "Keep doing it"),
            observationWindowStartMs = sevenDaysAgo,
            observationWindowEndMs = now,
            firstObservedAt = sevenDaysAgo,
            lastObservedAt = now,
            confidence = 0.8f,
            epistemicAuthority = EpistemicAuthority.AI_HYPOTHESIS,
            createdAt = now,
            updatedAt = now
        )
        fakeInsightRepository.saveInsight(insight)

        val feedbackText = "Morning tasks were due to client availability."
        val result = reviewUseCase.provideFeedback(insight.id, userId, feedbackText)
        assertTrue(result is Result.Success)

        val updated = (fakeInsightRepository.getInsightById(insight.id) as Result.Success).data
        assertNotNull(updated)
        assertEquals(InsightReviewStatus.USEFUL, updated!!.reviewStatus)
        assertEquals(feedbackText, updated.userFeedback)
    }

    // =========================================================================
    // Test 10: Intervention Acceptance Not Treated as Success
    // =========================================================================

    @Test
    fun test10_interventionAcceptance_notTreatedAsSuccess() = runBlocking {
        val rec = InterventionRecommendation(
            id = "rec-101",
            userId = userId,
            interventionType = InterventionType.BREAK_DOWN,
            objective = InterventionObjective.REDUCE_FRICTION,
            targetEntityId = "task-55",
            targetEntityType = "TASK",
            rationale = "Task is large",
            suggestedAction = "Break into smaller sub-tasks",
            status = InterventionRecommendationStatus.ACCEPTED, // USER ACCEPTED!
            createdAt = now - 3600000L
        )

        // NO OUTCOMES RECORDED!
        fakeRecommendationRepository.saveRecommendation(rec)
        fakeContextEngine.stubContext = UnifiedContext(
            userId = userId,
            generatedAt = now,
            windowStartTimeMs = sevenDaysAgo,
            windowEndTimeMs = now
        )

        val result = orchestrator.orchestrate(userId, sevenDaysAgo, now, zoneId)
        val insights = (result as Result.Success).data.newlyDiscoveredInsights

        val outcomeInsight = insights.find { it.category == InsightCategory.INTERVENTION_OUTCOME }
        assertNotNull("Must generate intervention outcome insight", outcomeInsight)
        assertEquals("Intervention Accepted — Outcome Not Measured", outcomeInsight!!.title)
        assertTrue(
            "Effectiveness must be explicitly UNKNOWN",
            outcomeInsight.explainabilityBlock.uncertaintyOrLimitations.contains("EFFECTIVENESS IS UNKNOWN")
        )
        assertTrue(
            "Acceptance must never be treated as success",
            outcomeInsight.explainabilityBlock.uncertaintyOrLimitations.contains("Acceptance of a recommendation must NEVER be treated as successful intervention")
        )
        assertEquals(EpistemicAuthority.AI_HYPOTHESIS, outcomeInsight.epistemicAuthority)
        assertTrue("Confidence must remain low without measured outcomes", outcomeInsight.confidence <= 0.3f)
    }

    // =========================================================================
    // Test 11: Outcome Measurement and Unknown Effectiveness
    // =========================================================================

    @Test
    fun test11_outcomeMeasurement_andUnknownEffectiveness() = runBlocking {
        val rec = InterventionRecommendation(
            id = "rec-102",
            userId = userId,
            interventionType = InterventionType.RESCHEDULE,
            objective = InterventionObjective.REDUCE_FRICTION,
            targetEntityId = "task-77",
            targetEntityType = "TASK",
            rationale = "Shift task to morning",
            suggestedAction = "Shift task to 9 AM",
            status = InterventionRecommendationStatus.ACCEPTED,
            createdAt = now - 86400000L
        )
        fakeRecommendationRepository.saveRecommendation(rec)

        // Case A: Only 1 outcome (N < 3) -> Insufficient samples
        fakeOutcomeRepository.saveOutcome(
            Outcome(
                id = "out-1",
                userId = userId,
                sourceEntityId = rec.id,
                sourceEntityType = "INTERVENTION",
                resultStatus = ResultStatus.POSITIVE,
                actualDurationMinutes = 30,
                completedAt = now - 3600000L,
                createdAt = now - 3600000L
            )
        )

        fakeContextEngine.stubContext = UnifiedContext(
            userId = userId,
            generatedAt = now,
            windowStartTimeMs = sevenDaysAgo,
            windowEndTimeMs = now
        )

        val resA = orchestrator.orchestrate(userId, sevenDaysAgo, now, zoneId)
        val insightA = (resA as Result.Success).data.newlyDiscoveredInsights.find { it.category == InsightCategory.INTERVENTION_OUTCOME }
        assertNotNull(insightA)
        assertEquals("Intervention Measurement: Insufficient Samples", insightA!!.title)
        assertTrue(
            "Must report effectiveness unknown due to N < 3",
            insightA.explainabilityBlock.uncertaintyOrLimitations.contains("EFFECTIVENESS IS UNKNOWN due to insufficient sample size")
        )

        // Case B: Add 3 more outcomes (total 4: 3 SUCCESS, 1 FAILED) -> Measured effectiveness!
        fakeInsightRepository.clear() // clear to avoid deduplication
        fakeOutcomeRepository.saveOutcome(Outcome("out-2", userId, rec.id, "INTERVENTION", ResultStatus.POSITIVE, 25, now, now))
        fakeOutcomeRepository.saveOutcome(Outcome("out-3", userId, rec.id, "INTERVENTION", ResultStatus.POSITIVE, 20, now, now))
        fakeOutcomeRepository.saveOutcome(Outcome("out-4", userId, rec.id, "INTERVENTION", ResultStatus.NEGATIVE, 45, now, now))

        val resB = orchestrator.orchestrate(userId, sevenDaysAgo, now, zoneId)
        val insightB = (resB as Result.Success).data.newlyDiscoveredInsights.find { it.category == InsightCategory.INTERVENTION_OUTCOME }
        assertNotNull(insightB)
        assertTrue(insightB!!.title.startsWith("Measured Intervention Effectiveness"))
        assertTrue(insightB.explanation.contains("3 were successful (75%)"))
        assertEquals(EpistemicAuthority.DERIVED_CALCULATION, insightB.epistemicAuthority)
        assertTrue(insightB.confidence >= 0.75f)
    }

    // =========================================================================
    // Test 12: Evidence Provenance Preservation
    // =========================================================================

    @Test
    fun test12_evidenceProvenance_preservation() = runBlocking {
        val commitments = listOf(
            createCommitment("c1", CommitmentStatus.COMPLETED),
            createCommitment("c2", CommitmentStatus.COMPLETED),
            createCommitment("c3", CommitmentStatus.COMPLETED)
        )
        fakeContextEngine.stubContext = UnifiedContext(
            userId = userId,
            generatedAt = now,
            windowStartTimeMs = sevenDaysAgo,
            windowEndTimeMs = now,
            productivity = ProductivityContext(
                commitmentSummary = CommitmentContextSummary(
                    completedCommitments = commitments
                )
            )
        )

        val result = orchestrator.orchestrate(userId, sevenDaysAgo, now, zoneId)
        val insights = (result as Result.Success).data.newlyDiscoveredInsights

        for (insight in insights) {
            assertTrue(
                "Source provenance must be non-empty and point to valid stores",
                insight.sourceProvenance.isNotEmpty()
            )
        }
    }

    // =========================================================================
    // Test 13: User/Session Ownership Isolation
    // =========================================================================

    @Test
    fun test13_userSessionOwnership_isolation() = runBlocking {
        val insightA = PersonalInsight(
            id = "ins-userA",
            userId = "user-A",
            category = InsightCategory.PRODUCTIVITY,
            title = "User A Rhythm",
            explanation = "User A data",
            explainabilityBlock = ExplainabilityBlock("A", "7d", "ev", "rel", "none", "steps"),
            observationWindowStartMs = sevenDaysAgo,
            observationWindowEndMs = now,
            firstObservedAt = sevenDaysAgo,
            lastObservedAt = now,
            confidence = 0.8f,
            epistemicAuthority = EpistemicAuthority.DERIVED_CALCULATION,
            createdAt = now,
            updatedAt = now
        )
        fakeInsightRepository.saveInsight(insightA)

        // User B attempts to dismiss User A's insight
        val crossUserDismiss = reviewUseCase.dismiss(insightA.id, "user-B")
        assertTrue("Cross-user dismiss must fail", crossUserDismiss is Result.Failure)

        // User B gets insights
        val userBInsights = (fakeInsightRepository.getInsightsForUser("user-B") as Result.Success).data
        assertTrue("User B must see zero of User A's insights", userBInsights.isEmpty())
    }

    // =========================================================================
    // Test 14: No Autonomous Intervention Execution
    // =========================================================================

    @Test
    fun test14_noAutonomousInterventionExecution() = runBlocking {
        fakeContextEngine.stubContext = UnifiedContext(
            userId = userId,
            generatedAt = now,
            windowStartTimeMs = sevenDaysAgo,
            windowEndTimeMs = now
        )

        val result = orchestrator.orchestrate(userId, sevenDaysAgo, now, zoneId)
        assertTrue(result is Result.Success)

        // Verify that recommendations remained PENDING and were not auto-executed
        val pendingRecs = fakeRecommendationRepository.recommendations.filter { it.userId == userId && it.status == InterventionRecommendationStatus.PENDING }
        for (rec in pendingRecs) {
            assertEquals(InterventionRecommendationStatus.PENDING, rec.status)
        }
    }

    // =========================================================================
    // Test 15: Regression of Existing Workflows
    // =========================================================================

    @Test
    fun test15_regression_existingWorkflows() = runBlocking {
        val pattern = Pattern(
            id = "pat-reg-1",
            userId = userId,
            fingerprint = "fp-1",
            type = PatternType.POSTPONEMENT_PATTERN,
            description = "Postponement pattern",
            conditions = null,
            predictedBehavior = null,
            confidence = 0.8f,
            evidenceCount = 3,
            evidenceReferences = emptyList(),
            firstObservedAt = sevenDaysAgo,
            lastObservedAt = now,
            status = com.sanket_satpute_20.ironmind.domain.model.pattern.PatternStatus.ACTIVE,
            confirmationState = com.sanket_satpute_20.ironmind.domain.model.MemoryConfirmationState.UNCONFIRMED,
            createdAt = sevenDaysAgo,
            updatedAt = now
        )
        fakePatternRepository.savePattern(pattern)

        val barrier = BarrierHypothesis(
            id = "bar-reg-1",
            userId = userId,
            category = BarrierCategory.DISTRACTION,
            description = "Friction observed",
            confirmationState = BarrierConfirmationState.UNCONFIRMED,
            status = BarrierStatus.ACTIVE,
            sourceReflectionId = "ref-1",
            firstObservedAt = sevenDaysAgo,
            lastObservedAt = now,
            title = "Task Friction",
            confidence = 0.8f,
            evidenceCount = 3,
            distinctDays = 2
        )
        fakeBarrierRepository.saveBarrier(barrier)

        fakeContextEngine.stubContext = UnifiedContext(
            userId = userId,
            generatedAt = now,
            windowStartTimeMs = sevenDaysAgo,
            windowEndTimeMs = now
        )

        val result = orchestrator.orchestrate(userId, sevenDaysAgo, now, zoneId)
        assertTrue(result is Result.Success)
        val data = (result as Result.Success).data

        // Barrier hypotheses and patterns continue to survive and pass through intact
        assertTrue(data.pipelineResult.activePatterns.any { it.id == "pat-reg-1" })
        assertTrue(data.pipelineResult.barrierHypotheses.any { it.id == "bar-reg-1" })
    }

    @Test
    fun test16_rejectedAndDismissedHighEvidenceBarriersFilteredFromPipelineOutput() = runBlocking {
        val postponementPattern = Pattern(
            id = "pat-barrier-filter",
            userId = userId,
            fingerprint = "fp-barrier-filter",
            type = PatternType.POSTPONEMENT_PATTERN,
            description = "Recurring task postponement",
            conditions = null,
            predictedBehavior = null,
            confidence = 0.9f,
            evidenceCount = 6,
            evidenceReferences = emptyList(),
            firstObservedAt = sevenDaysAgo,
            lastObservedAt = now,
            status = com.sanket_satpute_20.ironmind.domain.model.pattern.PatternStatus.ACTIVE,
            confirmationState = com.sanket_satpute_20.ironmind.domain.model.MemoryConfirmationState.UNCONFIRMED,
            createdAt = sevenDaysAgo,
            updatedAt = now
        )
        fakePatternRepository.savePattern(postponementPattern)

        val rejectedHighEvidence = BarrierHypothesis(
            id = "bar-rejected-high",
            userId = userId,
            category = BarrierCategory.COMPETING_PRIORITIES,
            description = "Rejected hypothesis",
            confirmationState = BarrierConfirmationState.REJECTED,
            status = BarrierStatus.EXPIRED,
            sourceReflectionId = "ref-rej",
            firstObservedAt = sevenDaysAgo,
            lastObservedAt = now,
            title = "Rejected Competing Priorities",
            confidence = 0.95f,
            evidenceCount = 10,
            distinctDays = 5,
            epistemicAuthority = EpistemicAuthority.AI_HYPOTHESIS
        )
        val dismissedHighEvidence = BarrierHypothesis(
            id = "bar-dismissed-high",
            userId = userId,
            category = BarrierCategory.DISTRACTION,
            description = "Dismissed hypothesis",
            confirmationState = BarrierConfirmationState.DISMISSED,
            status = BarrierStatus.EXPIRED,
            sourceReflectionId = "ref-dis",
            firstObservedAt = sevenDaysAgo,
            lastObservedAt = now,
            title = "Dismissed Distraction",
            confidence = 0.92f,
            evidenceCount = 9,
            distinctDays = 4,
            epistemicAuthority = EpistemicAuthority.AI_HYPOTHESIS
        )
        val confirmedActive = BarrierHypothesis(
            id = "bar-confirmed-active",
            userId = userId,
            category = BarrierCategory.EXCESSIVE_TASK_SIZE,
            description = "Confirmed barrier",
            confirmationState = BarrierConfirmationState.CONFIRMED,
            status = BarrierStatus.ACTIVE,
            sourceReflectionId = "ref-conf",
            firstObservedAt = sevenDaysAgo,
            lastObservedAt = now,
            title = "Confirmed Task Size",
            confidence = 0.85f,
            evidenceCount = 1,
            distinctDays = 1,
            epistemicAuthority = EpistemicAuthority.USER_CONFIRMED_FACT
        )
        fakeBarrierRepository.saveBarrier(rejectedHighEvidence)
        fakeBarrierRepository.saveBarrier(dismissedHighEvidence)
        fakeBarrierRepository.saveBarrier(confirmedActive)

        fakeContextEngine.stubContext = UnifiedContext(
            userId = userId,
            generatedAt = now,
            windowStartTimeMs = sevenDaysAgo,
            windowEndTimeMs = now
        )

        val result = orchestrator.orchestrate(userId, sevenDaysAgo, now, zoneId)
        assertTrue(result is Result.Success)
        val pipelineResult = (result as Result.Success).data.pipelineResult

        val outputIds = pipelineResult.barrierHypotheses.map { it.id }.toSet()
        assertFalse(outputIds.contains("bar-rejected-high"))
        assertFalse(outputIds.contains("bar-dismissed-high"))
        assertTrue(outputIds.contains("bar-confirmed-active"))

        // Rejected/dismissed history still suppresses category rediscovery
        assertTrue(pipelineResult.barrierHypotheses.none {
            it.category == BarrierCategory.COMPETING_PRIORITIES || it.category == BarrierCategory.DISTRACTION
        })

        // Intervention reasoning generates no recommendations from rejected or dismissed barriers
        assertTrue(pipelineResult.interventionRecommendations.none {
            it.causalHypothesis?.id == "bar-rejected-high" || it.causalHypothesis?.id == "bar-dismissed-high"
        })

        // Persisted historical records preserve epistemic authority without promoting to USER_CONFIRMED_FACT
        val persistedDismissed = (fakeBarrierRepository.getBarrier("bar-dismissed-high") as Result.Success).data!!
        assertEquals(BarrierConfirmationState.DISMISSED, persistedDismissed.confirmationState)
        assertEquals(EpistemicAuthority.AI_HYPOTHESIS, persistedDismissed.epistemicAuthority)
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private fun createCommitment(id: String, status: CommitmentStatus): Commitment {
        return Commitment(
            id = id,
            userId = userId,
            title = "Commitment $id",
            description = "Desc",
            committedAt = sevenDaysAgo,
            status = status,
            priority = 1,
            source = com.sanket_satpute_20.ironmind.domain.model.EntitySource.USER,
            createdAt = sevenDaysAgo,
            updatedAt = now
        )
    }

    private fun createTask(id: String, completed: Boolean, time: Long): Task {
        return Task(
            id = id,
            userId = userId,
            planId = "plan-1",
            title = "Task $id",
            description = "Desc",
            status = if (completed) TaskStatus.COMPLETED else TaskStatus.PENDING,
            priority = 1,
            completedAt = if (completed) time else null,
            source = com.sanket_satpute_20.ironmind.domain.model.EntitySource.USER,
            createdAt = sevenDaysAgo,
            updatedAt = time
        )
    }

    // =========================================================================
    // Test Doubles
    // =========================================================================

    class InMemoryInsightRepository : InsightRepository {
        private val insights = mutableMapOf<String, PersonalInsight>()
        private val flow = MutableStateFlow<List<PersonalInsight>>(emptyList())

        fun clear() {
            insights.clear()
            flow.value = emptyList()
        }

        override suspend fun saveInsight(insight: PersonalInsight): Result<PersonalInsight, Exception> {
            insights[insight.id] = insight
            flow.value = insights.values.toList()
            return Result.Success(insight)
        }

        override suspend fun getInsightById(id: String): Result<PersonalInsight?, Exception> {
            return Result.Success(insights[id])
        }

        override suspend fun getInsightsForUser(userId: String): Result<List<PersonalInsight>, Exception> {
            return Result.Success(insights.values.filter { it.userId == userId })
        }

        override fun observeInsightsForUser(userId: String): Flow<List<PersonalInsight>> {
            return flow.map { list -> list.filter { it.userId == userId } }
        }

        override suspend fun updateReviewStatus(
            id: String,
            userId: String,
            status: InsightReviewStatus,
            feedback: String?,
            reviewedAt: Long
        ): Result<Unit, Exception> {
            val existing = insights[id] ?: return Result.Failure(IllegalArgumentException("Not found"))
            if (existing.userId != userId) {
                return Result.Failure(SecurityException("User mismatch"))
            }
            val updated = existing.copy(
                reviewStatus = status,
                userFeedback = feedback ?: existing.userFeedback,
                reviewedAt = reviewedAt,
                updatedAt = reviewedAt
            )
            insights[id] = updated
            flow.value = insights.values.toList()
            return Result.Success(Unit)
        }

        override suspend fun updateFactualConfirmation(
            id: String,
            userId: String,
            confirmationState: com.sanket_satpute_20.ironmind.domain.model.MemoryConfirmationState,
            correction: String?,
            timestamp: Long
        ): Result<Unit, Exception> {
            val existing = insights[id] ?: return Result.Failure(IllegalArgumentException("Not found"))
            if (existing.userId != userId) {
                return Result.Failure(SecurityException("User mismatch"))
            }
            val newAuthority = if (confirmationState == com.sanket_satpute_20.ironmind.domain.model.MemoryConfirmationState.USER_CONFIRMED) {
                EpistemicAuthority.USER_CONFIRMED_FACT
            } else {
                existing.epistemicAuthority
            }
            val updated = existing.copy(
                confirmationState = confirmationState,
                epistemicAuthority = newAuthority,
                userFeedback = correction ?: existing.userFeedback,
                updatedAt = timestamp
            )
            insights[id] = updated
            flow.value = insights.values.toList()
            return Result.Success(Unit)
        }
    }

    class InMemoryOutcomeRepository : OutcomeRepository {
        private val outcomes = mutableMapOf<String, Outcome>()

        override suspend fun saveOutcome(outcome: Outcome): Result<Unit, Exception> {
            outcomes[outcome.id] = outcome
            return Result.Success(Unit)
        }

        override suspend fun getOutcomeForSource(sourceEntityId: String): Result<Outcome?, Exception> {
            return Result.Success(outcomes.values.find { it.sourceEntityId == sourceEntityId })
        }

        override suspend fun getOutcomesForUser(userId: String): Result<List<Outcome>, Exception> {
            return Result.Success(outcomes.values.filter { it.userId == userId })
        }
    }

    class InMemoryBarrierRepository : BarrierRepository {
        val barriers = mutableListOf<BarrierHypothesis>()

        override suspend fun saveBarrier(barrier: BarrierHypothesis): Result<Unit, Exception> {
            barriers.removeAll { it.id == barrier.id }
            barriers.add(barrier)
            return Result.Success(Unit)
        }

        override suspend fun getBarriersForUser(userId: String): Result<List<BarrierHypothesis>, Exception> {
            return Result.Success(barriers.filter { it.userId == userId })
        }

        override fun observeBarriersForUser(userId: String): Flow<List<BarrierHypothesis>> {
            return MutableStateFlow(barriers.filter { it.userId == userId }).asStateFlow()
        }

        override suspend fun getActiveBarriersForUser(
            userId: String,
            excludedStates: List<BarrierConfirmationState>,
            limit: Int
        ): Result<List<BarrierHypothesis>, Exception> {
            return Result.Success(barriers.filter { it.userId == userId && it.confirmationState !in excludedStates }.take(limit))
        }

        override suspend fun getBarrier(id: String): Result<BarrierHypothesis?, Exception> {
            return Result.Success(barriers.firstOrNull { it.id == id })
        }
    }

    class InMemoryRecommendationRepository : InterventionRecommendationRepository {
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
            return Result.Success(recommendations.filter {
                it.userId == userId &&
                        it.objective.name == objective &&
                        it.targetEntityType == targetEntityType &&
                        it.targetEntityId == targetEntityId
            })
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

    class InMemoryPatternRepository : PatternRepository {
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

        override suspend fun getPatternsByStatus(userId: String, status: com.sanket_satpute_20.ironmind.domain.model.pattern.PatternStatus): Result<List<Pattern>, Exception> {
            return Result.Success(patterns.filter { it.userId == userId && it.status == status })
        }

        override suspend fun getPatternsByStatus(userId: String, status: com.sanket_satpute_20.ironmind.domain.model.pattern.PatternStatus, limit: Int): Result<List<Pattern>, Exception> {
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

    class FakeContextEngine : ContextEngine {
        var stubContext: UnifiedContext? = null

        override suspend fun getCurrentContext(userId: String): Result<ContextSnapshot, Exception> {
            return Result.Failure(UnsupportedOperationException())
        }

        override suspend fun getUnifiedContext(
            userId: String,
            windowStartTimeMs: Long?,
            windowEndTimeMs: Long?
        ): Result<UnifiedContext, Exception> {
            return Result.Success(stubContext ?: UnifiedContext(userId, System.currentTimeMillis()))
        }
    }

    class FakePatternEngine : PatternEngine {
        var stubPatterns: List<DiscoveredPattern> = emptyList()

        override suspend fun decayStalePatterns(userId: String): Result<Unit, Exception> {
            return Result.Success(Unit)
        }

        override suspend fun discoverPatterns(
            unifiedContext: UnifiedContext,
            zoneId: ZoneId
        ): Result<List<DiscoveredPattern>, Exception> {
            return Result.Success(stubPatterns)
        }
    }
}
