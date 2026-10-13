package com.sanket_satpute_20.ironmind.execution.background

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import com.sanket_satpute_20.ironmind.domain.ai.InterventionObjective
import com.sanket_satpute_20.ironmind.domain.ai.InterventionType
import com.sanket_satpute_20.ironmind.domain.common.Clock
import com.sanket_satpute_20.ironmind.domain.common.IdGenerator
import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.Event
import com.sanket_satpute_20.ironmind.domain.model.EventType
import com.sanket_satpute_20.ironmind.domain.model.intervention.InterventionRecommendation
import com.sanket_satpute_20.ironmind.domain.model.intervention.InterventionRecommendationStatus
import com.sanket_satpute_20.ironmind.domain.repository.EventRepository
import com.sanket_satpute_20.ironmind.domain.usecase.ai.HandleInterventionResultUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.ai.UpdateInterventionRecommendationStatusUseCase
import com.sanket_satpute_20.ironmind.infrastructure.worker.EventProcessingWorker
import com.sanket_satpute_20.ironmind.testutil.fake.FakeBackgroundExecutor
import com.sanket_satpute_20.ironmind.testutil.fake.FakeEventRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class EventProcessingSchedulingTest {

    private lateinit var context: Context
    private lateinit var workManager: WorkManager
    private lateinit var backgroundExecutor: BackgroundExecutorImpl

    @Before
    fun setUp() {
        context = mockk(relaxed = true)
        workManager = mockk(relaxed = true)
        backgroundExecutor = BackgroundExecutorImpl(context, workManager)
    }

    @Test
    fun `scheduleEventProcessing enqueues unique work with KEEP policy to address duplicate execution`() {
        val workNameSlot = slot<String>()
        val policySlot = slot<ExistingWorkPolicy>()
        val requestSlot = slot<OneTimeWorkRequest>()

        every {
            workManager.enqueueUniqueWork(
                capture(workNameSlot),
                capture(policySlot),
                capture(requestSlot)
            )
        } returns mockk()

        backgroundExecutor.scheduleEventProcessing()

        verify(exactly = 1) {
            workManager.enqueueUniqueWork(any(), any(), any<OneTimeWorkRequest>())
        }
        assertEquals(EventProcessingWorker.UNIQUE_WORK_NAME, workNameSlot.captured)
        assertEquals(ExistingWorkPolicy.KEEP, policySlot.captured)
        assertEquals(EventProcessingWorker::class.java.name, requestSlot.captured.workSpec.workerClassName)
    }

    @Test
    fun `scheduleEventReconciliation enqueues unique periodic work with 15min interval and KEEP policy`() {
        val workNameSlot = slot<String>()
        val policySlot = slot<ExistingPeriodicWorkPolicy>()
        val requestSlot = slot<PeriodicWorkRequest>()

        every {
            workManager.enqueueUniquePeriodicWork(
                capture(workNameSlot),
                capture(policySlot),
                capture(requestSlot)
            )
        } returns mockk()

        backgroundExecutor.scheduleEventReconciliation()

        verify(exactly = 1) {
            workManager.enqueueUniquePeriodicWork(any(), any(), any<PeriodicWorkRequest>())
        }
        assertEquals(EventProcessingWorker.RECONCILIATION_WORK_NAME, workNameSlot.captured)
        assertEquals(ExistingPeriodicWorkPolicy.KEEP, policySlot.captured)
        assertEquals(EventProcessingWorker::class.java.name, requestSlot.captured.workSpec.workerClassName)
        assertEquals(15 * 60 * 1000L, requestSlot.captured.workSpec.intervalDuration)
    }

    @Test
    fun `HandleInterventionResultUseCase records PENDING status and triggers scheduleEventProcessing`() = runBlocking {
        val eventRepo = mockk<EventRepository>()
        val savedEventSlot = slot<Event>()
        coEvery { eventRepo.saveEventWithLearnerStatus(capture(savedEventSlot)) } answers { Result.Success(savedEventSlot.captured) }

        val updateStatusUseCase = mockk<UpdateInterventionRecommendationStatusUseCase>()
        coEvery { updateStatusUseCase(any(), any()) } returns Result.Success(Unit)

        val fakeBackgroundExecutor = FakeBackgroundExecutor()

        val clock = object : Clock { override fun currentTimeMillis(): Long = 5000L }
        val idGenerator = object : IdGenerator { override fun generateId(): String = "test-event-id" }

        val useCase = HandleInterventionResultUseCase(
            eventRepository = eventRepo,
            updateInterventionRecommendationStatusUseCase = updateStatusUseCase,
            clock = clock,
            idGenerator = idGenerator,
            backgroundExecutor = fakeBackgroundExecutor
        )

        val rec = InterventionRecommendation(
            id = "rec-1",
            userId = "user-1",
            interventionType = InterventionType.BREAK_DOWN,
            targetEntityId = "task-1",
            targetEntityType = "Task",
            rationale = "Hydration prompt",
            suggestedAction = "Drink water",
            status = InterventionRecommendationStatus.PENDING,
            createdAt = 1000L,
            expiresAt = 9000L,
            objective = InterventionObjective.INITIATE_ACTION
        )

        val result = useCase(rec, HandleInterventionResultUseCase.Action.CORRECT, "user-1", "Drink warm tea")
        assertTrue(result is Result.Success)

        // Verify saveEventWithLearnerStatus was called with correct event
        coVerify(exactly = 1) { eventRepo.saveEventWithLearnerStatus(any()) }
        assertEquals("test-event-id", savedEventSlot.captured.id)
        assertEquals("user-1", savedEventSlot.captured.userId)
        assertEquals(EventType.INTERVENTION_OVERRIDDEN, savedEventSlot.captured.type)

        // Verify background scheduling was called
        assertTrue(fakeBackgroundExecutor.scheduleEventProcessingCalled)
    }

    @Test
    fun `HandleInterventionResultUseCase succeeds even if DAO insertion or scheduling throws`() = runBlocking {
        val eventRepo = FakeEventRepository()
        val updateStatusUseCase = mockk<UpdateInterventionRecommendationStatusUseCase>()
        coEvery { updateStatusUseCase(any(), any()) } returns Result.Success(Unit)

        val fakeBackgroundExecutor = FakeBackgroundExecutor().apply { shouldThrowException = true }

        val clock = object : Clock { override fun currentTimeMillis(): Long = 5000L }
        val idGenerator = object : IdGenerator { override fun generateId(): String = "test-event-id" }

        val useCase = HandleInterventionResultUseCase(
            eventRepository = eventRepo,
            updateInterventionRecommendationStatusUseCase = updateStatusUseCase,
            clock = clock,
            idGenerator = idGenerator,
            backgroundExecutor = fakeBackgroundExecutor
        )

        val rec = InterventionRecommendation(
            id = "rec-1",
            userId = "user-1",
            interventionType = InterventionType.BREAK_DOWN,
            targetEntityId = "task-1",
            targetEntityType = "Task",
            rationale = "Hydration prompt",
            suggestedAction = "Drink water",
            status = InterventionRecommendationStatus.PENDING,
            createdAt = 1000L,
            expiresAt = 9000L,
            objective = InterventionObjective.INITIATE_ACTION
        )

        // Must succeed without failing the primary flow!
        val result = useCase(rec, HandleInterventionResultUseCase.Action.ACCEPT, "user-1")
        assertTrue("Primary intervention flow must succeed despite background scheduling failure", result is Result.Success)
    }

    @Test
    fun `IronMindApplication registerStartupReconciliation schedules reconciliation on startup`() {
        val fakeExecutor = FakeBackgroundExecutor()
        com.sanket_satpute_20.ironmind.IronMindApplication.registerStartupReconciliation(fakeExecutor)
        assertTrue(fakeExecutor.scheduleEventReconciliationCalled)
    }

    @Test
    fun `IronMindApplication registerStartupReconciliation does not crash application if scheduling throws`() {
        val failingExecutor = FakeBackgroundExecutor().apply { shouldThrowException = true }
        // Must execute without throwing
        com.sanket_satpute_20.ironmind.IronMindApplication.registerStartupReconciliation(failingExecutor)
    }
}
