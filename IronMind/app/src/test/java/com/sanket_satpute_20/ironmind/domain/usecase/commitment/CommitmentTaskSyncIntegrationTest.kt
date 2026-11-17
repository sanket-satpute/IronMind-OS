package com.sanket_satpute_20.ironmind.domain.usecase.commitment

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.sanket_satpute_20.ironmind.data.local.IronMindDatabase
import com.sanket_satpute_20.ironmind.data.local.dao.OutboxDao
import com.sanket_satpute_20.ironmind.data.local.entity.OutboxEntity
import com.sanket_satpute_20.ironmind.data.repository.CommitmentRepositoryImpl
import com.sanket_satpute_20.ironmind.data.repository.EventRepositoryImpl
import com.sanket_satpute_20.ironmind.data.repository.OutcomeRepositoryImpl
import com.sanket_satpute_20.ironmind.data.repository.TaskRepositoryImpl
import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.Commitment
import com.sanket_satpute_20.ironmind.domain.model.CommitmentStatus
import com.sanket_satpute_20.ironmind.domain.model.EntitySource
import com.sanket_satpute_20.ironmind.domain.model.EventType
import com.sanket_satpute_20.ironmind.domain.model.ResultStatus
import com.sanket_satpute_20.ironmind.domain.model.Task
import com.sanket_satpute_20.ironmind.domain.model.TaskStatus
import com.sanket_satpute_20.ironmind.testutil.fake.FakeClock
import com.sanket_satpute_20.ironmind.testutil.fake.FakeIdGenerator
import com.sanket_satpute_20.ironmind.testutil.fake.FakeReminderScheduler
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CommitmentTaskSyncIntegrationTest {

    private lateinit var context: Context
    private lateinit var db: IronMindDatabase
    private lateinit var delegatingOutboxDao: DelegatingFailingOutboxDao
    private lateinit var taskRepository: TaskRepositoryImpl
    private lateinit var commitmentRepository: CommitmentRepositoryImpl
    private lateinit var outcomeRepository: OutcomeRepositoryImpl
    private lateinit var eventRepository: EventRepositoryImpl

    private lateinit var clock: FakeClock
    private lateinit var idGenerator: FakeIdGenerator
    private lateinit var reminderScheduler: FakeReminderScheduler
    private lateinit var updateCommitmentStatusUseCase: UpdateCommitmentStatusUseCase

    private class DelegatingFailingOutboxDao(private val delegate: OutboxDao) : OutboxDao by delegate {
        var shouldFail: Boolean = false

        override fun insertEntry(entry: OutboxEntity) {
            if (shouldFail) {
                throw android.database.sqlite.SQLiteException("Simulated outbox persistence failure for testing rollback")
            }
            delegate.insertEntry(entry)
        }
    }

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        val directExecutor = java.util.concurrent.Executor { it.run() }
        db = Room.inMemoryDatabaseBuilder(context, IronMindDatabase::class.java)
            .allowMainThreadQueries()
            .setQueryExecutor(directExecutor)
            .setTransactionExecutor(directExecutor)
            .build()

        delegatingOutboxDao = DelegatingFailingOutboxDao(db.outboxDao())
        clock = FakeClock(1000L)
        idGenerator = FakeIdGenerator("test-id-")
        reminderScheduler = FakeReminderScheduler()

        taskRepository = TaskRepositoryImpl(
            dao = db.ironMindDao(),
            outboxDao = delegatingOutboxDao,
            database = db,
            idGenerator = idGenerator,
            backgroundExecutor = null
        )

        commitmentRepository = CommitmentRepositoryImpl(dao = db.ironMindDao())
        outcomeRepository = OutcomeRepositoryImpl(dao = db.ironMindDao())
        eventRepository = EventRepositoryImpl(dao = db.ironMindDao(), database = db)

        updateCommitmentStatusUseCase = UpdateCommitmentStatusUseCase(
            repository = commitmentRepository,
            outcomeRepository = outcomeRepository,
            reminderScheduler = reminderScheduler,
            clock = clock,
            idGenerator = idGenerator,
            eventRepository = eventRepository,
            taskRepository = taskRepository
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun createDomainTask(
        id: String = "task-1",
        userId: String = "user-1",
        status: TaskStatus = TaskStatus.PENDING,
        completedAt: Long? = null
    ): Task {
        return Task(
            id = id,
            userId = userId,
            goalId = "goal-1",
            planId = "plan-1",
            parentTaskId = null,
            title = "Task $id",
            description = "Description for $id",
            status = status,
            priority = 1,
            estimatedDurationMinutes = 30,
            scheduledAt = 1000L,
            dueAt = 5000L,
            createdAt = 1000L,
            updatedAt = 1000L,
            completedAt = completedAt,
            postponedAt = null,
            source = EntitySource.USER
        )
    }

    private fun createDomainCommitment(
        id: String = "comm-1",
        userId: String = "user-1",
        taskId: String? = "task-1",
        status: CommitmentStatus = CommitmentStatus.COMMITTED,
        startedAt: Long? = null,
        completedAt: Long? = null
    ): Commitment {
        return Commitment(
            id = id,
            userId = userId,
            goalId = "goal-1",
            planId = "plan-1",
            taskId = taskId,
            parentCommitmentId = null,
            title = "Commitment $id",
            description = "Description for $id",
            committedAt = 1000L,
            scheduledStartAt = 1000L,
            scheduledEndAt = 2000L,
            status = status,
            priority = 1,
            source = EntitySource.USER,
            createdAt = 1000L,
            updatedAt = 1000L,
            startedAt = startedAt,
            completedAt = completedAt,
            postponedAt = null,
            missedAt = null,
            recoveredAt = null
        )
    }

    @Test
    fun test1_completingCommitmentWithLinkedTask_updatesTaskToCompleted() = runTest {
        val task = createDomainTask(id = "task-1", userId = "user-1", status = TaskStatus.PENDING)
        taskRepository.saveTask(task)

        val commitment = createDomainCommitment(
            id = "comm-1",
            userId = "user-1",
            taskId = "task-1",
            status = CommitmentStatus.COMMITTED
        )
        commitmentRepository.saveCommitment(commitment)

        clock.setTime(2000L)
        val startedResult = updateCommitmentStatusUseCase("comm-1", CommitmentStatus.STARTED)
        assertTrue(startedResult is Result.Success)
        val taskMid = (taskRepository.getTask("task-1") as Result.Success).data
        assertNotNull(taskMid)
        assertEquals(TaskStatus.PENDING, taskMid!!.status)

        clock.setTime(3000L)
        val completedResult = updateCommitmentStatusUseCase("comm-1", CommitmentStatus.COMPLETED)
        assertTrue(completedResult is Result.Success)
        val updatedComm = (completedResult as Result.Success).data
        assertEquals(CommitmentStatus.COMPLETED, updatedComm.status)
        assertEquals(3000L, updatedComm.completedAt)

        val taskFinal = (taskRepository.getTask("task-1") as Result.Success).data
        assertNotNull(taskFinal)
        assertEquals(TaskStatus.COMPLETED, taskFinal!!.status)
        assertEquals(3000L, taskFinal.completedAt)
        assertEquals(3000L, taskFinal.updatedAt)
    }

    @Test
    fun test2_taskUpdateProducesCorrectOutboxEntityPayloadAndOwnership() = runTest {
        val task = createDomainTask(id = "task-outbox", userId = "user-alice", status = TaskStatus.PENDING)
        taskRepository.saveTask(task)

        // Clear out initial task insertion outbox entry to isolate commitment completion outbox write
        val initialEntries = delegatingOutboxDao.getAllEntries()
        initialEntries.forEach { delegatingOutboxDao.deleteEntry(it.operationId) }
        assertEquals(0, delegatingOutboxDao.getAllEntries().size)

        val commitment = createDomainCommitment(
            id = "comm-outbox",
            userId = "user-alice",
            taskId = "task-outbox",
            status = CommitmentStatus.STARTED,
            startedAt = 1500L
        )
        commitmentRepository.saveCommitment(commitment)

        clock.setTime(4000L)
        val result = updateCommitmentStatusUseCase("comm-outbox", CommitmentStatus.COMPLETED)
        assertTrue(result is Result.Success)

        val entries = delegatingOutboxDao.getAllEntries().filter { it.entityType == "Task" && it.entityId == "task-outbox" }
        assertEquals(1, entries.size)
        val outbox = entries.first()
        assertEquals("UPSERT", outbox.operationType)
        assertEquals("PENDING", outbox.status)

        val payload = JSONObject(outbox.payload)
        assertEquals("task-outbox", payload.getString("id"))
        assertEquals("user-alice", payload.getString("userId"))
        assertEquals("COMPLETED", payload.getString("status"))
        assertEquals(4000L, payload.getLong("completedAt"))
        assertEquals(4000L, payload.getLong("updatedAt"))
    }

    @Test
    fun test3_taskUpdateAndOutboxWrite_rollBackTogetherOnTransactionFailure() = runTest {
        val task = createDomainTask(id = "task-rollback", userId = "user-1", status = TaskStatus.PENDING)
        taskRepository.saveTask(task)

        val commitment = createDomainCommitment(
            id = "comm-rollback",
            userId = "user-1",
            taskId = "task-rollback",
            status = CommitmentStatus.STARTED,
            startedAt = 1000L
        )
        commitmentRepository.saveCommitment(commitment)

        delegatingOutboxDao.shouldFail = true
        clock.setTime(5000L)

        val result = updateCommitmentStatusUseCase("comm-rollback", CommitmentStatus.COMPLETED)
        assertTrue("Expected failure when outbox write fails", result is Result.Failure)

        // Verify task status was rolled back and is STILL PENDING in database
        val taskInDb = (taskRepository.getTask("task-rollback") as Result.Success).data
        assertNotNull(taskInDb)
        assertEquals(TaskStatus.PENDING, taskInDb!!.status)
        assertNull(taskInDb.completedAt)

        // Verify no completed outbox entry was recorded
        val outboxEntries = db.outboxDao().getAllEntries().filter {
            it.entityType == "Task" && it.entityId == "task-rollback" && it.payload.contains("COMPLETED")
        }
        assertEquals(0, outboxEntries.size)
    }

    @Test
    fun test4_commitmentWithoutTaskId_preservesCurrentBehavior() = runTest {
        val commitment = createDomainCommitment(
            id = "comm-no-task",
            userId = "user-1",
            taskId = null,
            status = CommitmentStatus.STARTED,
            startedAt = 1000L
        )
        commitmentRepository.saveCommitment(commitment)

        clock.setTime(2000L)
        val result = updateCommitmentStatusUseCase("comm-no-task", CommitmentStatus.COMPLETED)
        assertTrue(result is Result.Success)
        val comm = (result as Result.Success).data
        assertEquals(CommitmentStatus.COMPLETED, comm.status)
        assertNull(comm.taskId)

        // Outbox should not contain any task operations
        val taskOutboxEntries = delegatingOutboxDao.getAllEntries().filter { it.entityType == "Task" }
        assertEquals(0, taskOutboxEntries.size)
    }

    @Test
    fun test5_missingTask_handledSafelyWithoutFailure() = runTest {
        val commitment = createDomainCommitment(
            id = "comm-missing",
            userId = "user-1",
            taskId = "non-existent-task-id",
            status = CommitmentStatus.STARTED,
            startedAt = 1000L
        )
        commitmentRepository.saveCommitment(commitment)

        clock.setTime(2500L)
        val result = updateCommitmentStatusUseCase("comm-missing", CommitmentStatus.COMPLETED)
        assertTrue("Missing task should not cause commitment completion to fail", result is Result.Success)
        val comm = (result as Result.Success).data
        assertEquals(CommitmentStatus.COMPLETED, comm.status)
    }

    @Test
    fun test6_crossUserTaskOwnership_neverMutated() = runTest {
        val foreignTask = createDomainTask(
            id = "task-foreign",
            userId = "user-2",
            status = TaskStatus.PENDING
        )
        taskRepository.saveTask(foreignTask)

        // Commitment belongs to user-1, but references user-2's task
        val commitment = createDomainCommitment(
            id = "comm-user1",
            userId = "user-1",
            taskId = "task-foreign",
            status = CommitmentStatus.STARTED,
            startedAt = 1000L
        )
        commitmentRepository.saveCommitment(commitment)

        clock.setTime(3000L)
        val result = updateCommitmentStatusUseCase("comm-user1", CommitmentStatus.COMPLETED)
        assertTrue(result is Result.Success)

        // Foreign task must NOT be modified
        val fetchedForeignTask = (taskRepository.getTask("task-foreign") as Result.Success).data
        assertNotNull(fetchedForeignTask)
        assertEquals("user-2", fetchedForeignTask!!.userId)
        assertEquals(TaskStatus.PENDING, fetchedForeignTask.status)
        assertNull(fetchedForeignTask.completedAt)

        // No completed outbox entry for foreign task
        val outboxEntries = delegatingOutboxDao.getAllEntries().filter {
            it.entityType == "Task" && it.entityId == "task-foreign" && it.payload.contains("COMPLETED")
        }
        assertEquals(0, outboxEntries.size)
    }

    @Test
    fun test7_nonCompletedCommitmentTransitions_doNotCompleteTask() = runTest {
        val task = createDomainTask(id = "task-transitions", userId = "user-1", status = TaskStatus.PENDING)
        taskRepository.saveTask(task)

        val commitment = createDomainCommitment(
            id = "comm-transitions",
            userId = "user-1",
            taskId = "task-transitions",
            status = CommitmentStatus.COMMITTED
        )
        commitmentRepository.saveCommitment(commitment)

        // Transition: COMMITTED -> STARTED
        clock.setTime(1100L)
        updateCommitmentStatusUseCase("comm-transitions", CommitmentStatus.STARTED)
        var currentTask = (taskRepository.getTask("task-transitions") as Result.Success).data!!
        assertEquals(TaskStatus.PENDING, currentTask.status)
        assertNull(currentTask.completedAt)

        // Transition: STARTED -> POSTPONED
        clock.setTime(1200L)
        updateCommitmentStatusUseCase("comm-transitions", CommitmentStatus.POSTPONED)
        currentTask = (taskRepository.getTask("task-transitions") as Result.Success).data!!
        assertEquals(TaskStatus.PENDING, currentTask.status)
        assertNull(currentTask.completedAt)

        // Transition: POSTPONED -> COMMITTED
        clock.setTime(1300L)
        updateCommitmentStatusUseCase("comm-transitions", CommitmentStatus.COMMITTED)
        currentTask = (taskRepository.getTask("task-transitions") as Result.Success).data!!
        assertEquals(TaskStatus.PENDING, currentTask.status)

        // Transition: COMMITTED -> MISSED
        clock.setTime(1400L)
        updateCommitmentStatusUseCase("comm-transitions", CommitmentStatus.MISSED)
        currentTask = (taskRepository.getTask("task-transitions") as Result.Success).data!!
        assertEquals(TaskStatus.PENDING, currentTask.status)

        // Transition: MISSED -> ABANDONED
        clock.setTime(1500L)
        updateCommitmentStatusUseCase("comm-transitions", CommitmentStatus.ABANDONED)
        currentTask = (taskRepository.getTask("task-transitions") as Result.Success).data!!
        assertEquals(TaskStatus.PENDING, currentTask.status)
        assertNull(currentTask.completedAt)
    }

    @Test
    fun test8_existingOutcomeAndEventBehavior_remainsIntact() = runTest {
        val task = createDomainTask(id = "task-audit", userId = "user-1", status = TaskStatus.PENDING)
        taskRepository.saveTask(task)

        val commitment = createDomainCommitment(
            id = "comm-audit",
            userId = "user-1",
            taskId = "task-audit",
            status = CommitmentStatus.STARTED,
            startedAt = 1000L
        )
        commitmentRepository.saveCommitment(commitment)
        reminderScheduler.scheduleReminder("comm-audit", 5000L, "Do the task")

        clock.setTime(6000L)
        val result = updateCommitmentStatusUseCase(
            commitmentId = "comm-audit",
            newStatus = CommitmentStatus.COMPLETED,
            resultStatus = ResultStatus.POSITIVE,
            actualDurationMinutes = 45
        )
        assertTrue(result is Result.Success)

        // 1. Reminder cancellation
        assertTrue(reminderScheduler.cancelledReminders.contains("comm-audit"))

        // 2. Outcome persistence
        val outcomeResult = outcomeRepository.getOutcomeForSource("comm-audit")
        assertTrue(outcomeResult is Result.Success)
        val outcome = (outcomeResult as Result.Success).data
        assertNotNull(outcome)
        assertEquals("user-1", outcome!!.userId)
        assertEquals("comm-audit", outcome.sourceEntityId)
        assertEquals("COMMITMENT", outcome.sourceEntityType)
        assertEquals(ResultStatus.POSITIVE, outcome.resultStatus)
        assertEquals(45, outcome.actualDurationMinutes)
        assertEquals(6000L, outcome.completedAt)

        // 3. Event persistence
        val events = db.ironMindDao().getEventsForEntity("comm-audit")
        val transitionEvent = events.find { it.type == EventType.COMMITMENT_COMPLETED.name }
        assertNotNull("Transition event must be recorded", transitionEvent)
        assertEquals("STARTED", transitionEvent!!.previousState)
        assertEquals("COMPLETED", transitionEvent.newState)

        val outcomeEvent = events.find { it.type == EventType.OUTCOME_RECORDED.name }
        assertNotNull("Outcome recorded event must be recorded", outcomeEvent)
        assertEquals("resultStatus=POSITIVE", outcomeEvent!!.metadata)

        // 4. Task updated as well
        val taskFinal = (taskRepository.getTask("task-audit") as Result.Success).data!!
        assertEquals(TaskStatus.COMPLETED, taskFinal.status)
        assertEquals(6000L, taskFinal.completedAt)
    }

    @Test
    fun test9_multipleCommitmentsReferencingSameTask_handledSafely() = runTest {
        val task = createDomainTask(id = "task-shared", userId = "user-1", status = TaskStatus.PENDING)
        taskRepository.saveTask(task)

        // Clear out initial task insertion outbox entry
        delegatingOutboxDao.getAllEntries().forEach { delegatingOutboxDao.deleteEntry(it.operationId) }

        // Two commitments referencing the exact same task
        val comm1 = createDomainCommitment(
            id = "comm-shift-1",
            userId = "user-1",
            taskId = "task-shared",
            status = CommitmentStatus.COMMITTED
        )
        val comm2 = createDomainCommitment(
            id = "comm-shift-2",
            userId = "user-1",
            taskId = "task-shared",
            status = CommitmentStatus.COMMITTED
        )
        commitmentRepository.saveCommitment(comm1)
        commitmentRepository.saveCommitment(comm2)

        // 1. First commitment starts and completes at T=2000
        clock.setTime(2000L)
        updateCommitmentStatusUseCase("comm-shift-1", CommitmentStatus.STARTED)
        val compResult1 = updateCommitmentStatusUseCase("comm-shift-1", CommitmentStatus.COMPLETED)
        assertTrue(compResult1 is Result.Success)

        val taskAfterFirst = (taskRepository.getTask("task-shared") as Result.Success).data!!
        assertEquals(TaskStatus.COMPLETED, taskAfterFirst.status)
        assertEquals(2000L, taskAfterFirst.completedAt)

        val outboxEntriesAfterFirst = delegatingOutboxDao.getAllEntries().filter {
            it.entityType == "Task" && it.entityId == "task-shared"
        }
        assertEquals(1, outboxEntriesAfterFirst.size)

        // 2. Second commitment starts and completes later at T=5000
        clock.setTime(5000L)
        updateCommitmentStatusUseCase("comm-shift-2", CommitmentStatus.STARTED)
        val compResult2 = updateCommitmentStatusUseCase("comm-shift-2", CommitmentStatus.COMPLETED)
        assertTrue(compResult2 is Result.Success)

        // Task should remain completed, keeping original completedAt=2000L, and NO duplicate outbox churn
        val taskAfterSecond = (taskRepository.getTask("task-shared") as Result.Success).data!!
        assertEquals(TaskStatus.COMPLETED, taskAfterSecond.status)
        assertEquals(2000L, taskAfterSecond.completedAt)

        val outboxEntriesAfterSecond = delegatingOutboxDao.getAllEntries().filter {
            it.entityType == "Task" && it.entityId == "task-shared"
        }
        assertEquals(1, outboxEntriesAfterSecond.size)

        // 3. Third commitment referencing the same task is missed: task must NOT be reset to PENDING
        val comm3 = createDomainCommitment(
            id = "comm-shift-3",
            userId = "user-1",
            taskId = "task-shared",
            status = CommitmentStatus.COMMITTED
        )
        commitmentRepository.saveCommitment(comm3)

        clock.setTime(7000L)
        val missedResult = updateCommitmentStatusUseCase("comm-shift-3", CommitmentStatus.MISSED)
        assertTrue(missedResult is Result.Success)

        val taskAfterMissed = (taskRepository.getTask("task-shared") as Result.Success).data!!
        assertEquals(TaskStatus.COMPLETED, taskAfterMissed.status)
        assertEquals(2000L, taskAfterMissed.completedAt)
    }
}

