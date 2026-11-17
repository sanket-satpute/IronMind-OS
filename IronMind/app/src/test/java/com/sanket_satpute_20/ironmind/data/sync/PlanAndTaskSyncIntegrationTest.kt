package com.sanket_satpute_20.ironmind.data.sync

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.sanket_satpute_20.ironmind.data.local.IronMindDatabase
import com.sanket_satpute_20.ironmind.data.local.dao.OutboxDao
import com.sanket_satpute_20.ironmind.data.local.entity.OutboxEntity
import com.sanket_satpute_20.ironmind.data.repository.DataManagementRepositoryImpl
import com.sanket_satpute_20.ironmind.data.repository.OutboxRepositoryImpl
import com.sanket_satpute_20.ironmind.data.repository.PlanRepositoryImpl
import com.sanket_satpute_20.ironmind.data.repository.TaskRepositoryImpl
import com.sanket_satpute_20.ironmind.domain.common.IdGenerator
import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.EntitySource
import com.sanket_satpute_20.ironmind.domain.model.OutboxEntry
import com.sanket_satpute_20.ironmind.domain.model.OutboxOperationType
import com.sanket_satpute_20.ironmind.domain.model.Plan
import com.sanket_satpute_20.ironmind.domain.model.PlanStatus
import com.sanket_satpute_20.ironmind.domain.model.SyncStatus
import com.sanket_satpute_20.ironmind.domain.model.Task
import com.sanket_satpute_20.ironmind.domain.model.TaskStatus
import com.sanket_satpute_20.ironmind.testutil.fake.FakeBackgroundExecutor
import com.sanket_satpute_20.ironmind.testutil.fake.FakeSyncRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.test.runTest
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
class PlanAndTaskSyncIntegrationTest {

    private lateinit var context: Context
    private lateinit var db: IronMindDatabase
    private lateinit var fakeBackgroundExecutor: FakeBackgroundExecutor
    private lateinit var planRepository: PlanRepositoryImpl
    private lateinit var taskRepository: TaskRepositoryImpl
    private lateinit var fakeSyncRepository: FakeSyncRepository
    private lateinit var outboxRepository: OutboxRepositoryImpl
    private lateinit var syncOrchestrator: SyncOrchestrator

    private class FailingOutboxDao(private val delegate: OutboxDao) : OutboxDao by delegate {
        var shouldFail: Boolean = false
        override fun insertEntry(entry: OutboxEntity) {
            if (shouldFail) {
                throw android.database.sqlite.SQLiteException("Simulated outbox disk error")
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

        fakeBackgroundExecutor = FakeBackgroundExecutor()
        fakeSyncRepository = FakeSyncRepository()
        outboxRepository = OutboxRepositoryImpl(db.outboxDao())
        syncOrchestrator = SyncOrchestrator(outboxRepository, fakeSyncRepository)

        val idGenerator = object : IdGenerator {
            private var count = 1
            override fun generateId(): String = "op-test-${count++}"
        }

        planRepository = PlanRepositoryImpl(
            dao = db.ironMindDao(),
            outboxDao = db.outboxDao(),
            database = db,
            idGenerator = idGenerator,
            backgroundExecutor = fakeBackgroundExecutor
        )

        taskRepository = TaskRepositoryImpl(
            dao = db.ironMindDao(),
            outboxDao = db.outboxDao(),
            database = db,
            idGenerator = idGenerator,
            backgroundExecutor = fakeBackgroundExecutor
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun createTestPlan(
        id: String = "plan-sync-1",
        userId: String = "user-sync-100",
        title: String = "Test Plan Sync"
    ): Plan = Plan(
        id = id,
        userId = userId,
        goalId = "goal-1",
        ambitionId = null,
        title = title,
        description = "Description",
        status = PlanStatus.ACTIVE,
        createdAt = 1000L,
        updatedAt = 1000L,
        startedAt = 1000L,
        completedAt = null,
        source = EntitySource.USER
    )

    private fun createTestTask(
        id: String = "task-sync-1",
        userId: String = "user-sync-100",
        title: String = "Test Task Sync"
    ): Task = Task(
        id = id,
        userId = userId,
        goalId = "goal-1",
        planId = "plan-sync-1",
        parentTaskId = null,
        title = title,
        description = "Description",
        status = TaskStatus.PENDING,
        priority = 1,
        estimatedDurationMinutes = 30,
        scheduledAt = null,
        dueAt = null,
        createdAt = 1000L,
        updatedAt = 1000L,
        completedAt = null,
        postponedAt = null,
        source = EntitySource.USER
    )

    @Test
    fun `successful plan persistence requests sync scheduling`() = runTest {
        val plan = createTestPlan()
        val result = planRepository.savePlan(plan)
        assertTrue(result is Result.Success)

        assertTrue("scheduleSync must be requested after successful plan commit", fakeBackgroundExecutor.scheduleSyncCalled)
        assertNotNull(db.ironMindDao().getPlan("plan-sync-1"))
        assertEquals(1, db.outboxDao().getPendingEntries().size)
    }

    @Test
    fun `successful task persistence requests sync scheduling`() = runTest {
        val task = createTestTask()
        val result = taskRepository.saveTask(task)
        assertTrue(result is Result.Success)

        assertTrue("scheduleSync must be requested after successful task commit", fakeBackgroundExecutor.scheduleSyncCalled)
        assertNotNull(db.ironMindDao().getTask("task-sync-1"))
        assertEquals(1, db.outboxDao().getPendingEntries().size)
    }

    @Test
    fun `failed plan transaction does not schedule sync`() = runTest {
        val failingOutbox = FailingOutboxDao(db.outboxDao()).apply { shouldFail = true }
        val transactionalRepo = PlanRepositoryImpl(
            dao = db.ironMindDao(),
            outboxDao = failingOutbox,
            database = db,
            backgroundExecutor = fakeBackgroundExecutor
        )

        val result = transactionalRepo.savePlan(createTestPlan("plan-fail"))
        assertTrue(result is Result.Failure)

        assertTrue("scheduleSync must NOT be called if transaction fails", !fakeBackgroundExecutor.scheduleSyncCalled)
        assertNull(db.ironMindDao().getPlan("plan-fail"))
        assertTrue(db.outboxDao().getPendingEntries().isEmpty())
    }

    @Test
    fun `failed task transaction does not schedule sync`() = runTest {
        val failingOutbox = FailingOutboxDao(db.outboxDao()).apply { shouldFail = true }
        val transactionalRepo = TaskRepositoryImpl(
            dao = db.ironMindDao(),
            outboxDao = failingOutbox,
            database = db,
            backgroundExecutor = fakeBackgroundExecutor
        )

        val result = transactionalRepo.saveTask(createTestTask("task-fail"))
        assertTrue(result is Result.Failure)

        assertTrue("scheduleSync must NOT be called if transaction fails", !fakeBackgroundExecutor.scheduleSyncCalled)
        assertNull(db.ironMindDao().getTask("task-fail"))
        assertTrue(db.outboxDao().getPendingEntries().isEmpty())
    }

    @Test
    fun `scheduling failure does not roll back committed plan or task`() = runTest {
        fakeBackgroundExecutor.shouldThrowException = true

        val planResult = planRepository.savePlan(createTestPlan("plan-ok"))
        assertTrue("Local save must succeed despite background scheduling exception", planResult is Result.Success)
        assertNotNull(db.ironMindDao().getPlan("plan-ok"))

        val taskResult = taskRepository.saveTask(createTestTask("task-ok"))
        assertTrue("Local save must succeed despite background scheduling exception", taskResult is Result.Success)
        assertNotNull(db.ironMindDao().getTask("task-ok"))

        assertEquals(2, db.outboxDao().getPendingEntries().size)
    }

    @Test
    fun `sync orchestrator successfully uploads plan and task outbox entries and cleans them from outbox`() = runTest {
        val userId = "user-sync-100"
        planRepository.savePlan(createTestPlan("plan-p1", userId))
        taskRepository.saveTask(createTestTask("task-t1", userId))

        assertEquals(2, db.outboxDao().getPendingEntries().size)

        val syncResult = syncOrchestrator.sync(userId)
        assertTrue("Sync should succeed", syncResult is Result.Success)

        // Verify uploads dispatched to SyncRepository
        assertEquals(2, fakeSyncRepository.uploadedEntries.size)
        val planEntry = fakeSyncRepository.uploadedEntries.find { it.entityType == "Plan" }
        assertNotNull(planEntry)
        assertEquals("plan-p1", planEntry!!.entityId)

        val taskEntry = fakeSyncRepository.uploadedEntries.find { it.entityType == "Task" }
        assertNotNull(taskEntry)
        assertEquals("task-t1", taskEntry!!.entityId)

        // Verify entries deleted from outbox
        assertTrue("Outbox must be empty after successful sync", db.outboxDao().getPendingEntries().isEmpty())
    }

    @Test
    fun `sync orchestrator quarantines plan and task entries with missing or blank userId to CONFLICT`() = runTest {
        val userId = "user-sync-100"
        val malformedPlan = OutboxEntry(
            operationId = "op-malformed-plan",
            entityType = "Plan",
            entityId = "plan-m",
            operationType = OutboxOperationType.UPSERT,
            payload = """{"id":"plan-m","title":"No User"}""", // missing userId
            createdAt = 1000L,
            retryCount = 0,
            lastAttemptAt = null,
            status = SyncStatus.PENDING,
            userId = userId
        )
        val blankUserTask = OutboxEntry(
            operationId = "op-blank-task",
            entityType = "Task",
            entityId = "task-b",
            operationType = OutboxOperationType.UPSERT,
            payload = """{"id":"task-b","userId":"   ","title":"Blank User"}""", // blank userId
            createdAt = 1000L,
            retryCount = 0,
            lastAttemptAt = null,
            status = SyncStatus.PENDING,
            userId = userId
        )

        outboxRepository.insertEntry(malformedPlan)
        outboxRepository.insertEntry(blankUserTask)

        val syncResult = syncOrchestrator.sync(userId)
        assertTrue(syncResult is Result.Success)

        // Neither uploaded
        assertTrue(fakeSyncRepository.uploadedEntries.isEmpty())

        // Both quarantined to CONFLICT
        val allEntries = db.outboxDao().getAllEntries()
        assertEquals(2, allEntries.size)
        assertTrue(allEntries.all { it.status == SyncStatus.CONFLICT.name })
    }

    @Test
    fun `sync orchestrator skips plan and task entries belonging to a foreign user`() = runTest {
        val targetUser = "user-target"
        val foreignUser = "user-foreign"

        // Insert entry for target user and foreign user
        planRepository.savePlan(createTestPlan("plan-target", targetUser))
        taskRepository.saveTask(createTestTask("task-foreign", foreignUser))

        val syncResult = syncOrchestrator.sync(targetUser)
        assertTrue(syncResult is Result.Success)

        // Only target user's plan uploaded
        assertEquals(1, fakeSyncRepository.uploadedEntries.size)
        assertEquals("plan-target", fakeSyncRepository.uploadedEntries[0].entityId)

        // Target user's outbox entry deleted; foreign user's outbox entry still PENDING
        val remaining = db.outboxDao().getAllEntries()
        assertEquals(1, remaining.size)
        assertEquals("task-foreign", remaining[0].entityId)
        assertEquals(SyncStatus.PENDING.name, remaining[0].status)
    }

    @Test
    fun `sync orchestrator increments retryCount and sets status to FAILED on upload failure`() = runTest {
        val userId = "user-sync-100"
        taskRepository.saveTask(createTestTask("task-fail-net", userId))

        val outbox = db.outboxDao().getPendingEntries()[0]
        fakeSyncRepository.failingOperationIds.add(outbox.operationId)

        val syncResult = syncOrchestrator.sync(userId)
        assertTrue(syncResult is Result.Success)

        // Outbox entry should be FAILED with retryCount = 1
        val updated = db.outboxDao().getAllEntries().first { it.operationId == outbox.operationId }
        assertEquals(SyncStatus.FAILED.name, updated.status)
        assertEquals(1, updated.retryCount)
    }

    @Test
    fun `sync orchestrator sets status to CONFLICT on remote conflict response`() = runTest {
        val userId = "user-sync-100"
        planRepository.savePlan(createTestPlan("plan-conflict", userId))

        val outbox = db.outboxDao().getPendingEntries()[0]
        fakeSyncRepository.conflictOperationIds.add(outbox.operationId)

        val syncResult = syncOrchestrator.sync(userId)
        assertTrue(syncResult is Result.Success)

        // Outbox entry should be CONFLICT
        val updated = db.outboxDao().getAllEntries().first { it.operationId == outbox.operationId }
        assertEquals(SyncStatus.CONFLICT.name, updated.status)
    }

    @Test
    fun `data erasure removes target user plan and task outbox entries while preserving foreign entries`() = runTest {
        val user1 = "user-1"
        val user2 = "user-2"

        planRepository.savePlan(createTestPlan("plan-u1", user1))
        taskRepository.saveTask(createTestTask("task-u1", user1))
        planRepository.savePlan(createTestPlan("plan-u2", user2))
        taskRepository.saveTask(createTestTask("task-u2", user2))

        val dataManagementRepository = DataManagementRepositoryImpl(
            database = db,
            ironMindDao = db.ironMindDao(),
            interventionDao = db.interventionDao(),
            patternDao = db.patternDao(),
            observationDao = db.observationDao(),
            experimentDao = db.experimentDao(),
            autonomySettingsDao = db.autonomySettingsDao(),
            globalAutonomyStateDao = db.globalAutonomyStateDao(),
            interventionRecommendationDao = db.interventionRecommendationDao(),
            barrierDao = db.barrierDao(),
            outboxDao = db.outboxDao(),
            workManager = null
        )

        val erasureResult = dataManagementRepository.deleteAllUserData(user1)
        assertTrue(erasureResult is Result.Success)

        // user1 local Plan and Task deleted
        assertNull(db.ironMindDao().getPlan("plan-u1"))
        assertNull(db.ironMindDao().getTask("task-u1"))

        // user2 local Plan and Task preserved
        assertNotNull(db.ironMindDao().getPlan("plan-u2"))
        assertNotNull(db.ironMindDao().getTask("task-u2"))

        // Outbox: user1 outbox entries deleted; user2 outbox entries preserved
        val remainingOutbox = db.outboxDao().getAllEntries()
        assertEquals(2, remainingOutbox.size)
        assertTrue(remainingOutbox.all { entry ->
            val json = org.json.JSONObject(entry.payload)
            json.getString("userId") == user2
        })
    }
}
