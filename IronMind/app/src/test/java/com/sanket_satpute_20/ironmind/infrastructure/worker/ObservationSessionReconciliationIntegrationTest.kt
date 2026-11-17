package com.sanket_satpute_20.ironmind.infrastructure.worker

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.sanket_satpute_20.ironmind.data.session.UserSessionProviderImpl
import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.AuthUser
import com.sanket_satpute_20.ironmind.domain.model.AutonomySettings
import com.sanket_satpute_20.ironmind.domain.model.AppUsageObservationSettings
import com.sanket_satpute_20.ironmind.domain.model.ActivityObservationSettings
import com.sanket_satpute_20.ironmind.domain.model.CalendarObservationSettings
import com.sanket_satpute_20.ironmind.domain.model.LocationObservationSettings
import com.sanket_satpute_20.ironmind.domain.repository.ActivityObservationSettingsRepository
import com.sanket_satpute_20.ironmind.domain.repository.AppUsageObservationSettingsRepository
import com.sanket_satpute_20.ironmind.domain.repository.AutonomySettingsRepository
import com.sanket_satpute_20.ironmind.domain.repository.CalendarObservationSettingsRepository
import com.sanket_satpute_20.ironmind.domain.repository.LocationObservationSettingsRepository
import com.sanket_satpute_20.ironmind.domain.session.ReconciliationResult
import com.sanket_satpute_20.ironmind.domain.session.SessionState
import com.sanket_satpute_20.ironmind.domain.session.UserIdentityReconciler
import com.sanket_satpute_20.ironmind.domain.session.UserSessionProvider
import com.sanket_satpute_20.ironmind.domain.usecase.observation.ActivateObservationSchedulingUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.observation.ExecuteObservationCollectionUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.observation.ObservationCollectionSummary
import com.sanket_satpute_20.ironmind.domain.usecase.observation.ObservationSchedulingCoordinator
import com.sanket_satpute_20.ironmind.domain.usecase.observation.ObservationSchedulingLifecycleEvent
import com.sanket_satpute_20.ironmind.domain.usecase.observation.ObservationSchedulingPolicyUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.observation.ReconcileObservationSchedulingUseCase
import com.sanket_satpute_20.ironmind.execution.background.BackgroundExecutorImpl
import com.sanket_satpute_20.ironmind.testutil.fake.FakeAuthRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ObservationSessionReconciliationIntegrationTest {

    private lateinit var context: Context
    private lateinit var workManager: WorkManager
    private lateinit var authRepository: FakeAuthRepository
    private lateinit var autonomySettingsRepository: AutonomySettingsRepository
    private lateinit var appUsageSettingsRepository: AppUsageObservationSettingsRepository
    private lateinit var activitySettingsRepository: ActivityObservationSettingsRepository
    private lateinit var calendarSettingsRepository: CalendarObservationSettingsRepository
    private lateinit var locationSettingsRepository: LocationObservationSettingsRepository
    private lateinit var reconciler: UserIdentityReconciler
    private val activeScopes = mutableListOf<CoroutineScope>()

    private val testUserId = "user_reconcile_test_123"
    private val testUser = AuthUser(id = testUserId, email = "test@ironmind.org", isAnonymous = false)

    private fun createTestScope(): CoroutineScope {
        val s = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        activeScopes.add(s)
        return s
    }

    @After
    fun tearDown() {
        activeScopes.forEach { it.cancel() }
        activeScopes.clear()
    }

    @Before
    fun setUp() {
        context = mockk(relaxed = true)
        workManager = mockk(relaxed = true)
        authRepository = FakeAuthRepository()

        autonomySettingsRepository = mockk {
            coEvery { getSettings(any()) } returns Result.Success(
                AutonomySettings(testUserId, emptyMap(), isGlobalPauseActive = false)
            )
        }
        appUsageSettingsRepository = mockk {
            coEvery { getSettings(any()) } returns Result.Success(
                AppUsageObservationSettings(testUserId, isEnabled = true)
            )
        }
        activitySettingsRepository = mockk {
            coEvery { getSettings(any()) } returns Result.Success(
                ActivityObservationSettings(testUserId, isEnabled = false, lastUpdatedAt = 0L)
            )
        }
        calendarSettingsRepository = mockk {
            coEvery { getSettings(any()) } returns Result.Success(
                CalendarObservationSettings(testUserId, isEnabled = false, updatedAt = 0L)
            )
        }
        locationSettingsRepository = mockk {
            coEvery { getSettings(any()) } returns Result.Success(
                LocationObservationSettings(testUserId, isEnabled = false, updatedAt = 0L)
            )
        }

        reconciler = mockk {
            coEvery { reconcile(any(), any()) } returns Result.Success(ReconciliationResult(true, 1, "Success"))
        }
    }

    private fun buildWiring(
        scope: CoroutineScope,
        initialAuthUser: AuthUser? = null
    ): TestHarness {
        authRepository.setTestUser(null)

        lateinit var sessionProvider: UserSessionProviderImpl
        lateinit var backgroundExecutor: BackgroundExecutorImpl
        lateinit var observationScheduler: ObservationScheduler
        lateinit var coordinator: ObservationSchedulingCoordinator

        observationScheduler = ObservationScheduler(
            workManager = workManager,
            sessionProvider = { sessionProvider }
        )

        val policyUseCase = ObservationSchedulingPolicyUseCase(
            authRepository = authRepository,
            autonomySettingsRepository = autonomySettingsRepository,
            appUsageSettingsRepository = appUsageSettingsRepository,
            activitySettingsRepository = activitySettingsRepository,
            calendarSettingsRepository = calendarSettingsRepository,
            locationSettingsRepository = locationSettingsRepository
        )

        val activateUseCase = ActivateObservationSchedulingUseCase(
            policyUseCase = policyUseCase,
            observationScheduler = observationScheduler
        )

        val reconcileUseCase = ReconcileObservationSchedulingUseCase(
            activateUseCase = activateUseCase,
            observationScheduler = observationScheduler
        )

        coordinator = ObservationSchedulingCoordinator(reconcileUseCase)

        backgroundExecutor = BackgroundExecutorImpl(
            context = context,
            workManager = workManager,
            sessionProvider = { sessionProvider },
            observationCoordinatorProvider = { coordinator },
            coroutineScope = scope
        )

        sessionProvider = UserSessionProviderImpl(
            authRepository = authRepository,
            reconciler = reconciler,
            scope = scope,
            autoPermitOfflineFallback = true,
            backgroundExecutor = backgroundExecutor
        )

        if (initialAuthUser != null) {
            authRepository.setTestUser(initialAuthUser)
        }

        return TestHarness(
            sessionProvider = sessionProvider,
            backgroundExecutor = backgroundExecutor,
            observationScheduler = observationScheduler,
            coordinator = coordinator
        )
    }

    data class TestHarness(
        val sessionProvider: UserSessionProviderImpl,
        val backgroundExecutor: BackgroundExecutorImpl,
        val observationScheduler: ObservationScheduler,
        val coordinator: ObservationSchedulingCoordinator
    )

    // Requirement 4.1: Successful initial reconciliation refreshes observation scheduling with the new generation
    @Test
    fun testInitialReconciliationRefreshesObservationSchedulingWithNewGeneration() = runBlocking {
        val scope = createTestScope()
        val requestSlot = slot<PeriodicWorkRequest>()
        every {
            workManager.enqueueUniquePeriodicWork(
                ObservationScheduler.UNIQUE_WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                capture(requestSlot)
            )
        } returns mockk()

        val harness = buildWiring(scope, initialAuthUser = testUser)

        // Await the asynchronous initial observation scheduling enqueue
        val deadline = System.currentTimeMillis() + 5_000L
        while (!requestSlot.isCaptured && System.currentTimeMillis() < deadline) {
            kotlinx.coroutines.delay(50)
        }

        // Session generation should have advanced from 1L to 2L
        assertEquals(2L, harness.sessionProvider.getSessionGeneration())
        assertTrue(harness.sessionProvider.sessionState.value is SessionState.Authenticated)

        // Verify ObservationScheduler enqueued periodic work with generation 2L
        verify(atLeast = 1) {
            workManager.enqueueUniquePeriodicWork(
                ObservationScheduler.UNIQUE_WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                any()
            )
        }
        val generation = requestSlot.captured.workSpec.input.getLong(ObservationWorker.KEY_SESSION_GENERATION, -1L)
        assertEquals(2L, generation)
    }

    // Requirement 4.2: Successful reconciliation retry refreshes observation scheduling
    @Test
    fun testReconciliationRetryRefreshesObservationScheduling() = runBlocking {
        val scope = createTestScope()
        val requestSlot = slot<PeriodicWorkRequest>()
        every {
            workManager.enqueueUniquePeriodicWork(
                ObservationScheduler.UNIQUE_WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                capture(requestSlot)
            )
        } returns mockk()

        // First attempt will fail
        coEvery { reconciler.reconcile(any(), any()) } returns Result.Failure(Exception("Network error during reconciliation"))

        val harness = buildWiring(scope, initialAuthUser = testUser)
        val failDeadline = System.currentTimeMillis() + 5_000L
        while (harness.sessionProvider.sessionState.value !is SessionState.ReconciliationFailed && System.currentTimeMillis() < failDeadline) {
            kotlinx.coroutines.delay(50)
        }

        assertTrue(harness.sessionProvider.sessionState.value is SessionState.ReconciliationFailed)

        // Now configure reconciler to succeed on retry
        coEvery { reconciler.reconcile(any(), any()) } returns Result.Success(ReconciliationResult(true, 1, "Success"))

        val retryResult = harness.sessionProvider.retryReconciliation()
        assertTrue(retryResult is Result.Success)

        // Await the asynchronous retry observation scheduling enqueue
        val retryDeadline = System.currentTimeMillis() + 5_000L
        while (!requestSlot.isCaptured && System.currentTimeMillis() < retryDeadline) {
            kotlinx.coroutines.delay(50)
        }

        assertEquals(2L, harness.sessionProvider.getSessionGeneration())
        assertTrue(harness.sessionProvider.sessionState.value is SessionState.Authenticated)

        // Verify periodic work was updated to generation 2L
        verify(atLeast = 1) {
            workManager.enqueueUniquePeriodicWork(
                ObservationScheduler.UNIQUE_WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                any()
            )
        }
        val generation = requestSlot.captured.workSpec.input.getLong(ObservationWorker.KEY_SESSION_GENERATION, -1L)
        assertEquals(2L, generation)
    }

    // Requirement 4.3: An already-enqueued periodic request is updated without creating duplicate unique work
    @Test
    fun testAlreadyEnqueuedPeriodicRequestUpdatedWithoutDuplicateUniqueWork() = runBlocking {
        val scope = createTestScope()
        val harness = buildWiring(scope, initialAuthUser = testUser)
        val genDeadline = System.currentTimeMillis() + 5_000L
        while (harness.sessionProvider.getSessionGeneration() < 2L && System.currentTimeMillis() < genDeadline) {
            kotlinx.coroutines.delay(50)
        }

        every {
            workManager.enqueueUniquePeriodicWork(any(), any(), any())
        } returns mockk()

        // Manually trigger scheduling 3 times
        harness.coordinator.handle(ObservationSchedulingLifecycleEvent.ApplicationStarted)
        harness.coordinator.handle(ObservationSchedulingLifecycleEvent.SessionReconciled)
        harness.coordinator.handle(ObservationSchedulingLifecycleEvent.SessionReconciled)

        // Every schedule call uses ExistingPeriodicWorkPolicy.UPDATE on the same UNIQUE_WORK_NAME
        verify(atLeast = 3) {
            workManager.enqueueUniquePeriodicWork(
                ObservationScheduler.UNIQUE_WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                any()
            )
        }
    }

    // Requirement 4.4: Ineligible settings or global pause do not accidentally activate observation collection
    @Test
    fun testIneligibleSettingsOrGlobalPauseDoNotActivateObservationCollection() = runBlocking {
        val scope = createTestScope()

        // Case A: Global pause active
        coEvery { autonomySettingsRepository.getSettings(any()) } returns Result.Success(
            AutonomySettings(testUserId, emptyMap(), isGlobalPauseActive = true)
        )

        val harness = buildWiring(scope, initialAuthUser = testUser)
        val pauseDeadline = System.currentTimeMillis() + 5_000L
        var pauseCancelled = false
        while (!pauseCancelled && System.currentTimeMillis() < pauseDeadline) {
            try {
                verify(atLeast = 1) { workManager.cancelUniqueWork(ObservationScheduler.UNIQUE_WORK_NAME) }
                pauseCancelled = true
            } catch (e: Throwable) {
                kotlinx.coroutines.delay(50)
            }
        }

        // Global pause must cause cancellation, zero enqueue of observation work
        verify(atLeast = 1) { workManager.cancelUniqueWork(ObservationScheduler.UNIQUE_WORK_NAME) }
        verify(exactly = 0) {
            workManager.enqueueUniquePeriodicWork(
                ObservationScheduler.UNIQUE_WORK_NAME,
                any(),
                any()
            )
        }

        // Case B: Global pause inactive, but all observation consent disabled
        coEvery { autonomySettingsRepository.getSettings(any()) } returns Result.Success(
            AutonomySettings(testUserId, emptyMap(), isGlobalPauseActive = false)
        )
        coEvery { appUsageSettingsRepository.getSettings(any()) } returns Result.Success(
            AppUsageObservationSettings(testUserId, isEnabled = false)
        )
        coEvery { activitySettingsRepository.getSettings(any()) } returns Result.Success(
            ActivityObservationSettings(testUserId, isEnabled = false, lastUpdatedAt = 0L)
        )
        coEvery { calendarSettingsRepository.getSettings(any()) } returns Result.Success(
            CalendarObservationSettings(testUserId, isEnabled = false, updatedAt = 0L)
        )
        coEvery { locationSettingsRepository.getSettings(any()) } returns Result.Success(
            LocationObservationSettings(testUserId, isEnabled = false, updatedAt = 0L)
        )

        harness.coordinator.handle(ObservationSchedulingLifecycleEvent.SessionReconciled)

        // No consent must also cause cancellation, zero enqueue of observation work
        verify(atLeast = 2) { workManager.cancelUniqueWork(ObservationScheduler.UNIQUE_WORK_NAME) }
        verify(exactly = 0) {
            workManager.enqueueUniquePeriodicWork(
                ObservationScheduler.UNIQUE_WORK_NAME,
                any(),
                any()
            )
        }
    }

    // Requirement 4.5: Sign-out and user-data erasure still cancel observation work
    @Test
    fun testSignOutAndUserDataErasureCancelObservationWork() = runBlocking {
        val scope = createTestScope()
        val harness = buildWiring(scope, initialAuthUser = testUser)
        val signDeadline = System.currentTimeMillis() + 5_000L
        while (harness.sessionProvider.getSessionGeneration() < 2L && System.currentTimeMillis() < signDeadline) {
            kotlinx.coroutines.delay(50)
        }

        // Sign out
        val signOutResult = authRepository.signOut()
        assertTrue(signOutResult is Result.Success)

        // When unauthenticated, coordinator cancels work
        harness.coordinator.handle(ObservationSchedulingLifecycleEvent.AuthenticationUnavailable)
        verify(atLeast = 1) { workManager.cancelUniqueWork(ObservationScheduler.UNIQUE_WORK_NAME) }

        // Verify while user is marked erased, schedule fails closed
        val erasedProvider: UserSessionProvider = mockk {
            every { getSessionGeneration() } returns 2L
            every { sessionState } returns MutableStateFlow(SessionState.Authenticated("erased_user", isAnonymous = false))
            every { getActiveUserId() } returns "erased_user"
            every { isUserErased("erased_user", 2L) } returns true
        }
        val erasedScheduler = ObservationScheduler(workManager, sessionProvider = { erasedProvider })
        val scheduleResult = erasedScheduler.scheduleObservationCollection()
        assertTrue(scheduleResult is Result.Failure)
    }

    // Requirement 4.6: Stale work remains rejected and cannot write user data
    @Test
    fun testStaleWorkRejectedAndCannotWriteUserData() = runBlocking {
        val scope = createTestScope()
        val harness = buildWiring(scope, initialAuthUser = testUser)
        val staleDeadline = System.currentTimeMillis() + 5_000L
        while (harness.sessionProvider.getSessionGeneration() < 2L && System.currentTimeMillis() < staleDeadline) {
            kotlinx.coroutines.delay(50)
        }

        // Session generation is 2L
        assertEquals(2L, harness.sessionProvider.getSessionGeneration())

        val mockUseCase = mockk<ExecuteObservationCollectionUseCase>()
        var useCaseInvoked = false
        coEvery { mockUseCase.invoke(any()) } coAnswers {
            useCaseInvoked = true
            Result.Success(ObservationCollectionSummary(0, emptyList(), emptyList()))
        }

        // Create worker with stale generation 1L
        val staleParams = mockk<WorkerParameters>(relaxed = true) {
            every { inputData.getLong(ObservationWorker.KEY_SESSION_GENERATION, -1L) } returns 1L
        }

        val worker = ObservationWorker(context, staleParams, mockUseCase, harness.sessionProvider)
        val workerResult = worker.doWork()

        // Fails closed with Result.failure()
        assertEquals(androidx.work.ListenableWorker.Result.failure(), workerResult)
        assertFalse("Stale worker must never execute use case or write data", useCaseInvoked)
    }

    // Requirement 4.7: Cold-start ordering cannot leave observation scheduling permanently bound to an old generation
    @Test
    fun testColdStartOrderingCannotLeaveObservationPermanentlyBoundToOldGeneration() = runBlocking {
        val scope = createTestScope()
        val capturedGenerations = java.util.Collections.synchronizedList(mutableListOf<Long>())
        every {
            workManager.enqueueUniquePeriodicWork(
                ObservationScheduler.UNIQUE_WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                any()
            )
        } answers {
            val req = thirdArg<PeriodicWorkRequest>()
            val gen = req.workSpec.input.getLong(ObservationWorker.KEY_SESSION_GENERATION, -1L)
            capturedGenerations.add(gen)
            mockk()
        }

        // Simulate cold start:
        // 1. Adapter starts immediately and handles ApplicationStarted (while generation is 1L)
        val harness = buildWiring(scope, initialAuthUser = null)

        // Simulate adapter firing before auth reconciliation commits
        harness.observationScheduler.scheduleObservationCollection()

        // Verify first schedule captured generation 1L
        assertEquals(1, capturedGenerations.size)
        assertEquals(1L, capturedGenerations[0])

        // 2. Now auth resolves and reconciliation executes
        authRepository.setTestUser(testUser)
        harness.sessionProvider.retryReconciliation()

        val deadline = System.currentTimeMillis() + 5_000L
        while ((capturedGenerations.size < 2 || capturedGenerations.last() != harness.sessionProvider.getSessionGeneration()) && System.currentTimeMillis() < deadline) {
            kotlinx.coroutines.delay(50)
        }

        // Post-reconciliation hook must have updated the schedule to the current active generation
        assertTrue("Expected schedule update with new generation", capturedGenerations.size >= 2)
        assertTrue(
            "Expected schedule update with a newer session generation",
            capturedGenerations.last() > 1L
        )
        assertEquals(
            harness.sessionProvider.getSessionGeneration(),
            capturedGenerations.last()
        )
    }
}
