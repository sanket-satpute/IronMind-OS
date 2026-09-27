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
        override suspend fun getReflectionsForDateRange(userId: String, startTime: Long, endTime: Long): Result<List<Reflection>, Exception> = Result.Success(reflections)
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
    fun `getCurrentContext returns successful snapshot`() = runTest {
        val now = 1700000000000L
        val startTimeMs = now - 86_400_000L
        val emptySnapshot = FactualContextSnapshot(
            userId = "user-1",
            startTimeMs = startTimeMs,
            endTimeMs = now,
            appUsage = AppUsageObservationAggregation("user-1", startTimeMs, now, 0, 0L, 0),
            activity = ActivityObservationAggregation("user-1", startTimeMs, now, 0, 0, emptyMap(), null, null),
            calendar = CalendarObservationAggregation("user-1", startTimeMs, now, 0, 0, 0L, null, null),
            location = LocationObservationAggregation("user-1", startTimeMs, now, 0, 0, 0, null, null),
            notifications = NotificationObservationAggregation("user-1", startTimeMs, now, 0, 0, null, null)
        )
        coEvery { buildFactualContextSnapshotUseCase("user-1", startTimeMs, now) } returns Result.Success(emptySnapshot)

        val result = engine.getCurrentContext("user-1")
        assertTrue(result is Result.Success)
        val snapshot = (result as Result.Success).data
        
        assertNotNull(snapshot)
        assertEquals(1700000000000L, snapshot.timestamp)
        // Day of week check
        val cal = Calendar.getInstance().apply { timeInMillis = 1700000000000L }
        assertEquals(cal.get(Calendar.DAY_OF_WEEK), snapshot.dayOfWeek)
        
        assertTrue(snapshot.activeCommitments.isEmpty())
        assertTrue(snapshot.recentEvents.isEmpty())
        assertNotNull(snapshot.factualContextSnapshot)
        assertEquals(startTimeMs, snapshot.factualContextSnapshot.startTimeMs)
        assertEquals(now, snapshot.factualContextSnapshot.endTimeMs)
        assertTrue(snapshot.recentReflections.isEmpty())
        assertTrue(snapshot.activeGoals.isEmpty())
        assertEquals(null, snapshot.activeProtectionSession)
    }

    @Test
    fun `getCurrentContext returns failure when factual assembly fails`() = runTest {
        val now = 1700000000000L
        val startTimeMs = now - 86_400_000L
        val expectedError = Exception("Factual aggregation failed")

        coEvery { buildFactualContextSnapshotUseCase("user-1", startTimeMs, now) } returns Result.Failure(expectedError)

        val result = engine.getCurrentContext("user-1")
        assertTrue(result is Result.Failure)
        assertEquals(expectedError, (result as Result.Failure).error)
    }
}
