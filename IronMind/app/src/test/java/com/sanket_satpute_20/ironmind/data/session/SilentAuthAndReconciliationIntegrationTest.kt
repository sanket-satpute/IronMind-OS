package com.sanket_satpute_20.ironmind.data.session

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.work.Data
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import com.sanket_satpute_20.ironmind.data.local.IronMindDatabase
import com.sanket_satpute_20.ironmind.data.repository.GoalRepositoryImpl
import com.sanket_satpute_20.ironmind.data.repository.OutboxRepositoryImpl
import com.sanket_satpute_20.ironmind.data.sync.SyncOrchestrator
import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.AuthUser
import com.sanket_satpute_20.ironmind.domain.model.Goal
import com.sanket_satpute_20.ironmind.domain.model.GoalStatus
import com.sanket_satpute_20.ironmind.domain.model.OutboxEntry
import com.sanket_satpute_20.ironmind.domain.model.OutboxOperationType
import com.sanket_satpute_20.ironmind.domain.model.SyncStatus
import com.sanket_satpute_20.ironmind.domain.session.ReconciliationResult
import com.sanket_satpute_20.ironmind.domain.session.SessionState
import com.sanket_satpute_20.ironmind.domain.session.UserIdentityReconciler
import com.sanket_satpute_20.ironmind.domain.session.UserSessionProvider
import com.sanket_satpute_20.ironmind.domain.usecase.auth.SignInAnonymouslyUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.sync.SyncUseCase
import com.sanket_satpute_20.ironmind.infrastructure.worker.SyncWorker
import com.sanket_satpute_20.ironmind.testutil.fake.FakeAuthRepository
import com.sanket_satpute_20.ironmind.testutil.fake.FakeBackgroundExecutor
import com.sanket_satpute_20.ironmind.testutil.fake.FakeSyncRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SilentAuthAndReconciliationIntegrationTest {

    private lateinit var context: Context
    private lateinit var db: IronMindDatabase
    private lateinit var fakeAuth: FakeAuthRepository
    private lateinit var reconciler: RoomUserIdentityReconciler
    private lateinit var fakeBackgroundExecutor: FakeBackgroundExecutor
    private lateinit var fakeSyncRepository: FakeSyncRepository
    private lateinit var outboxRepository: OutboxRepositoryImpl
    private lateinit var syncOrchestrator: SyncOrchestrator
    private lateinit var syncUseCase: SyncUseCase
    private lateinit var goalRepository: GoalRepositoryImpl
    private lateinit var workerParams: WorkerParameters
    private lateinit var testScope: CoroutineScope
    private var activeTestProvider: UserSessionProvider? = null

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        val directExecutor = java.util.concurrent.Executor { it.run() }
        db = Room.inMemoryDatabaseBuilder(context, IronMindDatabase::class.java)
            .allowMainThreadQueries()
            .setQueryExecutor(directExecutor)
            .setTransactionExecutor(directExecutor)
            .build()

        testScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        fakeAuth = FakeAuthRepository()
        reconciler = RoomUserIdentityReconciler(db)
        fakeBackgroundExecutor = FakeBackgroundExecutor()
        fakeSyncRepository = FakeSyncRepository()
        outboxRepository = OutboxRepositoryImpl(db.outboxDao())
        syncOrchestrator = SyncOrchestrator(outboxRepository, fakeSyncRepository)
        syncUseCase = SyncUseCase(syncOrchestrator)

        goalRepository = GoalRepositoryImpl(
            dao = db.ironMindDao(),
            outboxDao = db.outboxDao(),
            database = db,
            backgroundExecutor = fakeBackgroundExecutor
        )

        workerParams = mockk(relaxed = true) {
            every { inputData } answers {
                val gen = activeTestProvider?.getSessionGeneration() ?: 1L
                Data.Builder()
                    .putLong(UserSessionProvider.KEY_SESSION_GENERATION, gen)
                    .build()
            }
        }
    }

    @After
    fun tearDown() {
        activeTestProvider = null
        testScope.cancel()
        db.close()
    }

    private suspend fun awaitState(
        provider: UserSessionProvider,
        timeoutMs: Long = 5000,
        predicate: (SessionState) -> Boolean
    ) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (predicate(provider.sessionState.value)) return
            delay(25)
        }
        throw AssertionError("Timed out waiting for expected session state. Current: ${provider.sessionState.value}")
    }

    private fun createTestGoal(
        id: String = "goal-stage-243",
        userId: String = SessionState.DEFAULT_LEGACY_USER_ID,
        title: String = "Stage 2.4.3 Goal"
    ): Goal = Goal(
        id = id,
        userId = userId,
        ambitionId = null,
        title = title,
        description = "Description",
        why = "Motivation",
        importance = 9,
        status = GoalStatus.ACTIVE,
        targetAt = null,
        startedAt = 2000L,
        completedAt = null,
        createdAt = 2000L,
        updatedAt = 2000L
    )

    // ── Test 1: No cached user triggers exactly one anonymous sign-in attempt ────
    @Test
    fun `no cached user triggers exactly one anonymous sign in attempt`() = runBlocking {
        val signInUseCase = SignInAnonymouslyUseCase(fakeAuth)
        val provider = UserSessionProviderImpl(
            authRepository = fakeAuth,
            reconciler = reconciler,
            scope = testScope,
            autoPermitOfflineFallback = true,
            signInAnonymouslyUseCase = signInUseCase,
            backgroundExecutor = fakeBackgroundExecutor
        )

        awaitState(provider) { it is SessionState.Authenticated }

        assertEquals("Exactly 1 sign-in attempt must be made", 1, fakeAuth.signInCallCount)
        assertTrue("Session must transition to Authenticated", provider.sessionState.value is SessionState.Authenticated)
        assertEquals("fake-anonymous-id", provider.getActiveUserId())
    }

    // ── Test 2: Existing cached user skips anonymous sign-in ─────────────────────
    @Test
    fun `existing cached user skips anonymous sign in`() = runBlocking {
        val cachedUser = AuthUser("cached-user-999", true, null, null)
        fakeAuth.setTestUser(cachedUser)

        val signInUseCase = SignInAnonymouslyUseCase(fakeAuth)
        val provider = UserSessionProviderImpl(
            authRepository = fakeAuth,
            reconciler = reconciler,
            scope = testScope,
            autoPermitOfflineFallback = true,
            signInAnonymouslyUseCase = signInUseCase,
            backgroundExecutor = fakeBackgroundExecutor
        )

        awaitState(provider) { it is SessionState.Authenticated }

        assertEquals("No sign-in attempt must be made when cached user exists", 0, fakeAuth.signInCallCount)
        assertEquals("cached-user-999", provider.getActiveUserId())
        assertTrue(provider.sessionState.value is SessionState.Authenticated)
    }

    // ── Test 3: Repeated startup or bootstrap does not duplicate sign-in ─────────
    @Test
    fun `repeated startup or bootstrap does not duplicate sign in or reconciliation`() = runBlocking {
        var reconciliationCount = 0
        val trackingReconciler = object : UserIdentityReconciler {
            override suspend fun reconcile(legacyUserId: String, authenticatedUserId: String): Result<ReconciliationResult, Exception> {
                reconciliationCount++
                return Result.Success(ReconciliationResult(true, 1, "OK"))
            }
        }

        val signInUseCase = SignInAnonymouslyUseCase(fakeAuth)
        val provider = UserSessionProviderImpl(
            authRepository = fakeAuth,
            reconciler = trackingReconciler,
            scope = testScope,
            autoPermitOfflineFallback = true,
            signInAnonymouslyUseCase = signInUseCase,
            backgroundExecutor = fakeBackgroundExecutor
        )

        awaitState(provider) { it is SessionState.Authenticated }

        // Attempt repeated bootstrap
        provider.bootstrapAuthentication()
        delay(100)

        assertEquals("Sign-in call count must remain 1", 1, fakeAuth.signInCallCount)
        assertEquals("Reconciliation call count must remain 1", 1, reconciliationCount)
    }

    // ── Test 4: Sign-in failure preserves offline fallback and goal creation ─────
    @Test
    fun `sign in failure preserves offline fallback and local goal creation succeeds`() = runBlocking {
        fakeAuth.signInShouldFail = true

        val signInUseCase = SignInAnonymouslyUseCase(fakeAuth)
        val provider = UserSessionProviderImpl(
            authRepository = fakeAuth,
            reconciler = reconciler,
            scope = testScope,
            autoPermitOfflineFallback = true,
            signInAnonymouslyUseCase = signInUseCase,
            backgroundExecutor = fakeBackgroundExecutor
        )

        delay(100)

        // Session stays in OfflineFallback
        assertTrue("Session must remain in OfflineFallback on auth failure", provider.sessionState.value is SessionState.OfflineFallback)
        assertEquals("user-1", provider.getActiveUserId())

        // Local goal creation still succeeds
        val goalResult = goalRepository.saveGoal(createTestGoal("goal-offline-ok"))
        assertTrue("Local goal creation must succeed offline", goalResult is Result.Success)
        assertNotNull(db.ironMindDao().getGoal("goal-offline-ok"))
    }

    // ── Test 5: Successful sign-in commits reconciliation before session is usable
    @Test
    fun `successful sign in commits reconciliation before session becomes usable`() = runBlocking {
        // Save goal offline under legacy ID
        goalRepository.saveGoal(createTestGoal("goal-pre-reconcile", SessionState.DEFAULT_LEGACY_USER_ID))

        val signInUseCase = SignInAnonymouslyUseCase(fakeAuth)
        val provider = UserSessionProviderImpl(
            authRepository = fakeAuth,
            reconciler = reconciler,
            scope = testScope,
            autoPermitOfflineFallback = true,
            signInAnonymouslyUseCase = signInUseCase,
            backgroundExecutor = fakeBackgroundExecutor
        )

        awaitState(provider) { it is SessionState.Authenticated }

        // Read active userId through session barrier
        var resolvedUserId: String? = null
        provider.withUserSession { userId ->
            resolvedUserId = userId
        }

        assertEquals("fake-anonymous-id", resolvedUserId)

        // Verify that database goal record was reconciled to the new UID
        val updatedGoal = db.ironMindDao().getGoal("goal-pre-reconcile")
        assertNotNull(updatedGoal)
        assertEquals("fake-anonymous-id", updatedGoal!!.userId)

        // Verify outbox payload was also rewritten
        val outbox = db.outboxDao().getPendingEntries()
        assertEquals(1, outbox.size)
        val payload = JSONObject(outbox[0].payload)
        assertEquals("fake-anonymous-id", payload.getString("userId"))
    }

    // ── Test 6: Reconciliation failure prevents authenticated sync and upload ────
    @Test
    fun `reconciliation failure prevents authenticated sync and upload`() = runBlocking {
        val failingReconciler = object : UserIdentityReconciler {
            override suspend fun reconcile(legacyUserId: String, authenticatedUserId: String): Result<ReconciliationResult, Exception> {
                return Result.Failure(IllegalStateException("Simulated SQLite reconciliation crash"))
            }
        }

        val signInUseCase = SignInAnonymouslyUseCase(fakeAuth)
        val provider = UserSessionProviderImpl(
            authRepository = fakeAuth,
            reconciler = failingReconciler,
            scope = testScope,
            autoPermitOfflineFallback = true,
            signInAnonymouslyUseCase = signInUseCase,
            backgroundExecutor = fakeBackgroundExecutor
        )
        activeTestProvider = provider

        awaitState(provider) { it is SessionState.ReconciliationFailed }

        // Session must be in ReconciliationFailed
        assertTrue(provider.sessionState.value is SessionState.ReconciliationFailed)

        // Post-reconciliation sync must NOT be scheduled
        assertTrue("Sync must not be scheduled if reconciliation failed", !fakeBackgroundExecutor.scheduleSyncCalled)

        // SyncWorker must defer and not upload
        val syncWorker = SyncWorker(context, workerParams, syncUseCase, provider)
        val workResult = syncWorker.doWork()
        assertEquals(ListenableWorker.Result.retry(), workResult)
        assertTrue("No uploads must occur when reconciliation failed", fakeSyncRepository.uploadedEntries.isEmpty())
    }

    // ── Test 7: Offline-created goal reconciled and then uploaded to authenticated UID
    @Test
    fun `offline created goal is reconciled and then uploaded only to authenticated UID`() = runBlocking {
        // Step A: Goal created offline
        goalRepository.saveGoal(createTestGoal("goal-e2e", SessionState.DEFAULT_LEGACY_USER_ID))
        fakeBackgroundExecutor.scheduleSyncCalled = false // Reset

        // Step B: Silent sign-in and reconciliation
        val signInUseCase = SignInAnonymouslyUseCase(fakeAuth)
        val provider = UserSessionProviderImpl(
            authRepository = fakeAuth,
            reconciler = reconciler,
            scope = testScope,
            autoPermitOfflineFallback = true,
            signInAnonymouslyUseCase = signInUseCase,
            backgroundExecutor = fakeBackgroundExecutor
        )
        activeTestProvider = provider

        awaitState(provider) { it is SessionState.Authenticated }

        // Post-reconciliation sync was requested
        assertTrue("scheduleSync must be requested after reconciliation", fakeBackgroundExecutor.scheduleSyncCalled)

        // Step C: SyncWorker executes under the authenticated session
        val syncWorker = SyncWorker(context, workerParams, syncUseCase, provider)
        val workResult = syncWorker.doWork()

        assertEquals(ListenableWorker.Result.success(), workResult)
        assertEquals(1, fakeSyncRepository.uploadedEntries.size)
        assertEquals("goal-e2e", fakeSyncRepository.uploadedEntries[0].entityId)

        // Uploaded under the reconciled authenticated UID
        val uploadedPayload = JSONObject(fakeSyncRepository.uploadedEntries[0].payload)
        assertEquals("fake-anonymous-id", uploadedPayload.getString("userId"))

        // Outbox is now empty
        assertTrue(db.outboxDao().getPendingEntries().isEmpty())
    }

    // ── Test 8: Sync attempting to run during active reconciliation waits for barrier
    @Test
    fun `sync worker attempting to run during active reconciliation waits for barrier`() = runBlocking {
        val barrier = SessionReadWriteBarrier()
        val reconStarted = CompletableDeferred<Unit>()
        val reconCanFinish = CompletableDeferred<Unit>()

        val blockingReconciler = object : UserIdentityReconciler {
            override suspend fun reconcile(legacyUserId: String, authenticatedUserId: String): Result<ReconciliationResult, Exception> {
                reconStarted.complete(Unit)
                reconCanFinish.await()
                return reconciler.reconcile(legacyUserId, authenticatedUserId)
            }
        }

        goalRepository.saveGoal(createTestGoal("goal-barrier", SessionState.DEFAULT_LEGACY_USER_ID))

        val provider = UserSessionProviderImpl(
            authRepository = fakeAuth,
            reconciler = blockingReconciler,
            scope = testScope,
            autoPermitOfflineFallback = true,
            barrier = barrier,
            signInAnonymouslyUseCase = null,
            backgroundExecutor = fakeBackgroundExecutor
        )
        activeTestProvider = provider

        // Trigger auth emission in background
        testScope.launch {
            fakeAuth.setTestUser(AuthUser("new-uid-barrier", true, null, null))
        }

        reconStarted.await()
        assertTrue("Barrier gate must be closed during reconciliation", !barrier.isGateOpen())

        // Worker attempts to run while reconciliation is held: session is not authenticated, defers safely
        val syncWorker = SyncWorker(context, workerParams, syncUseCase, provider)
        val inFlightOutcome = syncWorker.doWork()
        assertEquals("SyncWorker must defer while reconciliation is incomplete", ListenableWorker.Result.retry(), inFlightOutcome)
        assertTrue("No uploads must occur while reconciliation is in flight", fakeSyncRepository.uploadedEntries.isEmpty())

        // Release reconciliation lock
        reconCanFinish.complete(Unit)

        awaitState(provider) { it is SessionState.Authenticated }

        // Post-reconciliation sync run succeeds completely
        val postReconOutcome = syncWorker.doWork()
        assertEquals(ListenableWorker.Result.success(), postReconOutcome)
        assertEquals(1, fakeSyncRepository.uploadedEntries.size)
    }

    // ── Test 9: Duplicate auth state emissions cause no duplicate migration ──────
    @Test
    fun `duplicate auth state emissions cause no duplicate migration or outbox corruption`() = runBlocking {
        var reconciliationCount = 0
        val trackingReconciler = object : UserIdentityReconciler {
            override suspend fun reconcile(legacyUserId: String, authenticatedUserId: String): Result<ReconciliationResult, Exception> {
                reconciliationCount++
                return Result.Success(ReconciliationResult(true, 1, "OK"))
            }
        }

        val provider = UserSessionProviderImpl(
            authRepository = fakeAuth,
            reconciler = trackingReconciler,
            scope = testScope,
            autoPermitOfflineFallback = true
        )

        // First emission
        fakeAuth.setTestUser(AuthUser("dup-uid-1", true, null, null))
        awaitState(provider) { it is SessionState.Authenticated }
        assertEquals(1, reconciliationCount)

        // Duplicate emission with identical UID
        fakeAuth.setTestUser(AuthUser("dup-uid-1", true, null, null))
        delay(100)
        assertEquals("Duplicate emission with same UID must not trigger re-reconciliation", 1, reconciliationCount)
    }

    // ── Test 10: Session invalidation or UID transition during sync prevents cross-user upload
    @Test
    fun `session invalidation or UID transition prevents cross user upload`() = runBlocking {
        // Outbox contains payload for user-A
        outboxRepository.insertEntry(
            OutboxEntry(
                operationId = "op-userA",
                entityType = "Goal",
                entityId = "goal-userA",
                operationType = OutboxOperationType.UPSERT,
                payload = """{"id":"goal-userA","userId":"user-A","title":"Goal A"}""",
                createdAt = 1000L,
                retryCount = 0,
                lastAttemptAt = null,
                status = SyncStatus.PENDING
            )
        )

        // Sync invoked for user-B
        val result = syncOrchestrator.sync("user-B")
        assertTrue(result is Result.Success)

        // Entry must NOT be uploaded for user-B
        assertTrue("No uploads must occur when payload userId mismatches target sync user", fakeSyncRepository.uploadedEntries.isEmpty())

        // Entry remains in outbox
        val pending = db.outboxDao().getPendingEntries()
        assertEquals(1, pending.size)
        assertEquals("op-userA", pending[0].operationId)
    }

    // ── Test 11: Scheduling failure after successful reconciliation preserves local records
    @Test
    fun `scheduling failure after successful reconciliation preserves local records`() = runBlocking {
        fakeBackgroundExecutor.shouldThrowException = true
        goalRepository.saveGoal(createTestGoal("goal-sched-fail-test", SessionState.DEFAULT_LEGACY_USER_ID))

        val signInUseCase = SignInAnonymouslyUseCase(fakeAuth)
        val provider = UserSessionProviderImpl(
            authRepository = fakeAuth,
            reconciler = reconciler,
            scope = testScope,
            autoPermitOfflineFallback = true,
            signInAnonymouslyUseCase = signInUseCase,
            backgroundExecutor = fakeBackgroundExecutor
        )

        awaitState(provider) { it is SessionState.Authenticated }

        // Provider must still successfully transition to Authenticated
        assertTrue(provider.sessionState.value is SessionState.Authenticated)
        assertEquals("fake-anonymous-id", provider.getActiveUserId())

        // Local goal remains successfully migrated
        val goal = db.ironMindDao().getGoal("goal-sched-fail-test")
        assertNotNull(goal)
        assertEquals("fake-anonymous-id", goal!!.userId)
    }
}
