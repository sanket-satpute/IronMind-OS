package com.sanket_satpute_20.ironmind.integration

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.sanket_satpute_20.ironmind.data.local.IronMindDatabase
import com.sanket_satpute_20.ironmind.data.local.dao.IronMindDao
import com.sanket_satpute_20.ironmind.data.local.entity.OutcomeEntity
import com.sanket_satpute_20.ironmind.data.local.entity.TaskEntity
import com.sanket_satpute_20.ironmind.data.repository.ActionRepositoryImpl
import com.sanket_satpute_20.ironmind.data.repository.CommitmentRepositoryImpl
import com.sanket_satpute_20.ironmind.data.repository.EventRepositoryImpl
import com.sanket_satpute_20.ironmind.data.repository.OutcomeRepositoryImpl
import com.sanket_satpute_20.ironmind.data.repository.TaskRepositoryImpl
import com.sanket_satpute_20.ironmind.domain.common.Clock
import com.sanket_satpute_20.ironmind.domain.common.IdGenerator
import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.EntitySource
import com.sanket_satpute_20.ironmind.domain.model.ResultStatus
import com.sanket_satpute_20.ironmind.domain.model.TaskStatus
import com.sanket_satpute_20.ironmind.domain.model.action.ActionCategory
import com.sanket_satpute_20.ironmind.domain.model.action.ActionReviewStatus
import com.sanket_satpute_20.ironmind.domain.model.action.ProposedAction
import com.sanket_satpute_20.ironmind.domain.usecase.action.ActionLifecycleUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.task.CreateTaskUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.task.EditTaskUseCase
import com.sanket_satpute_20.ironmind.testutil.TestDispatcherRule
import com.sanket_satpute_20.ironmind.testutil.fake.FakeClock
import com.sanket_satpute_20.ironmind.testutil.fake.FakeIdGenerator
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Phase 3.5.1.2: Crash-Safety & Idempotency Integration Tests.
 *
 * Exercises real Room SQLite database instances (Room.inMemoryDatabaseBuilder)
 * to verify transactional crash consistency, deterministic idempotency,
 * and recovery protocols across simulated restarts and crash windows.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class ActionExecutionCrashSafetyIntegrationTest {

    @get:Rule
    val dispatcherRule = TestDispatcherRule()

    private lateinit var context: Context
    private lateinit var db: IronMindDatabase
    private lateinit var dao: IronMindDao

    private lateinit var actionRepository: ActionRepositoryImpl
    private lateinit var taskRepository: TaskRepositoryImpl
    private lateinit var outcomeRepository: OutcomeRepositoryImpl
    private lateinit var eventRepository: EventRepositoryImpl
    private lateinit var commitmentRepository: CommitmentRepositoryImpl

    private lateinit var editTaskUseCase: EditTaskUseCase
    private lateinit var createTaskUseCase: CreateTaskUseCase
    private lateinit var actionLifecycleUseCase: ActionLifecycleUseCase

    private val fakeClock = FakeClock(currentTime = 1_000_000L)
    private val fakeIdGenerator = FakeIdGenerator()
    private val testUserId = "user_crash_safety_test"

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, IronMindDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.ironMindDao()

        dao.insertUserProfile(
            com.sanket_satpute_20.ironmind.data.local.entity.UserProfileEntity(
                id = testUserId,
                createdAt = 1_000_000L,
                updatedAt = 1_000_000L,
                displayName = "Test User",
                timezone = "UTC",
                createdFrom = "TEST",
                status = "ACTIVE",
                schemaVersion = 1
            )
        )

        actionRepository = ActionRepositoryImpl(dao)
        taskRepository = TaskRepositoryImpl(dao = dao, outboxDao = db.outboxDao(), database = db)
        outcomeRepository = OutcomeRepositoryImpl(dao)
        eventRepository = EventRepositoryImpl(dao = dao, database = db)
        commitmentRepository = CommitmentRepositoryImpl(dao = dao, outboxDao = db.outboxDao(), database = db)

        editTaskUseCase = EditTaskUseCase(taskRepository, fakeClock)
        createTaskUseCase = CreateTaskUseCase(taskRepository, fakeIdGenerator, fakeClock, eventRepository)

        actionLifecycleUseCase = createUseCaseInstance(fakeClock, fakeIdGenerator)
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun createUseCaseInstance(clock: Clock, idGen: IdGenerator): ActionLifecycleUseCase {
        return ActionLifecycleUseCase(
            actionRepository = actionRepository,
            outcomeRepository = outcomeRepository,
            eventRepository = eventRepository,
            taskRepository = taskRepository,
            editTaskUseCase = editTaskUseCase,
            createTaskUseCase = createTaskUseCase,
            clock = clock,
            idGenerator = idGen,
            commitmentRepository = commitmentRepository,
            database = db
        )
    }

    // =========================================================================
    // Test 1: Task decomposition idempotency on retry / restart
    // =========================================================================

    @Test
    fun same_decomposition_action_executed_twice_does_not_create_duplicate_subtasks() = runTest {
        // Given a parent task in Room
        val parentTask = TaskEntity(
            id = "parent_task_1",
            userId = testUserId,
            goalId = null,
            planId = null,
            parentTaskId = null,
            title = "Write Architecture Spec",
            description = "Detailed specification",
            status = TaskStatus.PENDING.name,
            priority = 1,
            estimatedDurationMinutes = 120,
            scheduledAt = null,
            dueAt = 2_000_000L,
            createdAt = 1_000_000L,
            updatedAt = 1_000_000L,
            completedAt = null,
            postponedAt = null,
            source = EntitySource.USER.name,
            schemaVersion = 1
        )
        dao.insertTask(parentTask)

        val decompAction = ProposedAction(
            id = "act_decomp_99",
            userId = testUserId,
            category = ActionCategory.TASK_DECOMPOSITION,
            title = "Decompose Architecture Spec",
            explanation = "Break down into actionable pieces",
            targetEntityId = "parent_task_1",
            targetEntityType = "TASK",
            suggestedSubTasks = listOf("Draft Introduction", "Define API Schema"),
            reviewStatus = ActionReviewStatus.ACCEPTED,
            createdAt = 1_000_000L
        )
        actionRepository.saveAction(decompAction)

        // When executed first time
        val firstResult = actionLifecycleUseCase.executeAction(decompAction, testUserId)
        assertTrue("First execution should succeed", firstResult is Result.Success)
        val executedAction = (firstResult as Result.Success).data
        assertEquals(ActionReviewStatus.EXECUTED, executedAction.reviewStatus)
        assertEquals(2, executedAction.executionResult?.resultingSubTaskIds?.size)

        // Check SQLite directly: exactly 3 tasks (1 parent + 2 subtasks)
        val tasksInDb = dao.getTasksForUser(testUserId)
        assertEquals(3, tasksInDb.size)

        // Simulate crash right before action status update: set action status back to ACCEPTED
        val rolledBackAction = decompAction.copy(reviewStatus = ActionReviewStatus.ACCEPTED)
        actionRepository.saveAction(rolledBackAction)

        // App restart simulation: new usecase instance
        val restartedUseCase = createUseCaseInstance(fakeClock, fakeIdGenerator)

        // Retrying execution on the same decomposition action
        val secondResult = restartedUseCase.executeAction(rolledBackAction, testUserId)
        assertTrue("Second execution should succeed idempotently", secondResult is Result.Success)

        // Verify SQLite still contains EXACTLY 3 tasks — zero duplicates created!
        val tasksAfterRetry = dao.getTasksForUser(testUserId)
        assertEquals("Subtasks must not be duplicated in SQLite on retry", 3, tasksAfterRetry.size)

        // Verify subtask IDs match deterministic schema
        val subtask0 = dao.getTask("act_decomp_99_sub_0")
        val subtask1 = dao.getTask("act_decomp_99_sub_1")
        assertNotNull("Subtask 0 must exist with deterministic ID", subtask0)
        assertNotNull("Subtask 1 must exist with deterministic ID", subtask1)
    }

    // =========================================================================
    // Test 2: Crash after mutation commit before action status persistence
    // =========================================================================

    @Test
    fun crash_after_mutation_commit_before_action_status_persistence_reconciles_cleanly() = runTest {
        // Given: Task in Room
        val task = TaskEntity(
            id = "task_reschedule_100",
            userId = testUserId,
            goalId = null,
            planId = null,
            parentTaskId = null,
            title = "Review PR",
            description = "Code review",
            status = TaskStatus.PENDING.name,
            priority = 2,
            estimatedDurationMinutes = 30,
            scheduledAt = null,
            dueAt = 1_500_000L,
            createdAt = 1_000_000L,
            updatedAt = 1_000_000L,
            completedAt = null,
            postponedAt = null,
            source = EntitySource.USER.name,
            schemaVersion = 1
        )
        dao.insertTask(task)

        val newDueAt = 3_000_000L
        val rescheduleAction = ProposedAction(
            id = "act_reschedule_100",
            userId = testUserId,
            category = ActionCategory.TASK_RESCHEDULE,
            title = "Reschedule PR Review",
            explanation = "Avoid conflict",
            targetEntityId = "task_reschedule_100",
            targetEntityType = "TASK",
            suggestedNewDueAt = newDueAt,
            reviewStatus = ActionReviewStatus.EXECUTING, // Crashed while in EXECUTING state
            lastAttemptedAt = 1_100_000L,
            createdAt = 1_000_000L
        )
        actionRepository.saveAction(rescheduleAction)

        // Domain mutation already committed in Room before crash
        dao.insertTask(task.copy(dueAt = newDueAt, updatedAt = 1_100_000L))

        // App restarts: new usecase instance
        val restartedUseCase = createUseCaseInstance(FakeClock(1_200_000L), fakeIdGenerator)

        // When: startup / briefing reconciliation runs
        val reconcileResult = restartedUseCase.reconcileInterruptedActions(testUserId)
        assertTrue(reconcileResult is Result.Success)
        val reconciledActions = (reconcileResult as Result.Success).data
        assertEquals(1, reconciledActions.size)

        val reconciled = reconciledActions[0]
        assertEquals("Action must transition to EXECUTED without repeating mutation", ActionReviewStatus.EXECUTED, reconciled.reviewStatus)
        assertEquals(newDueAt, reconciled.executionResult?.newDueAt)

        // Verify task in Room is unchanged and intact
        val finalTask = dao.getTask("task_reschedule_100")
        assertEquals(newDueAt, finalTask?.dueAt)

        // Verify that trying to execute the reconciled action is blocked
        val repeatExecResult = restartedUseCase.executeAction(reconciled, testUserId)
        assertTrue("Already executed action cannot be re-executed", repeatExecResult is Result.Failure)
    }

    // =========================================================================
    // Test 3: Crash before mutation commit reverts to ACCEPTED on restart
    // =========================================================================

    @Test
    fun crash_before_mutation_commit_reverts_to_accepted_for_user_control() = runTest {
        // Given: Task in Room with original dueAt
        val task = TaskEntity(
            id = "task_reschedule_200",
            userId = testUserId,
            goalId = null,
            planId = null,
            parentTaskId = null,
            title = "Deploy Service",
            description = "Production deploy",
            status = TaskStatus.PENDING.name,
            priority = 1,
            estimatedDurationMinutes = 60,
            scheduledAt = null,
            dueAt = 1_500_000L,
            createdAt = 1_000_000L,
            updatedAt = 1_000_000L,
            completedAt = null,
            postponedAt = null,
            source = EntitySource.USER.name,
            schemaVersion = 1
        )
        dao.insertTask(task)

        // Action was marked EXECUTING, but crash killed process BEFORE mutation could touch SQLite
        val action = ProposedAction(
            id = "act_reschedule_200",
            userId = testUserId,
            category = ActionCategory.TASK_RESCHEDULE,
            title = "Reschedule Deploy",
            explanation = "Deploy on weekend",
            targetEntityId = "task_reschedule_200",
            targetEntityType = "TASK",
            suggestedNewDueAt = 5_000_000L,
            reviewStatus = ActionReviewStatus.EXECUTING,
            lastAttemptedAt = 1_100_000L,
            createdAt = 1_000_000L
        )
        actionRepository.saveAction(action)

        // App restarts: new usecase instance
        val restartedUseCase = createUseCaseInstance(FakeClock(1_200_000L), fakeIdGenerator)

        // When: startup / briefing reconciliation runs
        val reconcileResult = restartedUseCase.reconcileInterruptedActions(testUserId)
        assertTrue(reconcileResult is Result.Success)
        val reconciledActions = (reconcileResult as Result.Success).data
        assertEquals(1, reconciledActions.size)

        val reconciled = reconciledActions[0]
        assertEquals("Interrupted action before mutation must revert to ACCEPTED", ActionReviewStatus.ACCEPTED, reconciled.reviewStatus)

        // Verify task in Room was NOT mutated
        val unmutatedTask = dao.getTask("task_reschedule_200")
        assertEquals(1_500_000L, unmutatedTask?.dueAt)
    }

    // =========================================================================
    // Test 4: Repeated outcome recording after restart does not duplicate outcomes
    // =========================================================================

    @Test
    fun repeated_outcome_recording_after_restart_does_not_duplicate_outcomes() = runTest {
        // Given an executed action
        val action = ProposedAction(
            id = "act_outcome_test_1",
            userId = testUserId,
            category = ActionCategory.BEHAVIOUR_PATTERN_REVIEW,
            title = "Acknowledge Pattern",
            explanation = "Confirm behavioural pattern",
            reviewStatus = ActionReviewStatus.EXECUTED,
            executedAt = 1_100_000L,
            createdAt = 1_000_000L
        )
        actionRepository.saveAction(action)

        // Record outcome first time
        val firstOutcomeRes = actionLifecycleUseCase.recordActionOutcome(
            action = action,
            userId = testUserId,
            resultStatus = ResultStatus.POSITIVE,
            actualDurationMinutes = 25
        )
        assertTrue(firstOutcomeRes is Result.Success)
        val firstOutcome = (firstOutcomeRes as Result.Success).data
        assertEquals("outcome_act_act_outcome_test_1", firstOutcome.id)

        // App restart simulation: new usecase instance
        val restartedUseCase = createUseCaseInstance(FakeClock(1_300_000L), FakeIdGenerator())

        // Record outcome second time
        val secondOutcomeRes = restartedUseCase.recordActionOutcome(
            action = action,
            userId = testUserId,
            resultStatus = ResultStatus.POSITIVE,
            actualDurationMinutes = 25
        )
        assertTrue(secondOutcomeRes is Result.Success)
        val secondOutcome = (secondOutcomeRes as Result.Success).data
        assertEquals("Must return identical outcome", firstOutcome.id, secondOutcome.id)

        // Verify SQLite: exactly ONE outcome row exists
        val outcomesInDb = dao.getOutcomesForUser(testUserId)
        assertEquals("SQLite must have exactly 1 outcome row — no duplicate outcomes", 1, outcomesInDb.size)
    }

    // =========================================================================
    // Test 5: Repeated outcome recording after restart does not duplicate learner events
    // =========================================================================

    @Test
    fun repeated_outcome_recording_after_restart_does_not_duplicate_learner_events() = runTest {
        // Given an executed action
        val action = ProposedAction(
            id = "act_outcome_events_1",
            userId = testUserId,
            category = ActionCategory.BEHAVIOUR_PATTERN_REVIEW,
            title = "Acknowledge Pattern",
            explanation = "Pattern acknowledgement",
            reviewStatus = ActionReviewStatus.EXECUTED,
            executedAt = 1_100_000L,
            createdAt = 1_000_000L
        )
        actionRepository.saveAction(action)

        // First outcome recording
        actionLifecycleUseCase.recordActionOutcome(
            action = action,
            userId = testUserId,
            resultStatus = ResultStatus.POSITIVE,
            actualDurationMinutes = 15
        )

        // App restart: new usecase instance with new ID generator
        val restartedUseCase = createUseCaseInstance(FakeClock(1_400_000L), FakeIdGenerator())

        // Second outcome recording
        restartedUseCase.recordActionOutcome(
            action = action,
            userId = testUserId,
            resultStatus = ResultStatus.POSITIVE,
            actualDurationMinutes = 15
        )

        // Verify SQLite events table: causationId == action.id must have exactly 1 event!
        val eventsInDb = dao.getEventsByCausationId(testUserId, "act_outcome_events_1")
        assertEquals("Must not create duplicate learner events in SQLite", 1, eventsInDb.size)
        assertEquals("evt_outcome_act_act_outcome_events_1", eventsInDb[0].id)
    }

    // =========================================================================
    // Test 6: Older outcome for same task is NOT reused by new action
    // =========================================================================

    @Test
    fun older_outcome_for_same_task_is_not_incorrectly_reused_by_new_action() = runTest {
        val taskId = "shared_task_123"

        // Insert an older outcome in Room belonging directly to this task
        val oldOutcome = OutcomeEntity(
            id = "old_task_outcome_999",
            userId = testUserId,
            sourceEntityId = taskId, // Task ID was the source of this old outcome
            sourceEntityType = "TASK",
            resultStatus = ResultStatus.POSITIVE.name,
            actualDurationMinutes = 45,
            completedAt = 500_000L,
            createdAt = 500_000L,
            schemaVersion = 1
        )
        dao.insertOutcome(oldOutcome)

        // A new action targets the same task
        val newAction = ProposedAction(
            id = "act_new_on_shared_task",
            userId = testUserId,
            category = ActionCategory.TASK_RESCHEDULE,
            title = "Reschedule Task",
            explanation = "Rescheduling task",
            targetEntityId = taskId,
            targetEntityType = "TASK",
            reviewStatus = ActionReviewStatus.EXECUTED,
            executedAt = 1_000_000L,
            createdAt = 900_000L
        )
        actionRepository.saveAction(newAction)

        // Record outcome on the new action
        val outcomeResult = actionLifecycleUseCase.recordActionOutcome(
            action = newAction,
            userId = testUserId,
            resultStatus = ResultStatus.POSITIVE,
            actualDurationMinutes = 10
        )

        assertTrue(outcomeResult is Result.Success)
        val recordedOutcome = (outcomeResult as Result.Success).data

        // Strict action-scoping: It must NOT reuse "old_task_outcome_999"!
        assertNotEquals("New action must NOT reuse older task outcome", "old_task_outcome_999", recordedOutcome.id)
        assertEquals("outcome_act_act_new_on_shared_task", recordedOutcome.id)
        assertEquals("act_new_on_shared_task", recordedOutcome.sourceEntityId)

        // Verify Room SQLite has BOTH outcomes distinct and preserved
        val allOutcomes = dao.getOutcomesForUser(testUserId)
        assertEquals("Both old task outcome and new action outcome must exist in SQLite", 2, allOutcomes.size)
    }

    // =========================================================================
    // Test 7: Unapproved actions cannot be executed
    // =========================================================================

    @Test
    fun unapproved_actions_are_blocked_from_execution() = runTest {
        val proposedAction = ProposedAction(
            id = "act_proposed",
            userId = testUserId,
            category = ActionCategory.BEHAVIOUR_PATTERN_REVIEW,
            title = "Review Pattern",
            explanation = "Not approved yet",
            reviewStatus = ActionReviewStatus.PROPOSED,
            createdAt = 1_000_000L
        )
        actionRepository.saveAction(proposedAction)

        val rejectedAction = proposedAction.copy(
            id = "act_rejected",
            reviewStatus = ActionReviewStatus.REJECTED
        )
        actionRepository.saveAction(rejectedAction)

        val dismissedAction = proposedAction.copy(
            id = "act_dismissed",
            reviewStatus = ActionReviewStatus.DISMISSED
        )
        actionRepository.saveAction(dismissedAction)

        assertTrue("PROPOSED cannot execute", actionLifecycleUseCase.executeAction(proposedAction, testUserId) is Result.Failure)
        assertTrue("REJECTED cannot execute", actionLifecycleUseCase.executeAction(rejectedAction, testUserId) is Result.Failure)
        assertTrue("DISMISSED cannot execute", actionLifecycleUseCase.executeAction(dismissedAction, testUserId) is Result.Failure)
    }
}
