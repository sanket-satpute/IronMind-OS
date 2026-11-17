package com.sanket_satpute_20.ironmind.data.sync

import android.content.Context
import androidx.arch.core.executor.ArchTaskExecutor
import androidx.arch.core.executor.TaskExecutor
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.sanket_satpute_20.ironmind.data.local.IronMindDatabase
import com.sanket_satpute_20.ironmind.data.local.Jdk25CompatibleRobolectricTestRunner
import com.sanket_satpute_20.ironmind.data.local.dao.OutboxDao
import com.sanket_satpute_20.ironmind.data.local.dao.SyncConflictDao
import com.sanket_satpute_20.ironmind.data.local.entity.OutboxEntity
import com.sanket_satpute_20.ironmind.data.repository.OutboxRepositoryImpl
import com.sanket_satpute_20.ironmind.data.repository.SyncConflictRepositoryImpl
import com.sanket_satpute_20.ironmind.data.session.UserSessionProviderImpl
import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.AuthUser
import com.sanket_satpute_20.ironmind.domain.model.OutboxEntry
import com.sanket_satpute_20.ironmind.domain.model.OutboxOperationType
import com.sanket_satpute_20.ironmind.domain.model.SyncStatus
import com.sanket_satpute_20.ironmind.domain.repository.AuthRepository
import com.sanket_satpute_20.ironmind.domain.repository.ConflictResolutionStrategy
import com.sanket_satpute_20.ironmind.domain.repository.SyncRepository
import com.sanket_satpute_20.ironmind.domain.repository.SyncUploadResult
import io.mockk.mockk
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(Jdk25CompatibleRobolectricTestRunner::class)
@Config(sdk = [34])
class SyncReliabilityAndConflictTest {

    private lateinit var context: Context
    private lateinit var db: IronMindDatabase
    private lateinit var outboxDao: OutboxDao
    private lateinit var syncConflictDao: SyncConflictDao
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
        outboxRepo = OutboxRepositoryImpl(outboxDao)
        conflictRepo = SyncConflictRepositoryImpl(syncConflictDao, outboxDao)
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
    // Part 1A: Serialize Sync Operations Per User
    // =========================================================================

    @Test
    fun testSameUserConcurrentSync_isStrictlySerialized_noDuplicateUploads() = runBlocking {
        // Seed 1 pending item for user1
        outboxDao.insertEntry(
            OutboxEntity("op-1", "Goal", "g-1", "UPSERT", """{"id":"g-1","userId":"$user1","updatedAt":100L}""", 1000L, 0, null, SyncStatus.PENDING.name, user1)
        )

        val uploadCount = AtomicInteger(0)
        val concurrentUploads = AtomicInteger(0)
        val maxConcurrentUploads = AtomicInteger(0)

        val delayedSyncRepo = object : SyncRepository {
            override suspend fun upload(userId: String, entry: OutboxEntry): Result<SyncUploadResult, Exception> {
                val current = concurrentUploads.incrementAndGet()
                if (current > maxConcurrentUploads.get()) {
                    maxConcurrentUploads.set(current)
                }
                delay(100) // simulate network latency
                uploadCount.incrementAndGet()
                concurrentUploads.decrementAndGet()
                return Result.Success(SyncUploadResult.Success)
            }
        }

        val orchestrator = SyncOrchestrator(outboxRepo, delayedSyncRepo, syncConflictDao)

        // Launch 2 concurrent sync calls for the SAME user
        val job1 = async(Dispatchers.Default) { orchestrator.sync(user1) }
        val job2 = async(Dispatchers.Default) { orchestrator.sync(user1) }

        val res1 = job1.await()
        val res2 = job2.await()

        assertTrue(res1 is Result.Success)
        assertTrue(res2 is Result.Success)
        assertEquals("Max concurrent uploads for the same user must be at most 1 (serialized by Mutex)", 1, maxConcurrentUploads.get())
        assertEquals("Upload must only be executed once, not duplicated", 1, uploadCount.get())
    }

    @Test
    fun testDifferentUsersConcurrentSync_runIndependentlyWithoutBlocking() = runBlocking {
        outboxDao.insertEntry(
            OutboxEntity("op-u1", "Goal", "g-1", "UPSERT", """{"id":"g-1","userId":"$user1","updatedAt":100L}""", 1000L, 0, null, SyncStatus.PENDING.name, user1)
        )
        outboxDao.insertEntry(
            OutboxEntity("op-u2", "Goal", "g-2", "UPSERT", """{"id":"g-2","userId":"$user2","updatedAt":100L}""", 1000L, 0, null, SyncStatus.PENDING.name, user2)
        )

        val startedU1 = kotlinx.coroutines.CompletableDeferred<Unit>()
        val releaseU1 = kotlinx.coroutines.CompletableDeferred<Unit>()
        val u2Completed = AtomicInteger(0)

        val testSyncRepo = object : SyncRepository {
            override suspend fun upload(userId: String, entry: OutboxEntry): Result<SyncUploadResult, Exception> {
                if (userId == user1) {
                    startedU1.complete(Unit)
                    releaseU1.await() // Hold user1
                } else if (userId == user2) {
                    u2Completed.incrementAndGet()
                }
                return Result.Success(SyncUploadResult.Success)
            }
        }

        val orchestrator = SyncOrchestrator(outboxRepo, testSyncRepo, syncConflictDao)

        // Launch user1 sync (will hold)
        val job1 = async(Dispatchers.Default) { orchestrator.sync(user1) }
        startedU1.await() // user1 is now holding inside its mutex

        // Launch user2 sync — must NOT be blocked by user1's mutex!
        val job2 = async(Dispatchers.Default) { orchestrator.sync(user2) }
        val res2 = job2.await()
        assertTrue("User 2 sync must succeed independently while user 1 is in-flight", res2 is Result.Success)
        assertEquals("User 2 must finish its upload without being blocked", 1, u2Completed.get())

        // Release user1
        releaseU1.complete(Unit)
        val res1 = job1.await()
        assertTrue(res1 is Result.Success)
    }

    // =========================================================================
    // Part 1B: Atomic Outbox Status Transition (CAS)
    // =========================================================================

    @Test
    fun testAtomicSyncingClaim_andZeroRowSkipUpload() = runBlocking {
        outboxDao.insertEntry(
            OutboxEntity("op-cas", "Goal", "g-cas", "UPSERT", """{"id":"g-cas","userId":"$user1","updatedAt":100L}""", 1000L, 0, null, SyncStatus.PENDING.name, user1)
        )

        val now = 2000L
        // First claim: status is PENDING -> exactly 1 row claimed
        val rows1 = outboxDao.claimEntryForSync("op-cas", user1, now)
        assertEquals(1, rows1)

        // Verify status is now SYNCING
        val entry = outboxDao.getEntryByOperationIdForUser("op-cas", user1)
        assertEquals(SyncStatus.SYNCING.name, entry?.status)
        assertEquals(now, entry?.lastAttemptAt)

        // Second claim attempt while status is SYNCING -> must return 0 rows
        val rows2 = outboxDao.claimEntryForSync("op-cas", user1, now + 10L)
        assertEquals("Concurrent or duplicate claim on already SYNCING entry must affect 0 rows", 0, rows2)

        // Cross-user claim attempt -> must return 0 rows
        val rowsCross = outboxDao.claimEntryForSync("op-cas", user2, now + 20L)
        assertEquals("Cross-user claim must affect 0 rows", 0, rowsCross)
    }

    // =========================================================================
    // Part 1C: Recover Abandoned SYNCING Entries
    // =========================================================================

    @Test
    fun testRecoverStaleSyncingEntries_recoversStaleAndPreservesActiveSyncing() = runBlocking {
        val now = 10_000_000L
        val staleThreshold = now - SyncOrchestrator.STALE_SYNCING_THRESHOLD_MS // now - 5 mins

        // 1. Genuinely stale entry (lastAttemptAt is 10 minutes ago)
        outboxDao.insertEntry(
            OutboxEntity("op-stale", "Goal", "g-1", "UPSERT", """{"id":"g-1","userId":"$user1"}""", 1000L, 1, now - (10 * 60 * 1000L), SyncStatus.SYNCING.name, user1)
        )

        // 2. Fresh, active SYNCING entry (lastAttemptAt is 30 seconds ago)
        outboxDao.insertEntry(
            OutboxEntity("op-active", "Goal", "g-2", "UPSERT", """{"id":"g-2","userId":"$user1"}""", 1000L, 0, now - 30_000L, SyncStatus.SYNCING.name, user1)
        )

        // 3. Stale entry belonging to USER 2 (must NOT be recovered by user 1)
        outboxDao.insertEntry(
            OutboxEntity("op-u2-stale", "Goal", "g-3", "UPSERT", """{"id":"g-3","userId":"$user2"}""", 1000L, 1, now - (10 * 60 * 1000L), SyncStatus.SYNCING.name, user2)
        )

        // User 1 runs stale recovery
        val recovered = outboxDao.recoverStaleSyncingEntriesForUser(user1, staleThreshold)
        assertEquals("Exactly 1 stale entry belonging to user 1 must be recovered", 1, recovered)

        // Verify op-stale is now PENDING
        val staleEntry = outboxDao.getEntryByOperationIdForUser("op-stale", user1)
        assertEquals(SyncStatus.PENDING.name, staleEntry?.status)
        assertEquals(1, staleEntry?.retryCount) // Preserves retry count!

        // Verify op-active remains SYNCING (not reset)
        val activeEntry = outboxDao.getEntryByOperationIdForUser("op-active", user1)
        assertEquals(SyncStatus.SYNCING.name, activeEntry?.status)

        // Verify op-u2-stale remains SYNCING (user 1 cannot recover user 2 records)
        val u2Entry = outboxDao.getEntryByOperationIdForUser("op-u2-stale", user2)
        assertEquals(SyncStatus.SYNCING.name, u2Entry?.status)
    }

    // =========================================================================
    // Part 1D: Post-Claim Verification Rollback
    // =========================================================================

    @Test
    fun testPostClaimVerificationFailure_rollsBackClaimSafely() = runBlocking {
        // Candidate with valid initial format in outbox
        outboxDao.insertEntry(
            OutboxEntity("op-tampered", "Goal", "g-t", "UPSERT", """{"id":"g-t","userId":"$user1"}""", 1000L, 0, null, SyncStatus.PENDING.name, null)
        )

        // Simulate post-claim verification rollback:
        // When claimLegacyEntry runs, userId becomes user1:
        val claimRows = outboxDao.claimLegacyEntry("op-tampered", user1)
        assertEquals(1, claimRows)

        // Suppose post-claim payload verification detects unexpected mismatch -> rollback triggered:
        val rollbackRows = outboxDao.rollbackLegacyEntryClaim("op-tampered", user1)
        assertEquals("Rollback must affect 1 row", 1, rollbackRows)

        // Verify record in DB has userId set back to NULL
        val orphan = outboxDao.getOrphanedEntries().firstOrNull { it.operationId == "op-tampered" }
        assertNotNull(orphan)
        assertNull(orphan?.userId)

        // Ensure user2 cannot roll back user1's claim
        val falseRollback = outboxDao.rollbackLegacyEntryClaim("op-tampered", user2)
        assertEquals(0, falseRollback)
    }

    // =========================================================================
    // Part 1E: Explicit Logout Generation Invalidation
    // =========================================================================

    @Test
    fun testExplicitLogout_immediatelyIncrementsSessionGeneration() = runBlocking {
        val authRepo = com.sanket_satpute_20.ironmind.testutil.fake.FakeAuthRepository()
        authRepo.setTestUser(AuthUser(id = user1, isAnonymous = false, email = "user@test.com"))

        val testScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val sessionProvider = UserSessionProviderImpl(authRepo, scope = testScope)

        // Wait until user1 is authenticated
        withTimeout(2000L) {
            sessionProvider.sessionState.first { it is com.sanket_satpute_20.ironmind.domain.session.SessionState.Authenticated }
        }

        // Initially check authenticated user generation
        val genInitial = sessionProvider.getSessionGeneration()
        assertTrue("Initial session generation must be at least 1", genInitial >= 1L)

        // User logs out -> authRepo emits null
        authRepo.signOut()
        withTimeout(2000L) {
            sessionProvider.sessionState.first { it is com.sanket_satpute_20.ironmind.domain.session.SessionState.OfflineFallback }
        }

        val genAfterLogout = sessionProvider.getSessionGeneration()
        assertEquals("Session generation must advance on explicit logout", genInitial + 1, genAfterLogout)

        // Subsequent login -> generation advances again
        authRepo.setTestUser(AuthUser(id = user2, isAnonymous = false, email = "user2@test.com"))
        withTimeout(2000L) {
            sessionProvider.sessionState.first { it is com.sanket_satpute_20.ironmind.domain.session.SessionState.Authenticated && it.currentUserId == user2 }
        }

        val genAfterLogin = sessionProvider.getSessionGeneration()
        assertEquals("Session generation must advance on new user login", genAfterLogout + 1, genAfterLogin)
    }

    // =========================================================================
    // Part 2: Document-Level Sync Conflict Detection & Resolution
    // =========================================================================

    @Test
    fun testRemoteVersionUnchanged_uploadAllowedAndOutboxDeleted() = runBlocking {
        outboxDao.insertEntry(
            OutboxEntity("op-upload", "Goal", "g-1", "UPSERT", """{"id":"g-1","userId":"$user1","updatedAt":500L}""", 1000L, 0, null, SyncStatus.PENDING.name, user1)
        )

        val fakeSyncRepo = object : SyncRepository {
            override suspend fun upload(userId: String, entry: OutboxEntry): Result<SyncUploadResult, Exception> {
                // Remote version has older or equal updatedAt (400L <= 500L) -> Success
                return Result.Success(SyncUploadResult.Success)
            }
        }

        val orchestrator = SyncOrchestrator(outboxRepo, fakeSyncRepo, syncConflictDao)
        val result = orchestrator.sync(user1)

        assertTrue(result is Result.Success)
        // Outbox entry must be deleted on success
        assertNull(outboxDao.getEntryByOperationIdForUser("op-upload", user1))
        assertEquals(0, outboxDao.countPendingEntriesForUser(user1))
    }

    @Test
    fun testRemoteVersionNewer_conflictDetected_persistedDurableInSyncConflicts() = runBlocking {
        outboxDao.insertEntry(
            OutboxEntity("op-conflict", "Goal", "g-c", "UPSERT", """{"id":"g-c","userId":"$user1","title":"Local Edit","updatedAt":200L}""", 1000L, 0, null, SyncStatus.PENDING.name, user1)
        )

        val remotePayloadJson = """{"id":"g-c","userId":"$user1","title":"Remote Newer Edit","updatedAt":800L}"""
        val fakeSyncRepo = object : SyncRepository {
            override suspend fun upload(userId: String, entry: OutboxEntry): Result<SyncUploadResult, Exception> {
                // Remote version has newer updatedAt (800L > 200L) -> Conflict
                return Result.Success(
                    SyncUploadResult.Conflict(
                        remotePayload = remotePayloadJson,
                        conflictReason = "Remote version is newer: remote updatedAt (800) > local updatedAt (200)"
                    )
                )
            }
        }

        val orchestrator = SyncOrchestrator(outboxRepo, fakeSyncRepo, syncConflictDao)
        val result = orchestrator.sync(user1)

        assertTrue(result is Result.Success)

        // 1. Outbox entry must be marked CONFLICT (not deleted, not pending)
        val outbox = outboxDao.getEntryByOperationIdForUser("op-conflict", user1)
        assertNotNull(outbox)
        assertEquals(SyncStatus.CONFLICT.name, outbox?.status)

        // 2. Durable conflict record MUST be stored in sync_conflicts table
        val conflicts = syncConflictDao.getUnresolvedConflictsForUser(user1)
        assertEquals(1, conflicts.size)

        val conflict = conflicts[0]
        assertEquals("op-conflict", conflict.operationId)
        assertEquals(user1, conflict.userId)
        assertEquals("Goal", conflict.entityType)
        assertEquals("g-c", conflict.entityId)
        assertTrue(conflict.localPayload.contains("Local Edit"))
        assertEquals(remotePayloadJson, conflict.remotePayload)
        assertEquals("UNRESOLVED", conflict.resolutionStatus)
    }

    @Test
    fun testConflictResolution_useLocal_resetsOutboxToPendingAndResolvesConflict() = runBlocking {
        // Seed an existing unresolved conflict
        syncConflictDao.insertConflict(
            com.sanket_satpute_20.ironmind.data.local.entity.SyncConflictEntity(
                conflictId = "conflict-1",
                operationId = "op-res-1",
                userId = user1,
                entityType = "Goal",
                entityId = "g-res",
                localPayload = """{"id":"g-res","userId":"$user1"}""",
                remotePayload = """{"id":"g-res","userId":"$user1","updatedAt":999L}""",
                conflictReason = "Remote version newer",
                createdAt = 1000L,
                updatedAt = 1000L,
                resolutionStatus = "UNRESOLVED"
            )
        )
        outboxDao.insertEntry(
            OutboxEntity("op-res-1", "Goal", "g-res", "UPSERT", """{"id":"g-res","userId":"$user1"}""", 1000L, 1, 1000L, SyncStatus.CONFLICT.name, user1)
        )

        // User resolves with USE_LOCAL
        val resolveRes = conflictRepo.resolveConflictForUser("conflict-1", user1, ConflictResolutionStrategy.USE_LOCAL)
        assertTrue(resolveRes is Result.Success)

        // Conflict record status updated to RESOLVED_LOCAL
        val updatedConflict = syncConflictDao.getConflictByIdForUser("conflict-1", user1)
        assertEquals("RESOLVED_LOCAL", updatedConflict?.resolutionStatus)
        assertEquals(user1, updatedConflict?.resolvedBy)

        // Outbox entry reset to PENDING
        val updatedOutbox = outboxDao.getEntryByOperationIdForUser("op-res-1", user1)
        assertEquals(SyncStatus.PENDING.name, updatedOutbox?.status)
        assertEquals(0, updatedOutbox?.retryCount)
    }

    @Test
    fun testConflictResolution_useRemote_discardsLocalOutboxEntry() = runBlocking {
        syncConflictDao.insertConflict(
            com.sanket_satpute_20.ironmind.data.local.entity.SyncConflictEntity(
                conflictId = "conflict-2",
                operationId = "op-res-2",
                userId = user1,
                entityType = "Goal",
                entityId = "g-res-2",
                localPayload = "{}",
                remotePayload = "{}",
                conflictReason = "Remote version newer",
                createdAt = 1000L,
                updatedAt = 1000L,
                resolutionStatus = "UNRESOLVED"
            )
        )
        outboxDao.insertEntry(
            OutboxEntity("op-res-2", "Goal", "g-res-2", "UPSERT", "{}", 1000L, 1, 1000L, SyncStatus.CONFLICT.name, user1)
        )

        // User resolves with USE_REMOTE
        val resolveRes = conflictRepo.resolveConflictForUser("conflict-2", user1, ConflictResolutionStrategy.USE_REMOTE)
        assertTrue(resolveRes is Result.Success)

        // Conflict marked RESOLVED_REMOTE
        val updatedConflict = syncConflictDao.getConflictByIdForUser("conflict-2", user1)
        assertEquals("RESOLVED_REMOTE", updatedConflict?.resolutionStatus)

        // Local outbox mutation discarded
        assertNull("Local outbox mutation must be discarded", outboxDao.getEntryByOperationIdForUser("op-res-2", user1))
    }

    @Test
    fun testConflictResolution_crossUserAccessForbidden() = runBlocking {
        syncConflictDao.insertConflict(
            com.sanket_satpute_20.ironmind.data.local.entity.SyncConflictEntity(
                conflictId = "conflict-u1",
                operationId = "op-u1",
                userId = user1,
                entityType = "Goal",
                entityId = "g-1",
                localPayload = "{}",
                remotePayload = "{}",
                conflictReason = "Remote newer",
                createdAt = 1000L,
                updatedAt = 1000L,
                resolutionStatus = "UNRESOLVED"
            )
        )

        // User 2 attempts to resolve User 1's conflict
        val crossResolve = conflictRepo.resolveConflictForUser("conflict-u1", user2, ConflictResolutionStrategy.USE_LOCAL)
        assertTrue("Cross user resolution must fail closed", crossResolve is Result.Failure)

        // Conflict remains UNRESOLVED for User 1
        val conflict = syncConflictDao.getConflictByIdForUser("conflict-u1", user1)
        assertEquals("UNRESOLVED", conflict?.resolutionStatus)
    }
}

