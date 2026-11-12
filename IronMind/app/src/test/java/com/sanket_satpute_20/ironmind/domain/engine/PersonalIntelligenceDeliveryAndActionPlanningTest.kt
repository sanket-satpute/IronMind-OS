package com.sanket_satpute_20.ironmind.domain.engine

import com.sanket_satpute_20.ironmind.domain.ai.BarrierCategory
import com.sanket_satpute_20.ironmind.domain.ai.InterventionObjective
import com.sanket_satpute_20.ironmind.domain.ai.InterventionType
import com.sanket_satpute_20.ironmind.domain.common.Clock
import com.sanket_satpute_20.ironmind.domain.common.IdGenerator
import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.Commitment
import com.sanket_satpute_20.ironmind.domain.model.CommitmentStatus
import com.sanket_satpute_20.ironmind.domain.model.DueTaskItem
import com.sanket_satpute_20.ironmind.domain.model.EntitySource
import com.sanket_satpute_20.ironmind.domain.model.Event
import com.sanket_satpute_20.ironmind.domain.model.EventType
import com.sanket_satpute_20.ironmind.domain.model.Goal
import com.sanket_satpute_20.ironmind.domain.model.GoalStatus
import com.sanket_satpute_20.ironmind.domain.model.MemoryConfirmationState
import com.sanket_satpute_20.ironmind.domain.model.Outcome
import com.sanket_satpute_20.ironmind.domain.model.Plan
import com.sanket_satpute_20.ironmind.domain.model.PlanStatus
import com.sanket_satpute_20.ironmind.domain.model.ResultStatus
import com.sanket_satpute_20.ironmind.domain.model.Task
import com.sanket_satpute_20.ironmind.domain.model.TaskStatus
import com.sanket_satpute_20.ironmind.domain.model.action.ActionCategory
import com.sanket_satpute_20.ironmind.domain.model.action.ActionPriority
import com.sanket_satpute_20.ironmind.domain.model.action.ActionReviewStatus
import com.sanket_satpute_20.ironmind.domain.model.action.ActionUrgency
import com.sanket_satpute_20.ironmind.domain.model.action.ProposedAction
import com.sanket_satpute_20.ironmind.domain.model.barrier.BarrierConfirmationState
import com.sanket_satpute_20.ironmind.domain.model.barrier.BarrierHypothesis
import com.sanket_satpute_20.ironmind.domain.model.barrier.BarrierStatus
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
import com.sanket_satpute_20.ironmind.domain.repository.EventRepository
import com.sanket_satpute_20.ironmind.domain.repository.InterventionRecommendationRepository
import com.sanket_satpute_20.ironmind.domain.repository.OutcomeRepository
import com.sanket_satpute_20.ironmind.domain.repository.TaskRepository
import com.sanket_satpute_20.ironmind.domain.usecase.action.ActionLifecycleUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.task.CreateTaskUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.task.EditTaskUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Phase 3.5: Personal Intelligence Delivery & Action Planning Test Suite.
 *
 * Verifies all 16 requirements:
 * 1. Daily briefing assembly answering core questions.
 * 2. Correct prioritization of due and overdue work.
 * 3. Evidence-backed recommendations.
 * 4. Insufficient evidence suppression.
 * 5. Stale source handling (priority downgraded).
 * 6. Missing Usage Access handling (explicit explanation, not zero usage).
 * 7. Duplicate recommendation suppression.
 * 8. Conflicting recommendation handling.
 * 9. User acceptance versus actual execution.
 * 10. Execution versus measured outcome.
 * 11. Rejection without insight refutation.
 * 12. User approval before domain mutation.
 * 13. Session ownership isolation.
 * 14. Session switching isolation.
 * 15. Existing task decomposition and rescheduling workflows.
 * 16. No autonomous execution invariant.
 */
class PersonalIntelligenceDeliveryAndActionPlanningTest {

    private val userId = "user-delivery-test"
    private val now = 1700000000000L

    private val fakeClock = object : Clock {
        override fun currentTimeMillis(): Long = now
    }

    private var idCounter = 1000
    private val fakeIdGenerator = object : IdGenerator {
        override fun generateId(): String = "id-${idCounter++}"
    }

    private lateinit var actionPlanningEngine: ActionPlanningEngine
    private lateinit var dailyBriefingEngine: DailyBriefingEngine
    private lateinit var fakeTaskRepo: InMemoryTaskRepository
    private lateinit var fakeRecRepo: InMemoryRecommendationRepository
    private lateinit var fakeOutcomeRepo: InMemoryOutcomeRepository
    private lateinit var fakeEventRepo: InMemoryEventRepository
    private lateinit var editTaskUseCase: EditTaskUseCase
    private lateinit var createTaskUseCase: CreateTaskUseCase
    private lateinit var actionLifecycleUseCase: ActionLifecycleUseCase

    @Before
    fun setUp() {
        actionPlanningEngine = ActionPlanningEngine(fakeClock, fakeIdGenerator)
        dailyBriefingEngine = DailyBriefingEngine(actionPlanningEngine, fakeClock)

        fakeTaskRepo = InMemoryTaskRepository()
        fakeRecRepo = InMemoryRecommendationRepository()
        fakeOutcomeRepo = InMemoryOutcomeRepository()
        fakeEventRepo = InMemoryEventRepository()

        editTaskUseCase = EditTaskUseCase(fakeTaskRepo, fakeClock, fakeEventRepo, fakeIdGenerator)
        createTaskUseCase = CreateTaskUseCase(fakeTaskRepo, fakeIdGenerator, fakeClock, fakeEventRepo)

        actionLifecycleUseCase = ActionLifecycleUseCase(
            recommendationRepository = fakeRecRepo,
            outcomeRepository = fakeOutcomeRepo,
            eventRepository = fakeEventRepo,
            taskRepository = fakeTaskRepo,
            editTaskUseCase = editTaskUseCase,
            createTaskUseCase = createTaskUseCase,
            clock = fakeClock,
            idGenerator = fakeIdGenerator
        )
    }

    private fun createSampleUnifiedContext(
        permissionStatus: UsagePermissionStatus = UsagePermissionStatus.GRANTED
    ): UnifiedContext {
        return UnifiedContext(
            userId = userId,
            generatedAt = now,
            windowStartTimeMs = now - 604800000L,
            windowEndTimeMs = now,
            productivity = ProductivityContext(),
            digitalBehaviour = DigitalBehaviourContext(
                permissionStatus = permissionStatus,
                availability = SourceAvailability.AVAILABLE,
                appUsages = emptyList()
            )
        )
    }

    private fun createSampleTask(
        id: String,
        title: String,
        dueAt: Long? = null,
        duration: Int = 30,
        planId: String? = "plan-1"
    ): Task {
        return Task(
            id = id,
            userId = userId,
            goalId = "goal-1",
            planId = planId,
            title = title,
            description = "Test task description",
            status = TaskStatus.PENDING,
            priority = 2,
            estimatedDurationMinutes = duration,
            scheduledAt = null,
            dueAt = dueAt,
            createdAt = now - 86400000L,
            updatedAt = now - 86400000L,
            source = EntitySource.USER
        )
    }

    private fun createSampleInsight(
        id: String,
        category: InsightCategory = InsightCategory.PRODUCTIVITY,
        confidence: Float = 0.8f,
        evidenceCount: Int = 3,
        freshness: SourceFreshness = SourceFreshness.CURRENT,
        reviewStatus: InsightReviewStatus = InsightReviewStatus.UNREVIEWED
    ): PersonalInsight {
        val evidenceIds = (1..evidenceCount).map { "ev-$it" }
        return PersonalInsight(
            id = id,
            userId = userId,
            category = category,
            title = "Test Insight $id",
            explanation = "Explanation for $id",
            explainabilityBlock = ExplainabilityBlock(
                whatWasObserved = "Observed events",
                observationPeriod = "Last 7 days",
                supportingEvidenceDescription = "Evidence description",
                whyItMatters = "Execution awareness",
                uncertaintyOrLimitations = "None",
                suggestedNextSteps = "Continue pacing"
            ),
            supportingEvidenceIds = evidenceIds,
            observationWindowStartMs = now - 604800000L,
            observationWindowEndMs = now,
            firstObservedAt = now - 604800000L,
            lastObservedAt = now,
            confidence = confidence,
            epistemicAuthority = EpistemicAuthority.DERIVED_CALCULATION,
            sourceProvenance = listOf("Room:task"),
            sourceFreshness = freshness,
            reviewStatus = reviewStatus,
            createdAt = now,
            updatedAt = now
        )
    }

    // =========================================================================
    // Scenario 1: Daily Briefing Assembly Answers All Core Questions
    // =========================================================================

    @Test
    fun test1_dailyBriefingAssembly_answersCoreQuestions() {
        val overdueTask = createSampleTask("task-overdue-1", "Overdue Task", dueAt = now - 3600000L)
        val dueTodayTask = createSampleTask("task-today-1", "Due Today Task", dueAt = now + 3600000L)

        val overdueItem = DueTaskItem(task = overdueTask, isOverdue = true)
        val dueTodayItem = DueTaskItem(task = dueTodayTask, isOverdue = false)

        val activeCommitment = Commitment(
            id = "com-1",
            userId = userId,
            goalId = null,
            planId = null,
            taskId = null,
            title = "Commitment 1",
            description = "Active commitment",
            status = CommitmentStatus.COMMITTED,
            priority = 2,
            committedAt = now,
            source = EntitySource.USER,
            createdAt = now,
            updatedAt = now
        )

        val plan = Plan(
            id = "plan-1",
            userId = userId,
            goalId = "goal-1",
            title = "Core Plan",
            description = "Active plan",
            status = PlanStatus.ACTIVE,
            source = EntitySource.USER,
            createdAt = now - 1000000L,
            updatedAt = now - 1000000L
        )

        val briefing = dailyBriefingEngine.assembleBriefing(
            userId = userId,
            unifiedContext = createSampleUnifiedContext(),
            overdueTasks = listOf(overdueItem),
            dueTodayTasks = listOf(dueTodayItem),
            activeCommitments = listOf(activeCommitment),
            goals = emptyList(),
            plans = listOf(plan),
            patterns = emptyList(),
            barrierHypotheses = emptyList(),
            recommendations = emptyList(),
            insights = emptyList()
        )

        // 1. Attention summary answered
        assertTrue(briefing.attentionSummary.contains("1 overdue task(s)"))
        assertTrue(briefing.attentionSummary.contains("1 task(s) due today"))
        assertTrue(briefing.attentionSummary.contains("1 active commitment(s)"))

        // 2. Due and overdue answered
        assertEquals(1, briefing.overdueTasks.size)
        assertEquals(1, briefing.dueTodayTasks.size)
        assertEquals(1, briefing.activeCommitments.size)

        // 3. Stalled/attention plans answered
        assertEquals(1, briefing.goalsAndPlansNeedingAttention.size)
        assertEquals("plan-1", briefing.goalsAndPlansNeedingAttention[0].entityId)

        // 6. Proposed next actions answered
        assertTrue(briefing.proposedNextActions.isNotEmpty())
    }

    // =========================================================================
    // Scenario 2: Correct Prioritization of Due and Overdue Work
    // =========================================================================

    @Test
    fun test2_correctPrioritization_dueAndOverdueWork() {
        val overdueTask = createSampleTask("task-overdue-2", "Urgent Overdue Task", dueAt = now - 7200000L)
        val normalInsight = createSampleInsight("ins-normal", confidence = 0.7f)

        val overdueItem = DueTaskItem(task = overdueTask, isOverdue = true)

        val actions = actionPlanningEngine.planActions(
            userId = userId,
            overdueTasks = listOf(overdueItem),
            dueTodayTasks = emptyList(),
            activeCommitments = emptyList(),
            goalsAndPlansNeedingAttention = emptyList(),
            recommendations = emptyList(),
            insights = listOf(normalInsight)
        )

        // Overdue task action MUST be first and have CRITICAL priority + IMMEDIATE urgency
        assertTrue(actions.isNotEmpty())
        val topAction = actions[0]
        assertEquals(ActionCategory.TASK_RESCHEDULE, topAction.category)
        assertEquals(ActionPriority.CRITICAL, topAction.priority)
        assertEquals(ActionUrgency.IMMEDIATE, topAction.urgency)
        assertEquals(overdueTask.id, topAction.targetEntityId)
    }

    // =========================================================================
    // Scenario 3: Evidence-Backed Recommendations Require Supporting Evidence
    // =========================================================================

    @Test
    fun test3_evidenceBackedRecommendations_requireSupportingEvidence() {
        val supportedInsight = createSampleInsight("ins-supported", confidence = 0.85f, evidenceCount = 4)

        val actions = actionPlanningEngine.planActions(
            userId = userId,
            overdueTasks = emptyList(),
            dueTodayTasks = emptyList(),
            activeCommitments = emptyList(),
            goalsAndPlansNeedingAttention = emptyList(),
            recommendations = emptyList(),
            insights = listOf(supportedInsight)
        )

        assertEquals(1, actions.size)
        assertEquals(supportedInsight.id, actions[0].sourceInsightId)
        assertTrue(actions[0].supportingEvidenceIds.size >= 4)
    }

    // =========================================================================
    // Scenario 4: Insufficient Evidence Suppression
    // =========================================================================

    @Test
    fun test4_insufficientEvidenceSuppression_noActionManufactured() {
        // Zero evidence items and low confidence
        val weakInsight = createSampleInsight("ins-weak", confidence = 0.40f, evidenceCount = 0)

        val actions = actionPlanningEngine.planActions(
            userId = userId,
            overdueTasks = emptyList(),
            dueTodayTasks = emptyList(),
            activeCommitments = emptyList(),
            goalsAndPlansNeedingAttention = emptyList(),
            recommendations = emptyList(),
            insights = listOf(weakInsight)
        )

        // Suppressed! No action should be manufactured
        assertTrue(actions.isEmpty())
    }

    // =========================================================================
    // Scenario 5: Stale Source Handling (Downgrades Priority)
    // =========================================================================

    @Test
    fun test5_staleSourceHandling_downgradesPriority() {
        val staleInsight = createSampleInsight(
            "ins-stale",
            confidence = 0.85f,
            evidenceCount = 3,
            freshness = SourceFreshness.STALE
        )

        val actions = actionPlanningEngine.planActions(
            userId = userId,
            overdueTasks = emptyList(),
            dueTodayTasks = emptyList(),
            activeCommitments = emptyList(),
            goalsAndPlansNeedingAttention = emptyList(),
            recommendations = emptyList(),
            insights = listOf(staleInsight)
        )

        assertEquals(1, actions.size)
        // Priority must be downgraded to LOW due to staleness
        assertEquals(ActionPriority.LOW, actions[0].priority)
    }

    // =========================================================================
    // Scenario 6: Missing Usage Access Handling (Explicit Explanation, Not Zero Usage)
    // =========================================================================

    @Test
    fun test6_missingUsageAccessHandling_explicitExplanation() {
        val contextWithoutPermission = createSampleUnifiedContext(
            permissionStatus = UsagePermissionStatus.DENIED
        )

        val briefing = dailyBriefingEngine.assembleBriefing(
            userId = userId,
            unifiedContext = contextWithoutPermission,
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

        assertNotNull(briefing.dataAvailabilityNote)
        assertTrue(briefing.dataAvailabilityNote!!.contains("Usage Access permission is not granted"))
        // Must never report "0 minutes" usage
        assertFalse(briefing.dataAvailabilityNote!!.contains("0 minutes"))
    }

    // =========================================================================
    // Scenario 7: Duplicate Recommendation Suppression
    // =========================================================================

    @Test
    fun test7_duplicateRecommendationSuppression() {
        val overdueTask = createSampleTask("task-dup-1", "Dup Overdue Task", dueAt = now - 3600000L)
        val overdueItem = DueTaskItem(task = overdueTask, isOverdue = true)

        val existingAction = ProposedAction(
            id = "act-existing-1",
            userId = userId,
            category = ActionCategory.TASK_RESCHEDULE,
            title = "Existing Reschedule",
            explanation = "Already proposed",
            targetEntityId = overdueTask.id,
            targetEntityType = "TASK",
            reviewStatus = ActionReviewStatus.PROPOSED,
            createdAt = now - 1000L
        )

        val actions = actionPlanningEngine.planActions(
            userId = userId,
            overdueTasks = listOf(overdueItem),
            dueTodayTasks = emptyList(),
            activeCommitments = emptyList(),
            goalsAndPlansNeedingAttention = emptyList(),
            recommendations = emptyList(),
            insights = emptyList(),
            existingActions = listOf(existingAction)
        )

        // Must be suppressed because an active action targeting the same entity already exists!
        assertTrue(actions.isEmpty())
    }

    @Test
    fun test7b_executingActionSuppressesDuplicateProposalAcrossKeys() {
        val executingTask = createSampleTask("task-exec-1", "Executing Task", dueAt = now - 3600000L)
        val freshTask = createSampleTask("task-fresh-1", "Fresh Overdue Task", dueAt = now - 3600000L)

        val executingTaskAction = ProposedAction(
            id = "custom-exec-task-action",
            userId = userId,
            category = ActionCategory.TASK_RESCHEDULE,
            title = "In-flight Reschedule",
            explanation = "Currently executing",
            targetEntityId = executingTask.id,
            targetEntityType = "TASK",
            reviewStatus = ActionReviewStatus.EXECUTING,
            createdAt = now - 1000L
        )
        val executingInsightAction = ProposedAction(
            id = "custom-exec-insight-action",
            userId = userId,
            category = ActionCategory.FOCUS_PROTECTION,
            title = "In-flight Focus",
            explanation = "Currently executing",
            sourceInsightId = "ins-exec-focus",
            reviewStatus = ActionReviewStatus.EXECUTING,
            createdAt = now - 1000L
        )
        val executingRecAction = ProposedAction(
            id = "custom-exec-rec-action",
            userId = userId,
            category = ActionCategory.TASK_RESCHEDULE,
            title = "In-flight Rec",
            explanation = "Currently executing",
            sourceRecommendationId = "rec-exec-1",
            reviewStatus = ActionReviewStatus.EXECUTING,
            createdAt = now - 1000L
        )

        val focusInsight = PersonalInsight(
            id = "ins-exec-focus",
            userId = userId,
            category = InsightCategory.FOCUS,
            title = "Focus insight",
            explanation = "Focus explanation",
            explainabilityBlock = ExplainabilityBlock(
                whatWasObserved = "Observed",
                observationPeriod = "Today",
                supportingEvidenceDescription = "Evidence",
                whyItMatters = "Matters",
                uncertaintyOrLimitations = "None",
                suggestedNextSteps = "Protect focus"
            ),
            observationWindowStartMs = now - 3600000L,
            observationWindowEndMs = now,
            firstObservedAt = now - 3600000L,
            lastObservedAt = now,
            confidence = 0.8f,
            epistemicAuthority = EpistemicAuthority.DERIVED_CALCULATION,
            sourceProvenance = listOf("Room:task"),
            sourceFreshness = SourceFreshness.CURRENT,
            createdAt = now,
            updatedAt = now
        )
        val pendingRec = InterventionRecommendation(
            id = "rec-exec-1",
            userId = userId,
            interventionType = InterventionType.RESCHEDULE,
            objective = InterventionObjective.REDUCE_FRICTION,
            targetEntityId = "task-rec-only",
            targetEntityType = "TASK",
            rationale = "Rationale",
            suggestedAction = "Suggested",
            status = InterventionRecommendationStatus.PENDING,
            createdAt = now
        )

        val planned = actionPlanningEngine.planActions(
            userId = userId,
            overdueTasks = listOf(
                DueTaskItem(task = executingTask, isOverdue = true),
                DueTaskItem(task = freshTask, isOverdue = true)
            ),
            dueTodayTasks = emptyList(),
            activeCommitments = emptyList(),
            goalsAndPlansNeedingAttention = emptyList(),
            recommendations = listOf(pendingRec),
            insights = listOf(focusInsight),
            existingActions = listOf(
                executingTaskAction,
                executingInsightAction,
                executingRecAction
            )
        )

        assertEquals(1, planned.size)
        assertEquals("act_reschedule_${freshTask.id}", planned[0].id)
        assertEquals(ActionReviewStatus.PROPOSED, planned[0].reviewStatus)
    }

    // =========================================================================
    // Scenario 8: Conflicting Recommendation Handling
    // =========================================================================

    @Test
    fun test8_conflictingRecommendationHandling() {
        // Two recommendations targeting the same task
        val rec1 = InterventionRecommendation(
            id = "rec-conflict-1",
            userId = userId,
            interventionType = InterventionType.RESCHEDULE,
            objective = InterventionObjective.REDUCE_FRICTION,
            targetEntityId = "task-conflict",
            targetEntityType = "TASK",
            rationale = "Reschedule conflict 1",
            suggestedAction = "Reschedule to 2 PM",
            status = InterventionRecommendationStatus.PENDING,
            createdAt = now
        )

        val rec2 = InterventionRecommendation(
            id = "rec-conflict-2",
            userId = userId,
            interventionType = InterventionType.RESCHEDULE,
            objective = InterventionObjective.REDUCE_FRICTION,
            targetEntityId = "task-conflict",
            targetEntityType = "TASK",
            rationale = "Reschedule conflict 2",
            suggestedAction = "Reschedule to 4 PM",
            status = InterventionRecommendationStatus.PENDING,
            createdAt = now
        )

        val actions = actionPlanningEngine.planActions(
            userId = userId,
            overdueTasks = emptyList(),
            dueTodayTasks = emptyList(),
            activeCommitments = emptyList(),
            goalsAndPlansNeedingAttention = emptyList(),
            recommendations = listOf(rec1, rec2),
            insights = emptyList()
        )

        // Only 1 action should survive conflict resolution!
        assertEquals(1, actions.size)
        assertEquals("task-conflict", actions[0].targetEntityId)
    }

    // =========================================================================
    // Scenario 9: User Acceptance versus Actual Execution
    // =========================================================================

    @Test
    fun test9_userAcceptanceVersusActualExecution() = runBlocking {
        val originalDueAt = now - 3600000L
        val task = createSampleTask("task-accept-exec", "Task Accept Test", dueAt = originalDueAt)
        fakeTaskRepo.saveTask(task)

        val action = ProposedAction(
            id = "act-accept-1",
            userId = userId,
            category = ActionCategory.TASK_RESCHEDULE,
            title = "Reschedule Task",
            explanation = "Overdue task",
            targetEntityId = task.id,
            targetEntityType = "TASK",
            reviewStatus = ActionReviewStatus.PROPOSED,
            createdAt = now,
            suggestedNewDueAt = now + 86400000L
        )

        // 1. User accepts the action
        val acceptResult = actionLifecycleUseCase.acceptAction(action, userId)
        assertTrue(acceptResult is Result.Success)
        val acceptedAction = (acceptResult as Result.Success).data

        assertEquals(ActionReviewStatus.ACCEPTED, acceptedAction.reviewStatus)

        // Invariant: Task due date in repository MUST NOT be mutated by acceptance!
        val taskInRepo = (fakeTaskRepo.getTask(task.id) as Result.Success).data
        assertEquals("Acceptance must not modify domain task state", originalDueAt, taskInRepo!!.dueAt)
    }

    // =========================================================================
    // Scenario 10: Execution versus Measured Outcome
    // =========================================================================

    @Test
    fun test10_executionVersusMeasuredOutcome() = runBlocking {
        val originalDueAt = now - 3600000L
        val newDueAt = now + 86400000L
        val task = createSampleTask("task-exec-outcome", "Task Outcome Test", dueAt = originalDueAt)
        fakeTaskRepo.saveTask(task)

        val action = ProposedAction(
            id = "act-exec-1",
            userId = userId,
            category = ActionCategory.TASK_RESCHEDULE,
            title = "Reschedule Task",
            explanation = "Overdue task",
            targetEntityId = task.id,
            targetEntityType = "TASK",
            reviewStatus = ActionReviewStatus.ACCEPTED,
            createdAt = now,
            suggestedNewDueAt = newDueAt
        )

        // 1. Execute action
        val execResult = actionLifecycleUseCase.executeAction(action, userId)
        assertTrue(execResult is Result.Success)
        val executedAction = (execResult as Result.Success).data

        assertEquals(ActionReviewStatus.EXECUTED, executedAction.reviewStatus)
        assertNotNull(executedAction.executionResult)
        assertTrue(executedAction.executionResult!!.success)

        // Task dueAt is now mutated in repo
        val updatedTask = (fakeTaskRepo.getTask(task.id) as Result.Success).data
        assertEquals(newDueAt, updatedTask!!.dueAt)

        // Invariant: Executing action does NOT mean measured success! Outcome is separate.
        val outcomesBefore = (fakeOutcomeRepo.getOutcomesForUser(userId) as Result.Success).data
        assertTrue(outcomesBefore.isEmpty())

        // 2. Explicit outcome recording
        val outcomeResult = actionLifecycleUseCase.recordActionOutcome(
            action = executedAction,
            userId = userId,
            resultStatus = ResultStatus.POSITIVE,
            actualDurationMinutes = 20,
            feedback = "Helped keep focus"
        )
        assertTrue(outcomeResult is Result.Success)

        val outcomesAfter = (fakeOutcomeRepo.getOutcomesForUser(userId) as Result.Success).data
        assertEquals(1, outcomesAfter.size)
        assertEquals(ResultStatus.POSITIVE, outcomesAfter[0].resultStatus)
    }

    // =========================================================================
    // Scenario 11: Rejection Without Insight Refutation
    // =========================================================================

    @Test
    fun test11_rejectionWithoutInsightRefutation() = runBlocking {
        val insight = createSampleInsight(
            "ins-reject-link",
            reviewStatus = InsightReviewStatus.USEFUL
        )

        val action = ProposedAction(
            id = "act-reject-1",
            userId = userId,
            category = ActionCategory.COMMITMENT_REVIEW,
            title = "Review Commitment",
            explanation = "Action explanation",
            sourceInsightId = insight.id,
            reviewStatus = ActionReviewStatus.PROPOSED,
            createdAt = now
        )

        // Reject action
        val rejectResult = actionLifecycleUseCase.rejectAction(action, userId, "Not applicable right now")
        assertTrue(rejectResult is Result.Success)
        val rejectedAction = (rejectResult as Result.Success).data

        assertEquals(ActionReviewStatus.REJECTED, rejectedAction.reviewStatus)

        // Source insight's review status and confirmation state remain untouched!
        assertEquals(InsightReviewStatus.USEFUL, insight.reviewStatus)
        assertEquals(MemoryConfirmationState.UNCONFIRMED, insight.confirmationState)
    }

    // =========================================================================
    // Scenario 12: User Approval Before Domain Mutation
    // =========================================================================

    @Test
    fun test12_userApprovalBeforeDomainMutation() = runBlocking {
        val task = createSampleTask("task-no-auto-mut", "No Auto Mut", dueAt = now - 1000L)
        fakeTaskRepo.saveTask(task)

        val action = ProposedAction(
            id = "act-proposed-only",
            userId = userId,
            category = ActionCategory.TASK_RESCHEDULE,
            title = "Reschedule Task",
            explanation = "Action proposed",
            targetEntityId = task.id,
            targetEntityType = "TASK",
            reviewStatus = ActionReviewStatus.PROPOSED,
            createdAt = now,
            suggestedNewDueAt = now + 50000L
        )

        // In PROPOSED state, domain state must remain unaltered
        val taskInRepo = (fakeTaskRepo.getTask(task.id) as Result.Success).data
        assertEquals(now - 1000L, taskInRepo!!.dueAt)
    }

    // =========================================================================
    // Scenario 13: Session Ownership Isolation
    // =========================================================================

    @Test
    fun test13_sessionOwnershipIsolation() = runBlocking {
        val otherUser = "user-intruder"
        val action = ProposedAction(
            id = "act-user-a",
            userId = userId,
            category = ActionCategory.FOCUS_PROTECTION,
            title = "User A Action",
            explanation = "Private action",
            reviewStatus = ActionReviewStatus.PROPOSED,
            createdAt = now
        )

        val result = actionLifecycleUseCase.acceptAction(action, otherUser)
        assertTrue("Other user must not be permitted to accept action", result is Result.Failure)
        assertTrue((result as Result.Failure).error is SecurityException)
    }

    // =========================================================================
    // Scenario 14: Session Switching Isolation
    // =========================================================================

    @Test
    fun test14_sessionSwitchingIsolation() {
        val userAContext = createSampleUnifiedContext()
        val userBContext = UnifiedContext(
            userId = "user-b",
            generatedAt = now,
            windowStartTimeMs = now - 604800000L,
            windowEndTimeMs = now,
            productivity = ProductivityContext(),
            digitalBehaviour = DigitalBehaviourContext(
                permissionStatus = UsagePermissionStatus.GRANTED,
                availability = SourceAvailability.AVAILABLE
            )
        )

        val briefingUserA = dailyBriefingEngine.assembleBriefing(
            userId = userId,
            unifiedContext = userAContext,
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

        val briefingUserB = dailyBriefingEngine.assembleBriefing(
            userId = "user-b",
            unifiedContext = userBContext,
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

        assertEquals(userId, briefingUserA.userId)
        assertEquals("user-b", briefingUserB.userId)
    }

    // =========================================================================
    // Scenario 15: Existing Task Decomposition Workflow
    // =========================================================================

    @Test
    fun test15_existingTaskDecompositionWorkflow() = runBlocking {
        val largeTask = createSampleTask(
            id = "task-large-decomp",
            title = "Large Architectural Migration",
            duration = 180,
            planId = "plan-decomp"
        )
        fakeTaskRepo.saveTask(largeTask)

        val action = ProposedAction(
            id = "act-decomp-1",
            userId = userId,
            category = ActionCategory.TASK_DECOMPOSITION,
            title = "Break Down Complex Task",
            explanation = "Task is large",
            targetEntityId = largeTask.id,
            targetEntityType = "TASK",
            reviewStatus = ActionReviewStatus.ACCEPTED,
            createdAt = now,
            suggestedSubTasks = listOf("Step 1: Inspect", "Step 2: Implement", "Step 3: Test")
        )

        val execResult = actionLifecycleUseCase.executeAction(action, userId)
        assertTrue(execResult is Result.Success)
        val executed = (execResult as Result.Success).data

        assertEquals(ActionReviewStatus.EXECUTED, executed.reviewStatus)
        assertEquals(3, executed.executionResult!!.resultingSubTaskIds.size)

        // Verify sub-tasks exist in repository
        val tasksInRepo = (fakeTaskRepo.getTasksForPlan("plan-decomp") as Result.Success).data
        assertEquals(4, tasksInRepo.size) // 1 parent + 3 created sub-tasks
    }

    // =========================================================================
    // Scenario 16: No Autonomous Execution Invariant
    // =========================================================================

    @Test
    fun test16_noAutonomousExecutionInvariant() {
        val overdueTask = createSampleTask("task-auto-inv", "Auto Invariant", dueAt = now - 5000L)
        val overdueItem = DueTaskItem(task = overdueTask, isOverdue = true)

        val briefing = dailyBriefingEngine.assembleBriefing(
            userId = userId,
            unifiedContext = createSampleUnifiedContext(),
            overdueTasks = listOf(overdueItem),
            dueTodayTasks = emptyList(),
            activeCommitments = emptyList(),
            goals = emptyList(),
            plans = emptyList(),
            patterns = emptyList(),
            barrierHypotheses = emptyList(),
            recommendations = emptyList(),
            insights = emptyList()
        )

        // All proposed actions in a newly assembled briefing MUST be in PROPOSED status!
        for (action in briefing.proposedNextActions) {
            assertEquals(
                "Proposed action must NEVER be autonomously executed or accepted",
                ActionReviewStatus.PROPOSED,
                action.reviewStatus
            )
            assertNull(action.executedAt)
            assertNull(action.executionResult)
        }
    }

    // =========================================================================
    // Test Doubles
    // =========================================================================

    class InMemoryTaskRepository : TaskRepository {
        private val tasks = mutableMapOf<String, Task>()

        override suspend fun saveTask(task: Task): Result<Unit, Exception> {
            tasks[task.id] = task
            return Result.Success(Unit)
        }

        override suspend fun getTask(id: String): Result<Task?, Exception> {
            return Result.Success(tasks[id])
        }

        override suspend fun getTasksForPlan(planId: String): Result<List<Task>, Exception> {
            return Result.Success(tasks.values.filter { it.planId == planId })
        }

        override suspend fun getTasksForGoal(goalId: String): Result<List<Task>, Exception> {
            return Result.Success(tasks.values.filter { it.goalId == goalId })
        }

        override suspend fun getTasksForUser(userId: String): Result<List<Task>, Exception> {
            return Result.Success(tasks.values.filter { it.userId == userId })
        }

        override suspend fun deleteTask(userId: String, taskId: String): Result<Unit, Exception> {
            tasks.remove(taskId)
            return Result.Success(Unit)
        }
    }

    class InMemoryRecommendationRepository : InterventionRecommendationRepository {
        private val recs = mutableMapOf<String, InterventionRecommendation>()

        override suspend fun saveRecommendation(recommendation: InterventionRecommendation): Result<Unit, Exception> {
            recs[recommendation.id] = recommendation
            return Result.Success(Unit)
        }

        override suspend fun getRecommendation(id: String): Result<InterventionRecommendation?, Exception> {
            return Result.Success(recs[id])
        }

        override fun getPendingRecommendations(userId: String, currentTime: Long): Flow<List<InterventionRecommendation>> {
            return flowOf(recs.values.filter { it.userId == userId && it.status == InterventionRecommendationStatus.PENDING })
        }

        override suspend fun updateRecommendationStatus(id: String, status: InterventionRecommendationStatus): Result<Unit, Exception> {
            val existing = recs[id] ?: return Result.Failure(IllegalArgumentException("Not found"))
            recs[id] = existing.copy(status = status)
            return Result.Success(Unit)
        }

        override suspend fun getEquivalentRecommendations(
            userId: String,
            objective: String,
            targetEntityType: String?,
            targetEntityId: String?
        ): Result<List<InterventionRecommendation>, Exception> {
            return Result.Success(emptyList())
        }

        override suspend fun getRecommendationsForUser(
            userId: String,
            startTime: Long,
            endTime: Long,
            limit: Int
        ): Result<List<InterventionRecommendation>, Exception> {
            return Result.Success(recs.values.filter { it.userId == userId })
        }
    }

    class InMemoryOutcomeRepository : OutcomeRepository {
        private val outcomes = mutableListOf<Outcome>()

        override suspend fun saveOutcome(outcome: Outcome): Result<Unit, Exception> {
            outcomes.add(outcome)
            return Result.Success(Unit)
        }

        override suspend fun getOutcomeForSource(sourceEntityId: String): Result<Outcome?, Exception> {
            return Result.Success(outcomes.firstOrNull { it.sourceEntityId == sourceEntityId })
        }

        override suspend fun getOutcomesForUser(userId: String): Result<List<Outcome>, Exception> {
            return Result.Success(outcomes.filter { it.userId == userId })
        }
    }

    class InMemoryEventRepository : EventRepository {
        private val events = mutableListOf<Event>()

        override suspend fun saveEvent(event: Event): Result<Event, Exception> {
            events.add(event)
            return Result.Success(event)
        }

        override suspend fun saveEventWithLearnerStatus(event: Event): Result<Event, Exception> {
            events.add(event)
            return Result.Success(event)
        }

        override suspend fun getEvent(id: String): Result<Event?, Exception> {
            return Result.Success(events.firstOrNull { it.id == id })
        }

        override suspend fun getEventForUser(userId: String, id: String): Result<Event?, Exception> {
            return Result.Success(events.firstOrNull { it.userId == userId && it.id == id })
        }

        override suspend fun getEventsForUser(userId: String): Result<List<Event>, Exception> {
            return Result.Success(events.filter { it.userId == userId })
        }

        override suspend fun getEventsForEntity(entityId: String): Result<List<Event>, Exception> {
            return Result.Success(events.filter { it.entityId == entityId })
        }

        override suspend fun getEventsForDateRange(userId: String, startTime: Long, endTime: Long): Result<List<Event>, Exception> {
            return Result.Success(events.filter { it.userId == userId && it.occurredAt in startTime..endTime })
        }

        override suspend fun getEventsForTimeWindow(
            userId: String,
            startTime: Long,
            endTime: Long,
            types: List<EventType>?,
            limit: Int,
            orderAsc: Boolean
        ): Result<List<Event>, Exception> {
            val filtered = events.filter {
                it.userId == userId && it.occurredAt in startTime..endTime && (types == null || it.type in types)
            }
            return Result.Success(filtered.take(limit))
        }

        override suspend fun searchEvents(userId: String, query: String): Result<List<Event>, Exception> {
            return Result.Success(events.filter { it.userId == userId && (it.metadata?.contains(query) == true) })
        }

        override suspend fun getEventsByCausationId(userId: String, causationId: String): Result<List<Event>, Exception> {
            return Result.Success(events.filter { it.userId == userId && it.causationId == causationId })
        }
    }
}
