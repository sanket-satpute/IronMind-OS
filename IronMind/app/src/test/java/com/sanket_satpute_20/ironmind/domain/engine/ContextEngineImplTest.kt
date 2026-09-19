package com.sanket_satpute_20.ironmind.domain.engine

import com.sanket_satpute_20.ironmind.domain.common.Clock
import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.context.ContextSnapshot
import com.sanket_satpute_20.ironmind.domain.model.Commitment
import com.sanket_satpute_20.ironmind.domain.model.CommitmentStatus
import com.sanket_satpute_20.ironmind.domain.model.Event
import com.sanket_satpute_20.ironmind.domain.model.Goal
import com.sanket_satpute_20.ironmind.domain.model.observation.Observation
import com.sanket_satpute_20.ironmind.domain.model.ProtectionSession
import com.sanket_satpute_20.ironmind.domain.model.Reflection
import com.sanket_satpute_20.ironmind.domain.repository.*
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
    private lateinit var observationRepository: FakeObservationRepository
    private lateinit var reflectionRepository: FakeReflectionRepository
    private lateinit var protectionRepository: FakeProtectionRepository
    private lateinit var goalRepository: FakeGoalRepository
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
    class FakeObservationRepository : ObservationRepository {
        var observations = emptyList<Observation>()
        override suspend fun getObservations(userId: String, limit: Int, offset: Int): Result<List<Observation>, Exception> = Result.Success(observations)
        override suspend fun insertObservation(observation: Observation): Result<Unit, Exception> = Result.Success(Unit)
        override suspend fun getObservationsByType(userId: String, type: com.sanket_satpute_20.ironmind.domain.model.observation.ObservationType, limit: Int, offset: Int): Result<List<Observation>, Exception> = Result.Success(observations)
        override suspend fun getObservationById(id: String): Result<Observation, Exception> = Result.Failure(Exception())
        override suspend fun deleteObservation(id: String): Result<Unit, Exception> = Result.Success(Unit)
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

    @Before
    fun setup() {
        clock = FakeClock(1700000000000L) // Some deterministic time
        commitmentRepository = FakeCommitmentRepository()
        eventRepository = FakeEventRepository()
        observationRepository = FakeObservationRepository()
        reflectionRepository = FakeReflectionRepository()
        protectionRepository = FakeProtectionRepository()
        goalRepository = FakeGoalRepository()

        engine = ContextEngineImpl(
            clock,
            commitmentRepository,
            eventRepository,
            observationRepository,
            reflectionRepository,
            protectionRepository,
            goalRepository
        )
    }

    @Test
    fun `getCurrentContext returns successful snapshot`() = runTest {
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
        assertTrue(snapshot.recentObservations.isEmpty())
        assertTrue(snapshot.recentReflections.isEmpty())
        assertTrue(snapshot.activeGoals.isEmpty())
        assertEquals(null, snapshot.activeProtectionSession)
    }
}
