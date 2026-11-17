package com.sanket_satpute_20.ironmind.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.sanket_satpute_20.ironmind.data.local.IronMindDatabase
import com.sanket_satpute_20.ironmind.data.local.dao.IronMindDao
import com.sanket_satpute_20.ironmind.data.local.dao.OutboxDao
import com.sanket_satpute_20.ironmind.data.local.entity.GoalEntity
import com.sanket_satpute_20.ironmind.data.local.entity.OutboxEntity
import com.sanket_satpute_20.ironmind.domain.common.IdGenerator
import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.Goal
import com.sanket_satpute_20.ironmind.domain.model.GoalStatus
import com.sanket_satpute_20.ironmind.domain.model.OutboxOperationType
import com.sanket_satpute_20.ironmind.domain.model.SyncStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
class AtomicGoalOutboxPersistenceTest {

    private lateinit var context: Context
    private lateinit var db: IronMindDatabase
    private lateinit var repository: GoalRepositoryImpl
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

    private class DelegatingFailingIronMindDao(private val delegate: IronMindDao) : IronMindDao by delegate {
        var shouldFail: Boolean = false
        override fun insertGoal(goal: GoalEntity) {
            if (shouldFail) {
                throw android.database.sqlite.SQLiteException("Simulated goal persistence failure")
            }
            delegate.insertGoal(goal)
        }
    }

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, IronMindDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        idGenerator = SequentialIdGenerator("op-goal-")
        repository = GoalRepositoryImpl(
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

    private fun createTestGoal(
        id: String = "goal-1",
        userId: String = "user-1",
        title: String = "Master Consistency",
        description: String = "Daily focus",
        why: String = "Growth",
        importance: Int = 9,
        status: GoalStatus = GoalStatus.ACTIVE,
        createdAt: Long = 1000L,
        updatedAt: Long = 1000L
    ): Goal = Goal(
        id = id,
        userId = userId,
        ambitionId = "ambition-root",
        title = title,
        description = description,
        why = why,
        importance = importance,
        status = status,
        targetAt = 5000L,
        startedAt = 1000L,
        completedAt = null,
        createdAt = createdAt,
        updatedAt = updatedAt
    )

    // ── Test 1: Successful creation persists Goal and matching OutboxEntry ───────
    @Test
    fun `successful goal creation persists goal and exactly one matching pending outbox entry`() = runTest {
        val goal = createTestGoal()

        val saveResult = repository.saveGoal(goal)
        assertTrue("saveGoal should succeed", saveResult is Result.Success)

        // 1. Verify Goal table in Room
        val savedGoal = db.ironMindDao().getGoal("goal-1")
        assertNotNull("Goal must exist in goal table", savedGoal)
        assertEquals("goal-1", savedGoal!!.id)
        assertEquals("user-1", savedGoal.userId)
        assertEquals("Master Consistency", savedGoal.title)
        assertEquals(GoalStatus.ACTIVE.name, savedGoal.status)

        // 2. Verify Outbox table in Room
        val pendingEntries = db.outboxDao().getPendingEntries()
        assertEquals("Exactly one outbox entry must be created", 1, pendingEntries.size)

        val outbox = pendingEntries[0]
        assertEquals("op-goal-1", outbox.operationId)
        assertEquals("Goal", outbox.entityType)
        assertEquals("goal-1", outbox.entityId)
        assertEquals(OutboxOperationType.UPSERT.name, outbox.operationType)
        assertEquals(SyncStatus.PENDING.name, outbox.status)
        assertEquals(0, outbox.retryCount)
        assertNull(outbox.lastAttemptAt)
        assertEquals(goal.createdAt, outbox.createdAt)
    }

    // ── Test 2: Outbox payload contract verification ─────────────────────────────
    @Test
    fun `outbox payload contains expected goal identity and fields according to serialization contract`() = runTest {
        val goal = createTestGoal()

        val saveResult = repository.saveGoal(goal)
        assertTrue(saveResult is Result.Success)

        val outbox = db.outboxDao().getPendingEntries()[0]
        val json = JSONObject(outbox.payload)

        assertEquals("goal-1", json.getString("id"))
        assertEquals("user-1", json.getString("userId"))
        assertEquals("ambition-root", json.getString("ambitionId"))
        assertEquals("Master Consistency", json.getString("title"))
        assertEquals("Daily focus", json.getString("description"))
        assertEquals("Growth", json.getString("why"))
        assertEquals(9, json.getInt("importance"))
        assertEquals("ACTIVE", json.getString("status"))
        assertEquals(5000L, json.getLong("targetAt"))
        assertEquals(1000L, json.getLong("startedAt"))
        assertTrue("completedAt should not be set when null", !json.has("completedAt"))
        assertEquals(1000L, json.getLong("createdAt"))
        assertEquals(1000L, json.getLong("updatedAt"))
    }

    // ── Test 3: Failure during outbox insertion rolls back goal insertion ─────────
    @Test
    fun `failure during outbox insertion rolls back the goal insertion`() = runTest {
        val failingOutboxDao = DelegatingFailingOutboxDao(db.outboxDao())
        val transactionalRepo = GoalRepositoryImpl(
            dao = db.ironMindDao(),
            outboxDao = failingOutboxDao,
            database = db,
            idGenerator = idGenerator
        )

        failingOutboxDao.shouldFail = true
        val goal = createTestGoal("goal-rollback", "user-1")

        val result = transactionalRepo.saveGoal(goal)
        assertTrue("saveGoal must return Failure on outbox exception", result is Result.Failure)

        // Verify rollback: Goal must NOT exist in Room
        val persistedGoal = db.ironMindDao().getGoal("goal-rollback")
        assertNull("Goal insertion must be rolled back on outbox failure", persistedGoal)

        // Verify outbox remains empty
        val outboxEntries = db.outboxDao().getPendingEntries()
        assertTrue("Outbox must be empty", outboxEntries.isEmpty())
    }

    // ── Test 4: Failure during goal insertion does not leave an outbox entry ───────
    @Test
    fun `failure during goal insertion does not leave an outbox entry`() = runTest {
        val failingGoalDao = DelegatingFailingIronMindDao(db.ironMindDao())
        val transactionalRepo = GoalRepositoryImpl(
            dao = failingGoalDao,
            outboxDao = db.outboxDao(),
            database = db,
            idGenerator = idGenerator
        )

        failingGoalDao.shouldFail = true
        val goal = createTestGoal("goal-fail-insert", "user-1")

        val result = transactionalRepo.saveGoal(goal)
        assertTrue("saveGoal must return Failure on goal insertion error", result is Result.Failure)

        // Outbox must have 0 entries
        val outboxEntries = db.outboxDao().getPendingEntries()
        assertTrue("Outbox must be empty when goal insertion fails", outboxEntries.isEmpty())
    }

    // ── Test 5: Repeated independent goal creations maintain separate outbox entries ──
    @Test
    fun `repeated independent goal creations produce separate outbox entries without cross-linking`() = runTest {
        val goal1 = createTestGoal("goal-alpha", "user-1", title = "Goal Alpha")
        val goal2 = createTestGoal("goal-beta", "user-1", title = "Goal Beta")

        val res1 = repository.saveGoal(goal1)
        val res2 = repository.saveGoal(goal2)

        assertTrue(res1 is Result.Success)
        assertTrue(res2 is Result.Success)

        // Verify both goals exist
        assertNotNull(db.ironMindDao().getGoal("goal-alpha"))
        assertNotNull(db.ironMindDao().getGoal("goal-beta"))

        // Verify outbox entries
        val entries = db.outboxDao().getPendingEntries().sortedBy { it.operationId }
        assertEquals("Must have exactly 2 outbox entries", 2, entries.size)

        val entry1 = entries[0]
        val entry2 = entries[1]

        assertEquals("op-goal-1", entry1.operationId)
        assertEquals("goal-alpha", entry1.entityId)
        val payload1 = JSONObject(entry1.payload)
        assertEquals("goal-alpha", payload1.getString("id"))
        assertEquals("Goal Alpha", payload1.getString("title"))

        assertEquals("op-goal-2", entry2.operationId)
        assertEquals("goal-beta", entry2.entityId)
        val payload2 = JSONObject(entry2.payload)
        assertEquals("goal-beta", payload2.getString("id"))
        assertEquals("Goal Beta", payload2.getString("title"))

        assertTrue("Operation IDs must be distinct", entry1.operationId != entry2.operationId)
    }

    @Test
    fun `saving goal with blank userId fails with IllegalArgumentException and preserves atomicity`() = runTest {
        val invalidGoal = createTestGoal(id = "goal-invalid", userId = "")
        val result = repository.saveGoal(invalidGoal)
        assertTrue(result is Result.Failure)
        assertTrue((result as Result.Failure).error is IllegalArgumentException)
        assertNull(db.ironMindDao().getGoal("goal-invalid"))
        assertEquals(0, db.outboxDao().getAllEntries().size)
    }

    @Test
    fun `outbox entry has non-null userId column matching goal userId`() = runTest {
        val goal = createTestGoal(id = "goal-uid-check", userId = "user-specific-123")
        val result = repository.saveGoal(goal)
        assertTrue(result is Result.Success)

        val outbox = db.outboxDao().getPendingEntries().firstOrNull { it.entityId == "goal-uid-check" }
        assertNotNull(outbox)
        assertEquals("user-specific-123", outbox?.userId)
    }

    @Test
    fun `goal updates persist to Room and outbox`() = runTest {
        val initialGoal = createTestGoal(id = "goal-update-1", title = "Initial", importance = 3)
        val initialResult = repository.saveGoal(initialGoal)
        assertTrue(initialResult is Result.Success)

        // Clear initial outbox entry to isolate update verification
        db.outboxDao().deleteEntry(db.outboxDao().getPendingEntries()[0].operationId)

        // Update goal fields
        val updatedGoal = initialGoal.copy(
            title = "Updated Title",
            description = "Updated Description",
            why = "Updated Why",
            importance = 8,
            updatedAt = 2000L
        )
        val updateResult = repository.saveGoal(updatedGoal)
        assertTrue(updateResult is Result.Success)

        // Verify Room persistence
        val savedEntity = db.ironMindDao().getGoal("goal-update-1")
        assertNotNull(savedEntity)
        assertEquals("Updated Title", savedEntity!!.title)
        assertEquals("Updated Description", savedEntity.description)
        assertEquals("Updated Why", savedEntity.why)
        assertEquals(8, savedEntity.importance)
        assertEquals(2000L, savedEntity.updatedAt)

        // Verify Outbox staging
        val outboxEntries = db.outboxDao().getPendingEntries()
        assertEquals(1, outboxEntries.size)
        val outbox = outboxEntries[0]
        assertEquals("Goal", outbox.entityType)
        assertEquals("goal-update-1", outbox.entityId)
        assertEquals(OutboxOperationType.UPSERT.name, outbox.operationType)

        val payload = JSONObject(outbox.payload)
        assertEquals("Updated Title", payload.getString("title"))
        assertEquals("Updated Description", payload.getString("description"))
        assertEquals("Updated Why", payload.getString("why"))
        assertEquals(8, payload.getInt("importance"))
        assertEquals(2000L, payload.getLong("updatedAt"))
    }

    @Test
    fun `goal status PAUSED persists to Room and outbox`() = runTest {
        val initialGoal = createTestGoal(id = "goal-paused-1", status = GoalStatus.ACTIVE)
        repository.saveGoal(initialGoal)

        db.outboxDao().deleteEntry(db.outboxDao().getPendingEntries()[0].operationId)

        val pausedGoal = initialGoal.copy(
            status = GoalStatus.PAUSED,
            updatedAt = 3000L
        )
        val result = repository.saveGoal(pausedGoal)
        assertTrue(result is Result.Success)

        // Verify Room
        val savedEntity = db.ironMindDao().getGoal("goal-paused-1")
        assertNotNull(savedEntity)
        assertEquals("PAUSED", savedEntity!!.status)
        assertNull(savedEntity.completedAt)

        // Verify Outbox
        val outboxEntries = db.outboxDao().getPendingEntries()
        assertEquals(1, outboxEntries.size)
        val payload = JSONObject(outboxEntries[0].payload)
        assertEquals("PAUSED", payload.getString("status"))
        assertFalse(payload.has("completedAt"))
    }

    @Test
    fun `goal status COMPLETED persists to Room and outbox`() = runTest {
        val initialGoal = createTestGoal(id = "goal-completed-1", status = GoalStatus.ACTIVE)
        repository.saveGoal(initialGoal)

        db.outboxDao().deleteEntry(db.outboxDao().getPendingEntries()[0].operationId)

        val completedGoal = initialGoal.copy(
            status = GoalStatus.COMPLETED,
            completedAt = 4500L,
            updatedAt = 4500L
        )
        val result = repository.saveGoal(completedGoal)
        assertTrue(result is Result.Success)

        // Verify Room
        val savedEntity = db.ironMindDao().getGoal("goal-completed-1")
        assertNotNull(savedEntity)
        assertEquals("COMPLETED", savedEntity!!.status)
        assertEquals(4500L, savedEntity.completedAt)

        // Verify Outbox
        val outboxEntries = db.outboxDao().getPendingEntries()
        assertEquals(1, outboxEntries.size)
        val payload = JSONObject(outboxEntries[0].payload)
        assertEquals("COMPLETED", payload.getString("status"))
        assertEquals(4500L, payload.getLong("completedAt"))
    }
}
