package com.sanket_satpute_20.ironmind.data.sync

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import com.sanket_satpute_20.ironmind.data.local.IronMindDatabase
import com.sanket_satpute_20.ironmind.data.local.dao.IronMindDao
import com.sanket_satpute_20.ironmind.data.local.dao.OutboxDao
import com.sanket_satpute_20.ironmind.data.local.entity.GoalEntity
import com.sanket_satpute_20.ironmind.data.local.entity.OutboxEntity
import com.sanket_satpute_20.ironmind.data.repository.GoalRepositoryImpl
import com.sanket_satpute_20.ironmind.data.repository.OutboxRepositoryImpl
import com.sanket_satpute_20.ironmind.domain.common.IdGenerator
import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.Goal
import com.sanket_satpute_20.ironmind.domain.model.GoalStatus
import com.sanket_satpute_20.ironmind.domain.model.OutboxEntry
import com.sanket_satpute_20.ironmind.domain.model.OutboxOperationType
import com.sanket_satpute_20.ironmind.domain.model.SyncStatus
import com.sanket_satpute_20.ironmind.domain.session.SessionState
import com.sanket_satpute_20.ironmind.domain.session.UserSessionProvider
import com.sanket_satpute_20.ironmind.domain.usecase.sync.SyncUseCase
import com.sanket_satpute_20.ironmind.infrastructure.worker.SyncWorker
import com.sanket_satpute_20.ironmind.testutil.FakeUserSessionProvider
import com.sanket_satpute_20.ironmind.testutil.fake.FakeBackgroundExecutor
import com.sanket_satpute_20.ironmind.testutil.fake.FakeSyncRepository
import io.mockk.mockk
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
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
class GoalSyncIntegrationAndSchedulingTest {

    private lateinit var context: Context
    private lateinit var db: IronMindDatabase
    private lateinit var fakeBackgroundExecutor: FakeBackgroundExecutor
    private lateinit var repository: GoalRepositoryImpl
    private lateinit var fakeSyncRepository: FakeSyncRepository
    private lateinit var outboxRepository: OutboxRepositoryImpl
    private lateinit var syncOrchestrator: SyncOrchestrator
    private lateinit var syncUseCase: SyncUseCase
    private lateinit var workerParams: WorkerParameters

    private class FailingOutboxDao(private val delegate: OutboxDao) : OutboxDao by delegate {
        var shouldFail: Boolean = false
        override fun insertEntry(entry: OutboxEntity) {
            if (shouldFail) {
                throw android.database.sqlite.SQLiteException("Simulated outbox disk error")
            }
            delegate.insertEntry(entry)
        }
    }

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        workerParams = mockk(relaxed = true) {
            io.mockk.every { inputData } returns androidx.work.Data.Builder()
                .putLong(UserSessionProvider.KEY_SESSION_GENERATION, 1L)
                .build()
        }
        db = Room.inMemoryDatabaseBuilder(context, IronMindDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        fakeBackgroundExecutor = FakeBackgroundExecutor()
        fakeSyncRepository = FakeSyncRepository()
        outboxRepository = OutboxRepositoryImpl(db.outboxDao())
        syncOrchestrator = SyncOrchestrator(outboxRepository, fakeSyncRepository)
        syncUseCase = SyncUseCase(syncOrchestrator)

        val idGenerator = object : IdGenerator {
            private var count = 1
            override fun generateId(): String = "op-sync-${count++}"
        }

        repository = GoalRepositoryImpl(
            dao = db.ironMindDao(),
            outboxDao = db.outboxDao(),
            database = db,
            idGenerator = idGenerator,
            backgroundExecutor = fakeBackgroundExecutor
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun createTestGoal(
        id: String = "goal-sched-1",
        userId: String = "auth-uid-100",
        title: String = "Test Scheduling"
    ): Goal = Goal(
        id = id,
        userId = userId,
        ambitionId = null,
        title = title,
        description = "Description",
        why = "Motivation",
        importance = 7,
        status = GoalStatus.ACTIVE,
        targetAt = null,
        startedAt = 1000L,
        completedAt = null,
        createdAt = 1000L,
        updatedAt = 1000L
    )

    private fun createOutboxEntry(
        opId: String,
        goalId: String,
        userId: String,
        title: String = "Test Goal"
    ): OutboxEntry = OutboxEntry(
        operationId = opId,
        entityType = "Goal",
        entityId = goalId,
        operationType = OutboxOperationType.UPSERT,
        payload = """{"id":"$goalId","userId":"$userId","title":"$title","status":"ACTIVE","createdAt":1000,"updatedAt":1000}""",
        createdAt = 1000L,
        retryCount = 0,
        lastAttemptAt = null,
        status = SyncStatus.PENDING
    )

    // ── Test 1: Successful goal persistence requests sync scheduling ─────────────
    @Test
    fun `successful goal persistence requests sync scheduling exactly as intended`() = runTest {
        val goal = createTestGoal()

        val result = repository.saveGoal(goal)
        assertTrue("Goal saving must succeed", result is Result.Success)

        // Verify that scheduleSync was called
        assertTrue("scheduleSync must be requested after successful commit", fakeBackgroundExecutor.scheduleSyncCalled)

        // Verify local database state
        assertNotNull(db.ironMindDao().getGoal("goal-sched-1"))
        assertEquals(1, db.outboxDao().getPendingEntries().size)
    }

    // ── Test 2: Failed goal or outbox transaction does NOT schedule sync ─────────
    @Test
    fun `failed goal or outbox transaction does not schedule sync`() = runTest {
        val failingOutbox = FailingOutboxDao(db.outboxDao()).apply { shouldFail = true }
        val transactionalRepo = GoalRepositoryImpl(
            dao = db.ironMindDao(),
            outboxDao = failingOutbox,
            database = db,
            backgroundExecutor = fakeBackgroundExecutor
        )

        val result = transactionalRepo.saveGoal(createTestGoal("goal-fail", "auth-uid-100"))
        assertTrue("Goal saving must fail", result is Result.Failure)

        // Sync must NOT have been scheduled!
        assertTrue("scheduleSync must NOT be called if transaction fails", !fakeBackgroundExecutor.scheduleSyncCalled)

        // Nothing committed to Room
        assertNull(db.ironMindDao().getGoal("goal-fail"))
        assertTrue(db.outboxDao().getPendingEntries().isEmpty())
    }

    // ── Test 3: Scheduling failure does not erase committed goal or outbox entry ─
    @Test
    fun `scheduling failure does not erase committed goal or outbox entry`() = runTest {
        fakeBackgroundExecutor.shouldThrowException = true
        val goal = createTestGoal("goal-sched-fail", "auth-uid-100")

        val result = repository.saveGoal(goal)
        // Must succeed locally despite background scheduling exception
        assertTrue("Local save must return Success even if scheduling threw", result is Result.Success)

        // Both Goal and Outbox entry remain intact in Room
        assertNotNull("Goal must remain in Room", db.ironMindDao().getGoal("goal-sched-fail"))
        val pending = db.outboxDao().getPendingEntries()
        assertEquals("Outbox entry must remain PENDING for next sync run", 1, pending.size)
        assertEquals("goal-sched-fail", pending[0].entityId)
    }

    // ── Test 4: No authenticated session means no Firestore upload ───────────────
    @Test
    fun `no authenticated session defers safely without Firestore upload`() = runTest {
        // Prepare pending outbox entry created in offline fallback mode
        outboxRepository.insertEntry(createOutboxEntry("op-offline", "goal-1", "user-1"))

        // Unauthenticated session provider
        val sessionProvider = FakeUserSessionProvider(SessionState.OfflineFallback("user-1"))
        val syncWorker = SyncWorker(context, workerParams, syncUseCase, sessionProvider)

        val workResult = syncWorker.doWork()

        // SyncWorker should defer with retry without uploading
        assertEquals(ListenableWorker.Result.retry(), workResult)
        assertTrue("No remote uploads must be attempted without authenticated session", fakeSyncRepository.uploadedEntries.isEmpty())

        // Outbox entry must remain pending
        assertEquals(1, db.outboxDao().getPendingEntries().size)
    }

    // ── Test 5: Authenticated user can upload only that user's goal payload ───────
    @Test
    fun `authenticated user can upload only that user's goal payload`() = runTest {
        // Outbox contains entries for user A and user B
        outboxRepository.insertEntry(createOutboxEntry("op-userA", "goal-A", "auth-user-A", "User A Goal"))
        outboxRepository.insertEntry(createOutboxEntry("op-userB", "goal-B", "auth-user-B", "User B Goal"))

        // Sync runs for user A
        val syncResult = syncOrchestrator.sync("auth-user-A")
        assertTrue(syncResult is Result.Success)

        // Only user A's entry should have been uploaded
        assertEquals(1, fakeSyncRepository.uploadedEntries.size)
        assertEquals("op-userA", fakeSyncRepository.uploadedEntries[0].operationId)

        // user A's entry is deleted from outbox; user B's entry remains intact
        val remaining = db.outboxDao().getPendingEntries()
        assertEquals(1, remaining.size)
        assertEquals("op-userB", remaining[0].operationId)
    }

    // ── Test 6: Failed upload retains and retries outbox entry ────────────────────
    @Test
    fun `failed upload retains and retries outbox entry according to policy`() = runTest {
        outboxRepository.insertEntry(createOutboxEntry("op-fail-net", "goal-retry", "auth-user-A"))
        fakeSyncRepository.failingOperationIds.add("op-fail-net")

        val syncResult = syncOrchestrator.sync("auth-user-A")
        assertTrue(syncResult is Result.Success)

        // Outbox entry must remain, status FAILED, retryCount incremented
        val entries = db.outboxDao().getPendingEntries()
        assertEquals(1, entries.size)
        val entry = entries[0]
        assertEquals(SyncStatus.FAILED.name, entry.status)
        assertEquals(1, entry.retryCount)
    }

    // ── Test 7: Full SyncWorker execution succeeds when authenticated ────────────
    @Test
    fun `authenticated session executes full SyncWorker drain successfully`() = runTest {
        outboxRepository.insertEntry(createOutboxEntry("op-auth-drain", "goal-drain", "auth-user-A"))

        val sessionProvider = FakeUserSessionProvider(SessionState.Authenticated("auth-user-A", true))
        val syncWorker = SyncWorker(context, workerParams, syncUseCase, sessionProvider)

        val workResult = syncWorker.doWork()

        assertEquals(ListenableWorker.Result.success(), workResult)
        assertEquals(1, fakeSyncRepository.uploadedEntries.size)
        assertEquals("op-auth-drain", fakeSyncRepository.uploadedEntries[0].operationId)

        // Outbox must be empty after successful drain
        assertTrue(db.outboxDao().getPendingEntries().isEmpty())
    }

    // ── Test 8: Ambiguous or missing userId payload is quarantined to CONFLICT ─────
    @Test
    fun `ambiguous or missing userId payload is quarantined to CONFLICT and never uploaded`() = runTest {
        val entry = OutboxEntry(
            operationId = "op-no-user",
            entityType = "Goal",
            entityId = "g-no-user",
            operationType = OutboxOperationType.UPSERT,
            payload = """{"id":"g-no-user","title":"Missing User Field"}""",
            createdAt = 1000L,
            retryCount = 0,
            lastAttemptAt = null,
            status = SyncStatus.PENDING,
            userId = "auth-user-A"
        )
        outboxRepository.insertEntry(entry)

        val syncResult = syncOrchestrator.sync("auth-user-A")
        assertTrue(syncResult is Result.Success)

        // Must not be uploaded
        assertTrue("No entries should be uploaded for missing userId payload", fakeSyncRepository.uploadedEntries.isEmpty())

        // Must be quarantined to CONFLICT so it is excluded from future pending drains
        val allPending = db.outboxDao().getPendingEntries()
        assertTrue("Quarantined entry must not appear in pending list", allPending.none { it.operationId == "op-no-user" })
    }

    // ── Test 9: Malformed JSON payload is quarantined to CONFLICT and never uploaded ─
    @Test
    fun `malformed JSON payload is quarantined to CONFLICT and never uploaded`() = runTest {
        val entry = OutboxEntry(
            operationId = "op-malformed",
            entityType = "Goal",
            entityId = "g-malformed",
            operationType = OutboxOperationType.UPSERT,
            payload = "NOT_A_VALID_JSON{{{",
            createdAt = 1000L,
            retryCount = 0,
            lastAttemptAt = null,
            status = SyncStatus.PENDING,
            userId = "auth-user-A"
        )
        outboxRepository.insertEntry(entry)

        val syncResult = syncOrchestrator.sync("auth-user-A")
        assertTrue(syncResult is Result.Success)

        // Must not be uploaded
        assertTrue("No entries should be uploaded for malformed JSON payload", fakeSyncRepository.uploadedEntries.isEmpty())

        // Must be quarantined to CONFLICT
        val allPending = db.outboxDao().getPendingEntries()
        assertTrue("Quarantined entry must not appear in pending list", allPending.none { it.operationId == "op-malformed" })
    }
}
