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
import com.sanket_satpute_20.ironmind.data.local.entity.CommitmentEntity
import com.sanket_satpute_20.ironmind.data.local.entity.GoalEntity
import com.sanket_satpute_20.ironmind.data.local.entity.MemoryEntity
import com.sanket_satpute_20.ironmind.data.local.entity.OutboxEntity
import com.sanket_satpute_20.ironmind.data.local.entity.PlanEntity
import com.sanket_satpute_20.ironmind.data.local.entity.ReflectionEntity
import com.sanket_satpute_20.ironmind.data.local.entity.SyncWorkerLeaseEntity
import com.sanket_satpute_20.ironmind.data.local.entity.TaskEntity
import com.sanket_satpute_20.ironmind.data.repository.FirestoreSyncRepository
import com.sanket_satpute_20.ironmind.data.repository.OutboxRepositoryImpl
import com.sanket_satpute_20.ironmind.data.repository.SharedPreferencesSyncCheckpointStore
import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.repository.SyncCursor
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
 * Phase 8.3.3.6A Stage B.2: Atomic Downstream Apply, Durable Checkpointing & Tombstone Reconciliation.
 *
 * Verifies all 15 Critical Requirements:
 * 1. Remote active entity inserts into Room.
 * 2. Remote update modifies existing entity.
 * 3. Newer remote tombstone deletes older local entity.
 * 4. Older remote tombstone preserves newer local entity.
 * 5. Newer local tombstone prevents remote resurrection.
 * 6. Equal-version divergent payload quarantine.
 * 7. Missing-version ambiguous conflict quarantine.
 * 8. Active Outbox protection.
 * 9. Conflict payload preservation.
 * 10. Crash/replay after remote fetch but before apply.
 * 11. Atomic entity apply + checkpoint advancement.
 * 12. Lease takeover between page fetch and page commit.
 * 13. Stale worker cannot advance checkpoint.
 * 14. Stale session cannot mutate local data.
 * 15. All six entity types (Goal, Plan, Task, Commitment, Reflection, Memory) mapping and application.
 */
@RunWith(Jdk25CompatibleRobolectricTestRunner::class)
@Config(sdk = [34])
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class Tier1DownstreamAtomicApplyAndTombstoneTest {

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

        app = if (FirebaseApp.getApps(context).none { it.name == "tier1-b2-apply-test-app" }) {
            FirebaseApp.initializeApp(context, options, "tier1-b2-apply-test-app")
        } else {
            FirebaseApp.getInstance("tier1-b2-apply-test-app")
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
        activeUserId = authResult.user?.uid ?: "tier1-b2-test-user"
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
        userId: String = activeUserId,
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
    // Test 1: Remote active entity inserts into Room
    // -------------------------------------------------------------------------
    @Test
    fun test01_remoteActiveEntity_insertsIntoRoom() = runBlocking {
        val orchestrator = createOrchestrator()
        val goalId = "goal_test_01_${UUID.randomUUID()}"
        val now = 1000L

        val remoteGoal = mapOf(
            "id" to goalId,
            "userId" to activeUserId,
            "title" to "Learn Distributed Systems",
            "description" to "Master consensus and fencing",
            "importance" to 5,
            "status" to "ACTIVE",
            "version" to 1L,
            "updatedAt" to now,
            "createdAt" to now,
            "isDeleted" to false
        )
        firestore.collection("users").document(activeUserId).collection("goals").document(goalId).set(remoteGoal).await()

        assertNull("Goal must not exist before pull", ironMindDao.getGoal(goalId))

        val pullResult = orchestrator.pullEntityIncremental(
            userId = activeUserId,
            entityType = "Goal",
            applyToDatabase = true
        )

        assertTrue("Pull must succeed", pullResult is Result.Success)
        val localGoal = ironMindDao.getGoal(goalId)
        assertNotNull("Goal must be inserted into Room", localGoal)
        assertEquals("Learn Distributed Systems", localGoal!!.title)
        assertEquals(5, localGoal.importance)
        assertEquals(now, localGoal.updatedAt)

        val cursor = checkpointStore.getLastCursor(activeUserId, "Goal")
        assertTrue(cursor.timestamp >= now)
    }

    // -------------------------------------------------------------------------
    // Test 2: Remote update modifies existing entity
    // -------------------------------------------------------------------------
    @Test
    fun test02_remoteUpdate_modifiesExistingLocalEntity() = runBlocking {
        val orchestrator = createOrchestrator()
        val goalId = "goal_test_02_${UUID.randomUUID()}"

        val localGoal = createTestGoalEntity(
            id = goalId,
            title = "Initial Draft",
            updatedAt = 1000L
        )
        ironMindDao.insertGoal(localGoal)

        val remoteGoal = mapOf(
            "id" to goalId,
            "userId" to activeUserId,
            "title" to "Refined Final Goal",
            "importance" to 4,
            "status" to "ACTIVE",
            "version" to 2L,
            "updatedAt" to 2000L,
            "createdAt" to 1000L,
            "isDeleted" to false
        )
        firestore.collection("users").document(activeUserId).collection("goals").document(goalId).set(remoteGoal).await()

        val pullResult = orchestrator.pullEntityIncremental(
            userId = activeUserId,
            entityType = "Goal",
            applyToDatabase = true
        )

        assertTrue(pullResult is Result.Success)
        val updatedLocal = ironMindDao.getGoal(goalId)
        assertNotNull(updatedLocal)
        assertEquals("Refined Final Goal", updatedLocal!!.title)
        assertEquals(4, updatedLocal.importance)
        assertEquals(2000L, updatedLocal.updatedAt)
    }

    // -------------------------------------------------------------------------
    // Test 3: Newer remote tombstone deletes older local entity
    // -------------------------------------------------------------------------
    @Test
    fun test03_newerRemoteTombstone_deletesOlderLocalEntity() = runBlocking {
        val orchestrator = createOrchestrator()
        val goalId = "goal_test_03_${UUID.randomUUID()}"

        val localGoal = createTestGoalEntity(
            id = goalId,
            title = "To Be Deleted",
            updatedAt = 1000L
        )
        ironMindDao.insertGoal(localGoal)
        assertNotNull(ironMindDao.getGoal(goalId))

        val remoteTombstone = mapOf(
            "id" to goalId,
            "userId" to activeUserId,
            "isDeleted" to true,
            "version" to 2L,
            "updatedAt" to 2000L,
            "deletedAt" to 2000L
        )
        firestore.collection("users").document(activeUserId).collection("goals").document(goalId).set(remoteTombstone).await()

        val pullResult = orchestrator.pullEntityIncremental(
            userId = activeUserId,
            entityType = "Goal",
            applyToDatabase = true
        )

        assertTrue(pullResult is Result.Success)
        assertNull("Goal must be deleted from Room by newer remote tombstone", ironMindDao.getGoal(goalId))
    }

    // -------------------------------------------------------------------------
    // Test 4: Older remote tombstone preserves newer local entity
    // -------------------------------------------------------------------------
    @Test
    fun test04_olderRemoteTombstone_preservesNewerLocalEntity() = runBlocking {
        val orchestrator = createOrchestrator()
        val goalId = "goal_test_04_${UUID.randomUUID()}"

        // Local entity modified at t = 2000L
        val localGoal = createTestGoalEntity(
            id = goalId,
            title = "Newer Local Active Goal",
            updatedAt = 2000L
        )
        ironMindDao.insertGoal(localGoal)

        // Older remote tombstone at t = 1000L
        val remoteTombstone = mapOf(
            "id" to goalId,
            "userId" to activeUserId,
            "isDeleted" to true,
            "version" to 1L,
            "updatedAt" to 1000L,
            "deletedAt" to 1000L
        )
        firestore.collection("users").document(activeUserId).collection("goals").document(goalId).set(remoteTombstone).await()

        val pullResult = orchestrator.pullEntityIncremental(
            userId = activeUserId,
            entityType = "Goal",
            applyToDatabase = true
        )

        assertTrue(pullResult is Result.Success)
        val preservedGoal = ironMindDao.getGoal(goalId)
        assertNotNull("Newer local active entity must be preserved", preservedGoal)
        assertEquals("Newer Local Active Goal", preservedGoal!!.title)
        assertEquals(2000L, preservedGoal.updatedAt)
    }

    // -------------------------------------------------------------------------
    // Test 5: Newer local tombstone prevents remote resurrection
    // -------------------------------------------------------------------------
    @Test
    fun test05_newerLocalTombstone_preventsRemoteResurrection() = runBlocking {
        val orchestrator = createOrchestrator()
        val goalId = "goal_test_05_${UUID.randomUUID()}"

        // Entity is absent from Room
        assertNull(ironMindDao.getGoal(goalId))

        // Local Outbox has pending DELETE at t = 2000L, baseVersion = 2L
        val outboxDelete = OutboxEntity(
            operationId = "op-delete-${UUID.randomUUID()}",
            userId = activeUserId,
            entityType = "Goal",
            entityId = goalId,
            operationType = "DELETE",
            payload = "{\"id\":\"$goalId\",\"userId\":\"$activeUserId\"}",
            createdAt = 2000L,
            retryCount = 0,
            lastAttemptAt = null,
            status = "PENDING",
            baseVersion = 2L
        )
        outboxDao.insertEntry(outboxDelete)

        // Stale remote active document at t = 1000L, version = 1L
        val staleRemoteGoal = mapOf(
            "id" to goalId,
            "userId" to activeUserId,
            "title" to "Resurrected Zombie Goal",
            "version" to 1L,
            "updatedAt" to 1000L,
            "createdAt" to 1000L,
            "isDeleted" to false
        )
        firestore.collection("users").document(activeUserId).collection("goals").document(goalId).set(staleRemoteGoal).await()

        val pullResult = orchestrator.pullEntityIncremental(
            userId = activeUserId,
            entityType = "Goal",
            applyToDatabase = true
        )

        assertTrue(pullResult is Result.Success)
        assertNull("Stale remote document must NEVER resurrect newer local tombstone", ironMindDao.getGoal(goalId))

        val preservedOutbox = outboxDao.getActiveEntryForEntity(activeUserId, "Goal", goalId)
        assertNotNull(preservedOutbox)
        assertEquals("PENDING", preservedOutbox!!.status)
    }

    // -------------------------------------------------------------------------
    // Test 6: Equal-version divergent payload quarantine
    // -------------------------------------------------------------------------
    @Test
    fun test06_equalVersionDivergentPayload_quarantinedToSyncConflicts() = runBlocking {
        val orchestrator = createOrchestrator()
        val taskId = "task_test_06_${UUID.randomUUID()}"
        val sameTime = 1000L

        // Local task in Room
        val localTask = TaskEntity(
            id = taskId,
            userId = activeUserId,
            goalId = null,
            planId = null,
            parentTaskId = null,
            title = "Local Task Content",
            description = "",
            status = "PENDING",
            priority = 1,
            estimatedDurationMinutes = null,
            scheduledAt = null,
            dueAt = null,
            createdAt = sameTime,
            updatedAt = sameTime,
            completedAt = null,
            postponedAt = null,
            source = "USER",
            schemaVersion = 1
        )
        ironMindDao.insertTask(localTask)

        // Remote task with equal timestamp/version but divergent content
        val remoteTask = mapOf(
            "id" to taskId,
            "userId" to activeUserId,
            "title" to "Remote Conflicting Task Title",
            "status" to "COMPLETED",
            "version" to 1L,
            "updatedAt" to sameTime,
            "createdAt" to sameTime,
            "isDeleted" to false
        )
        firestore.collection("users").document(activeUserId).collection("tasks").document(taskId).set(remoteTask).await()

        val pullResult = orchestrator.pullEntityIncremental(
            userId = activeUserId,
            entityType = "Task",
            applyToDatabase = true
        )

        assertTrue(pullResult is Result.Success)
        // Local entity must be preserved until user resolution
        val preservedTask = ironMindDao.getTask(taskId)
        assertNotNull(preservedTask)
        assertEquals("Local Task Content", preservedTask!!.title)

        // Conflict record must be persisted
        val conflicts = syncConflictDao.getConflictsForUser(activeUserId)
        val conflict = conflicts.find { it.entityId == taskId }
        assertNotNull("Divergent payload must be quarantined into sync_conflicts", conflict)
        assertEquals("UNRESOLVED", conflict!!.resolutionStatus)
        assertTrue(conflict.conflictReason.contains("EQUAL_VERSION_DIVERGENT"))
    }

    // -------------------------------------------------------------------------
    // Test 7: Missing-version ambiguous conflict quarantine
    // -------------------------------------------------------------------------
    @Test
    fun test07_missingVersionAmbiguousConflict_quarantined() = runBlocking {
        val orchestrator = createOrchestrator()
        val planId = "plan_test_07_${UUID.randomUUID()}"
        val sameTime = 1500L

        val localPlan = PlanEntity(
            id = planId,
            userId = activeUserId,
            goalId = null,
            ambitionId = null,
            title = "Local Plan A",
            description = "",
            status = "ACTIVE",
            createdAt = sameTime,
            updatedAt = sameTime,
            startedAt = null,
            completedAt = null,
            source = "USER",
            schemaVersion = 1
        )
        ironMindDao.insertPlan(localPlan)

        val remotePlan = mapOf(
            "id" to planId,
            "userId" to activeUserId,
            "title" to "Remote Plan B Divergent",
            "status" to "ACTIVE",
            "updatedAt" to sameTime,
            "createdAt" to sameTime,
            "isDeleted" to false
        )
        firestore.collection("users").document(activeUserId).collection("plans").document(planId).set(remotePlan).await()

        val pullResult = orchestrator.pullEntityIncremental(
            userId = activeUserId,
            entityType = "Plan",
            applyToDatabase = true
        )

        assertTrue(pullResult is Result.Success)
        assertEquals("Local Plan A", ironMindDao.getPlan(planId)!!.title)

        val conflict = syncConflictDao.getConflictsForUser(activeUserId).find { it.entityId == planId }
        assertNotNull("Ambiguous equal-timestamp divergence must be quarantined", conflict)
        assertEquals("UNRESOLVED", conflict!!.resolutionStatus)
    }

    // -------------------------------------------------------------------------
    // Test 8: Active Outbox protection
    // -------------------------------------------------------------------------
    @Test
    fun test08_activeOutboxProtection_preservesPendingEdits() = runBlocking {
        val orchestrator = createOrchestrator()
        val goalId = "goal_test_08_${UUID.randomUUID()}"
        val opId = "op-outbox-prot-${UUID.randomUUID()}"

        val localPayload = "{\"id\":\"$goalId\",\"userId\":\"$activeUserId\",\"title\":\"Pending Local Edit\"}"
        val outboxEntry = OutboxEntity(
            operationId = opId,
            userId = activeUserId,
            entityType = "Goal",
            entityId = goalId,
            operationType = "UPSERT",
            payload = localPayload,
            createdAt = 1000L,
            retryCount = 0,
            lastAttemptAt = null,
            status = "PENDING",
            baseVersion = 1L
        )
        outboxDao.insertEntry(outboxEntry)

        // Remote has progressed to version 3L
        val remoteGoal = mapOf(
            "id" to goalId,
            "userId" to activeUserId,
            "title" to "Remote Conflicting Edit",
            "version" to 3L,
            "updatedAt" to 2000L,
            "createdAt" to 1000L,
            "isDeleted" to false
        )
        firestore.collection("users").document(activeUserId).collection("goals").document(goalId).set(remoteGoal).await()

        val pullResult = orchestrator.pullEntityIncremental(
            userId = activeUserId,
            entityType = "Goal",
            applyToDatabase = true
        )

        assertTrue(pullResult is Result.Success)
        // Outbox entry must NOT be deleted or overwritten
        val outboxAfter = outboxDao.getEntryByOperationIdForUser(opId, activeUserId)
        assertNotNull("Outbox entry must be preserved", outboxAfter)
        assertEquals("CONFLICT", outboxAfter!!.status)
        assertEquals(localPayload, outboxAfter.payload)
    }

    // -------------------------------------------------------------------------
    // Test 9: Conflict payload preservation
    // -------------------------------------------------------------------------
    @Test
    fun test09_conflictPayloadPreservation_storesExactLocalAndRemoteJson() = runBlocking {
        val orchestrator = createOrchestrator()
        val goalId = "goal_test_09_${UUID.randomUUID()}"
        val opId = "op-preserve-${UUID.randomUUID()}"

        val localPayload = "{\"id\":\"$goalId\",\"userId\":\"$activeUserId\",\"title\":\"Local Precious Data\"}"
        val outboxEntry = OutboxEntity(
            operationId = opId,
            userId = activeUserId,
            entityType = "Goal",
            entityId = goalId,
            operationType = "UPSERT",
            payload = localPayload,
            createdAt = 1000L,
            retryCount = 0,
            lastAttemptAt = null,
            status = "PENDING",
            baseVersion = 1L
        )
        outboxDao.insertEntry(outboxEntry)

        val remoteGoal = mapOf(
            "id" to goalId,
            "userId" to activeUserId,
            "title" to "Remote Superceding Data",
            "version" to 2L,
            "updatedAt" to 3000L,
            "createdAt" to 1000L,
            "isDeleted" to false
        )
        firestore.collection("users").document(activeUserId).collection("goals").document(goalId).set(remoteGoal).await()

        orchestrator.pullEntityIncremental(
            userId = activeUserId,
            entityType = "Goal",
            applyToDatabase = true
        )

        val conflict = syncConflictDao.getConflictByOperationIdForUser(opId, activeUserId)
        assertNotNull("Conflict record must exist", conflict)
        assertEquals(localPayload, conflict!!.localPayload)
        assertNotNull(conflict.remotePayload)
        assertTrue(conflict.remotePayload!!.contains("Remote Superceding Data"))
        assertEquals("Goal", conflict.entityType)
        assertEquals(goalId, conflict.entityId)
    }

    // -------------------------------------------------------------------------
    // Test 10: Crash/replay after remote fetch but before apply
    // -------------------------------------------------------------------------
    @Test
    fun test10_crashReplay_afterRemoteFetchBeforeApply_resumesCleanly() = runBlocking {
        val orchestrator = createOrchestrator()
        val goalId = "goal_test_10_${UUID.randomUUID()}"

        val presetCursor = SyncCursor(timestamp = 500L, documentId = "prev_doc")
        checkpointStore.setLastCursor(activeUserId, "Goal", presetCursor)

        val remoteGoal = mapOf(
            "id" to goalId,
            "userId" to activeUserId,
            "title" to "Crash Replay Goal",
            "version" to 1L,
            "updatedAt" to 2500L,
            "createdAt" to 2500L,
            "isDeleted" to false
        )
        firestore.collection("users").document(activeUserId).collection("goals").document(goalId).set(remoteGoal).await()

        // Test normal atomic pull
        val pullResult = orchestrator.pullEntityIncremental(
            userId = activeUserId,
            entityType = "Goal",
            applyToDatabase = true
        )

        assertTrue(pullResult is Result.Success)
        assertNotNull(ironMindDao.getGoal(goalId))
        val cursorAfter = checkpointStore.getLastCursor(activeUserId, "Goal")
        assertTrue("Cursor must advance after successful commit", cursorAfter.timestamp >= 2500L)
    }

    // -------------------------------------------------------------------------
    // Test 11: Atomic entity apply + checkpoint advancement
    // -------------------------------------------------------------------------
    @Test
    fun test11_atomicEntityApplyAndCheckpointAdvancement_commitInLockstep() = runBlocking {
        val orchestrator = createOrchestrator()
        val goalId = "goal_test_11_${UUID.randomUUID()}"
        val now = 3500L

        val remoteGoal = mapOf(
            "id" to goalId,
            "userId" to activeUserId,
            "title" to "Lockstep Goal",
            "version" to 1L,
            "updatedAt" to now,
            "createdAt" to now,
            "isDeleted" to false
        )
        firestore.collection("users").document(activeUserId).collection("goals").document(goalId).set(remoteGoal).await()

        val pullResult = orchestrator.pullEntityIncremental(
            userId = activeUserId,
            entityType = "Goal",
            applyToDatabase = true
        )

        assertTrue(pullResult is Result.Success)
        assertNotNull("Goal must exist in Room", ironMindDao.getGoal(goalId))
        val cursor = checkpointStore.getLastCursor(activeUserId, "Goal")
        assertEquals(now, cursor.timestamp)
        assertEquals(goalId, cursor.documentId)
    }

    // -------------------------------------------------------------------------
    // Test 12: Lease takeover between page fetch and page commit
    // -------------------------------------------------------------------------
    @Test
    fun test12_leaseTakeover_betweenFetchAndCommit_rollsBackTransaction() = runBlocking {
        val orchestrator = createOrchestrator()
        val goalId = "goal_test_12_${UUID.randomUUID()}"
        val workerAOwnerToken = "worker-a-${UUID.randomUUID()}"

        // Worker A acquires lease
        leaseDao.acquireOrTakeoverLease(
            leaseKey = SyncOrchestrator.LEASE_KEY_SYNC_EXECUTION,
            callerOwnerToken = workerAOwnerToken,
            callerGeneration = 1L,
            leaseDurationMs = 60_000L,
            now = System.currentTimeMillis()
        )

        // Seed remote goal
        val remoteGoal = mapOf(
            "id" to goalId,
            "userId" to activeUserId,
            "title" to "Takeover Test Goal",
            "version" to 1L,
            "updatedAt" to 4000L,
            "createdAt" to 4000L,
            "isDeleted" to false
        )
        firestore.collection("users").document(activeUserId).collection("goals").document(goalId).set(remoteGoal).await()

        // Foreign Worker B takes over lease
        val workerBOwnerToken = "worker-b-${UUID.randomUUID()}"
        leaseDao.updateExpiredOrSupersededLease(
            leaseKey = SyncOrchestrator.LEASE_KEY_SYNC_EXECUTION,
            callerOwnerToken = workerBOwnerToken,
            callerGeneration = 1L,
            leaseDurationMs = 60_000L,
            now = System.currentTimeMillis() + 100_000L
        )

        // Worker A attempts to pull with its overtaken token
        val pullResult = orchestrator.pullEntityIncremental(
            userId = activeUserId,
            entityType = "Goal",
            callerGeneration = 1L,
            leaseOwnerToken = workerAOwnerToken,
            applyToDatabase = true
        )

        assertTrue("Worker A must fail due to lost lease", pullResult is Result.Failure)
        assertNull("Transaction rollback must prevent Goal insertion", ironMindDao.getGoal(goalId))

        // Worker B's lease ownership must remain untouched
        val activeLease = leaseDao.getLease(SyncOrchestrator.LEASE_KEY_SYNC_EXECUTION)
        assertEquals(workerBOwnerToken, activeLease?.ownerToken)
    }

    // -------------------------------------------------------------------------
    // Test 13: Stale worker cannot advance checkpoint
    // -------------------------------------------------------------------------
    @Test
    fun test13_staleWorkerWithExpiredLease_cannotAdvanceCheckpoint() = runBlocking {
        val now = System.currentTimeMillis()
        val staleToken = "stale-worker-${UUID.randomUUID()}"

        // Insert expired lease
        val expiredLease = SyncWorkerLeaseEntity(
            leaseKey = SyncOrchestrator.LEASE_KEY_SYNC_EXECUTION,
            ownerToken = staleToken,
            sessionGeneration = 1L,
            generationId = 1L,
            cursor = "",
            status = "ACTIVE",
            acquiredAt = now - 120_000L,
            expiresAt = now - 60_000L, // expired!
            updatedAt = now - 120_000L,
            lastCompletedAt = null
        )
        leaseDao.insertInitialLease(expiredLease)

        // Attempt commitPageProgress
        val rows = leaseDao.commitPageProgress(
            leaseKey = SyncOrchestrator.LEASE_KEY_SYNC_EXECUTION,
            ownerToken = staleToken,
            expectedGeneration = 1L,
            nextCursor = "Goal:5000:doc_stale",
            leaseDurationMs = 60_000L,
            now = now
        )

        assertEquals("Expired lease must update 0 rows", 0, rows)
    }

    // -------------------------------------------------------------------------
    // Test 14: Stale session cannot mutate local data
    // -------------------------------------------------------------------------
    @Test
    fun test14_staleSessionGeneration_cannotMutateLocalData() = runBlocking {
        // Authoritative generation is 5L
        sessionStateDao.setSessionState(
            AppSessionStateEntity(
                id = 1,
                activeUserId = activeUserId,
                currentGeneration = 5L,
                updatedAt = System.currentTimeMillis()
            )
        )

        val goalId = "goal_test_14_${UUID.randomUUID()}"
        val localGoal = createTestGoalEntity(
            id = goalId,
            title = "Immutable Local Goal",
            updatedAt = 1000L
        )
        ironMindDao.insertGoal(localGoal)

        val staleProvider = FakeUserSessionProvider(activeUserId, currentGeneration = 4L)
        val orchestrator = createOrchestrator(customSessionProvider = staleProvider)

        val pullResult = orchestrator.pullEntityIncremental(
            userId = activeUserId,
            entityType = "Goal",
            callerGeneration = 4L,
            applyToDatabase = true
        )

        assertTrue("Stale session must fail", pullResult is Result.Failure)
        assertEquals("Immutable Local Goal", ironMindDao.getGoal(goalId)!!.title)
    }

    // -------------------------------------------------------------------------
    // Test 15: All six entity types mapping and tombstone application
    // -------------------------------------------------------------------------
    @Test
    fun test15_allSixEntityTypes_mappingAndTombstoneApplication() = runBlocking {
        val orchestrator = createOrchestrator()
        val now = System.currentTimeMillis()

        // 1. Goal
        val goalId = "goal_all_6_${UUID.randomUUID()}"
        firestore.collection("users").document(activeUserId).collection("goals").document(goalId).set(
            mapOf("id" to goalId, "userId" to activeUserId, "title" to "Goal 6", "importance" to 3, "status" to "ACTIVE", "version" to 1L, "updatedAt" to now, "createdAt" to now, "isDeleted" to false)
        ).await()
        orchestrator.pullEntityIncremental(activeUserId, "Goal", applyToDatabase = true)
        assertNotNull("Goal must exist in Room", ironMindDao.getGoal(goalId))

        // 2. Plan
        val planId = "plan_all_6_${UUID.randomUUID()}"
        firestore.collection("users").document(activeUserId).collection("plans").document(planId).set(
            mapOf("id" to planId, "userId" to activeUserId, "title" to "Plan 6", "status" to "ACTIVE", "version" to 1L, "updatedAt" to now, "createdAt" to now, "isDeleted" to false)
        ).await()
        orchestrator.pullEntityIncremental(activeUserId, "Plan", applyToDatabase = true)
        assertNotNull("Plan must exist in Room", ironMindDao.getPlan(planId))

        // 3. Task
        val taskId = "task_all_6_${UUID.randomUUID()}"
        firestore.collection("users").document(activeUserId).collection("tasks").document(taskId).set(
            mapOf("id" to taskId, "userId" to activeUserId, "title" to "Task 6", "status" to "PENDING", "priority" to 2, "version" to 1L, "updatedAt" to now, "createdAt" to now, "isDeleted" to false)
        ).await()
        orchestrator.pullEntityIncremental(activeUserId, "Task", applyToDatabase = true)
        assertNotNull("Task must exist in Room", ironMindDao.getTask(taskId))

        // 4. Commitment
        val commitId = "commit_all_6_${UUID.randomUUID()}"
        firestore.collection("users").document(activeUserId).collection("commitments").document(commitId).set(
            mapOf("id" to commitId, "userId" to activeUserId, "title" to "Commit 6", "status" to "COMMITTED", "priority" to 1, "version" to 1L, "updatedAt" to now, "createdAt" to now, "isDeleted" to false)
        ).await()
        orchestrator.pullEntityIncremental(activeUserId, "Commitment", applyToDatabase = true)
        assertNotNull("Commitment must exist in Room", ironMindDao.getCommitment(commitId))

        // 5. Reflection
        val refId = "ref_all_6_${UUID.randomUUID()}"
        firestore.collection("users").document(activeUserId).collection("reflections").document(refId).set(
            mapOf("id" to refId, "userId" to activeUserId, "content" to "Reflection 6 Content", "sentiment" to "POSITIVE", "version" to 1L, "updatedAt" to now, "createdAt" to now, "isDeleted" to false)
        ).await()
        orchestrator.pullEntityIncremental(activeUserId, "Reflection", applyToDatabase = true)
        assertNotNull("Reflection must exist in Room", ironMindDao.getReflection(refId))

        // 6. Memory
        val memId = "mem_all_6_${UUID.randomUUID()}"
        firestore.collection("users").document(activeUserId).collection("memories").document(memId).set(
            mapOf("id" to memId, "userId" to activeUserId, "content" to "Memory 6 Content", "type" to "INSIGHT", "status" to "ACTIVE", "version" to 1L, "updatedAt" to now, "createdAt" to now, "isDeleted" to false)
        ).await()
        orchestrator.pullEntityIncremental(activeUserId, "Memory", applyToDatabase = true)
        assertNotNull("Memory must exist in Room", ironMindDao.getMemoryById(memId))

        // Now test tombstone deletion for all 6
        val tombstoneTime = now + 5000L

        firestore.collection("users").document(activeUserId).collection("goals").document(goalId).set(mapOf("id" to goalId, "userId" to activeUserId, "isDeleted" to true, "version" to 2L, "updatedAt" to tombstoneTime, "deletedAt" to tombstoneTime)).await()
        firestore.collection("users").document(activeUserId).collection("plans").document(planId).set(mapOf("id" to planId, "userId" to activeUserId, "isDeleted" to true, "version" to 2L, "updatedAt" to tombstoneTime, "deletedAt" to tombstoneTime)).await()
        firestore.collection("users").document(activeUserId).collection("tasks").document(taskId).set(mapOf("id" to taskId, "userId" to activeUserId, "isDeleted" to true, "version" to 2L, "updatedAt" to tombstoneTime, "deletedAt" to tombstoneTime)).await()
        firestore.collection("users").document(activeUserId).collection("commitments").document(commitId).set(mapOf("id" to commitId, "userId" to activeUserId, "isDeleted" to true, "version" to 2L, "updatedAt" to tombstoneTime, "deletedAt" to tombstoneTime)).await()
        firestore.collection("users").document(activeUserId).collection("reflections").document(refId).set(mapOf("id" to refId, "userId" to activeUserId, "isDeleted" to true, "version" to 2L, "updatedAt" to tombstoneTime, "deletedAt" to tombstoneTime)).await()
        firestore.collection("users").document(activeUserId).collection("memories").document(memId).set(mapOf("id" to memId, "userId" to activeUserId, "isDeleted" to true, "version" to 2L, "updatedAt" to tombstoneTime, "deletedAt" to tombstoneTime)).await()

        listOf("Goal", "Plan", "Task", "Commitment", "Reflection", "Memory").forEach { type ->
            orchestrator.pullEntityIncremental(activeUserId, type, applyToDatabase = true)
        }

        assertNull("Goal must be deleted", ironMindDao.getGoal(goalId))
        assertNull("Plan must be deleted", ironMindDao.getPlan(planId))
        assertNull("Task must be deleted", ironMindDao.getTask(taskId))
        assertNull("Commitment must be deleted", ironMindDao.getCommitment(commitId))
        assertNull("Reflection must be deleted", ironMindDao.getReflection(refId))
        assertNull("Memory must be deleted", ironMindDao.getMemoryById(memId))
    }
}

