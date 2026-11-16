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
import com.sanket_satpute_20.ironmind.data.local.entity.OutboxEntity
import com.sanket_satpute_20.ironmind.data.repository.FirestoreSyncRepository
import com.sanket_satpute_20.ironmind.data.repository.InMemorySyncCheckpointStore
import com.sanket_satpute_20.ironmind.data.repository.OutboxRepositoryImpl
import com.sanket_satpute_20.ironmind.data.repository.SharedPreferencesSyncCheckpointStore
import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.OutboxEntry
import com.sanket_satpute_20.ironmind.domain.model.OutboxOperationType
import com.sanket_satpute_20.ironmind.domain.model.SyncStatus
import com.sanket_satpute_20.ironmind.domain.repository.SyncPullResult
import com.sanket_satpute_20.ironmind.domain.session.SessionState
import com.sanket_satpute_20.ironmind.domain.session.UserSessionProvider
import java.util.UUID
import java.util.concurrent.Executor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import org.robolectric.annotation.Config

/**
 * Phase 8.3.3.5 — Genuine Android Firebase SDK Downstream Remote-to-Local Integration Tests.
 *
 * Validates bidirectional synchronization (Device A -> Firestore Emulator -> Device B):
 * - Incremental pull across all 6 entity types (Goal, Plan, Task, Commitment, Reflection, Memory)
 * - Atomic Room persistence
 * - Tombstone-based remote deletion propagation
 * - Monotonic checkpoint advancement
 * - Concurrency divergence quarantine and durable conflict logging
 * - Active user session-generation fencing
 */
@RunWith(Jdk25CompatibleRobolectricTestRunner::class)
@Config(sdk = [34])
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class FirestoreDownstreamSyncSdkTest {

    private lateinit var context: Context
    private lateinit var app: FirebaseApp
    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore
    private lateinit var syncRepository: FirestoreSyncRepository

    private lateinit var db: IronMindDatabase
    private lateinit var outboxDao: OutboxDao
    private lateinit var syncConflictDao: SyncConflictDao
    private lateinit var ironMindDao: IronMindDao
    private lateinit var outboxRepo: OutboxRepositoryImpl
    private lateinit var checkpointStore: InMemorySyncCheckpointStore

    private var activeUserId: String = ""

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

    private fun createTestOutboxEntity(
        operationId: String,
        userId: String,
        entityType: String,
        entityId: String,
        operationType: String = "UPSERT",
        payload: String,
        createdAt: Long = System.currentTimeMillis(),
        status: String = "PENDING",
        baseVersion: Long? = null
    ) = OutboxEntity(
        operationId = operationId,
        userId = userId,
        entityType = entityType,
        entityId = entityId,
        operationType = operationType,
        payload = payload,
        createdAt = createdAt,
        retryCount = 0,
        lastAttemptAt = null,
        status = status,
        baseVersion = baseVersion
    )

    private fun createTestOutboxEntry(
        operationId: String,
        userId: String,
        entityType: String,
        entityId: String,
        operationType: OutboxOperationType = OutboxOperationType.UPSERT,
        payload: String,
        createdAt: Long = System.currentTimeMillis(),
        baseVersion: Long? = null
    ) = OutboxEntry(
        operationId = operationId,
        userId = userId,
        entityType = entityType,
        entityId = entityId,
        operationType = operationType,
        payload = payload,
        createdAt = createdAt,
        retryCount = 0,
        lastAttemptAt = null,
        status = SyncStatus.PENDING,
        baseVersion = baseVersion
    )

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

    private lateinit var sessionProvider: FakeUserSessionProvider
    private lateinit var leaseDao: SyncWorkerLeaseDao
    private lateinit var sessionStateDao: AppSessionStateDao

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
        outboxRepo = OutboxRepositoryImpl(outboxDao)
        checkpointStore = InMemorySyncCheckpointStore()

        val options = FirebaseOptions.Builder()
            .setApplicationId("com.sanket_satpute_20.ironmind")
            .setProjectId("demo-ironmind")
            .setApiKey("fake-api-key")
            .build()

        app = if (FirebaseApp.getApps(context).none { it.name == "downstream-sync-sdk-app" }) {
            FirebaseApp.initializeApp(context, options, "downstream-sync-sdk-app")
        } else {
            FirebaseApp.getInstance("downstream-sync-sdk-app")
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

        val currentUser = auth.currentUser
        if (currentUser == null) {
            val authResult = auth.signInAnonymously().await()
            activeUserId = authResult.user?.uid ?: "downstream-test-user-${UUID.randomUUID()}"
        } else {
            activeUserId = currentUser.uid
        }
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

        // Clean subcollections for activeUserId to ensure cross-test isolation
        clearUserSubcollections(activeUserId)
    }

    @After
    fun tearDown() = runBlocking {
        try {
            clearUserSubcollections(activeUserId)
        } catch (_: Throwable) {}
        try {
            db.close()
        } finally {
            ArchTaskExecutor.getInstance().setDelegate(null)
        }
    }

    private suspend fun clearUserSubcollections(userId: String) {
        val subcollections = listOf("goals", "plans", "tasks", "commitments", "reflections", "memories")
        var batch = firestore.batch()
        var count = 0
        for (sub in subcollections) {
            val snapshot = firestore.collection("users").document(userId).collection(sub).get().await()
            for (doc in snapshot.documents) {
                batch.delete(doc.reference)
                count++
                if (count % 400 == 0) {
                    batch.commit().await()
                    batch = firestore.batch()
                }
            }
        }
        if (count > 0 && count % 400 != 0) {
            batch.commit().await()
        }
    }

    private fun createOrchestrator(customSessionProvider: UserSessionProvider? = sessionProvider): SyncOrchestrator {
        return SyncOrchestrator(
            outboxRepository = outboxRepo,
            syncRepository = syncRepository,
            syncConflictDao = syncConflictDao,
            ironMindDao = ironMindDao,
            outboxDao = outboxDao,
            database = db,
            checkpointStore = checkpointStore,
            userSessionProvider = customSessionProvider,
            syncWorkerLeaseDao = leaseDao,
            appSessionStateDao = sessionStateDao
        )
    }

    // -------------------------------------------------------------------------
    // Scenario 1: Downstream pull for Goal entity
    // -------------------------------------------------------------------------
    @Test
    fun test01_downstreamPull_goal_persistsToRoomDatabase() = runBlocking {
        val orchestrator = createOrchestrator()
        val now = System.currentTimeMillis()
        val goalId = "goal-downstream-${UUID.randomUUID()}"

        // Simulate Client A uploading Goal to Firestore
        val goalData = mapOf(
            "id" to goalId,
            "userId" to activeUserId,
            "title" to "Master Distributed Sync",
            "description" to "Clean bidirectional pipeline",
            "importance" to 5,
            "status" to "ACTIVE",
            "version" to 1L,
            "updatedAt" to now,
            "createdAt" to now,
            "isDeleted" to false
        )
        firestore.collection("users").document(activeUserId).collection("goals").document(goalId).set(goalData).await()

        // Verify Client B has no Goal in local Room
        assertNull(ironMindDao.getGoal(goalId))

        // Execute pull
        val pullResult = orchestrator.pullRemoteChanges(activeUserId)
        assertTrue("Pull must succeed", pullResult is Result.Success)
        assertEquals(1, (pullResult as Result.Success).data)

        // Verify Goal exists in Room
        val localGoal = ironMindDao.getGoal(goalId)
        assertNotNull("Goal must be persisted in Room", localGoal)
        assertEquals("Master Distributed Sync", localGoal!!.title)
        assertEquals(activeUserId, localGoal.userId)

        // Verify checkpoint advanced
        val checkpoint = checkpointStore.getLastCheckpoint(activeUserId, "Goal")
        assertEquals(now, checkpoint)
    }

    // -------------------------------------------------------------------------
    // Scenario 2: Downstream pull for Plan and Task entities
    // -------------------------------------------------------------------------
    @Test
    fun test02_downstreamPull_planAndTask_persistsToRoomDatabase() = runBlocking {
        val orchestrator = createOrchestrator()
        val now = System.currentTimeMillis()
        val planId = "plan-downstream-${UUID.randomUUID()}"
        val taskId = "task-downstream-${UUID.randomUUID()}"

        val planData = mapOf(
            "id" to planId,
            "userId" to activeUserId,
            "goalId" to "goal-parent-1",
            "title" to "Execution Plan Alpha",
            "version" to 1L,
            "updatedAt" to now,
            "createdAt" to now,
            "isDeleted" to false
        )
        val taskData = mapOf(
            "id" to taskId,
            "userId" to activeUserId,
            "planId" to planId,
            "title" to "Complete Task Beta",
            "version" to 1L,
            "updatedAt" to now,
            "createdAt" to now,
            "isDeleted" to false
        )
        firestore.collection("users").document(activeUserId).collection("plans").document(planId).set(planData).await()
        firestore.collection("users").document(activeUserId).collection("tasks").document(taskId).set(taskData).await()

        val pullResult = orchestrator.pullRemoteChanges(activeUserId)
        assertTrue(pullResult is Result.Success)
        assertTrue((pullResult as Result.Success).data >= 2)

        val localPlan = ironMindDao.getPlan(planId)
        val localTask = ironMindDao.getTask(taskId)
        assertNotNull("Plan must exist in Room", localPlan)
        assertNotNull("Task must exist in Room", localTask)
        assertEquals("Execution Plan Alpha", localPlan!!.title)
        assertEquals("Complete Task Beta", localTask!!.title)
    }

    // -------------------------------------------------------------------------
    // Scenario 3: Downstream pull for Commitment and Reflection entities
    // -------------------------------------------------------------------------
    @Test
    fun test03_downstreamPull_commitmentAndReflection_persistsToRoomDatabase() = runBlocking {
        val orchestrator = createOrchestrator()
        val now = System.currentTimeMillis()
        val commitId = "commit-downstream-${UUID.randomUUID()}"
        val reflId = "refl-downstream-${UUID.randomUUID()}"

        val commitData = mapOf(
            "id" to commitId,
            "userId" to activeUserId,
            "title" to "Daily Deep Work",
            "category" to "HEALTH",
            "targetFrequency" to "DAILY",
            "status" to "ACTIVE",
            "version" to 1L,
            "updatedAt" to now,
            "createdAt" to now,
            "isDeleted" to false
        )
        val reflData = mapOf(
            "id" to reflId,
            "userId" to activeUserId,
            "content" to "Achieved all planned targets today.",
            "sentiment" to "POSITIVE",
            "version" to 1L,
            "updatedAt" to now,
            "createdAt" to now,
            "isDeleted" to false
        )
        firestore.collection("users").document(activeUserId).collection("commitments").document(commitId).set(commitData).await()
        firestore.collection("users").document(activeUserId).collection("reflections").document(reflId).set(reflData).await()

        val pullResult = orchestrator.pullRemoteChanges(activeUserId)
        assertTrue(pullResult is Result.Success)

        val localCommit = ironMindDao.getCommitment(commitId)
        val localRefl = ironMindDao.getReflection(reflId)
        assertNotNull("Commitment must exist in Room", localCommit)
        assertNotNull("Reflection must exist in Room", localRefl)
        assertEquals("Daily Deep Work", localCommit!!.title)
        assertEquals("Achieved all planned targets today.", localRefl!!.content)
    }

    // -------------------------------------------------------------------------
    // Scenario 4: Downstream pull for Memory entity
    // -------------------------------------------------------------------------
    @Test
    fun test04_downstreamPull_memory_persistsToRoomDatabase() = runBlocking {
        val orchestrator = createOrchestrator()
        val now = System.currentTimeMillis()
        val memoryId = "mem-downstream-${UUID.randomUUID()}"

        val memData = mapOf(
            "id" to memoryId,
            "userId" to activeUserId,
            "type" to "PREFERENCE",
            "content" to "Prefers early morning deep work sessions",
            "source" to "USER",
            "version" to 1L,
            "updatedAt" to now,
            "createdAt" to now,
            "isDeleted" to false
        )
        firestore.collection("users").document(activeUserId).collection("memories").document(memoryId).set(memData).await()

        val pullResult = orchestrator.pullRemoteChanges(activeUserId)
        assertTrue(pullResult is Result.Success)

        val localMemory = ironMindDao.getMemoryById(memoryId)
        assertNotNull("Memory must exist in Room", localMemory)
        assertEquals("Prefers early morning deep work sessions", localMemory!!.content)
    }

    // -------------------------------------------------------------------------
    // Scenario 5: Tombstone deletion propagation
    // -------------------------------------------------------------------------
    @Test
    fun test05_downstreamPull_tombstoneDeletion_removesFromRoomDatabase() = runBlocking {
        val orchestrator = createOrchestrator()
        val now = System.currentTimeMillis()
        val goalId = "goal-to-delete-${UUID.randomUUID()}"

        // Seed Room with existing Goal
        ironMindDao.insertGoal(
            createTestGoalEntity(
                id = goalId,
                userId = activeUserId,
                title = "To Be Deleted",
                importance = 1,
                status = "ACTIVE",
                createdAt = now - 5000L,
                updatedAt = now - 5000L
            )
        )
        assertNotNull(ironMindDao.getGoal(goalId))

        // Remote tombstone
        val tombstone = mapOf(
            "id" to goalId,
            "userId" to activeUserId,
            "isDeleted" to true,
            "deletedAt" to now,
            "updatedAt" to now,
            "version" to 2L
        )
        firestore.collection("users").document(activeUserId).collection("goals").document(goalId).set(tombstone).await()

        val pullResult = orchestrator.pullRemoteChanges(activeUserId)
        assertTrue(pullResult is Result.Success)

        // Verify Goal is deleted from Room
        assertNull("Goal must be deleted from Room following remote tombstone", ironMindDao.getGoal(goalId))
    }

    // -------------------------------------------------------------------------
    // Scenario 6: Idempotency & Checkpoint skipping
    // -------------------------------------------------------------------------
    @Test
    fun test06_downstreamPull_idempotentRepeat_checkpointPreventsDuplicateFetch() = runBlocking {
        val orchestrator = createOrchestrator()
        val now = System.currentTimeMillis()
        val goalId = "goal-idempotent-${UUID.randomUUID()}"

        val goalData = mapOf(
            "id" to goalId,
            "userId" to activeUserId,
            "title" to "Idempotent Test",
            "importance" to 3,
            "status" to "ACTIVE",
            "version" to 1L,
            "updatedAt" to now,
            "createdAt" to now,
            "isDeleted" to false
        )
        firestore.collection("users").document(activeUserId).collection("goals").document(goalId).set(goalData).await()

        val firstPull = orchestrator.pullRemoteChanges(activeUserId)
        assertTrue("First pull must pull at least 1 item", (firstPull as Result.Success).data >= 1)
        val initialCheckpoint = checkpointStore.getLastCheckpoint(activeUserId, "Goal")
        assertTrue("Checkpoint must be set to at least now", initialCheckpoint >= now)

        // Repeat pull without new changes
        val secondPull = orchestrator.pullRemoteChanges(activeUserId)
        assertEquals(0, (secondPull as Result.Success).data)
        assertEquals(initialCheckpoint, checkpointStore.getLastCheckpoint(activeUserId, "Goal"))
    }

    // -------------------------------------------------------------------------
    // Scenario 7: Divergence conflict quarantine & local Room preservation
    // -------------------------------------------------------------------------
    @Test
    fun test07_downstreamPull_divergenceDetection_quarantinesToConflictAndPreservesLocalRoom() = runBlocking {
        val orchestrator = createOrchestrator()
        val now = System.currentTimeMillis()
        val goalId = "goal-divergence-${UUID.randomUUID()}"

        // Client B has local offline edit
        val localGoal = createTestGoalEntity(
            id = goalId,
            userId = activeUserId,
            title = "Local Offline Title",
            importance = 3,
            status = "ACTIVE",
            createdAt = now - 1000L,
            updatedAt = now
        )
        ironMindDao.insertGoal(localGoal)

        // Client B enqueued outbox mutation based on version 1L
        val localOutbox = createTestOutboxEntity(
            operationId = "op-diverge-1",
            userId = activeUserId,
            entityType = "Goal",
            entityId = goalId,
            operationType = "UPSERT",
            payload = """{"id":"$goalId","userId":"$activeUserId","title":"Local Offline Title"}""",
            createdAt = now,
            baseVersion = 1L,
            status = "PENDING"
        )
        outboxDao.insertEntry(localOutbox)

        // Client A uploaded remote version 2L with different title
        val remoteData = mapOf(
            "id" to goalId,
            "userId" to activeUserId,
            "title" to "Remote Concurrent Title",
            "importance" to 5,
            "status" to "ACTIVE",
            "version" to 2L,
            "updatedAt" to now + 500L,
            "createdAt" to now,
            "isDeleted" to false
        )
        firestore.collection("users").document(activeUserId).collection("goals").document(goalId).set(remoteData).await()

        // Execute pull
        val pullResult = orchestrator.pullRemoteChanges(activeUserId)
        assertTrue(pullResult is Result.Success)

        // 1. Outbox entry must be quarantined to CONFLICT
        val updatedOutbox = outboxDao.getEntryByOperationIdForUser("op-diverge-1", activeUserId)
        assertNotNull(updatedOutbox)
        assertEquals("CONFLICT", updatedOutbox!!.status)

        // 2. Durable conflict must be recorded in sync_conflicts table
        val conflicts = syncConflictDao.getConflictsForUser(activeUserId)
        assertTrue("Conflict must be stored in sync_conflicts", conflicts.any { it.operationId == "op-diverge-1" })

        // 3. Local Room state must be preserved (not overwritten by remote)
        val roomGoal = ironMindDao.getGoal(goalId)
        assertNotNull(roomGoal)
        assertEquals("Local Offline Title", roomGoal!!.title)
    }

    // -------------------------------------------------------------------------
    // Scenario 8: Local edit newer than remote skips overwrite
    // -------------------------------------------------------------------------
    @Test
    fun test08_downstreamPull_localEditNewerThanRemote_skipsOverwriting() = runBlocking {
        val orchestrator = createOrchestrator()
        val now = System.currentTimeMillis()
        val goalId = "goal-newer-${UUID.randomUUID()}"

        ironMindDao.insertGoal(
            createTestGoalEntity(
                id = goalId,
                userId = activeUserId,
                title = "Local Newer Edit",
                importance = 5,
                status = "ACTIVE",
                createdAt = now - 1000L,
                updatedAt = now
            )
        )

        // Local outbox already based on version 3L
        val localOutbox = createTestOutboxEntity(
            operationId = "op-newer-1",
            userId = activeUserId,
            entityType = "Goal",
            entityId = goalId,
            operationType = "UPSERT",
            payload = """{"id":"$goalId","userId":"$activeUserId","title":"Local Newer Edit"}""",
            createdAt = now,
            baseVersion = 3L,
            status = "PENDING"
        )
        outboxDao.insertEntry(localOutbox)

        // Remote only has version 2L
        val remoteData = mapOf(
            "id" to goalId,
            "userId" to activeUserId,
            "title" to "Old Remote Stale Version",
            "version" to 2L,
            "updatedAt" to now - 500L,
            "createdAt" to now - 2000L,
            "isDeleted" to false
        )
        firestore.collection("users").document(activeUserId).collection("goals").document(goalId).set(remoteData).await()

        val pullResult = orchestrator.pullRemoteChanges(activeUserId)
        assertTrue(pullResult is Result.Success)

        // Outbox must remain PENDING for upcoming push
        val outbox = outboxDao.getEntryByOperationIdForUser("op-newer-1", activeUserId)
        assertEquals("PENDING", outbox!!.status)
        assertEquals("Local Newer Edit", ironMindDao.getGoal(goalId)!!.title)
    }

    // -------------------------------------------------------------------------
    // Scenario 9: User session switch mid-pull aborts write
    // -------------------------------------------------------------------------
    @Test
    fun test09_downstreamPull_sessionChange_abortsPull() = runBlocking {
        // Change session provider to different user ID
        sessionProvider.setUserId("different-user-999")
        val orchestrator = createOrchestrator(sessionProvider)

        val pullResult = orchestrator.pullRemoteChanges(activeUserId)
        assertTrue("Pull must fail when user session does not match", pullResult is Result.Failure)
        assertTrue((pullResult as Result.Failure).error is IllegalStateException)
    }

    // -------------------------------------------------------------------------
    // Scenario 10: Cross-user pull blocked
    // -------------------------------------------------------------------------
    @Test
    fun test10_downstreamPull_strictUserScoping() = runBlocking {
        val orchestrator = createOrchestrator()
        val otherUserId = "other-user-${UUID.randomUUID()}"

        // Attempting to pull for a different userId than the authenticated user
        val pullResult = syncRepository.pull(
            userId = otherUserId,
            entityType = "Goal",
            sinceTimestamp = 0L,
            limit = 10
        )
        // With emulator rules, querying other user's subcollection results in PERMISSION_DENIED
        assertTrue("Cross user pull must fail with permission denied", pullResult is Result.Failure)
    }

    // -------------------------------------------------------------------------
    // Scenario 11: CheckpointStore persistence & monotonicity
    // -------------------------------------------------------------------------
    @Test
    fun test11_checkpointStore_inMemoryAndSharedPreferences() {
        val memStore = InMemorySyncCheckpointStore()
        assertEquals(0L, memStore.getLastCheckpoint("user1", "Goal"))

        memStore.setLastCheckpoint("user1", "Goal", 1000L)
        assertEquals(1000L, memStore.getLastCheckpoint("user1", "Goal"))

        // Smaller timestamp should not regress
        memStore.setLastCheckpoint("user1", "Goal", 500L)
        assertEquals(1000L, memStore.getLastCheckpoint("user1", "Goal"))

        // Clear checkpoints for user
        memStore.clearCheckpointsForUser("user1")
        assertEquals(0L, memStore.getLastCheckpoint("user1", "Goal"))

        // SharedPreferences store
        val spStore = SharedPreferencesSyncCheckpointStore(context)
        spStore.clearCheckpointsForUser("user2")
        assertEquals(0L, spStore.getLastCheckpoint("user2", "Goal"))
        spStore.setLastCheckpoint("user2", "Goal", 2000L)
        assertEquals(2000L, spStore.getLastCheckpoint("user2", "Goal"))
        // Smaller timestamp should not regress SharedPreferences
        spStore.setLastCheckpoint("user2", "Goal", 1500L)
        assertEquals(2000L, spStore.getLastCheckpoint("user2", "Goal"))
        spStore.clearCheckpointsForUser("user2")
        assertEquals(0L, spStore.getLastCheckpoint("user2", "Goal"))
    }

    // -------------------------------------------------------------------------
    // Scenario 12: End-to-End Bidirectional Sync Cycle
    // -------------------------------------------------------------------------
    @Test
    fun test12_endToEnd_bidirectionalSyncCycle() = runBlocking {
        val orchestrator = createOrchestrator()
        val now = System.currentTimeMillis()
        val goalId = "goal-e2e-${UUID.randomUUID()}"
        val planId = "plan-e2e-${UUID.randomUUID()}"

        // Step 1: Client A pushes Goal to remote Firestore
        val clientAUpsert = createTestOutboxEntry(
            operationId = "op-e2e-1",
            userId = activeUserId,
            entityType = "Goal",
            entityId = goalId,
            operationType = OutboxOperationType.UPSERT,
            payload = """{"id":"$goalId","userId":"$activeUserId","title":"E2E Goal Alpha","importance":4,"status":"ACTIVE","createdAt":$now,"updatedAt":$now}""",
            createdAt = now,
            baseVersion = 0L
        )
        val uploadResult = syncRepository.upload(activeUserId, clientAUpsert)
        assertTrue(uploadResult is Result.Success)

        // Step 2: Client B pulls remote changes & pushes local Plan
        // Client B creates local Plan in outbox
        val clientBPlanOutbox = createTestOutboxEntity(
            operationId = "op-e2e-2",
            userId = activeUserId,
            entityType = "Plan",
            entityId = planId,
            operationType = "UPSERT",
            payload = """{"id":"$planId","userId":"$activeUserId","goalId":"$goalId","title":"E2E Plan Beta","createdAt":$now,"updatedAt":$now}""",
            createdAt = now,
            baseVersion = 0L,
            status = "PENDING"
        )
        outboxDao.insertEntry(clientBPlanOutbox)

        // Client B executes full sync (pull Goal -> upload Plan)
        val syncResult = orchestrator.sync(activeUserId)
        assertTrue(syncResult is Result.Success)

        // Verify Client B received Goal in Room
        val pulledGoal = ironMindDao.getGoal(goalId)
        assertNotNull("Client B Room must contain Goal from Client A", pulledGoal)
        assertEquals("E2E Goal Alpha", pulledGoal!!.title)

        // Verify Client B's Plan was uploaded to Firestore
        val remotePlanSnap = firestore.collection("users").document(activeUserId).collection("plans").document(planId).get().await()
        assertTrue("Remote Plan must exist in Firestore", remotePlanSnap.exists())
        assertEquals("E2E Plan Beta", remotePlanSnap.getString("title"))
    }

    // -------------------------------------------------------------------------
    // Phase 8.3.3.6A Part B Hardened Pagination & Consistency Tests
    // -------------------------------------------------------------------------

    @Test
    fun test13_pagination_55DocumentsSharingSameTimestampWithPageLimit50_allPulled() = runBlocking {
        val orchestrator = createOrchestrator()
        val sharedTimestamp = 5_000_000L
        val totalDocs = 55

        // Seed 55 Goal documents sharing the EXACT SAME timestamp in Firestore
        for (i in 0 until totalDocs) {
            val goalId = "goal-same-ts-${String.format("%02d", i)}"
            val goalData = mapOf(
                "id" to goalId,
                "userId" to activeUserId,
                "title" to "Same Timestamp Goal #$i",
                "importance" to 1,
                "status" to "ACTIVE",
                "version" to 1L,
                "createdAt" to sharedTimestamp,
                "updatedAt" to sharedTimestamp,
                "isDeleted" to false
            )
            firestore.collection("users")
                .document(activeUserId)
                .collection("goals")
                .document(goalId)
                .set(goalData)
                .await()
        }

        // Pull remote changes (batch limit is 50, so this requires deterministic compound cursor pagination across pages)
        val pullResult = orchestrator.pullRemoteChanges(activeUserId)
        assertTrue("Pull must succeed: ${(pullResult as? Result.Failure)?.error?.message}", pullResult is Result.Success)

        // Verify Room received all 55 documents
        for (i in 0 until totalDocs) {
            val goalId = "goal-same-ts-${String.format("%02d", i)}"
            val roomGoal = ironMindDao.getGoal(goalId)
            assertNotNull("Goal $goalId must be present in Room without being dropped at page boundary", roomGoal)
        }

        // Verify compound cursor persisted in checkpointStore points to the last document
        val cursor = checkpointStore.getLastCursor(activeUserId, "Goal")
        assertEquals(sharedTimestamp, cursor.timestamp)
        assertEquals("goal-same-ts-54", cursor.documentId)
    }

    @Test
    fun test14_lateArrivingDocument_behindCheckpoint_recoveredByLookbackWindow() = runBlocking {
        val orchestrator = createOrchestrator()
        val normalGoalId = "goal-normal-${UUID.randomUUID()}"
        val normalTimestamp = 7_000_000L

        // Client uploads normal Goal at T = 7,000,000L
        val normalGoalData = mapOf(
            "id" to normalGoalId,
            "userId" to activeUserId,
            "title" to "Normal Goal",
            "importance" to 2,
            "status" to "ACTIVE",
            "version" to 1L,
            "createdAt" to normalTimestamp,
            "updatedAt" to normalTimestamp,
            "isDeleted" to false
        )
        firestore.collection("users").document(activeUserId).collection("goals").document(normalGoalId).set(normalGoalData).await()

        // Pull: checkpoint advances to 7,000,000L
        val pull1 = orchestrator.pullRemoteChanges(activeUserId)
        assertTrue(pull1 is Result.Success)
        assertNotNull(ironMindDao.getGoal(normalGoalId))
        assertEquals(normalTimestamp, checkpointStore.getLastCursor(activeUserId, "Goal").timestamp)

        // Late-arriving document arrives in Firestore with timestamp behind the checkpoint (T = 6,500,000L)
        val lateGoalId = "goal-late-${UUID.randomUUID()}"
        val lateTimestamp = 6_500_000L
        val lateGoalData = mapOf(
            "id" to lateGoalId,
            "userId" to activeUserId,
            "title" to "Late Arriving Goal",
            "importance" to 3,
            "status" to "ACTIVE",
            "version" to 1L,
            "createdAt" to lateTimestamp,
            "updatedAt" to lateTimestamp,
            "isDeleted" to false
        )
        firestore.collection("users").document(activeUserId).collection("goals").document(lateGoalId).set(lateGoalData).await()

        // Standard incremental pull (lookbackWindowMs = 0) starts strictly after 7,000,000L and skips late document
        val standardPull = orchestrator.pullRemoteChanges(activeUserId, lookbackWindowMs = 0L)
        assertTrue(standardPull is Result.Success)
        assertNull("Standard pull without lookback should not see document behind checkpoint", ironMindDao.getGoal(lateGoalId))

        // Reconciliation / catch-up pull with lookbackWindowMs = 1,000,000L (scans back to 6,000,000L)
        val currentCursor = checkpointStore.getLastCursor(activeUserId, "Goal")
        checkpointStore.clearCheckpointsForUser(activeUserId)
        checkpointStore.setLastCursor(
            activeUserId,
            "Goal",
            com.sanket_satpute_20.ironmind.domain.repository.SyncCursor(
                (currentCursor.timestamp - 1_000_000L).coerceAtLeast(0L),
                "0"
            )
        )
        val catchUpPull = orchestrator.pullRemoteChanges(activeUserId, lookbackWindowMs = 1_000_000L)
        assertTrue(catchUpPull is Result.Success)

        // Late-arriving document is successfully recovered and persisted in Room
        val recoveredGoal = ironMindDao.getGoal(lateGoalId)
        assertNotNull("Late-arriving document must be recovered by lookback window pull", recoveredGoal)
        assertEquals("Late Arriving Goal", recoveredGoal!!.title)
    }

    @Test
    fun test15_interruptedPagination_andRepeatedPulls_idempotentWithoutLoss() = runBlocking {
        val orchestrator = createOrchestrator()
        val baseTimestamp = 9_000_000L
        val totalDocs = 60

        // Create 60 distinct goals with monotonically increasing timestamps
        for (i in 0 until totalDocs) {
            val goalId = "goal-interrupted-${String.format("%02d", i)}"
            val ts = baseTimestamp + (i * 1000L)
            val goalData = mapOf(
                "id" to goalId,
                "userId" to activeUserId,
                "title" to "Interrupted Test Goal #$i",
                "importance" to 1,
                "status" to "ACTIVE",
                "version" to 1L,
                "createdAt" to ts,
                "updatedAt" to ts,
                "isDeleted" to false
            )
            firestore.collection("users").document(activeUserId).collection("goals").document(goalId).set(goalData).await()
        }

        // Simulate Page 1 pull (50 items)
        val page1Result = syncRepository.pull(
            userId = activeUserId,
            entityType = "Goal",
            cursor = com.sanket_satpute_20.ironmind.domain.repository.SyncCursor(0L, ""),
            limit = 50
        )
        assertTrue(page1Result is Result.Success)
        val page1Success = (page1Result as Result.Success).data as SyncPullResult.Success
        assertEquals(50, page1Success.items.size)
        assertTrue(page1Success.hasMore)

        // Seed Page 1 items in Room to represent committed state before interruption
        for (i in 0 until 50) {
            val goalId = "goal-interrupted-${String.format("%02d", i)}"
            val ts = baseTimestamp + (i * 1000L)
            ironMindDao.insertGoal(
                GoalEntity(
                    id = goalId,
                    userId = activeUserId,
                    ambitionId = null,
                    title = "Interrupted Test Goal #$i",
                    description = "",
                    why = "",
                    importance = 1,
                    status = "ACTIVE",
                    targetAt = null,
                    startedAt = null,
                    completedAt = null,
                    createdAt = ts,
                    updatedAt = ts,
                    schemaVersion = 1
                )
            )
        }

        // Simulate interrupted state after Page 1 commit: checkpoint store set to Page 1 cursor
        val page1LastItem = page1Success.items.last()
        val interruptedCursor = com.sanket_satpute_20.ironmind.domain.repository.SyncCursor(
            timestamp = page1LastItem.updatedAt,
            documentId = page1LastItem.entityId
        )
        checkpointStore.setLastCursor(activeUserId, "Goal", interruptedCursor)

        // Resume pull through orchestrator: picks up from interruptedCursor and finishes the remaining 10 items
        val resumedPull = orchestrator.pullRemoteChanges(activeUserId)
        assertTrue(resumedPull is Result.Success)

        // Verify all 60 documents are in Room
        for (i in 0 until totalDocs) {
            val goalId = "goal-interrupted-${String.format("%02d", i)}"
            assertNotNull("Goal $goalId must exist in Room after resumed pull", ironMindDao.getGoal(goalId))
        }

        // Run repeated pull: must be completely idempotent and apply 0 new items
        val repeatedPull = orchestrator.pullRemoteChanges(activeUserId)
        assertTrue(repeatedPull is Result.Success)
        assertEquals(0, (repeatedPull as Result.Success).data)
    }
}
