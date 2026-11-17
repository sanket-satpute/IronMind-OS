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
import com.google.firebase.firestore.SetOptions
import com.sanket_satpute_20.ironmind.data.local.IronMindDatabase
import com.sanket_satpute_20.ironmind.data.local.Jdk25CompatibleRobolectricTestRunner
import com.sanket_satpute_20.ironmind.data.local.dao.IronMindDao
import com.sanket_satpute_20.ironmind.data.local.dao.OutboxDao
import com.sanket_satpute_20.ironmind.data.local.dao.SyncConflictDao
import com.sanket_satpute_20.ironmind.data.local.entity.CommitmentEntity
import com.sanket_satpute_20.ironmind.data.local.entity.GoalEntity
import com.sanket_satpute_20.ironmind.data.local.entity.MemoryEntity
import com.sanket_satpute_20.ironmind.data.local.entity.OutboxEntity
import com.sanket_satpute_20.ironmind.data.local.entity.ReflectionEntity
import com.sanket_satpute_20.ironmind.data.local.entity.SyncConflictEntity
import com.sanket_satpute_20.ironmind.data.local.entity.toDomain
import com.sanket_satpute_20.ironmind.data.repository.FirestoreSyncRepository
import com.sanket_satpute_20.ironmind.data.repository.OutboxRepositoryImpl
import com.sanket_satpute_20.ironmind.data.repository.SyncConflictRepositoryImpl
import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.OutboxEntry
import com.sanket_satpute_20.ironmind.domain.model.OutboxOperationType
import com.sanket_satpute_20.ironmind.domain.model.SyncStatus
import com.sanket_satpute_20.ironmind.domain.repository.ConflictResolutionStrategy
import com.sanket_satpute_20.ironmind.domain.repository.SyncUploadResult
import java.util.UUID
import java.util.concurrent.Executor
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
 * Phase 8.3.3.2.5 — Genuine Android Firebase SDK to Firestore Emulator Integration Test Suite.
 *
 * Exercises the actual production FirestoreSyncRepository using the Firebase Android SDK
 * communicating over live gRPC/HTTP with the local Firebase Auth and Firestore Emulators.
 */
@RunWith(Jdk25CompatibleRobolectricTestRunner::class)
@Config(sdk = [34])
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class FirestoreEmulatorSdkTest {

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
    private lateinit var conflictRepo: SyncConflictRepositoryImpl

    private var activeUserId: String = ""

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
        outboxRepo = OutboxRepositoryImpl(outboxDao)
        conflictRepo = SyncConflictRepositoryImpl(syncConflictDao, outboxDao, db, ironMindDao)

        val options = FirebaseOptions.Builder()
            .setApplicationId("com.sanket_satpute_20.ironmind")
            .setProjectId("demo-ironmind")
            .setApiKey("fake-api-key")
            .build()

        app = if (FirebaseApp.getApps(context).none { it.name == "emulator-sdk-app" }) {
            FirebaseApp.initializeApp(context, options, "emulator-sdk-app")
        } else {
            FirebaseApp.getInstance("emulator-sdk-app")
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

        // Sign in anonymously to Auth emulator to acquire valid UID for security rules
        val authResult = auth.signInAnonymously().await()
        activeUserId = authResult.user?.uid ?: "test-sdk-user"
    }

    @After
    fun tearDown() {
        try {
            db.close()
        } finally {
            ArchTaskExecutor.getInstance().setDelegate(null)
        }
    }

    // -------------------------------------------------------------------------
    // 1. Create a document through the actual repository
    // -------------------------------------------------------------------------
    @Test
    fun test01_createDocumentThroughActualRepository() = runBlocking {
        val entry = OutboxEntry(
            operationId = "op-create-1",
            entityType = "Goal",
            entityId = "goal-sdk-1",
            operationType = OutboxOperationType.UPSERT,
            payload = """{"id":"goal-sdk-1","userId":"$activeUserId","title":"Master Kotlin","importance":5,"status":"ACTIVE"}""",
            createdAt = 1000L,
            retryCount = 0,
            lastAttemptAt = null,
            status = SyncStatus.PENDING,
            baseVersion = 0L
        )

        val result = syncRepository.upload(activeUserId, entry)
        assertTrue("Upload must succeed: $result", result is Result.Success)
        val uploadResult = (result as Result.Success).data
        assertEquals(SyncUploadResult.Success, uploadResult)

        // Verify document directly in Firestore Emulator
        val docSnap = firestore.collection("users").document(activeUserId).collection("goals").document("goal-sdk-1").get().await()
        assertTrue("Document must exist in Firestore Emulator", docSnap.exists())
        assertEquals("Master Kotlin", docSnap.getString("title"))
        assertEquals(1L, docSnap.getLong("version"))
        assertEquals(false, docSnap.getBoolean("isDeleted"))
    }

    // -------------------------------------------------------------------------
    // 2. Update a document using baseVersion
    // -------------------------------------------------------------------------
    @Test
    fun test02_updateDocumentUsingBaseVersion() = runBlocking {
        // First ensure version 1 exists
        val entry1 = OutboxEntry(
            operationId = "op-init-2",
            entityType = "Goal",
            entityId = "goal-sdk-2",
            operationType = OutboxOperationType.UPSERT,
            payload = """{"id":"goal-sdk-2","userId":"$activeUserId","title":"Initial Title"}""",
            createdAt = 1000L,
            retryCount = 0,
            lastAttemptAt = null,
            status = SyncStatus.PENDING,
            baseVersion = 0L
        )
        syncRepository.upload(activeUserId, entry1)

        // Update with baseVersion = 1L
        val entry2 = OutboxEntry(
            operationId = "op-update-2",
            entityType = "Goal",
            entityId = "goal-sdk-2",
            operationType = OutboxOperationType.UPSERT,
            payload = """{"id":"goal-sdk-2","userId":"$activeUserId","title":"Updated Title"}""",
            createdAt = 2000L,
            retryCount = 0,
            lastAttemptAt = null,
            status = SyncStatus.PENDING,
            baseVersion = 1L
        )

        val result = syncRepository.upload(activeUserId, entry2)
        assertTrue("Update must succeed", result is Result.Success)
        assertEquals(SyncUploadResult.Success, (result as Result.Success).data)

        val docSnap = firestore.collection("users").document(activeUserId).collection("goals").document("goal-sdk-2").get().await()
        assertEquals(2L, docSnap.getLong("version"))
        assertEquals("Updated Title", docSnap.getString("title"))
    }

    // -------------------------------------------------------------------------
    // 3. Upload stale data and verify conflict
    // -------------------------------------------------------------------------
    @Test
    fun test03_uploadStaleDataAndVerifyConflict() = runBlocking {
        // Setup document at version 2
        val docRef = firestore.collection("users").document(activeUserId).collection("goals").document("goal-sdk-3")
        docRef.set(mapOf("id" to "goal-sdk-3", "userId" to activeUserId, "title" to "Server Master", "version" to 2L, "isDeleted" to false)).await()

        // Stale upload with baseVersion = 1L
        val staleEntry = OutboxEntry(
            operationId = "op-stale-3",
            entityType = "Goal",
            entityId = "goal-sdk-3",
            operationType = OutboxOperationType.UPSERT,
            payload = """{"id":"goal-sdk-3","userId":"$activeUserId","title":"Stale Local"}""",
            createdAt = 3000L,
            retryCount = 0,
            lastAttemptAt = null,
            status = SyncStatus.PENDING,
            baseVersion = 1L
        )

        val result = syncRepository.upload(activeUserId, staleEntry)
        assertTrue("Upload must return Result.Success containing Conflict", result is Result.Success)
        val uploadResult = (result as Result.Success).data
        assertTrue("Must be Conflict result", uploadResult is SyncUploadResult.Conflict)

        val conflict = uploadResult as SyncUploadResult.Conflict
        assertTrue(conflict.conflictReason.contains("newer than expected baseVersion"))
    }

    // -------------------------------------------------------------------------
    // 4. Resolve USE_LOCAL
    // -------------------------------------------------------------------------
    @Test
    fun test04_resolveUseLocalRebasesAndUploadsSuccessfully() = runBlocking {
        val entityId = "goal-sdk-4"
        val opId = "op-use-local"
        val docRef = firestore.collection("users").document(activeUserId).collection("goals").document(entityId)
        docRef.set(mapOf("id" to entityId, "userId" to activeUserId, "title" to "Remote Version 2", "version" to 2L, "isDeleted" to false)).await()

        // Insert local outbox record stuck in CONFLICT
        outboxDao.insertEntry(
            OutboxEntity(
                operationId = opId,
                entityType = "Goal",
                entityId = entityId,
                operationType = "UPSERT",
                payload = """{"id":"$entityId","userId":"$activeUserId","title":"Local Wins"}""",
                createdAt = 1000L,
                retryCount = 1,
                lastAttemptAt = null,
                status = SyncStatus.CONFLICT.name,
                userId = activeUserId,
                baseVersion = 1L
            )
        )

        val conflictId = "conf-sdk-4"
        syncConflictDao.insertConflict(
            SyncConflictEntity(
                conflictId = conflictId,
                operationId = opId,
                userId = activeUserId,
                entityType = "Goal",
                entityId = entityId,
                localPayload = """{"id":"$entityId","userId":"$activeUserId","title":"Local Wins"}""",
                remotePayload = """{"id":"$entityId","userId":"$activeUserId","title":"Remote Version 2","version":2}""",
                conflictReason = "Remote version 2 is newer",
                createdAt = 1000L,
                updatedAt = 1000L,
                resolutionStatus = "UNRESOLVED"
            )
        )

        // Resolve via USE_LOCAL
        val resolveRes = conflictRepo.resolveConflictForUser(conflictId, activeUserId, ConflictResolutionStrategy.USE_LOCAL, "user")
        assertTrue(resolveRes is Result.Success)

        // Verify outbox entry was rebased to baseVersion = 2L and status = PENDING
        val rebasedEntry = outboxDao.getEntryByOperationIdForUser(opId, activeUserId)
        assertNotNull(rebasedEntry)
        assertEquals(2L, rebasedEntry?.baseVersion)
        assertEquals(SyncStatus.PENDING.name, rebasedEntry?.status)

        // Now upload rebased entry to Firestore Emulator
        val uploadResult = syncRepository.upload(activeUserId, rebasedEntry!!.toDomain())
        assertTrue(uploadResult is Result.Success)
        assertEquals(SyncUploadResult.Success, (uploadResult as Result.Success).data)

        // Document in Firestore Emulator must now have advanced to version = 3L
        val docSnap = docRef.get().await()
        assertEquals(3L, docSnap.getLong("version"))
        assertEquals("Local Wins", docSnap.getString("title"))
    }

    // -------------------------------------------------------------------------
    // 5. Resolve USE_REMOTE
    // -------------------------------------------------------------------------
    @Test
    fun test05_resolveUseRemoteConvergesRoomEntityAndDeletesOutbox() = runBlocking {
        val entityId = "goal-sdk-5"
        val opId = "op-use-remote"

        // Insert initial local Room entity
        ironMindDao.insertGoal(
            GoalEntity(
                id = entityId,
                userId = activeUserId,
                ambitionId = null,
                title = "Local Divergent Title",
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

        outboxDao.insertEntry(
            OutboxEntity(opId, "Goal", entityId, "UPSERT", """{"id":"$entityId","userId":"$activeUserId","title":"Local Divergent Title"}""", 1000L, 0, null, SyncStatus.CONFLICT.name, activeUserId)
        )

        val conflictId = "conf-sdk-5"
        syncConflictDao.insertConflict(
            SyncConflictEntity(
                conflictId = conflictId,
                operationId = opId,
                userId = activeUserId,
                entityType = "Goal",
                entityId = entityId,
                localPayload = """{"id":"$entityId","userId":"$activeUserId","title":"Local Divergent Title"}""",
                remotePayload = """{"id":"$entityId","userId":"$activeUserId","title":"Server Authority Title","importance":9,"status":"ACTIVE","version":5}""",
                conflictReason = "Remote version newer",
                createdAt = 1000L,
                updatedAt = 1000L,
                resolutionStatus = "UNRESOLVED"
            )
        )

        val resolveRes = conflictRepo.resolveConflictForUser(conflictId, activeUserId, ConflictResolutionStrategy.USE_REMOTE, "user")
        assertTrue(resolveRes is Result.Success)

        // Verify Room converged to remote title
        val localGoal = ironMindDao.getGoal(entityId)
        assertNotNull(localGoal)
        assertEquals("Server Authority Title", localGoal?.title)
        assertEquals(9, localGoal?.importance)

        // Verify outbox entry discarded
        assertNull(outboxDao.getEntryByOperationIdForUser(opId, activeUserId))
        assertEquals("RESOLVED_REMOTE", syncConflictDao.getConflictByIdForUser(conflictId, activeUserId)?.resolutionStatus)
    }

    // -------------------------------------------------------------------------
    // 6. Handle remote tombstone
    // -------------------------------------------------------------------------
    @Test
    fun test06_handleRemoteTombstoneDeletesRoomEntityWithoutResurrecting() = runBlocking {
        val entityId = "goal-sdk-6"
        val opId = "op-tombstone"

        // 1. Delete entity in Firestore Emulator to create remote tombstone
        val deleteEntry = OutboxEntry(
            operationId = "op-del-server",
            entityType = "Goal",
            entityId = entityId,
            operationType = OutboxOperationType.DELETE,
            payload = """{"id":"$entityId","userId":"$activeUserId"}""",
            createdAt = 1000L,
            retryCount = 0,
            lastAttemptAt = null,
            status = SyncStatus.PENDING,
            baseVersion = 1L
        )
        syncRepository.upload(activeUserId, deleteEntry)

        // Verify tombstone on emulator
        val docSnap = firestore.collection("users").document(activeUserId).collection("goals").document(entityId).get().await()
        assertEquals(true, docSnap.getBoolean("isDeleted"))
        val tombstoneVersion = docSnap.getLong("version") ?: 2L

        // 2. Pre-insert local Goal in Room
        ironMindDao.insertGoal(
            GoalEntity(
                id = entityId,
                userId = activeUserId,
                ambitionId = null,
                title = "Stale Local Goal",
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
        assertNotNull(ironMindDao.getGoal(entityId))

        // 3. Stale client attempts update -> triggers conflict
        val staleEntry = OutboxEntry(
            operationId = opId,
            entityType = "Goal",
            entityId = entityId,
            operationType = OutboxOperationType.UPSERT,
            payload = """{"id":"$entityId","userId":"$activeUserId","title":"Stale Local Goal"}""",
            createdAt = 2000L,
            retryCount = 0,
            lastAttemptAt = null,
            status = SyncStatus.PENDING,
            baseVersion = 1L
        )
        val uploadRes = syncRepository.upload(activeUserId, staleEntry)
        assertTrue(uploadRes is Result.Success && (uploadRes as Result.Success).data is SyncUploadResult.Conflict)

        // 4. Resolve USE_REMOTE with tombstone payload
        val conflictId = "conf-sdk-6"
        syncConflictDao.insertConflict(
            SyncConflictEntity(
                conflictId = conflictId,
                operationId = opId,
                userId = activeUserId,
                entityType = "Goal",
                entityId = entityId,
                localPayload = staleEntry.payload,
                remotePayload = """{"id":"$entityId","userId":"$activeUserId","isDeleted":true,"version":$tombstoneVersion}""",
                conflictReason = "Remote is tombstone",
                createdAt = 2000L,
                updatedAt = 2000L,
                resolutionStatus = "UNRESOLVED"
            )
        )

        val resolveRes = conflictRepo.resolveConflictForUser(conflictId, activeUserId, ConflictResolutionStrategy.USE_REMOTE, "user")
        assertTrue(resolveRes is Result.Success)

        // Must be deleted from Room, NOT resurrected! (SEC-01 Verified)
        assertNull("Local Goal must be deleted upon remote tombstone", ironMindDao.getGoal(entityId))
    }

    // -------------------------------------------------------------------------
    // 7. Verify Memory conflict resolution
    // -------------------------------------------------------------------------
    @Test
    fun test07_verifyMemoryActiveAndTombstoneResolution() = runBlocking {
        val memId = "mem-sdk-7"
        val opId = "op-mem-7"

        // Active Memory resolution
        val confActiveId = "conf-mem-act"
        syncConflictDao.insertConflict(
            SyncConflictEntity(
                conflictId = confActiveId,
                operationId = opId,
                userId = activeUserId,
                entityType = "Memory",
                entityId = memId,
                localPayload = """{"id":"$memId","userId":"$activeUserId","content":"Local Memory"}""",
                remotePayload = """{"id":"$memId","userId":"$activeUserId","content":"Remote Truth Memory","type":"INSIGHT","source":"SYSTEM","confidence":0.9,"evidenceCount":3,"confirmationState":"CONFIRMED","status":"ACTIVE","version":3}""",
                conflictReason = "Remote version newer",
                createdAt = 1000L,
                updatedAt = 1000L,
                resolutionStatus = "UNRESOLVED"
            )
        )

        val resActive = conflictRepo.resolveConflictForUser(confActiveId, activeUserId, ConflictResolutionStrategy.USE_REMOTE, "user")
        assertTrue(resActive is Result.Success)
        val activeMem = ironMindDao.getMemoryById(memId)
        assertNotNull(activeMem)
        assertEquals("Remote Truth Memory", activeMem?.content)

        // Tombstone Memory resolution
        val confTombId = "conf-mem-tomb"
        syncConflictDao.insertConflict(
            SyncConflictEntity(
                conflictId = confTombId,
                operationId = "op-mem-tomb",
                userId = activeUserId,
                entityType = "Memory",
                entityId = memId,
                localPayload = """{"id":"$memId","userId":"$activeUserId"}""",
                remotePayload = """{"id":"$memId","userId":"$activeUserId","isDeleted":true,"version":4}""",
                conflictReason = "Remote tombstone",
                createdAt = 2000L,
                updatedAt = 2000L,
                resolutionStatus = "UNRESOLVED"
            )
        )

        val resTomb = conflictRepo.resolveConflictForUser(confTombId, activeUserId, ConflictResolutionStrategy.USE_REMOTE, "user")
        assertTrue(resTomb is Result.Success)
        assertNull("Memory must be deleted upon tombstone resolution", ironMindDao.getMemoryById(memId))
    }

    // -------------------------------------------------------------------------
    // 8. Verify cross-user isolation
    // -------------------------------------------------------------------------
    @Test
    fun test08_verifyCrossUserIsolationBlockedBySecurityRules() = runBlocking {
        val otherUserId = "unauthorized-other-user-${UUID.randomUUID()}"
        val entry = OutboxEntry(
            operationId = "op-cross-user",
            entityType = "Goal",
            entityId = "goal-cross",
            operationType = OutboxOperationType.UPSERT,
            payload = """{"id":"goal-cross","userId":"$otherUserId","title":"Steal Data"}""",
            createdAt = 1000L,
            retryCount = 0,
            lastAttemptAt = null,
            status = SyncStatus.PENDING,
            baseVersion = 0L
        )

        // Attempt to upload under otherUserId path while signed in as activeUserId
        val result = syncRepository.upload(otherUserId, entry)
        assertTrue("Cross-user upload must fail: $result", result is Result.Failure)
        val error = (result as Result.Failure).error
        assertTrue(
            "Security rules must deny cross-user access (PERMISSION_DENIED)",
            error.message?.contains("PERMISSION_DENIED") == true
        )
    }

    // -------------------------------------------------------------------------
    // 9. Verify malformed version handling
    // -------------------------------------------------------------------------
    @Test
    fun test09_verifyMalformedVersionHandlingQuarantinesAsConflict() = runBlocking {
        val entityId = "goal-corrupt-9"
        val docRef = firestore.collection("users").document(activeUserId).collection("goals").document(entityId)

        // Manually write unversioned document lacking version and updatedAt
        docRef.set(mapOf("id" to entityId, "userId" to activeUserId, "title" to "Raw Corrupt Doc")).await()

        val entry = OutboxEntry(
            operationId = "op-corrupt-9",
            entityType = "Goal",
            entityId = entityId,
            operationType = OutboxOperationType.UPSERT,
            payload = """{"id":"$entityId","userId":"$activeUserId","title":"Valid Local"}""",
            createdAt = 1000L,
            retryCount = 0,
            lastAttemptAt = null,
            status = SyncStatus.PENDING,
            baseVersion = 0L
        )

        val result = syncRepository.upload(activeUserId, entry)
        assertTrue("Must succeed with conflict detection: $result", result is Result.Success)
        val uploadResult = (result as Result.Success).data
        assertTrue("Must be Conflict", uploadResult is SyncUploadResult.Conflict)
        val conflict = uploadResult as SyncUploadResult.Conflict
        assertTrue(
            "Conflict reason must identify unversioned/corrupt document",
            conflict.conflictReason.contains("unversioned/corrupt")
        )
    }

    // -------------------------------------------------------------------------
    // 10. Verify repeated upload behavior
    // -------------------------------------------------------------------------
    @Test
    fun test10_verifyRepeatedUploadBehavior() = runBlocking {
        val entityId = "goal-repeat-10"
        val entry = OutboxEntry(
            operationId = "op-repeat-10",
            entityType = "Goal",
            entityId = entityId,
            operationType = OutboxOperationType.UPSERT,
            payload = """{"id":"$entityId","userId":"$activeUserId","title":"Initial Commit"}""",
            createdAt = 1000L,
            retryCount = 0,
            lastAttemptAt = null,
            status = SyncStatus.PENDING,
            baseVersion = 0L
        )

        // First upload succeeds (version advances to 1L)
        val result1 = syncRepository.upload(activeUserId, entry)
        assertTrue(result1 is Result.Success)
        assertEquals(SyncUploadResult.Success, (result1 as Result.Success).data)

        // Repeated upload with the same baseVersion = 0L without advancing
        val result2 = syncRepository.upload(activeUserId, entry)
        assertTrue(result2 is Result.Success)
        val uploadResult2 = (result2 as Result.Success).data
        assertTrue(
            "Repeated upload with stale baseVersion must return Conflict",
            uploadResult2 is SyncUploadResult.Conflict
        )
        val conflict = uploadResult2 as SyncUploadResult.Conflict
        assertTrue(conflict.conflictReason.contains("Remote version (1) is newer than expected baseVersion (0)"))
    }

    // -------------------------------------------------------------------------
    // 11. Commitment: Create and Monotonic Version Advancement
    // -------------------------------------------------------------------------
    @Test
    fun test11_commitmentCreateAndMonotonicVersionAdvancement() = runBlocking {
        val entityId = "comm-sdk-11"
        val entry1 = OutboxEntry(
            operationId = "op-comm-create-11",
            entityType = "Commitment",
            entityId = entityId,
            operationType = OutboxOperationType.UPSERT,
            payload = """{"id":"$entityId","userId":"$activeUserId","title":"Morning Workout","description":"Pushups and running","committedAt":1000,"status":"COMMITTED","priority":2,"source":"USER","createdAt":1000,"updatedAt":1000,"schemaVersion":1}""",
            createdAt = 1000L,
            retryCount = 0,
            lastAttemptAt = null,
            status = SyncStatus.PENDING,
            baseVersion = 0L
        )

        val result1 = syncRepository.upload(activeUserId, entry1)
        assertTrue("Initial upload must succeed: $result1", result1 is Result.Success)
        assertEquals(SyncUploadResult.Success, (result1 as Result.Success).data)

        // Verify document directly in Firestore Emulator at users/{uid}/commitments/{id}
        val docSnap1 = firestore.collection("users").document(activeUserId).collection("commitments").document(entityId).get().await()
        assertTrue("Commitment document must exist in Firestore Emulator", docSnap1.exists())
        assertEquals("Morning Workout", docSnap1.getString("title"))
        assertEquals("Pushups and running", docSnap1.getString("description"))
        assertEquals(activeUserId, docSnap1.getString("userId"))
        assertEquals(1L, docSnap1.getLong("version"))
        assertEquals(false, docSnap1.getBoolean("isDeleted"))

        // Update with baseVersion = 1L -> advances to version 2L
        val entry2 = OutboxEntry(
            operationId = "op-comm-update-11",
            entityType = "Commitment",
            entityId = entityId,
            operationType = OutboxOperationType.UPSERT,
            payload = """{"id":"$entityId","userId":"$activeUserId","title":"Morning Workout Extended","description":"Pushups, pullups and running","committedAt":1000,"status":"COMMITTED","priority":3,"source":"USER","createdAt":1000,"updatedAt":2000,"schemaVersion":1}""",
            createdAt = 2000L,
            retryCount = 0,
            lastAttemptAt = null,
            status = SyncStatus.PENDING,
            baseVersion = 1L
        )

        val result2 = syncRepository.upload(activeUserId, entry2)
        assertTrue("Update must succeed: $result2", result2 is Result.Success)
        assertEquals(SyncUploadResult.Success, (result2 as Result.Success).data)

        val docSnap2 = firestore.collection("users").document(activeUserId).collection("commitments").document(entityId).get().await()
        assertEquals(2L, docSnap2.getLong("version"))
        assertEquals("Morning Workout Extended", docSnap2.getString("title"))
        assertEquals(3L, docSnap2.getLong("priority"))
    }

    // -------------------------------------------------------------------------
    // 12. Commitment: Stale baseVersion Conflict Detection and USE_LOCAL Resolution
    // -------------------------------------------------------------------------
    @Test
    fun test12_commitmentStaleBaseVersionAndConflictResolution() = runBlocking {
        val entityId = "comm-sdk-12"
        val opId = "op-comm-local-12"
        val docRef = firestore.collection("users").document(activeUserId).collection("commitments").document(entityId)

        // Seed remote document at version 2L
        docRef.set(mapOf(
            "id" to entityId,
            "userId" to activeUserId,
            "title" to "Server Commitment v2",
            "description" to "Server Authority",
            "committedAt" to 1000L,
            "status" to "COMMITTED",
            "priority" to 1,
            "version" to 2L,
            "isDeleted" to false
        )).await()

        // Local client attempts stale upload with baseVersion = 1L
        val staleEntry = OutboxEntry(
            operationId = opId,
            entityType = "Commitment",
            entityId = entityId,
            operationType = OutboxOperationType.UPSERT,
            payload = """{"id":"$entityId","userId":"$activeUserId","title":"Local Wins Commitment","description":"Local Client Authority","committedAt":1000,"status":"COMMITTED","priority":4,"source":"USER","createdAt":1000,"updatedAt":2500,"schemaVersion":1}""",
            createdAt = 2500L,
            retryCount = 0,
            lastAttemptAt = null,
            status = SyncStatus.PENDING,
            baseVersion = 1L
        )

        val conflictResult = syncRepository.upload(activeUserId, staleEntry)
        assertTrue("Stale upload must result in conflict", conflictResult is Result.Success)
        val uploadData = (conflictResult as Result.Success).data
        assertTrue("Must be SyncUploadResult.Conflict", uploadData is SyncUploadResult.Conflict)
        val conflictData = uploadData as SyncUploadResult.Conflict
        assertTrue(
            "Conflict reason must state remote is newer than baseVersion",
            conflictData.conflictReason.contains("newer than expected baseVersion")
        )

        // Persist local conflict and outbox entry in Room
        outboxDao.insertEntry(
            OutboxEntity(
                operationId = opId,
                entityType = "Commitment",
                entityId = entityId,
                operationType = "UPSERT",
                payload = staleEntry.payload,
                createdAt = 2500L,
                retryCount = 1,
                lastAttemptAt = null,
                status = SyncStatus.CONFLICT.name,
                userId = activeUserId,
                baseVersion = 1L
            )
        )

        val conflictId = "conf-comm-12"
        syncConflictDao.insertConflict(
            SyncConflictEntity(
                conflictId = conflictId,
                operationId = opId,
                userId = activeUserId,
                entityType = "Commitment",
                entityId = entityId,
                localPayload = staleEntry.payload,
                remotePayload = conflictData.remotePayload,
                conflictReason = conflictData.conflictReason,
                createdAt = 2500L,
                updatedAt = 2500L,
                resolutionStatus = "UNRESOLVED"
            )
        )

        // Resolve conflict via USE_LOCAL
        val resolveRes = conflictRepo.resolveConflictForUser(conflictId, activeUserId, ConflictResolutionStrategy.USE_LOCAL, "user")
        assertTrue(resolveRes is Result.Success)

        // Verify outbox entry rebased: baseVersion = 2L, status = PENDING
        val rebasedEntry = outboxDao.getEntryByOperationIdForUser(opId, activeUserId)
        assertNotNull(rebasedEntry)
        assertEquals(2L, rebasedEntry?.baseVersion)
        assertEquals(SyncStatus.PENDING.name, rebasedEntry?.status)

        // Re-upload rebased entry to Firestore Emulator
        val uploadResult = syncRepository.upload(activeUserId, rebasedEntry!!.toDomain())
        assertTrue(uploadResult is Result.Success)
        assertEquals(SyncUploadResult.Success, (uploadResult as Result.Success).data)

        // Verify document advanced to version 3L in Firestore Emulator with local values
        val docSnap = docRef.get().await()
        assertEquals(3L, docSnap.getLong("version"))
        assertEquals("Local Wins Commitment", docSnap.getString("title"))
        assertEquals(4L, docSnap.getLong("priority"))
    }

    // -------------------------------------------------------------------------
    // 13. Commitment: USE_REMOTE Convergence and Remote Tombstone Deletion
    // -------------------------------------------------------------------------
    @Test
    fun test13_commitmentUseRemoteConvergenceAndTombstone() = runBlocking {
        val entityId = "comm-sdk-13"
        val opIdActive = "op-comm-remote-13"

        // Step 1: Pre-insert divergent local Commitment in Room
        ironMindDao.insertCommitment(
            CommitmentEntity(
                id = entityId,
                userId = activeUserId,
                goalId = null,
                planId = null,
                taskId = null,
                title = "Local Divergent Commitment",
                description = "Local Description",
                committedAt = 1000L,
                scheduledStartAt = null,
                scheduledEndAt = null,
                status = "COMMITTED",
                priority = 1,
                source = "USER",
                createdAt = 1000L,
                updatedAt = 1000L,
                startedAt = null,
                completedAt = null,
                postponedAt = null,
                missedAt = null,
                recoveredAt = null,
                parentCommitmentId = null,
                schemaVersion = 1
            )
        )

        outboxDao.insertEntry(
            OutboxEntity(
                operationId = opIdActive,
                entityType = "Commitment",
                entityId = entityId,
                operationType = "UPSERT",
                payload = """{"id":"$entityId","userId":"$activeUserId","title":"Local Divergent Commitment"}""",
                createdAt = 1000L,
                retryCount = 1,
                lastAttemptAt = null,
                status = SyncStatus.CONFLICT.name,
                userId = activeUserId
            )
        )

        val confActiveId = "conf-comm-act-13"
        val remotePayloadActive = """{"id":"$entityId","userId":"$activeUserId","title":"Remote Authoritative Commitment","description":"Authoritative Remote Description","committedAt":1000,"status":"COMPLETED","priority":5,"source":"AI","createdAt":1000,"updatedAt":2000,"schemaVersion":1,"version":3}"""
        syncConflictDao.insertConflict(
            SyncConflictEntity(
                conflictId = confActiveId,
                operationId = opIdActive,
                userId = activeUserId,
                entityType = "Commitment",
                entityId = entityId,
                localPayload = """{"id":"$entityId","userId":"$activeUserId","title":"Local Divergent Commitment"}""",
                remotePayload = remotePayloadActive,
                conflictReason = "Remote version newer",
                createdAt = 1000L,
                updatedAt = 1000L,
                resolutionStatus = "UNRESOLVED"
            )
        )

        // Resolve active conflict via USE_REMOTE
        val resActive = conflictRepo.resolveConflictForUser(confActiveId, activeUserId, ConflictResolutionStrategy.USE_REMOTE, "user")
        assertTrue(resActive is Result.Success)

        // Verify Room entity converged to remote authoritative values
        val localComm = ironMindDao.getCommitment(entityId)
        assertNotNull("Local commitment must exist", localComm)
        assertEquals("Remote Authoritative Commitment", localComm?.title)
        assertEquals("Authoritative Remote Description", localComm?.description)
        assertEquals(5, localComm?.priority)
        assertEquals("COMPLETED", localComm?.status)
        assertEquals("AI", localComm?.source)
        assertNull("Outbox entry must be discarded", outboxDao.getEntryByOperationIdForUser(opIdActive, activeUserId))

        // Step 2: Remote Tombstone Deletion Handling (SEC-01)
        val deleteEntry = OutboxEntry(
            operationId = "op-comm-del-13",
            entityType = "Commitment",
            entityId = entityId,
            operationType = OutboxOperationType.DELETE,
            payload = """{"id":"$entityId","userId":"$activeUserId"}""",
            createdAt = 3000L,
            retryCount = 0,
            lastAttemptAt = null,
            status = SyncStatus.PENDING,
            baseVersion = 3L
        )
        val deleteResult = syncRepository.upload(activeUserId, deleteEntry)
        assertTrue(deleteResult is Result.Success)

        val docSnap = firestore.collection("users").document(activeUserId).collection("commitments").document(entityId).get().await()
        assertEquals(true, docSnap.getBoolean("isDeleted"))
        val tombstoneVersion = docSnap.getLong("version") ?: 4L

        // Local state still has the commitment
        assertNotNull(ironMindDao.getCommitment(entityId))

        // Conflict record received with remote tombstone payload
        val confTombId = "conf-comm-tomb-13"
        val opIdTomb = "op-comm-stale-after-del"
        outboxDao.insertEntry(
            OutboxEntity(
                operationId = opIdTomb,
                entityType = "Commitment",
                entityId = entityId,
                operationType = "UPSERT",
                payload = """{"id":"$entityId","userId":"$activeUserId","title":"Resurrect Local"}""",
                createdAt = 3500L,
                retryCount = 1,
                lastAttemptAt = null,
                status = SyncStatus.CONFLICT.name,
                userId = activeUserId
            )
        )
        syncConflictDao.insertConflict(
            SyncConflictEntity(
                conflictId = confTombId,
                operationId = opIdTomb,
                userId = activeUserId,
                entityType = "Commitment",
                entityId = entityId,
                localPayload = """{"id":"$entityId","userId":"$activeUserId","title":"Resurrect Local"}""",
                remotePayload = """{"id":"$entityId","userId":"$activeUserId","isDeleted":true,"version":$tombstoneVersion}""",
                conflictReason = "Remote is tombstone",
                createdAt = 3500L,
                updatedAt = 3500L,
                resolutionStatus = "UNRESOLVED"
            )
        )

        // Resolve tombstone via USE_REMOTE
        val resTomb = conflictRepo.resolveConflictForUser(confTombId, activeUserId, ConflictResolutionStrategy.USE_REMOTE, "user")
        assertTrue(resTomb is Result.Success)

        // Verify Room entity is DELETED and NOT resurrected
        assertNull("Commitment must be deleted in Room upon remote tombstone USE_REMOTE resolution", ironMindDao.getCommitment(entityId))
        assertNull("Outbox entry must be discarded", outboxDao.getEntryByOperationIdForUser(opIdTomb, activeUserId))
    }

    // -------------------------------------------------------------------------
    // 14. Reflection: Create and Monotonic Version Advancement
    // -------------------------------------------------------------------------
    @Test
    fun test14_reflectionCreateAndMonotonicVersionAdvancement() = runBlocking {
        val entityId = "refl-sdk-14"
        val entry1 = OutboxEntry(
            operationId = "op-refl-create-14",
            entityType = "Reflection",
            entityId = entityId,
            operationType = OutboxOperationType.UPSERT,
            payload = """{"id":"$entityId","userId":"$activeUserId","targetEntityId":"goal-sdk-14","targetEntityType":"Goal","content":"High focus achieved during deep work.","sentiment":"POSITIVE","createdAt":1000,"schemaVersion":1}""",
            createdAt = 1000L,
            retryCount = 0,
            lastAttemptAt = null,
            status = SyncStatus.PENDING,
            baseVersion = 0L
        )

        val result1 = syncRepository.upload(activeUserId, entry1)
        assertTrue("Initial upload must succeed: $result1", result1 is Result.Success)
        assertEquals(SyncUploadResult.Success, (result1 as Result.Success).data)

        // Verify document directly in Firestore Emulator at users/{uid}/reflections/{id}
        val docSnap1 = firestore.collection("users").document(activeUserId).collection("reflections").document(entityId).get().await()
        assertTrue("Reflection document must exist in Firestore Emulator", docSnap1.exists())
        assertEquals("High focus achieved during deep work.", docSnap1.getString("content"))
        assertEquals("POSITIVE", docSnap1.getString("sentiment"))
        assertEquals("goal-sdk-14", docSnap1.getString("targetEntityId"))
        assertEquals("Goal", docSnap1.getString("targetEntityType"))
        assertEquals(activeUserId, docSnap1.getString("userId"))
        assertEquals(1L, docSnap1.getLong("version"))
        assertEquals(false, docSnap1.getBoolean("isDeleted"))

        // Update with baseVersion = 1L -> advances to version 2L
        val entry2 = OutboxEntry(
            operationId = "op-refl-update-14",
            entityType = "Reflection",
            entityId = entityId,
            operationType = OutboxOperationType.UPSERT,
            payload = """{"id":"$entityId","userId":"$activeUserId","targetEntityId":"goal-sdk-14","targetEntityType":"Goal","content":"High focus achieved during deep work. Extended into evening.","sentiment":"VERY_POSITIVE","createdAt":1000,"schemaVersion":1}""",
            createdAt = 2000L,
            retryCount = 0,
            lastAttemptAt = null,
            status = SyncStatus.PENDING,
            baseVersion = 1L
        )

        val result2 = syncRepository.upload(activeUserId, entry2)
        assertTrue("Update must succeed: $result2", result2 is Result.Success)
        assertEquals(SyncUploadResult.Success, (result2 as Result.Success).data)

        val docSnap2 = firestore.collection("users").document(activeUserId).collection("reflections").document(entityId).get().await()
        assertEquals(2L, docSnap2.getLong("version"))
        assertEquals("High focus achieved during deep work. Extended into evening.", docSnap2.getString("content"))
        assertEquals("VERY_POSITIVE", docSnap2.getString("sentiment"))
    }

    // -------------------------------------------------------------------------
    // 15. Reflection: Stale baseVersion Conflict Detection and USE_LOCAL Resolution
    // -------------------------------------------------------------------------
    @Test
    fun test15_reflectionStaleBaseVersionAndConflictResolution() = runBlocking {
        val entityId = "refl-sdk-15"
        val opId = "op-refl-local-15"
        val docRef = firestore.collection("users").document(activeUserId).collection("reflections").document(entityId)

        // Seed remote document at version 2L
        docRef.set(mapOf(
            "id" to entityId,
            "userId" to activeUserId,
            "content" to "Server Reflection v2",
            "sentiment" to "NEUTRAL",
            "version" to 2L,
            "isDeleted" to false
        )).await()

        // Local client attempts stale upload with baseVersion = 1L
        val staleEntry = OutboxEntry(
            operationId = opId,
            entityType = "Reflection",
            entityId = entityId,
            operationType = OutboxOperationType.UPSERT,
            payload = """{"id":"$entityId","userId":"$activeUserId","content":"Local Reflection Overwrite","sentiment":"POSITIVE","createdAt":1000,"schemaVersion":1}""",
            createdAt = 2500L,
            retryCount = 0,
            lastAttemptAt = null,
            status = SyncStatus.PENDING,
            baseVersion = 1L
        )

        val conflictResult = syncRepository.upload(activeUserId, staleEntry)
        assertTrue("Stale upload must result in conflict", conflictResult is Result.Success)
        val uploadData = (conflictResult as Result.Success).data
        assertTrue("Must be SyncUploadResult.Conflict", uploadData is SyncUploadResult.Conflict)
        val conflictData = uploadData as SyncUploadResult.Conflict
        assertTrue(
            "Conflict reason must state remote is newer than baseVersion",
            conflictData.conflictReason.contains("newer than expected baseVersion")
        )

        // Persist local conflict and outbox entry in Room
        outboxDao.insertEntry(
            OutboxEntity(
                operationId = opId,
                entityType = "Reflection",
                entityId = entityId,
                operationType = "UPSERT",
                payload = staleEntry.payload,
                createdAt = 2500L,
                retryCount = 1,
                lastAttemptAt = null,
                status = SyncStatus.CONFLICT.name,
                userId = activeUserId,
                baseVersion = 1L
            )
        )

        val conflictId = "conf-refl-15"
        syncConflictDao.insertConflict(
            SyncConflictEntity(
                conflictId = conflictId,
                operationId = opId,
                userId = activeUserId,
                entityType = "Reflection",
                entityId = entityId,
                localPayload = staleEntry.payload,
                remotePayload = conflictData.remotePayload,
                conflictReason = conflictData.conflictReason,
                createdAt = 2500L,
                updatedAt = 2500L,
                resolutionStatus = "UNRESOLVED"
            )
        )

        // Resolve conflict via USE_LOCAL
        val resolveRes = conflictRepo.resolveConflictForUser(conflictId, activeUserId, ConflictResolutionStrategy.USE_LOCAL, "user")
        assertTrue(resolveRes is Result.Success)

        // Verify outbox entry rebased: baseVersion = 2L, status = PENDING
        val rebasedEntry = outboxDao.getEntryByOperationIdForUser(opId, activeUserId)
        assertNotNull(rebasedEntry)
        assertEquals(2L, rebasedEntry?.baseVersion)
        assertEquals(SyncStatus.PENDING.name, rebasedEntry?.status)

        // Re-upload rebased entry to Firestore Emulator
        val uploadResult = syncRepository.upload(activeUserId, rebasedEntry!!.toDomain())
        assertTrue(uploadResult is Result.Success)
        assertEquals(SyncUploadResult.Success, (uploadResult as Result.Success).data)

        // Verify document advanced to version 3L in Firestore Emulator with local values
        val docSnap = docRef.get().await()
        assertEquals(3L, docSnap.getLong("version"))
        assertEquals("Local Reflection Overwrite", docSnap.getString("content"))
        assertEquals("POSITIVE", docSnap.getString("sentiment"))
    }

    // -------------------------------------------------------------------------
    // 16. Reflection: USE_REMOTE Convergence and Remote Tombstone Deletion
    // -------------------------------------------------------------------------
    @Test
    fun test16_reflectionUseRemoteConvergenceAndTombstone() = runBlocking {
        val entityId = "refl-sdk-16"
        val opIdActive = "op-refl-remote-16"

        // Step 1: Pre-insert divergent local Reflection in Room
        ironMindDao.insertReflection(
            ReflectionEntity(
                id = entityId,
                userId = activeUserId,
                targetEntityId = "goal-16",
                targetEntityType = "Goal",
                content = "Local Divergent Reflection",
                sentiment = "NEGATIVE",
                createdAt = 1000L,
                schemaVersion = 1
            )
        )

        outboxDao.insertEntry(
            OutboxEntity(
                operationId = opIdActive,
                entityType = "Reflection",
                entityId = entityId,
                operationType = "UPSERT",
                payload = """{"id":"$entityId","userId":"$activeUserId","content":"Local Divergent Reflection"}""",
                createdAt = 1000L,
                retryCount = 1,
                lastAttemptAt = null,
                status = SyncStatus.CONFLICT.name,
                userId = activeUserId
            )
        )

        val confActiveId = "conf-refl-act-16"
        val remotePayloadActive = """{"id":"$entityId","userId":"$activeUserId","targetEntityId":"goal-16","targetEntityType":"Goal","content":"Server Master Reflection Truth","sentiment":"HIGHLY_POSITIVE","createdAt":1000,"schemaVersion":1,"version":3}"""
        syncConflictDao.insertConflict(
            SyncConflictEntity(
                conflictId = confActiveId,
                operationId = opIdActive,
                userId = activeUserId,
                entityType = "Reflection",
                entityId = entityId,
                localPayload = """{"id":"$entityId","userId":"$activeUserId","content":"Local Divergent Reflection"}""",
                remotePayload = remotePayloadActive,
                conflictReason = "Remote version newer",
                createdAt = 1000L,
                updatedAt = 1000L,
                resolutionStatus = "UNRESOLVED"
            )
        )

        // Resolve active conflict via USE_REMOTE
        val resActive = conflictRepo.resolveConflictForUser(confActiveId, activeUserId, ConflictResolutionStrategy.USE_REMOTE, "user")
        assertTrue(resActive is Result.Success)

        // Verify Room entity converged to remote authoritative values
        val localRefl = ironMindDao.getReflection(entityId)
        assertNotNull("Local reflection must exist", localRefl)
        assertEquals("Server Master Reflection Truth", localRefl?.content)
        assertEquals("HIGHLY_POSITIVE", localRefl?.sentiment)
        assertNull("Outbox entry must be discarded", outboxDao.getEntryByOperationIdForUser(opIdActive, activeUserId))

        // Step 2: Remote Tombstone Deletion Handling (SEC-01)
        val deleteEntry = OutboxEntry(
            operationId = "op-refl-del-16",
            entityType = "Reflection",
            entityId = entityId,
            operationType = OutboxOperationType.DELETE,
            payload = """{"id":"$entityId","userId":"$activeUserId"}""",
            createdAt = 3000L,
            retryCount = 0,
            lastAttemptAt = null,
            status = SyncStatus.PENDING,
            baseVersion = 3L
        )
        val deleteResult = syncRepository.upload(activeUserId, deleteEntry)
        assertTrue(deleteResult is Result.Success)

        val docSnap = firestore.collection("users").document(activeUserId).collection("reflections").document(entityId).get().await()
        assertEquals(true, docSnap.getBoolean("isDeleted"))
        val tombstoneVersion = docSnap.getLong("version") ?: 4L

        // Local state still has the reflection
        assertNotNull(ironMindDao.getReflection(entityId))

        // Conflict record received with remote tombstone payload
        val confTombId = "conf-refl-tomb-16"
        val opIdTomb = "op-refl-stale-after-del"
        outboxDao.insertEntry(
            OutboxEntity(
                operationId = opIdTomb,
                entityType = "Reflection",
                entityId = entityId,
                operationType = "UPSERT",
                payload = """{"id":"$entityId","userId":"$activeUserId","content":"Resurrect Local Reflection"}""",
                createdAt = 3500L,
                retryCount = 1,
                lastAttemptAt = null,
                status = SyncStatus.CONFLICT.name,
                userId = activeUserId
            )
        )
        syncConflictDao.insertConflict(
            SyncConflictEntity(
                conflictId = confTombId,
                operationId = opIdTomb,
                userId = activeUserId,
                entityType = "Reflection",
                entityId = entityId,
                localPayload = """{"id":"$entityId","userId":"$activeUserId","content":"Resurrect Local Reflection"}""",
                remotePayload = """{"id":"$entityId","userId":"$activeUserId","isDeleted":true,"version":$tombstoneVersion}""",
                conflictReason = "Remote is tombstone",
                createdAt = 3500L,
                updatedAt = 3500L,
                resolutionStatus = "UNRESOLVED"
            )
        )

        // Resolve tombstone via USE_REMOTE
        val resTomb = conflictRepo.resolveConflictForUser(confTombId, activeUserId, ConflictResolutionStrategy.USE_REMOTE, "user")
        assertTrue(resTomb is Result.Success)

        // Verify Room entity is DELETED and NOT resurrected
        assertNull("Reflection must be deleted in Room upon remote tombstone USE_REMOTE resolution", ironMindDao.getReflection(entityId))
        assertNull("Outbox entry must be discarded", outboxDao.getEntryByOperationIdForUser(opIdTomb, activeUserId))
    }

    // -------------------------------------------------------------------------
    // 17. Commitment and Reflection: Cross-User Access Blocked by Security Rules
    // -------------------------------------------------------------------------
    @Test
    fun test17_commitmentAndReflectionCrossUserSecurityRulesDenied() = runBlocking {
        val otherUserId = "unauthorized-other-user-${UUID.randomUUID()}"

        // Commitment cross-user upload attempt
        val entryComm = OutboxEntry(
            operationId = "op-cross-comm-17",
            entityType = "Commitment",
            entityId = "comm-cross-17",
            operationType = OutboxOperationType.UPSERT,
            payload = """{"id":"comm-cross-17","userId":"$otherUserId","title":"Unauthorized Commitment","committedAt":1000,"status":"COMMITTED","priority":1,"source":"USER","createdAt":1000,"updatedAt":1000,"schemaVersion":1}""",
            createdAt = 1000L,
            retryCount = 0,
            lastAttemptAt = null,
            status = SyncStatus.PENDING,
            baseVersion = 0L
        )
        val resComm = syncRepository.upload(otherUserId, entryComm)
        assertTrue("Commitment cross-user upload must fail: $resComm", resComm is Result.Failure)
        val errorComm = (resComm as Result.Failure).error
        assertTrue(
            "Security rules must deny cross-user Commitment write (PERMISSION_DENIED)",
            errorComm.message?.contains("PERMISSION_DENIED") == true
        )

        // Reflection cross-user upload attempt
        val entryRefl = OutboxEntry(
            operationId = "op-cross-refl-17",
            entityType = "Reflection",
            entityId = "refl-cross-17",
            operationType = OutboxOperationType.UPSERT,
            payload = """{"id":"refl-cross-17","userId":"$otherUserId","content":"Unauthorized Reflection","createdAt":1000,"schemaVersion":1}""",
            createdAt = 1000L,
            retryCount = 0,
            lastAttemptAt = null,
            status = SyncStatus.PENDING,
            baseVersion = 0L
        )
        val resRefl = syncRepository.upload(otherUserId, entryRefl)
        assertTrue("Reflection cross-user upload must fail: $resRefl", resRefl is Result.Failure)
        val errorRefl = (resRefl as Result.Failure).error
        assertTrue(
            "Security rules must deny cross-user Reflection write (PERMISSION_DENIED)",
            errorRefl.message?.contains("PERMISSION_DENIED") == true
        )
    }

    // -------------------------------------------------------------------------
    // 18. Outbox Lease Fencing and Atomic Completion Contract
    // -------------------------------------------------------------------------
    @Test
    fun test18_outboxCompletionRequiresMatchingLeaseTokenAndFencing() = runBlocking {
        val opId = "op-lease-comm-18"
        val correctLease = "lease-token-alpha"
        val mismatchedLease = "lease-token-beta"

        // Insert entry with active lease token
        outboxDao.insertEntry(
            OutboxEntity(
                operationId = opId,
                entityType = "Commitment",
                entityId = "comm-lease-18",
                operationType = "UPSERT",
                payload = """{"id":"comm-lease-18","userId":"$activeUserId","title":"Leased Commitment"}""",
                createdAt = 1000L,
                retryCount = 0,
                lastAttemptAt = 1000L,
                status = "SYNCING",
                userId = activeUserId,
                baseVersion = 0L,
                leaseToken = correctLease,
                leaseExpiresAt = System.currentTimeMillis() + 60_000L
            )
        )

        // Attempt completion with mismatched lease token -> must fail (0 rows affected)
        val failedCompletion = outboxDao.completeEntryWithLease(opId, activeUserId, mismatchedLease)
        assertEquals("Mismatched lease token must not delete entry", 0, failedCompletion)
        assertNotNull("Entry must still exist in outbox", outboxDao.getEntryByOperationIdForUser(opId, activeUserId))

        // Complete with matching lease token -> succeeds (1 row deleted)
        val successCompletion = outboxDao.completeEntryWithLease(opId, activeUserId, correctLease)
        assertEquals("Matching lease token must delete entry upon completion", 1, successCompletion)
        assertNull("Entry must be deleted from outbox", outboxDao.getEntryByOperationIdForUser(opId, activeUserId))
    }
}
