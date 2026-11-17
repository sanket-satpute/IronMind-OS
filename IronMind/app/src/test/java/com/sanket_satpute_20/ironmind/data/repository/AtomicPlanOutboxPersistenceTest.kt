package com.sanket_satpute_20.ironmind.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.sanket_satpute_20.ironmind.data.local.IronMindDatabase
import com.sanket_satpute_20.ironmind.data.local.dao.IronMindDao
import com.sanket_satpute_20.ironmind.data.local.dao.OutboxDao
import com.sanket_satpute_20.ironmind.data.local.entity.OutboxEntity
import com.sanket_satpute_20.ironmind.data.local.entity.PlanEntity
import com.sanket_satpute_20.ironmind.domain.common.IdGenerator
import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.EntitySource
import com.sanket_satpute_20.ironmind.domain.model.OutboxOperationType
import com.sanket_satpute_20.ironmind.domain.model.Plan
import com.sanket_satpute_20.ironmind.domain.model.PlanStatus
import com.sanket_satpute_20.ironmind.domain.model.SyncStatus
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
class AtomicPlanOutboxPersistenceTest {

    private lateinit var context: Context
    private lateinit var db: IronMindDatabase
    private lateinit var repository: PlanRepositoryImpl
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
        val directExecutor = java.util.concurrent.Executor { it.run() }
        db = Room.inMemoryDatabaseBuilder(context, IronMindDatabase::class.java)
            .allowMainThreadQueries()
            .setQueryExecutor(directExecutor)
            .setTransactionExecutor(directExecutor)
            .build()

        idGenerator = SequentialIdGenerator("op-plan-")
        repository = PlanRepositoryImpl(
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

    private fun createTestPlan(
        id: String = "plan-1",
        userId: String = "user-1",
        goalId: String? = "goal-1",
        ambitionId: String? = "ambition-1",
        title: String = "Master Step-by-Step",
        description: String = "Break goal into steps",
        status: PlanStatus = PlanStatus.ACTIVE,
        createdAt: Long = 1000L,
        updatedAt: Long = 1000L,
        startedAt: Long? = 1000L,
        completedAt: Long? = null,
        source: EntitySource = EntitySource.USER
    ): Plan = Plan(
        id = id,
        userId = userId,
        goalId = goalId,
        ambitionId = ambitionId,
        title = title,
        description = description,
        status = status,
        createdAt = createdAt,
        updatedAt = updatedAt,
        startedAt = startedAt,
        completedAt = completedAt,
        source = source
    )

    @Test
    fun `successful plan creation persists plan and exactly one matching pending outbox entry`() = runTest {
        val plan = createTestPlan()

        val saveResult = repository.savePlan(plan)
        assertTrue("savePlan should succeed", saveResult is Result.Success)

        // 1. Verify Plan table in Room
        val savedPlan = db.ironMindDao().getPlan("plan-1")
        assertNotNull("Plan must exist in plan table", savedPlan)
        assertEquals("plan-1", savedPlan!!.id)
        assertEquals("user-1", savedPlan.userId)
        assertEquals("goal-1", savedPlan.goalId)
        assertEquals("ambition-1", savedPlan.ambitionId)
        assertEquals("Master Step-by-Step", savedPlan.title)
        assertEquals(PlanStatus.ACTIVE.name, savedPlan.status)

        // 2. Verify Outbox table in Room
        val pendingEntries = db.outboxDao().getPendingEntries()
        assertEquals("Exactly one outbox entry must be created", 1, pendingEntries.size)

        val outbox = pendingEntries[0]
        assertEquals("op-plan-1", outbox.operationId)
        assertEquals("Plan", outbox.entityType)
        assertEquals("plan-1", outbox.entityId)
        assertEquals(OutboxOperationType.UPSERT.name, outbox.operationType)
        assertEquals(SyncStatus.PENDING.name, outbox.status)
        assertEquals(0, outbox.retryCount)
        assertNull(outbox.lastAttemptAt)
        assertEquals(plan.createdAt, outbox.createdAt)
    }

    @Test
    fun `outbox payload contains expected plan identity and fields according to serialization contract`() = runTest {
        val plan = createTestPlan()

        val saveResult = repository.savePlan(plan)
        assertTrue(saveResult is Result.Success)

        val outbox = db.outboxDao().getPendingEntries()[0]
        val json = JSONObject(outbox.payload)

        assertEquals("plan-1", json.getString("id"))
        assertEquals("user-1", json.getString("userId"))
        assertEquals("goal-1", json.getString("goalId"))
        assertEquals("ambition-1", json.getString("ambitionId"))
        assertEquals("Master Step-by-Step", json.getString("title"))
        assertEquals("Break goal into steps", json.getString("description"))
        assertEquals("ACTIVE", json.getString("status"))
        assertEquals(1000L, json.getLong("createdAt"))
        assertEquals(1000L, json.getLong("updatedAt"))
        assertEquals(1000L, json.getLong("startedAt"))
        assertTrue("completedAt should not be set when null", !json.has("completedAt"))
        assertEquals("USER", json.getString("source"))
    }

    @Test
    fun `failure during outbox insertion rolls back the plan insertion`() = runTest {
        val failingOutboxDao = DelegatingFailingOutboxDao(db.outboxDao())
        val transactionalRepo = PlanRepositoryImpl(
            dao = db.ironMindDao(),
            outboxDao = failingOutboxDao,
            database = db,
            idGenerator = idGenerator
        )

        failingOutboxDao.shouldFail = true
        val plan = createTestPlan("plan-rollback", "user-1")

        val result = transactionalRepo.savePlan(plan)
        assertTrue("savePlan must return Failure on outbox exception", result is Result.Failure)

        // Verify rollback: Plan must NOT exist in Room
        val persistedPlan = db.ironMindDao().getPlan("plan-rollback")
        assertNull("Plan insertion must be rolled back on outbox failure", persistedPlan)

        // Verify outbox remains empty
        val outboxEntries = db.outboxDao().getPendingEntries()
        assertTrue("Outbox must be empty", outboxEntries.isEmpty())
    }

    @Test
    fun `plan update produces updated plan and a matching outbox upsert entry`() = runTest {
        val initialPlan = createTestPlan(status = PlanStatus.ACTIVE, updatedAt = 1000L)
        repository.savePlan(initialPlan)

        val updatedPlan = initialPlan.copy(
            status = PlanStatus.COMPLETED,
            completedAt = 2500L,
            updatedAt = 2500L
        )
        val updateResult = repository.savePlan(updatedPlan)
        assertTrue("Update must succeed", updateResult is Result.Success)

        // Verify Room updated
        val persisted = db.ironMindDao().getPlan("plan-1")
        assertNotNull(persisted)
        assertEquals(PlanStatus.COMPLETED.name, persisted!!.status)
        assertEquals(2500L, persisted.completedAt)

        // Verify two outbox entries exists
        val pending = db.outboxDao().getPendingEntries()
        assertEquals(2, pending.size)
        val latestOutbox = pending.first { it.operationId == "op-plan-2" }
        assertEquals("Plan", latestOutbox.entityType)
        assertEquals("plan-1", latestOutbox.entityId)
        val json = JSONObject(latestOutbox.payload)
        assertEquals("COMPLETED", json.getString("status"))
        assertEquals(2500L, json.getLong("completedAt"))
        assertEquals(2500L, json.getLong("updatedAt"))
    }

    @Test
    fun `savePlan with null outboxDao succeeds locally and does not fail`() = runTest {
        val localRepo = PlanRepositoryImpl(
            dao = db.ironMindDao(),
            outboxDao = null,
            database = null
        )

        val plan = createTestPlan("plan-local-only")
        val result = localRepo.savePlan(plan)
        assertTrue(result is Result.Success)

        assertNotNull(db.ironMindDao().getPlan("plan-local-only"))
        assertTrue("Outbox must remain empty when outboxDao is null", db.outboxDao().getPendingEntries().isEmpty())
    }

    @Test
    fun `saving plan with blank userId fails with IllegalArgumentException and preserves atomicity`() = runTest {
        val invalidPlan = createTestPlan(id = "plan-invalid", userId = "")
        val result = repository.savePlan(invalidPlan)
        assertTrue(result is Result.Failure)
        assertTrue((result as Result.Failure).error is IllegalArgumentException)
        assertNull(db.ironMindDao().getPlan("plan-invalid"))
        assertEquals(0, db.outboxDao().getAllEntries().size)
    }

    @Test
    fun `outbox entry has non-null userId column matching plan userId`() = runTest {
        val plan = createTestPlan(id = "plan-uid-check", userId = "user-plan-456")
        val result = repository.savePlan(plan)
        assertTrue(result is Result.Success)

        val outbox = db.outboxDao().getPendingEntries().firstOrNull { it.entityId == "plan-uid-check" }
        assertNotNull(outbox)
        assertEquals("user-plan-456", outbox?.userId)
    }

    @Test
    fun `plan paused status transition updates Room and stages outbox upsert with PAUSED status`() = runTest {
        val initialPlan = createTestPlan(status = PlanStatus.ACTIVE, updatedAt = 1000L)
        repository.savePlan(initialPlan)

        val pausedPlan = initialPlan.copy(
            status = PlanStatus.PAUSED,
            updatedAt = 3000L
        )
        val updateResult = repository.savePlan(pausedPlan)
        assertTrue("Updating to PAUSED must succeed", updateResult is Result.Success)

        // Verify Room
        val persisted = db.ironMindDao().getPlan("plan-1")
        assertNotNull(persisted)
        assertEquals(PlanStatus.PAUSED.name, persisted!!.status)
        assertEquals(3000L, persisted.updatedAt)

        // Verify Outbox
        val pending = db.outboxDao().getPendingEntries()
        assertEquals(2, pending.size)
        val latestOutbox = pending.first { it.operationId == "op-plan-2" }
        assertEquals("Plan", latestOutbox.entityType)
        assertEquals("plan-1", latestOutbox.entityId)
        val json = JSONObject(latestOutbox.payload)
        assertEquals("PAUSED", json.getString("status"))
        assertEquals(3000L, json.getLong("updatedAt"))
    }
}

