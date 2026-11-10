package com.sanket_satpute_20.ironmind.domain.engine

import com.sanket_satpute_20.ironmind.domain.ai.BarrierCategory
import com.sanket_satpute_20.ironmind.domain.ai.InterventionObjective
import com.sanket_satpute_20.ironmind.domain.ai.InterventionType
import com.sanket_satpute_20.ironmind.domain.model.Commitment
import com.sanket_satpute_20.ironmind.domain.model.CommitmentStatus
import com.sanket_satpute_20.ironmind.domain.model.DueTaskItem
import com.sanket_satpute_20.ironmind.domain.model.EntitySource
import com.sanket_satpute_20.ironmind.domain.model.Goal
import com.sanket_satpute_20.ironmind.domain.model.GoalStatus
import com.sanket_satpute_20.ironmind.domain.model.MemoryConfirmationState
import com.sanket_satpute_20.ironmind.domain.model.Plan
import com.sanket_satpute_20.ironmind.domain.model.PlanStatus
import com.sanket_satpute_20.ironmind.domain.model.Task
import com.sanket_satpute_20.ironmind.domain.model.TaskStatus
import com.sanket_satpute_20.ironmind.domain.model.action.ActionCategory
import com.sanket_satpute_20.ironmind.domain.model.action.ActionExecutionResult
import com.sanket_satpute_20.ironmind.domain.model.action.ActionReviewStatus
import com.sanket_satpute_20.ironmind.domain.model.action.ProposedAction
import com.sanket_satpute_20.ironmind.domain.model.barrier.BarrierConfirmationState
import com.sanket_satpute_20.ironmind.domain.model.barrier.BarrierHypothesis
import com.sanket_satpute_20.ironmind.domain.model.barrier.BarrierStatus
import com.sanket_satpute_20.ironmind.domain.model.briefing.AttentionReason
import com.sanket_satpute_20.ironmind.domain.model.context.AppUsageSummary
import com.sanket_satpute_20.ironmind.domain.model.context.CrossDomainCorrelation
import com.sanket_satpute_20.ironmind.domain.model.context.CrossDomainCorrelationType
import com.sanket_satpute_20.ironmind.domain.model.context.DigitalBehaviourContext
import com.sanket_satpute_20.ironmind.domain.model.context.EpistemicAuthority
import com.sanket_satpute_20.ironmind.domain.model.context.ProductivityContext
import com.sanket_satpute_20.ironmind.domain.model.context.SourceAvailability
import com.sanket_satpute_20.ironmind.domain.model.context.SourceFreshness
import com.sanket_satpute_20.ironmind.domain.model.context.UnifiedContext
import com.sanket_satpute_20.ironmind.domain.model.context.UsagePermissionStatus
import com.sanket_satpute_20.ironmind.domain.model.insight.ExplainabilityBlock
import com.sanket_satpute_20.ironmind.domain.model.insight.InsightCategory
import com.sanket_satpute_20.ironmind.domain.model.insight.InsightReviewStatus
import com.sanket_satpute_20.ironmind.domain.model.insight.PersonalInsight
import com.sanket_satpute_20.ironmind.domain.model.intervention.InterventionRecommendation
import com.sanket_satpute_20.ironmind.domain.model.intervention.InterventionRecommendationStatus
import com.sanket_satpute_20.ironmind.domain.model.pattern.Pattern
import com.sanket_satpute_20.ironmind.domain.model.pattern.PatternStatus
import com.sanket_satpute_20.ironmind.domain.model.pattern.PatternType
import com.sanket_satpute_20.ironmind.testutil.fake.FakeClock
import com.sanket_satpute_20.ironmind.testutil.fake.FakeIdGenerator
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Dedicated unit tests for [DailyBriefingEngine], verifying briefing construction,
 * empty/incomplete context handling, plan attention rules, pattern/barrier epistemic
 * authority preservation, insight filtering and ranking, existing action retention,
 * determinism, dependency failure propagation, and sequential user isolation.
 */
class DailyBriefingEngineTest {

    private val userId = "user-briefing-test"
    private val now = 1_700_000_000_000L
    private val sevenDaysMs = 7L * 24 * 60 * 60 * 1000L

    private lateinit var clock: FakeClock
    private lateinit var idGenerator: FakeIdGenerator
    private lateinit var actionPlanningEngine: ActionPlanningEngine
    private lateinit var engine: DailyBriefingEngine

    @Before
    fun setUp() {
        clock = FakeClock(now)
        idGenerator = FakeIdGenerator("act_")
        actionPlanningEngine = ActionPlanningEngine(clock, idGenerator)
        engine = DailyBriefingEngine(actionPlanningEngine, clock)
    }

    @Test
    fun `empty context produces clear schedule summary and empty collections`() {
        val briefing = engine.assembleBriefing(
            userId = userId,
            unifiedContext = buildContext(),
            overdueTasks = emptyList(),
            dueTodayTasks = emptyList(),
            activeCommitments = emptyList(),
            goals = emptyList(),
            plans = emptyList(),
            patterns = emptyList(),
            barrierHypotheses = emptyList(),
            recommendations = emptyList(),
            insights = emptyList()
        )

        assertEquals(userId, briefing.userId)
        assertEquals(now, briefing.generatedAt)
        assertEquals(
            "Schedule is clear: No overdue tasks or pressing commitments for today.",
            briefing.attentionSummary
        )
        assertTrue(briefing.overdueTasks.isEmpty())
        assertTrue(briefing.dueTodayTasks.isEmpty())
        assertTrue(briefing.activeCommitments.isEmpty())
        assertTrue(briefing.goalsAndPlansNeedingAttention.isEmpty())
        assertTrue(briefing.discoveredPatterns.isEmpty())
        assertTrue(briefing.evidenceBackedBarriers.isEmpty())
        assertTrue(briefing.proposedNextActions.isEmpty())
        assertTrue(briefing.relevantInsights.isEmpty())
        assertEquals(
            "No app usage data recorded yet for the observation period.",
            briefing.dataAvailabilityNote
        )
    }

    @Test
    fun `data availability note reflects permission status and recorded app usage`() {
        val deniedBriefing = engine.assembleBriefing(
            userId = userId,
            unifiedContext = buildContext(permissionStatus = UsagePermissionStatus.DENIED),
            overdueTasks = emptyList(),
            dueTodayTasks = emptyList(),
            activeCommitments = emptyList(),
            goals = emptyList(),
            plans = emptyList(),
            patterns = emptyList(),
            barrierHypotheses = emptyList(),
            recommendations = emptyList(),
            insights = emptyList()
        )
        assertEquals(
            "Usage Access permission is not granted. Digital phone behaviour context is currently unavailable.",
            deniedBriefing.dataAvailabilityNote
        )

        val unavailableBriefing = engine.assembleBriefing(
            userId = userId,
            unifiedContext = buildContext(permissionStatus = UsagePermissionStatus.UNAVAILABLE),
            overdueTasks = emptyList(),
            dueTodayTasks = emptyList(),
            activeCommitments = emptyList(),
            goals = emptyList(),
            plans = emptyList(),
            patterns = emptyList(),
            barrierHypotheses = emptyList(),
            recommendations = emptyList(),
            insights = emptyList()
        )
        assertEquals(
            "Usage Access permission is not granted. Digital phone behaviour context is currently unavailable.",
            unavailableBriefing.dataAvailabilityNote
        )

        val emptyUsageBriefing = engine.assembleBriefing(
            userId = userId,
            unifiedContext = buildContext(permissionStatus = UsagePermissionStatus.GRANTED, appUsages = emptyList()),
            overdueTasks = emptyList(),
            dueTodayTasks = emptyList(),
            activeCommitments = emptyList(),
            goals = emptyList(),
            plans = emptyList(),
            patterns = emptyList(),
            barrierHypotheses = emptyList(),
            recommendations = emptyList(),
            insights = emptyList()
        )
        assertEquals(
            "No app usage data recorded yet for the observation period.",
            emptyUsageBriefing.dataAvailabilityNote
        )

        val usage = AppUsageSummary(
            packageName = "com.example.focus",
            appName = "Focus App",
            totalDurationMs = 900_000L,
            sessionCount = 3,
            firstObservedAt = now - 3_600_000L,
            lastObservedAt = now - 60_000L
        )
        val grantedWithDataBriefing = engine.assembleBriefing(
            userId = userId,
            unifiedContext = buildContext(permissionStatus = UsagePermissionStatus.GRANTED, appUsages = listOf(usage)),
            overdueTasks = emptyList(),
            dueTodayTasks = emptyList(),
            activeCommitments = emptyList(),
            goals = emptyList(),
            plans = emptyList(),
            patterns = emptyList(),
            barrierHypotheses = emptyList(),
            recommendations = emptyList(),
            insights = emptyList()
        )
        assertNull(grantedWithDataBriefing.dataAvailabilityNote)
    }

    @Test
    fun `plan attention handles overdue preference, strict 7-day boundary, and inactive plans`() {
        val overduePlan = buildPlan(
            id = "plan-overdue-and-old",
            title = "Overdue Plan",
            status = PlanStatus.ACTIVE,
            updatedAt = now - sevenDaysMs - 10_000L
        )
        val exactSevenDayPlan = buildPlan(
            id = "plan-boundary-7d",
            title = "Exact 7d Plan",
            status = PlanStatus.ACTIVE,
            updatedAt = now - sevenDaysMs
        )
        val stalledPlan = buildPlan(
            id = "plan-stalled",
            title = "Stalled Plan",
            status = PlanStatus.ACTIVE,
            updatedAt = now - sevenDaysMs - 1L
        )
        val pausedOldPlan = buildPlan(
            id = "plan-paused",
            title = "Paused Plan",
            status = PlanStatus.PAUSED,
            updatedAt = now - (30L * 24 * 60 * 60 * 1000L)
        )
        val completedPlanWithOverdue = buildPlan(
            id = "plan-completed",
            title = "Completed Plan",
            status = PlanStatus.COMPLETED,
            updatedAt = now - (30L * 24 * 60 * 60 * 1000L)
        )

        val overdueItems = listOf(
            DueTaskItem(task = buildTask("t1", planId = overduePlan.id, dueAt = now - 5_000L), isOverdue = true),
            DueTaskItem(task = buildTask("t2", planId = overduePlan.id, dueAt = now - 4_000L), isOverdue = true),
            DueTaskItem(task = buildTask("t3", planId = completedPlanWithOverdue.id, dueAt = now - 3_000L), isOverdue = true)
        )

        val goal = Goal(
            id = "goal-1",
            userId = userId,
            title = "Health Goal",
            description = "Improve sleep",
            why = "Energy",
            importance = 5,
            status = GoalStatus.ACTIVE,
            createdAt = now - (40L * 24 * 60 * 60 * 1000L),
            updatedAt = now - (40L * 24 * 60 * 60 * 1000L)
        )

        val briefing = engine.assembleBriefing(
            userId = userId,
            unifiedContext = buildContext(),
            overdueTasks = overdueItems,
            dueTodayTasks = emptyList(),
            activeCommitments = emptyList(),
            goals = listOf(goal),
            plans = listOf(overduePlan, exactSevenDayPlan, stalledPlan, pausedOldPlan, completedPlanWithOverdue),
            patterns = emptyList(),
            barrierHypotheses = emptyList(),
            recommendations = emptyList(),
            insights = emptyList()
        )

        assertEquals(2, briefing.goalsAndPlansNeedingAttention.size)
        val overdueAttn = briefing.goalsAndPlansNeedingAttention.first { it.entityId == overduePlan.id }
        assertEquals(AttentionReason.OVERDUE_TASKS_PRESENT, overdueAttn.reason)
        assertEquals("PLAN", overdueAttn.entityType)
        assertEquals(2, overdueAttn.overdueTasks)

        val stalledAttn = briefing.goalsAndPlansNeedingAttention.first { it.entityId == stalledPlan.id }
        assertEquals(AttentionReason.STALLED_PLAN, stalledAttn.reason)
        assertEquals(0, stalledAttn.overdueTasks)

        assertTrue(briefing.goalsAndPlansNeedingAttention.none { it.entityId == exactSevenDayPlan.id })
        assertTrue(briefing.goalsAndPlansNeedingAttention.none { it.entityId == pausedOldPlan.id })
        assertTrue(briefing.goalsAndPlansNeedingAttention.none { it.entityId == completedPlanWithOverdue.id })
        assertTrue(briefing.goalsAndPlansNeedingAttention.none { it.entityId == goal.id })

        assertEquals(
            "Today's Focus: 3 overdue task(s), 2 plan(s) need attention.",
            briefing.attentionSummary
        )
    }

    @Test
    fun `discovered patterns preserve epistemic distinction without promoting unconfirmed patterns`() {
        val confirmedPattern = buildPattern(
            id = "pat-confirmed",
            type = PatternType.FOCUS_PATTERN,
            description = "Morning focus blocks complete 85% of tasks",
            confidence = 0.88f,
            evidenceCount = 6,
            confirmationState = MemoryConfirmationState.USER_CONFIRMED
        )
        val unconfirmedPattern = buildPattern(
            id = "pat-unconfirmed",
            type = PatternType.POSTPONEMENT_PATTERN,
            description = "Tasks above 90m are postponed late afternoon",
            confidence = 0.72f,
            evidenceCount = 4,
            confirmationState = MemoryConfirmationState.UNCONFIRMED
        )
        val systemConfirmedPattern = buildPattern(
            id = "pat-sys",
            type = PatternType.TIME_PATTERN,
            description = "Peak completion between 9am and 11am",
            confidence = 0.79f,
            evidenceCount = 5,
            confirmationState = MemoryConfirmationState.SYSTEM_CONFIRMED
        )

        val briefing = engine.assembleBriefing(
            userId = userId,
            unifiedContext = buildContext(),
            overdueTasks = emptyList(),
            dueTodayTasks = emptyList(),
            activeCommitments = emptyList(),
            goals = emptyList(),
            plans = emptyList(),
            patterns = listOf(confirmedPattern, unconfirmedPattern, systemConfirmedPattern),
            barrierHypotheses = emptyList(),
            recommendations = emptyList(),
            insights = emptyList()
        )

        assertEquals(3, briefing.discoveredPatterns.size)

        val itemConfirmed = briefing.discoveredPatterns.first { it.patternId == "pat-confirmed" }
        assertEquals(EpistemicAuthority.USER_CONFIRMED_FACT, itemConfirmed.epistemicAuthority)
        assertTrue(itemConfirmed.isUserConfirmed)
        assertEquals(6, itemConfirmed.occurrences)
        assertEquals(0.88f, itemConfirmed.confidence, 0.0001f)
        assertEquals("FOCUS_PATTERN", itemConfirmed.patternType)
        assertEquals(SourceFreshness.CURRENT, itemConfirmed.freshness)

        val itemUnconfirmed = briefing.discoveredPatterns.first { it.patternId == "pat-unconfirmed" }
        assertEquals(EpistemicAuthority.DERIVED_CALCULATION, itemUnconfirmed.epistemicAuthority)
        assertFalse(itemUnconfirmed.isUserConfirmed)

        val itemSystem = briefing.discoveredPatterns.first { it.patternId == "pat-sys" }
        assertEquals(EpistemicAuthority.DERIVED_CALCULATION, itemSystem.epistemicAuthority)
        assertFalse(itemSystem.isUserConfirmed)
    }

    @Test
    fun `barriers suppress low-evidence unconfirmed hypotheses and distinguish AI hypothesis vs confirmed fact`() {
        val lowEvidenceUnconfirmed = buildBarrier(
            id = "bar-low-unconf",
            evidenceCount = 1,
            confirmationState = BarrierConfirmationState.UNCONFIRMED
        )
        val zeroEvidenceUnconfirmed = buildBarrier(
            id = "bar-zero-unconf",
            evidenceCount = 0,
            confirmationState = BarrierConfirmationState.UNCONFIRMED
        )
        val enoughEvidenceUnconfirmed = buildBarrier(
            id = "bar-ev2-unconf",
            evidenceCount = 2,
            confirmationState = BarrierConfirmationState.UNCONFIRMED,
            confidence = 0.81f
        )
        val singleEvidenceConfirmed = buildBarrier(
            id = "bar-low-conf",
            evidenceCount = 1,
            confirmationState = BarrierConfirmationState.CONFIRMED,
            confidence = 0.92f
        )

        val briefing = engine.assembleBriefing(
            userId = userId,
            unifiedContext = buildContext(),
            overdueTasks = emptyList(),
            dueTodayTasks = emptyList(),
            activeCommitments = emptyList(),
            goals = emptyList(),
            plans = emptyList(),
            patterns = emptyList(),
            barrierHypotheses = listOf(
                lowEvidenceUnconfirmed,
                zeroEvidenceUnconfirmed,
                enoughEvidenceUnconfirmed,
                singleEvidenceConfirmed
            ),
            recommendations = emptyList(),
            insights = emptyList()
        )

        assertEquals(2, briefing.evidenceBackedBarriers.size)
        assertTrue(briefing.evidenceBackedBarriers.none { it.barrierId == "bar-low-unconf" })
        assertTrue(briefing.evidenceBackedBarriers.none { it.barrierId == "bar-zero-unconf" })

        val aiHypothesisItem = briefing.evidenceBackedBarriers.first { it.barrierId == "bar-ev2-unconf" }
        assertFalse(aiHypothesisItem.isUserConfirmed)
        assertEquals(EpistemicAuthority.AI_HYPOTHESIS, aiHypothesisItem.epistemicAuthority)
        assertEquals(2, aiHypothesisItem.supportingEvidenceCount)

        val confirmedItem = briefing.evidenceBackedBarriers.first { it.barrierId == "bar-low-conf" }
        assertTrue(confirmedItem.isUserConfirmed)
        assertEquals(EpistemicAuthority.USER_CONFIRMED_FACT, confirmedItem.epistemicAuthority)
        assertEquals(1, confirmedItem.supportingEvidenceCount)
    }

    @Test
    fun `relevant insights exclude dismissed entries and sort strictly by epistemic authority then confidence`() {
        val dismissedTop = buildInsight(
            id = "ins-dismissed",
            authority = EpistemicAuthority.USER_CONFIRMED_FACT,
            confidence = 0.99f,
            reviewStatus = InsightReviewStatus.DISMISSED
        )
        val aiHighConf = buildInsight(
            id = "ins-ai",
            authority = EpistemicAuthority.AI_HYPOTHESIS,
            confidence = 0.95f,
            reviewStatus = InsightReviewStatus.UNREVIEWED
        )
        val userConfirmedMidConf = buildInsight(
            id = "ins-user",
            authority = EpistemicAuthority.USER_CONFIRMED_FACT,
            confidence = 0.60f,
            reviewStatus = InsightReviewStatus.USEFUL
        )
        val derivedLowConf = buildInsight(
            id = "ins-derived-low",
            authority = EpistemicAuthority.DERIVED_CALCULATION,
            confidence = 0.70f,
            reviewStatus = InsightReviewStatus.REVIEW_LATER
        )
        val systemObserved = buildInsight(
            id = "ins-sys",
            authority = EpistemicAuthority.SYSTEM_RECORDED_OBSERVATION,
            confidence = 0.50f,
            reviewStatus = InsightReviewStatus.UNREVIEWED
        )
        val derivedHighConf = buildInsight(
            id = "ins-derived-high",
            authority = EpistemicAuthority.DERIVED_CALCULATION,
            confidence = 0.90f,
            reviewStatus = InsightReviewStatus.UNREVIEWED
        )

        val briefing = engine.assembleBriefing(
            userId = userId,
            unifiedContext = buildContext(),
            overdueTasks = emptyList(),
            dueTodayTasks = emptyList(),
            activeCommitments = emptyList(),
            goals = emptyList(),
            plans = emptyList(),
            patterns = emptyList(),
            barrierHypotheses = emptyList(),
            recommendations = emptyList(),
            insights = listOf(
                aiHighConf,
                dismissedTop,
                derivedLowConf,
                systemObserved,
                userConfirmedMidConf,
                derivedHighConf
            )
        )

        assertEquals(
            listOf("ins-user", "ins-sys", "ins-derived-high", "ins-derived-low", "ins-ai"),
            briefing.relevantInsights.map { it.id }
        )
        val aiResult = briefing.relevantInsights.last()
        assertEquals(EpistemicAuthority.AI_HYPOTHESIS, aiResult.epistemicAuthority)
    }

    @Test
    fun `correlations in unified context do not create causal claims or promote hypotheses to facts`() {
        val correlation = CrossDomainCorrelation(
            id = "corr-1",
            title = "Evening app usage coinciding with pending task",
            description = "Social app used for 30m while task remained pending",
            productivityEntityId = "task-1",
            productivityEntityType = "TASK",
            productivityEntityTitle = "Write tests",
            digitalAppPackage = "com.social.app",
            digitalUsageDurationMs = 1_800_000L,
            observedWindowStartMs = now - 3_600_000L,
            observedWindowEndMs = now,
            correlationType = CrossDomainCorrelationType.HIGH_USAGE_COINCIDING_WITH_MISSED_COMMITMENT,
            confidence = 0.85f
        )
        val unconfirmedBarrier = buildBarrier(
            id = "bar-corr",
            evidenceCount = 3,
            confirmationState = BarrierConfirmationState.UNCONFIRMED
        )
        val aiInsight = buildInsight(
            id = "ins-corr",
            authority = EpistemicAuthority.AI_HYPOTHESIS,
            confidence = 0.75f
        )

        val briefing = engine.assembleBriefing(
            userId = userId,
            unifiedContext = buildContext(correlations = listOf(correlation)),
            overdueTasks = emptyList(),
            dueTodayTasks = emptyList(),
            activeCommitments = listOf(buildCommitment("com-1")),
            goals = emptyList(),
            plans = emptyList(),
            patterns = emptyList(),
            barrierHypotheses = listOf(unconfirmedBarrier),
            recommendations = emptyList(),
            insights = listOf(aiInsight)
        )

        assertEquals("Today's Focus: 1 active commitment(s).", briefing.attentionSummary)
        assertFalse(briefing.attentionSummary.contains("because", ignoreCase = true))
        assertFalse(briefing.attentionSummary.contains("caused", ignoreCase = true))
        assertTrue(briefing.discoveredPatterns.isEmpty())
        assertEquals(1, briefing.evidenceBackedBarriers.size)
        assertFalse(briefing.evidenceBackedBarriers[0].isUserConfirmed)
        assertEquals(EpistemicAuthority.AI_HYPOTHESIS, briefing.evidenceBackedBarriers[0].epistemicAuthority)
        assertEquals(EpistemicAuthority.AI_HYPOTHESIS, briefing.relevantInsights[0].epistemicAuthority)
    }

    @Test
    fun `existing active actions are retained, terminal actions excluded, and duplicate ids suppressed`() {
        val taskAccepted = buildTask("task-acc", dueAt = now - 10_000L)
        val taskRejected = buildTask("task-rej", dueAt = now - 10_000L)
        val taskNewOverdue = buildTask("task-new", dueAt = now - 10_000L)

        val acceptedMatchingDeterministicId = buildAction(
            id = "act_reschedule_${taskAccepted.id}",
            category = ActionCategory.TASK_RESCHEDULE,
            status = ActionReviewStatus.ACCEPTED,
            targetEntityId = taskAccepted.id
        )
        val proposedExisting = buildAction(
            id = "act-prop-keep",
            status = ActionReviewStatus.PROPOSED
        )
        val executingExisting = buildAction(
            id = "act-exec-keep",
            status = ActionReviewStatus.EXECUTING
        )
        val failedExisting = buildAction(
            id = "act-fail-keep",
            status = ActionReviewStatus.EXECUTION_FAILED
        )
        val executedAwaitingOutcome = buildAction(
            id = "act-await-outcome",
            status = ActionReviewStatus.EXECUTED,
            outcomeId = null
        )
        val executedWithOutcome = buildAction(
            id = "act-done",
            status = ActionReviewStatus.EXECUTED,
            outcomeId = "out-1"
        )
        val rejectedAction = buildAction(
            id = "act-rej-drop",
            category = ActionCategory.TASK_RESCHEDULE,
            status = ActionReviewStatus.REJECTED,
            targetEntityId = taskRejected.id
        )
        val dismissedInsightAction = buildAction(
            id = "act-ins-drop",
            category = ActionCategory.FOCUS_PROTECTION,
            status = ActionReviewStatus.DISMISSED,
            sourceInsightId = "ins-suppressed"
        )
        val deferredRecAction = buildAction(
            id = "act-rec-drop",
            category = ActionCategory.COMMITMENT_REVIEW,
            status = ActionReviewStatus.DEFERRED,
            sourceRecommendationId = "rec-suppressed"
        )

        val suppressedInsight = buildInsight(
            id = "ins-suppressed",
            category = InsightCategory.FOCUS,
            authority = EpistemicAuthority.DERIVED_CALCULATION,
            confidence = 0.80f
        )
        val suppressedRec = buildRecommendation(
            id = "rec-suppressed",
            type = InterventionType.REDIRECT
        )

        val briefing = engine.assembleBriefing(
            userId = userId,
            unifiedContext = buildContext(),
            overdueTasks = listOf(
                DueTaskItem(task = taskAccepted, isOverdue = true),
                DueTaskItem(task = taskRejected, isOverdue = true),
                DueTaskItem(task = taskNewOverdue, isOverdue = true)
            ),
            dueTodayTasks = emptyList(),
            activeCommitments = emptyList(),
            goals = emptyList(),
            plans = emptyList(),
            patterns = emptyList(),
            barrierHypotheses = emptyList(),
            recommendations = listOf(suppressedRec),
            insights = listOf(suppressedInsight),
            existingActions = listOf(
                acceptedMatchingDeterministicId,
                proposedExisting,
                executingExisting,
                failedExisting,
                executedAwaitingOutcome,
                executedWithOutcome,
                rejectedAction,
                dismissedInsightAction,
                deferredRecAction
            )
        )

        val resultIds = briefing.proposedNextActions.map { it.id }

        // Active existing actions kept
        assertTrue(resultIds.contains("act_reschedule_${taskAccepted.id}"))
        assertTrue(resultIds.contains("act-prop-keep"))
        assertTrue(resultIds.contains("act-exec-keep"))
        assertTrue(resultIds.contains("act-fail-keep"))
        assertTrue(resultIds.contains("act-await-outcome"))

        // Existing ACCEPTED action not reset to PROPOSED or duplicated
        val keptAccepted = briefing.proposedNextActions.filter { it.id == "act_reschedule_${taskAccepted.id}" }
        assertEquals(1, keptAccepted.size)
        assertEquals(ActionReviewStatus.ACCEPTED, keptAccepted[0].reviewStatus)

        // Terminal/deferred existing actions excluded from active view
        assertFalse(resultIds.contains("act-done"))
        assertFalse(resultIds.contains("act-rej-drop"))
        assertFalse(resultIds.contains("act-ins-drop"))
        assertFalse(resultIds.contains("act-rec-drop"))

        // Rejected/dismissed/deferred history still prevented regeneration for those entities
        assertFalse(resultIds.contains("act_reschedule_${taskRejected.id}"))
        assertFalse(resultIds.contains("act_ins_${suppressedInsight.id}"))
        assertFalse(resultIds.contains("act_rec_${suppressedRec.id}"))

        // Unseen overdue task generates a fresh PROPOSED action
        val freshAction = briefing.proposedNextActions.firstOrNull { it.id == "act_reschedule_${taskNewOverdue.id}" }
        assertNotNull(freshAction)
        assertEquals(ActionReviewStatus.PROPOSED, freshAction!!.reviewStatus)
    }

    @Test
    fun `equivalent inputs with fixed clock produce identical deterministic briefings`() {
        val task = buildTask("task-det", dueAt = now - 10_000L)
        val plan = buildPlan("plan-det", status = PlanStatus.ACTIVE, updatedAt = now - 1_000L)
        val pattern = buildPattern("pat-det", confirmationState = MemoryConfirmationState.UNCONFIRMED)
        val barrier = buildBarrier("bar-det", evidenceCount = 2, confirmationState = BarrierConfirmationState.UNCONFIRMED)
        val insight = buildInsight("ins-det", authority = EpistemicAuthority.DERIVED_CALCULATION, confidence = 0.8f)

        val first = engine.assembleBriefing(
            userId = userId,
            unifiedContext = buildContext(),
            overdueTasks = listOf(DueTaskItem(task = task, isOverdue = true)),
            dueTodayTasks = emptyList(),
            activeCommitments = emptyList(),
            goals = emptyList(),
            plans = listOf(plan),
            patterns = listOf(pattern),
            barrierHypotheses = listOf(barrier),
            recommendations = emptyList(),
            insights = listOf(insight)
        )

        val second = engine.assembleBriefing(
            userId = userId,
            unifiedContext = buildContext(),
            overdueTasks = listOf(DueTaskItem(task = task, isOverdue = true)),
            dueTodayTasks = emptyList(),
            activeCommitments = emptyList(),
            goals = emptyList(),
            plans = listOf(plan),
            patterns = listOf(pattern),
            barrierHypotheses = listOf(barrier),
            recommendations = emptyList(),
            insights = listOf(insight)
        )

        assertEquals(first, second)
    }

    @Test(expected = IllegalStateException::class)
    fun `failure in ActionPlanningEngine propagates cleanly without partial return`() {
        val failingPlanner = mockk<ActionPlanningEngine>()
        every {
            failingPlanner.planActions(any(), any(), any(), any(), any(), any(), any(), any())
        } throws IllegalStateException("Action planning error")

        val failingEngine = DailyBriefingEngine(failingPlanner, clock)
        failingEngine.assembleBriefing(
            userId = userId,
            unifiedContext = buildContext(),
            overdueTasks = emptyList(),
            dueTodayTasks = emptyList(),
            activeCommitments = emptyList(),
            goals = emptyList(),
            plans = emptyList(),
            patterns = emptyList(),
            barrierHypotheses = emptyList(),
            recommendations = emptyList(),
            insights = emptyList()
        )
    }

    @Test
    fun `sequential user briefings isolate userId and planned actions without state bleed`() {
        val userATask = buildTask("task-a", ownerId = "user-alpha", dueAt = now - 5_000L)
        val userBTask = buildTask("task-b", ownerId = "user-beta", dueAt = now - 5_000L)

        val briefingA = engine.assembleBriefing(
            userId = "user-alpha",
            unifiedContext = buildContext(ownerId = "user-alpha"),
            overdueTasks = listOf(DueTaskItem(task = userATask, isOverdue = true)),
            dueTodayTasks = emptyList(),
            activeCommitments = listOf(buildCommitment("com-a", ownerId = "user-alpha")),
            goals = emptyList(),
            plans = emptyList(),
            patterns = emptyList(),
            barrierHypotheses = emptyList(),
            recommendations = emptyList(),
            insights = emptyList()
        )

        val briefingB = engine.assembleBriefing(
            userId = "user-beta",
            unifiedContext = buildContext(ownerId = "user-beta"),
            overdueTasks = listOf(DueTaskItem(task = userBTask, isOverdue = true)),
            dueTodayTasks = emptyList(),
            activeCommitments = emptyList(),
            goals = emptyList(),
            plans = emptyList(),
            patterns = emptyList(),
            barrierHypotheses = emptyList(),
            recommendations = emptyList(),
            insights = emptyList()
        )

        assertEquals("user-alpha", briefingA.userId)
        assertTrue(briefingA.proposedNextActions.isNotEmpty())
        assertTrue(briefingA.proposedNextActions.all { it.userId == "user-alpha" })

        assertEquals("user-beta", briefingB.userId)
        assertTrue(briefingB.activeCommitments.isEmpty())
        assertTrue(briefingB.proposedNextActions.isNotEmpty())
        assertTrue(briefingB.proposedNextActions.all { it.userId == "user-beta" })
        assertTrue(briefingB.proposedNextActions.none { it.targetEntityId == "task-a" })
    }

    // -------------------------------------------------------------------------
    // Local Test Helpers
    // -------------------------------------------------------------------------

    private fun buildContext(
        ownerId: String = userId,
        permissionStatus: UsagePermissionStatus = UsagePermissionStatus.GRANTED,
        appUsages: List<AppUsageSummary> = emptyList(),
        correlations: List<CrossDomainCorrelation> = emptyList()
    ) = UnifiedContext(
        userId = ownerId,
        generatedAt = now,
        windowStartTimeMs = now - sevenDaysMs,
        windowEndTimeMs = now,
        productivity = ProductivityContext(),
        digitalBehaviour = DigitalBehaviourContext(
            permissionStatus = permissionStatus,
            availability = SourceAvailability.AVAILABLE,
            appUsages = appUsages
        ),
        correlations = correlations
    )

    private fun buildTask(
        id: String,
        ownerId: String = userId,
        planId: String? = "plan-1",
        dueAt: Long? = null
    ) = Task(
        id = id,
        userId = ownerId,
        goalId = "goal-1",
        planId = planId,
        title = "Task $id",
        description = "Desc $id",
        status = TaskStatus.PENDING,
        priority = 2,
        estimatedDurationMinutes = 30,
        scheduledAt = null,
        dueAt = dueAt,
        createdAt = now - 86_400_000L,
        updatedAt = now - 86_400_000L,
        source = EntitySource.USER
    )

    private fun buildCommitment(id: String, ownerId: String = userId) = Commitment(
        id = id,
        userId = ownerId,
        goalId = null,
        planId = null,
        taskId = null,
        title = "Commitment $id",
        description = "Desc",
        status = CommitmentStatus.COMMITTED,
        priority = 2,
        committedAt = now,
        source = EntitySource.USER,
        createdAt = now,
        updatedAt = now
    )

    private fun buildPlan(
        id: String,
        title: String = "Plan $id",
        status: PlanStatus = PlanStatus.ACTIVE,
        updatedAt: Long = now - 1_000L
    ) = Plan(
        id = id,
        userId = userId,
        goalId = "goal-1",
        title = title,
        description = "Plan Desc",
        status = status,
        createdAt = now - 30L * 86_400_000L,
        updatedAt = updatedAt,
        source = EntitySource.USER
    )

    private fun buildPattern(
        id: String,
        type: PatternType = PatternType.POSTPONEMENT_PATTERN,
        description: String = "Pattern $id",
        confidence: Float = 0.75f,
        evidenceCount: Int = 3,
        confirmationState: MemoryConfirmationState = MemoryConfirmationState.UNCONFIRMED
    ) = Pattern(
        id = id,
        userId = userId,
        fingerprint = "fp-$id",
        type = type,
        description = description,
        conditions = null,
        predictedBehavior = null,
        confidence = confidence,
        evidenceCount = evidenceCount,
        evidenceReferences = emptyList(),
        firstObservedAt = now - sevenDaysMs,
        lastObservedAt = now,
        status = PatternStatus.ACTIVE,
        confirmationState = confirmationState,
        createdAt = now - sevenDaysMs,
        updatedAt = now
    )

    private fun buildBarrier(
        id: String,
        evidenceCount: Int,
        confirmationState: BarrierConfirmationState,
        confidence: Float = 0.75f
    ) = BarrierHypothesis(
        id = id,
        userId = userId,
        category = BarrierCategory.DISTRACTION,
        description = "Barrier $id",
        confirmationState = confirmationState,
        status = BarrierStatus.ACTIVE,
        sourceReflectionId = "ref-1",
        firstObservedAt = now - sevenDaysMs,
        lastObservedAt = now,
        evidenceCount = evidenceCount,
        confidence = confidence
    )

    private fun buildInsight(
        id: String,
        category: InsightCategory = InsightCategory.PRODUCTIVITY,
        authority: EpistemicAuthority = EpistemicAuthority.DERIVED_CALCULATION,
        confidence: Float = 0.8f,
        reviewStatus: InsightReviewStatus = InsightReviewStatus.UNREVIEWED
    ) = PersonalInsight(
        id = id,
        userId = userId,
        category = category,
        title = "Insight $id",
        explanation = "Explanation $id",
        explainabilityBlock = ExplainabilityBlock(
            whatWasObserved = "Observed behavior",
            observationPeriod = "Last 7 days",
            supportingEvidenceDescription = "Multiple logs",
            whyItMatters = "Planning impact",
            uncertaintyOrLimitations = "Correlation only",
            suggestedNextSteps = "Review workload"
        ),
        supportingEvidenceIds = listOf("ev-1", "ev-2"),
        observationWindowStartMs = now - sevenDaysMs,
        observationWindowEndMs = now,
        firstObservedAt = now - sevenDaysMs,
        lastObservedAt = now,
        confidence = confidence,
        epistemicAuthority = authority,
        sourceProvenance = listOf("Room:task"),
        sourceFreshness = SourceFreshness.CURRENT,
        reviewStatus = reviewStatus,
        createdAt = now,
        updatedAt = now
    )

    private fun buildRecommendation(
        id: String,
        type: InterventionType = InterventionType.RESCHEDULE
    ) = InterventionRecommendation(
        id = id,
        userId = userId,
        interventionType = type,
        objective = InterventionObjective.REDUCE_FRICTION,
        targetEntityId = "com-1",
        targetEntityType = "COMMITMENT",
        rationale = "Recommendation rationale $id",
        suggestedAction = "Review commitment",
        status = InterventionRecommendationStatus.PENDING,
        createdAt = now
    )

    private fun buildAction(
        id: String,
        category: ActionCategory = ActionCategory.COMMITMENT_REVIEW,
        status: ActionReviewStatus,
        targetEntityId: String? = null,
        sourceInsightId: String? = null,
        sourceRecommendationId: String? = null,
        outcomeId: String? = null
    ) = ProposedAction(
        id = id,
        userId = userId,
        category = category,
        title = "Action $id",
        explanation = "Explanation $id",
        sourceRecommendationId = sourceRecommendationId,
        sourceInsightId = sourceInsightId,
        targetEntityId = targetEntityId,
        targetEntityType = if (targetEntityId != null) "TASK" else null,
        reviewStatus = status,
        createdAt = now - 1_000L,
        outcomeId = outcomeId,
        executionResult = if (status == ActionReviewStatus.EXECUTED) {
            ActionExecutionResult(
                executedAt = now - 500L,
                success = true,
                affectedEntityId = targetEntityId,
                affectedEntityType = "TASK",
                message = "Done"
            )
        } else {
            null
        }
    )
}
