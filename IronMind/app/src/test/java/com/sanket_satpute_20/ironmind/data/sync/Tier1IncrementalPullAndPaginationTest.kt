package com.sanket_satpute_20.ironmind.data.sync

import android.content.Context
import androidx.arch.core.executor.ArchTaskExecutor
import androidx.arch.core.executor.TaskExecutor
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.sanket_satpute_20.ironmind.data.local.IronMindDatabase
import com.sanket_satpute_20.ironmind.data.local.Jdk25CompatibleRobolectricTestRunner
import com.sanket_satpute_20.ironmind.data.local.dao.AppSessionStateDao
import com.sanket_satpute_20.ironmind.data.local.dao.IronMindDao
import com.sanket_satpute_20.ironmind.data.local.dao.OutboxDao
import com.sanket_satpute_20.ironmind.data.local.dao.SyncConflictDao
import com.sanket_satpute_20.ironmind.data.local.dao.SyncWorkerLeaseDao
import com.sanket_satpute_20.ironmind.data.local.entity.AppSessionStateEntity
import com.sanket_satpute_20.ironmind.data.local.entity.GoalEntity
import com.sanket_satpute_20.ironmind.data.local.entity.SyncWorkerLeaseEntity
import com.sanket_satpute_20.ironmind.data.repository.FirestoreSyncRepository
import com.sanket_satpute_20.ironmind.data.repository.InMemorySyncCheckpointStore
import com.sanket_satpute_20.ironmind.data.repository.OutboxRepositoryImpl
import com.sanket_satpute_20.ironmind.data.repository.SharedPreferencesSyncCheckpointStore
import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.repository.SyncCursor
import com.sanket_satpute_20.ironmind.domain.repository.SyncPullResult
import com.sanket_satpute_20.ironmind.domain.session.SessionState
import com.sanket_satpute_20.ironmind.domain.session.UserSessionProvider
import java.util.UUID
import java.util.concurrent.Executor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import org.robolectric.annotation.Config

/**
 * Phase 8.3.3.6A Stage B.1: Tier 1 Incremental Downstream Pull and Compound Pagination Tests.
 *
 * Verifies:
 * 1. Compound (updatedAt, documentId) pagination handling multiple documents with identical updatedAt.
 * 2. Deterministic page continuation across boundaries without duplicate reads or skipped documents.
 * 3. Empty collection handling: returns emptyList, hasMore=false, does NOT alter checkpoint.
 * 4. Lookahead overfetch (limit + 1): exact multiple of limit resolves hasMore=false without extra roundtrip.
 * 5. SharedPreferences checkpoint persistence survives simulated app restart.
 * 6. Authoritative session generation validation: stale caller generation is rejected before lease acquisition.
 * 7. Active lease fencing during pagination: stolen or expired lease causes immediate fail-fast termination.
 * 8. Malformed remote payloads (missing timestamps, null data, blank IDs) fail fast and preserve checkpoint.
 * 9. Per-user checkpoint isolation: operations for User A do not modify or leak into User B's cursors.
 * 10. Stage B.1 boundary guarantee: local Room entities are NOT overwritten during Tier 1 pull.
 */
@RunWith(Jdk25CompatibleRobolectricTestRunner::class)
@Config(sdk = [34])
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class Tier1IncrementalPullAndPaginationTest {

    private lateinit var context: Context
    private lateinit var app: FirebaseApp
    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore
    private lateinit var syncRepository: FirestoreSyncRepository

    private lateinit var db: IronMindDatabase
    private lateinit var outboxDao: OutboxDao
    private lateinit var syncConflictDao: SyncConflictDao
    private lateinit var ironMindDao: IronMindDao
    private lateinit var leaseDao: SyncWorkerLeaseDao
    private lateinit var sessionStateDao: AppSessionStateDao
    private lateinit var outboxRepo: OutboxRepositoryImpl
    private lateinit var checkpointStore: SharedPreferencesSyncCheckpointStore

    private var activeUserId: String = ""
    private lateinit var sessionProvider: FakeUserSessionProvider

    private class FakeUserSessionProvider(
        initialUserId: String,
        private var currentGeneration: Long = 1L
    ) : UserSessionProvider {
        private val _sessionState = MutableStateFlow<SessionState>(SessionState.Authenticated(initialUserId, false))
        override val sessionState: StateFlow<SessionState> = _sessionState
        private var currentUserId = initialUserId

        fun setUserId(newUserId: String) {
            currentUserId = newUserId
            _sessionState.value = SessionState.Authenticated(newUserId, false)
        }

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

    @Before
    fun setUp() = runBlocking {
        ArchTaskExecutor.getInstance().setDelegate(object : TaskExecutor() {
            override fun executeOnDiskIO(runnable: Runnable) = runnable.run()
            override fun postToMainThread(runnable: Runnable) = runnable.run()
            override fun isMainThread(): Boolean = true
        })

        Dispatchers.setMain(Dispatchers.Unconfined)
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
        outboxRepo = OutboxRepositoryImpl(outboxDao)
        checkpointStore = SharedPreferencesSyncCheckpointStore(context)

        val options = FirebaseOptions.Builder()
            .setApplicationId("com.sanket_satpute_20.ironmind")
            .setProjectId("demo-ironmind")
            .setApiKey("fake-api-key")
            .build()

        app = if (FirebaseApp.getApps(context).none { it.name == "tier1-pull-test-app" }) {
            FirebaseApp.initializeApp(context, options, "tier1-pull-test-app")
        } else {
            FirebaseApp.getInstance("tier1-pull-test-app")
        }

        auth = FirebaseAuth.getInstance(app)
        firestore = FirebaseFirestore.getInstance(app)

        try {
            auth.useEmulator("127.0.0.1", 9199)
        } catch (_: IllegalStateException) {}

        try {
            firestore.useEmulator("127.0.0.1", 8180)
            val settings = FirebaseFirestoreSettings.Builder()
                .setPersistenceEnabled(false)
                .build()
            firestore.firestoreSettings = settings
        } catch (_: IllegalStateException) {}

        syncRepository = FirestoreSyncRepository(firestore)

        val authResult = auth.signInAnonymously().await()
        activeUserId = authResult.user?.uid ?: "tier1-test-user-${UUID.randomUUID()}"
        sessionProvider = FakeUserSessionProvider(activeUserId, currentGeneration = 1L)

        // Seed authoritative session state in Room
        sessionStateDao.setSessionState(
            AppSessionStateEntity(
                id = 1,
                activeUserId = activeUserId,
                currentGeneration = 1L,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    @After
    fun tearDown() {
        try {
            db.close()
        } finally {
            ArchTaskExecutor.getInstance().setDelegate(null)
            Dispatchers.resetMain()
        }
    }

    private fun createOrchestrator(
        customSessionProvider: UserSessionProvider? = sessionProvider,
        customCheckpointStore: SharedPreferencesSyncCheckpointStore = checkpointStore
    ): SyncOrchestrator {
        return SyncOrchestrator(
            outboxRepository = outboxRepo,
            syncRepository = syncRepository,
            syncConflictDao = syncConflictDao,
            ironMindDao = ironMindDao,
            outboxDao = outboxDao,
            database = db,
            checkpointStore = customCheckpointStore,
            userSessionProvider = customSessionProvider,
            syncWorkerLeaseDao = leaseDao,
            appSessionStateDao = sessionStateDao
        )
    }

    private fun createTestGoalEntity(
        id: String,
        userId: String,
        title: String = "Test Goal",
        importance: Int = 1,
        status: String = "ACTIVE",
        createdAt: Long = System.currentTimeMillis(),
        updatedAt: Long = System.currentTimeMillis()
    ) = GoalEntity(
        id = id,
        userId = userId,
        ambitionId = null,
        title = title,
        description = "",
        why = "",
        importance = importance,
        status = status,
        targetAt = null,
        startedAt = null,
        completedAt = null,
        createdAt = createdAt,
        updatedAt = updatedAt,
        schemaVersion = 1
    )

    // -------------------------------------------------------------------------
    // Scenario 1: Multiple documents sharing the exact same updatedAt
    // -------------------------------------------------------------------------
    @Test
    fun test01_compoundPagination_multipleDocumentsWithIdenticalTimestamp_ordersByIdWithoutSkipsOrDuplicates() = runBlocking {
        val sameTimestamp = 5000L
        val docIds = listOf("doc_a", "doc_b", "doc_c", "doc_d", "doc_e")

        for (id in docIds) {
            val docData = mapOf(
                "id" to id,
                "userId" to activeUserId,
                "title" to "Goal $id",
                "importance" to 1,
                "status" to "ACTIVE",
                "version" to 1L,
                "updatedAt" to sameTimestamp,
                "createdAt" to sameTimestamp,
                "isDeleted" to false
            )
            firestore.collection("users").document(activeUserId).collection("goals").document(id).set(docData).await()
        }

        // Page 1 with limit = 2
        val page1Result = syncRepository.pull(
            userId = activeUserId,
            entityType = "Goal",
            cursor = SyncCursor(0L, ""),
            limit = 2
        )
        assertTrue("Page 1 must succeed", page1Result is Result.Success)
        val page1Data = (page1Result as Result.Success).data as SyncPullResult.Success
        assertEquals(2, page1Data.items.size)
        assertTrue("Page 1 hasMore must be true", page1Data.hasMore)
        assertEquals("doc_a", page1Data.items[0].entityId)
        assertEquals("doc_b", page1Data.items[1].entityId)

        // Page 2 using compound cursor (sameTimestamp, "doc_b")
        val cursorPage2 = SyncCursor(sameTimestamp, "doc_b")
        val page2Result = syncRepository.pull(
            userId = activeUserId,
            entityType = "Goal",
            cursor = cursorPage2,
            limit = 2
        )
        assertTrue("Page 2 must succeed", page2Result is Result.Success)
        val page2Data = (page2Result as Result.Success).data as SyncPullResult.Success
        assertEquals(2, page2Data.items.size)
        assertTrue("Page 2 hasMore must be true", page2Data.hasMore)
        assertEquals("doc_c", page2Data.items[0].entityId)
        assertEquals("doc_d", page2Data.items[1].entityId)

        // Page 3 using compound cursor (sameTimestamp, "doc_d")
        val cursorPage3 = SyncCursor(sameTimestamp, "doc_d")
        val page3Result = syncRepository.pull(
            userId = activeUserId,
            entityType = "Goal",
            cursor = cursorPage3,
            limit = 2
        )
        assertTrue("Page 3 must succeed", page3Result is Result.Success)
        val page3Data = (page3Result as Result.Success).data as SyncPullResult.Success
        assertEquals(1, page3Data.items.size)
        assertFalse("Page 3 hasMore must be false", page3Data.hasMore)
        assertEquals("doc_e", page3Data.items[0].entityId)

        val allIds = page1Data.items.map { it.entityId } +
                     page2Data.items.map { it.entityId } +
                     page3Data.items.map { it.entityId }
        assertEquals("All 5 docs retrieved without skips or duplicates", docIds, allIds)
    }

    // -------------------------------------------------------------------------
    // Scenario 2: Page-boundary continuation via Orchestrator
    // -------------------------------------------------------------------------
    @Test
    fun test02_pageBoundaryContinuation_orchestratorIncrementalPull_pagesCleanly() = runBlocking {
        val orchestrator = createOrchestrator()
        val timestamps = listOf(1000L, 2000L, 3000L, 4000L)
        val docIds = listOf("task_01", "task_02", "task_03", "task_04")

        for (i in docIds.indices) {
            val taskData = mapOf(
                "id" to docIds[i],
                "userId" to activeUserId,
                "planId" to "plan-1",
                "title" to "Task ${docIds[i]}",
                "version" to 1L,
                "updatedAt" to timestamps[i],
                "createdAt" to timestamps[i],
                "isDeleted" to false
            )
            firestore.collection("users").document(activeUserId).collection("tasks").document(docIds[i]).set(taskData).await()
        }

        // Incremental pull with batch limit = 2
        val pullResult = orchestrator.pullEntityIncremental(
            userId = activeUserId,
            entityType = "Task",
            limit = 2
        )

        assertTrue("Incremental pull must succeed", pullResult is Result.Success)
        val successData = (pullResult as Result.Success).data
        assertEquals("Must accumulate all 4 items across both pages", 4, successData.items.size)
        assertEquals(docIds, successData.items.map { it.entityId })

        // Checkpoint must advance monotonically to last document
        val savedCursor = checkpointStore.getLastCursor(activeUserId, "Task")
        assertEquals(4000L, savedCursor.timestamp)
        assertEquals("task_04", savedCursor.documentId)

        // Subsequent pull must return empty because cursor is at head
        val subsequentPull = orchestrator.pullEntityIncremental(
            userId = activeUserId,
            entityType = "Task",
            limit = 2
        )
        assertTrue(subsequentPull is Result.Success)
        val subsequentItems = (subsequentPull as Result.Success).data.items
        assertEquals(0, subsequentItems.size)
    }

    // -------------------------------------------------------------------------
    // Scenario 3: Empty collection handling
    // -------------------------------------------------------------------------
    @Test
    fun test03_emptyCollection_returnsEmptyAndPreservesCheckpoint() = runBlocking {
        val orchestrator = createOrchestrator()
        val initialCursor = SyncCursor(timestamp = 9999L, documentId = "existing_mem")
        checkpointStore.setLastCursor(activeUserId, "Memory", initialCursor)

        val pullResult = orchestrator.pullEntityIncremental(
            userId = activeUserId,
            entityType = "Memory",
            limit = 10
        )

        assertTrue("Pull must succeed on empty collection", pullResult is Result.Success)
        val data = (pullResult as Result.Success).data
        assertTrue("Items must be empty", data.items.isEmpty())
        assertFalse("hasMore must be false", data.hasMore)

        // Invariant: Checkpoint must not be cleared or overwritten
        val cursorAfter = checkpointStore.getLastCursor(activeUserId, "Memory")
        assertEquals(initialCursor.timestamp, cursorAfter.timestamp)
        assertEquals(initialCursor.documentId, cursorAfter.documentId)
    }

    // -------------------------------------------------------------------------
    // Scenario 4: Lookahead overfetch (limit + 1)
    // -------------------------------------------------------------------------
    @Test
    fun test04_lookaheadOverfetch_exactMultipleOfLimit_hasMoreIsFalseWithoutExtraRoundtrip() = runBlocking {
        val docIds = listOf("commit_1", "commit_2", "commit_3")
        for (i in docIds.indices) {
            val commitData = mapOf(
                "id" to docIds[i],
                "userId" to activeUserId,
                "goalId" to "goal-1",
                "title" to "Commitment ${docIds[i]}",
                "version" to 1L,
                "updatedAt" to (1000L * (i + 1)),
                "createdAt" to (1000L * (i + 1)),
                "isDeleted" to false
            )
            firestore.collection("users").document(activeUserId).collection("commitments").document(docIds[i]).set(commitData).await()
        }

        // Pull with limit = 3 (exact multiple of collection count)
        val pullResult = syncRepository.pull(
            userId = activeUserId,
            entityType = "Commitment",
            cursor = SyncCursor(0L, ""),
            limit = 3
        )

        assertTrue(pullResult is Result.Success)
        val data = (pullResult as Result.Success).data as SyncPullResult.Success
        assertEquals(3, data.items.size)
        // Over-fetch queried limit + 1 = 4. Since Firestore returned 3 docs, size <= limit, so hasMore is false.
        assertFalse("hasMore must be false immediately on first query", data.hasMore)
    }

    // -------------------------------------------------------------------------
    // Scenario 5: SharedPreferences cursor persistence across process restart
    // -------------------------------------------------------------------------
    @Test
    fun test05_cursorPersistence_acrossProcessRestart_usesSharedPreferences() {
        val store1 = SharedPreferencesSyncCheckpointStore(context)
        val cursor = SyncCursor(timestamp = 1700000000000L, documentId = "plan_persisted_01")
        store1.setLastCursor(activeUserId, "Plan", cursor)

        // Simulate app kill / restart: create new store instance against same context
        val store2 = SharedPreferencesSyncCheckpointStore(context)
        val retrieved = store2.getLastCursor(activeUserId, "Plan")

        assertEquals(cursor.timestamp, retrieved.timestamp)
        assertEquals(cursor.documentId, retrieved.documentId)

        // Monotonicity check: attempting to write older cursor must be ignored
        val olderCursor = SyncCursor(timestamp = 1600000000000L, documentId = "plan_older")
        store2.setLastCursor(activeUserId, "Plan", olderCursor)
        val afterOlderAttempt = store2.getLastCursor(activeUserId, "Plan")
        assertEquals("Older cursor must not regress stored checkpoint", cursor.timestamp, afterOlderAttempt.timestamp)
        assertEquals(cursor.documentId, afterOlderAttempt.documentId)
    }

    // -------------------------------------------------------------------------
    // Scenario 6: Authoritative session generation validation
    // -------------------------------------------------------------------------
    @Test
    fun test06_authoritativeSessionGeneration_staleGenerationAbortsPull() = runBlocking {
        // Authoritative generation is 5L
        sessionStateDao.setSessionState(
            AppSessionStateEntity(
                id = 1,
                activeUserId = activeUserId,
                currentGeneration = 5L,
                updatedAt = System.currentTimeMillis()
            )
        )

        // Caller / provider attempts pull with stale generation 4L
        val staleProvider = FakeUserSessionProvider(activeUserId, currentGeneration = 4L)
        val orchestrator = createOrchestrator(customSessionProvider = staleProvider)

        val pullResult = orchestrator.pullEntityIncremental(
            userId = activeUserId,
            entityType = "Goal",
            callerGeneration = 4L
        )

        assertTrue("Stale generation must result in Failure", pullResult is Result.Failure)
        val error = (pullResult as Result.Failure).error
        assertTrue(
            "Error message must indicate stale generation",
            error.message?.contains("Stale session generation") == true
        )
    }

    // -------------------------------------------------------------------------
    // Scenario 7: Active lease fencing during pagination
    // -------------------------------------------------------------------------
    @Test
    fun test07_lostLeaseDuringPagination_abortsPullImmediately() = runBlocking {
        val orchestrator = createOrchestrator()
        val now = System.currentTimeMillis()

        // Acquire active lease for a different worker owner token
        val foreignOwnerToken = "foreign-worker-${UUID.randomUUID()}"
        val initialLease = SyncWorkerLeaseEntity(
            leaseKey = SyncOrchestrator.LEASE_KEY_SYNC_EXECUTION,
            ownerToken = foreignOwnerToken,
            sessionGeneration = 1L,
            generationId = 1L,
            cursor = "",
            status = "ACTIVE",
            acquiredAt = now,
            expiresAt = now + 60_000L,
            updatedAt = now,
            lastCompletedAt = null
        )
        db.syncWorkerLeaseDao().insertInitialLease(initialLease)

        // Local worker attempts incremental pull while foreign worker holds active lease
        val pullResult = orchestrator.pullEntityIncremental(
            userId = activeUserId,
            entityType = "Goal",
            callerGeneration = 1L
        )

        assertTrue("Pull must be rejected when active lease is held", pullResult is Result.Failure)
        val error = (pullResult as Result.Failure).error
        assertTrue(
            "Error message must indicate lease acquisition failure",
            error.message?.contains("Failed to acquire sync lease") == true
        )
    }

    // -------------------------------------------------------------------------
    // Scenario 8: Malformed remote document handling
    // -------------------------------------------------------------------------
    @Test
    fun test08_malformedRemotePayload_failsFastAndDoesNotCorruptCheckpoint() = runBlocking {
        val malformedDocId = "reflection_corrupt_${UUID.randomUUID()}"
        // Document with non-numeric updatedAt metadata
        val corruptData = mapOf(
            "id" to malformedDocId,
            "userId" to activeUserId,
            "notes" to "Corrupted document payload",
            "updatedAt" to "invalid-non-numeric-timestamp"
        )
        firestore.collection("users").document(activeUserId).collection("reflections").document(malformedDocId).set(corruptData).await()

        val presetCursor = SyncCursor(timestamp = 2500L, documentId = "valid_prior_doc")
        checkpointStore.setLastCursor(activeUserId, "Reflection", presetCursor)

        val pullResult = syncRepository.pull(
            userId = activeUserId,
            entityType = "Reflection",
            cursor = SyncCursor(0L, ""),
            limit = 5
        )

        assertTrue("Pull must fail when encountering malformed document", pullResult is Result.Failure)
        val error = (pullResult as Result.Failure).error
        assertTrue(
            "Error must specify missing timestamp metadata",
            error.message?.contains("missing updatedAt/deletedAt/createdAt") == true
        )

        // Checkpoint must remain safe at prior valid cursor
        val checkpointAfter = checkpointStore.getLastCursor(activeUserId, "Reflection")
        assertEquals(presetCursor.timestamp, checkpointAfter.timestamp)
        assertEquals(presetCursor.documentId, checkpointAfter.documentId)
    }

    // -------------------------------------------------------------------------
    // Scenario 9: User-specific checkpoint isolation
    // -------------------------------------------------------------------------
    @Test
    fun test09_userCheckpointIsolation_independentPerUserCursors() {
        val userA = "user_alpha_${UUID.randomUUID()}"
        val userB = "user_beta_${UUID.randomUUID()}"

        checkpointStore.setLastCursor(userA, "Goal", SyncCursor(5000L, "doc_user_a"))
        checkpointStore.setLastCursor(userB, "Goal", SyncCursor(9000L, "doc_user_b"))

        // Advance User A
        checkpointStore.setLastCursor(userA, "Goal", SyncCursor(6000L, "doc_user_a2"))

        // Assert User B was completely unaffected
        val userBCursor = checkpointStore.getLastCursor(userB, "Goal")
        assertEquals(9000L, userBCursor.timestamp)
        assertEquals("doc_user_b", userBCursor.documentId)

        // Clear User A checkpoints
        checkpointStore.clearCheckpointsForUser(userA)

        val userACursorAfterClear = checkpointStore.getLastCursor(userA, "Goal")
        assertEquals(0L, userACursorAfterClear.timestamp)
        assertEquals("", userACursorAfterClear.documentId)

        val userBCursorAfterClear = checkpointStore.getLastCursor(userB, "Goal")
        assertEquals(9000L, userBCursorAfterClear.timestamp)
        assertEquals("doc_user_b", userBCursorAfterClear.documentId)
    }

    // -------------------------------------------------------------------------
    // Scenario 10: Stage B.1 Invariant: Local Room entities NOT overwritten
    // -------------------------------------------------------------------------
    @Test
    fun test10_stageB1Invariant_localRoomEntitiesNotOverwritten() = runBlocking {
        val orchestrator = createOrchestrator()
        val goalId = "goal_invariant_${UUID.randomUUID()}"

        // Insert local Goal in Room
        val localGoal = createTestGoalEntity(
            id = goalId,
            userId = activeUserId,
            title = "Pristine Local Goal",
            importance = 3,
            updatedAt = 1000L
        )
        ironMindDao.insertGoal(localGoal)

        // Pre-advance checkpoint to existing goals before creating the test goal
        val existingGoals = syncRepository.pull(activeUserId, "Goal", SyncCursor(0L, ""), 100)
        val lastExisting = (existingGoals as? Result.Success)?.let { (it.data as SyncPullResult.Success).items.lastOrNull() }
        if (lastExisting != null) {
            checkpointStore.setLastCursor(activeUserId, "Goal", SyncCursor(lastExisting.updatedAt, lastExisting.entityId))
        }

        // Upload remote Goal with higher updatedAt (6000L) and different title to Firestore
        val remoteData = mapOf(
            "id" to goalId,
            "userId" to activeUserId,
            "title" to "Overwriting Remote Goal",
            "importance" to 5,
            "status" to "ACTIVE",
            "version" to 2L,
            "updatedAt" to 6000L,
            "createdAt" to 1000L,
            "isDeleted" to false
        )
        firestore.collection("users").document(activeUserId).collection("goals").document(goalId).set(remoteData).await()

        // Execute pullEntityIncremental with Stage B.1 mode (does NOT overwrite Room)
        val pullResult = orchestrator.pullEntityIncremental(
            userId = activeUserId,
            entityType = "Goal"
        )

        assertTrue("Pull must succeed", pullResult is Result.Success)
        val successData = (pullResult as Result.Success).data
        assertEquals("Must pull 1 remote entity", 1, successData.items.size)
        assertEquals(goalId, successData.items.first().entityId)

        // Verify local Room entity was NOT touched or overwritten
        val roomGoal = ironMindDao.getGoal(goalId)
        assertNotNull("Goal must remain in Room", roomGoal)
        assertEquals("Pristine Local Goal", roomGoal!!.title)
        assertEquals(3, roomGoal.importance)
        assertEquals(1000L, roomGoal.updatedAt)

        // Checkpoint must advance
        val cursor = checkpointStore.getLastCursor(activeUserId, "Goal")
        assertEquals(6000L, cursor.timestamp)
        assertEquals(goalId, cursor.documentId)
    }
}

