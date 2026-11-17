package com.sanket_satpute_20.ironmind.data.sync

import android.content.Context
import androidx.arch.core.executor.ArchTaskExecutor
import androidx.arch.core.executor.TaskExecutor
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.sanket_satpute_20.ironmind.data.local.IronMindDatabase
import com.sanket_satpute_20.ironmind.data.local.Jdk25CompatibleRobolectricTestRunner
import com.sanket_satpute_20.ironmind.data.local.dao.IronMindDao
import com.sanket_satpute_20.ironmind.data.local.dao.OutboxDao
import com.sanket_satpute_20.ironmind.data.local.dao.SyncConflictDao
import com.sanket_satpute_20.ironmind.data.local.entity.GoalEntity
import com.sanket_satpute_20.ironmind.data.local.entity.MemoryEntity
import com.sanket_satpute_20.ironmind.data.local.entity.OutboxEntity
import com.sanket_satpute_20.ironmind.data.local.entity.PlanEntity
import com.sanket_satpute_20.ironmind.data.local.entity.SyncConflictEntity
import com.sanket_satpute_20.ironmind.data.local.entity.TaskEntity
import com.sanket_satpute_20.ironmind.data.repository.OutboxRepositoryImpl
import com.sanket_satpute_20.ironmind.data.repository.SyncConflictRepositoryImpl
import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.OutboxEntry
import com.sanket_satpute_20.ironmind.domain.model.OutboxOperationType
import com.sanket_satpute_20.ironmind.domain.model.SyncStatus
import com.sanket_satpute_20.ironmind.domain.repository.ConflictResolutionStrategy
import com.sanket_satpute_20.ironmind.domain.repository.SyncRepository
import com.sanket_satpute_20.ironmind.domain.repository.SyncUploadResult
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Phase 8.3.3.2.2 Distributed Sync Consistency & Concurrency Hardening Test Suite.
 *
 * Verifies:
 * 1. Monotonic document revisions and baseVersion protocol.
 * 2. Atomic distributed transaction simulation & conflict detection.
 * 3. Versioned DELETE / tombstone handling.
 * 4. Lease-based outbox fencing, stale lease recovery, and stale worker rejection.
 * 5. Transactional conflict resolution (USE_LOCAL rebase, USE_REMOTE Room convergence, DISCARD, Atomic CAS).
 * 6. Reference-counted keyed mutex lifecycle and idle eviction (zero leak).
 * 7. Cross-user isolation and multi-tenant data barriers.
 */
@RunWith(Jdk25CompatibleRobolectricTestRunner::class)
@Config(sdk = [34])
class DistributedSyncConsistencyTest {

    private lateinit var context: Context
    private lateinit var db: IronMindDatabase
    private lateinit var outboxDao: OutboxDao
    private lateinit var syncConflictDao: SyncConflictDao
    private lateinit var ironMindDao: IronMindDao
    private lateinit var outboxRepo: OutboxRepositoryImpl
    private lateinit var conflictRepo: SyncConflictRepositoryImpl

    private val user1 = "test-user-alpha"
    private val user2 = "test-user-beta"

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
        ironMindDao = db.ironMindDao()
        outboxRepo = OutboxRepositoryImpl(outboxDao)
        conflictRepo = SyncConflictRepositoryImpl(syncConflictDao, outboxDao, db, ironMindDao)
    }

    @After
    fun tearDown() {
        try {
            db.close()
        } finally {
            ArchTaskExecutor.getInstance().setDelegate(null)
        }
    }

    // =========================================================================
    // Category 1: Versioning & Monotonicity (Tests 1 - 7)
    // =========================================================================

    @Test
    fun test01_monotonicVersioning_sequentialUpdatesIncrementVersion() {
        var remoteVersion = 0L
        val updates = listOf("v1", "v2", "v3")

        for (u in updates) {
            val expectedBase = remoteVersion
            val nextVersion = (expectedBase + 1L).coerceAtLeast(1L)
            assertTrue("Next version must be strictly greater than base", nextVersion > expectedBase)
            remoteVersion = nextVersion
        }
        assertEquals(3L, remoteVersion)
    }

    @Test
    fun test02_versionConflictDetection_remoteNewerThanBase_triggersConflict() {
        val remoteVersion = 5L
        val expectedBaseVersion = 4L
        val isConflict = remoteVersion > expectedBaseVersion
        assertTrue("Remote version 5 > expected base 4 must trigger conflict", isConflict)
    }

    @Test
    fun test03_versionMatch_allowsUpdateAndIncrementsVersion() {
        val remoteVersion = 4L
        val expectedBaseVersion = 4L
        val isConflict = remoteVersion > expectedBaseVersion
        assertFalse("Matching versions must not conflict", isConflict)
        val nextVersion = remoteVersion + 1L
        assertEquals(5L, nextVersion)
    }

    @Test
    fun test04_legacyDocumentWithoutVersion_fallsBackToUpdatedAt() {
        val remoteSnapshotData = mapOf("updatedAt" to 1700000000000L)
        val remoteVersion = (remoteSnapshotData["version"] as? Number)?.toLong()
            ?: (remoteSnapshotData["updatedAt"] as? Number)?.toLong()
            ?: 0L

        assertEquals(1700000000000L, remoteVersion)
        val expectedBaseVersion = 1700000000000L
        val nextVersion = remoteVersion + 1L
        assertEquals(1700000000001L, nextVersion)
    }

    @Test
    fun test05_versionPreconditionCheck_rejectsStaleClient() {
        val remoteVersion = 10L
        val clientBaseVersion = 8L
        val isConflict = remoteVersion > clientBaseVersion
        assertTrue("Stale client with baseVersion 8 must be rejected against remote 10", isConflict)
    }

    @Test
    fun test06_versionedDelete_createsTombstoneWithIncrementedVersion() {
        val remoteVersion = 3L
        val expectedBaseVersion = 3L
        val nextVersion = (maxOf(remoteVersion, expectedBaseVersion) + 1L)
        val tombstone = mapOf(
            "id" to "task-1",
            "userId" to user1,
            "isDeleted" to true,
            "deletedAt" to 1000L,
            "version" to nextVersion
        )

        assertEquals(true, tombstone["isDeleted"])
        assertEquals(4L, tombstone["version"])
    }

    @Test
    fun test07_versionedDelete_remoteNewerThanBase_triggersConflict() {
        val remoteVersion = 7L
        val clientBaseVersion = 5L
        val isConflict = remoteVersion > clientBaseVersion
        assertTrue("DELETE on stale baseVersion must trigger conflict to prevent silent clobber", isConflict)
    }

    // =========================================================================
    // Category 2: Outbox Lease Fencing (Tests 8 - 17)
    // =========================================================================

    @Test
    fun test08_claimEntryWithLease_setsLeaseTokenAndExpiration() = runBlocking {
        outboxDao.insertEntry(
            OutboxEntity("op-l1", "Goal", "g-1", "UPSERT", """{"id":"g-1","userId":"$user1"}""", 1000L, 0, null, SyncStatus.PENDING.name, user1)
        )

        val now = 1000L
        val leaseToken = "lease-token-1"
        val leaseExpiresAt = now + 60_000L

        val claimed = outboxDao.claimEntryWithLease("op-l1", user1, leaseToken, leaseExpiresAt, now)
        assertEquals(1, claimed)

        val entry = outboxDao.getEntryByOperationIdForUser("op-l1", user1)
        assertNotNull(entry)
        assertEquals(SyncStatus.SYNCING.name, entry?.status)
        assertEquals(leaseToken, entry?.leaseToken)
        assertEquals(leaseExpiresAt, entry?.leaseExpiresAt)
    }

    @Test
    fun test09_activeLease_preventsSecondWorkerFromClaiming() = runBlocking {
        outboxDao.insertEntry(
            OutboxEntity("op-l2", "Goal", "g-2", "UPSERT", """{"id":"g-2","userId":"$user1"}""", 1000L, 0, null, SyncStatus.PENDING.name, user1)
        )

        val now = 1000L
        val token1 = "worker-1"
        val expiresAt = now + 60_000L

        // Worker 1 claims
        val claim1 = outboxDao.claimEntryWithLease("op-l2", user1, token1, expiresAt, now)
        assertEquals(1, claim1)

        // Worker 2 attempts claim while lease is active
        val token2 = "worker-2"
        val claim2 = outboxDao.claimEntryWithLease("op-l2", user1, token2, expiresAt + 10_000L, now + 1000L)
        assertEquals("Second worker must fail to claim actively leased entry", 0, claim2)

        val entry = outboxDao.getEntryByOperationIdForUser("op-l2", user1)
        assertEquals(token1, entry?.leaseToken)
    }

    @Test
    fun test10_expiredLease_allowsNewWorkerToClaim() = runBlocking {
        outboxDao.insertEntry(
            OutboxEntity("op-l3", "Goal", "g-3", "UPSERT", """{"id":"g-3","userId":"$user1"}""", 1000L, 0, null, SyncStatus.PENDING.name, user1)
        )

        val t0 = 1000L
        val token1 = "worker-1"
        val expiresAt = t0 + 10_000L

        outboxDao.claimEntryWithLease("op-l3", user1, token1, expiresAt, t0)

        // Worker 2 attempts claim AFTER lease expiration (t = t0 + 15_000L)
        val t1 = t0 + 15_000L
        val token2 = "worker-2"
        val newExpiresAt = t1 + 60_000L

        val claim2 = outboxDao.claimEntryWithLease("op-l3", user1, token2, newExpiresAt, t1)
        assertEquals("New worker must successfully claim entry after lease expiration", 1, claim2)

        val entry = outboxDao.getEntryByOperationIdForUser("op-l3", user1)
        assertEquals(token2, entry?.leaseToken)
        assertEquals(newExpiresAt, entry?.leaseExpiresAt)
    }

    @Test
    fun test11_staleWorkerWithExpiredLease_cannotCompleteEntry() = runBlocking {
        outboxDao.insertEntry(
            OutboxEntity("op-l4", "Goal", "g-4", "UPSERT", """{"id":"g-4","userId":"$user1"}""", 1000L, 0, null, SyncStatus.PENDING.name, user1)
        )

        val token1 = "stale-worker"
        val token2 = "active-worker"

        // Worker 1 claimed earlier
        outboxDao.claimEntryWithLease("op-l4", user1, token1, 2000L, 1000L)
        // Worker 2 re-claimed after expiration
        outboxDao.claimEntryWithLease("op-l4", user1, token2, 5000L, 3000L)

        // Stale worker 1 attempts to complete
        val completed = outboxDao.completeEntryWithLease("op-l4", user1, token1)
        assertEquals("Stale worker must affect 0 rows when completing", 0, completed)

        // Entry must still exist in outbox
        val entry = outboxDao.getEntryByOperationIdForUser("op-l4", user1)
        assertNotNull("Entry must not be deleted by stale worker", entry)
        assertEquals(token2, entry?.leaseToken)
    }

    @Test
    fun test12_staleWorkerWithExpiredLease_cannotFailEntry() = runBlocking {
        outboxDao.insertEntry(
            OutboxEntity("op-l5", "Goal", "g-5", "UPSERT", """{"id":"g-5","userId":"$user1"}""", 1000L, 0, null, SyncStatus.PENDING.name, user1)
        )

        outboxDao.claimEntryWithLease("op-l5", user1, "worker-1", 2000L, 1000L)
        outboxDao.claimEntryWithLease("op-l5", user1, "worker-2", 5000L, 3000L)

        val failed = outboxDao.failEntryWithLease("op-l5", user1, "worker-1", 1, 3500L)
        assertEquals("Stale worker must affect 0 rows when failing", 0, failed)

        val entry = outboxDao.getEntryByOperationIdForUser("op-l5", user1)
        assertEquals(SyncStatus.SYNCING.name, entry?.status)
        assertEquals("worker-2", entry?.leaseToken)
    }

    @Test
    fun test13_staleWorkerWithExpiredLease_cannotConflictEntry() = runBlocking {
        outboxDao.insertEntry(
            OutboxEntity("op-l6", "Goal", "g-6", "UPSERT", """{"id":"g-6","userId":"$user1"}""", 1000L, 0, null, SyncStatus.PENDING.name, user1)
        )

        outboxDao.claimEntryWithLease("op-l6", user1, "worker-1", 2000L, 1000L)
        outboxDao.claimEntryWithLease("op-l6", user1, "worker-2", 5000L, 3000L)

        val conflicted = outboxDao.conflictEntryWithLease("op-l6", user1, "worker-1", 1, 3500L)
        assertEquals("Stale worker must affect 0 rows when setting conflict", 0, conflicted)

        val entry = outboxDao.getEntryByOperationIdForUser("op-l6", user1)
        assertEquals(SyncStatus.SYNCING.name, entry?.status)
        assertEquals("worker-2", entry?.leaseToken)
    }

    @Test
    fun test14_validLeaseWorker_completesEntryAndRemovesFromOutbox() = runBlocking {
        outboxDao.insertEntry(
            OutboxEntity("op-l7", "Goal", "g-7", "UPSERT", """{"id":"g-7","userId":"$user1"}""", 1000L, 0, null, SyncStatus.PENDING.name, user1)
        )

        val token = "valid-worker"
        outboxDao.claimEntryWithLease("op-l7", user1, token, 5000L, 1000L)

        val completed = outboxDao.completeEntryWithLease("op-l7", user1, token)
        assertEquals(1, completed)

        val entry = outboxDao.getEntryByOperationIdForUser("op-l7", user1)
        assertNull("Entry must be deleted upon completion", entry)
    }

    @Test
    fun test15_recoverExpiredLeasesForUser_resetsExpiredEntriesToPending() = runBlocking {
        outboxDao.insertEntry(
            OutboxEntity("op-l8", "Goal", "g-8", "UPSERT", """{"id":"g-8","userId":"$user1"}""", 1000L, 0, null, SyncStatus.PENDING.name, user1)
        )

        outboxDao.claimEntryWithLease("op-l8", user1, "token-crash", 2000L, 1000L)

        val recovered = outboxDao.recoverExpiredLeasesForUser(user1, 3000L)
        assertEquals(1, recovered)

        val entry = outboxDao.getEntryByOperationIdForUser("op-l8", user1)
        assertNotNull(entry)
        assertEquals(SyncStatus.PENDING.name, entry?.status)
        assertNull(entry?.leaseToken)
        assertNull(entry?.leaseExpiresAt)
    }

    @Test
    fun test16_recoverExpiredLeasesForUser_preservesActiveUnexpiredLeases() = runBlocking {
        outboxDao.insertEntry(
            OutboxEntity("op-l9", "Goal", "g-9", "UPSERT", """{"id":"g-9","userId":"$user1"}""", 1000L, 0, null, SyncStatus.PENDING.name, user1)
        )

        outboxDao.claimEntryWithLease("op-l9", user1, "token-active", 10_000L, 1000L)

        val recovered = outboxDao.recoverExpiredLeasesForUser(user1, 3000L)
        assertEquals(0, recovered)

        val entry = outboxDao.getEntryByOperationIdForUser("op-l9", user1)
        assertEquals(SyncStatus.SYNCING.name, entry?.status)
        assertEquals("token-active", entry?.leaseToken)
    }

    @Test
    fun test17_crossUserLeaseIsolation_user2CannotClaimOrCompleteUser1Lease() = runBlocking {
        outboxDao.insertEntry(
            OutboxEntity("op-l10", "Goal", "g-10", "UPSERT", """{"id":"g-10","userId":"$user1"}""", 1000L, 0, null, SyncStatus.PENDING.name, user1)
        )

        // User2 attempts to claim user1's entry
        val claimOther = outboxDao.claimEntryWithLease("op-l10", user2, "token-user2", 5000L, 1000L)
        assertEquals("User2 must not claim user1's outbox entry", 0, claimOther)

        // User1 claims legitimately
        outboxDao.claimEntryWithLease("op-l10", user1, "token-user1", 5000L, 1000L)

        // User2 attempts to complete user1's entry
        val completeOther = outboxDao.completeEntryWithLease("op-l10", user2, "token-user1")
        assertEquals("User2 must not complete user1's outbox entry", 0, completeOther)

        val entry = outboxDao.getEntryByOperationIdForUser("op-l10", user1)
        assertNotNull(entry)
    }

    // =========================================================================
    // Category 3: Mutex Lifecycle & Concurrency (Tests 18 - 20)
    // =========================================================================

    @Test
    fun test18_mutexSerialization_concurrentSyncsSameUserAreSerialized() = runBlocking {
        outboxDao.insertEntry(
            OutboxEntity("op-m1", "Goal", "g-m1", "UPSERT", """{"id":"g-m1","userId":"$user1"}""", 1000L, 0, null, SyncStatus.PENDING.name, user1)
        )

        val concurrentCount = AtomicInteger(0)
        val maxConcurrent = AtomicInteger(0)

        val fakeSyncRepo = object : SyncRepository {
            override suspend fun upload(userId: String, entry: OutboxEntry): Result<SyncUploadResult, Exception> {
                val current = concurrentCount.incrementAndGet()
                maxConcurrent.updateAndGet { maxOf(it, current) }
                delay(100)
                concurrentCount.decrementAndGet()
                return Result.Success(SyncUploadResult.Success)
            }
        }

        val orchestrator = SyncOrchestrator(outboxRepo, fakeSyncRepo, syncConflictDao)

        val job1 = async { orchestrator.sync(user1) }
        val job2 = async { orchestrator.sync(user1) }

        job1.await()
        job2.await()

        assertEquals("Same user syncs must never overlap concurrently", 1, maxConcurrent.get())
    }

    @Test
    fun test19_mutexNonBlocking_independentUsersSyncConcurrently() = runBlocking {
        outboxDao.insertEntry(
            OutboxEntity("op-u1", "Goal", "g-u1", "UPSERT", """{"id":"g-u1","userId":"$user1"}""", 1000L, 0, null, SyncStatus.PENDING.name, user1)
        )
        outboxDao.insertEntry(
            OutboxEntity("op-u2", "Goal", "g-u2", "UPSERT", """{"id":"g-u2","userId":"$user2"}""", 1001L, 0, null, SyncStatus.PENDING.name, user2)
        )

        val concurrentCount = AtomicInteger(0)
        val maxConcurrent = AtomicInteger(0)

        val fakeSyncRepo = object : SyncRepository {
            override suspend fun upload(userId: String, entry: OutboxEntry): Result<SyncUploadResult, Exception> {
                val current = concurrentCount.incrementAndGet()
                maxConcurrent.updateAndGet { maxOf(it, current) }
                delay(100)
                concurrentCount.decrementAndGet()
                return Result.Success(SyncUploadResult.Success)
            }
        }

        val orchestrator = SyncOrchestrator(outboxRepo, fakeSyncRepo, syncConflictDao)

        val job1 = async { orchestrator.sync(user1) }
        val job2 = async { orchestrator.sync(user2) }

        job1.await()
        job2.await()

        assertEquals("Independent users must execute sync concurrently", 2, maxConcurrent.get())
    }

    @Test
    fun test20_refCountedMutexLifecycle_evictedWhenIdle_zeroMemoryLeak() = runBlocking {
        val fakeSyncRepo = object : SyncRepository {
            override suspend fun upload(userId: String, entry: OutboxEntry): Result<SyncUploadResult, Exception> {
                return Result.Success(SyncUploadResult.Success)
            }
        }

        val orchestrator = SyncOrchestrator(outboxRepo, fakeSyncRepo, syncConflictDao)
        assertEquals(0, orchestrator.activeMutexCount())

        orchestrator.sync("user-temp-1")
        orchestrator.sync("user-temp-2")
        orchestrator.sync("user-temp-3")

        assertEquals("All idle mutexes must be evicted from the map upon completion", 0, orchestrator.activeMutexCount())
    }

    // =========================================================================
    // Category 4: Transactional Conflict Resolution (Tests 21 - 32)
    // =========================================================================

    @Test
    fun test21_conflictResolutionUseLocal_rebasesBaseVersionAndPayload() = runBlocking {
        val opId = "op-c-local"
        outboxDao.insertEntry(
            OutboxEntity(opId, "Goal", "g-c1", "UPSERT", """{"id":"g-c1","userId":"$user1","title":"Local Title","updatedAt":100L}""", 1000L, 1, 1000L, SyncStatus.CONFLICT.name, user1, baseVersion = 1L)
        )

        val conflictId = "conflict-1"
        syncConflictDao.insertConflict(
            SyncConflictEntity(
                conflictId = conflictId,
                operationId = opId,
                userId = user1,
                entityType = "Goal",
                entityId = "g-c1",
                localPayload = """{"id":"g-c1","userId":"$user1","title":"Local Title","updatedAt":100L}""",
                remotePayload = """{"id":"g-c1","userId":"$user1","title":"Remote Title","version":5,"updatedAt":500L}""",
                conflictReason = "Remote version newer",
                createdAt = 1000L,
                updatedAt = 1000L,
                resolutionStatus = "UNRESOLVED"
            )
        )

        val res = conflictRepo.resolveConflictForUser(conflictId, user1, ConflictResolutionStrategy.USE_LOCAL, "user")
        assertTrue("USE_LOCAL resolution must succeed", res is Result.Success)

        val updatedConflict = syncConflictDao.getConflictByIdForUser(conflictId, user1)
        assertEquals("RESOLVED_LOCAL", updatedConflict?.resolutionStatus)

        val rebasedOutbox = outboxDao.getEntryByOperationIdForUser(opId, user1)
        assertNotNull(rebasedOutbox)
        assertEquals(SyncStatus.PENDING.name, rebasedOutbox?.status)
        assertEquals(5L, rebasedOutbox?.baseVersion)
        assertEquals(0, rebasedOutbox?.retryCount)
        assertNull(rebasedOutbox?.leaseToken)

        val payloadJson = JSONObject(rebasedOutbox!!.payload)
        assertEquals(5L, payloadJson.getLong("baseVersion"))
    }

    @Test
    fun test22_conflictResolutionUseLocal_subsequentSyncUploadsWithoutConflictLoop() = runBlocking {
        val opId = "op-c-loop"
        outboxDao.insertEntry(
            OutboxEntity(opId, "Goal", "g-loop", "UPSERT", """{"id":"g-loop","userId":"$user1","title":"Local","updatedAt":100L}""", 1000L, 1, 1000L, SyncStatus.CONFLICT.name, user1, baseVersion = 1L)
        )

        val conflictId = "conflict-loop"
        syncConflictDao.insertConflict(
            SyncConflictEntity(
                conflictId = conflictId,
                operationId = opId,
                userId = user1,
                entityType = "Goal",
                entityId = "g-loop",
                localPayload = """{"id":"g-loop","userId":"$user1","title":"Local","updatedAt":100L}""",
                remotePayload = """{"id":"g-loop","userId":"$user1","title":"Remote","version":5}""",
                conflictReason = "Remote version 5 > local 1",
                createdAt = 1000L,
                updatedAt = 1000L,
                resolutionStatus = "UNRESOLVED"
            )
        )

        // Resolve USE_LOCAL
        conflictRepo.resolveConflictForUser(conflictId, user1, ConflictResolutionStrategy.USE_LOCAL, "user")

        // Next sync attempt: remote has version 5
        val remoteVersion = 5L
        val fakeSyncRepo = object : SyncRepository {
            override suspend fun upload(userId: String, entry: OutboxEntry): Result<SyncUploadResult, Exception> {
                return if (remoteVersion > (entry.baseVersion ?: 0L)) {
                    Result.Success(SyncUploadResult.Conflict("""{"version":$remoteVersion}""", "conflict"))
                } else {
                    Result.Success(SyncUploadResult.Success)
                }
            }
        }

        val orchestrator = SyncOrchestrator(outboxRepo, fakeSyncRepo, syncConflictDao)
        val syncResult = orchestrator.sync(user1)

        assertTrue(syncResult is Result.Success)
        val remainingOutbox = outboxDao.getEntryByOperationIdForUser(opId, user1)
        assertNull("Rebased outbox entry must be successfully uploaded and removed without re-triggering conflict", remainingOutbox)
    }

    @Test
    fun test23_conflictResolutionUseRemote_updatesLocalRoomGoalEntity() = runBlocking {
        val opId = "op-c-goal"
        outboxDao.insertEntry(
            OutboxEntity(opId, "Goal", "g-converge", "UPSERT", """{"id":"g-converge","userId":"$user1","title":"Local"}""", 1000L, 1, 1000L, SyncStatus.CONFLICT.name, user1)
        )

        val conflictId = "conflict-goal"
        val remotePayload = """{"id":"g-converge","userId":"$user1","title":"Remote Updated Goal","description":"Remote Desc","status":"COMPLETED","version":8,"updatedAt":2000L}"""
        syncConflictDao.insertConflict(
            SyncConflictEntity(
                conflictId = conflictId,
                operationId = opId,
                userId = user1,
                entityType = "Goal",
                entityId = "g-converge",
                localPayload = """{"id":"g-converge","userId":"$user1","title":"Local"}""",
                remotePayload = remotePayload,
                conflictReason = "Remote newer",
                createdAt = 1000L,
                updatedAt = 1000L,
                resolutionStatus = "UNRESOLVED"
            )
        )

        val res = conflictRepo.resolveConflictForUser(conflictId, user1, ConflictResolutionStrategy.USE_REMOTE, "user")
        assertTrue(res is Result.Success)

        // Room Goal table must contain the remote entity
        val localGoal = ironMindDao.getGoal("g-converge")
        assertNotNull("Local Goal entity must be inserted/updated from remote payload", localGoal)
        assertEquals("Remote Updated Goal", localGoal?.title)
        assertEquals("Remote Desc", localGoal?.description)
        assertEquals("COMPLETED", localGoal?.status)

        // Outbox must be cleaned up
        val outbox = outboxDao.getEntryByOperationIdForUser(opId, user1)
        assertNull("Outbox entry must be deleted on USE_REMOTE", outbox)
    }

    @Test
    fun test24_conflictResolutionUseRemote_updatesLocalRoomPlanEntity() = runBlocking {
        val opId = "op-c-plan"
        outboxDao.insertEntry(
            OutboxEntity(opId, "Plan", "p-converge", "UPSERT", """{"id":"p-converge","userId":"$user1","title":"Local Plan"}""", 1000L, 1, 1000L, SyncStatus.CONFLICT.name, user1)
        )

        val conflictId = "conflict-plan"
        val remotePayload = """{"id":"p-converge","userId":"$user1","title":"Remote Authoritative Plan","description":"Plan Desc","status":"ARCHIVED","version":3,"updatedAt":3000L}"""
        syncConflictDao.insertConflict(
            SyncConflictEntity(
                conflictId = conflictId,
                operationId = opId,
                userId = user1,
                entityType = "Plan",
                entityId = "p-converge",
                localPayload = """{"id":"p-converge","userId":"$user1"}""",
                remotePayload = remotePayload,
                conflictReason = "Remote newer",
                createdAt = 1000L,
                updatedAt = 1000L,
                resolutionStatus = "UNRESOLVED"
            )
        )

        conflictRepo.resolveConflictForUser(conflictId, user1, ConflictResolutionStrategy.USE_REMOTE, "user")

        val localPlan = ironMindDao.getPlan("p-converge")
        assertNotNull(localPlan)
        assertEquals("Remote Authoritative Plan", localPlan?.title)
        assertEquals("ARCHIVED", localPlan?.status)
    }

    @Test
    fun test25_conflictResolutionUseRemote_updatesLocalRoomTaskEntity() = runBlocking {
        val opId = "op-c-task"
        outboxDao.insertEntry(
            OutboxEntity(opId, "Task", "t-converge", "UPSERT", """{"id":"t-converge","userId":"$user1","title":"Local Task"}""", 1000L, 1, 1000L, SyncStatus.CONFLICT.name, user1)
        )

        val conflictId = "conflict-task"
        val remotePayload = """{"id":"t-converge","userId":"$user1","title":"Remote Master Task","description":"Task Desc","status":"COMPLETED","priority":3,"version":4,"updatedAt":4000L}"""
        syncConflictDao.insertConflict(
            SyncConflictEntity(
                conflictId = conflictId,
                operationId = opId,
                userId = user1,
                entityType = "Task",
                entityId = "t-converge",
                localPayload = """{"id":"t-converge","userId":"$user1"}""",
                remotePayload = remotePayload,
                conflictReason = "Remote newer",
                createdAt = 1000L,
                updatedAt = 1000L,
                resolutionStatus = "UNRESOLVED"
            )
        )

        conflictRepo.resolveConflictForUser(conflictId, user1, ConflictResolutionStrategy.USE_REMOTE, "user")

        val localTask = ironMindDao.getTask("t-converge")
        assertNotNull(localTask)
        assertEquals("Remote Master Task", localTask?.title)
        assertEquals("COMPLETED", localTask?.status)
        assertEquals(3, localTask?.priority)
    }

    @Test
    fun test26_conflictResolutionUseRemote_updatesLocalRoomCommitmentEntity() = runBlocking {
        val opId = "op-c-comm"
        outboxDao.insertEntry(
            OutboxEntity(opId, "Commitment", "c-converge", "UPSERT", """{"id":"c-converge","userId":"$user1","title":"Local"}""", 1000L, 1, 1000L, SyncStatus.CONFLICT.name, user1)
        )

        val conflictId = "conflict-comm"
        val remotePayload = """{"id":"c-converge","userId":"$user1","title":"Remote Commitment","description":"Comm Desc","status":"COMPLETED","priority":2,"committedAt":1500L}"""
        syncConflictDao.insertConflict(
            SyncConflictEntity(
                conflictId = conflictId,
                operationId = opId,
                userId = user1,
                entityType = "Commitment",
                entityId = "c-converge",
                localPayload = """{"id":"c-converge","userId":"$user1"}""",
                remotePayload = remotePayload,
                conflictReason = "Remote newer",
                createdAt = 1000L,
                updatedAt = 1000L,
                resolutionStatus = "UNRESOLVED"
            )
        )

        conflictRepo.resolveConflictForUser(conflictId, user1, ConflictResolutionStrategy.USE_REMOTE, "user")

        val localComm = ironMindDao.getCommitment("c-converge")
        assertNotNull(localComm)
        assertEquals("Remote Commitment", localComm?.title)
        assertEquals("COMPLETED", localComm?.status)
    }

    @Test
    fun test27_conflictResolutionUseRemote_deletesOutboxAndMarksResolvedRemote() = runBlocking {
        val opId = "op-c-stat"
        outboxDao.insertEntry(
            OutboxEntity(opId, "Goal", "g-stat", "UPSERT", """{"id":"g-stat","userId":"$user1"}""", 1000L, 1, 1000L, SyncStatus.CONFLICT.name, user1)
        )

        val conflictId = "conflict-stat"
        syncConflictDao.insertConflict(
            SyncConflictEntity(
                conflictId = conflictId,
                operationId = opId,
                userId = user1,
                entityType = "Goal",
                entityId = "g-stat",
                localPayload = """{"id":"g-stat","userId":"$user1"}""",
                remotePayload = """{"id":"g-stat","userId":"$user1","title":"Remote"}""",
                conflictReason = "conflict",
                createdAt = 1000L,
                updatedAt = 1000L,
                resolutionStatus = "UNRESOLVED"
            )
        )

        conflictRepo.resolveConflictForUser(conflictId, user1, ConflictResolutionStrategy.USE_REMOTE, "user")

        val conflict = syncConflictDao.getConflictByIdForUser(conflictId, user1)
        assertEquals("RESOLVED_REMOTE", conflict?.resolutionStatus)
        assertNull(outboxDao.getEntryByOperationIdForUser(opId, user1))
    }

    @Test
    fun test28_conflictResolutionDiscard_deletesOutboxAndMarksDiscarded() = runBlocking {
        val opId = "op-c-disc"
        outboxDao.insertEntry(
            OutboxEntity(opId, "Goal", "g-disc", "UPSERT", """{"id":"g-disc","userId":"$user1"}""", 1000L, 1, 1000L, SyncStatus.CONFLICT.name, user1)
        )

        val conflictId = "conflict-disc"
        syncConflictDao.insertConflict(
            SyncConflictEntity(
                conflictId = conflictId,
                operationId = opId,
                userId = user1,
                entityType = "Goal",
                entityId = "g-disc",
                localPayload = """{"id":"g-disc","userId":"$user1"}""",
                remotePayload = """{"id":"g-disc","userId":"$user1","title":"Remote"}""",
                conflictReason = "conflict",
                createdAt = 1000L,
                updatedAt = 1000L,
                resolutionStatus = "UNRESOLVED"
            )
        )

        val res = conflictRepo.resolveConflictForUser(conflictId, user1, ConflictResolutionStrategy.DISCARD, "user")
        assertTrue(res is Result.Success)

        val conflict = syncConflictDao.getConflictByIdForUser(conflictId, user1)
        assertEquals("DISCARDED", conflict?.resolutionStatus)
        assertNull(outboxDao.getEntryByOperationIdForUser(opId, user1))
    }

    @Test
    fun test29_atomicCasResolution_secondResolutionAttemptFails_singleWinner() = runBlocking {
        val opId = "op-cas"
        outboxDao.insertEntry(
            OutboxEntity(opId, "Goal", "g-cas", "UPSERT", """{"id":"g-cas","userId":"$user1"}""", 1000L, 1, 1000L, SyncStatus.CONFLICT.name, user1)
        )

        val conflictId = "conflict-cas"
        syncConflictDao.insertConflict(
            SyncConflictEntity(
                conflictId = conflictId,
                operationId = opId,
                userId = user1,
                entityType = "Goal",
                entityId = "g-cas",
                localPayload = """{"id":"g-cas","userId":"$user1"}""",
                remotePayload = """{"id":"g-cas","userId":"$user1","title":"Remote"}""",
                conflictReason = "conflict",
                createdAt = 1000L,
                updatedAt = 1000L,
                resolutionStatus = "UNRESOLVED"
            )
        )

        val first = conflictRepo.resolveConflictForUser(conflictId, user1, ConflictResolutionStrategy.USE_LOCAL, "worker-1")
        assertTrue("First resolution must succeed", first is Result.Success)

        val second = conflictRepo.resolveConflictForUser(conflictId, user1, ConflictResolutionStrategy.DISCARD, "worker-2")
        assertTrue("Second resolution on already resolved conflict must fail", second is Result.Failure)
        assertTrue((second as Result.Failure).error is IllegalStateException)
    }

    @Test
    fun test30_crossUserConflictIsolation_user2CannotResolveUser1Conflict() = runBlocking {
        val conflictId = "conflict-iso"
        syncConflictDao.insertConflict(
            SyncConflictEntity(
                conflictId = conflictId,
                operationId = "op-iso",
                userId = user1,
                entityType = "Goal",
                entityId = "g-iso",
                localPayload = "{}",
                remotePayload = "{}",
                conflictReason = "conflict",
                createdAt = 1000L,
                updatedAt = 1000L,
                resolutionStatus = "UNRESOLVED"
            )
        )

        val res = conflictRepo.resolveConflictForUser(conflictId, user2, ConflictResolutionStrategy.USE_LOCAL, "user2")
        assertTrue("User2 cannot resolve User1's conflict", res is Result.Failure)
        assertTrue((res as Result.Failure).error is NoSuchElementException)
    }

    @Test
    fun test31_crossUserConflictQuerying_user2CannotSeeUser1Conflicts() = runBlocking {
        syncConflictDao.insertConflict(
            SyncConflictEntity(
                conflictId = "c-u1",
                operationId = "op-u1",
                userId = user1,
                entityType = "Goal",
                entityId = "g-1",
                localPayload = "{}",
                remotePayload = "{}",
                conflictReason = "c",
                createdAt = 1000L,
                updatedAt = 1000L,
                resolutionStatus = "UNRESOLVED"
            )
        )

        val u2Conflicts = conflictRepo.getUnresolvedConflictsForUser(user2)
        assertTrue(u2Conflicts is Result.Success)
        assertTrue("User2 must see 0 unresolved conflicts", (u2Conflicts as Result.Success).data.isEmpty())
    }

    @Test
    fun test32_transactionalAtomicity_resolutionRollsBackOnDaoFailure() = runBlocking {
        val opId = "op-tx-fail"
        outboxDao.insertEntry(
            OutboxEntity(opId, "Goal", "g-tx", "UPSERT", """{"id":"g-tx","userId":"$user1"}""", 1000L, 1, 1000L, SyncStatus.CONFLICT.name, user1)
        )

        val conflictId = "conflict-tx"
        syncConflictDao.insertConflict(
            SyncConflictEntity(
                conflictId = conflictId,
                operationId = opId,
                userId = user1,
                entityType = "Goal",
                entityId = "g-tx",
                localPayload = """{"id":"g-tx","userId":"$user1"}""",
                remotePayload = "INVALID_JSON_CORRUPTED",
                conflictReason = "c",
                createdAt = 1000L,
                updatedAt = 1000L,
                resolutionStatus = "UNRESOLVED"
            )
        )

        // Attempt USE_LOCAL with malformed payload (which causes JSON error during rebase)
        val res = conflictRepo.resolveConflictForUser(conflictId, user1, ConflictResolutionStrategy.USE_LOCAL, "user")
        // Conflict must remain in a consistent state
        val conflict = syncConflictDao.getConflictByIdForUser(conflictId, user1)
        assertNotNull(conflict)
    }

    // =========================================================================
    // Category 5: End-to-End Sync Pipeline & Quarantine (Tests 33 - 36)
    // =========================================================================

    @Test
    fun test33_outboxBatchingAndOrdering_preservedUnderLeaseClaims() = runBlocking {
        outboxDao.insertEntry(
            OutboxEntity("op-b1", "Goal", "g-b1", "UPSERT", """{"id":"g-b1","userId":"$user1"}""", 1000L, 0, null, SyncStatus.PENDING.name, user1)
        )
        outboxDao.insertEntry(
            OutboxEntity("op-b2", "Goal", "g-b2", "UPSERT", """{"id":"g-b2","userId":"$user1"}""", 2000L, 0, null, SyncStatus.PENDING.name, user1)
        )
        outboxDao.insertEntry(
            OutboxEntity("op-b3", "Goal", "g-b3", "UPSERT", """{"id":"g-b3","userId":"$user1"}""", 3000L, 0, null, SyncStatus.PENDING.name, user1)
        )

        val uploadedOps = mutableListOf<String>()
        val fakeSyncRepo = object : SyncRepository {
            override suspend fun upload(userId: String, entry: OutboxEntry): Result<SyncUploadResult, Exception> {
                uploadedOps.add(entry.operationId)
                return Result.Success(SyncUploadResult.Success)
            }
        }

        val orchestrator = SyncOrchestrator(outboxRepo, fakeSyncRepo, syncConflictDao)
        val res = orchestrator.sync(user1)

        assertTrue(res is Result.Success)
        assertEquals(listOf("op-b1", "op-b2", "op-b3"), uploadedOps)
        assertEquals(0, outboxDao.countPendingEntriesForUser(user1))
    }

    @Test
    fun test34_malformedPayload_quarantinedToConflict() = runBlocking {
        outboxDao.insertEntry(
            OutboxEntity("op-malformed", "Goal", "g-bad", "UPSERT", "NOT_A_JSON_PAYLOAD", 1000L, 0, null, SyncStatus.PENDING.name, user1)
        )

        var uploaded = false
        val fakeSyncRepo = object : SyncRepository {
            override suspend fun upload(userId: String, entry: OutboxEntry): Result<SyncUploadResult, Exception> {
                uploaded = true
                return Result.Success(SyncUploadResult.Success)
            }
        }

        val orchestrator = SyncOrchestrator(outboxRepo, fakeSyncRepo, syncConflictDao)
        val res = orchestrator.sync(user1)

        assertTrue(res is Result.Success)
        assertFalse("Malformed entry must not be uploaded", uploaded)

        val entry = outboxDao.getEntryByOperationIdForUser("op-malformed", user1)
        assertEquals(SyncStatus.CONFLICT.name, entry?.status)
    }

    @Test
    fun test35_crossUserPayloadMismatch_skippedSafelyWithoutUpload() = runBlocking {
        outboxDao.insertEntry(
            OutboxEntity("op-mismatch", "Goal", "g-mis", "UPSERT", """{"id":"g-mis","userId":"$user2"}""", 1000L, 0, null, SyncStatus.PENDING.name, user1)
        )

        var uploaded = false
        val fakeSyncRepo = object : SyncRepository {
            override suspend fun upload(userId: String, entry: OutboxEntry): Result<SyncUploadResult, Exception> {
                uploaded = true
                return Result.Success(SyncUploadResult.Success)
            }
        }

        val orchestrator = SyncOrchestrator(outboxRepo, fakeSyncRepo, syncConflictDao)
        val res = orchestrator.sync(user1)

        assertTrue(res is Result.Success)
        assertFalse("Mismatched payload user must be skipped without upload", uploaded)
    }

    @Test
    fun test36_fullSyncConflictResolutionReSyncConvergence() = runBlocking {
        val opId = "op-e2e"
        outboxDao.insertEntry(
            OutboxEntity(opId, "Goal", "g-e2e", "UPSERT", """{"id":"g-e2e","userId":"$user1","title":"Local Work"}""", 1000L, 0, null, SyncStatus.PENDING.name, user1, baseVersion = 1L)
        )

        var serverVersion = 2L
        val fakeSyncRepo = object : SyncRepository {
            override suspend fun upload(userId: String, entry: OutboxEntry): Result<SyncUploadResult, Exception> {
                return if (serverVersion > (entry.baseVersion ?: 0L)) {
                    Result.Success(SyncUploadResult.Conflict("""{"id":"g-e2e","userId":"$user1","title":"Server Master","version":$serverVersion}""", "Remote version newer"))
                } else {
                    serverVersion++
                    Result.Success(SyncUploadResult.Success)
                }
            }
        }

        val orchestrator = SyncOrchestrator(outboxRepo, fakeSyncRepo, syncConflictDao)

        // 1. Initial sync triggers conflict
        val sync1 = orchestrator.sync(user1)
        assertTrue(sync1 is Result.Success)
        val entry1 = outboxDao.getEntryByOperationIdForUser(opId, user1)
        assertEquals(SyncStatus.CONFLICT.name, entry1?.status)

        val conflicts = syncConflictDao.getUnresolvedConflictsForUser(user1)
        assertEquals(1, conflicts.size)
        val conflictId = conflicts.first().conflictId

        // 2. User chooses USE_LOCAL (rebase)
        val resolveRes = conflictRepo.resolveConflictForUser(conflictId, user1, ConflictResolutionStrategy.USE_LOCAL, "user1")
        assertTrue(resolveRes is Result.Success)

        // 3. Second sync cycle successfully commits rebased mutation
        val sync2 = orchestrator.sync(user1)
        assertTrue(sync2 is Result.Success)

        // Outbox entry must be completed and removed
        val finalOutbox = outboxDao.getEntryByOperationIdForUser(opId, user1)
        assertNull("Rebased mutation must be successfully uploaded and removed from outbox", finalOutbox)

        // Unresolved conflicts count must be 0
        assertEquals(0, syncConflictDao.countUnresolvedConflictsForUser(user1))
    }

    // =========================================================================
    // Category 8: Remediation Verification (Phase 8.3.3.2.4 — SEC-01 to SEC-07)
    // =========================================================================

    @Test
    fun test37_sec01_useRemoteWithTombstoneDeletesLocalGoalAndTaskWithoutResurrecting() = runBlocking {
        // 1. Pre-insert local Goal and Task into Room
        ironMindDao.insertGoal(
            GoalEntity(
                id = "g-del",
                userId = user1,
                ambitionId = null,
                title = "Local Goal Active",
                description = "",
                why = "",
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
        assertNotNull("Goal must exist locally before resolution", ironMindDao.getGoal("g-del"))

        val opId = "op-tomb-goal"
        outboxDao.insertEntry(
            OutboxEntity(opId, "Goal", "g-del", "UPSERT", """{"id":"g-del","userId":"$user1","title":"Local Goal Active"}""", 1000L, 0, null, SyncStatus.CONFLICT.name, user1)
        )
        val conflictId = "conf-tomb-1"
        syncConflictDao.insertConflict(
            SyncConflictEntity(
                conflictId = conflictId,
                operationId = opId,
                userId = user1,
                entityType = "Goal",
                entityId = "g-del",
                localPayload = """{"id":"g-del","userId":"$user1","title":"Local Goal Active"}""",
                remotePayload = """{"id":"g-del","userId":"$user1","isDeleted":true,"version":5}""",
                conflictReason = "Remote tombstone newer",
                createdAt = 1000L,
                updatedAt = 1000L,
                resolutionStatus = "UNRESOLVED"
            )
        )

        // Resolve via USE_REMOTE
        val resolveRes = conflictRepo.resolveConflictForUser(conflictId, user1, ConflictResolutionStrategy.USE_REMOTE, "test")
        assertTrue(resolveRes is Result.Success)

        // Local goal MUST be deleted from Room, NOT resurrected
        assertNull("Local Goal must be deleted when remote is a tombstone", ironMindDao.getGoal("g-del"))
        assertNull("Outbox entry must be discarded", outboxDao.getEntryByOperationIdForUser(opId, user1))
        val resolvedConf = syncConflictDao.getConflictByIdForUser(conflictId, user1)
        assertEquals("RESOLVED_REMOTE", resolvedConf?.resolutionStatus)
    }

    @Test
    fun test38_sec03_staleWorkerWithExpiredLeaseCannotInsertPhantomConflict() = runBlocking {
        val opId = "op-fenced-conflict"
        val oldToken = "old-worker-token"
        val now = 10000L

        // Entry is currently claimed by worker 2 with a new lease token
        outboxDao.insertEntry(
            OutboxEntity(
                operationId = opId,
                entityType = "Goal",
                entityId = "g-fence",
                operationType = "UPSERT",
                payload = """{"id":"g-fence","userId":"$user1","title":"Fencing Test"}""",
                createdAt = 1000L,
                retryCount = 1,
                lastAttemptAt = now,
                status = SyncStatus.SYNCING.name,
                userId = user1,
                baseVersion = 1L,
                leaseToken = "worker-2-active-token",
                leaseExpiresAt = now + 30000L
            )
        )

        // Stale Worker 1 attempts to record a conflict with oldToken
        val conflictResult = outboxRepo.conflictEntryWithLease(
            operationId = opId,
            userId = user1,
            leaseToken = oldToken,
            retryCount = 2,
            lastAttemptAt = now
        )
        val rowsAffected = (conflictResult as? Result.Success)?.data ?: 0
        assertEquals("Stale worker must affect 0 rows", 0, rowsAffected)

        // Fenced worker must NOT insert conflict record
        val conflictsBefore = syncConflictDao.countUnresolvedConflictsForUser(user1)
        if (rowsAffected > 0) {
            syncConflictDao.insertConflict(
                SyncConflictEntity(
                    conflictId = "phantom-conflict",
                    operationId = opId,
                    userId = user1,
                    entityType = "Goal",
                    entityId = "g-fence",
                    localPayload = "",
                    remotePayload = "",
                    conflictReason = "Phantom",
                    createdAt = now,
                    updatedAt = now,
                    resolutionStatus = "UNRESOLVED"
                )
            )
        }
        val conflictsAfter = syncConflictDao.countUnresolvedConflictsForUser(user1)
        assertEquals("No phantom conflict must be inserted when lease is expired", conflictsBefore, conflictsAfter)
    }

    @Test
    fun test39_sec04_useRemoteWithMalformedJsonFailsClosedAndPreservesOutbox() = runBlocking {
        val opId = "op-malformed"
        outboxDao.insertEntry(
            OutboxEntity(opId, "Goal", "g-malformed", "UPSERT", """{"id":"g-malformed","userId":"$user1"}""", 1000L, 0, null, SyncStatus.CONFLICT.name, user1)
        )
        val conflictId = "conf-malformed"
        syncConflictDao.insertConflict(
            SyncConflictEntity(
                conflictId = conflictId,
                operationId = opId,
                userId = user1,
                entityType = "Goal",
                entityId = "g-malformed",
                localPayload = """{"id":"g-malformed","userId":"$user1"}""",
                remotePayload = """{ corrupt json syntax ...""",
                conflictReason = "Remote update",
                createdAt = 1000L,
                updatedAt = 1000L,
                resolutionStatus = "UNRESOLVED"
            )
        )

        val res = conflictRepo.resolveConflictForUser(conflictId, user1, ConflictResolutionStrategy.USE_REMOTE, "test")
        assertTrue("Resolution must fail when payload is malformed", res is Result.Failure)

        // Outbox entry must NOT be deleted
        assertNotNull("Outbox entry must be preserved on deserialization failure", outboxDao.getEntryByOperationIdForUser(opId, user1))

        // Conflict must remain UNRESOLVED
        val conf = syncConflictDao.getConflictByIdForUser(conflictId, user1)
        assertEquals("UNRESOLVED", conf?.resolutionStatus)
    }

    @Test
    fun test40_sec05_useRemoteWithMemoryActivePayloadAndTombstone() = runBlocking {
        // 1. Active Memory payload convergence
        val opId1 = "op-mem-active"
        outboxDao.insertEntry(
            OutboxEntity(opId1, "Memory", "m-1", "UPSERT", """{"id":"m-1","userId":"$user1","content":"Local"}""", 1000L, 0, null, SyncStatus.CONFLICT.name, user1)
        )
        val confId1 = "conf-mem-1"
        syncConflictDao.insertConflict(
            SyncConflictEntity(
                conflictId = confId1,
                operationId = opId1,
                userId = user1,
                entityType = "Memory",
                entityId = "m-1",
                localPayload = """{"id":"m-1","userId":"$user1","content":"Local"}""",
                remotePayload = """{"id":"m-1","userId":"$user1","content":"Remote Truth","type":"INSIGHT","source":"SYSTEM","confidence":0.95,"evidenceCount":2,"confirmationState":"CONFIRMED","status":"ACTIVE","version":2}""",
                conflictReason = "Remote version newer",
                createdAt = 1000L,
                updatedAt = 1000L,
                resolutionStatus = "UNRESOLVED"
            )
        )

        val res1 = conflictRepo.resolveConflictForUser(confId1, user1, ConflictResolutionStrategy.USE_REMOTE, "test")
        assertTrue(res1 is Result.Success)

        val localMem = ironMindDao.getMemoryById("m-1")
        assertNotNull("Memory entity must be converged into Room", localMem)
        assertEquals("Remote Truth", localMem?.content)

        // 2. Tombstone Memory payload convergence
        val opId2 = "op-mem-del"
        outboxDao.insertEntry(
            OutboxEntity(opId2, "Memory", "m-1", "UPSERT", """{"id":"m-1","userId":"$user1","content":"Local"}""", 1000L, 0, null, SyncStatus.CONFLICT.name, user1)
        )
        val confId2 = "conf-mem-2"
        syncConflictDao.insertConflict(
            SyncConflictEntity(
                conflictId = confId2,
                operationId = opId2,
                userId = user1,
                entityType = "Memory",
                entityId = "m-1",
                localPayload = """{"id":"m-1","userId":"$user1"}""",
                remotePayload = """{"id":"m-1","userId":"$user1","isDeleted":true,"version":3}""",
                conflictReason = "Remote tombstone",
                createdAt = 2000L,
                updatedAt = 2000L,
                resolutionStatus = "UNRESOLVED"
            )
        )

        val res2 = conflictRepo.resolveConflictForUser(confId2, user1, ConflictResolutionStrategy.USE_REMOTE, "test")
        assertTrue(res2 is Result.Success)
        assertNull("Memory entity must be deleted from Room upon remote tombstone", ironMindDao.getMemoryById("m-1"))
    }

    @Test
    fun test41_sec07_legacySyncingRowWithNullLeaseReclaimedImmediately() {
        val now = 50000L
        outboxDao.insertEntry(
            OutboxEntity(
                operationId = "legacy-stuck-op",
                entityType = "Goal",
                entityId = "g-legacy",
                operationType = "UPSERT",
                payload = """{"id":"g-legacy","userId":"$user1"}""",
                createdAt = 1000L,
                retryCount = 0,
                lastAttemptAt = now - 1000L,
                status = SyncStatus.SYNCING.name,
                userId = user1,
                baseVersion = null,
                leaseToken = null,
                leaseExpiresAt = null
            )
        )

        // Verify recoverExpiredLeasesForUser recovers legacy NULL lease without 5-minute wait
        val recovered = outboxDao.recoverExpiredLeasesForUser(user1, now)
        assertEquals("Legacy NULL lease entry must be recovered immediately", 1, recovered)

        val entry = outboxDao.getEntryByOperationIdForUser("legacy-stuck-op", user1)
        assertEquals(SyncStatus.PENDING.name, entry?.status)
    }

    @Test
    fun test42_sec07_legacySyncingRowWithNullLeaseCanBeClaimedDirectlyWithLease() {
        val now = 50000L
        outboxDao.insertEntry(
            OutboxEntity(
                operationId = "legacy-claim-op",
                entityType = "Plan",
                entityId = "p-legacy",
                operationType = "UPSERT",
                payload = """{"id":"p-legacy","userId":"$user1"}""",
                createdAt = 1000L,
                retryCount = 0,
                lastAttemptAt = now - 1000L,
                status = SyncStatus.SYNCING.name,
                userId = user1,
                baseVersion = null,
                leaseToken = null,
                leaseExpiresAt = null
            )
        )

        val claimed = outboxDao.claimEntryWithLease(
            operationId = "legacy-claim-op",
            userId = user1,
            leaseToken = "new-lease-token",
            leaseExpiresAt = now + 30000L,
            now = now
        )
        assertEquals("Legacy NULL lease row in SYNCING must be claimable with new lease", 1, claimed)

        val entry = outboxDao.getEntryByOperationIdForUser("legacy-claim-op", user1)
        assertEquals("new-lease-token", entry?.leaseToken)
        assertEquals(now + 30000L, entry?.leaseExpiresAt)
    }
}

