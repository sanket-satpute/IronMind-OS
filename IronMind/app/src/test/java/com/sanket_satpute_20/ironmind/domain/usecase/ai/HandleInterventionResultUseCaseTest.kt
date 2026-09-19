package com.sanket_satpute_20.ironmind.domain.usecase.ai

import com.sanket_satpute_20.ironmind.domain.ai.AIOutput
import com.sanket_satpute_20.ironmind.domain.ai.InterventionType
import com.sanket_satpute_20.ironmind.domain.common.Clock
import com.sanket_satpute_20.ironmind.domain.common.IdGenerator
import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.Event
import com.sanket_satpute_20.ironmind.domain.model.EventType
import com.sanket_satpute_20.ironmind.domain.repository.EventRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class HandleInterventionResultUseCaseTest {

    private lateinit var useCase: HandleInterventionResultUseCase
    private lateinit var eventRepository: FakeEventRepository
    private lateinit var clock: FakeClock
    private lateinit var idGenerator: FakeIdGenerator

    @Before
    fun setup() {
        eventRepository = FakeEventRepository()
        clock = FakeClock()
        idGenerator = FakeIdGenerator()
        useCase = HandleInterventionResultUseCase(eventRepository, clock, idGenerator)
    }

    @Test
    fun `invoke with ACCEPT saves INTERVENTION_ACCEPTED event`() = runTest {
        val recommendation = AIOutput.InterventionRecommendation(
            targetEntityId = "task-1",
            recommendation = "Take a break",
            reason = "You have been working for 2 hours.",
            confidence = 0.9f,
            interventionType = InterventionType.BREAK_DOWN,
            supportingContext = "Observation context"
        )
        
        val result = useCase(recommendation, HandleInterventionResultUseCase.Action.ACCEPT, "user-1")
        
        assertTrue(result is Result.Success)
        assertEquals(1, eventRepository.events.size)
        val event = eventRepository.events.first()
        
        assertEquals(EventType.INTERVENTION_ACCEPTED, event.type)
        assertEquals("user-1", event.userId)
        assertEquals("task-1", event.entityId)
        assertEquals("Intervention", event.entityType)
        assertTrue(event.metadata!!.contains("type=BREAK_DOWN"))
        assertTrue(event.metadata!!.contains("recommendation=Take a break"))
    }

    @Test
    fun `invoke with CORRECT saves INTERVENTION_OVERRIDDEN event with corrected text`() = runTest {
        val recommendation = AIOutput.InterventionRecommendation(
            targetEntityId = "task-2",
            recommendation = "Reschedule to tomorrow",
            reason = "Too late today",
            confidence = 0.8f,
            interventionType = InterventionType.RESCHEDULE,
            supportingContext = null
        )
        
        val result = useCase(
            recommendation, 
            HandleInterventionResultUseCase.Action.CORRECT, 
            "user-1", 
            "I will do it tonight instead"
        )
        
        assertTrue(result is Result.Success)
        val event = eventRepository.events.first()
        
        assertEquals(EventType.INTERVENTION_OVERRIDDEN, event.type)
        assertTrue(event.metadata!!.contains("correctedText=I will do it tonight instead"))
    }
}

class FakeEventRepository : EventRepository {
    val events = mutableListOf<Event>()

    override suspend fun saveEvent(event: Event): Result<Event, Exception> {
        events.add(event)
        return Result.Success(event)
    }

    override suspend fun getEventsForEntity(entityId: String): Result<List<Event>, Exception> {
        return Result.Success(events.filter { it.entityId == entityId })
    }

    override suspend fun getEventsForUser(userId: String): Result<List<Event>, Exception> {
        return Result.Success(events.filter { it.userId == userId })
    }

    override suspend fun getEvent(id: String): Result<Event?, Exception> {
        return Result.Success(events.find { it.id == id })
    }

    override suspend fun getEventsForDateRange(userId: String, startTime: Long, endTime: Long): Result<List<Event>, Exception> {
        return Result.Success(events.filter { it.userId == userId && it.occurredAt in startTime..endTime })
    }

    override suspend fun searchEvents(userId: String, query: String): Result<List<Event>, Exception> {
        return Result.Success(events.filter { it.userId == userId && it.metadata?.contains(query) == true })
    }
}

class FakeClock : Clock {
    var currentTime = 1000L
    override fun currentTimeMillis(): Long = currentTime
}

class FakeIdGenerator : IdGenerator {
    var nextId = "id-1"
    override fun generateId(): String = nextId
}
