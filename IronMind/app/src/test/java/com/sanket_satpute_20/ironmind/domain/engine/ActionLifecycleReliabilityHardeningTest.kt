package com.sanket_satpute_20.ironmind.domain.engine

import com.sanket_satpute_20.ironmind.data.local.dao.IronMindDao
import com.sanket_satpute_20.ironmind.data.local.entity.MemoryEntity
import com.sanket_satpute_20.ironmind.data.repository.ActionRepositoryImpl
import com.sanket_satpute_20.ironmind.domain.ai.InterventionObjective
import com.sanket_satpute_20.ironmind.domain.ai.InterventionType
import com.sanket_satpute_20.ironmind.domain.common.Clock
import com.sanket_satpute_20.ironmind.domain.common.IdGenerator
import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.DueTaskItem
import com.sanket_satpute_20.ironmind.domain.model.EntitySource
import com.sanket_satpute_20.ironmind.domain.model.Event
import com.sanket_satpute_20.ironmind.domain.model.EventType
import com.sanket_satpute_20.ironmind.domain.model.Outcome
import com.sanket_satpute_20.ironmind.domain.model.ResultStatus
import com.sanket_satpute_20.ironmind.domain.model.Task
import com.sanket_satpute_20.ironmind.domain.model.TaskStatus
import com.sanket_satpute_20.ironmind.domain.model.action.ActionCategory
import com.sanket_satpute_20.ironmind.domain.model.action.ActionExecutionResult
import com.sanket_satpute_20.ironmind.domain.model.action.ActionPriority
import com.sanket_satpute_20.ironmind.domain.model.action.ActionReviewStatus
import com.sanket_satpute_20.ironmind.domain.model.action.ActionUrgency
import com.sanket_satpute_20.ironmind.domain.model.action.ProposedAction
import com.sanket_satpute_20.ironmind.domain.model.context.DigitalBehaviourContext
import com.sanket_satpute_20.ironmind.domain.model.context.EpistemicAuthority
import com.sanket_satpute_20.ironmind.domain.model.context.ProductivityContext
import com.sanket_satpute_20.ironmind.domain.model.context.SourceAvailability
import com.sanket_satpute_20.ironmind.domain.model.context.SourceFreshness
import com.sanket_satpute_20.ironmind.domain.model.context.UnifiedContext
import com.sanket_satpute_20.ironmind.domain.model.context.UsagePermissionStatus
import com.sanket_satpute_20.ironmind.domain.model.intervention.InterventionRecommendation
import com.sanket_satpute_20.ironmind.domain.model.intervention.InterventionRecommendationStatus
import com.sanket_satpute_20.ironmind.domain.repository.ActionRepository
import com.sanket_satpute_20.ironmind.domain.repository.EventRepository
import com.sanket_satpute_20.ironmind.domain.repository.InterventionRecommendationRepository
import com.sanket_satpute_20.ironmind.domain.repository.OutcomeRepository
import com.sanket_satpute_20.ironmind.domain.repository.TaskRepository
import com.sanket_satpute_20.ironmind.domain.usecase.action.ActionLifecycleUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.task.CreateTaskUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.task.EditTaskUseCase
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy

/**
 * Phase 3.5.1: Action Lifecycle Reliability Hardening Test Suite.
 *
 * Covers all 14 mandatory reliability scenarios:
 * 1. Action persistence after reload (re-instantiated repository from Room memory table).
 * 2. Action identity stability after briefing regeneration (deterministic IDs).
 * 3. Accepted state persistence.
 * 4. Executed state persistence with executionResult.
 * 5. Outcome reference persistence and idempotent linkage.
 * 6. Duplicate execution prevention (executed, proposed, execution_failed).
 * 7. Concurrent execution prevention (Mutex protection against double-tap).
 * 8. Execution failure recovery and reset protocol.
 * 9. Duplicate outcome submission prevention (idempotent outcome creation).
 * 10. Outcome recording only after execution.
 * 11. Ownership validation across all lifecycle operations.
 * 12. Session switching isolation.
 * 13. No accidental domain mutation during acceptance.
 * 14. Existing Phase 3.5 workflow regression (end-to-end happy path).
 */
class ActionLifecycleReliabilityHardeningTest {

    private val userA = "user_alpha"
    private val userB = "user_bravo"
    private val now = 1700000000000L

    private val fakeClock = object : Clock {
        override fun currentTimeMillis(): Long = now
    }

    private var idCounter = 1000
    private val fakeIdGenerator = object : IdGenerator {
        override fun generateId(): String = "id-${idCounter++}"
    }

    private lateinit var fakeDao: FakeIronMindDao
    private lateinit var actionRepository: ActionRepository
    private lateinit var fakeTaskRepo: InMemoryTaskRepository
    private lateinit var fakeRecRepo: InMemoryRecommendationRepository
    private lateinit var fakeOutcomeRepo: InMemoryOutcomeRepository
    private lateinit var fakeEventRepo: InMemoryEventRepository
    private lateinit var editTaskUseCase: EditTaskUseCase
    private lateinit var createTaskUseCase: CreateTaskUseCase
    private lateinit var actionLifecycleUseCase: ActionLifecycleUseCase
    private lateinit var actionPlanningEngine: ActionPlanningEngine
    private lateinit var dailyBriefingEngine: DailyBriefingEngine

    @Before
    fun setUp() {
        fakeDao = FakeIronMindDao()
        actionRepository = ActionRepositoryImpl(fakeDao.proxy)

        fakeTaskRepo = InMemoryTaskRepository()
        fakeRecRepo = InMemoryRecommendationRepository()
        fakeOutcomeRepo = InMemoryOutcomeRepository()
        fakeEventRepo = InMemoryEventRepository()

        editTaskUseCase = EditTaskUseCase(fakeTaskRepo, fakeClock, fakeEventRepo, fakeIdGenerator)
        createTaskUseCase = CreateTaskUseCase(fakeTaskRepo, fakeIdGenerator, fakeClock, fakeEventRepo)

        actionLifecycleUseCase = ActionLifecycleUseCase(
            actionRepository = actionRepository,
            recommendationRepository = fakeRecRepo,
            outcomeRepository = fakeOutcomeRepo,
            eventRepository = fakeEventRepo,
            taskRepository = fakeTaskRepo,
            editTaskUseCase = editTaskUseCase,
            createTaskUseCase = createTaskUseCase,
            clock = fakeClock,
            idGenerator = fakeIdGenerator
        )

        actionPlanningEngine = ActionPlanningEngine(fakeClock, fakeIdGenerator)
        dailyBriefingEngine = DailyBriefingEngine(actionPlanningEngine, fakeClock)
    }

    private fun createSampleTask(
        id: String,
        userId: String = userA,
        title: String = "Test Task",
        dueAt: Long? = now - 3600000L
    ): Task = Task(
        id = id,
        userId = userId,
        goalId = "goal-1",
        planId = "plan-1",
        title = title,
        description = "Task description",
        status = TaskStatus.PENDING,
        priority = 2,
        estimatedDurationMinutes = 45,
        scheduledAt = null,
        dueAt = dueAt,
        completedAt = null,
        source = EntitySource.USER,
        createdAt = now - 7200000L,
        updatedAt = now - 7200000L
    )

    private fun createSampleAction(
        id: String,
        userId: String = userA,
        category: ActionCategory = ActionCategory.TASK_RESCHEDULE,
        targetEntityId: String? = "task-1",
        reviewStatus: ActionReviewStatus = ActionReviewStatus.PROPOSED
    ): ProposedAction = ProposedAction(
        id = id,
        userId = userId,
        category = category,
        title = "Reschedule Task $targetEntityId",
        explanation = "Action explanation",
        targetEntityId = targetEntityId,
        targetEntityType = "TASK",
        priority = ActionPriority.HIGH,
        urgency = ActionUrgency.TODAY,
        confidence = 0.90f,
        reviewStatus = reviewStatus,
        createdAt = now,
        suggestedNewDueAt = now + 86400000L
    )

    // =========================================================================
    // Scenario 1: Action Persistence Across Reload
    // =========================================================================

    @Test
    fun test1_actionPersistenceAcrossReload() = runBlocking {
        val originalAction = ProposedAction(
            id = "act-p1",
            userId = userA,
            category = ActionCategory.TASK_DECOMPOSITION,
            title = "Decompose Large Task",
            explanation = "Task is too complex",
            targetEntityId = "task-large-1",
            targetEntityType = "TASK",
            priority = ActionPriority.CRITICAL,
            urgency = ActionUrgency.IMMEDIATE,
            supportingEvidenceIds = listOf("ev1", "ev2"),
            confidence = 0.92f,
            epistemicAuthority = EpistemicAuthority.SYSTEM_RECORDED_OBSERVATION,
            sourceProvenance = listOf("Room:tasks"),
            sourceFreshness = SourceFreshness.CURRENT,
            reviewStatus = ActionReviewStatus.PROPOSED,
            createdAt = now - 5000L,
            suggestedSubTasks = listOf("Sub 1", "Sub 2", "Sub 3"),
            suggestedNewDueAt = now + 86400000L
        )

        // 1. Save to repository
        val saveResult = actionRepository.saveAction(originalAction)
        assertTrue(saveResult is Result.Success)

        // 2. Re-instantiate repository from the same underlying DAO (simulating app process death & restart)
        val freshRepo: ActionRepository = ActionRepositoryImpl(fakeDao.proxy)
        val loadResult = freshRepo.getActionById("act-p1")
        assertTrue(loadResult is Result.Success)
        val loaded = (loadResult as Result.Success).data
        assertNotNull(loaded)

        assertEquals(originalAction.id, loaded!!.id)
        assertEquals(originalAction.userId, loaded.userId)
        assertEquals(originalAction.category, loaded.category)
        assertEquals(originalAction.title, loaded.title)
        assertEquals(originalAction.explanation, loaded.explanation)
        assertEquals(originalAction.priority, loaded.priority)
        assertEquals(originalAction.urgency, loaded.urgency)
        assertEquals(originalAction.supportingEvidenceIds, loaded.supportingEvidenceIds)
        assertEquals(originalAction.confidence, loaded.confidence, 0.001f)
        assertEquals(originalAction.epistemicAuthority, loaded.epistemicAuthority)
        assertEquals(originalAction.sourceFreshness, loaded.sourceFreshness)
        assertEquals(originalAction.reviewStatus, loaded.reviewStatus)
        assertEquals(originalAction.createdAt, loaded.createdAt)
        assertEquals(originalAction.suggestedSubTasks, loaded.suggestedSubTasks)
        assertEquals(originalAction.suggestedNewDueAt, loaded.suggestedNewDueAt)
    }

    // =========================================================================
    // Scenario 2: Action Identity Stability After Briefing Regeneration
    // =========================================================================

    @Test
    fun test2_actionIdentityStabilityAfterBriefingRegeneration() {
        val overdueTask = createSampleTask("task-stable-1")
        val overdueItem = DueTaskItem(task = overdueTask, isOverdue = true)

        val rec = InterventionRecommendation(
            id = "rec-stable-1",
            userId = userA,
            interventionType = InterventionType.RESCHEDULE,
            objective = InterventionObjective.REDUCE_FRICTION,
            targetEntityId = "task-rec-target",
            targetEntityType = "TASK",
            rationale = "Stable rec rationale",
            suggestedAction = "Reschedule",
            status = InterventionRecommendationStatus.PENDING,
            createdAt = now
        )

        // Generation 1: Initial planning
        val firstRunActions = actionPlanningEngine.planActions(
            userId = userA,
            overdueTasks = listOf(overdueItem),
            dueTodayTasks = emptyList(),
            activeCommitments = emptyList(),
            goalsAndPlansNeedingAttention = emptyList(),
            recommendations = listOf(rec),
            insights = emptyList()
        )

        assertEquals(2, firstRunActions.size)
        val overdueActionId1 = firstRunActions.first { it.targetEntityId == overdueTask.id }.id
        val recActionId1 = firstRunActions.first { it.sourceRecommendationId == rec.id }.id

        // Deterministic ID format assertions
        assertEquals("act_reschedule_${overdueTask.id}", overdueActionId1)
        assertEquals("act_rec_${rec.id}", recActionId1)

        // Generation 2: Re-planning with existingActions passed
        val briefing2 = dailyBriefingEngine.assembleBriefing(
            userId = userA,
            unifiedContext = UnifiedContext(
                userId = userA,
                generatedAt = now,
                windowStartTimeMs = now - 600000L,
                windowEndTimeMs = now,
                productivity = ProductivityContext(),
                digitalBehaviour = DigitalBehaviourContext(
                    permissionStatus = UsagePermissionStatus.GRANTED,
                    availability = SourceAvailability.AVAILABLE,
                    appUsages = emptyList()
                )
            ),
            overdueTasks = listOf(overdueItem),
            dueTodayTasks = emptyList(),
            activeCommitments = emptyList(),
            goals = emptyList(),
            plans = emptyList(),
            patterns = emptyList(),
            barrierHypotheses = emptyList(),
            recommendations = listOf(rec),
            insights = emptyList(),
            existingActions = firstRunActions
        )

        // Invariant: No duplicate actions manufactured, IDs remain completely stable
        assertEquals(2, briefing2.proposedNextActions.size)
        assertTrue(briefing2.proposedNextActions.any { it.id == overdueActionId1 })
        assertTrue(briefing2.proposedNextActions.any { it.id == recActionId1 })
    }

    // =========================================================================
    // Scenario 3: Accepted State Persistence
    // =========================================================================

    @Test
    fun test3_acceptedStatePersistence() = runBlocking {
        val action = createSampleAction("act_reschedule_task-accept-3")
        actionRepository.saveAction(action)

        // 1. User accepts action
        val acceptResult = actionLifecycleUseCase.acceptAction(action, userA)
        assertTrue(acceptResult is Result.Success)
        val accepted = (acceptResult as Result.Success).data
        assertEquals(ActionReviewStatus.ACCEPTED, accepted.reviewStatus)

        // 2. Verify persisted in ActionRepository
        val inRepo = (actionRepository.getActionById(action.id) as Result.Success).data
        assertNotNull(inRepo)
        assertEquals(ActionReviewStatus.ACCEPTED, inRepo!!.reviewStatus)
        assertEquals(now, inRepo.reviewedAt)

        // 3. Re-assemble briefing: ACCEPTED action must be preserved in proposedNextActions
        val overdueTask = createSampleTask("task-accept-3")
        val briefing = dailyBriefingEngine.assembleBriefing(
            userId = userA,
            unifiedContext = UnifiedContext(
                userId = userA,
                generatedAt = now,
                windowStartTimeMs = now - 600000L,
                windowEndTimeMs = now,
                productivity = ProductivityContext(),
                digitalBehaviour = DigitalBehaviourContext(
                    permissionStatus = UsagePermissionStatus.GRANTED,
                    availability = SourceAvailability.AVAILABLE,
                    appUsages = emptyList()
                )
            ),
            overdueTasks = listOf(DueTaskItem(task = overdueTask, isOverdue = true)),
            dueTodayTasks = emptyList(),
            activeCommitments = emptyList(),
            goals = emptyList(),
            plans = emptyList(),
            patterns = emptyList(),
            barrierHypotheses = emptyList(),
            recommendations = emptyList(),
            insights = emptyList(),
            existingActions = listOf(inRepo)
        )

        val preserved = briefing.proposedNextActions.find { it.id == action.id }
        assertNotNull(preserved)
        assertEquals(ActionReviewStatus.ACCEPTED, preserved!!.reviewStatus)
    }

    // =========================================================================
    // Scenario 4: Executed State Persistence
    // =========================================================================

    @Test
    fun test4_executedStatePersistence() = runBlocking {
        val task = createSampleTask("task-exec-4", dueAt = now - 3600000L)
        fakeTaskRepo.saveTask(task)

        val action = createSampleAction("act_reschedule_task-exec-4", targetEntityId = task.id)
        actionRepository.saveAction(action)

        // Accept first
        actionLifecycleUseCase.acceptAction(action, userA)

        // Execute action
        val newDueAt = now + 86400000L
        val execResult = actionLifecycleUseCase.executeAction(
            action = action.copy(reviewStatus = ActionReviewStatus.ACCEPTED),
            userId = userA,
            executionParams = mapOf("newDueAt" to newDueAt)
        )
        assertTrue(execResult is Result.Success)
        val executed = (execResult as Result.Success).data
        assertEquals(ActionReviewStatus.EXECUTED, executed.reviewStatus)

        // Verify repository persistence across reload
        val freshRepo: ActionRepository = ActionRepositoryImpl(fakeDao.proxy)
        val loaded = (freshRepo.getActionById(action.id) as Result.Success).data
        assertNotNull(loaded)
        assertEquals(ActionReviewStatus.EXECUTED, loaded!!.reviewStatus)
        assertEquals(now, loaded.executedAt)
        assertNotNull(loaded.executionResult)
        assertTrue(loaded.executionResult!!.success)
        assertEquals(task.id, loaded.executionResult!!.affectedEntityId)
        assertEquals(newDueAt, loaded.executionResult!!.newDueAt)
    }

    // =========================================================================
    // Scenario 5: Outcome Reference Persistence
    // =========================================================================

    @Test
    fun test5_outcomeReferencePersistence() = runBlocking {
        val task = createSampleTask("task-out-5")
        fakeTaskRepo.saveTask(task)

        val action = createSampleAction("act_reschedule_task-out-5", targetEntityId = task.id)
        actionRepository.saveAction(action)
        actionLifecycleUseCase.acceptAction(action, userA)
        val executed = (actionLifecycleUseCase.executeAction(action.copy(reviewStatus = ActionReviewStatus.ACCEPTED), userA) as Result.Success).data

        // Record outcome
        val outcomeResult = actionLifecycleUseCase.recordActionOutcome(
            action = executed,
            userId = userA,
            resultStatus = ResultStatus.POSITIVE,
            actualDurationMinutes = 25,
            feedback = "Task executed smoothly"
        )
        assertTrue(outcomeResult is Result.Success)
        val outcome = (outcomeResult as Result.Success).data

        // Verify action in repository has outcomeId linked durably
        val freshRepo: ActionRepository = ActionRepositoryImpl(fakeDao.proxy)
        val reloaded = (freshRepo.getActionById(action.id) as Result.Success).data
        assertNotNull(reloaded)
        assertEquals(outcome.id, reloaded!!.outcomeId)
    }

    // =========================================================================
    // Scenario 6: Duplicate Execution Prevention
    // =========================================================================

    @Test
    fun test6_duplicateExecutionPrevention() = runBlocking {
        val task = createSampleTask("task-dup-exec")
        fakeTaskRepo.saveTask(task)

        val action = createSampleAction("act_reschedule_task-dup-exec", targetEntityId = task.id)
        actionRepository.saveAction(action)

        // 6a: Attempt to execute PROPOSED action directly without acceptance -> must fail
        val proposedExec = actionLifecycleUseCase.executeAction(action, userA)
        assertTrue(proposedExec is Result.Failure)
        assertTrue((proposedExec as Result.Failure).error is IllegalStateException)
        assertTrue(proposedExec.error.message!!.contains("must be in ACCEPTED state"))

        // Accept and execute cleanly
        actionLifecycleUseCase.acceptAction(action, userA)
        val cleanExec = actionLifecycleUseCase.executeAction(action.copy(reviewStatus = ActionReviewStatus.ACCEPTED), userA)
        assertTrue(cleanExec is Result.Success)

        // 6b: Attempt to execute already EXECUTED action -> must fail
        val duplicateExec = actionLifecycleUseCase.executeAction(
            action = (cleanExec as Result.Success).data,
            userId = userA
        )
        assertTrue(duplicateExec is Result.Failure)
        assertTrue((duplicateExec as Result.Failure).error is IllegalStateException)
        assertTrue(duplicateExec.error.message!!.contains("already been executed"))

        // 6c: EXECUTION_FAILED action cannot be silently re-executed without reset
        val failedAction = action.copy(
            id = "act-failed-1",
            reviewStatus = ActionReviewStatus.EXECUTION_FAILED
        )
        actionRepository.saveAction(failedAction)

        val retryFailedExec = actionLifecycleUseCase.executeAction(failedAction, userA)
        assertTrue(retryFailedExec is Result.Failure)
        assertTrue((retryFailedExec as Result.Failure).error is IllegalStateException)
        assertTrue(retryFailedExec.error.message!!.contains("previously failed execution"))
    }

    // =========================================================================
    // Scenario 7: Concurrent Execution Prevention
    // =========================================================================

    @Test
    fun test7_concurrentExecutionPrevention() = runBlocking {
        val task = createSampleTask("task-concurrent-7")
        fakeTaskRepo.saveTask(task)

        val action = createSampleAction("act_reschedule_task-concurrent-7", targetEntityId = task.id)
        actionRepository.saveAction(action)
        actionLifecycleUseCase.acceptAction(action, userA)
        val acceptedAction = action.copy(reviewStatus = ActionReviewStatus.ACCEPTED)

        // Simulate concurrent button taps
        val call1 = async { actionLifecycleUseCase.executeAction(acceptedAction, userA) }
        val call2 = async { actionLifecycleUseCase.executeAction(acceptedAction, userA) }

        val result1 = call1.await()
        val result2 = call2.await()

        val results = listOf(result1, result2)
        val successCount = results.count { it is Result.Success }
        val failureCount = results.count { it is Result.Failure }

        // Invariant: Exactly one execution succeeds, duplicate execution is blocked
        assertEquals(1, successCount)
        assertEquals(1, failureCount)
        val failure = results.first { it is Result.Failure } as Result.Failure
        assertTrue(failure.error is IllegalStateException)
    }

    // =========================================================================
    // Scenario 8: Execution Failure Recovery
    // =========================================================================

    @Test
    fun test8_executionFailureRecovery() = runBlocking {
        // Target task does NOT exist in fakeTaskRepo -> executeTaskReschedule will fail
        val missingTaskId = "task-missing-8"
        val action = createSampleAction("act_reschedule_missing", targetEntityId = missingTaskId)
        actionRepository.saveAction(action)
        actionLifecycleUseCase.acceptAction(action, userA)

        val failResult = actionLifecycleUseCase.executeAction(action.copy(reviewStatus = ActionReviewStatus.ACCEPTED), userA)
        assertTrue(failResult is Result.Success)
        val failedAction = (failResult as Result.Success).data

        // State is EXECUTION_FAILED and message recorded
        assertEquals(ActionReviewStatus.EXECUTION_FAILED, failedAction.reviewStatus)
        assertNotNull(failedAction.executionResult)
        assertFalse(failedAction.executionResult!!.success)

        // Persisted in repository
        val inRepo = (actionRepository.getActionById(action.id) as Result.Success).data
        assertNotNull(inRepo)
        assertEquals(ActionReviewStatus.EXECUTION_FAILED, inRepo!!.reviewStatus)

        // User recovery protocol: explicitly re-accepting the action resets it to ACCEPTED
        val resetResult = actionLifecycleUseCase.acceptAction(failedAction, userA)
        assertTrue(resetResult is Result.Success)
        val resetAction = (resetResult as Result.Success).data
        assertEquals(ActionReviewStatus.ACCEPTED, resetAction.reviewStatus)

        // Now creating the task and executing succeeds
        fakeTaskRepo.saveTask(createSampleTask(missingTaskId))
        val retryResult = actionLifecycleUseCase.executeAction(resetAction, userA)
        assertTrue(retryResult is Result.Success)
        assertEquals(ActionReviewStatus.EXECUTED, (retryResult as Result.Success).data.reviewStatus)
    }

    // =========================================================================
    // Scenario 9: Duplicate Outcome Submission Prevention
    // =========================================================================

    @Test
    fun test9_duplicateOutcomeSubmissionPrevention() = runBlocking {
        val task = createSampleTask("task-dup-outcome")
        fakeTaskRepo.saveTask(task)

        val action = createSampleAction("act_reschedule_task-dup-outcome", targetEntityId = task.id)
        actionRepository.saveAction(action)
        actionLifecycleUseCase.acceptAction(action, userA)
        val executed = (actionLifecycleUseCase.executeAction(action.copy(reviewStatus = ActionReviewStatus.ACCEPTED), userA) as Result.Success).data

        // First outcome submission
        val outcome1Result = actionLifecycleUseCase.recordActionOutcome(
            action = executed,
            userId = userA,
            resultStatus = ResultStatus.POSITIVE,
            actualDurationMinutes = 30
        )
        assertTrue(outcome1Result is Result.Success)
        val outcome1 = (outcome1Result as Result.Success).data

        // Second outcome submission for same action
        val outcome2Result = actionLifecycleUseCase.recordActionOutcome(
            action = executed,
            userId = userA,
            resultStatus = ResultStatus.POSITIVE,
            actualDurationMinutes = 30
        )
        assertTrue(outcome2Result is Result.Success)
        val outcome2 = (outcome2Result as Result.Success).data

        // Invariant: Idempotent - returns exact same outcome, does not duplicate records
        assertEquals(outcome1.id, outcome2.id)
        val allOutcomes = (fakeOutcomeRepo.getOutcomesForUser(userA) as Result.Success).data
        assertEquals(1, allOutcomes.size)
    }

    // =========================================================================
    // Scenario 10: Outcome Recording Only After Execution
    // =========================================================================

    @Test
    fun test10_outcomeRecordingOnlyAfterExecution() = runBlocking {
        val action = createSampleAction("act-unexecuted-10")
        actionRepository.saveAction(action)

        // 10a: Proposed action cannot record outcome
        val resProposed = actionLifecycleUseCase.recordActionOutcome(action, userA, ResultStatus.POSITIVE)
        assertTrue(resProposed is Result.Failure)
        assertTrue((resProposed as Result.Failure).error is IllegalStateException)
        assertTrue(resProposed.error.message!!.contains("must be EXECUTED"))

        // 10b: Accepted action cannot record outcome
        actionLifecycleUseCase.acceptAction(action, userA)
        val resAccepted = actionLifecycleUseCase.recordActionOutcome(
            action.copy(reviewStatus = ActionReviewStatus.ACCEPTED),
            userA,
            ResultStatus.POSITIVE
        )
        assertTrue(resAccepted is Result.Failure)
        assertTrue((resAccepted as Result.Failure).error is IllegalStateException)
        assertTrue(resAccepted.error.message!!.contains("must be EXECUTED"))
    }

    // =========================================================================
    // Scenario 11: Ownership Validation Across Operations
    // =========================================================================

    @Test
    fun test11_ownershipValidation() = runBlocking {
        val action = createSampleAction("act-ownership-11", userId = userA)
        actionRepository.saveAction(action)

        // User B attempts actions on User A's action
        val acceptFail = actionLifecycleUseCase.acceptAction(action, userB)
        assertTrue(acceptFail is Result.Failure && acceptFail.error is SecurityException)

        val rejectFail = actionLifecycleUseCase.rejectAction(action, userB)
        assertTrue(rejectFail is Result.Failure && rejectFail.error is SecurityException)

        val dismissFail = actionLifecycleUseCase.dismissAction(action, userB)
        assertTrue(dismissFail is Result.Failure && dismissFail.error is SecurityException)

        val deferFail = actionLifecycleUseCase.deferAction(action, userB, now + 3600000L)
        assertTrue(deferFail is Result.Failure && deferFail.error is SecurityException)

        val execFail = actionLifecycleUseCase.executeAction(action, userB)
        assertTrue(execFail is Result.Failure && execFail.error is SecurityException)

        val outcomeFail = actionLifecycleUseCase.recordActionOutcome(action, userB, ResultStatus.POSITIVE)
        assertTrue(outcomeFail is Result.Failure && outcomeFail.error is SecurityException)
    }

    // =========================================================================
    // Scenario 12: Session Switching Isolation
    // =========================================================================

    @Test
    fun test12_sessionSwitchingIsolation() = runBlocking {
        val actionA = createSampleAction("act-userA", userId = userA)
        val actionB = createSampleAction("act-userB", userId = userB)

        actionRepository.saveAction(actionA)
        actionRepository.saveAction(actionB)

        // Query user A
        val userAActions = (actionRepository.getActionsForUser(userA) as Result.Success).data
        assertEquals(1, userAActions.size)
        assertEquals(userA, userAActions[0].userId)

        // Query user B
        val userBActions = (actionRepository.getActionsForUser(userB) as Result.Success).data
        assertEquals(1, userBActions.size)
        assertEquals(userB, userBActions[0].userId)
    }

    // =========================================================================
    // Scenario 13: No Accidental Mutation During Acceptance
    // =========================================================================

    @Test
    fun test13_noAccidentalMutationDuringAcceptance() = runBlocking {
        val originalDueAt = now - 3600000L
        val task = createSampleTask("task-no-mutation", dueAt = originalDueAt)
        fakeTaskRepo.saveTask(task)

        val action = createSampleAction("act_reschedule_task-no-mutation", targetEntityId = task.id)
        actionRepository.saveAction(action)

        // Accept action
        val res = actionLifecycleUseCase.acceptAction(action, userA)
        assertTrue(res is Result.Success)

        // Task in repo must be completely unchanged
        val taskInRepo = (fakeTaskRepo.getTask(task.id) as Result.Success).data
        assertNotNull(taskInRepo)
        assertEquals(originalDueAt, taskInRepo!!.dueAt)
        assertEquals(TaskStatus.PENDING, taskInRepo.status)
    }

    // =========================================================================
    // Scenario 14: Existing Phase 3.5 Workflow Regression (End-to-End Happy Path)
    // =========================================================================

    @Test
    fun test14_existingPhase35WorkflowRegression() = runBlocking {
        val originalDueAt = now - 7200000L
        val task = createSampleTask("task-e2e-14", dueAt = originalDueAt)
        fakeTaskRepo.saveTask(task)

        // 1. Plan action via DailyBriefingEngine
        val overdueItem = DueTaskItem(task = task, isOverdue = true)
        val plannedActions = actionPlanningEngine.planActions(
            userId = userA,
            overdueTasks = listOf(overdueItem),
            dueTodayTasks = emptyList(),
            activeCommitments = emptyList(),
            goalsAndPlansNeedingAttention = emptyList(),
            recommendations = emptyList(),
            insights = emptyList()
        )
        assertEquals(1, plannedActions.size)
        val planned = plannedActions[0]
        assertEquals("act_reschedule_${task.id}", planned.id)
        assertEquals(ActionReviewStatus.PROPOSED, planned.reviewStatus)

        // 2. User accepts action
        val acceptResult = actionLifecycleUseCase.acceptAction(planned, userA)
        assertTrue(acceptResult is Result.Success)
        val accepted = (acceptResult as Result.Success).data
        assertEquals(ActionReviewStatus.ACCEPTED, accepted.reviewStatus)

        // 3. User executes action
        val newDueAt = now + 86400000L
        val execResult = actionLifecycleUseCase.executeAction(
            action = accepted,
            userId = userA,
            executionParams = mapOf("newDueAt" to newDueAt)
        )
        assertTrue(execResult is Result.Success)
        val executed = (execResult as Result.Success).data
        assertEquals(ActionReviewStatus.EXECUTED, executed.reviewStatus)

        // Domain task updated
        val updatedTask = (fakeTaskRepo.getTask(task.id) as Result.Success).data
        assertEquals(newDueAt, updatedTask!!.dueAt)

        // 4. Record outcome
        val outcomeResult = actionLifecycleUseCase.recordActionOutcome(
            action = executed,
            userId = userA,
            resultStatus = ResultStatus.POSITIVE,
            actualDurationMinutes = 20,
            feedback = "Task successfully rescheduled and completed"
        )
        assertTrue(outcomeResult is Result.Success)
        val outcome = (outcomeResult as Result.Success).data
        assertEquals(ResultStatus.POSITIVE, outcome.resultStatus)

        // Action linked to outcome
        val finalAction = (actionRepository.getActionById(planned.id) as Result.Success).data
        assertNotNull(finalAction)
        assertEquals(outcome.id, finalAction!!.outcomeId)
    }

    // =========================================================================
    // Fakes and In-Memory Test Doubles
    // =========================================================================

    class FakeIronMindDao {
        private val memories = mutableMapOf<String, MemoryEntity>()

        val proxy: IronMindDao = Proxy.newProxyInstance(
            IronMindDao::class.java.classLoader,
            arrayOf(IronMindDao::class.java),
            object : InvocationHandler {
                override fun invoke(proxy: Any?, method: Method, args: Array<out Any?>?): Any? {
                    return when (method.name) {
                        "insertMemory" -> {
                            val memory = args!![0] as MemoryEntity
                            memories[memory.id] = memory
                            Unit
                        }
                        "getMemoryById" -> {
                            val id = args!![0] as String
                            memories[id]
                        }
                        "getMemoriesForUser" -> {
                            val userId = args!![0] as String
                            memories.values.filter { it.userId == userId }
                        }
                        "observeMemoriesForUser" -> {
                            val userId = args!![0] as String
                            flowOf(memories.values.filter { it.userId == userId })
                        }
                        "deleteMemory" -> {
                            val userId = args!![0] as String
                            val id = args!![1] as String
                            val existing = memories[id]
                            if (existing != null && existing.userId == userId) {
                                memories.remove(id)
                                1
                            } else {
                                0
                            }
                        }
                        else -> null
                    }
                }
            }
        ) as IronMindDao
    }

    class InMemoryTaskRepository : TaskRepository {
        val tasks = mutableMapOf<String, Task>()

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
        val recs = mutableMapOf<String, InterventionRecommendation>()

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
        val outcomes = mutableListOf<Outcome>()

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
        val events = mutableListOf<Event>()

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
