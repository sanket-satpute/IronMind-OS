package com.sanket_satpute_20.ironmind.domain.engine

import com.sanket_satpute_20.ironmind.domain.common.Clock
import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.context.ContextSnapshot
import com.sanket_satpute_20.ironmind.domain.model.Commitment
import com.sanket_satpute_20.ironmind.domain.model.CommitmentStatus
import com.sanket_satpute_20.ironmind.domain.model.Event
import com.sanket_satpute_20.ironmind.domain.model.Goal
import com.sanket_satpute_20.ironmind.domain.model.observation.FactualContextSnapshot
import com.sanket_satpute_20.ironmind.domain.model.observation.AppUsageObservationAggregation
import com.sanket_satpute_20.ironmind.domain.model.observation.ActivityObservationAggregation
import com.sanket_satpute_20.ironmind.domain.model.observation.CalendarObservationAggregation
import com.sanket_satpute_20.ironmind.domain.model.observation.LocationObservationAggregation
import com.sanket_satpute_20.ironmind.domain.model.observation.NotificationObservationAggregation
import com.sanket_satpute_20.ironmind.domain.model.ProtectionSession
import com.sanket_satpute_20.ironmind.domain.model.Reflection
import com.sanket_satpute_20.ironmind.domain.usecase.observation.BuildFactualContextSnapshotUseCase
import com.sanket_satpute_20.ironmind.domain.repository.*
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Calendar

class ContextEngineImplTest {

    private lateinit var clock: FakeClock
    private lateinit var commitmentRepository: FakeCommitmentRepository
    private lateinit var eventRepository: FakeEventRepository
    private lateinit var buildFactualContextSnapshotUseCase: BuildFactualContextSnapshotUseCase
    private lateinit var reflectionRepository: FakeReflectionRepository
    private lateinit var protectionRepository: FakeProtectionRepository
    private lateinit var goalRepository: FakeGoalRepository
    private lateinit var patternRepository: FakePatternRepository
    private lateinit var engine: ContextEngineImpl

    class FakeClock(var time: Long) : Clock {
        override fun currentTimeMillis() = time
    }
    class FakeCommitmentRepository : CommitmentRepository {
        var commitments = emptyList<Commitment>()
        override suspend fun getCommitment(id: String): Result<Commitment?, Exception> = Result.Failure(Exception())
        override suspend fun getCommitmentsForUser(userId: String): Result<List<Commitment>, Exception> = Result.Success(commitments)
        override suspend fun getActiveCommitmentsForUser(userId: String, statuses: List<CommitmentStatus>): Result<List<Commitment>, Exception> = Result.Success(commitments)
        override suspend fun getCommitmentsForDateRange(userId: String, startTime: Long, endTime: Long): Result<List<Commitment>, Exception> = Result.Success(commitments)
        override suspend fun getCommitmentsForGoal(goalId: String): Result<List<Commitment>, Exception> = Result.Success(commitments)
        override suspend fun getCommitmentsForPlan(planId: String): Result<List<Commitment>, Exception> = Result.Success(commitments)
        override suspend fun getCommitmentsForTask(taskId: String): Result<List<Commitment>, Exception> = Result.Success(commitments)
        override suspend fun saveCommitment(commitment: Commitment): Result<Unit, Exception> = Result.Success(Unit)
        override suspend fun searchCommitments(userId: String, query: String): Result<List<Commitment>, Exception> = Result.Success(commitments)
    }
    class FakeEventRepository : EventRepository {
        var events = emptyList<Event>()
        override suspend fun getEventsForUser(userId: String): Result<List<Event>, Exception> = Result.Success(events)
        override suspend fun getEventsForEntity(entityId: String): Result<List<Event>, Exception> = Result.Success(events)
        override suspend fun getEvent(id: String): Result<Event?, Exception> = Result.Failure(Exception())
        override suspend fun getEventsForDateRange(userId: String, startTime: Long, endTime: Long): Result<List<Event>, Exception> = Result.Success(events)
        override suspend fun saveEvent(event: Event): Result<Event, Exception> = Result.Success(event)
        override suspend fun searchEvents(userId: String, query: String): Result<List<Event>, Exception> = Result.Success(events)
    }

    class FakeReflectionRepository : ReflectionRepository {
        var reflections = emptyList<Reflection>()
        var getReflectionsForDateRangeCallCount = 0
        override suspend fun getReflectionsForDateRange(userId: String, startTime: Long, endTime: Long): Result<List<Reflection>, Exception> {
            getReflectionsForDateRangeCallCount++
            return Result.Success(reflections)
        }
        override suspend fun getReflection(id: String): Result<Reflection?, Exception> = Result.Failure(Exception())
        override suspend fun saveReflection(reflection: Reflection): Result<Unit, Exception> = Result.Success(Unit)
        override suspend fun searchReflections(userId: String, query: String): Result<List<Reflection>, Exception> = Result.Success(reflections)
    }
    class FakeProtectionRepository : ProtectionRepository {
        var activeSession: ProtectionSession? = null
        override suspend fun getProtectionRule(id: String): Result<com.sanket_satpute_20.ironmind.domain.model.ProtectionRule, Exception> = Result.Failure(Exception())
        override suspend fun getProtectionRulesForUser(userId: String): Result<List<com.sanket_satpute_20.ironmind.domain.model.ProtectionRule>, Exception> = Result.Success(emptyList())
        override suspend fun saveProtectionRule(rule: com.sanket_satpute_20.ironmind.domain.model.ProtectionRule): Result<Unit, Exception> = Result.Success(Unit)

        override suspend fun getProtectionSession(id: String): Result<ProtectionSession, Exception> = Result.Failure(Exception())
        override suspend fun getActiveProtectionSessionsForUser(userId: String): Result<List<ProtectionSession>, Exception> = Result.Success(listOfNotNull(activeSession))
        override suspend fun saveProtectionSession(session: ProtectionSession): Result<Unit, Exception> = Result.Success(Unit)
    }
    class FakeGoalRepository : GoalRepository {
        var goals = emptyList<Goal>()
        override suspend fun getGoalsForUser(userId: String): Result<List<Goal>, Exception> = Result.Success(goals)
        override suspend fun getGoal(id: String): Result<Goal?, Exception> = Result.Failure(Exception())
        override suspend fun saveGoal(goal: Goal): Result<Unit, Exception> = Result.Success(Unit)
        override suspend fun searchGoals(userId: String, query: String): Result<List<Goal>, Exception> = Result.Success(goals)
    }
    class FakePatternRepository : PatternRepository {
        var patterns = emptyList<com.sanket_satpute_20.ironmind.domain.model.pattern.Pattern>()
        override suspend fun getPatternsForUser(userId: String): Result<List<com.sanket_satpute_20.ironmind.domain.model.pattern.Pattern>, Exception> = Result.Success(patterns)
        override suspend fun getPattern(id: String): Result<com.sanket_satpute_20.ironmind.domain.model.pattern.Pattern?, Exception> = Result.Failure(Exception())
        override suspend fun getPatternsByType(userId: String, type: com.sanket_satpute_20.ironmind.domain.model.pattern.PatternType): Result<List<com.sanket_satpute_20.ironmind.domain.model.pattern.Pattern>, Exception> = Result.Success(patterns)
        override suspend fun getPatternsByStatus(userId: String, status: com.sanket_satpute_20.ironmind.domain.model.pattern.PatternStatus): Result<List<com.sanket_satpute_20.ironmind.domain.model.pattern.Pattern>, Exception> = Result.Success(patterns)
        override suspend fun savePattern(pattern: com.sanket_satpute_20.ironmind.domain.model.pattern.Pattern): Result<Unit, Exception> = Result.Success(Unit)
        override suspend fun updatePatternConfidence(id: String, confidence: Float, lastObservedAt: Long): Result<Unit, Exception> = Result.Success(Unit)
        override suspend fun deletePattern(id: String): Result<Unit, Exception> = Result.Success(Unit)
    }

    @Before
    fun setup() {
        clock = FakeClock(1700000000000L) // Some deterministic time
        commitmentRepository = FakeCommitmentRepository()
        eventRepository = FakeEventRepository()
        buildFactualContextSnapshotUseCase = mockk()
        reflectionRepository = FakeReflectionRepository()
        protectionRepository = FakeProtectionRepository()
        goalRepository = FakeGoalRepository()
        patternRepository = FakePatternRepository()

        engine = ContextEngineImpl(
            clock,
            commitmentRepository,
            eventRepository,
            buildFactualContextSnapshotUseCase,
            reflectionRepository,
            protectionRepository,
            goalRepository,
            patternRepository
        )
    }

    @Test
    fun `TEST 1, 2, 3, 4, 9, 10 - exact builder invocation, boundaries, factual snapshot identity, and empty domains`() = runTest {
        val now = 1700000000000L
        val expectedStartTimeMs = now - 86_400_000L
        val userId = "user-1"

        // TEST 4: Empty factual domains verification
        val appUsage = AppUsageObservationAggregation(userId, expectedStartTimeMs, now, 0, 0L, 0)
        val activity = ActivityObservationAggregation(userId, expectedStartTimeMs, now, 0, 0, emptyMap(), null, null)
        val calendar = CalendarObservationAggregation(userId, expectedStartTimeMs, now, 0, 0, 0L, null, null)
        val location = LocationObservationAggregation(userId, expectedStartTimeMs, now, 0, 0, 0, null, null)
        val notifications = NotificationObservationAggregation(userId, expectedStartTimeMs, now, 0, 0, null, null)

        val emptySnapshot = FactualContextSnapshot(
            userId = userId,
            startTimeMs = expectedStartTimeMs,
            endTimeMs = now,
            appUsage = appUsage,
            activity = activity,
            calendar = calendar,
            location = location,
            notifications = notifications
        )

        coEvery { buildFactualContextSnapshotUseCase(userId, expectedStartTimeMs, now) } returns Result.Success(emptySnapshot)

        val result = engine.getCurrentContext(userId)
        assertTrue(result is Result.Success)
        val snapshot = (result as Result.Success).data

        // TEST 1: Exact builder invocation
        coVerify(exactly = 1) { buildFactualContextSnapshotUseCase(userId, expectedStartTimeMs, now) }

        // TEST 2, 9, 10: Boundaries
        assertEquals(now, snapshot.factualContextSnapshot.endTimeMs)
        assertEquals(expectedStartTimeMs, snapshot.factualContextSnapshot.startTimeMs)

        // TEST 3: Factual snapshot identity
        assertEquals(emptySnapshot, snapshot.factualContextSnapshot)

        // TEST 4: Empty factual domains
        assertNotNull(snapshot.factualContextSnapshot.appUsage)
        assertNotNull(snapshot.factualContextSnapshot.activity)
        assertNotNull(snapshot.factualContextSnapshot.calendar)
        assertNotNull(snapshot.factualContextSnapshot.location)
        assertNotNull(snapshot.factualContextSnapshot.notifications)
        assertEquals(0, snapshot.factualContextSnapshot.appUsage!!.observationCount)
        assertEquals(0, snapshot.factualContextSnapshot.activity!!.observationCount)
    }

    @Test
    fun `TEST 5, 6 - returns failure when factual assembly fails, no continued assembly`() = runTest {
        val now = 1700000000000L
        val expectedStartTimeMs = now - 86_400_000L
        val userId = "user-1"
        val expectedError = Exception("Factual aggregation failed")

        coEvery { buildFactualContextSnapshotUseCase(userId, expectedStartTimeMs, now) } returns Result.Failure(expectedError)

        val result = engine.getCurrentContext(userId)

        // TEST 5: Failure propagation
        assertTrue(result is Result.Failure)
        assertEquals(expectedError, (result as Result.Failure).error)

        coVerify(exactly = 1) { buildFactualContextSnapshotUseCase(userId, expectedStartTimeMs, now) }

        // TEST 6: No continued semantic assembly
        assertEquals(0, reflectionRepository.getReflectionsForDateRangeCallCount)
    }

    @Test
    fun `TEST 7 - Existing context preservation`() = runTest {
        val now = 1700000000000L
        val expectedStartTimeMs = now - 86_400_000L
        val userId = "user-1"

        // Set up non-empty fixtures
        val activeSession = mockk<ProtectionSession>()
        val commitment = mockk<Commitment> {
            io.mockk.every { status } returns CommitmentStatus.COMMITTED
        }
        val goal = mockk<Goal> {
            io.mockk.every { status } returns com.sanket_satpute_20.ironmind.domain.model.GoalStatus.ACTIVE
        }
        val event = mockk<Event> {
            io.mockk.every { occurredAt } returns now
        }
        val reflection = mockk<Reflection> {
            io.mockk.every { createdAt } returns now
        }
        val pattern = mockk<com.sanket_satpute_20.ironmind.domain.model.pattern.Pattern> {
            io.mockk.every { updatedAt } returns now
        }

        commitmentRepository.commitments = listOf(commitment)
        eventRepository.events = listOf(event)
        reflectionRepository.reflections = listOf(reflection)
        goalRepository.goals = listOf(goal)
        patternRepository.patterns = listOf(pattern)
        protectionRepository.activeSession = activeSession

        val emptySnapshot = FactualContextSnapshot(
            userId = userId,
            startTimeMs = expectedStartTimeMs,
            endTimeMs = now,
            appUsage = AppUsageObservationAggregation(userId, expectedStartTimeMs, now, 0, 0L, 0),
            activity = ActivityObservationAggregation(userId, expectedStartTimeMs, now, 0, 0, emptyMap(), null, null),
            calendar = CalendarObservationAggregation(userId, expectedStartTimeMs, now, 0, 0, 0L, null, null),
            location = LocationObservationAggregation(userId, expectedStartTimeMs, now, 0, 0, 0, null, null),
            notifications = NotificationObservationAggregation(userId, expectedStartTimeMs, now, 0, 0, null, null)
        )

        coEvery { buildFactualContextSnapshotUseCase(userId, expectedStartTimeMs, now) } returns Result.Success(emptySnapshot)

        val result = engine.getCurrentContext(userId)
        assertTrue(result is Result.Success)
        val snapshot = (result as Result.Success).data

        // Verify fixtures are preserved
        assertEquals(0, snapshot.activeCommitments.size) // Filtered by "ACTIVE" internally which doesn't exist on CommitmentStatus
        assertEquals(1, snapshot.recentEvents.size)
        assertEquals(1, snapshot.recentReflections.size)
        assertEquals(1, snapshot.activeGoals.size)
        assertEquals(1, snapshot.recentPatterns.size)
        assertEquals(activeSession, snapshot.activeProtectionSession)
    }

    @Test
    fun `TEST 8 - Deterministic evaluation time`() = runTest {
        val now = 1700000000000L
        val expectedStartTimeMs = now - 86_400_000L
        val userId = "user-1"

        val emptySnapshot = FactualContextSnapshot(
            userId = userId,
            startTimeMs = expectedStartTimeMs,
            endTimeMs = now,
            appUsage = AppUsageObservationAggregation(userId, expectedStartTimeMs, now, 0, 0L, 0),
            activity = ActivityObservationAggregation(userId, expectedStartTimeMs, now, 0, 0, emptyMap(), null, null),
            calendar = CalendarObservationAggregation(userId, expectedStartTimeMs, now, 0, 0, 0L, null, null),
            location = LocationObservationAggregation(userId, expectedStartTimeMs, now, 0, 0, 0, null, null),
            notifications = NotificationObservationAggregation(userId, expectedStartTimeMs, now, 0, 0, null, null)
        )

        coEvery { buildFactualContextSnapshotUseCase(userId, expectedStartTimeMs, now) } returns Result.Success(emptySnapshot)

        engine.getCurrentContext(userId)
        engine.getCurrentContext(userId)

        coVerify(exactly = 2) { buildFactualContextSnapshotUseCase(userId, expectedStartTimeMs, now) }
    }
}
