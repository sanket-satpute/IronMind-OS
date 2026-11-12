package com.sanket_satpute_20.ironmind.domain.engine

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.sanket_satpute_20.ironmind.data.local.IronMindDatabase
import com.sanket_satpute_20.ironmind.data.local.Jdk25CompatibleRobolectricTestRunner
import com.sanket_satpute_20.ironmind.data.repository.EventRepositoryImpl
import com.sanket_satpute_20.ironmind.data.repository.OutcomeRepositoryImpl
import com.sanket_satpute_20.ironmind.data.repository.TaskRepositoryImpl
import com.sanket_satpute_20.ironmind.domain.common.IdGenerator
import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.EntitySource
import com.sanket_satpute_20.ironmind.domain.model.Event
import com.sanket_satpute_20.ironmind.domain.model.EventType
import com.sanket_satpute_20.ironmind.domain.model.Outcome
import com.sanket_satpute_20.ironmind.domain.model.ResultStatus
import com.sanket_satpute_20.ironmind.domain.model.Task
import com.sanket_satpute_20.ironmind.domain.model.TaskStatus
import com.sanket_satpute_20.ironmind.domain.model.context.ExecutionTimeBucket
import com.sanket_satpute_20.ironmind.domain.model.context.SourceAvailability
import com.sanket_satpute_20.ironmind.domain.model.context.TemporalSampleSource
import com.sanket_satpute_20.ironmind.domain.model.observation.FactualContextSnapshot
import com.sanket_satpute_20.ironmind.domain.repository.CommitmentRepository
import com.sanket_satpute_20.ironmind.domain.repository.EventRepository
import com.sanket_satpute_20.ironmind.domain.repository.GoalRepository
import com.sanket_satpute_20.ironmind.domain.repository.OutcomeRepository
import com.sanket_satpute_20.ironmind.domain.repository.PatternRepository
import com.sanket_satpute_20.ironmind.domain.repository.ProtectionRepository
import com.sanket_satpute_20.ironmind.domain.repository.ReflectionRepository
import com.sanket_satpute_20.ironmind.domain.usecase.observation.BuildFactualContextSnapshotUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.task.EditTaskUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.task.UpdateTaskStatusUseCase
import com.sanket_satpute_20.ironmind.testutil.fake.FakeClock
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.ZoneId
import java.time.ZonedDateTime

@RunWith(Jdk25CompatibleRobolectricTestRunner::class)
@Config(sdk = [34])
class ContextEngineTemporalIntegrationRoomTest {

    private lateinit var context: Context
    private lateinit var db: IronMindDatabase
    private lateinit var taskRepository: TaskRepositoryImpl
    private lateinit var eventRepository: EventRepositoryImpl
    private lateinit var outcomeRepository: OutcomeRepositoryImpl
    private lateinit var clock: FakeClock
    private lateinit var idGenerator: IdGenerator
    private var idSeq = 0

    private lateinit var commitmentRepository: CommitmentRepository
    private lateinit var buildFactualContextSnapshotUseCase: BuildFactualContextSnapshotUseCase
    private lateinit var reflectionRepository: ReflectionRepository
    private lateinit var protectionRepository: ProtectionRepository
    private lateinit var goalRepository: GoalRepository
    private lateinit var patternRepository: PatternRepository

    private val userA = "user-temporal-a"
    private val userB = "user-temporal-b"
    private val zoneUtc = ZoneId.of("UTC")

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, IronMindDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        idSeq = 0
        idGenerator = object : IdGenerator {
            override fun generateId(): String = "id-${++idSeq}"
        }
        clock = FakeClock(ZonedDateTime.of(2026, 3, 10, 9, 0, 0, 0, zoneUtc).toInstant().toEpochMilli())

        taskRepository = TaskRepositoryImpl(dao = db.ironMindDao(), database = db)
        eventRepository = EventRepositoryImpl(dao = db.ironMindDao(), database = db)
        outcomeRepository = OutcomeRepositoryImpl(dao = db.ironMindDao())

        commitmentRepository = mockk(relaxed = true)
        buildFactualContextSnapshotUseCase = mockk(relaxed = true)
        reflectionRepository = mockk(relaxed = true)
        protectionRepository = mockk(relaxed = true)
        goalRepository = mockk(relaxed = true)
        patternRepository = mockk(relaxed = true)

        coEvery { commitmentRepository.getCommitmentsForUser(any()) } returns Result.Success(emptyList())
        coEvery { buildFactualContextSnapshotUseCase(any(), any(), any()) } returns Result.Success(mockk<FactualContextSnapshot>(relaxed = true))
        coEvery { reflectionRepository.getReflectionsForDateRange(any(), any(), any()) } returns Result.Success(emptyList())
        coEvery { protectionRepository.getActiveProtectionSessionsForUser(any()) } returns Result.Success(emptyList())
        coEvery { protectionRepository.getProtectionSessionsForUser(any()) } returns Result.Success(emptyList())
        coEvery { protectionRepository.getProtectionRulesForUser(any()) } returns Result.Success(emptyList())
        coEvery { goalRepository.getGoalsForUser(any()) } returns Result.Success(emptyList())
        coEvery { patternRepository.getPatternsForUser(any()) } returns Result.Success(emptyList())
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun createEngine(
        eventsRepo: EventRepository = eventRepository,
        outcomesRepo: OutcomeRepository? = outcomeRepository,
        zoneId: ZoneId = zoneUtc
    ) = ContextEngineImpl(
        clock = clock,
        commitmentRepository = commitmentRepository,
        eventRepository = eventsRepo,
        buildFactualContextSnapshotUseCase = buildFactualContextSnapshotUseCase,
        reflectionRepository = reflectionRepository,
        protectionRepository = protectionRepository,
        goalRepository = goalRepository,
        patternRepository = patternRepository,
        taskRepository = taskRepository,
        outcomeRepository = outcomesRepo,
        zoneId = zoneId
    )

    @Test
    fun `end to end task lifecycle and forward reschedule use cases feed Room-backed executionTiming without double counting`() = runBlocking {
        val windowStart = ZonedDateTime.of(2026, 3, 10, 0, 0, 0, 0, zoneUtc).toInstant().toEpochMilli()
        val scheduledMorning = ZonedDateTime.of(2026, 3, 10, 9, 0, 0, 0, zoneUtc).toInstant().toEpochMilli()
        val dueMorning = scheduledMorning + 3_600_000L // 10:00 UTC

        val initialTask = Task(
            id = "task-1",
            userId = userA,
            title = "Write architecture draft",
            description = "Core draft",
            status = TaskStatus.PENDING,
            priority = 2,
            estimatedDurationMinutes = 45,
            scheduledAt = scheduledMorning,
            dueAt = dueMorning,
            createdAt = windowStart,
            updatedAt = windowStart,
            source = EntitySource.USER
        )
        assertTrue(taskRepository.saveTask(initialTask) is Result.Success)

        val updateStatus = UpdateTaskStatusUseCase(taskRepository, clock, eventRepository, idGenerator)
        val editTask = EditTaskUseCase(taskRepository, clock, eventRepository, idGenerator)

        // Title/priority/duration edit must emit 0 TASK_POSTPONED events
        clock.setTime(scheduledMorning - 1_800_000L) // 08:30
        assertTrue(editTask("task-1", title = "Updated draft title", priority = 1, estimatedDurationMinutes = 60) is Result.Success)

        // Single forward reschedule moving BOTH scheduledAt and dueAt must count as exactly 1 postponement
        val rescheduledStart = scheduledMorning + 1_800_000L // 09:30
        val rescheduledDue = dueMorning + 3_600_000L // 11:00
        assertTrue(editTask("task-1", scheduledAt = rescheduledStart, dueAt = rescheduledDue) is Result.Success)

        // Start 15m after rescheduledStart -> +900_000ms start delay (09:45 UTC = MORNING)
        clock.setTime(rescheduledStart + 900_000L)
        assertTrue(updateStatus("task-1", TaskStatus.IN_PROGRESS) is Result.Success)

        // Complete 20m early before rescheduledDue -> -1_200_000ms completion delay (10:40 UTC = MORNING)
        clock.setTime(rescheduledDue - 1_200_000L)
        assertTrue(updateStatus("task-1", TaskStatus.COMPLETED) is Result.Success)

        // Also seed another user's task & event to verify strict user isolation
        val otherTask = initialTask.copy(id = "task-other", userId = userB, completedAt = rescheduledDue)
        assertTrue(taskRepository.saveTask(otherTask) is Result.Success)
        assertTrue(
            eventRepository.saveEvent(
                Event("ev-other", userB, EventType.TASK_COMPLETED, "TASK", "task-other", rescheduledDue, rescheduledDue, source = EntitySource.USER)
            ) is Result.Success
        )

        val windowEnd = rescheduledDue + 600_000L
        clock.setTime(windowEnd)
        val res = createEngine().getUnifiedContext(userA, windowStart, windowEnd)
        assertTrue(res is Result.Success)
        val unified = (res as Result.Success).data
        val timing = unified.productivity.executionTiming

        assertTrue(timing.hasSufficientData)
        assertEquals(1, timing.startDelaySamples.size)
        assertEquals(900_000L, timing.averageStartDelayMs)
        assertEquals(1, timing.completionDelaySamples.size)
        assertEquals(-1_200_000L, timing.averageCompletionDelayMs)
        assertEquals(TemporalSampleSource.EVENT_DERIVED, timing.completionDelaySamples.first().source)
        assertEquals(1, timing.forwardPostponementCount)
        assertEquals(1, timing.provenance.eventDerivedPostponementCount)
        assertEquals(listOf("task-1"), timing.postponedTaskIds)
        assertEquals(mapOf(ExecutionTimeBucket.MORNING to 1), timing.startTimeDistribution)
        assertEquals(mapOf(ExecutionTimeBucket.MORNING to 1), timing.completionTimeDistribution)
        assertEquals(mapOf(ExecutionTimeBucket.MORNING to 2), timing.totalExecutionTimeDistribution)

        // Check downstream Pattern discovery compatibility without crashes
        val patternEngine = BehaviourPatternDiscoveryEngine(clock = clock)
        val patterns = patternEngine.discoverPatterns(unified, existingPatterns = emptyList(), zoneId = zoneUtc)
        assertNotNull(patterns)
    }

    @Test
    fun `window event query retains more than 15 events and preserves execution and protection events beyond general cap`() = runBlocking {
        val windowStart = ZonedDateTime.of(2026, 3, 10, 0, 0, 0, 0, zoneUtc).toInstant().toEpochMilli()
        val tScheduled = windowStart + 3_600_000L
        val tStarted = tScheduled + 300_000L
        val tProtectionOverride = tStarted + 300_000L
        val tCompleted = tStarted + 1_200_000L

        assertTrue(
            taskRepository.saveTask(
                Task(
                    id = "t-early",
                    userId = userA,
                    title = "Early task before burst",
                    description = "",
                    status = TaskStatus.COMPLETED,
                    priority = 2,
                    scheduledAt = tScheduled,
                    dueAt = tCompleted + 600_000L,
                    createdAt = windowStart,
                    updatedAt = tCompleted,
                    completedAt = tCompleted,
                    source = EntitySource.USER
                )
            ) is Result.Success
        )

        assertTrue(
            eventRepository.saveEvent(
                Event("ev-start", userA, EventType.TASK_STARTED, "TASK", "t-early", tStarted, tStarted, source = EntitySource.USER)
            ) is Result.Success
        )
        assertTrue(
            eventRepository.saveEvent(
                Event("ev-prot", userA, EventType.PROTECTION_OVERRIDDEN, "PROTECTION", "sess-1", tProtectionOverride, tProtectionOverride, source = EntitySource.USER)
            ) is Result.Success
        )
        assertTrue(
            eventRepository.saveEvent(
                Event("ev-done", userA, EventType.TASK_COMPLETED, "TASK", "t-early", tCompleted, tCompleted, source = EntitySource.USER)
            ) is Result.Success
        )

        // Emit 260 newer SYNC_COMPLETED events (> ContextEngineImpl.MAX_WINDOW_EVENTS = 250)
        for (i in 1..260) {
            val ts = tCompleted + i * 1_000L
            assertTrue(
                eventRepository.saveEvent(
                    Event("ev-sync-$i", userA, EventType.SYNC_COMPLETED, "SYNC", "s-$i", ts, ts, source = EntitySource.SYSTEM)
                ) is Result.Success
            )
        }

        // Also store 15 in-window outcomes (> previous .take(10) cap) + 1 out-of-window outcome
        for (i in 1..15) {
            val ts = tCompleted + i * 500L
            assertTrue(
                outcomeRepository.saveOutcome(
                    Outcome("out-$i", userA, "t-early", "TASK", ResultStatus.POSITIVE, 20, ts, ts)
                ) is Result.Success
            )
        }
        assertTrue(
            outcomeRepository.saveOutcome(
                Outcome("out-old", userA, "t-old", "TASK", ResultStatus.POSITIVE, 20, windowStart - 10_000L, windowStart - 10_000L)
            ) is Result.Success
        )

        val windowEnd = tCompleted + 300_000L
        clock.setTime(windowEnd)
        val result = createEngine().getUnifiedContext(userA, windowStart, windowEnd)
        assertTrue(result is Result.Success)
        val unified = (result as Result.Success).data

        // Outcomes: all 15 in-window outcomes retained, old one excluded
        assertEquals(15, unified.productivity.recentOutcomes.size)
        assertFalse(unified.productivity.recentOutcomes.any { it.id == "out-old" })

        // Events: >15 retained AND truncation reported explicitly AND execution/protection prioritized
        assertTrue(unified.productivity.recentEvents.size > ContextEngineImpl.MAX_WINDOW_EVENTS)
        assertTrue(unified.productivity.recentEvents.any { it.id == "ev-start" })
        assertTrue(unified.productivity.recentEvents.any { it.id == "ev-done" })
        assertTrue(unified.digitalBehaviour.recentProtectionEvents.any { it.id == "ev-prot" })
        assertTrue(unified.productivity.executionTiming.provenance.isEventHistoryTruncated)
        assertEquals(1, unified.productivity.executionTiming.startDelaySamples.size)
        assertEquals(300_000L, unified.productivity.executionTiming.averageStartDelayMs)
        assertTrue(unified.provenanceMap["Events"]?.notes?.contains("truncated", ignoreCase = true) == true)
    }

    @Test
    fun `event and outcome repository failures do not invalidate valid context and preserve task timestamp fallbacks`() = runBlocking {
        val windowStart = ZonedDateTime.of(2026, 3, 10, 0, 0, 0, 0, zoneUtc).toInstant().toEpochMilli()
        val due = windowStart + 7_200_000L
        val completed = due + 1_800_000L

        assertTrue(
            taskRepository.saveTask(
                Task(
                    id = "t-fallback",
                    userId = userA,
                    title = "Historical completed task",
                    description = "",
                    status = TaskStatus.COMPLETED,
                    priority = 2,
                    dueAt = due,
                    createdAt = windowStart,
                    updatedAt = completed,
                    completedAt = completed,
                    source = EntitySource.USER
                )
            ) is Result.Success
        )

        val failingEventsRepo = mockk<EventRepository>(relaxed = true)
        coEvery { failingEventsRepo.getEventsForTimeWindow(any(), any(), any(), any(), any(), any()) } returns
            Result.Failure(IllegalStateException("Event table lock"))

        val failingOutcomesRepo = mockk<OutcomeRepository>(relaxed = true)
        coEvery { failingOutcomesRepo.getOutcomesForUser(any()) } returns
            Result.Failure(IllegalStateException("Outcome query failed"))

        val windowEnd = completed + 3_600_000L
        clock.setTime(windowEnd)
        val res = createEngine(eventsRepo = failingEventsRepo, outcomesRepo = failingOutcomesRepo)
            .getUnifiedContext(userA, windowStart, windowEnd)

        assertTrue(res is Result.Success)
        val unified = (res as Result.Success).data
        assertEquals(SourceAvailability.UNAVAILABLE_HARDWARE, unified.provenanceMap["Events"]?.availability)
        assertEquals(SourceAvailability.UNAVAILABLE_HARDWARE, unified.provenanceMap["Outcomes"]?.availability)
        assertFalse(unified.productivity.executionTiming.provenance.eventRepositoryAvailable)
        assertEquals(1, unified.productivity.executionTiming.completionDelaySamples.size)
        assertEquals(TemporalSampleSource.TASK_TIMESTAMP_FALLBACK, unified.productivity.executionTiming.completionDelaySamples.first().source)
        assertEquals(1_800_000L, unified.productivity.executionTiming.averageCompletionDelayMs)
    }
}
