package com.sanket_satpute_20.ironmind.domain.usecase.ai

import com.sanket_satpute_20.ironmind.domain.ai.InterventionType
import com.sanket_satpute_20.ironmind.domain.common.Clock
import com.sanket_satpute_20.ironmind.domain.common.IdGenerator
import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.Event
import com.sanket_satpute_20.ironmind.domain.model.EventType
import com.sanket_satpute_20.ironmind.domain.model.intervention.InterventionRecommendation
import com.sanket_satpute_20.ironmind.domain.model.intervention.InterventionRecommendationStatus
import com.sanket_satpute_20.ironmind.domain.repository.EventRepository
import com.sanket_satpute_20.ironmind.domain.repository.InterventionRecommendationRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class HandleInterventionResultUseCaseTest {

    private lateinit var useCase: HandleInterventionResultUseCase
    private lateinit var eventRepository: FakeEventRepository
    private lateinit var recommendationRepository: HandleFakeInterventionRecommendationRepository
    private lateinit var updateStatusUseCase: UpdateInterventionRecommendationStatusUseCase
    private lateinit var clock: FakeClock
    private lateinit var idGenerator: FakeIdGenerator

    @Before
    fun setup() {
        eventRepository = FakeEventRepository()
        recommendationRepository = HandleFakeInterventionRecommendationRepository()
        clock = FakeClock()
        idGenerator = FakeIdGenerator()
        updateStatusUseCase = UpdateInterventionRecommendationStatusUseCase(recommendationRepository)
        useCase = HandleInterventionResultUseCase(eventRepository, updateStatusUseCase, clock, idGenerator)
    }

    @Test
    fun `invoke with ACCEPT saves INTERVENTION_ACCEPTED event and updates status`() = runTest {
        val recommendation = InterventionRecommendation(
            id = "rec-1",
            userId = "user-1",
            interventionType = InterventionType.BREAK_DOWN,
            objective = com.sanket_satpute_20.ironmind.domain.ai.InterventionObjective.INITIATE_ACTION,
            targetEntityId = "task-1",
            targetEntityType = "Task",
            suggestedAction = "Take a break",
            rationale = "You have been working for 2 hours.",
            status = InterventionRecommendationStatus.PENDING,
            createdAt = 1000L,
            expiresAt = 2000L
        )
        recommendationRepository.recommendations["rec-1"] = recommendation

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

        val updatedRec = recommendationRepository.recommendations["rec-1"]
        assertEquals(InterventionRecommendationStatus.ACCEPTED, updatedRec?.status)
    }

    @Test
    fun `invoke with CORRECT saves INTERVENTION_OVERRIDDEN event with corrected text and does NOT change status`() = runTest {
        val recommendation = InterventionRecommendation(
            id = "rec-2",
            userId = "user-1",
            interventionType = InterventionType.RESCHEDULE,
            objective = com.sanket_satpute_20.ironmind.domain.ai.InterventionObjective.INITIATE_ACTION,
            targetEntityId = "task-2",
            targetEntityType = "Task",
            suggestedAction = "Reschedule to tomorrow",
            rationale = "Too late today",
            status = InterventionRecommendationStatus.PENDING,
            createdAt = 1000L,
            expiresAt = 2000L
        )
        recommendationRepository.recommendations["rec-2"] = recommendation

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

        val updatedRec = recommendationRepository.recommendations["rec-2"]
        assertEquals(InterventionRecommendationStatus.PENDING, updatedRec?.status)
    }
}

class HandleFakeInterventionRecommendationRepository : InterventionRecommendationRepository {
    val recommendations = mutableMapOf<String, InterventionRecommendation>()
    
    override suspend fun saveRecommendation(recommendation: InterventionRecommendation): Result<Unit, Exception> {
        recommendations[recommendation.id] = recommendation
        return Result.Success(Unit)
    }

    override suspend fun getRecommendation(id: String): Result<InterventionRecommendation?, Exception> {
        return Result.Success(recommendations[id])
    }

    override fun getPendingRecommendations(userId: String, currentTime: Long): Flow<List<InterventionRecommendation>> {
        return MutableStateFlow(recommendations.values.filter { it.userId == userId && it.status == InterventionRecommendationStatus.PENDING })
    }

    override suspend fun updateRecommendationStatus(id: String, status: InterventionRecommendationStatus): Result<Unit, Exception> {
        val rec = recommendations[id] ?: return Result.Failure(Exception("Not found"))
        recommendations[id] = rec.copy(status = status)
        return Result.Success(Unit)
    }

    override suspend fun getEquivalentRecommendations(
        userId: String,
        objective: String,
        targetEntityType: String?,
        targetEntityId: String?
    ): Result<List<InterventionRecommendation>, Exception> {
        return Result.Success(recommendations.values.filter { 
            it.userId == userId && 
            it.objective.name == objective && 
            it.targetEntityType == targetEntityType && 
            it.targetEntityId == targetEntityId 
        }.sortedByDescending { it.createdAt })
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

    override suspend fun getEventForUser(userId: String, id: String): Result<Event?, Exception> {
        return Result.Success(events.find { it.userId == userId && it.id == id })
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

    override suspend fun getEventsForTimeWindow(userId: String, startTime: Long, endTime: Long, types: List<com.sanket_satpute_20.ironmind.domain.model.EventType>?, limit: Int, orderAsc: Boolean): com.sanket_satpute_20.ironmind.domain.common.Result<List<com.sanket_satpute_20.ironmind.domain.model.Event>, Exception> = com.sanket_satpute_20.ironmind.domain.common.Result.Success(emptyList())
}

class FakeClock : Clock {
    var currentTime = 1000L
    override fun currentTimeMillis(): Long = currentTime
}

class FakeIdGenerator : IdGenerator {
    var nextId = "id-1"
    override fun generateId(): String = nextId
}
