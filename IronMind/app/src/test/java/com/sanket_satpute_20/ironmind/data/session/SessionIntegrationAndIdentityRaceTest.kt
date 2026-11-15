package com.sanket_satpute_20.ironmind.data.session

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.sanket_satpute_20.ironmind.data.ai.CloudflareWorkerIronMindAI
import com.sanket_satpute_20.ironmind.data.local.Jdk25CompatibleRobolectricTestRunner
import com.sanket_satpute_20.ironmind.di.DefaultAppContainer
import com.sanket_satpute_20.ironmind.domain.ai.AIRequest
import com.sanket_satpute_20.ironmind.domain.ai.AIRequestType
import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.AuthStatus
import com.sanket_satpute_20.ironmind.domain.model.AuthUser
import com.sanket_satpute_20.ironmind.domain.repository.AuthRepository
import com.sanket_satpute_20.ironmind.domain.session.PatternFingerprintCollisionException
import com.sanket_satpute_20.ironmind.domain.session.ReconciliationResult
import com.sanket_satpute_20.ironmind.domain.session.SessionState
import com.sanket_satpute_20.ironmind.domain.session.UserIdentityReconciler
import com.sanket_satpute_20.ironmind.ui.screens.goals.GoalDetailViewModel
import com.sanket_satpute_20.ironmind.ui.screens.goals.GoalsViewModel
import com.sanket_satpute_20.ironmind.ui.screens.history.HistoryViewModel
import com.sanket_satpute_20.ironmind.ui.screens.history.IntelligenceHistoryViewModel
import com.sanket_satpute_20.ironmind.ui.screens.plans.PlanDetailViewModel
import com.sanket_satpute_20.ironmind.ui.screens.protection.ProtectionViewModel
import com.sanket_satpute_20.ironmind.ui.screens.reflection.NightReflectionViewModel
import com.sanket_satpute_20.ironmind.ui.screens.settings.SettingsViewModel
import com.sanket_satpute_20.ironmind.ui.screens.today.TodayViewModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(Jdk25CompatibleRobolectricTestRunner::class)
@Config(sdk = [34])
class SessionIntegrationAndIdentityRaceTest {

    @org.junit.Before
    fun setUp() {
        androidx.arch.core.executor.ArchTaskExecutor.getInstance().setDelegate(object : androidx.arch.core.executor.TaskExecutor() {
            override fun executeOnDiskIO(runnable: Runnable) {
                runnable.run()
            }
            override fun postToMainThread(runnable: Runnable) {
                runnable.run()
            }
            override fun isMainThread(): Boolean = true
        })
    }

    @After
    fun tearDown() {
        try {
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
        } finally {
            androidx.arch.core.executor.ArchTaskExecutor.getInstance().setDelegate(null)
        }
    }

    private class FakeAuthRepository(
        initialUser: AuthUser? = null
    ) : AuthRepository {
        val userFlow = MutableStateFlow(initialUser)
        private var currentUserRef = initialUser

        override val currentUser: Flow<AuthUser?> = userFlow
        override val authStatus: Flow<AuthStatus> = MutableStateFlow(
            if (initialUser != null) AuthStatus.AUTHENTICATED else AuthStatus.UNAUTHENTICATED
        )

        override fun getCurrentUser(): AuthUser? = currentUserRef

        override suspend fun signInAnonymously(): Result<AuthUser, Exception> {
            val user = AuthUser("anon-new-uid", true, null, null)
            currentUserRef = user
            userFlow.value = user
            return Result.Success(user)
        }

        override suspend fun signOut(): Result<Unit, Exception> {
            currentUserRef = null
            userFlow.value = null
            return Result.Success(Unit)
        }

        fun emitUser(user: AuthUser?) {
            currentUserRef = user
            userFlow.value = user
        }
    }

    private class FakeUserIdentityReconciler(
        var shouldFailWithCollision: Boolean = false,
        var reconcileCallback: (suspend (String, String) -> Unit)? = null
    ) : UserIdentityReconciler {
        var callCount = 0
        var lastLegacyUserId: String? = null
        var lastAuthenticatedUserId: String? = null

        override suspend fun reconcile(
            legacyUserId: String,
            authenticatedUserId: String
        ): Result<ReconciliationResult, Exception> {
            callCount++
            lastLegacyUserId = legacyUserId
            lastAuthenticatedUserId = authenticatedUserId

            reconcileCallback?.invoke(legacyUserId, authenticatedUserId)

            if (shouldFailWithCollision) {
                return Result.Failure(
                    PatternFingerprintCollisionException(
                        message = "Collision detected on fingerprints during reconciliation",
                        conflictingFingerprints = listOf("FINGERPRINT_CONFLICT_123")
                    )
                )
            }
            return Result.Success(ReconciliationResult(isReconciled = true, recordsUpdated = 5, message = "Reconciliation complete"))
        }
    }

    // 1. One shared session-provider instance across the app container
    @Test
    fun `app container provides one shared UserSessionProvider and UserIdentityReconciler singleton`() {
        val context: Context = ApplicationProvider.getApplicationContext()
        val container = DefaultAppContainer(context)
        try {
            val provider1 = container.userSessionProvider
            val provider2 = container.userSessionProvider
            val reconciler1 = container.userIdentityReconciler
            val reconciler2 = container.userIdentityReconciler

            assertSame("UserSessionProvider must be a singleton in AppContainer", provider1, provider2)
            assertSame("UserIdentityReconciler must be a singleton in AppContainer", reconciler1, reconciler2)
        } finally {
            (container.userSessionProvider as? UserSessionProviderImpl)?.scope?.cancel()
            container.database.close()
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
        }
    }

    // 2. Cached Firebase identity on cold start
    @Test
    fun `cached Firebase identity on cold start immediately initializes session with authenticated UID`() = runTest(UnconfinedTestDispatcher()) {
        val cachedUser = AuthUser("cached-uid-abc", true, null, null)
        val fakeAuth = FakeAuthRepository(cachedUser)
        val fakeReconciler = FakeUserIdentityReconciler()

        val provider = UserSessionProviderImpl(
            authRepository = fakeAuth,
            reconciler = fakeReconciler,
            scope = backgroundScope
        )

        val state = provider.sessionState.value
        assertTrue("State must be Authenticated on cold start with cached user", state is SessionState.Authenticated)
        assertEquals("cached-uid-abc", provider.getActiveUserId())
        assertEquals(1, fakeReconciler.callCount)
        assertEquals("user-1", fakeReconciler.lastLegacyUserId)
        assertEquals("cached-uid-abc", fakeReconciler.lastAuthenticatedUserId)
    }

    // 3. Offline fallback and subsequent authentication
    @Test
    fun `offline fallback starts with legacy ID and transitions to authenticated on sign-in`() = runTest(UnconfinedTestDispatcher()) {
        val fakeAuth = FakeAuthRepository(null)
        val fakeReconciler = FakeUserIdentityReconciler()

        val provider = UserSessionProviderImpl(
            authRepository = fakeAuth,
            reconciler = fakeReconciler,
            scope = backgroundScope,
            autoPermitOfflineFallback = true
        )

        assertEquals("user-1", provider.getActiveUserId())
        assertTrue(provider.sessionState.value is SessionState.OfflineFallback)

        // Sign in occurs later
        fakeAuth.emitUser(AuthUser("new-firebase-uid-999", true, null, null))

        val state = provider.sessionState.value
        assertTrue("State must transition to Authenticated", state is SessionState.Authenticated)
        assertEquals("new-firebase-uid-999", provider.getActiveUserId())
        assertEquals(1, fakeReconciler.callCount)
    }

    // 4. A write beginning before authentication resolves
    @Test
    fun `a write beginning before authentication captures offline legacy ID and completes atomically`() = runTest(UnconfinedTestDispatcher()) {
        val fakeAuth = FakeAuthRepository(null)
        val provider = UserSessionProviderImpl(
            authRepository = fakeAuth,
            reconciler = null,
            scope = backgroundScope,
            autoPermitOfflineFallback = true
        )

        var capturedId: String? = null
        provider.withUserSession { userId ->
            capturedId = userId
        }

        assertEquals("user-1", capturedId)
    }

    // 5 & 6. A write racing with reconciliation: reconciliation waits for write, and new write receives authenticated ID
    @Test
    fun `write racing with reconciliation executes safely with barrier coordination`() = runTest(UnconfinedTestDispatcher()) {
        val barrier = SessionReadWriteBarrier()
        val fakeAuth = FakeAuthRepository(null)

        val writeStarted = CompletableDeferred<Unit>()
        val writeCanFinish = CompletableDeferred<Unit>()
        val writeFinished = CompletableDeferred<String>()

        val reconcilerStarted = CompletableDeferred<Unit>()

        val fakeReconciler = FakeUserIdentityReconciler(
            reconcileCallback = { _, _ ->
                reconcilerStarted.complete(Unit)
            }
        )

        val provider = UserSessionProviderImpl(
            authRepository = fakeAuth,
            reconciler = fakeReconciler,
            scope = backgroundScope,
            autoPermitOfflineFallback = true,
            barrier = barrier
        )

        // Step 1: Launch an in-flight write operation before reconciliation starts
        backgroundScope.launch {
            provider.withUserSession { userId ->
                writeStarted.complete(Unit)
                // Hold write lock until signal
                writeCanFinish.await()
                writeFinished.complete(userId)
            }
        }

        writeStarted.await()
        assertTrue("Active readers count must be 1 while write is in flight", barrier.getActiveReadersCount() == 1)

        // Step 2: Trigger reconciliation concurrently while write is in flight
        backgroundScope.launch {
            fakeAuth.emitUser(AuthUser("authenticated-uid-xyz", true, null, null))
        }

        // Verify barrier: write is still holding the read lock, so reconciler has not acquired exclusive write lock
        assertFalse("Reconciler must not start while in-flight write holds read lock", reconcilerStarted.isCompleted)
        assertEquals("user-1", provider.getActiveUserId())

        // Step 3: Complete the in-flight write
        writeCanFinish.complete(Unit)
        val legacyWriteId = writeFinished.await()
        assertEquals("user-1", legacyWriteId)

        // Now reconciliation can acquire exclusive access and finish
        reconcilerStarted.await()
        assertEquals("authenticated-uid-xyz", provider.getActiveUserId())

        // Step 4: Any new write entering withUserSession receives the authenticated ID
        val postReconciliationWriteId = provider.withUserSession { it }
        assertEquals("authenticated-uid-xyz", postReconciliationWriteId)
    }

    // 7. Reconciliation failure due to pattern collision
    @Test
    fun `reconciliation failure due to collision transitions to ReconciliationFailed and blocks unsafe writes`() = runTest(UnconfinedTestDispatcher()) {
        val fakeAuth = FakeAuthRepository(null)
        val fakeReconciler = FakeUserIdentityReconciler(shouldFailWithCollision = true)

        val provider = UserSessionProviderImpl(
            authRepository = fakeAuth,
            reconciler = fakeReconciler,
            scope = backgroundScope,
            autoPermitOfflineFallback = true
        )

        // Trigger sign-in that will fail reconciliation
        fakeAuth.emitUser(AuthUser("colliding-uid", true, null, null))

        val state = provider.sessionState.value
        assertTrue("State must be ReconciliationFailed", state is SessionState.ReconciliationFailed)
        val failState = state as SessionState.ReconciliationFailed
        assertTrue(failState.error is PatternFingerprintCollisionException)
        assertEquals("colliding-uid", failState.targetUserId)

        // Attempting to write with withUserSession must throw IllegalStateException to protect data
        var threwExpectedError = false
        try {
            provider.withUserSession { it }
        } catch (e: IllegalStateException) {
            threwExpectedError = true
            assertTrue(e.message?.contains("identity reconciliation failed") == true)
        }
        assertTrue("Must throw IllegalStateException when attempting withUserSession in ReconciliationFailed state", threwExpectedError)
    }

    // 8. Retry after reconciliation failure
    @Test
    fun `retryReconciliation succeeds when collision is resolved and restores Authenticated state`() = runTest(UnconfinedTestDispatcher()) {
        val fakeAuth = FakeAuthRepository(null)
        val fakeReconciler = FakeUserIdentityReconciler(shouldFailWithCollision = true)

        val provider = UserSessionProviderImpl(
            authRepository = fakeAuth,
            reconciler = fakeReconciler,
            scope = backgroundScope,
            autoPermitOfflineFallback = true
        )

        fakeAuth.emitUser(AuthUser("target-uid-retry", true, null, null))
        assertTrue(provider.sessionState.value is SessionState.ReconciliationFailed)

        // Collision resolved on next attempt
        fakeReconciler.shouldFailWithCollision = false
        val retryResult = provider.retryReconciliation()

        assertTrue("Retry must succeed", retryResult is Result.Success)
        assertTrue(provider.sessionState.value is SessionState.Authenticated)
        assertEquals("target-uid-retry", provider.getActiveUserId())

        // withUserSession now succeeds with the authenticated UID
        val uid = provider.withUserSession { it }
        assertEquals("target-uid-retry", uid)
    }

    // 10. AI request userId matching authenticated UID
    @Test
    fun `AI request ensures userId matches the authenticated UID when authUserProvider is configured`() = runTest(UnconfinedTestDispatcher()) {
        var capturedRequestBody: String? = null

        val ai = CloudflareWorkerIronMindAI(
            endpointUrl = "http://10.0.2.2:8787/v1/process",
            requireAppCheck = false,
            idTokenProvider = { "fake-jwt-token" },
            httpTransport = { _, _, body ->
                capturedRequestBody = body
                Pair(200, """{"rawResponse":"{\"intentDescription\":\"Valid test intent\",\"confidence\":0.9}"}""")
            },
            authUserProvider = { "authenticated-firebase-uid-555" }
        )

        // Caller passes legacy user-1
        val requestWithLegacyId = AIRequest(
            userId = "user-1",
            input = "Go for a walk",
            requestType = AIRequestType.INTENT_EXTRACTION
        )

        val result = ai.process(requestWithLegacyId)
        assertTrue("AI request should succeed", result is Result.Success)

        val parsedJson = JSONObject(capturedRequestBody!!)
        assertEquals(
            "UID sent in AI request must match authenticated Firebase UID",
            "authenticated-firebase-uid-555",
            parsedJson.getString("userId")
        )
    }

    // 11 & 12. No production caller accidentally using user-1 after successful authenticated session
    @Test
    fun `all production ViewModels reflect authenticated UID from UserSessionProvider`() = runTest(UnconfinedTestDispatcher()) {
        val fakeAuth = FakeAuthRepository(AuthUser("authenticated-user-777", true, null, null))
        val fakeReconciler = FakeUserIdentityReconciler()
        val provider = UserSessionProviderImpl(fakeAuth, fakeReconciler, backgroundScope)

        assertEquals("authenticated-user-777", provider.getActiveUserId())

        // 1. Verify TodayViewModel queries for the authenticated UID (not user-1)
        val mockGetActiveCommitments = mockk<com.sanket_satpute_20.ironmind.domain.usecase.commitment.GetActiveCommitmentsUseCase>()
        coEvery { mockGetActiveCommitments(any()) } returns Result.Success(emptyList())
        val mockGetPendingRecs = mockk<com.sanket_satpute_20.ironmind.domain.usecase.ai.GetPendingInterventionRecommendationsUseCase>()
        every { mockGetPendingRecs(any(), any()) } returns flowOf(emptyList())

        val todayVm = TodayViewModel(
            getActiveCommitmentsUseCase = mockGetActiveCommitments,
            updateCommitmentStatusUseCase = mockk(relaxed = true),
            scheduleCommitmentUseCase = mockk(relaxed = true),
            cancelCommitmentScheduleUseCase = mockk(relaxed = true),
            getPendingInterventionRecommendationsUseCase = mockGetPendingRecs,
            handleInterventionResultUseCase = mockk(relaxed = true),
            orchestrateInterventionGenerationUseCase = mockk(relaxed = true),
            userSessionProvider = provider
        )
        coVerify { mockGetActiveCommitments("authenticated-user-777") }

        // 2. Verify GoalsViewModel queries for the authenticated UID
        val mockGetGoals = mockk<com.sanket_satpute_20.ironmind.domain.usecase.goal.GetGoalsUseCase>()
        coEvery { mockGetGoals(any()) } returns Result.Success(emptyList())

        val goalsVm = GoalsViewModel(
            getGoalsUseCase = mockGetGoals,
            createGoalUseCase = mockk(relaxed = true),
            logger = mockk(relaxed = true),
            userSessionProvider = provider
        )
        coVerify { mockGetGoals("authenticated-user-777") }

        // 3. Verify HistoryViewModel queries for the authenticated UID
        val mockGetTimeline = mockk<com.sanket_satpute_20.ironmind.domain.usecase.history.GetTimelineUseCase>()
        coEvery { mockGetTimeline(any()) } returns Result.Success(emptyList())

        val historyVm = HistoryViewModel(
            getTimelineUseCase = mockGetTimeline,
            userSessionProvider = provider
        )
        coVerify { mockGetTimeline("authenticated-user-777") }

        // 4. Verify ProtectionViewModel queries for the authenticated UID
        val mockGetProtectionSession = mockk<com.sanket_satpute_20.ironmind.domain.usecase.protection.GetActiveProtectionSessionUseCase>()
        coEvery { mockGetProtectionSession(any()) } returns Result.Success(null)

        val protectionVm = ProtectionViewModel(
            getActiveProtectionSessionUseCase = mockGetProtectionSession,
            startProtectionSessionUseCase = mockk(relaxed = true),
            stopProtectionSessionUseCase = mockk(relaxed = true),
            userSessionProvider = provider
        )
        coVerify { mockGetProtectionSession("authenticated-user-777") }

        // 5. Verify that offline fallback correctly queries with legacy ID "user-1"
        val offlineProvider = UserSessionProviderImpl(
            authRepository = FakeAuthRepository(null),
            reconciler = fakeReconciler,
            scope = backgroundScope,
            autoPermitOfflineFallback = true
        )
        assertEquals("user-1", offlineProvider.getActiveUserId())

        val offlineTodayVm = TodayViewModel(
            getActiveCommitmentsUseCase = mockGetActiveCommitments,
            updateCommitmentStatusUseCase = mockk(relaxed = true),
            scheduleCommitmentUseCase = mockk(relaxed = true),
            cancelCommitmentScheduleUseCase = mockk(relaxed = true),
            getPendingInterventionRecommendationsUseCase = mockGetPendingRecs,
            handleInterventionResultUseCase = mockk(relaxed = true),
            orchestrateInterventionGenerationUseCase = mockk(relaxed = true),
            userSessionProvider = offlineProvider
        )
        coVerify { mockGetActiveCommitments("user-1") }
    }
}
