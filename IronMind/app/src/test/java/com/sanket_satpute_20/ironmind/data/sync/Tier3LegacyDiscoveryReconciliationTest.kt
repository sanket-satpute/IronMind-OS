package com.sanket_satpute_20.ironmind.data.sync

import android.content.Context
import androidx.arch.core.executor.ArchTaskExecutor
import androidx.arch.core.executor.TaskExecutor
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.sanket_satpute_20.ironmind.data.local.IronMindDatabase
import com.sanket_satpute_20.ironmind.data.local.Jdk25CompatibleRobolectricTestRunner
import com.sanket_satpute_20.ironmind.data.local.dao.AppSessionStateDao
import com.sanket_satpute_20.ironmind.data.local.dao.IronMindDao
import com.sanket_satpute_20.ironmind.data.local.dao.LocalTombstoneDao
import com.sanket_satpute_20.ironmind.data.local.dao.OutboxDao
import com.sanket_satpute_20.ironmind.data.local.dao.SyncCheckpointDao
import com.sanket_satpute_20.ironmind.data.local.dao.SyncConflictDao
import com.sanket_satpute_20.ironmind.data.local.dao.SyncWorkerLeaseDao
import com.sanket_satpute_20.ironmind.data.local.entity.AppSessionStateEntity
import com.sanket_satpute_20.ironmind.data.local.entity.GoalEntity
import com.sanket_satpute_20.ironmind.data.local.entity.SyncWorkerLeaseEntity
import com.sanket_satpute_20.ironmind.data.repository.OutboxRepositoryImpl
import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.OutboxEntry
import com.sanket_satpute_20.ironmind.domain.repository.RemoteEntityPayload
import com.sanket_satpute_20.ironmind.domain.repository.SyncCursor
import com.sanket_satpute_20.ironmind.domain.repository.SyncPullResult
import com.sanket_satpute_20.ironmind.domain.repository.SyncRepository
import com.sanket_satpute_20.ironmind.domain.repository.SyncUploadResult
import com.sanket_satpute_20.ironmind.domain.session.SessionState
import com.sanket_satpute_20.ironmind.domain.session.UserSessionProvider
import java.io.IOException
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import org.robolectric.annotation.Config

/**
 * Phase 8.3.3.6A Tier 3 Legacy Discovery and Reconciliation Test Suite.
 *
 * Verifies all 12 Tier 3 Requirements:
 * 1. Legacy documents without updatedAt being discovered and graduated.
 * 2. Multiple document-ID pages traversal.
 * 3. Stable cursor continuation after process interruption.
 * 4. Generation rollover upon full collection completion.
 * 5. No skipped documents across completed cycles.
 * 6. Duplicate discovery idempotency.
 * 7. Preservation of deleted-document metadata.
 * 8. Concurrent Tier 1/Tier 3 execution lock exclusion.
 * 9. Stale generation rejection.
 * 10. Lease expiry and safe takeover.
 * 11. Checkpoint rollback when a page fails.
 * 12. No premature checkpoint advancement.
 */
@RunWith(Jdk25CompatibleRobolectricTestRunner::class)
@Config(sdk = [34])
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class Tier3LegacyDiscoveryReconciliationTest {

    private lateinit var context: Context
    private lateinit var db: IronMindDatabase
    private lateinit var outboxDao: OutboxDao
    private lateinit var syncConflictDao: SyncConflictDao
    private lateinit var ironMindDao: IronMindDao
    private lateinit var leaseDao: SyncWorkerLeaseDao
    private lateinit var sessionStateDao: AppSessionStateDao
    private lateinit var checkpointDao: SyncCheckpointDao
    private lateinit var tombstoneDao: LocalTombstoneDao
    private lateinit var outboxRepo: OutboxRepositoryImpl

    private val userId = "tier3-test-user"
    private lateinit var sessionProvider: FakeUserSessionProvider
    private lateinit var fakeSyncRepository: FakeTier3SyncRepository
    private lateinit var orchestrator: SyncOrchestrator

    private class FakeUserSessionProvider(
        private val currentUserId: String,
        private var currentGeneration: Long = 1L
    ) : UserSessionProvider {
        private val _sessionState = MutableStateFlow<SessionState>(SessionState.Authenticated(currentUserId, false))
        override val sessionState: StateFlow<SessionState> = _sessionState

        fun setGeneration(gen: Long) {
            currentGeneration = gen
        }

        override fun getSessionGeneration(): Long = currentGeneration
        override fun getActiveUserId(): String = currentUserId
        override suspend fun <T> withUserSession(action: suspend (userId: String) -> T): T = action(currentUserId)
        override suspend fun <T> withExclusiveAccess(action: suspend (userId: String) -> T): T = action(currentUserId)
        override suspend fun <T> withExclusiveAccess(drainTimeoutMs: Long, action: suspend (userId: String) -> T): T = action(currentUserId)
        override fun permitOfflineFallback() {}
        override suspend fun retryReconciliation(): Result<com.sanket_satpute_20.ironmind.domain.session.ReconciliationResult, Exception> =
            Result.Success(com.sanket_satpute_20.ironmind.domain.session.ReconciliationResult(true, 0, "OK"))
    }

    private class FakeTier3SyncRepository : SyncRepository {
        val documents = ConcurrentHashMap<String, MutableMap<String, MutableMap<String, Any?>>>()
        var shouldFailOnPage: Int? = null
        var pageCounter = 0
        val backfilledDocIds = mutableListOf<String>()

        fun seedDocument(entityType: String, docId: String, data: Map<String, Any?>) {
            documents.computeIfAbsent(entityType) { ConcurrentHashMap() }[docId] = data.toMutableMap()
        }

        override suspend fun upload(userId: String, entry: OutboxEntry): Result<SyncUploadResult, Exception> =
            Result.Success(SyncUploadResult.Success)

        override suspend fun pull(
            userId: String,
            entityType: String,
            cursor: SyncCursor,
            limit: Int
        ): Result<SyncPullResult, Exception> {
            val entityDocs = documents[entityType] ?: return Result.Success(SyncPullResult.Success(emptyList(), false))
            val items = entityDocs.values.map { map ->
                val id = map["id"] as String
                val v = (map["version"] as? Number)?.toLong() ?: 1L
                val u = (map["updatedAt"] as? Number)?.toLong() ?: 0L
                val del = (map["isDeleted"] as? Boolean) ?: false
                RemoteEntityPayload(entityType, id, v, u, del, JSONObject(map).toString())
            }
            return Result.Success(SyncPullResult.Success(items, false))
        }

        override suspend fun discoverLegacyDocuments(
            userId: String,
            entityType: String,
            startAfterDocId: String,
            limit: Int
        ): Result<SyncPullResult, Exception> {
            pageCounter++
            if (shouldFailOnPage == pageCounter) {
                return Result.Failure(IOException("Simulated network failure on page $pageCounter"))
            }

            val entityDocs = documents[entityType] ?: return Result.Success(SyncPullResult.Success(emptyList(), false))
            val sortedKeys = entityDocs.keys.sorted()
            val filteredKeys = if (startAfterDocId.isNotBlank()) {
                sortedKeys.filter { it > startAfterDocId }
            } else {
                sortedKeys
            }

            val hasMore = filteredKeys.size > limit
            val pageKeys = if (hasMore) filteredKeys.take(limit) else filteredKeys

            val items = mutableListOf<RemoteEntityPayload>()
            for (docId in pageKeys) {
                val docData = entityDocs[docId] ?: continue
                val hasUpdatedAt = docData.containsKey("updatedAt") && docData["updatedAt"] != null

                val finalMap: Map<String, Any?> = if (hasUpdatedAt) {
                    docData
                } else {
                    // Graduate legacy document per .6.4 / .6.5
                    backfilledDocIds.add(docId)
                    val currentVersion = (docData["version"] as? Number)?.toLong()
                    val targetVersion = currentVersion ?: 1L
                    val isDeleted = (docData["isDeleted"] as? Boolean) ?: false
                    val now = System.currentTimeMillis()

                    docData["updatedAt"] = now
                    docData["version"] = targetVersion
                    if (isDeleted) {
                        docData["isDeleted"] = true
                        if (!docData.containsKey("deletedAt")) {
                            docData["deletedAt"] = now
                        }
                    }
                    docData
                }

                val updatedAt = (finalMap["updatedAt"] as? Number)?.toLong() ?: System.currentTimeMillis()
                val version = (finalMap["version"] as? Number)?.toLong() ?: 1L
                val isDeleted = (finalMap["isDeleted"] as? Boolean) ?: false
                val payloadJson = JSONObject(finalMap).toString()

                items.add(
                    RemoteEntityPayload(
                        entityType = entityType,
                        entityId = docId,
                        version = version,
                        updatedAt = updatedAt,
                        isDeleted = isDeleted,
                        payloadJson = payloadJson
                    )
                )
            }

            return Result.Success(SyncPullResult.Success(items = items, hasMore = hasMore))
        }
    }

    @Before
    fun setUp() = runBlocking {
        ArchTaskExecutor.getInstance().setDelegate(object : TaskExecutor() {
            override fun executeOnDiskIO(runnable: Runnable) = runnable.run()
            override fun postToMainThread(runnable: Runnable) = runnable.run()
            override fun isMainThread(): Boolean = true
        })
        context = ApplicationProvider.getApplicationContext()

        val directExecutor = Executor { it.run() }
        db = Room.inMemoryDatabaseBuilder(context, IronMindDatabase::class.java)
            .allowMainThreadQueries()
            .setQueryExecutor(directExecutor)
            .setTransactionExecutor(directExecutor)
            .build()

        outboxDao = db.outboxDao()
        syncConflictDao = db.syncConflictDao()
        ironMindDao = db.ironMindDao()
        leaseDao = db.syncWorkerLeaseDao()
        sessionStateDao = db.appSessionStateDao()
        checkpointDao = db.syncCheckpointDao()
        tombstoneDao = db.localTombstoneDao()
        outboxRepo = OutboxRepositoryImpl(outboxDao)

        // Seed authoritative session state in Room
        sessionStateDao.setSessionState(
            AppSessionStateEntity(
                id = 1,
                activeUserId = userId,
                currentGeneration = 1L,
                updatedAt = System.currentTimeMillis()
            )
        )

        sessionProvider = FakeUserSessionProvider(userId, currentGeneration = 1L)
        fakeSyncRepository = FakeTier3SyncRepository()

        orchestrator = SyncOrchestrator(
            outboxRepository = outboxRepo,
            syncRepository = fakeSyncRepository,
            syncConflictDao = syncConflictDao,
            ironMindDao = ironMindDao,
            database = db,
            checkpointStore = null,
            userSessionProvider = sessionProvider,
            syncWorkerLeaseDao = leaseDao,
            appSessionStateDao = sessionStateDao,
            syncCheckpointDao = checkpointDao,
            localTombstoneDao = tombstoneDao
        )
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
    fun test01_legacyDocumentsWithoutUpdatedAtAreDiscoveredAndGraduated() = runBlocking {
        // Seed remote document lacking updatedAt
        fakeSyncRepository.seedDocument(
            entityType = "Goal",
            docId = "legacy-goal-1",
            data = mapOf(
                "id" to "legacy-goal-1",
                "userId" to userId,
                "title" to "Become Stronger",
                "description" to "Legacy description",
                "createdAt" to 1000L
                // Notice: no updatedAt, no version
            )
        )

        val result = orchestrator.reconcileEntityTier3Legacy(userId, "Goal")
        assertTrue("Reconciliation must succeed", result is Result.Success)
        val items = (result as Result.Success).data.items
        assertEquals(1, items.size)
        assertEquals("legacy-goal-1", items[0].entityId)
        assertTrue("Document must have received graduated updatedAt", items[0].updatedAt > 0L)
        assertEquals("Default targetVersion must be 1L", 1L, items[0].version)

        // Assert Room has the entity applied
        val localGoal = ironMindDao.getGoal("legacy-goal-1")
        assertNotNull("Local goal must be persisted in Room", localGoal)
        assertEquals("Become Stronger", localGoal?.title)
    }

    @Test
    fun test02_multipleDocumentIdPagesTraversal() = runBlocking {
        // Seed 5 documents: doc_1 through doc_5
        for (i in 1..5) {
            fakeSyncRepository.seedDocument(
                entityType = "Goal",
                docId = "doc_$i",
                data = mapOf(
                    "id" to "doc_$i",
                    "userId" to userId,
                    "title" to "Goal $i",
                    "createdAt" to 1000L + i
                )
            )
        }

        // Reconcile with limit = 2 (requires 3 pages: 2, 2, 1)
        val result = orchestrator.reconcileEntityTier3Legacy(userId, "Goal", limit = 2)
        assertTrue(result is Result.Success)
        val items = (result as Result.Success).data.items
        assertEquals(5, items.size)
        assertEquals(listOf("doc_1", "doc_2", "doc_3", "doc_4", "doc_5"), items.map { it.entityId })

        // Assert all 5 goals are persisted locally
        for (i in 1..5) {
            assertNotNull(ironMindDao.getGoal("doc_$i"))
        }
    }

    @Test
    fun test03_stableCursorContinuationAfterProcessInterruption() = runBlocking {
        for (i in 1..4) {
            fakeSyncRepository.seedDocument(
                entityType = "Goal",
                docId = "doc_$i",
                data = mapOf(
                    "id" to "doc_$i",
                    "userId" to userId,
                    "title" to "Goal $i"
                )
            )
        }

        // Simulate page 1 completed and stored in SQLite checkpoint: doc_2
        checkpointDao.upsertCheckpoint(
            com.sanket_satpute_20.ironmind.data.local.entity.SyncCheckpointEntity(
                userId = userId,
                entityType = "Goal",
                traversalType = SyncOrchestrator.TRAVERSAL_TIER3_LEGACY,
                lastTimestamp = 0L,
                lastDocumentId = "doc_2",
                cycleCount = 0,
                lastCycleCompletedAt = 0L,
                updatedAt = System.currentTimeMillis()
            )
        )

        // Run reconciliation: should resume from doc_2 and only fetch doc_3, doc_4
        val result = orchestrator.reconcileEntityTier3Legacy(userId, "Goal", limit = 10)
        assertTrue(result is Result.Success)
        val items = (result as Result.Success).data.items
        assertEquals(2, items.size)
        assertEquals("doc_3", items[0].entityId)
        assertEquals("doc_4", items[1].entityId)
    }

    @Test
    fun test04_generationRolloverUponFullTraversal() = runBlocking {
        fakeSyncRepository.seedDocument(
            entityType = "Goal",
            docId = "goal-final",
            data = mapOf("id" to "goal-final", "userId" to userId, "title" to "Final Goal")
        )

        val result = orchestrator.reconcileEntityTier3Legacy(userId, "Goal", limit = 10)
        assertTrue(result is Result.Success)

        // After complete traversal, checkpoint must reflect cycle rollover
        val checkpoint = checkpointDao.getCheckpoint(userId, "Goal", SyncOrchestrator.TRAVERSAL_TIER3_LEGACY)
        assertNotNull(checkpoint)
        assertEquals("Cursor must reset to empty on rollover", "", checkpoint?.lastDocumentId)
        assertEquals("cycleCount must increment to 1", 1, checkpoint?.cycleCount)
        assertTrue("lastCycleCompletedAt must be recorded", (checkpoint?.lastCycleCompletedAt ?: 0L) > 0L)
    }

    @Test
    fun test05_noSkippedDocumentsAcrossCompletedCycles() = runBlocking {
        fakeSyncRepository.seedDocument("Goal", "goal-1", mapOf("id" to "goal-1", "userId" to userId, "title" to "G1"))

        // Cycle 1
        orchestrator.reconcileEntityTier3Legacy(userId, "Goal")
        val cp1 = checkpointDao.getCheckpoint(userId, "Goal", SyncOrchestrator.TRAVERSAL_TIER3_LEGACY)
        assertEquals(1, cp1?.cycleCount)
        assertEquals("", cp1?.lastDocumentId)

        // Add doc 2
        fakeSyncRepository.seedDocument("Goal", "goal-2", mapOf("id" to "goal-2", "userId" to userId, "title" to "G2"))

        // Cycle 2 resumes from beginning because cursor was reset to ""
        val result2 = orchestrator.reconcileEntityTier3Legacy(userId, "Goal")
        assertTrue(result2 is Result.Success)
        val items2 = (result2 as Result.Success).data.items
        assertEquals(2, items2.size)

        val cp2 = checkpointDao.getCheckpoint(userId, "Goal", SyncOrchestrator.TRAVERSAL_TIER3_LEGACY)
        assertEquals(2, cp2?.cycleCount)
    }

    @Test
    fun test06_duplicateDiscoveryIsHarmlessAndIdempotent() = runBlocking {
        fakeSyncRepository.seedDocument(
            entityType = "Goal",
            docId = "goal-dup",
            data = mapOf("id" to "goal-dup", "userId" to userId, "title" to "Initial Title", "version" to 1L)
        )

        // First pass
        orchestrator.reconcileEntityTier3Legacy(userId, "Goal")
        val local1 = ironMindDao.getGoal("goal-dup")
        assertNotNull(local1)
        assertEquals("Initial Title", local1?.title)

        // Second pass: same document
        val result2 = orchestrator.reconcileEntityTier3Legacy(userId, "Goal")
        assertTrue(result2 is Result.Success)

        val local2 = ironMindDao.getGoal("goal-dup")
        assertNotNull(local2)
        assertEquals("Initial Title", local2?.title)
    }

    @Test
    fun test07_preservationOfDeletedDocumentMetadata() = runBlocking {
        // Seed deleted legacy document missing updatedAt
        fakeSyncRepository.seedDocument(
            entityType = "Goal",
            docId = "deleted-goal-1",
            data = mapOf(
                "id" to "deleted-goal-1",
                "userId" to userId,
                "title" to "Deleted Goal",
                "isDeleted" to true,
                "deletedAt" to 5000L
            )
        )

        // Seed a local active goal first
        ironMindDao.insertGoal(
            GoalEntity(
                id = "deleted-goal-1",
                userId = userId,
                ambitionId = null,
                title = "Active Local",
                description = "Active description",
                why = "Why",
                importance = 1,
                status = "ACTIVE",
                targetAt = null,
                startedAt = null,
                completedAt = null,
                createdAt = 1000L,
                updatedAt = 1000L,
                schemaVersion = 1
            )
        )

        val result = orchestrator.reconcileEntityTier3Legacy(userId, "Goal")
        assertTrue(result is Result.Success)

        // Assert local goal was deleted
        val localGoal = ironMindDao.getGoal("deleted-goal-1")
        assertNull("Deleted remote entity must remove local entity", localGoal)

        // Assert local tombstone was recorded
        val tombstone = tombstoneDao.getTombstone(userId, "Goal", "deleted-goal-1")
        assertNotNull("Local tombstone must be created", tombstone)
        assertEquals("SYNCED", tombstone?.status)
    }

    @Test
    fun test08_concurrentTier1AndTier3LockExclusion() = runBlocking {
        // Worker A acquires sync_execution_lock for Tier 1
        val now = System.currentTimeMillis()
        val acq = leaseDao.acquireOrTakeoverLease(
            leaseKey = SyncOrchestrator.LEASE_KEY_SYNC_EXECUTION,
            callerOwnerToken = "tier1-worker-token",
            callerGeneration = 1L,
            leaseDurationMs = 60_000L,
            now = now
        )
        assertEquals(1L, acq)

        // Tier 3 tries to reconcile: must fail because lease is held!
        val result = orchestrator.reconcileTier3Legacy(userId)
        assertTrue("Tier 3 must be excluded when execution lock is active", result is Result.Failure)
        val ex = (result as Result.Failure).error
        assertTrue(ex.message?.contains("Failed to acquire sync lease") == true)
    }

    @Test
    fun test09_staleGenerationRejection() = runBlocking {
        // Authoritative generation is 2L
        sessionStateDao.setSessionState(
            AppSessionStateEntity(id = 1, activeUserId = userId, currentGeneration = 2L, updatedAt = System.currentTimeMillis())
        )

        // Caller attempts with callerGeneration = 1L
        val result = orchestrator.reconcileEntityTier3Legacy(
            userId = userId,
            entityType = "Goal",
            callerGeneration = 1L
        )
        assertTrue("Stale caller generation must be rejected", result is Result.Failure)
        val ex = (result as Result.Failure).error
        assertTrue(ex.message?.contains("Stale session generation") == true)
    }

    @Test
    fun test10_leaseExpiryAndSafeTakeover() = runBlocking {
        val now = System.currentTimeMillis()
        // Stale lease held with expired timestamp
        leaseDao.acquireOrTakeoverLease(
            leaseKey = SyncOrchestrator.LEASE_KEY_SYNC_EXECUTION,
            callerOwnerToken = "stale-dead-worker",
            callerGeneration = 1L,
            leaseDurationMs = 1000L,
            now = now - 5000L // 5 seconds ago, expired
        )

        fakeSyncRepository.seedDocument("Goal", "g1", mapOf("id" to "g1", "userId" to userId, "title" to "Goal 1"))

        // Fresh worker reconciles: Condition A matches, takeover succeeds!
        val result = orchestrator.reconcileEntityTier3Legacy(userId, "Goal")
        assertTrue("Expired lease must be safely taken over", result is Result.Success)
    }

    @Test
    fun test11_checkpointRollbackWhenPageFails() = runBlocking {
        for (i in 1..4) {
            fakeSyncRepository.seedDocument("Goal", "doc_$i", mapOf("id" to "doc_$i", "userId" to userId, "title" to "G$i"))
        }

        // Configure fake repository to fail on page 2
        fakeSyncRepository.shouldFailOnPage = 2

        val result = orchestrator.reconcileEntityTier3Legacy(userId, "Goal", limit = 2)
        assertTrue("Reconciliation must fail when remote page fails", result is Result.Failure)

        // Checkpoint must reflect only Page 1 (doc_2), not doc_4
        val checkpoint = checkpointDao.getCheckpoint(userId, "Goal", SyncOrchestrator.TRAVERSAL_TIER3_LEGACY)
        assertNotNull(checkpoint)
        assertEquals("doc_2", checkpoint?.lastDocumentId)
    }

    @Test
    fun test12_noPrematureCheckpointAdvancement() = runBlocking {
        // Initial checkpoint does not exist
        assertNull(checkpointDao.getCheckpoint(userId, "Goal", SyncOrchestrator.TRAVERSAL_TIER3_LEGACY))

        // When repository fails immediately on page 1
        fakeSyncRepository.shouldFailOnPage = 1
        fakeSyncRepository.seedDocument("Goal", "doc_1", mapOf("id" to "doc_1", "userId" to userId, "title" to "G1"))

        val result = orchestrator.reconcileEntityTier3Legacy(userId, "Goal")
        assertTrue(result is Result.Failure)

        // Checkpoint must NOT be created or advanced
        val cp = checkpointDao.getCheckpoint(userId, "Goal", SyncOrchestrator.TRAVERSAL_TIER3_LEGACY)
        assertNull("Checkpoint must never be written before successful page commit", cp)
    }
}

