package com.sanket_satpute_20.ironmind.infrastructure.worker

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.work.Data
import androidx.work.ListenableWorker.Result
import androidx.work.WorkerParameters
import com.sanket_satpute_20.ironmind.data.local.IronMindDatabase
import com.sanket_satpute_20.ironmind.data.local.Jdk25CompatibleRobolectricTestRunner
import com.sanket_satpute_20.ironmind.data.local.dao.LearnerEventStatusDao
import com.sanket_satpute_20.ironmind.data.local.entity.LearnerEventStatusEntity
import com.sanket_satpute_20.ironmind.data.local.entity.toEntity
import com.sanket_satpute_20.ironmind.domain.common.Clock
import com.sanket_satpute_20.ironmind.domain.model.EntitySource
import com.sanket_satpute_20.ironmind.domain.model.Event
import com.sanket_satpute_20.ironmind.domain.model.EventType
import com.sanket_satpute_20.ironmind.domain.session.SessionState
import com.sanket_satpute_20.ironmind.domain.session.UserSessionProvider
import com.sanket_satpute_20.ironmind.execution.background.DefaultEventProcessor
import com.sanket_satpute_20.ironmind.execution.background.EventProcessingOutcome
import com.sanket_satpute_20.ironmind.execution.background.EventProcessor
import com.sanket_satpute_20.ironmind.testutil.fake.FakeEventRepository
import com.sanket_satpute_20.ironmind.testutil.FakeUserSessionProvider
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(Jdk25CompatibleRobolectricTestRunner::class)
@Config(sdk = [34])
class EventProcessingWorkerTest {

    private lateinit var context: Context
    private lateinit var workerParams: WorkerParameters
    private lateinit var db: IronMindDatabase
    private lateinit var dao: LearnerEventStatusDao
    private lateinit var eventRepo: FakeEventRepository
    private lateinit var clock: TestClock
    private lateinit var processor: TestEventProcessor
    private lateinit var userSessionProvider: FakeUserSessionProvider

    class TestClock(var time: Long = 1000L) : Clock {
        override fun currentTimeMillis(): Long = time
    }

    class TestEventProcessor : EventProcessor {
        var outcomeToReturn: EventProcessingOutcome = EventProcessingOutcome.Success
        val processedEvents = mutableListOf<Event>()
        var throwCancellation: Boolean = false

        override suspend fun processEvent(event: Event): EventProcessingOutcome {
            if (throwCancellation) {
                throw CancellationException("Simulated worker cancellation")
            }
            processedEvents.add(event)
            return outcomeToReturn
        }
    }

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        workerParams = mockk(relaxed = true)
        every { workerParams.inputData } returns Data.Builder()
            .putLong(UserSessionProvider.KEY_SESSION_GENERATION, 1L)
            .build()
        db = Room.inMemoryDatabaseBuilder(context, IronMindDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.learnerEventStatusDao()
        eventRepo = FakeEventRepository()
        clock = TestClock(1000L)
        processor = TestEventProcessor()
        userSessionProvider = FakeUserSessionProvider(SessionState.Authenticated("user-123", true))
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun createWorker(
        eventProcessor: EventProcessor = processor,
        batchSize: Int = EventProcessingWorker.DEFAULT_BATCH_SIZE,
        maxDrainIterations: Int = EventProcessingWorker.DEFAULT_MAX_DRAIN_ITERATIONS
    ): EventProcessingWorker {
        return EventProcessingWorker(
            appContext = context,
            workerParams = workerParams,
            learnerEventStatusDao = dao,
            eventRepository = eventRepo,
            eventProcessor = eventProcessor,
            clock = clock,
            logger = null,
            leaseDurationMs = 30_000L,
            batchSize = batchSize,
            maxDrainIterations = maxDrainIterations,
            userSessionProvider = userSessionProvider
        )
    }

    private fun seedStatus(
        id: String,
        status: String = "PENDING",
        claimCount: Int = 0,
        failureCount: Int = 0,
        maxAttempts: Int = 5,
        nextAttemptAt: Long = 1000L,
        leaseToken: String? = null,
        leaseExpiresAt: Long? = null,
        userId: String = "user-123"
    ) = runBlocking {
        dao.insert(
            LearnerEventStatusEntity(
                eventId = id,
                userId = userId,
                status = status,
                claimCount = claimCount,
                failureCount = failureCount,
                maxAttempts = maxAttempts,
                leaseToken = leaseToken,
                leaseExpiresAt = leaseExpiresAt,
                lastAttemptAt = null,
                nextAttemptAt = nextAttemptAt,
                errorMessage = null,
                hadMalformedMetadata = false,
                createdAt = 1000L,
                updatedAt = 1000L
            )
        )
    }

    private fun seedEvent(
        id: String,
        type: EventType = EventType.INTERVENTION_OVERRIDDEN,
        userId: String = "user-123"
    ) = runBlocking {
        eventRepo.saveEvent(
            Event(
                id = id,
                userId = userId,
                type = type,
                entityType = "Intervention",
                entityId = "entity-$id",
                occurredAt = 1000L,
                recordedAt = 1000L,
                source = EntitySource.SYSTEM,
                metadata = "scope=INTERVENTION_CORRECTION",
                causationId = "rec-$id"
            )
        )
        db.ironMindDao().insertEvent(
            Event(
                id = id,
                userId = userId,
                type = type,
                entityType = "Intervention",
                entityId = "entity-$id",
                occurredAt = 1000L,
                recordedAt = 1000L,
                source = EntitySource.SYSTEM,
                metadata = "scope=INTERVENTION_CORRECTION",
                causationId = "rec-$id"
            ).toEntity()
        )
    }

    @Test
    fun `worker does not claim another users event status`() = runBlocking {
        seedStatus("event-other-user", userId = "another-user")
        seedEvent("event-other-user", userId = "another-user")

        val result = createWorker().doWork()

        assertEquals(Result.success(), result)
        assertTrue(processor.processedEvents.isEmpty())
        val untouched = dao.getEventStatus("event-other-user")
        assertEquals("PENDING", untouched?.status)
        assertEquals(0, untouched?.claimCount)
    }

    @Test
    fun `worker excludes a queued event whose owner differs from active session`() = runBlocking {
        seedStatus("event-owner-mismatch", userId = "user-123")
        seedEvent("event-owner-mismatch", userId = "another-user")

        val result = createWorker().doWork()

        assertEquals(Result.success(), result)
        assertTrue(processor.processedEvents.isEmpty())
        val untouched = dao.getEventStatus("event-owner-mismatch")
        assertEquals("PENDING", untouched?.status)
        assertEquals(0, untouched?.claimCount)
    }

    @Test
    fun `successful claim reaches handler and marks event SUCCEEDED with token cleared`() = runBlocking {
        seedStatus("event-1")
        seedEvent("event-1")
        processor.outcomeToReturn = EventProcessingOutcome.Success

        val worker = createWorker()
        val result = worker.doWork()

        assertEquals(Result.success(), result)
        assertEquals(1, processor.processedEvents.size)
        assertEquals("event-1", processor.processedEvents[0].id)

        val persisted = dao.getEventStatus("event-1")
        assertNotNull(persisted)
        assertEquals("SUCCEEDED", persisted?.status)
        assertNull(persisted?.leaseToken)
        assertNull(persisted?.leaseExpiresAt)
        assertEquals(1, persisted?.claimCount)
        assertEquals(0, persisted?.failureCount)
    }

    @Test
    fun `failed claim never reaches handler`() = runBlocking {
        // Claim count is already exhausted at maxAttempts
        seedStatus("event-exhausted", claimCount = 5, maxAttempts = 5)
        seedEvent("event-exhausted")

        val worker = createWorker()
        val result = worker.doWork()

        assertEquals(Result.success(), result)
        assertTrue("Handler must never be reached if claim fails", processor.processedEvents.isEmpty())
        val persisted = dao.getEventStatus("event-exhausted")
        // Sweeper marks it FAILED_PERMANENT due to claim limit exhaustion
        assertEquals("FAILED_PERMANENT", persisted?.status)
    }

    @Test
    fun `retryable failure persists nextAttemptAt and increments failureCount exactly once`() = runBlocking {
        seedStatus("event-retry", failureCount = 0)
        seedEvent("event-retry")
        processor.outcomeToReturn = EventProcessingOutcome.RetryableFailure("TRANSIENT_DB_ERROR")

        val worker = createWorker()
        val result = worker.doWork()

        assertEquals(Result.success(), result)
        val persisted = dao.getEventStatus("event-retry")
        assertEquals("FAILED_RETRYABLE", persisted?.status)
        assertEquals(1, persisted?.failureCount)
        assertEquals(1, persisted?.claimCount)
        assertEquals(clock.time + 30_000L, persisted?.nextAttemptAt)
        assertEquals("TRANSIENT_DB_ERROR", persisted?.errorMessage)
        assertNull(persisted?.leaseToken)
    }

    @Test
    fun `retryable failure transitions to FAILED_PERMANENT when max failure attempts reached`() = runBlocking {
        seedStatus("event-retry-max", failureCount = 4, maxAttempts = 5)
        seedEvent("event-retry-max")
        processor.outcomeToReturn = EventProcessingOutcome.RetryableFailure("PERSISTENT_NETWORK_FAIL")

        val worker = createWorker()
        worker.doWork()

        val persisted = dao.getEventStatus("event-retry-max")
        assertEquals("FAILED_PERMANENT", persisted?.status)
        assertEquals(5, persisted?.failureCount)
        assertTrue(persisted?.errorMessage?.contains("MAX_ATTEMPTS_EXHAUSTED") == true)
        assertNull(persisted?.leaseToken)
    }

    @Test
    fun `permanent failure transitions to FAILED_PERMANENT terminal state`() = runBlocking {
        seedStatus("event-perm")
        seedEvent("event-perm")
        processor.outcomeToReturn = EventProcessingOutcome.PermanentFailure("CORRUPTED_EVENT_DATA")

        val worker = createWorker()
        worker.doWork()

        val persisted = dao.getEventStatus("event-perm")
        assertEquals("FAILED_PERMANENT", persisted?.status)
        assertEquals("CORRUPTED_EVENT_DATA", persisted?.errorMessage)
        assertNull(persisted?.leaseToken)
    }

    @Test
    fun `cooperative cancellation releases lease to PENDING without failure count increment and rethrows`() = runBlocking {
        seedStatus("event-cancel")
        seedEvent("event-cancel")
        processor.throwCancellation = true

        val worker = createWorker()
        try {
            worker.doWork()
            fail("Expected CancellationException")
        } catch (e: CancellationException) {
            // Expected
        }

        val persisted = dao.getEventStatus("event-cancel")
        assertEquals("PENDING", persisted?.status)
        assertEquals(0, persisted?.failureCount)
        assertEquals(1, persisted?.claimCount)
        assertNull(persisted?.leaseToken)
        assertNull(persisted?.leaseExpiresAt)
    }

    @Test
    fun `missing source event leads deterministically to permanent failure`() = runBlocking {
        // Seed status record, but do NOT seed event in eventRepo
        seedStatus("event-missing")

        val worker = createWorker()
        worker.doWork()

        val persisted = dao.getEventStatus("event-missing")
        assertEquals("FAILED_PERMANENT", persisted?.status)
        assertEquals("SOURCE_EVENT_NOT_FOUND", persisted?.errorMessage)
    }

    @Test
    fun `unsupported event type transitions deterministically to FAILED_PERMANENT without false success`() = runBlocking {
        seedStatus("event-unsupported")
        seedEvent("event-unsupported", type = EventType.TASK_CREATED)

        val worker = createWorker(eventProcessor = DefaultEventProcessor())
        worker.doWork()

        val persisted = dao.getEventStatus("event-unsupported")
        assertEquals("FAILED_PERMANENT", persisted?.status)
        assertTrue(persisted?.errorMessage?.contains("UNSUPPORTED_EVENT_TYPE") == true)
        // Ensure it is NEVER falsely marked SUCCEEDED
        assertTrue(persisted?.status != "SUCCEEDED")
    }

    @Test
    fun `reclaims expired processing lease and executes successfully`() = runBlocking {
        // PROCESSING event whose lease expired at 500L (current clock is 1000L)
        seedStatus("event-expired", status = "PROCESSING", claimCount = 1, leaseExpiresAt = 500L, leaseToken = "old-token")
        seedEvent("event-expired")
        processor.outcomeToReturn = EventProcessingOutcome.Success

        val worker = createWorker()
        worker.doWork()

        val persisted = dao.getEventStatus("event-expired")
        assertEquals("SUCCEEDED", persisted?.status)
        assertEquals(2, persisted?.claimCount)
        assertNull(persisted?.leaseToken)
    }

    @Test
    fun `worker drains multiple events bounded by batch limits`() = runBlocking {
        clock.time = 5000L
        for (i in 1..10) {
            seedStatus("event-$i", nextAttemptAt = 1000L + i)
            seedEvent("event-$i")
        }
        processor.outcomeToReturn = EventProcessingOutcome.Success

        // batchSize = 3, maxDrainIterations = 2 -> maximum 6 events processed
        val worker = createWorker(batchSize = 3, maxDrainIterations = 2)
        val result = worker.doWork()

        // Tail-check detects remaining eligible work (events 7-10) and returns Result.retry()
        assertEquals(Result.retry(), result)
        assertEquals(6, processor.processedEvents.size)
        // First 6 events SUCCEEDED
        for (i in 1..6) {
            assertEquals("SUCCEEDED", dao.getEventStatus("event-$i")?.status)
        }
        // Remaining 4 events are still PENDING
        for (i in 7..10) {
            assertEquals("PENDING", dao.getEventStatus("event-$i")?.status)
        }
    }

    @Test
    fun `future retry timestamp is not claimed or processed`() = runBlocking {
        seedStatus("event-future", nextAttemptAt = clock.time + 50_000L)
        seedEvent("event-future")

        val worker = createWorker()
        worker.doWork()

        assertEquals(0, processor.processedEvents.size)
        assertEquals("PENDING", dao.getEventStatus("event-future")?.status)
    }

    @Test
    fun `stale lease token causes zero-row update without corrupting newer state`() = runBlocking {
        seedStatus("event-stale")
        seedEvent("event-stale")

        // Interfere during processing: simulate lease expiration and reclamation by another worker
        val interferingProcessor = object : EventProcessor {
            override suspend fun processEvent(event: Event): EventProcessingOutcome {
                val usurpTime = clock.currentTimeMillis() + 40_000L
                dao.claimExpiredProcessingEvent(
                    eventId = event.id,
                    now = usurpTime,
                    leaseExpiresAt = usurpTime + 60_000L,
                    newLeaseToken = "usurper-token"
                )
                return EventProcessingOutcome.Success
            }
        }

        val worker = createWorker(eventProcessor = interferingProcessor)
        val result = worker.doWork()

        assertEquals(Result.success(), result)
        val persisted = dao.getEventStatus("event-stale")
        // Stale update affects 0 rows and does NOT overwrite the usurper lease token or mark SUCCEEDED!
        assertEquals("PROCESSING", persisted?.status)
        assertEquals("usurper-token", persisted?.leaseToken)
    }

    @Test
    fun `retryable failure becomes eligible after backoff expires and succeeds on next run`() = runBlocking {
        seedStatus("event-retry-flow", failureCount = 0)
        seedEvent("event-retry-flow")

        // Run 1: Fails with RetryableFailure
        processor.outcomeToReturn = EventProcessingOutcome.RetryableFailure("TRANSIENT_NETWORK_ERR")
        val worker1 = createWorker()
        val result1 = worker1.doWork()

        assertEquals(Result.success(), result1)
        val afterRun1 = dao.getEventStatus("event-retry-flow")
        assertEquals("FAILED_RETRYABLE", afterRun1?.status)
        assertEquals(1, afterRun1?.failureCount)
        assertEquals(clock.time + 30_000L, afterRun1?.nextAttemptAt)

        // Run 2: Advance clock by only 10s -> Still ineligible (1000 + 10_000 < 1000 + 30_000)
        clock.time += 10_000L
        processor.outcomeToReturn = EventProcessingOutcome.Success
        val worker2 = createWorker()
        val result2 = worker2.doWork()

        assertEquals(Result.success(), result2)
        val afterRun2 = dao.getEventStatus("event-retry-flow")
        assertEquals("FAILED_RETRYABLE", afterRun2?.status)
        assertEquals(1, afterRun2?.claimCount) // Claim count unchanged!

        // Run 3: Advance clock past backoff (+25s more, total +35s >= nextAttemptAt) -> Eligible!
        clock.time += 25_000L
        val worker3 = createWorker()
        val result3 = worker3.doWork()

        assertEquals(Result.success(), result3)
        val afterRun3 = dao.getEventStatus("event-retry-flow")
        assertEquals("SUCCEEDED", afterRun3?.status)
        assertEquals(2, afterRun3?.claimCount)
        assertEquals(1, afterRun3?.failureCount)
        assertNull(afterRun3?.leaseToken)
    }

    @Test
    fun `event arriving after initial discovery but before exit is processed within drain loop`() = runBlocking {
        seedStatus("event-initial")
        seedEvent("event-initial")

        // When processing event-initial, simulate another event arriving concurrently
        val dynamicProcessor = object : EventProcessor {
            val processed = mutableListOf<String>()
            override suspend fun processEvent(event: Event): EventProcessingOutcome {
                processed.add(event.id)
                if (event.id == "event-initial") {
                    // Seed new event into DB while worker is actively running
                    seedStatus("event-concurrent", nextAttemptAt = clock.time)
                    seedEvent("event-concurrent")
                }
                return EventProcessingOutcome.Success
            }
        }

        val worker = createWorker(eventProcessor = dynamicProcessor, batchSize = 1, maxDrainIterations = 3)
        val result = worker.doWork()

        assertEquals(Result.success(), result)
        assertEquals(listOf("event-initial", "event-concurrent"), dynamicProcessor.processed)
        assertEquals("SUCCEEDED", dao.getEventStatus("event-initial")?.status)
        assertEquals("SUCCEEDED", dao.getEventStatus("event-concurrent")?.status)
    }

    @Test
    fun `permanent failure remains terminal and is never re-claimed even after time advances`() = runBlocking {
        seedStatus("event-terminal", status = "FAILED_PERMANENT", claimCount = 1, failureCount = 1)
        seedEvent("event-terminal")

        clock.time += 1_000_000L
        val worker = createWorker()
        val result = worker.doWork()

        assertEquals(Result.success(), result)
        assertEquals(0, processor.processedEvents.size)
        val persisted = dao.getEventStatus("event-terminal")
        assertEquals("FAILED_PERMANENT", persisted?.status)
        assertEquals(1, persisted?.claimCount)
    }
}
