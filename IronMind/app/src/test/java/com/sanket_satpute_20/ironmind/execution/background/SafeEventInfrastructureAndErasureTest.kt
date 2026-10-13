package com.sanket_satpute_20.ironmind.execution.background

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.work.WorkerParameters
import com.sanket_satpute_20.ironmind.data.local.IronMindDatabase
import com.sanket_satpute_20.ironmind.data.local.Jdk25CompatibleRobolectricTestRunner
import com.sanket_satpute_20.ironmind.data.local.dao.IronMindDao
import com.sanket_satpute_20.ironmind.data.local.dao.LearnerEventStatusDao
import com.sanket_satpute_20.ironmind.data.local.entity.LearnerEventStatusEntity
import com.sanket_satpute_20.ironmind.data.local.entity.MemoryEntity
import com.sanket_satpute_20.ironmind.data.local.entity.UserProfileEntity
import com.sanket_satpute_20.ironmind.data.repository.DataManagementRepositoryImpl
import com.sanket_satpute_20.ironmind.data.repository.EventRepositoryImpl
import com.sanket_satpute_20.ironmind.domain.ai.InterventionObjective
import com.sanket_satpute_20.ironmind.domain.ai.InterventionType
import com.sanket_satpute_20.ironmind.domain.common.Clock
import com.sanket_satpute_20.ironmind.domain.common.IdGenerator
import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.EntitySource
import com.sanket_satpute_20.ironmind.domain.model.Event
import com.sanket_satpute_20.ironmind.domain.model.EventType
import com.sanket_satpute_20.ironmind.domain.model.intervention.InterventionRecommendation
import com.sanket_satpute_20.ironmind.domain.model.intervention.InterventionRecommendationStatus
import com.sanket_satpute_20.ironmind.domain.repository.InterventionRecommendationRepository
import com.sanket_satpute_20.ironmind.domain.usecase.ai.HandleInterventionResultUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.ai.UpdateInterventionRecommendationStatusUseCase
import com.sanket_satpute_20.ironmind.infrastructure.worker.EventProcessingWorker
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(Jdk25CompatibleRobolectricTestRunner::class)
@Config(sdk = [34])
class SafeEventInfrastructureAndErasureTest {

    private lateinit var context: Context
    private lateinit var db: IronMindDatabase
    private lateinit var ironMindDao: IronMindDao
    private lateinit var learnerEventStatusDao: LearnerEventStatusDao
    private lateinit var eventRepository: EventRepositoryImpl
    private lateinit var dataManagementRepository: DataManagementRepositoryImpl
    private lateinit var workerParams: WorkerParameters

    private val testClock = object : Clock {
        var currentTime = 10_000L
        override fun currentTimeMillis(): Long = currentTime
    }

    private val testIdGenerator = object : IdGenerator {
        var counter = 0
        override fun generateId(): String = "id-${++counter}"
    }

    private class PausableEventProcessor(
        private val onProcessingStarted: CompletableDeferred<Unit>,
        private val allowResume: CompletableDeferred<Unit>,
        private val delegate: EventProcessor = DefaultEventProcessor()
    ) : EventProcessor {
        override suspend fun processEvent(event: Event): EventProcessingOutcome {
            onProcessingStarted.complete(Unit)
            allowResume.await()
            return delegate.processEvent(event)
        }
    }

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        workerParams = mockk(relaxed = true)
        db = Room.inMemoryDatabaseBuilder(context, IronMindDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        ironMindDao = db.ironMindDao()
        learnerEventStatusDao = db.learnerEventStatusDao()

        eventRepository = EventRepositoryImpl(
            dao = ironMindDao,
            logger = null,
            database = db,
            learnerEventStatusDao = learnerEventStatusDao
        )

        dataManagementRepository = DataManagementRepositoryImpl(
            ironMindDao = ironMindDao,
            patternDao = db.patternDao(),
            observationDao = db.observationDao(),
            interventionDao = db.interventionDao(),
            experimentDao = db.experimentDao(),
            autonomySettingsDao = db.autonomySettingsDao(),
            globalAutonomyStateDao = db.globalAutonomyStateDao(),
            learnerEventStatusDao = learnerEventStatusDao,
            database = db,
            workManager = null
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun insertTestUserProfile(userId: String) {
        val profile = UserProfileEntity(
            id = userId,
            createdAt = 1000L,
            updatedAt = 1000L,
            displayName = "Test User",
            timezone = "UTC",
            createdFrom = "ONBOARDING",
            status = "ACTIVE",
            schemaVersion = 1
        )
        ironMindDao.insertUserProfile(profile)
    }

    private fun createStatusEntity(
        eventId: String,
        userId: String,
        status: String = "PENDING",
        leaseToken: String? = null,
        leaseExpiresAt: Long? = null
    ): LearnerEventStatusEntity = LearnerEventStatusEntity(
        eventId = eventId,
        userId = userId,
        status = status,
        claimCount = if (leaseToken != null) 1 else 0,
        failureCount = 0,
        maxAttempts = 5,
        leaseToken = leaseToken,
        leaseExpiresAt = leaseExpiresAt,
        lastAttemptAt = null,
        nextAttemptAt = testClock.currentTimeMillis(),
        errorMessage = null,
        hadMalformedMetadata = false,
        createdAt = testClock.currentTimeMillis(),
        updatedAt = testClock.currentTimeMillis()
    )

    private fun createEvent(
        id: String,
        userId: String,
        type: EventType = EventType.INTERVENTION_ACCEPTED,
        metadata: String? = "type=BREAK_DOWN,recommendation=Take a break"
    ): Event = Event(
        id = id,
        userId = userId,
        type = type,
        entityType = "Task",
        entityId = "task-1",
        occurredAt = testClock.currentTimeMillis(),
        recordedAt = testClock.currentTimeMillis(),
        source = EntitySource.SYSTEM,
        metadata = metadata,
        causationId = "rec-1"
    )

    private fun createWorker(
        eventProcessor: EventProcessor = DefaultEventProcessor()
    ): EventProcessingWorker = EventProcessingWorker(
        appContext = context,
        workerParams = workerParams,
        learnerEventStatusDao = learnerEventStatusDao,
        eventRepository = eventRepository,
        eventProcessor = eventProcessor,
        clock = testClock,
        logger = null,
        database = db,
        ironMindDao = ironMindDao
    )

    @Test
    fun `real SQLite transaction rollback when learner status insertion fails`() = runBlocking {
        val userId = "user-rollback-test"
        insertTestUserProfile(userId)

        val collidingEventId = "event-collision-1"

        // Pre-insert a learner status with this ID to force an SQLite primary key collision
        val existingStatus = createStatusEntity(collidingEventId, userId)
        learnerEventStatusDao.insert(existingStatus)

        // Now attempt to persist event with the colliding ID
        val event = createEvent(collidingEventId, userId)

        val result = eventRepository.saveEventWithLearnerStatus(event)

        // Must result in Failure
        assertTrue("Expected failure due to status insert collision", result is Result.Failure)

        // CRITICAL INVARIANT: The event insertion must have rolled back in SQLite
        val eventInDb = ironMindDao.getEvent(collidingEventId)
        assertNull("Event must not exist in DB because transaction rolled back", eventInDb)
    }

    @Test
    fun `event persistence fails and does not persist when user profile does not exist or is erased`() = runBlocking {
        val erasedUserId = "user-erased-404"
        // Do NOT insert user profile for erasedUserId

        val eventId = "event-erased-test"
        val event = createEvent(eventId, erasedUserId)

        val result = eventRepository.saveEventWithLearnerStatus(event)

        assertTrue("Expected failure when user does not exist", result is Result.Failure)
        val error = (result as Result.Failure).error
        assertTrue(
            "Expected USER_PROFILE_NOT_FOUND error message, got: ${error.message}",
            error.message?.contains("USER_PROFILE_NOT_FOUND") == true
        )

        // Verify neither Event nor LearnerStatus was persisted
        assertNull("Event must not be persisted for erased user", ironMindDao.getEvent(eventId))
        assertNull("Learner status must not be persisted for erased user", learnerEventStatusDao.getEventStatus(eventId))
    }

    @Test
    fun `account erasure is transactional across all user tables including learner_event_status`() = runBlocking {
        val userId = "user-erase-me"
        insertTestUserProfile(userId)

        val eventId = "event-for-erasure"
        val event = createEvent(eventId, userId)
        val saveResult = eventRepository.saveEventWithLearnerStatus(event)
        assertTrue(saveResult is Result.Success)

        // Seed a MemoryEntity
        val memory = MemoryEntity(
            id = "mem-1",
            userId = userId,
            type = "PREFERENCE",
            content = "Prefers morning focus",
            source = "SYSTEM",
            confidence = 0.8f,
            evidenceCount = 1,
            firstObservedAt = 1000L,
            lastObservedAt = 1000L,
            confirmationState = "UNCONFIRMED",
            status = "ACTIVE",
            expiresAt = null,
            createdAt = 1000L,
            updatedAt = 1000L,
            schemaVersion = 1
        )
        ironMindDao.insertMemory(memory)

        // Pre-condition check: tables contain data
        assertNotNull(ironMindDao.getUserProfile(userId))
        assertNotNull(ironMindDao.getEvent(eventId))
        assertNotNull(learnerEventStatusDao.getEventStatus(eventId))
        assertEquals(1, ironMindDao.getMemoriesForUser(userId).size)

        // Execute account erasure
        val eraseResult = dataManagementRepository.deleteAllUserData(userId)
        assertTrue("Erasure should succeed", eraseResult is Result.Success)

        // Post-condition: All tables cleared including learner_event_status
        assertNull("User profile must be deleted", ironMindDao.getUserProfile(userId))
        assertTrue("Events must be cleared", ironMindDao.getEventsForUser(userId).isEmpty())
        assertNull("Learner event status must be erased", learnerEventStatusDao.getEventStatus(eventId))
        assertTrue("Memories must be cleared", ironMindDao.getMemoriesForUser(userId).isEmpty())
    }

    @Test
    fun `concurrent race - worker processing paused at boundary while erasure executes ensures no data is recreated`() = runBlocking {
        val userId = "user-race-worker-concurrent"
        insertTestUserProfile(userId)

        val eventId = "event-worker-race"
        val event = createEvent(eventId, userId, EventType.INTERVENTION_ACCEPTED)
        val saveResult = eventRepository.saveEventWithLearnerStatus(event)
        assertTrue(saveResult is Result.Success)

        // Step 1: Set up synchronization primitives to pause worker mid-processing
        val workerStarted = CompletableDeferred<Unit>()
        val allowResume = CompletableDeferred<Unit>()
        val pausableProcessor = PausableEventProcessor(workerStarted, allowResume)
        val worker = createWorker(pausableProcessor)

        // Launch worker on IO dispatcher
        val workerJob = launch(Dispatchers.IO) {
            worker.doWork()
        }

        // Wait until worker claims the event and reaches the processing boundary
        workerStarted.await()

        // Verify worker has claimed the event and is in PROCESSING state
        val statusWhileProcessing = learnerEventStatusDao.getEventStatus(eventId)
        assertNotNull(statusWhileProcessing)
        assertEquals("PROCESSING", statusWhileProcessing?.status)

        // Step 2: Concurrently execute account erasure while worker is paused mid-processing
        val eraseResult = dataManagementRepository.deleteAllUserData(userId)
        assertTrue("Erasure must succeed", eraseResult is Result.Success)

        // Verify profile is deleted by erasure
        assertNull("User profile must be deleted", ironMindDao.getUserProfile(userId))

        // Step 3: Resume the worker so it enters handleOutcome under database.withTransaction
        allowResume.complete(Unit)

        // Step 4: Await worker completion
        workerJob.join()

        // Step 5: Verify strict invariants:
        // - Worker detected erasure under transaction lock, did NOT recreate user profile or memories
        assertNull("User profile must remain null", ironMindDao.getUserProfile(userId))
        assertTrue("No memories recreated", ironMindDao.getMemoriesForUser(userId).isEmpty())
        assertTrue("No events recreated", ironMindDao.getEventsForUser(userId).isEmpty())
        // - Learner status row does not survive erasure
        assertNull("No learner event status must survive or be recreated", learnerEventStatusDao.getEventStatus(eventId))
    }

    @Test
    fun `concurrent race - event creation competing with account erasure guarantees no orphaned data`() = runBlocking {
        val userId = "user-competing-erasure"
        insertTestUserProfile(userId)

        val eventId = "event-competing"
        val event = createEvent(eventId, userId, EventType.INTERVENTION_ACCEPTED)

        val eventStarted = CompletableDeferred<Unit>()
        val erasureFinished = CompletableDeferred<Unit>()
        val saveResults = mutableListOf<Result<Event, Exception>>()

        val eventJob = launch(Dispatchers.IO) {
            eventStarted.complete(Unit)
            erasureFinished.await()
            val postErasureResult = eventRepository.saveEventWithLearnerStatus(event.copy(id = "event-post-erasure"))
            saveResults.add(postErasureResult)
        }

        eventStarted.await()

        // Execute account erasure
        val eraseResult = dataManagementRepository.deleteAllUserData(userId)
        assertTrue("Erasure must succeed", eraseResult is Result.Success)
        erasureFinished.complete(Unit)

        eventJob.join()

        // Assert post-erasure event creation was rejected
        val postErasureResult = saveResults.first()
        assertTrue("Post-erasure event persistence must fail", postErasureResult is Result.Failure)
        val error = (postErasureResult as Result.Failure).error
        assertTrue(
            "Must report USER_PROFILE_NOT_FOUND",
            error.message?.contains("USER_PROFILE_NOT_FOUND") == true
        )

        // Assert final state: zero orphaned data
        assertNull("User profile must be null", ironMindDao.getUserProfile(userId))
        assertTrue("Events table must be empty", ironMindDao.getEventsForUser(userId).isEmpty())
        assertNull("Learner status must not exist", learnerEventStatusDao.getEventStatus("event-post-erasure"))
    }

    @Test
    fun `concurrent stress race - simultaneous event insertions and account erasure leave zero orphaned data`() = runBlocking {
        val userId = "user-stress-race"
        insertTestUserProfile(userId)

        val coroutineCount = 10
        val startGate = CompletableDeferred<Unit>()

        val jobs = (1..coroutineCount).map { i ->
            launch(Dispatchers.IO) {
                startGate.await()
                val ev = createEvent("event-stress-$i", userId)
                eventRepository.saveEventWithLearnerStatus(ev)
            }
        }

        val eraseJob = launch(Dispatchers.IO) {
            startGate.await()
            dataManagementRepository.deleteAllUserData(userId)
        }

        startGate.complete(Unit)

        jobs.forEach { it.join() }
        eraseJob.join()

        // Final state invariant: zero surviving data for erased user
        assertNull("User profile must be null", ironMindDao.getUserProfile(userId))
        assertTrue("Events must be completely empty", ironMindDao.getEventsForUser(userId).isEmpty())
        for (i in 1..coroutineCount) {
            assertNull("Learner status must not exist for event-stress-$i", learnerEventStatusDao.getEventStatus("event-stress-$i"))
        }
    }

    @Test
    fun `worker lease loss prevents completing event status`() = runBlocking {
        val userId = "user-lease-loss"
        insertTestUserProfile(userId)

        val eventId = "event-lease-loss"
        val event = createEvent(eventId, userId)
        val saveResult = eventRepository.saveEventWithLearnerStatus(event)
        assertTrue(saveResult is Result.Success)

        // Worker 1 claims event
        val tokenWorker1 = "token-worker-1"
        learnerEventStatusDao.claimPendingEvent(
            eventId = eventId,
            now = testClock.currentTimeMillis(),
            leaseExpiresAt = testClock.currentTimeMillis() + 30_000L,
            newLeaseToken = tokenWorker1
        )

        // Lease expires or is stolen by Worker 2
        testClock.currentTime += 35_000L
        val tokenWorker2 = "token-worker-2"
        val stolen = learnerEventStatusDao.claimExpiredProcessingEvent(
            eventId = eventId,
            now = testClock.currentTimeMillis(),
            leaseExpiresAt = testClock.currentTimeMillis() + 30_000L,
            newLeaseToken = tokenWorker2
        )
        assertEquals("Worker 2 should claim expired lease", 1, stolen)

        // Worker 1 attempts to complete event using stale tokenWorker1
        val completedRows = learnerEventStatusDao.completeEvent(eventId, tokenWorker1, testClock.currentTimeMillis())
        assertEquals("Worker 1 complete must affect 0 rows because lease token is stale", 0, completedRows)

        // Current status in DB must still belong to Worker 2
        val currentStatus = learnerEventStatusDao.getEventStatus(eventId)
        assertNotNull(currentStatus)
        assertEquals("token-worker-2", currentStatus?.leaseToken)
        assertEquals("PROCESSING", currentStatus?.status)
    }

    @Test
    fun `noop handling for correction events completes processing atomically without mutating memory or patterns`() = runBlocking {
        val userId = "user-correction-noop"
        insertTestUserProfile(userId)

        val eventId = "event-correction-1"
        val correctionMetadata = "type=BREAK_DOWN,recommendation=Take a break,correctedText=I will take a walk instead"
        val event = createEvent(
            id = eventId,
            userId = userId,
            type = EventType.INTERVENTION_OVERRIDDEN,
            metadata = correctionMetadata
        )
        val saveResult = eventRepository.saveEventWithLearnerStatus(event)
        assertTrue(saveResult is Result.Success)

        // Test DefaultEventProcessor behavior directly
        val processor = DefaultEventProcessor()
        val outcome = processor.processEvent(event)
        assertTrue("Correction event must yield SuccessNoChange", outcome is EventProcessingOutcome.SuccessNoChange)
        assertEquals("UNVERIFIED_CORRECTION_SAFE_NO_OP", (outcome as EventProcessingOutcome.SuccessNoChange).reason)

        // Now run worker to process the event
        val worker = createWorker(processor)
        val workerResult = worker.doWork()
        assertTrue("Worker must succeed", workerResult is androidx.work.ListenableWorker.Result.Success)

        // Verify status moved to SUCCEEDED
        val processedStatus = learnerEventStatusDao.getEventStatus(eventId)
        assertNotNull(processedStatus)
        assertEquals("SUCCEEDED", processedStatus?.status)

        // CRITICAL INVARIANT: Zero Memory, Pattern, or Barrier created
        assertTrue("No memory should be created from unverified correction", ironMindDao.getMemoriesForUser(userId).isEmpty())
        assertTrue("No pattern should be created", db.patternDao().getPatternsForUser(userId).isEmpty())
        assertTrue("No barrier should be created", db.barrierDao().getBarriersByUserId(userId).isEmpty())
    }

    @Test
    fun `HandleInterventionResultUseCase persists atomically and fails when user is erased`() = runBlocking {
        val recRepo = mockk<InterventionRecommendationRepository>(relaxed = true)
        val updateStatusUseCase = UpdateInterventionRecommendationStatusUseCase(recRepo)

        val useCase = HandleInterventionResultUseCase(
            eventRepository = eventRepository,
            updateInterventionRecommendationStatusUseCase = updateStatusUseCase,
            clock = testClock,
            idGenerator = testIdGenerator
        )

        val recommendation = InterventionRecommendation(
            id = "rec-404",
            userId = "user-missing",
            interventionType = InterventionType.BREAK_DOWN,
            objective = InterventionObjective.INITIATE_ACTION,
            targetEntityId = "task-404",
            targetEntityType = "Task",
            suggestedAction = "Take a break",
            rationale = "Long work session",
            status = InterventionRecommendationStatus.PENDING,
            createdAt = testClock.currentTimeMillis(),
            expiresAt = testClock.currentTimeMillis() + 60_000L
        )

        // Case A: User profile does not exist -> must fail and persist nothing
        val failResult = useCase(
            recommendation = recommendation,
            action = HandleInterventionResultUseCase.Action.CORRECT,
            userId = "user-missing",
            correctedText = "I need water"
        )
        assertTrue("Must fail when user does not exist", failResult is Result.Failure)
        assertEquals(0, ironMindDao.getEventsForUser("user-missing").size)

        // Case B: User profile exists -> persists atomically
        val validUserId = "user-valid-rec"
        insertTestUserProfile(validUserId)
        val validRecommendation = recommendation.copy(userId = validUserId)

        val successResult = useCase(
            recommendation = validRecommendation,
            action = HandleInterventionResultUseCase.Action.CORRECT,
            userId = validUserId,
            correctedText = "I need water"
        )
        assertTrue("Must succeed when user exists", successResult is Result.Success)

        val events = ironMindDao.getEventsForUser(validUserId)
        assertEquals(1, events.size)
        val savedEvent = events.first()
        assertEquals(EventType.INTERVENTION_OVERRIDDEN.name, savedEvent.type)

        val learnerStatus = learnerEventStatusDao.getEventStatus(savedEvent.id)
        assertNotNull("Learner status must be persisted atomically", learnerStatus)
        assertEquals("PENDING", learnerStatus?.status)
    }
}
