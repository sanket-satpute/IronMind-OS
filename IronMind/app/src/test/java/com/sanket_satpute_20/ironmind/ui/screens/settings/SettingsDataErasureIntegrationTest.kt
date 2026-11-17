package com.sanket_satpute_20.ironmind.ui.screens.settings

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.sanket_satpute_20.ironmind.data.local.IronMindDatabase
import com.sanket_satpute_20.ironmind.data.local.entity.GoalEntity
import com.sanket_satpute_20.ironmind.data.local.entity.OutboxEntity
import com.sanket_satpute_20.ironmind.data.local.entity.PlanEntity
import com.sanket_satpute_20.ironmind.data.local.entity.TaskEntity
import com.sanket_satpute_20.ironmind.data.repository.DataManagementRepositoryImpl
import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.AutonomySettings
import com.sanket_satpute_20.ironmind.domain.model.AppUsageObservationSettings
import com.sanket_satpute_20.ironmind.domain.model.CalendarObservationSettings
import com.sanket_satpute_20.ironmind.domain.model.LocationObservationSettings
import com.sanket_satpute_20.ironmind.domain.model.NotificationObservationSettings
import com.sanket_satpute_20.ironmind.domain.model.ActivityObservationSettings
import com.sanket_satpute_20.ironmind.domain.provider.ActivityObservationProvider
import com.sanket_satpute_20.ironmind.domain.provider.AppUsageObservationProvider
import com.sanket_satpute_20.ironmind.domain.provider.CalendarObservationProvider
import com.sanket_satpute_20.ironmind.domain.provider.LocationObservationProvider
import com.sanket_satpute_20.ironmind.domain.provider.NotificationObservationProvider
import com.sanket_satpute_20.ironmind.domain.repository.AuthRepository
import com.sanket_satpute_20.ironmind.domain.session.SessionState
import com.sanket_satpute_20.ironmind.domain.usecase.autonomy.GetAutonomySettingsUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.autonomy.ToggleGlobalPauseUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.data.DeleteUserDataUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.observation.GetActivityObservationSettingsUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.observation.GetAppUsageObservationSettingsUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.observation.GetCalendarObservationSettingsUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.observation.GetLocationObservationSettingsUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.observation.GetNotificationObservationSettingsUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.observation.SetActivityObservationEnabledUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.observation.SetAppUsageObservationEnabledUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.observation.SetCalendarObservationEnabledUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.observation.SetLocationObservationEnabledUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.observation.SetNotificationObservationEnabledUseCase
import com.sanket_satpute_20.ironmind.testutil.FakeUserSessionProvider
import com.sanket_satpute_20.ironmind.testutil.TestDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SettingsDataErasureIntegrationTest {

    @get:Rule
    val dispatcherRule = TestDispatcherRule()

    private lateinit var getAutonomySettingsUseCase: GetAutonomySettingsUseCase
    private lateinit var toggleGlobalPauseUseCase: ToggleGlobalPauseUseCase
    private lateinit var getAppUsageObservationSettingsUseCase: GetAppUsageObservationSettingsUseCase
    private lateinit var setAppUsageObservationEnabledUseCase: SetAppUsageObservationEnabledUseCase
    private lateinit var appUsageObservationProvider: AppUsageObservationProvider
    private lateinit var getNotificationObservationSettingsUseCase: GetNotificationObservationSettingsUseCase
    private lateinit var setNotificationObservationEnabledUseCase: SetNotificationObservationEnabledUseCase
    private lateinit var notificationObservationProvider: NotificationObservationProvider
    private lateinit var getCalendarObservationSettingsUseCase: GetCalendarObservationSettingsUseCase
    private lateinit var setCalendarObservationEnabledUseCase: SetCalendarObservationEnabledUseCase
    private lateinit var calendarObservationProvider: CalendarObservationProvider
    private lateinit var getLocationObservationSettingsUseCase: GetLocationObservationSettingsUseCase
    private lateinit var setLocationObservationEnabledUseCase: SetLocationObservationEnabledUseCase
    private lateinit var locationObservationProvider: LocationObservationProvider
    private lateinit var getActivityObservationSettingsUseCase: GetActivityObservationSettingsUseCase
    private lateinit var setActivityObservationEnabledUseCase: SetActivityObservationEnabledUseCase
    private lateinit var activityObservationProvider: ActivityObservationProvider

    private lateinit var mockDeleteUserDataUseCase: DeleteUserDataUseCase
    private lateinit var sessionProvider: FakeUserSessionProvider
    private lateinit var viewModel: SettingsViewModel

    private lateinit var context: Context
    private lateinit var db: IronMindDatabase

    @Before
    fun setUp() {
        getAutonomySettingsUseCase = mockk(relaxed = true)
        toggleGlobalPauseUseCase = mockk(relaxed = true)
        getAppUsageObservationSettingsUseCase = mockk(relaxed = true)
        setAppUsageObservationEnabledUseCase = mockk(relaxed = true)
        appUsageObservationProvider = mockk(relaxed = true)
        getNotificationObservationSettingsUseCase = mockk(relaxed = true)
        setNotificationObservationEnabledUseCase = mockk(relaxed = true)
        notificationObservationProvider = mockk(relaxed = true)
        getCalendarObservationSettingsUseCase = mockk(relaxed = true)
        setCalendarObservationEnabledUseCase = mockk(relaxed = true)
        calendarObservationProvider = mockk(relaxed = true)
        getLocationObservationSettingsUseCase = mockk(relaxed = true)
        setLocationObservationEnabledUseCase = mockk(relaxed = true)
        locationObservationProvider = mockk(relaxed = true)
        getActivityObservationSettingsUseCase = mockk(relaxed = true)
        setActivityObservationEnabledUseCase = mockk(relaxed = true)
        activityObservationProvider = mockk(relaxed = true)

        coEvery { getAutonomySettingsUseCase(any()) } returns Result.Success(
            AutonomySettings(userId = "user-1", levels = emptyMap(), isGlobalPauseActive = false)
        )
        coEvery { getAppUsageObservationSettingsUseCase(any()) } returns Result.Success(
            AppUsageObservationSettings(userId = "user-1", isEnabled = true)
        )
        coEvery { getNotificationObservationSettingsUseCase(any()) } returns Result.Success(
            NotificationObservationSettings(userId = "user-1", isEnabled = true)
        )
        coEvery { getCalendarObservationSettingsUseCase(any()) } returns Result.Success(
            CalendarObservationSettings(userId = "user-1", isEnabled = true, updatedAt = 1000L)
        )
        coEvery { getLocationObservationSettingsUseCase(any()) } returns Result.Success(
            LocationObservationSettings(userId = "user-1", isEnabled = true, updatedAt = 1000L)
        )
        coEvery { getActivityObservationSettingsUseCase(any()) } returns Result.Success(
            ActivityObservationSettings(userId = "user-1", isEnabled = true, lastUpdatedAt = 1000L)
        )

        mockDeleteUserDataUseCase = mockk(relaxed = true)
        sessionProvider = FakeUserSessionProvider(SessionState.Authenticated("user-1", false))

        viewModel = SettingsViewModel(
            getAutonomySettingsUseCase = getAutonomySettingsUseCase,
            toggleGlobalPauseUseCase = toggleGlobalPauseUseCase,
            getAppUsageObservationSettingsUseCase = getAppUsageObservationSettingsUseCase,
            setAppUsageObservationEnabledUseCase = setAppUsageObservationEnabledUseCase,
            appUsageObservationProvider = appUsageObservationProvider,
            getNotificationObservationSettingsUseCase = getNotificationObservationSettingsUseCase,
            setNotificationObservationEnabledUseCase = setNotificationObservationEnabledUseCase,
            notificationObservationProvider = notificationObservationProvider,
            getCalendarObservationSettingsUseCase = getCalendarObservationSettingsUseCase,
            setCalendarObservationEnabledUseCase = setCalendarObservationEnabledUseCase,
            calendarObservationProvider = calendarObservationProvider,
            getLocationObservationSettingsUseCase = getLocationObservationSettingsUseCase,
            setLocationObservationEnabledUseCase = setLocationObservationEnabledUseCase,
            locationObservationProvider = locationObservationProvider,
            getActivityObservationSettingsUseCase = getActivityObservationSettingsUseCase,
            setActivityObservationEnabledUseCase = setActivityObservationEnabledUseCase,
            activityObservationProvider = activityObservationProvider,
            userSessionProvider = sessionProvider,
            deleteUserDataUseCase = mockDeleteUserDataUseCase
        )

        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, IronMindDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun test1_confirmationCancellationPerformsNoDeletion() = runTest {
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isErasureConfirmationVisible)

        viewModel.requestErasureConfirmation()
        assertTrue(viewModel.uiState.value.isErasureConfirmationVisible)

        viewModel.dismissErasureConfirmation()
        assertFalse(viewModel.uiState.value.isErasureConfirmationVisible)

        coVerify(exactly = 0) { mockDeleteUserDataUseCase.invoke(any()) }
    }

    @Test
    fun test2_confirmedActionInvokesUseCaseExactlyOnce() = runTest {
        advanceUntilIdle()
        coEvery { mockDeleteUserDataUseCase.invoke("user-1") } returns Result.Success(Unit)

        viewModel.requestErasureConfirmation()
        assertTrue(viewModel.uiState.value.isErasureConfirmationVisible)

        viewModel.eraseLocalData()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isErasureConfirmationVisible)
        coVerify(exactly = 1) { mockDeleteUserDataUseCase.invoke("user-1") }
    }

    @Test
    fun test3_progressAndRepeatedTapProtection() = runTest {
        advanceUntilIdle()

        val deferred = CompletableDeferred<Result<Unit, Exception>>()
        coEvery { mockDeleteUserDataUseCase.invoke("user-1") } coAnswers {
            deferred.await()
        }

        viewModel.eraseLocalData()
        assertTrue("isErasingData must be true during in-flight operation", viewModel.uiState.value.isErasingData)

        // Attempting repeated tap while in-flight must be ignored
        viewModel.eraseLocalData()
        viewModel.requestErasureConfirmation()
        assertFalse("Confirmation dialog must not open while erasing", viewModel.uiState.value.isErasureConfirmationVisible)

        // Complete the operation
        deferred.complete(Result.Success(Unit))
        advanceUntilIdle()

        assertFalse("isErasingData must return to false after completion", viewModel.uiState.value.isErasingData)
        coVerify(exactly = 1) { mockDeleteUserDataUseCase.invoke(any()) }
    }

    @Test
    fun test4_successfulErasureUpdatesUiStateCorrectly() = runTest {
        advanceUntilIdle()
        coEvery { mockDeleteUserDataUseCase.invoke("user-1") } returns Result.Success(Unit)

        viewModel.eraseLocalData()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isErasingData)
        assertFalse(state.isErasureConfirmationVisible)
        assertEquals("Local IronMind data has been safely erased.", state.erasureSuccessMessage)
        assertNull(state.erasureErrorMessage)

        viewModel.dismissErasureMessages()
        assertNull(viewModel.uiState.value.erasureSuccessMessage)
    }

    @Test
    fun test5_failedErasureDoesNotShowFalseSuccess() = runTest {
        advanceUntilIdle()
        val errorMsg = "SQLiteException: database is locked"
        coEvery { mockDeleteUserDataUseCase.invoke("user-1") } returns Result.Failure(IllegalStateException(errorMsg))

        viewModel.eraseLocalData()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isErasingData)
        assertNull("erasureSuccessMessage must be null on failure", state.erasureSuccessMessage)
        assertEquals(errorMsg, state.erasureErrorMessage)

        viewModel.dismissErasureMessages()
        assertNull(viewModel.uiState.value.erasureErrorMessage)
    }

    @Test
    fun test6_existingOwnershipIsolationAndSessionSafeguardsRemainIntact() = runTest {
        advanceUntilIdle()

        // Transition session from user-1 to user-2
        sessionProvider.setState(SessionState.Authenticated("user-2", false))
        advanceUntilIdle()

        coEvery { mockDeleteUserDataUseCase.invoke("user-2") } returns Result.Success(Unit)

        viewModel.eraseLocalData()
        advanceUntilIdle()

        coVerify(exactly = 1) { mockDeleteUserDataUseCase.invoke("user-2") }
        coVerify(exactly = 0) { mockDeleteUserDataUseCase.invoke("user-1") }
    }

    @Test
    fun test7_realUseCaseAtomicRoomErasureAndOutboxCleanup() = runTest {
        // Seed auth-user-1 data in Room
        val now = 1000L
        val testUser1 = "auth-user-1"
        val testUser2 = "auth-user-2"

        db.ironMindDao().insertGoal(
            GoalEntity(
                id = "goal-1",
                userId = testUser1,
                ambitionId = null,
                title = "Goal 1",
                description = "Desc",
                why = "Why",
                importance = 5,
                status = "ACTIVE",
                targetAt = null,
                startedAt = null,
                completedAt = null,
                createdAt = now,
                updatedAt = now,
                schemaVersion = 1
            )
        )
        db.ironMindDao().insertPlan(
            PlanEntity(
                id = "plan-1",
                userId = testUser1,
                goalId = "goal-1",
                ambitionId = null,
                title = "Plan 1",
                description = "Desc",
                status = "ACTIVE",
                createdAt = now,
                updatedAt = now,
                startedAt = null,
                completedAt = null,
                source = "USER",
                schemaVersion = 1
            )
        )
        db.ironMindDao().insertTask(
            TaskEntity(
                id = "task-1",
                userId = testUser1,
                goalId = "goal-1",
                planId = "plan-1",
                parentTaskId = null,
                title = "Task 1",
                description = "Desc",
                status = "PENDING",
                priority = 1,
                estimatedDurationMinutes = 30,
                scheduledAt = null,
                dueAt = null,
                createdAt = now,
                updatedAt = now,
                completedAt = null,
                postponedAt = null,
                source = "USER",
                schemaVersion = 1
            )
        )
        db.outboxDao().insertEntry(
            OutboxEntity(
                operationId = "op-1",
                entityType = "TASK",
                entityId = "task-1",
                operationType = "UPSERT",
                payload = "{\"id\":\"task-1\",\"userId\":\"$testUser1\"}",
                createdAt = now,
                retryCount = 0,
                lastAttemptAt = null,
                status = "PENDING"
            )
        )

        // Seed auth-user-2 data in Room (multi-tenant guard)
        db.ironMindDao().insertGoal(
            GoalEntity(
                id = "goal-2",
                userId = testUser2,
                ambitionId = null,
                title = "Goal 2",
                description = "Desc",
                why = "Why",
                importance = 5,
                status = "ACTIVE",
                targetAt = null,
                startedAt = null,
                completedAt = null,
                createdAt = now,
                updatedAt = now,
                schemaVersion = 1
            )
        )
        db.outboxDao().insertEntry(
            OutboxEntity(
                operationId = "op-2",
                entityType = "GOAL",
                entityId = "goal-2",
                operationType = "UPSERT",
                payload = "{\"id\":\"goal-2\",\"userId\":\"$testUser2\"}",
                createdAt = now,
                retryCount = 0,
                lastAttemptAt = null,
                status = "PENDING"
            )
        )

        val dataMgmtRepo = DataManagementRepositoryImpl(
            ironMindDao = db.ironMindDao(),
            patternDao = db.patternDao(),
            observationDao = db.observationDao(),
            interventionDao = db.interventionDao(),
            experimentDao = db.experimentDao(),
            autonomySettingsDao = db.autonomySettingsDao(),
            globalAutonomyStateDao = db.globalAutonomyStateDao(),
            database = db,
            outboxDao = db.outboxDao()
        )

        val realSessionProvider = FakeUserSessionProvider(SessionState.Authenticated(testUser1, false))
        val fakeAuthRepo = mockk<AuthRepository>(relaxed = true)
        coEvery { fakeAuthRepo.signOut() } returns Result.Success(Unit)

        val realUseCase = DeleteUserDataUseCase(
            dataManagementRepository = dataMgmtRepo,
            userSessionProvider = realSessionProvider,
            authRepository = fakeAuthRepo
        )

        // Perform real erasure for auth-user-1
        val result = realUseCase(testUser1)
        assertTrue("Expected Result.Success but was $result", result is Result.Success)

        // Verify auth-user-1 data is deleted
        assertNull("auth-user-1 goal must be deleted", db.ironMindDao().getGoal("goal-1"))
        assertNull("auth-user-1 plan must be deleted", db.ironMindDao().getPlan("plan-1"))
        assertNull("auth-user-1 task must be deleted", db.ironMindDao().getTask("task-1"))
        assertFalse("auth-user-1 outbox entry must be deleted", db.outboxDao().entryExists("op-1"))

        // Verify auth-user-2 data is strictly preserved
        assertNotNull("auth-user-2 goal must NOT be deleted", db.ironMindDao().getGoal("goal-2"))
        assertTrue("auth-user-2 outbox entry must NOT be deleted", db.outboxDao().entryExists("op-2"))

        // Verify auth-user-1 is marked erased in session admission fence
        assertTrue(realSessionProvider.isUserErased(testUser1))
    }
}

