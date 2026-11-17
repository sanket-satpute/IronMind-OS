package com.sanket_satpute_20.ironmind.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.sanket_satpute_20.ironmind.data.local.IronMindDatabase
import com.sanket_satpute_20.ironmind.data.local.Jdk25CompatibleRobolectricTestRunner
import com.sanket_satpute_20.ironmind.data.local.dao.IronMindDao
import com.sanket_satpute_20.ironmind.data.local.dao.OutboxDao
import com.sanket_satpute_20.ironmind.data.local.dao.SyncConflictDao
import com.sanket_satpute_20.ironmind.data.local.entity.OutboxEntity
import com.sanket_satpute_20.ironmind.data.local.entity.SyncConflictEntity
import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.Commitment
import com.sanket_satpute_20.ironmind.domain.model.CommitmentStatus
import com.sanket_satpute_20.ironmind.domain.model.EntitySource
import com.sanket_satpute_20.ironmind.domain.model.Memory
import com.sanket_satpute_20.ironmind.domain.model.MemoryConfirmationState
import com.sanket_satpute_20.ironmind.domain.model.MemoryStatus
import com.sanket_satpute_20.ironmind.domain.model.OutboxOperationType
import com.sanket_satpute_20.ironmind.domain.model.Reflection
import com.sanket_satpute_20.ironmind.domain.model.SyncStatus
import com.sanket_satpute_20.ironmind.domain.repository.ConflictResolutionStrategy
import kotlinx.coroutines.runBlocking
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
import org.robolectric.annotation.Config

@RunWith(Jdk25CompatibleRobolectricTestRunner::class)
@Config(sdk = [34])
class SyncPipelineCompletenessTest {

    private lateinit var context: Context
    private lateinit var db: IronMindDatabase
    private lateinit var dao: IronMindDao
    private lateinit var outboxDao: OutboxDao
    private lateinit var syncConflictDao: SyncConflictDao

    private lateinit var commitmentRepo: CommitmentRepositoryImpl
    private lateinit var reflectionRepo: ReflectionRepositoryImpl
    private lateinit var memoryRepo: MemoryRepositoryImpl
    private lateinit var conflictRepo: SyncConflictRepositoryImpl

    private val user1 = "user-pipeline-1"
    private val user2 = "user-pipeline-2"

    private fun createCommitment(
        id: String,
        userId: String = user1,
        title: String = "Morning Meditation",
        description: String = "10 minutes mindfulness",
        status: CommitmentStatus = CommitmentStatus.COMMITTED,
        priority: Int = 1,
        createdAt: Long = 1000L,
        updatedAt: Long = 1000L
    ): Commitment = Commitment(
        id = id,
        userId = userId,
        goalId = null,
        planId = null,
        taskId = null,
        parentCommitmentId = null,
        title = title,
        description = description,
        committedAt = createdAt,
        scheduledStartAt = createdAt + 1000,
        scheduledEndAt = createdAt + 600000,
        status = status,
        priority = priority,
        source = EntitySource.USER,
        createdAt = createdAt,
        updatedAt = updatedAt
    )

    private fun createReflection(
        id: String,
        userId: String = user1,
        targetEntityId: String? = null,
        targetEntityType: String? = null,
        content: String = "Reflection content",
        sentiment: String? = "POSITIVE",
        createdAt: Long = 1000L
    ): Reflection = Reflection(
        id = id,
        userId = userId,
        targetEntityId = targetEntityId,
        targetEntityType = targetEntityType,
        content = content,
        sentiment = sentiment,
        createdAt = createdAt,
        schemaVersion = 1
    )

    private fun createMemory(
        id: String,
        userId: String = user1,
        type: String = "INSIGHT",
        content: String = "Test memory content",
        confirmationState: MemoryConfirmationState = MemoryConfirmationState.USER_CONFIRMED,
        status: MemoryStatus = MemoryStatus.ACTIVE,
        createdAt: Long = 1000L,
        updatedAt: Long = 1000L
    ): Memory = Memory(
        id = id,
        userId = userId,
        type = type,
        content = content,
        source = EntitySource.USER,
        confidence = 0.9f,
        evidenceCount = 1,
        firstObservedAt = createdAt,
        lastObservedAt = updatedAt,
        confirmationState = confirmationState,
        status = status,
        expiresAt = null,
        createdAt = createdAt,
        updatedAt = updatedAt
    )

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, IronMindDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        dao = db.ironMindDao()
        outboxDao = db.outboxDao()
        syncConflictDao = db.syncConflictDao()

        commitmentRepo = CommitmentRepositoryImpl(
            dao = dao,
            outboxDao = outboxDao,
            database = db
        )

        reflectionRepo = ReflectionRepositoryImpl(
            dao = dao,
            outboxDao = outboxDao,
            database = db
        )

        memoryRepo = MemoryRepositoryImpl(
            dao = dao,
            database = db,
            outboxDao = outboxDao
        )

        conflictRepo = SyncConflictRepositoryImpl(
            syncConflictDao = syncConflictDao,
            outboxDao = outboxDao,
            database = db,
            ironMindDao = dao
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun test01_commitmentSave_atomicallyWritesCommitmentAndOutboxEntry() = runBlocking {
        val now = System.currentTimeMillis()
        val commitment = createCommitment(
            id = "c-1",
            userId = user1,
            title = "Morning Meditation",
            description = "10 minutes of mindfulness",
            status = CommitmentStatus.COMMITTED,
            priority = 2,
            createdAt = now,
            updatedAt = now
        )

        val saveRes = commitmentRepo.saveCommitment(commitment)
        assertTrue("Commitment save must succeed", saveRes is Result.Success)

        // Verify entity persisted in Room
        val savedEntity = dao.getCommitment("c-1")
        assertNotNull("Commitment entity must be persisted in database", savedEntity)
        assertEquals("Morning Meditation", savedEntity?.title)
        assertEquals(user1, savedEntity?.userId)

        // Verify outbox entry persisted atomically
        val pending = outboxDao.getPendingEntriesForUser(user1)
        assertEquals("Exactly one outbox entry must be created", 1, pending.size)
        val entry = pending[0]
        assertEquals("Commitment", entry.entityType)
        assertEquals("c-1", entry.entityId)
        assertEquals(user1, entry.userId)
        assertEquals(OutboxOperationType.UPSERT.name, entry.operationType)
        assertEquals(SyncStatus.PENDING.name, entry.status)

        // Validate outbox payload
        val json = JSONObject(entry.payload)
        assertEquals("c-1", json.getString("id"))
        assertEquals(user1, json.getString("userId"))
        assertEquals("Morning Meditation", json.getString("title"))
        assertEquals("COMMITTED", json.getString("status"))
        assertEquals(2, json.getInt("priority"))
    }

    @Test
    fun test02_commitmentSave_rollsBackCommitmentWhenOutboxFails() = runBlocking {
        val failingOutboxDao = object : OutboxDao by outboxDao {
            override fun insertEntry(entry: OutboxEntity) {
                throw RuntimeException("Simulated Outbox SQLite disk failure")
            }
        }

        val failingRepo = CommitmentRepositoryImpl(
            dao = dao,
            outboxDao = failingOutboxDao,
            database = db
        )

        val commitment = createCommitment(
            id = "c-fail-1",
            userId = user1,
            title = "Failing Commitment"
        )

        val res = failingRepo.saveCommitment(commitment)
        assertTrue("Save must return Failure when outbox fails", res is Result.Failure)

        // Verify Room transaction rolled back the commitment entity
        val entity = dao.getCommitment("c-fail-1")
        assertNull("Commitment must be rolled back on outbox insertion failure", entity)
    }

    @Test
    fun test03_commitmentSave_failsWhenUserIdIsBlank() = runBlocking {
        val blankCommitment = createCommitment(
            id = "c-blank",
            userId = "",
            title = "Unowned Commitment"
        )

        val res = commitmentRepo.saveCommitment(blankCommitment)
        assertTrue("Save must fail when userId is blank", res is Result.Failure)
        assertNull("Blank-user commitment must not be persisted", dao.getCommitment("c-blank"))
    }

    @Test
    fun test04_reflectionSave_atomicallyWritesReflectionAndOutboxEntry() = runBlocking {
        val now = System.currentTimeMillis()
        val reflection = createReflection(
            id = "r-1",
            userId = user1,
            targetEntityId = "c-1",
            targetEntityType = "Commitment",
            content = "Felt calm and present today.",
            sentiment = "POSITIVE",
            createdAt = now
        )

        val res = reflectionRepo.saveReflection(reflection)
        assertTrue("Reflection save must succeed", res is Result.Success)

        // Verify entity persisted in Room
        val entity = dao.getReflection("r-1")
        assertNotNull("Reflection must be persisted", entity)
        assertEquals("r-1", entity?.id)
        assertEquals(user1, entity?.userId)

        // Verify outbox entry
        val pending = outboxDao.getPendingEntriesForUser(user1)
        assertEquals(1, pending.size)
        val entry = pending[0]
        assertEquals("Reflection", entry.entityType)
        assertEquals("r-1", entry.entityId)
        assertEquals(user1, entry.userId)
        assertEquals(OutboxOperationType.UPSERT.name, entry.operationType)

        val json = JSONObject(entry.payload)
        assertEquals("r-1", json.getString("id"))
        assertEquals(user1, json.getString("userId"))
        assertEquals("Felt calm and present today.", json.getString("content"))
        assertEquals("POSITIVE", json.getString("sentiment"))
    }

    @Test
    fun test05_reflectionSave_rollsBackReflectionWhenOutboxFails() = runBlocking {
        val failingOutboxDao = object : OutboxDao by outboxDao {
            override fun insertEntry(entry: OutboxEntity) {
                throw RuntimeException("Simulated Outbox Failure")
            }
        }

        val failingRepo = ReflectionRepositoryImpl(
            dao = dao,
            outboxDao = failingOutboxDao,
            database = db
        )

        val reflection = createReflection(
            id = "r-fail",
            userId = user1,
            content = "Should roll back"
        )

        val res = failingRepo.saveReflection(reflection)
        assertTrue(res is Result.Failure)
        assertNull("Reflection must be rolled back on outbox failure", dao.getReflection("r-fail"))
    }

    @Test
    fun test06_reflectionSave_failsWhenUserIdIsBlank() = runBlocking {
        val blankReflection = createReflection(
            id = "r-blank",
            userId = "",
            content = "Blank user"
        )

        val res = reflectionRepo.saveReflection(blankReflection)
        assertTrue(res is Result.Failure)
        assertNull(dao.getReflection("r-blank"))
    }

    @Test
    fun test07_memorySave_atomicallyWritesMemoryAndOutboxEntry() = runBlocking {
        val now = System.currentTimeMillis()
        val memory = createMemory(
            id = "m-1",
            userId = user1,
            content = "Consistent early sleep improves focus",
            createdAt = now,
            updatedAt = now
        )

        val res = memoryRepo.saveMemory(memory)
        assertTrue("Memory save must succeed", res is Result.Success)

        // Verify entity persisted
        val entity = dao.getMemoryById("m-1")
        assertNotNull("Memory must be persisted", entity)
        assertEquals(user1, entity?.userId)
        assertEquals("Consistent early sleep improves focus", entity?.content)

        // Verify outbox entry
        val pending = outboxDao.getPendingEntriesForUser(user1)
        assertEquals(1, pending.size)
        val entry = pending[0]
        assertEquals("Memory", entry.entityType)
        assertEquals("m-1", entry.entityId)
        assertEquals(user1, entry.userId)
        assertEquals(OutboxOperationType.UPSERT.name, entry.operationType)

        val json = JSONObject(entry.payload)
        assertEquals("m-1", json.getString("id"))
        assertEquals(user1, json.getString("userId"))
        assertEquals("USER_CONFIRMED", json.getString("confirmationState"))
        assertEquals("ACTIVE", json.getString("status"))
        assertEquals(0.9, json.getDouble("confidence"), 0.01)
    }

    @Test
    fun test08_memorySave_rollsBackWhenOutboxFails() = runBlocking {
        val failingOutboxDao = object : OutboxDao by outboxDao {
            override fun insertEntry(entry: OutboxEntity) {
                throw RuntimeException("Simulated Outbox Failure")
            }
        }

        val failingRepo = MemoryRepositoryImpl(
            dao = dao,
            database = db,
            outboxDao = failingOutboxDao
        )

        val memory = createMemory(
            id = "m-fail",
            userId = user1,
            content = "Rollback memory"
        )

        val res = failingRepo.saveMemory(memory)
        assertTrue(res is Result.Failure)
        assertNull("Memory must be rolled back on outbox failure", dao.getMemoryById("m-fail"))
    }

    @Test
    fun test09_memoryDelete_atomicallyDeletesMemoryAndWritesDeleteTombstoneOutboxEntry() = runBlocking {
        val now = System.currentTimeMillis()
        val memory = createMemory(
            id = "m-del-1",
            userId = user1,
            content = "Memory to be deleted",
            createdAt = now,
            updatedAt = now
        )
        memoryRepo.saveMemory(memory)
        outboxDao.deleteEntriesForUser(user1) // Clear initial UPSERT outbox entry

        // Now execute delete
        val delRes = memoryRepo.deleteMemory(user1, "m-del-1")
        assertTrue("Memory delete must succeed", delRes is Result.Success)

        // Verify Room entity deleted
        assertNull("Memory entity must be deleted from Room", dao.getMemoryById("m-del-1"))

        // Verify DELETE tombstone in outbox
        val pending = outboxDao.getPendingEntriesForUser(user1)
        assertEquals(1, pending.size)
        val tombstone = pending[0]
        assertEquals("Memory", tombstone.entityType)
        assertEquals("m-del-1", tombstone.entityId)
        assertEquals(user1, tombstone.userId)
        assertEquals(OutboxOperationType.DELETE.name, tombstone.operationType)

        val json = JSONObject(tombstone.payload)
        assertEquals("m-del-1", json.getString("id"))
        assertEquals(user1, json.getString("userId"))
        assertTrue("Payload must indicate isDeleted=true", json.getBoolean("isDeleted"))
        assertTrue("Payload must contain deletedAt", json.has("deletedAt"))
    }

    @Test
    fun test10_memoryDelete_crossUserForbidden() = runBlocking {
        val memory = createMemory(
            id = "m-user1",
            userId = user1,
            content = "User1 private memory"
        )
        memoryRepo.saveMemory(memory)
        outboxDao.deleteEntriesForUser(user1)

        // User2 attempts to delete User1's memory
        val delRes = memoryRepo.deleteMemory(user2, "m-user1")
        assertTrue("Cross-user memory deletion must fail", delRes is Result.Failure)

        // Verify entity still exists for User1
        assertNotNull("Memory must remain intact", dao.getMemoryById("m-user1"))
        // Verify User2 has 0 outbox entries
        assertEquals(0, outboxDao.getPendingEntriesForUser(user2).size)
    }

    @Test
    fun test11_memoryDelete_rollsBackWhenOutboxFails() = runBlocking {
        val memory = createMemory(
            id = "m-rollback-del",
            userId = user1,
            content = "Keep on failure"
        )
        memoryRepo.saveMemory(memory)

        val failingOutboxDao = object : OutboxDao by outboxDao {
            override fun insertEntry(entry: OutboxEntity) {
                throw RuntimeException("Simulated Outbox Failure during tombstone enqueue")
            }
        }

        val failingRepo = MemoryRepositoryImpl(
            dao = dao,
            database = db,
            outboxDao = failingOutboxDao
        )

        val delRes = failingRepo.deleteMemory(user1, "m-rollback-del")
        assertTrue(delRes is Result.Failure)

        // Memory must NOT be deleted because transaction was rolled back
        assertNotNull("Memory must still exist after rollback", dao.getMemoryById("m-rollback-del"))
    }

    @Test
    fun test12_memoryUpdateState_atomicallyUpdatesMemoryAndWritesUpsertOutboxEntry() = runBlocking {
        val now = 1000L
        val memory = createMemory(
            id = "m-update-1",
            userId = user1,
            content = "Habit insight",
            confirmationState = MemoryConfirmationState.UNCONFIRMED,
            status = MemoryStatus.ACTIVE,
            createdAt = now,
            updatedAt = now
        )
        memoryRepo.saveMemory(memory)
        outboxDao.deleteEntriesForUser(user1)

        val updateTime = 2000L
        val updateRes = memoryRepo.updateMemoryState(
            id = "m-update-1",
            userId = user1,
            confirmationState = MemoryConfirmationState.USER_CONFIRMED,
            status = MemoryStatus.INACTIVE,
            updatedAt = updateTime
        )
        assertTrue("Update must succeed", updateRes is Result.Success)

        // Verify entity updated
        val updatedEntity = dao.getMemoryById("m-update-1")
        assertEquals("USER_CONFIRMED", updatedEntity?.confirmationState)
        assertEquals("INACTIVE", updatedEntity?.status)
        assertEquals(updateTime, updatedEntity?.updatedAt)

        // Verify outbox entry
        val pending = outboxDao.getPendingEntriesForUser(user1)
        assertEquals(1, pending.size)
        val entry = pending[0]
        assertEquals("Memory", entry.entityType)
        assertEquals("m-update-1", entry.entityId)
        assertEquals(OutboxOperationType.UPSERT.name, entry.operationType)

        val json = JSONObject(entry.payload)
        assertEquals("USER_CONFIRMED", json.getString("confirmationState"))
        assertEquals("INACTIVE", json.getString("status"))
        assertEquals(updateTime, json.getLong("updatedAt"))
    }

    @Test
    fun test13_memoryUpdateState_noOpWhenStateUnchanged() = runBlocking {
        val now = 1000L
        val memory = createMemory(
            id = "m-noop",
            userId = user1,
            content = "No-op memory",
            confirmationState = MemoryConfirmationState.USER_CONFIRMED,
            status = MemoryStatus.ACTIVE,
            createdAt = now,
            updatedAt = now
        )
        memoryRepo.saveMemory(memory)
        outboxDao.deleteEntriesForUser(user1)

        // Call update with identical state
        val updateRes = memoryRepo.updateMemoryState(
            id = "m-noop",
            userId = user1,
            confirmationState = MemoryConfirmationState.USER_CONFIRMED,
            status = MemoryStatus.ACTIVE,
            updatedAt = 2000L
        )
        assertTrue(updateRes is Result.Success)

        // No new outbox entry should be enqueued
        val pending = outboxDao.getPendingEntriesForUser(user1)
        assertEquals("Unchanged state must not produce duplicate outbox entry", 0, pending.size)
    }

    @Test
    fun test14_syncConflictResolution_supportedForCommitment() = runBlocking {
        val opId = "op-c-conflict"
        outboxDao.insertEntry(
            OutboxEntity(opId, "Commitment", "c-conf", "UPSERT", """{"id":"c-conf","userId":"$user1"}""", 1000L, 0, null, SyncStatus.CONFLICT.name, user1)
        )

        val conflictId = "conf-c-1"
        val remoteCommitmentPayload = JSONObject().apply {
            put("id", "c-conf")
            put("userId", user1)
            put("title", "Remote Mastered Commitment")
            put("description", "From cloud")
            put("status", "COMPLETED")
            put("version", 5L)
        }.toString()

        syncConflictDao.insertConflict(
            SyncConflictEntity(
                conflictId = conflictId,
                operationId = opId,
                userId = user1,
                entityType = "Commitment",
                entityId = "c-conf",
                localPayload = """{"id":"c-conf","userId":"$user1","title":"Local Commitment"}""",
                remotePayload = remoteCommitmentPayload,
                conflictReason = "Remote version is newer",
                createdAt = 1000L,
                updatedAt = 1000L,
                resolutionStatus = "UNRESOLVED"
            )
        )

        val res = conflictRepo.resolveConflictForUser(conflictId, user1, ConflictResolutionStrategy.USE_REMOTE)
        assertTrue("Conflict resolution must succeed", res is Result.Success)

        // Verify local Room entity converges to remote payload
        val converged = dao.getCommitment("c-conf")
        assertNotNull("Commitment must be converged in Room", converged)
        assertEquals("Remote Mastered Commitment", converged?.title)
        assertEquals("COMPLETED", converged?.status)

        // Verify outbox entry discarded
        assertFalse("Outbox entry must be discarded on USE_REMOTE", outboxDao.entryExists(opId))
    }

    @Test
    fun test15_syncConflictResolution_supportedForReflection() = runBlocking {
        val opId = "op-r-conflict"
        outboxDao.insertEntry(
            OutboxEntity(opId, "Reflection", "r-conf", "UPSERT", """{"id":"r-conf","userId":"$user1"}""", 1000L, 0, null, SyncStatus.CONFLICT.name, user1)
        )

        val conflictId = "conf-r-1"
        val remoteReflectionPayload = JSONObject().apply {
            put("id", "r-conf")
            put("userId", user1)
            put("content", "Remote reflection content")
            put("sentiment", "VERY_POSITIVE")
            put("version", 4L)
        }.toString()

        syncConflictDao.insertConflict(
            SyncConflictEntity(
                conflictId = conflictId,
                operationId = opId,
                userId = user1,
                entityType = "Reflection",
                entityId = "r-conf",
                localPayload = """{"id":"r-conf","userId":"$user1","content":"Local content"}""",
                remotePayload = remoteReflectionPayload,
                conflictReason = "Remote version is newer",
                createdAt = 1000L,
                updatedAt = 1000L,
                resolutionStatus = "UNRESOLVED"
            )
        )

        val res = conflictRepo.resolveConflictForUser(conflictId, user1, ConflictResolutionStrategy.USE_REMOTE)
        assertTrue(res is Result.Success)

        val converged = dao.getReflection("r-conf")
        assertNotNull("Reflection must be converged in Room", converged)
        assertEquals("Remote reflection content", converged?.content)
        assertEquals("VERY_POSITIVE", converged?.sentiment)
    }

    @Test
    fun test16_syncConflictResolution_supportedForMemory() = runBlocking {
        val opId = "op-m-conflict"
        outboxDao.insertEntry(
            OutboxEntity(opId, "Memory", "m-conf", "UPSERT", """{"id":"m-conf","userId":"$user1"}""", 1000L, 0, null, SyncStatus.CONFLICT.name, user1)
        )

        val conflictId = "conf-m-1"
        val remoteMemoryPayload = JSONObject().apply {
            put("id", "m-conf")
            put("userId", user1)
            put("content", "Remote convergent memory")
            put("confirmationState", "CONFIRMED")
            put("status", "ACTIVE")
            put("version", 3L)
        }.toString()

        syncConflictDao.insertConflict(
            SyncConflictEntity(
                conflictId = conflictId,
                operationId = opId,
                userId = user1,
                entityType = "Memory",
                entityId = "m-conf",
                localPayload = """{"id":"m-conf","userId":"$user1","content":"Local"}""",
                remotePayload = remoteMemoryPayload,
                conflictReason = "Remote version is newer",
                createdAt = 1000L,
                updatedAt = 1000L,
                resolutionStatus = "UNRESOLVED"
            )
        )

        val res = conflictRepo.resolveConflictForUser(conflictId, user1, ConflictResolutionStrategy.USE_REMOTE)
        assertTrue(res is Result.Success)

        val converged = dao.getMemoryById("m-conf")
        assertNotNull("Memory must be converged in Room", converged)
        assertEquals("Remote convergent memory", converged?.content)
    }

    @Test
    fun test17_syncConflictResolution_remoteTombstoneDeletesLocalEntities() = runBlocking {
        // Setup existing entities in Room
        val now = 1000L
        dao.insertCommitment(
            com.sanket_satpute_20.ironmind.data.local.entity.CommitmentEntity(
                id = "c-tomb", userId = user1, title = "To delete", description = "",
                committedAt = now, scheduledStartAt = null, scheduledEndAt = null,
                status = "COMMITTED", priority = 1, source = "USER", createdAt = now, updatedAt = now,
                startedAt = null, completedAt = null, postponedAt = null, missedAt = null, recoveredAt = null,
                parentCommitmentId = null, schemaVersion = 1, goalId = null, planId = null, taskId = null
            )
        )

        val opId = "op-tomb-c"
        outboxDao.insertEntry(
            OutboxEntity(opId, "Commitment", "c-tomb", "UPSERT", """{"id":"c-tomb","userId":"$user1"}""", 1000L, 0, null, SyncStatus.CONFLICT.name, user1)
        )

        val conflictId = "conf-tomb-c"
        val remoteTombstonePayload = JSONObject().apply {
            put("id", "c-tomb")
            put("userId", user1)
            put("isDeleted", true)
            put("version", 10L)
        }.toString()

        syncConflictDao.insertConflict(
            SyncConflictEntity(
                conflictId = conflictId,
                operationId = opId,
                userId = user1,
                entityType = "Commitment",
                entityId = "c-tomb",
                localPayload = """{"id":"c-tomb","userId":"$user1"}""",
                remotePayload = remoteTombstonePayload,
                conflictReason = "Remote tombstone deletion",
                createdAt = 1000L,
                updatedAt = 1000L,
                resolutionStatus = "UNRESOLVED"
            )
        )

        val res = conflictRepo.resolveConflictForUser(conflictId, user1, ConflictResolutionStrategy.USE_REMOTE)
        assertTrue(res is Result.Success)

        // Verify entity was deleted locally in Room
        assertNull("Local commitment entity must be deleted upon tombstone resolution", dao.getCommitment("c-tomb"))
    }
}
