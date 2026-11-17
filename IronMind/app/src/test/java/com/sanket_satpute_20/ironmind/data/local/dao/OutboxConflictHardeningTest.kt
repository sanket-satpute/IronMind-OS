package com.sanket_satpute_20.ironmind.data.local.dao

import android.content.Context
import androidx.arch.core.executor.ArchTaskExecutor
import androidx.arch.core.executor.TaskExecutor
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.sanket_satpute_20.ironmind.data.local.IronMindDatabase
import com.sanket_satpute_20.ironmind.data.local.Jdk25CompatibleRobolectricTestRunner
import com.sanket_satpute_20.ironmind.data.local.entity.OutboxEntity
import com.sanket_satpute_20.ironmind.data.local.entity.SyncConflictEntity
import com.sanket_satpute_20.ironmind.data.repository.DataManagementRepositoryImpl
import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.SyncStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(Jdk25CompatibleRobolectricTestRunner::class)
@Config(sdk = [34])
class OutboxConflictHardeningTest {

    private lateinit var context: Context
    private lateinit var db: IronMindDatabase
    private lateinit var outboxDao: OutboxDao
    private lateinit var syncConflictDao: SyncConflictDao

    private val user1 = "user-alpha"
    private val user2 = "user-beta"

    @Before
    fun setUp() {
        ArchTaskExecutor.getInstance().setDelegate(object : TaskExecutor() {
            override fun executeOnDiskIO(runnable: Runnable) = runnable.run()
            override fun postToMainThread(runnable: Runnable) = runnable.run()
            override fun isMainThread(): Boolean = true
        })
        context = ApplicationProvider.getApplicationContext()
        val directExecutor = java.util.concurrent.Executor { it.run() }
        db = Room.inMemoryDatabaseBuilder(context, IronMindDatabase::class.java)
            .allowMainThreadQueries()
            .setQueryExecutor(directExecutor)
            .setTransactionExecutor(directExecutor)
            .build()
        outboxDao = db.outboxDao()
        syncConflictDao = db.syncConflictDao()
    }

    @After
    fun tearDown() {
        try {
            db.close()
        } finally {
            ArchTaskExecutor.getInstance().setDelegate(null)
        }
    }

    @Test
    fun testUserScopedOutboxQueries_strictlyIsolateByUserId() = runBlocking {
        // User 1 entries
        outboxDao.insertEntry(
            OutboxEntity(
                operationId = "op-u1-pending",
                userId = user1,
                entityType = "Goal",
                entityId = "g-1",
                operationType = "UPSERT",
                payload = "{}",
                createdAt = 1000L,
                retryCount = 0,
                lastAttemptAt = null,
                status = SyncStatus.PENDING.name
            )
        )
        outboxDao.insertEntry(
            OutboxEntity(
                operationId = "op-u1-failed",
                userId = user1,
                entityType = "Task",
                entityId = "t-1",
                operationType = "UPSERT",
                payload = "{}",
                createdAt = 1001L,
                retryCount = 1,
                lastAttemptAt = 1005L,
                status = SyncStatus.FAILED.name
            )
        )
        outboxDao.insertEntry(
            OutboxEntity(
                operationId = "op-u1-conflict",
                userId = user1,
                entityType = "Plan",
                entityId = "p-1",
                operationType = "UPSERT",
                payload = "{}",
                createdAt = 1002L,
                retryCount = 0,
                lastAttemptAt = null,
                status = SyncStatus.CONFLICT.name
            )
        )

        // User 2 entries
        outboxDao.insertEntry(
            OutboxEntity(
                operationId = "op-u2-pending",
                userId = user2,
                entityType = "Goal",
                entityId = "g-2",
                operationType = "UPSERT",
                payload = "{}",
                createdAt = 1003L,
                retryCount = 0,
                lastAttemptAt = null,
                status = SyncStatus.PENDING.name
            )
        )
        outboxDao.insertEntry(
            OutboxEntity(
                operationId = "op-u2-conflict",
                userId = user2,
                entityType = "Plan",
                entityId = "p-2",
                operationType = "UPSERT",
                payload = "{}",
                createdAt = 1004L,
                retryCount = 0,
                lastAttemptAt = null,
                status = SyncStatus.CONFLICT.name
            )
        )

        // Orphaned entries (userId = null)
        outboxDao.insertEntry(
            OutboxEntity(
                operationId = "op-orphan-1",
                userId = null,
                entityType = "Goal",
                entityId = "g-3",
                operationType = "UPSERT",
                payload = "{}",
                createdAt = 1005L,
                retryCount = 0,
                lastAttemptAt = null,
                status = SyncStatus.PENDING.name
            )
        )

        // Verify counts
        assertEquals(3, outboxDao.getOutboxCountForUser(user1).first())
        assertEquals(2, outboxDao.countPendingEntriesForUser(user1))
        assertEquals(2, outboxDao.getOutboxCountForUser(user2).first())
        assertEquals(1, outboxDao.countPendingEntriesForUser(user2))
        assertEquals(1, outboxDao.countOrphanedEntries())

        // Verify user 1 queries do not leak user 2 or orphaned entries
        val u1Pending = outboxDao.getPendingEntriesForUser(user1)
        assertEquals(2, u1Pending.size)
        assertEquals(listOf("op-u1-pending", "op-u1-failed"), u1Pending.map { it.operationId })

        val u1Failed = outboxDao.getFailedEntriesForUser(user1)
        assertEquals(1, u1Failed.size)
        assertEquals("op-u1-failed", u1Failed[0].operationId)

        val u1Conflict = outboxDao.getConflictEntriesForUser(user1)
        assertEquals(1, u1Conflict.size)
        assertEquals("op-u1-conflict", u1Conflict[0].operationId)

        // Verify orphaned entries query
        val orphans = outboxDao.getOrphanedEntries()
        assertEquals(1, orphans.size)
        assertEquals("op-orphan-1", orphans[0].operationId)
    }

    @Test
    fun testSyncConflictDao_crudAndUserIsolation() = runBlocking {
        val conflict1 = SyncConflictEntity(
            conflictId = "c-1",
            operationId = "op-1",
            userId = user1,
            entityType = "Goal",
            entityId = "g-1",
            localPayload = """{"title":"Local 1"}""",
            remotePayload = """{"title":"Remote 1"}""",
            conflictReason = "VERSION_MISMATCH",
            createdAt = 1000L,
            updatedAt = 1000L,
            resolutionStatus = "UNRESOLVED"
        )
        val conflict2 = SyncConflictEntity(
            conflictId = "c-2",
            operationId = "op-2",
            userId = user1,
            entityType = "Goal",
            entityId = "g-1",
            localPayload = """{"title":"Local 2"}""",
            remotePayload = """{"title":"Remote 2"}""",
            conflictReason = "CONCURRENT_MODIFICATION",
            createdAt = 1005L,
            updatedAt = 1005L,
            resolutionStatus = "RESOLVED",
            resolvedAt = 1010L,
            resolvedBy = "CLIENT_OVERWRITE"
        )
        val conflictUser2 = SyncConflictEntity(
            conflictId = "c-3",
            operationId = "op-3",
            userId = user2,
            entityType = "Goal",
            entityId = "g-99",
            localPayload = """{"title":"Local U2"}""",
            remotePayload = """{"title":"Remote U2"}""",
            conflictReason = "VERSION_MISMATCH",
            createdAt = 1000L,
            updatedAt = 1000L,
            resolutionStatus = "UNRESOLVED"
        )

        syncConflictDao.insertConflict(conflict1)
        syncConflictDao.insertConflict(conflict2)
        syncConflictDao.insertConflict(conflictUser2)

        // Lookup by ID and operation ID
        assertEquals("c-1", syncConflictDao.getConflictById("c-1")?.conflictId)
        assertEquals("c-1", syncConflictDao.getConflictByOperationId("op-1")?.conflictId)

        // Verify user 1 queries
        val u1All = syncConflictDao.getConflictsForUser(user1)
        assertEquals(2, u1All.size)

        val u1Unresolved = syncConflictDao.getUnresolvedConflictsForUser(user1)
        assertEquals(1, u1Unresolved.size)
        assertEquals("c-1", u1Unresolved[0].conflictId)
        assertEquals(1, syncConflictDao.countUnresolvedConflictsForUser(user1))

        // Update resolution status for conflict1
        syncConflictDao.updateResolutionStatus(
            conflictId = "c-1",
            resolutionStatus = "RESOLVED",
            resolvedAt = 1020L,
            resolvedBy = "SERVER_ACCEPT",
            updatedAt = 1020L
        )
        assertEquals("RESOLVED", syncConflictDao.getConflictById("c-1")?.resolutionStatus)
        assertEquals("SERVER_ACCEPT", syncConflictDao.getConflictById("c-1")?.resolvedBy)
        assertEquals(0, syncConflictDao.countUnresolvedConflictsForUser(user1))

        // User 2 queries
        val u2All = syncConflictDao.getConflictsForUser(user2)
        assertEquals(1, u2All.size)
        assertEquals("c-3", u2All[0].conflictId)
    }

    @Test
    fun testDataErasure_deletesUserOutboxAndConflicts_preservesForeignAndOrphans() = runBlocking {
        // Populate outbox
        outboxDao.insertEntry(
            OutboxEntity(
                operationId = "op-u1-1",
                userId = user1,
                entityType = "Goal",
                entityId = "g-1",
                operationType = "UPSERT",
                payload = "{}",
                createdAt = 1000L,
                retryCount = 0,
                lastAttemptAt = null,
                status = SyncStatus.PENDING.name
            )
        )
        outboxDao.insertEntry(
            OutboxEntity(
                operationId = "op-u1-2",
                userId = user1,
                entityType = "Task",
                entityId = "t-1",
                operationType = "UPSERT",
                payload = "{}",
                createdAt = 1001L,
                retryCount = 0,
                lastAttemptAt = null,
                status = SyncStatus.FAILED.name
            )
        )
        outboxDao.insertEntry(
            OutboxEntity(
                operationId = "op-u2-1",
                userId = user2,
                entityType = "Goal",
                entityId = "g-2",
                operationType = "UPSERT",
                payload = "{}",
                createdAt = 1002L,
                retryCount = 0,
                lastAttemptAt = null,
                status = SyncStatus.PENDING.name
            )
        )
        outboxDao.insertEntry(
            OutboxEntity(
                operationId = "op-orphan-1",
                userId = null,
                entityType = "Goal",
                entityId = "g-3",
                operationType = "UPSERT",
                payload = """{"id":"g-3","title":"Orphan"}""",
                createdAt = 1003L,
                retryCount = 0,
                lastAttemptAt = null,
                status = SyncStatus.PENDING.name
            )
        )

        // Populate conflicts
        syncConflictDao.insertConflict(
            SyncConflictEntity("c-u1", "op-u1-1", user1, "Goal", "g-1", "{}", "{}", "REASON", 1000L, 1000L, "UNRESOLVED")
        )
        syncConflictDao.insertConflict(
            SyncConflictEntity("c-u2", "op-u2-1", user2, "Goal", "g-2", "{}", "{}", "REASON", 1000L, 1000L, "UNRESOLVED")
        )

        val repo = DataManagementRepositoryImpl(
            ironMindDao = db.ironMindDao(),
            patternDao = db.patternDao(),
            observationDao = db.observationDao(),
            interventionDao = db.interventionDao(),
            experimentDao = db.experimentDao(),
            autonomySettingsDao = db.autonomySettingsDao(),
            globalAutonomyStateDao = db.globalAutonomyStateDao(),
            database = db,
            outboxDao = outboxDao,
            syncConflictDao = syncConflictDao
        )

        val result = repo.deleteAllUserData(user1)
        assertTrue(result is Result.Success)

        // Target user (user1) records should be deleted
        assertNull(outboxDao.getEntryByOperationId("op-u1-1"))
        assertNull(outboxDao.getEntryByOperationId("op-u1-2"))
        assertNull(syncConflictDao.getConflictById("c-u1"))

        // Foreign user (user2) records must be preserved
        assertNotNull(outboxDao.getEntryByOperationId("op-u2-1"))
        assertNotNull(syncConflictDao.getConflictById("c-u2"))

        // Orphaned record must be preserved
        assertNotNull(outboxDao.getEntryByOperationId("op-orphan-1"))
    }

    @Test
    fun testUserScopedOutboxOperations_crossUserAccessAndMutationForbidden() = runBlocking {
        // 1. Setup entries for User A and User B
        val entryA = OutboxEntity(
            operationId = "op-user-a-1",
            userId = user1,
            entityType = "Goal",
            entityId = "g-a",
            operationType = "UPSERT",
            payload = """{"id":"g-a","userId":"$user1"}""",
            createdAt = 1000L,
            retryCount = 0,
            lastAttemptAt = null,
            status = SyncStatus.PENDING.name
        )
        val entryB = OutboxEntity(
            operationId = "op-user-b-1",
            userId = user2,
            entityType = "Task",
            entityId = "t-b",
            operationType = "UPSERT",
            payload = """{"id":"t-b","userId":"$user2"}""",
            createdAt = 1001L,
            retryCount = 0,
            lastAttemptAt = null,
            status = SyncStatus.PENDING.name
        )
        outboxDao.insertEntry(entryA)
        outboxDao.insertEntry(entryB)

        // 2. User A can fetch A's entry
        val fetchedA = outboxDao.getEntryByOperationIdForUser("op-user-a-1", user1)
        assertNotNull("User A must be able to fetch A's own entry", fetchedA)
        assertEquals("op-user-a-1", fetchedA?.operationId)
        assertEquals(user1, fetchedA?.userId)

        // 3. User A CANNOT fetch B's entry by operation ID
        val crossFetch = outboxDao.getEntryByOperationIdForUser("op-user-b-1", user1)
        assertNull("User A must not be able to fetch User B's entry", crossFetch)

        // 4. User A attempts to update B's entry status -> affects ZERO rows
        val crossUpdateRows = outboxDao.updateStatusForUser(
            operationId = "op-user-b-1",
            userId = user1,
            status = SyncStatus.SYNCED.name,
            retryCount = 5,
            lastAttemptAt = 9999L
        )
        assertEquals("Cross-user status update must affect 0 rows", 0, crossUpdateRows)

        // Verify User B's entry is completely unchanged
        val bAfterCrossUpdate = outboxDao.getEntryByOperationIdForUser("op-user-b-1", user2)
        assertNotNull(bAfterCrossUpdate)
        assertEquals(SyncStatus.PENDING.name, bAfterCrossUpdate?.status)
        assertEquals(0, bAfterCrossUpdate?.retryCount)
        assertNull(bAfterCrossUpdate?.lastAttemptAt)

        // 5. User A attempts to delete B's entry -> affects ZERO rows
        val crossDeleteRows = outboxDao.deleteEntryForUser("op-user-b-1", user1)
        assertEquals("Cross-user delete must affect 0 rows", 0, crossDeleteRows)

        // Verify User B's entry still exists
        val bAfterCrossDelete = outboxDao.getEntryByOperationIdForUser("op-user-b-1", user2)
        assertNotNull("User B's entry must still exist after denied cross-user delete", bAfterCrossDelete)

        // 6. User A updates own entry -> succeeds (affects 1 row)
        val ownUpdateRows = outboxDao.updateStatusForUser(
            operationId = "op-user-a-1",
            userId = user1,
            status = SyncStatus.FAILED.name,
            retryCount = 1,
            lastAttemptAt = 1050L
        )
        assertEquals("Own status update must affect 1 row", 1, ownUpdateRows)
        val aUpdated = outboxDao.getEntryByOperationIdForUser("op-user-a-1", user1)
        assertEquals(SyncStatus.FAILED.name, aUpdated?.status)
        assertEquals(1, aUpdated?.retryCount)
        assertEquals(1050L, aUpdated?.lastAttemptAt)

        // 7. User A deletes own entry -> succeeds (affects 1 row)
        val ownDeleteRows = outboxDao.deleteEntryForUser("op-user-a-1", user1)
        assertEquals("Own delete must affect 1 row", 1, ownDeleteRows)
        assertNull(outboxDao.getEntryByOperationIdForUser("op-user-a-1", user1))

        // 8. User B's entry remains intact and fully accessible to B
        val bFinal = outboxDao.getEntryByOperationIdForUser("op-user-b-1", user2)
        assertNotNull("User B's record must be fully intact", bFinal)
        assertEquals("op-user-b-1", bFinal?.operationId)

        // 9. Nonexistent operation ID affects 0 rows
        val nonExistentUpdate = outboxDao.updateStatusForUser("nonexistent", user1, SyncStatus.SYNCED.name, 0, 0L)
        assertEquals(0, nonExistentUpdate)
        val nonExistentDelete = outboxDao.deleteEntryForUser("nonexistent", user1)
        assertEquals(0, nonExistentDelete)
    }

    @Test
    fun testUserScopedSyncConflictOperations_crossUserAccessAndMutationForbidden() = runBlocking {
        // 1. Insert conflict for User A and User B
        val conflictA = SyncConflictEntity(
            conflictId = "conflict-u1-alpha",
            operationId = "op-conflict-u1",
            userId = user1,
            entityType = "Goal",
            entityId = "g-a",
            localPayload = """{"title":"Local A"}""",
            remotePayload = """{"title":"Remote A"}""",
            conflictReason = "VERSION_MISMATCH",
            createdAt = 1000L,
            updatedAt = 1000L,
            resolutionStatus = "UNRESOLVED",
            resolvedAt = null,
            resolvedBy = null
        )
        val conflictB = SyncConflictEntity(
            conflictId = "conflict-u2-beta",
            operationId = "op-conflict-u2",
            userId = user2,
            entityType = "Task",
            entityId = "t-b",
            localPayload = """{"title":"Local B"}""",
            remotePayload = """{"title":"Remote B"}""",
            conflictReason = "CONCURRENT_MODIFICATION",
            createdAt = 1001L,
            updatedAt = 1001L,
            resolutionStatus = "UNRESOLVED",
            resolvedAt = null,
            resolvedBy = null
        )
        syncConflictDao.insertConflict(conflictA)
        syncConflictDao.insertConflict(conflictB)

        // 2. User A can retrieve A's conflict by conflictId and operationId
        val fetchedAById = syncConflictDao.getConflictByIdForUser("conflict-u1-alpha", user1)
        assertNotNull(fetchedAById)
        assertEquals("conflict-u1-alpha", fetchedAById?.conflictId)

        val fetchedAByOp = syncConflictDao.getConflictByOperationIdForUser("op-conflict-u1", user1)
        assertNotNull(fetchedAByOp)
        assertEquals("conflict-u1-alpha", fetchedAByOp?.conflictId)

        // 3. User A CANNOT retrieve B's conflict by conflictId or operationId
        val crossById = syncConflictDao.getConflictByIdForUser("conflict-u2-beta", user1)
        assertNull("User A must not be able to lookup User B's conflict by ID", crossById)

        val crossByOp = syncConflictDao.getConflictByOperationIdForUser("op-conflict-u2", user1)
        assertNull("User A must not be able to lookup User B's conflict by operation ID", crossByOp)

        // 4. User A attempts to update B's resolution status -> affects ZERO rows
        val crossStatusUpdate = syncConflictDao.updateResolutionStatusForUser(
            conflictId = "conflict-u2-beta",
            userId = user1,
            resolutionStatus = "RESOLVED",
            resolvedAt = 2000L,
            resolvedBy = "TAMPERER",
            updatedAt = 2000L
        )
        assertEquals("Cross-user conflict resolution update must affect 0 rows", 0, crossStatusUpdate)

        // 5. User A attempts to update B's resolution with data -> affects ZERO rows
        val crossDataUpdate = syncConflictDao.updateResolutionStatusWithDataForUser(
            conflictId = "conflict-u2-beta",
            userId = user1,
            resolutionStatus = "RESOLVED",
            remotePayload = """{"tampered":true}""",
            resolvedAt = 2000L,
            resolvedBy = "TAMPERER",
            updatedAt = 2000L
        )
        assertEquals("Cross-user conflict resolution with data must affect 0 rows", 0, crossDataUpdate)

        // Verify User B's conflict is completely unchanged
        val bAfterCrossAttempts = syncConflictDao.getConflictByIdForUser("conflict-u2-beta", user2)
        assertNotNull(bAfterCrossAttempts)
        assertEquals("UNRESOLVED", bAfterCrossAttempts?.resolutionStatus)
        assertEquals("""{"title":"Remote B"}""", bAfterCrossAttempts?.remotePayload)
        assertNull(bAfterCrossAttempts?.resolvedAt)
        assertNull(bAfterCrossAttempts?.resolvedBy)

        // 6. User A attempts to delete B's conflict -> affects ZERO rows
        val crossDelete = syncConflictDao.deleteConflictForUser("conflict-u2-beta", user1)
        assertEquals("Cross-user conflict delete must affect 0 rows", 0, crossDelete)

        // Verify User B's conflict still exists
        val bAfterCrossDelete = syncConflictDao.getConflictByIdForUser("conflict-u2-beta", user2)
        assertNotNull("User B's conflict must still exist after cross-user delete attempt", bAfterCrossDelete)

        // 7. User A resolves own conflict -> succeeds (affects 1 row)
        val ownStatusUpdate = syncConflictDao.updateResolutionStatusForUser(
            conflictId = "conflict-u1-alpha",
            userId = user1,
            resolutionStatus = "RESOLVED",
            resolvedAt = 3000L,
            resolvedBy = "USER_ALPHA",
            updatedAt = 3000L
        )
        assertEquals("Own conflict resolution must affect 1 row", 1, ownStatusUpdate)
        val aResolved = syncConflictDao.getConflictByIdForUser("conflict-u1-alpha", user1)
        assertEquals("RESOLVED", aResolved?.resolutionStatus)
        assertEquals("USER_ALPHA", aResolved?.resolvedBy)
        assertEquals(3000L, aResolved?.resolvedAt)

        // 8. User A deletes own conflict -> succeeds (affects 1 row)
        val ownDelete = syncConflictDao.deleteConflictForUser("conflict-u1-alpha", user1)
        assertEquals("Own conflict delete must affect 1 row", 1, ownDelete)
        assertNull(syncConflictDao.getConflictByIdForUser("conflict-u1-alpha", user1))

        // 9. User B's conflict remains intact
        val bFinal = syncConflictDao.getConflictByIdForUser("conflict-u2-beta", user2)
        assertNotNull(bFinal)
        assertEquals("UNRESOLVED", bFinal?.resolutionStatus)

        // 10. Nonexistent conflict ID affects 0 rows
        assertEquals(0, syncConflictDao.deleteConflictForUser("nonexistent", user1))
        assertEquals(0, syncConflictDao.updateResolutionStatusForUser("nonexistent", user1, "RESOLVED", 0L, "X", 0L))
    }

    @Test
    fun testProductionSyncFlow_strictUserIsolation() = runBlocking {
        // Setup outbox repository and sync repository
        val outboxRepo = com.sanket_satpute_20.ironmind.data.repository.OutboxRepositoryImpl(outboxDao)
        val uploadedList = mutableListOf<com.sanket_satpute_20.ironmind.domain.model.OutboxEntry>()
        val fakeSyncRepo = object : com.sanket_satpute_20.ironmind.domain.repository.SyncRepository {
            override suspend fun upload(
                userId: String,
                entry: com.sanket_satpute_20.ironmind.domain.model.OutboxEntry
            ): Result<com.sanket_satpute_20.ironmind.domain.repository.SyncUploadResult, Exception> {
                uploadedList.add(entry)
                return Result.Success(com.sanket_satpute_20.ironmind.domain.repository.SyncUploadResult.Success)
            }
        }
        val orchestrator = com.sanket_satpute_20.ironmind.data.sync.SyncOrchestrator(outboxRepo, fakeSyncRepo)

        // Insert entry for User A
        outboxDao.insertEntry(
            OutboxEntity(
                operationId = "sync-op-u1",
                userId = user1,
                entityType = "Goal",
                entityId = "g-1",
                operationType = "UPSERT",
                payload = """{"id":"g-1","userId":"$user1","title":"Goal A"}""",
                createdAt = 1000L,
                retryCount = 0,
                lastAttemptAt = null,
                status = SyncStatus.PENDING.name
            )
        )
        // Insert entry for User B
        outboxDao.insertEntry(
            OutboxEntity(
                operationId = "sync-op-u2",
                userId = user2,
                entityType = "Goal",
                entityId = "g-2",
                operationType = "UPSERT",
                payload = """{"id":"g-2","userId":"$user2","title":"Goal B"}""",
                createdAt = 1001L,
                retryCount = 0,
                lastAttemptAt = null,
                status = SyncStatus.PENDING.name
            )
        )

        // Execute sync for User A
        val syncResult = orchestrator.sync(user1)
        assertTrue(syncResult is Result.Success)

        // Verify only User A's entry was uploaded
        assertEquals(1, uploadedList.size)
        assertEquals("sync-op-u1", uploadedList[0].operationId)

        // Verify User A's entry was deleted from outbox
        assertNull(outboxDao.getEntryByOperationIdForUser("sync-op-u1", user1))

        // Verify User B's entry was NOT uploaded and is STILL PENDING in outbox
        val bEntry = outboxDao.getEntryByOperationIdForUser("sync-op-u2", user2)
        assertNotNull("User B's entry must remain untouched in outbox", bEntry)
        assertEquals(SyncStatus.PENDING.name, bEntry?.status)
    }

    // =========================================================================
    // Phase 8.3.3.1.3 Adversarial Legacy NULL Ownership Isolation Tests (F1 - F6)
    // =========================================================================

    private fun seedBenchmarkRecordsAThroughF() {
        // Record A: legacy-user-a, userId = NULL, payload owner = user1
        outboxDao.insertEntry(
            OutboxEntity("legacy-user-a", "Goal", "g-a", "UPSERT", """{"id":"g-a","userId":"$user1","title":"A"}""", 1000L, 0, null, SyncStatus.PENDING.name, null)
        )
        // Record B: legacy-user-b, userId = NULL, payload owner = user2
        outboxDao.insertEntry(
            OutboxEntity("legacy-user-b", "Goal", "g-b", "UPSERT", """{"id":"g-b","userId":"$user2","title":"B"}""", 1001L, 0, null, SyncStatus.PENDING.name, null)
        )
        // Record C: legacy-malformed, userId = NULL, malformed JSON
        outboxDao.insertEntry(
            OutboxEntity("legacy-malformed", "Goal", "g-c", "UPSERT", "NOT_A_VALID_JSON{{{", 1002L, 0, null, SyncStatus.PENDING.name, null)
        )
        // Record D: legacy-no-owner, userId = NULL, valid JSON but missing userId
        outboxDao.insertEntry(
            OutboxEntity("legacy-no-owner", "Goal", "g-d", "UPSERT", """{"id":"g-d","title":"No Owner"}""", 1003L, 0, null, SyncStatus.PENDING.name, null)
        )
        // Record E: owned-user-a, userId = user1
        outboxDao.insertEntry(
            OutboxEntity("owned-user-a", "Goal", "g-e", "UPSERT", """{"id":"g-e","userId":"$user1","title":"Owned A"}""", 1004L, 0, null, SyncStatus.PENDING.name, user1)
        )
        // Record F: owned-user-b, userId = user2
        outboxDao.insertEntry(
            OutboxEntity("owned-user-b", "Goal", "g-f", "UPSERT", """{"id":"g-f","userId":"$user2","title":"Owned B"}""", 1005L, 0, null, SyncStatus.PENDING.name, user2)
        )
    }

    @Test
    fun testF1_strictNormalQueries_neverIncludeNullOrCrossUserRecords() = runBlocking {
        seedBenchmarkRecordsAThroughF()

        // User 1 queries
        val u1Pending = outboxDao.getPendingEntriesForUser(user1)
        assertEquals("User 1 pending query must return ONLY explicitly owned User 1 records", 1, u1Pending.size)
        assertEquals("owned-user-a", u1Pending[0].operationId)
        assertEquals(1, outboxDao.countPendingEntriesForUser(user1))

        // Ensure User 1 CANNOT lookup unowned legacy records via strict user-scoped lookup
        assertNull("Lookup of unowned Record A must return null", outboxDao.getEntryByOperationIdForUser("legacy-user-a", user1))
        assertNull("Lookup of unowned Record B must return null", outboxDao.getEntryByOperationIdForUser("legacy-user-b", user1))
        assertNull("Lookup of unowned Record C must return null", outboxDao.getEntryByOperationIdForUser("legacy-malformed", user1))
        assertNull("Lookup of unowned Record D must return null", outboxDao.getEntryByOperationIdForUser("legacy-no-owner", user1))
        assertNull("Lookup of User 2 Record F must return null", outboxDao.getEntryByOperationIdForUser("owned-user-b", user1))

        // User 2 queries
        val u2Pending = outboxDao.getPendingEntriesForUser(user2)
        assertEquals("User 2 pending query must return ONLY explicitly owned User 2 records", 1, u2Pending.size)
        assertEquals("owned-user-b", u2Pending[0].operationId)
        assertEquals(1, outboxDao.countPendingEntriesForUser(user2))
    }

    @Test
    fun testF2_legacyOwnershipRecovery_claimsOnlyMatchingUserAndPreservesOthers() = runBlocking {
        seedBenchmarkRecordsAThroughF()
        val outboxRepo = com.sanket_satpute_20.ironmind.data.repository.OutboxRepositoryImpl(outboxDao)

        // User 1 executes recovery
        val claimResult1 = outboxRepo.claimLegacyEntriesForUser(user1, batchLimit = 50)
        assertTrue(claimResult1 is Result.Success)
        assertEquals("User 1 must claim exactly 1 matching record (Record A)", 1, (claimResult1 as Result.Success).data)

        // Record A is now explicitly owned by User 1
        val recordA = outboxDao.getEntryByOperationIdForUser("legacy-user-a", user1)
        assertNotNull("Record A must now be retrievable by User 1", recordA)
        assertEquals(user1, recordA?.userId)

        // Record B, C, D must remain strictly NULL
        val orphans = outboxDao.getOrphanedEntries()
        assertEquals(3, orphans.size)
        assertTrue(orphans.any { it.operationId == "legacy-user-b" })
        assertTrue(orphans.any { it.operationId == "legacy-malformed" })
        assertTrue(orphans.any { it.operationId == "legacy-no-owner" })

        // User 1 pending now contains 2 records (owned-user-a and claimed legacy-user-a)
        val u1PendingAfterClaim = outboxDao.getPendingEntriesForUser(user1)
        assertEquals(2, u1PendingAfterClaim.size)
        assertTrue(u1PendingAfterClaim.any { it.operationId == "owned-user-a" })
        assertTrue(u1PendingAfterClaim.any { it.operationId == "legacy-user-a" })

        // User 2 executes recovery
        val claimResult2 = outboxRepo.claimLegacyEntriesForUser(user2, batchLimit = 50)
        assertTrue(claimResult2 is Result.Success)
        assertEquals("User 2 must claim exactly 1 matching record (Record B)", 1, (claimResult2 as Result.Success).data)

        // Record B is now explicitly owned by User 2
        val recordB = outboxDao.getEntryByOperationIdForUser("legacy-user-b", user2)
        assertNotNull("Record B must now be retrievable by User 2", recordB)
        assertEquals(user2, recordB?.userId)

        // Malformed C and missing-owner D remain NULL and unclaimed
        val remainingOrphans = outboxDao.getOrphanedEntries()
        assertEquals(2, remainingOrphans.size)
        assertTrue(remainingOrphans.any { it.operationId == "legacy-malformed" })
        assertTrue(remainingOrphans.any { it.operationId == "legacy-no-owner" })
    }

    @Test
    fun testF3_crossUserAttack_userCannotFetchUpdateDeleteOrClaimOtherUserLegacyRecord() = runBlocking {
        seedBenchmarkRecordsAThroughF()
        val outboxRepo = com.sanket_satpute_20.ironmind.data.repository.OutboxRepositoryImpl(outboxDao)

        // User 2 attempts to claim User 1's legacy record (Record A)
        val u2ClaimResult = outboxRepo.claimLegacyEntriesForUser(user2, batchLimit = 50)
        assertTrue(u2ClaimResult is Result.Success)
        assertEquals("User 2 should only claim Record B, NOT Record A", 1, (u2ClaimResult as Result.Success).data)

        // Verify Record A is STILL NULL (User 2 could not claim it)
        val rawRecordA = outboxDao.getOrphanedEntries().firstOrNull { it.operationId == "legacy-user-a" }
        assertNotNull("Record A must still be in orphans", rawRecordA)
        assertNull(rawRecordA?.userId)

        // User 2 cannot fetch Record A
        assertNull(outboxDao.getEntryByOperationIdForUser("legacy-user-a", user2))

        // User 2 cannot update Record A
        val updateRows = outboxDao.updateStatusForUser("legacy-user-a", user2, SyncStatus.SYNCED.name, 5, 9999L)
        assertEquals("Cross-user status update must affect 0 rows", 0, updateRows)

        // User 2 cannot delete Record A
        val deleteRows = outboxDao.deleteEntryForUser("legacy-user-a", user2)
        assertEquals("Cross-user delete must affect 0 rows", 0, deleteRows)

        // User 1 claims Record A legitimately
        outboxRepo.claimLegacyEntriesForUser(user1, batchLimit = 50)
        assertNotNull(outboxDao.getEntryByOperationIdForUser("legacy-user-a", user1))

        // User 2 still cannot touch Record A after claim
        assertEquals(0, outboxDao.updateStatusForUser("legacy-user-a", user2, SyncStatus.FAILED.name, 1, 0L))
        assertEquals(0, outboxDao.deleteEntryForUser("legacy-user-a", user2))
        assertNull(outboxDao.getEntryByOperationIdForUser("legacy-user-a", user2))
    }

    @Test
    fun testF4_concurrentClaim_atomicRaceProtection() = runBlocking {
        outboxDao.insertEntry(
            OutboxEntity("race-candidate", "Goal", "g-race", "UPSERT", """{"id":"g-race","userId":"$user1"}""", 1000L, 0, null, SyncStatus.PENDING.name, null)
        )

        // Simulate concurrent raw SQL claims on the candidate:
        // 1. User 1 claims first
        val u1Rows = outboxDao.claimLegacyEntry("race-candidate", user1)
        assertEquals("First claim on NULL candidate must succeed", 1, u1Rows)

        // 2. User 2 concurrently attempts to claim the same record
        val u2Rows = outboxDao.claimLegacyEntry("race-candidate", user2)
        assertEquals("Second claim on now non-null record MUST affect 0 rows", 0, u2Rows)

        // 3. User 1 attempts second claim (idempotency check)
        val u1SecondRows = outboxDao.claimLegacyEntry("race-candidate", user1)
        assertEquals("Subsequent claim by same user affects 0 rows since userId is no longer NULL", 0, u1SecondRows)

        // Verify record ownership is intact for User 1
        val finalRecord = outboxDao.getEntryByOperationIdForUser("race-candidate", user1)
        assertNotNull(finalRecord)
        assertEquals(user1, finalRecord?.userId)
    }

    @Test
    fun testF5_payloadTampering_failsClosedOnAllAmbiguousOrMalformedPayloads() {
        val extract = { payload: String -> com.sanket_satpute_20.ironmind.data.local.entity.extractRootPayloadUserId(payload) }

        // Valid
        assertEquals("user-1", extract("""{"userId":"user-1","title":"Test"}"""))

        // Blank and whitespace
        assertNull(extract("""{"userId":""}"""))
        assertNull(extract("""{"userId":"   "}"""))

        // Missing userId
        assertNull(extract("""{"id":"123","title":"No User"}"""))
        assertNull(extract("""{}"""))

        // Null value
        assertNull(extract("""{"userId":null}"""))

        // Wrong types (number, boolean, array, object)
        assertNull(extract("""{"userId":12345}"""))
        assertNull(extract("""{"userId":true}"""))
        assertNull(extract("""{"userId":["user-1"]}"""))
        assertNull(extract("""{"userId":{"id":"user-1"}}"""))

        // Nested userId (must NOT be treated as root ownership)
        assertNull(extract("""{"data":{"userId":"user-1"}}"""))
        assertNull(extract("""{"user":{"userId":"user-1","name":"Alice"}}"""))

        // Malformed JSON
        assertNull(extract("NOT_VALID_JSON{{{"))
        assertNull(extract("{userId: missing_quotes}"))
        assertNull(extract(""))
        assertNull(extract("   "))
    }

    @Test
    fun testF6_erasure_safelyRemovesMatchingExplicitAndLegacyRecords() = runBlocking {
        seedBenchmarkRecordsAThroughF()

        val repo = DataManagementRepositoryImpl(
            ironMindDao = db.ironMindDao(),
            patternDao = db.patternDao(),
            observationDao = db.observationDao(),
            interventionDao = db.interventionDao(),
            experimentDao = db.experimentDao(),
            autonomySettingsDao = db.autonomySettingsDao(),
            globalAutonomyStateDao = db.globalAutonomyStateDao(),
            database = db,
            outboxDao = outboxDao,
            syncConflictDao = syncConflictDao
        )

        // Execute full erasure for User 1
        val result = repo.deleteAllUserData(user1)
        assertTrue(result is Result.Success)

        // User 1 explicit record E and legacy NULL record A must be deleted
        assertNull(outboxDao.getEntryByOperationId("owned-user-a"))
        assertNull(outboxDao.getEntryByOperationId("legacy-user-a"))

        // User 2 explicit record F and legacy NULL record B must be preserved
        assertNotNull(outboxDao.getEntryByOperationId("owned-user-b"))
        assertNotNull(outboxDao.getEntryByOperationId("legacy-user-b"))

        // Malformed C and missing-owner D must be preserved
        assertNotNull(outboxDao.getEntryByOperationId("legacy-malformed"))
        assertNotNull(outboxDao.getEntryByOperationId("legacy-no-owner"))
    }
}
