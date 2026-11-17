package com.sanket_satpute_20.ironmind.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.sanket_satpute_20.ironmind.data.local.IronMindDatabase
import com.sanket_satpute_20.ironmind.data.local.dao.IronMindDao
import com.sanket_satpute_20.ironmind.data.local.dao.OutboxDao
import com.sanket_satpute_20.ironmind.data.local.entity.OutboxEntity
import com.sanket_satpute_20.ironmind.data.local.entity.TaskEntity
import com.sanket_satpute_20.ironmind.domain.common.IdGenerator
import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.EntitySource
import com.sanket_satpute_20.ironmind.domain.model.OutboxOperationType
import com.sanket_satpute_20.ironmind.domain.model.SyncStatus
import com.sanket_satpute_20.ironmind.domain.model.Task
import com.sanket_satpute_20.ironmind.domain.model.TaskStatus
import io.mockk.mockk
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
class AtomicTaskOutboxPersistenceTest {

    private lateinit var context: Context
    private lateinit var db: IronMindDatabase
    private lateinit var repository: TaskRepositoryImpl
    private lateinit var idGenerator: SequentialIdGenerator

    class SequentialIdGenerator(private val prefix: String = "op-") : IdGenerator {
        private var counter = 1
        override fun generateId(): String = "$prefix${counter++}"
    }

    private class DelegatingFailingOutboxDao(private val delegate: OutboxDao) : OutboxDao by delegate {
        var shouldFail: Boolean = false
        override fun insertEntry(entry: OutboxEntity) {
            if (shouldFail) {
                throw android.database.sqlite.SQLiteException("Simulated outbox persistence failure")
            }
            delegate.insertEntry(entry)
        }
    }

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, IronMindDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        idGenerator = SequentialIdGenerator("op-task-")
        repository = TaskRepositoryImpl(
            dao = db.ironMindDao(),
            outboxDao = db.outboxDao(),
            database = db,
            idGenerator = idGenerator
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun createTestTask(
        id: String = "task-1",
        userId: String = "user-1",
        goalId: String? = "goal-1",
        planId: String? = "plan-1",
        parentTaskId: String? = null,
        title: String = "Draft initial design",
        description: String = "Write design document",
        status: TaskStatus = TaskStatus.PENDING,
        priority: Int = 1,
        estimatedDurationMinutes: Int? = 45,
        scheduledAt: Long? = 2000L,
        dueAt: Long? = 5000L,
        createdAt: Long = 1000L,
        updatedAt: Long = 1000L,
        completedAt: Long? = null,
        postponedAt: Long? = null,
        source: EntitySource = EntitySource.USER
    ): Task = Task(
        id = id,
        userId = userId,
        goalId = goalId,
        planId = planId,
        parentTaskId = parentTaskId,
        title = title,
        description = description,
        status = status,
        priority = priority,
        estimatedDurationMinutes = estimatedDurationMinutes,
        scheduledAt = scheduledAt,
        dueAt = dueAt,
        createdAt = createdAt,
        updatedAt = updatedAt,
        completedAt = completedAt,
        postponedAt = postponedAt,
        source = source
    )

    @Test
    fun `successful task creation persists task and exactly one matching pending outbox entry`() = runTest {
        val task = createTestTask()

        val saveResult = repository.saveTask(task)
        assertTrue("saveTask should succeed", saveResult is Result.Success)

        // 1. Verify Task table in Room
        val savedTask = db.ironMindDao().getTask("task-1")
        assertNotNull("Task must exist in task table", savedTask)
        assertEquals("task-1", savedTask!!.id)
        assertEquals("user-1", savedTask.userId)
        assertEquals("goal-1", savedTask.goalId)
        assertEquals("plan-1", savedTask.planId)
        assertEquals("Draft initial design", savedTask.title)
        assertEquals(TaskStatus.PENDING.name, savedTask.status)
        assertEquals(1, savedTask.priority)
        assertEquals(45, savedTask.estimatedDurationMinutes)

        // 2. Verify Outbox table in Room
        val pendingEntries = db.outboxDao().getPendingEntries()
        assertEquals("Exactly one outbox entry must be created", 1, pendingEntries.size)

        val outbox = pendingEntries[0]
        assertEquals("op-task-1", outbox.operationId)
        assertEquals("Task", outbox.entityType)
        assertEquals("task-1", outbox.entityId)
        assertEquals(OutboxOperationType.UPSERT.name, outbox.operationType)
        assertEquals(SyncStatus.PENDING.name, outbox.status)
        assertEquals(0, outbox.retryCount)
        assertNull(outbox.lastAttemptAt)
        assertEquals(task.createdAt, outbox.createdAt)
    }

    @Test
    fun `outbox payload contains expected task identity and fields according to serialization contract`() = runTest {
        val task = createTestTask()

        val saveResult = repository.saveTask(task)
        assertTrue(saveResult is Result.Success)

        val outbox = db.outboxDao().getPendingEntries()[0]
        val json = JSONObject(outbox.payload)

        assertEquals("task-1", json.getString("id"))
        assertEquals("user-1", json.getString("userId"))
        assertEquals("goal-1", json.getString("goalId"))
        assertEquals("plan-1", json.getString("planId"))
        assertTrue("parentTaskId should not be set when null", !json.has("parentTaskId"))
        assertEquals("Draft initial design", json.getString("title"))
        assertEquals("Write design document", json.getString("description"))
        assertEquals("PENDING", json.getString("status"))
        assertEquals(1, json.getInt("priority"))
        assertEquals(45, json.getInt("estimatedDurationMinutes"))
        assertEquals(2000L, json.getLong("scheduledAt"))
        assertEquals(5000L, json.getLong("dueAt"))
        assertEquals(1000L, json.getLong("createdAt"))
        assertEquals(1000L, json.getLong("updatedAt"))
        assertTrue("completedAt should be null in JSON", json.isNull("completedAt"))
        assertTrue("postponedAt should be null in JSON", json.isNull("postponedAt"))
        assertEquals("USER", json.getString("source"))
    }

    @Test
    fun `supported task state transitions persist task and produce valid outbox upsert entries`() = runTest {
        val baseTask = createTestTask()
        repository.saveTask(baseTask)

        val transitions = listOf(
            TaskStatus.IN_PROGRESS to { t: Task -> t.copy(status = TaskStatus.IN_PROGRESS, updatedAt = 1100L) },
            TaskStatus.POSTPONED to { t: Task -> t.copy(status = TaskStatus.POSTPONED, postponedAt = 1200L, updatedAt = 1200L) },
            TaskStatus.COMPLETED to { t: Task -> t.copy(status = TaskStatus.COMPLETED, completedAt = 1300L, updatedAt = 1300L) },
            TaskStatus.FAILED to { t: Task -> t.copy(status = TaskStatus.FAILED, completedAt = 1400L, updatedAt = 1400L) },
            TaskStatus.CANCELLED to { t: Task -> t.copy(status = TaskStatus.CANCELLED, updatedAt = 1500L) }
        )

        var current = baseTask
        for ((expectedStatus, transitionFn) in transitions) {
            current = transitionFn(current)
            val result = repository.saveTask(current)
            assertTrue("Transition to $expectedStatus should succeed", result is Result.Success)

            val roomTask = db.ironMindDao().getTask("task-1")
            assertEquals(expectedStatus.name, roomTask!!.status)
        }

        // 1 initial + 5 transitions = 6 outbox records
        val allOutbox = db.outboxDao().getPendingEntries()
        assertEquals(6, allOutbox.size)

        val finalOutbox = allOutbox.last()
        val json = JSONObject(finalOutbox.payload)
        assertEquals("CANCELLED", json.getString("status"))
        assertEquals(1500L, json.getLong("updatedAt"))
    }

    @Test
    fun `failure during outbox insertion rolls back task insertion`() = runTest {
        val failingOutboxDao = DelegatingFailingOutboxDao(db.outboxDao())
        val transactionalRepo = TaskRepositoryImpl(
            dao = db.ironMindDao(),
            outboxDao = failingOutboxDao,
            database = db,
            idGenerator = idGenerator
        )

        failingOutboxDao.shouldFail = true
        val task = createTestTask("task-rollback", "user-1")

        val result = transactionalRepo.saveTask(task)
        assertTrue("saveTask must return Failure on outbox exception", result is Result.Failure)

        // Verify rollback: Task must NOT exist in Room
        val persistedTask = db.ironMindDao().getTask("task-rollback")
        assertNull("Task insertion must be rolled back on outbox failure", persistedTask)

        // Verify outbox remains empty
        val outboxEntries = db.outboxDao().getPendingEntries()
        assertTrue("Outbox must be empty", outboxEntries.isEmpty())
    }

    @Test
    fun `saveTask with null outboxDao succeeds locally and does not fail`() = runTest {
        val localRepo = TaskRepositoryImpl(
            dao = db.ironMindDao(),
            outboxDao = null,
            database = null
        )

        val task = createTestTask("task-local-only")
        val result = localRepo.saveTask(task)
        assertTrue(result is Result.Success)

        assertNotNull(db.ironMindDao().getTask("task-local-only"))
        assertTrue("Outbox must remain empty when outboxDao is null", db.outboxDao().getPendingEntries().isEmpty())
    }

    @Test
    fun `completed task transitioning to IN_PROGRESS clears completedAt in Room and outbox payload`() = runTest {
        val completedTask = createTestTask(
            status = TaskStatus.COMPLETED,
            completedAt = 1000L,
            updatedAt = 1000L
        )
        repository.saveTask(completedTask)

        // Verify initial completed state
        val initialOutbox = db.outboxDao().getPendingEntries()[0]
        val initialJson = JSONObject(initialOutbox.payload)
        assertEquals(1000L, initialJson.getLong("completedAt"))

        // Transition back to IN_PROGRESS (completedAt cleared to null)
        val inProgressTask = completedTask.copy(
            status = TaskStatus.IN_PROGRESS,
            completedAt = null,
            updatedAt = 2000L
        )
        val updateResult = repository.saveTask(inProgressTask)
        assertTrue(updateResult is Result.Success)

        // Room entity has completedAt == null
        val roomTask = db.ironMindDao().getTask("task-1")
        assertNotNull(roomTask)
        assertEquals(TaskStatus.IN_PROGRESS.name, roomTask!!.status)
        assertNull(roomTask.completedAt)

        // Second outbox entry has completedAt serialized as null
        val entries = db.outboxDao().getPendingEntries()
        assertEquals(2, entries.size)
        val latestOutbox = entries.first { it.operationId == "op-task-2" }
        val latestJson = JSONObject(latestOutbox.payload)
        assertTrue("completedAt must be null in JSON", latestJson.isNull("completedAt"))

        // FirestoreSyncRepository converts payload to Map with completedAt = null
        val syncRepo = FirestoreSyncRepository(mockk(relaxed = true))
        val parsed = syncRepo.parsePayload(latestOutbox.payload)
        assertTrue("completedAt must be present in parsed map", parsed.containsKey("completedAt"))
        assertNull("completedAt must be null in parsed map to clear Firestore document", parsed["completedAt"])
    }

    @Test
    fun `postponed task transitioning to COMPLETED sets completedAt and clears postponedAt`() = runTest {
        val postponedTask = createTestTask(
            status = TaskStatus.POSTPONED,
            postponedAt = 1200L,
            completedAt = null,
            updatedAt = 1200L
        )
        repository.saveTask(postponedTask)

        // Transition to COMPLETED (completedAt set, postponedAt cleared to null)
        val completedTask = postponedTask.copy(
            status = TaskStatus.COMPLETED,
            completedAt = 2500L,
            postponedAt = null,
            updatedAt = 2500L
        )
        val updateResult = repository.saveTask(completedTask)
        assertTrue(updateResult is Result.Success)

        // Room entity has completedAt set and postponedAt null
        val roomTask = db.ironMindDao().getTask("task-1")
        assertNotNull(roomTask)
        assertEquals(2500L, roomTask!!.completedAt)
        assertNull(roomTask.postponedAt)

        // Second outbox entry has completedAt = 2500L and postponedAt as null
        val latestOutbox = db.outboxDao().getPendingEntries().first { it.operationId == "op-task-2" }
        val latestJson = JSONObject(latestOutbox.payload)
        assertEquals(2500L, latestJson.getLong("completedAt"))
        assertTrue("postponedAt must be null in JSON", latestJson.isNull("postponedAt"))

        val syncRepo = FirestoreSyncRepository(mockk(relaxed = true))
        val parsed = syncRepo.parsePayload(latestOutbox.payload)
        assertEquals(2500L, (parsed["completedAt"] as? Number)?.toLong())
        assertTrue("postponedAt must be present in parsed map", parsed.containsKey("postponedAt"))
        assertNull("postponedAt must be null in parsed map to clear Firestore document", parsed["postponedAt"])
    }

    @Test
    fun `existing non-null timestamps scheduledAt and dueAt preserved when unchanged`() = runTest {
        val taskWithDates = createTestTask(
            scheduledAt = 3000L,
            dueAt = 6000L,
            updatedAt = 1000L
        )
        repository.saveTask(taskWithDates)

        val updatedTask = taskWithDates.copy(
            status = TaskStatus.IN_PROGRESS,
            updatedAt = 1500L
        )
        val updateResult = repository.saveTask(updatedTask)
        assertTrue(updateResult is Result.Success)

        val latestOutbox = db.outboxDao().getPendingEntries().first { it.operationId == "op-task-2" }
        val latestJson = JSONObject(latestOutbox.payload)
        assertEquals(3000L, latestJson.getLong("scheduledAt"))
        assertEquals(6000L, latestJson.getLong("dueAt"))

        val syncRepo = FirestoreSyncRepository(mockk(relaxed = true))
        val parsed = syncRepo.parsePayload(latestOutbox.payload)
        assertEquals(3000L, (parsed["scheduledAt"] as? Number)?.toLong())
        assertEquals(6000L, (parsed["dueAt"] as? Number)?.toLong())
    }

    @Test
    fun `clearing scheduledAt and dueAt serializes them as null in outbox payload`() = runTest {
        val taskWithDates = createTestTask(
            scheduledAt = 3000L,
            dueAt = 6000L,
            updatedAt = 1000L
        )
        repository.saveTask(taskWithDates)

        val taskWithoutDates = taskWithDates.copy(
            scheduledAt = null,
            dueAt = null,
            updatedAt = 1500L
        )
        val updateResult = repository.saveTask(taskWithoutDates)
        assertTrue(updateResult is Result.Success)

        val latestOutbox = db.outboxDao().getPendingEntries().first { it.operationId == "op-task-2" }
        val latestJson = JSONObject(latestOutbox.payload)
        assertTrue("scheduledAt must be null in JSON", latestJson.isNull("scheduledAt"))
        assertTrue("dueAt must be null in JSON", latestJson.isNull("dueAt"))

        val syncRepo = FirestoreSyncRepository(mockk(relaxed = true))
        val parsed = syncRepo.parsePayload(latestOutbox.payload)
        assertTrue(parsed.containsKey("scheduledAt"))
        assertNull(parsed["scheduledAt"])
        assertTrue(parsed.containsKey("dueAt"))
        assertNull(parsed["dueAt"])
    }

    @Test
    fun `saving task with blank userId fails with IllegalArgumentException and preserves atomicity`() = runTest {
        val invalidTask = createTestTask(id = "task-invalid", userId = "")
        val result = repository.saveTask(invalidTask)
        assertTrue(result is Result.Failure)
        assertTrue((result as Result.Failure).error is IllegalArgumentException)
        assertNull(db.ironMindDao().getTask("task-invalid"))
        assertEquals(0, db.outboxDao().getAllEntries().size)
    }

    @Test
    fun `outbox entry has non-null userId column matching task userId`() = runTest {
        val task = createTestTask(id = "task-uid-check", userId = "user-task-789")
        val result = repository.saveTask(task)
        assertTrue(result is Result.Success)

        val outbox = db.outboxDao().getPendingEntries().firstOrNull { it.entityId == "task-uid-check" }
        assertNotNull(outbox)
        assertEquals("user-task-789", outbox?.userId)
    }

    @Test
    fun `successful task deletion removes task from Room and produces pending outbox DELETE entry with correct tombstone payload and ownership`() = runTest {
        val task = createTestTask(id = "task-delete-1", userId = "user-alice")
        val saveResult = repository.saveTask(task)
        assertTrue(saveResult is Result.Success)

        // Clear outbox from initial creation
        db.outboxDao().getAllEntries().forEach { db.outboxDao().deleteEntry(it.operationId) }
        assertEquals(0, db.outboxDao().getAllEntries().size)

        val deleteResult = repository.deleteTask("user-alice", "task-delete-1")
        assertTrue("deleteTask must return Success", deleteResult is Result.Success)

        // Verify removed from Room
        assertNull(db.ironMindDao().getTask("task-delete-1"))

        // Verify outbox entry
        val outboxEntries = db.outboxDao().getPendingEntries()
        assertEquals(1, outboxEntries.size)
        val deleteOutbox = outboxEntries.first()
        assertEquals("Task", deleteOutbox.entityType)
        assertEquals("task-delete-1", deleteOutbox.entityId)
        assertEquals("user-alice", deleteOutbox.userId)
        assertEquals("DELETE", deleteOutbox.operationType)
        assertEquals("PENDING", deleteOutbox.status)

        val payload = JSONObject(deleteOutbox.payload)
        assertEquals("task-delete-1", payload.getString("id"))
        assertEquals("user-alice", payload.getString("userId"))
        assertTrue(payload.getBoolean("isDeleted"))
        assertTrue(payload.getLong("deletedAt") > 0)
        assertTrue(payload.getLong("updatedAt") > 0)
    }

    @Test
    fun `failure during outbox insertion rolls back task deletion in Room`() = runTest {
        val failingOutboxDao = DelegatingFailingOutboxDao(db.outboxDao())
        val transactionalRepo = TaskRepositoryImpl(
            dao = db.ironMindDao(),
            outboxDao = failingOutboxDao,
            database = db,
            idGenerator = idGenerator
        )

        val task = createTestTask(id = "task-delete-rollback", userId = "user-bob")
        val saveResult = transactionalRepo.saveTask(task)
        assertTrue(saveResult is Result.Success)
        assertNotNull(db.ironMindDao().getTask("task-delete-rollback"))

        // Clear outbox from creation
        db.outboxDao().getAllEntries().forEach { db.outboxDao().deleteEntry(it.operationId) }

        // Enable failure on outbox
        failingOutboxDao.shouldFail = true
        val deleteResult = transactionalRepo.deleteTask("user-bob", "task-delete-rollback")
        assertTrue("deleteTask must return Failure on outbox insertion error", deleteResult is Result.Failure)

        // Verify rollback: Task MUST still exist in Room
        val preservedTask = db.ironMindDao().getTask("task-delete-rollback")
        assertNotNull("Task must NOT be deleted from Room if Outbox write fails", preservedTask)
        assertEquals("task-delete-rollback", preservedTask?.id)

        // Verify outbox remains empty
        assertEquals(0, db.outboxDao().getAllEntries().size)
    }

    @Test
    fun `deleting non-existent task fails without inserting outbox entry`() = runTest {
        val deleteResult = repository.deleteTask("user-1", "non-existent-task")
        assertTrue(deleteResult is Result.Failure)
        assertTrue((deleteResult as Result.Failure).error is IllegalArgumentException)
        assertEquals(0, db.outboxDao().getAllEntries().size)
    }

    @Test
    fun `deleting task owned by another user fails and preserves task and outbox`() = runTest {
        val task = createTestTask(id = "task-owned-by-alice", userId = "user-alice")
        repository.saveTask(task)
        db.outboxDao().getAllEntries().forEach { db.outboxDao().deleteEntry(it.operationId) }

        val deleteResult = repository.deleteTask("user-mallory", "task-owned-by-alice")
        assertTrue(deleteResult is Result.Failure)
        assertTrue((deleteResult as Result.Failure).error is IllegalStateException)

        // Task must still exist in Room
        assertNotNull(db.ironMindDao().getTask("task-owned-by-alice"))
        // No delete outbox entry
        assertEquals(0, db.outboxDao().getAllEntries().size)
    }
}

