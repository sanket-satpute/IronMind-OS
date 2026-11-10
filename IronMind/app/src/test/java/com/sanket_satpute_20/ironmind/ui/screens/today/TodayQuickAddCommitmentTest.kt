package com.sanket_satpute_20.ironmind.ui.screens.today

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.sanket_satpute_20.ironmind.data.local.IronMindDatabase
import com.sanket_satpute_20.ironmind.data.local.dao.OutboxDao
import com.sanket_satpute_20.ironmind.data.local.entity.OutboxEntity
import com.sanket_satpute_20.ironmind.data.repository.CommitmentRepositoryImpl
import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.CommitmentStatus
import com.sanket_satpute_20.ironmind.domain.model.OutboxOperationType
import com.sanket_satpute_20.ironmind.domain.model.SyncStatus
import com.sanket_satpute_20.ironmind.domain.session.SessionState
import com.sanket_satpute_20.ironmind.domain.usecase.ai.GetPendingInterventionRecommendationsUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.ai.HandleInterventionResultUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.ai.OrchestrateInterventionGenerationUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.commitment.CancelCommitmentScheduleUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.commitment.CreateCommitmentUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.commitment.GetActiveCommitmentsUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.commitment.ScheduleCommitmentUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.commitment.UpdateCommitmentStatusUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.task.GetDueTasksUseCase
import com.sanket_satpute_20.ironmind.testutil.FakeUserSessionProvider
import com.sanket_satpute_20.ironmind.testutil.TestDispatcherRule
import com.sanket_satpute_20.ironmind.testutil.fake.FakeClock
import com.sanket_satpute_20.ironmind.testutil.fake.FakeCommitmentRepository
import com.sanket_satpute_20.ironmind.testutil.fake.FakeEventRepository
import com.sanket_satpute_20.ironmind.testutil.fake.FakeIdGenerator
import com.sanket_satpute_20.ironmind.testutil.fake.FakePlanRepository
import com.sanket_satpute_20.ironmind.testutil.fake.FakeReminderScheduler
import com.sanket_satpute_20.ironmind.testutil.fake.FakeTaskRepository
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
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
@Config(sdk = [34], application = android.app.Application::class)
class TodayQuickAddCommitmentTest {

    @get:Rule
    val dispatcherRule = TestDispatcherRule()

    private lateinit var clock: FakeClock
    private lateinit var idGenerator: FakeIdGenerator
    private lateinit var eventRepository: FakeEventRepository
    private lateinit var reminderScheduler: FakeReminderScheduler
    private lateinit var sessionProvider: FakeUserSessionProvider

    private class DebounceAwareCommitmentRepository(
        private val delegate: FakeCommitmentRepository = FakeCommitmentRepository()
    ) : com.sanket_satpute_20.ironmind.domain.repository.CommitmentRepository by delegate {
        var delayMs: Long = 0L
        override suspend fun saveCommitment(commitment: com.sanket_satpute_20.ironmind.domain.model.Commitment): Result<Unit, Exception> {
            if (delayMs > 0L) kotlinx.coroutines.delay(delayMs)
            return delegate.saveCommitment(commitment)
        }
        var shouldFail: Boolean
            get() = delegate.shouldFail
            set(value) { delegate.shouldFail = value }
    }

    // ViewModel fakes
    private lateinit var fakeCommitmentRepository: DebounceAwareCommitmentRepository
    private lateinit var taskRepository: FakeTaskRepository
    private lateinit var planRepository: FakePlanRepository

    private lateinit var getActiveCommitmentsUseCase: GetActiveCommitmentsUseCase
    private lateinit var updateCommitmentStatusUseCase: UpdateCommitmentStatusUseCase
    private lateinit var scheduleCommitmentUseCase: ScheduleCommitmentUseCase
    private lateinit var cancelCommitmentScheduleUseCase: CancelCommitmentScheduleUseCase
    private lateinit var createCommitmentUseCase: CreateCommitmentUseCase
    private lateinit var getDueTasksUseCase: GetDueTasksUseCase

    private lateinit var viewModel: TodayViewModel

    // Real database fixtures for outbox atomicity and persistence verification
    private lateinit var context: Context
    private lateinit var db: IronMindDatabase
    private lateinit var delegatingOutboxDao: DelegatingFailingOutboxDao
    private lateinit var realCommitmentRepository: CommitmentRepositoryImpl
    private lateinit var realCreateCommitmentUseCase: CreateCommitmentUseCase

    private class DelegatingFailingOutboxDao(private val delegate: OutboxDao) : OutboxDao by delegate {
        var shouldFail: Boolean = false
        override fun insertEntry(entry: OutboxEntity) {
            if (shouldFail) {
                throw android.database.sqlite.SQLiteException("Simulated outbox write failure")
            }
            delegate.insertEntry(entry)
        }
    }

    @Before
    fun setUp() {
        clock = FakeClock(1794068400000L) // Fixed timestamp: Nov 6, 2026
        idGenerator = FakeIdGenerator("comm-")
        eventRepository = FakeEventRepository()
        reminderScheduler = FakeReminderScheduler()
        sessionProvider = FakeUserSessionProvider(SessionState.Authenticated("user-1", isAnonymous = false))

        fakeCommitmentRepository = DebounceAwareCommitmentRepository()
        taskRepository = FakeTaskRepository()
        planRepository = FakePlanRepository()

        getActiveCommitmentsUseCase = GetActiveCommitmentsUseCase(fakeCommitmentRepository)
        updateCommitmentStatusUseCase = mockk(relaxed = true)
        scheduleCommitmentUseCase = mockk(relaxed = true)
        cancelCommitmentScheduleUseCase = mockk(relaxed = true)
        createCommitmentUseCase = CreateCommitmentUseCase(
            repository = fakeCommitmentRepository,
            reminderScheduler = reminderScheduler,
            idGenerator = idGenerator,
            clock = clock,
            eventRepository = eventRepository
        )
        getDueTasksUseCase = GetDueTasksUseCase(
            taskRepository = taskRepository,
            planRepository = planRepository,
            clock = clock
        )

        val getPendingRecommendations = mockk<GetPendingInterventionRecommendationsUseCase>(relaxed = true)
        io.mockk.every { getPendingRecommendations(any(), any()) } returns flowOf(emptyList())

        val handleInterventionResultUseCase = mockk<HandleInterventionResultUseCase>(relaxed = true)
        val orchestrateInterventionGenerationUseCase = mockk<OrchestrateInterventionGenerationUseCase>(relaxed = true)

        viewModel = TodayViewModel(
            getActiveCommitmentsUseCase = getActiveCommitmentsUseCase,
            updateCommitmentStatusUseCase = updateCommitmentStatusUseCase,
            scheduleCommitmentUseCase = scheduleCommitmentUseCase,
            cancelCommitmentScheduleUseCase = cancelCommitmentScheduleUseCase,
            getPendingInterventionRecommendationsUseCase = getPendingRecommendations,
            handleInterventionResultUseCase = handleInterventionResultUseCase,
            orchestrateInterventionGenerationUseCase = orchestrateInterventionGenerationUseCase,
            userSessionProvider = sessionProvider,
            createCommitmentUseCase = createCommitmentUseCase,
            getDueTasksUseCase = getDueTasksUseCase
        )

        // Real Room DB for persistence & outbox verification
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, IronMindDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        delegatingOutboxDao = DelegatingFailingOutboxDao(db.outboxDao())
        realCommitmentRepository = CommitmentRepositoryImpl(
            dao = db.ironMindDao(),
            outboxDao = delegatingOutboxDao,
            database = db,
            idGenerator = idGenerator
        )
        realCreateCommitmentUseCase = CreateCommitmentUseCase(
            repository = realCommitmentRepository,
            reminderScheduler = reminderScheduler,
            idGenerator = idGenerator,
            clock = clock,
            eventRepository = eventRepository
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `successful standalone commitment creation persists commitment in Room and Outbox`() = runTest {
        // Execute real use case against Room DB and Outbox
        val result = realCreateCommitmentUseCase(
            userId = "user-1",
            goalId = null,
            planId = null,
            taskId = null,
            parentCommitmentId = null,
            title = "Drink 2L Water",
            description = "Health hydration goal",
            priority = 2,
            scheduledStartAt = null,
            scheduledEndAt = null,
            initialStatus = CommitmentStatus.COMMITTED
        )

        assertTrue(result is Result.Success)
        val createdCommitment = (result as Result.Success).data

        // 1. Verify entity values
        assertEquals("Drink 2L Water", createdCommitment.title)
        assertEquals("Health hydration goal", createdCommitment.description)
        assertEquals(2, createdCommitment.priority)
        assertEquals("user-1", createdCommitment.userId)
        assertEquals(CommitmentStatus.COMMITTED, createdCommitment.status)
        assertEquals(clock.currentTimeMillis(), createdCommitment.committedAt)
        assertNull("Standalone commitment must have null goalId", createdCommitment.goalId)
        assertNull("Standalone commitment must have null planId", createdCommitment.planId)
        assertNull("Standalone commitment must have null taskId", createdCommitment.taskId)
        assertNull("Standalone commitment must have null parentCommitmentId", createdCommitment.parentCommitmentId)

        // 2. Verify Room persistence
        val commitmentsInDb = db.ironMindDao().getCommitmentsForUser("user-1")
        assertEquals(1, commitmentsInDb.size)
        val entityInDb = commitmentsInDb[0]
        assertEquals("Drink 2L Water", entityInDb.title)
        assertEquals(CommitmentStatus.COMMITTED.name, entityInDb.status)

        // 3. Verify Outbox table
        val outboxEntries = db.outboxDao().getPendingEntries()
        assertEquals(1, outboxEntries.size)
        val outbox = outboxEntries[0]
        assertEquals("Commitment", outbox.entityType)
        assertEquals(createdCommitment.id, outbox.entityId)
        assertEquals(OutboxOperationType.UPSERT.name, outbox.operationType)
        assertEquals(SyncStatus.PENDING.name, outbox.status)

        val json = JSONObject(outbox.payload)
        assertEquals("Drink 2L Water", json.getString("title"))
        assertEquals("COMMITTED", json.getString("status"))
        assertFalse("Outbox payload should not contain null taskId", json.has("taskId"))
        assertFalse("Outbox payload should not contain null planId", json.has("planId"))
        assertFalse("Outbox payload should not contain null goalId", json.has("goalId"))
    }

    @Test
    fun `outbox failure rolls back Room commitment atomically`() = runTest {
        delegatingOutboxDao.shouldFail = true

        val result = realCreateCommitmentUseCase(
            userId = "user-1",
            goalId = null,
            planId = null,
            taskId = null,
            parentCommitmentId = null,
            title = "Failing task",
            description = "Desc",
            priority = 1,
            scheduledStartAt = null,
            scheduledEndAt = null,
            initialStatus = CommitmentStatus.COMMITTED
        )

        assertTrue(result is Result.Failure)

        // Verify transaction rolled back: nothing in Room commitment table
        val allCommitments = db.ironMindDao().getCommitmentsForUser("user-1")
        assertTrue("Commitment must be rolled back on outbox failure", allCommitments.isEmpty())
        assertTrue("No outbox entry should be recorded", db.outboxDao().getPendingEntries().isEmpty())
    }

    @Test
    fun `viewModel quick add creates commitment and updates uiState`() = runTest {
        advanceUntilIdle()

        viewModel.openQuickAdd()
        assertTrue(viewModel.showQuickAddDialog.value)

        viewModel.updateQuickAddTitle("Morning Walk")
        viewModel.updateQuickAddDescription("30 mins cardio")
        viewModel.updateQuickAddPriority(3)

        viewModel.saveQuickAddCommitment()
        advanceUntilIdle()

        assertFalse(viewModel.showQuickAddDialog.value)
        val successState = viewModel.uiState.value as TodayUiState.Success
        assertEquals("Commitment added for today", successState.userMessage)
        assertEquals(1, successState.activeCommitments.size)
        assertEquals("Morning Walk", successState.activeCommitments[0].title)
        assertEquals(3, successState.activeCommitments[0].priority)
    }

    @Test
    fun `quick add validates non-blank title and prevents submission`() = runTest {
        advanceUntilIdle()

        viewModel.openQuickAdd()
        viewModel.updateQuickAddTitle("   ")

        viewModel.saveQuickAddCommitment()
        advanceUntilIdle()

        assertTrue("Dialog must remain open on validation failure", viewModel.showQuickAddDialog.value)
        assertEquals("Title cannot be empty", viewModel.quickAddError.value)

        val successState = viewModel.uiState.value as TodayUiState.Success
        assertTrue(successState.activeCommitments.isEmpty())
    }

    @Test
    fun `duplicate submission debounce prevents concurrent creation`() = runTest {
        advanceUntilIdle()

        fakeCommitmentRepository.delayMs = 500L
        viewModel.openQuickAdd()
        viewModel.updateQuickAddTitle("Read chapter 4")

        viewModel.saveQuickAddCommitment()
        assertTrue("isQuickAdding must be true while saving in-flight", viewModel.isQuickAdding.value)
        viewModel.saveQuickAddCommitment() // Concurrent call while in-flight
        advanceUntilIdle()

        assertFalse("isQuickAdding must be reset after completion", viewModel.isQuickAdding.value)
        val successState = viewModel.uiState.value as TodayUiState.Success
        assertEquals(1, successState.activeCommitments.size)
    }

    @Test
    fun `repository failure surfaces error message without displaying false success`() = runTest {
        advanceUntilIdle()

        fakeCommitmentRepository.shouldFail = true

        viewModel.openQuickAdd()
        viewModel.updateQuickAddTitle("Failing task")

        viewModel.saveQuickAddCommitment()
        advanceUntilIdle()

        assertTrue(viewModel.showQuickAddDialog.value)
        assertNotNull(viewModel.quickAddError.value)
        assertTrue(viewModel.quickAddError.value!!.contains("Fake failure"))

        fakeCommitmentRepository.shouldFail = false
        val allCommitments = (fakeCommitmentRepository.getActiveCommitmentsForUser("user-1", listOf(CommitmentStatus.COMMITTED)) as Result.Success).data
        assertTrue(allCommitments.isEmpty())
    }

    @Test
    fun `closeQuickAdd dismisses dialog and clears error`() = runTest {
        advanceUntilIdle()

        viewModel.openQuickAdd()
        viewModel.updateQuickAddTitle("   ")
        viewModel.saveQuickAddCommitment()
        assertEquals("Title cannot be empty", viewModel.quickAddError.value)

        viewModel.closeQuickAdd()
        assertFalse(viewModel.showQuickAddDialog.value)
        assertNull(viewModel.quickAddError.value)
    }
}
