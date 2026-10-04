package com.sanket_satpute_20.ironmind.domain.usecase.intervention

import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.*
import com.sanket_satpute_20.ironmind.domain.model.barrier.*
import com.sanket_satpute_20.ironmind.domain.model.observation.*
import com.sanket_satpute_20.ironmind.domain.model.pattern.*
import com.sanket_satpute_20.ironmind.domain.ai.BarrierCategory
import com.sanket_satpute_20.ironmind.domain.repository.*
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class AssembleRecommendationContextUseCaseTest {

    private lateinit var goalRepo: GoalRepository
    private lateinit var commitmentRepo: CommitmentRepository
    private lateinit var observationRepo: ObservationRepository
    private lateinit var patternRepo: PatternRepository
    private lateinit var barrierRepo: BarrierRepository
    private lateinit var reflectionRepo: ReflectionRepository
    private lateinit var eventRepo: EventRepository
    private lateinit var useCase: AssembleRecommendationContextUseCase
    
    private val timeProvider: () -> Long = { 10000000L }

    @Before
    fun setup() {
        goalRepo = mockk()
        commitmentRepo = mockk()
        observationRepo = mockk()
        patternRepo = mockk()
        barrierRepo = mockk()
        reflectionRepo = mockk()
        eventRepo = mockk()

        useCase = AssembleRecommendationContextUseCase(
            goalRepository = goalRepo,
            commitmentRepository = commitmentRepo,
            observationRepository = observationRepo,
            patternRepository = patternRepo,
            barrierRepository = barrierRepo,
            reflectionRepository = reflectionRepo,
            eventRepository = eventRepo,
            timeProvider = timeProvider
        )
    }

    @Test
    fun `assemble context uses bounded queries and returns minimal projections`() = runBlocking {
        val userId = "user1"
        
        // Mocks
        val activeGoal = Goal("g1", userId, null, "Goal 1", "Desc", "Why", 1, GoalStatus.ACTIVE, null, null, null, 0L, 0L)
        coEvery { goalRepo.getActiveGoalsForUser(userId, AssembleRecommendationContextUseCase.MAX_GOALS) } returns Result.Success(listOf(activeGoal))
        
        val activeCommitment = Commitment("c1", userId, null, null, null, null, "Comm 1", "Desc", 0L, null, null, CommitmentStatus.COMMITTED, 1, EntitySource.USER, 0L, 0L)
        coEvery { commitmentRepo.getActiveCommitmentsForUser(userId, AssembleRecommendationContextUseCase.ACTIVE_COMMITMENT_STATUSES, AssembleRecommendationContextUseCase.MAX_COMMITMENTS) } returns Result.Success(listOf(activeCommitment))
        
        val observation = Observation("o1", userId, ObservationType.UNKNOWN, ObservationSource.SYSTEM, 0L, 0L, null, "value", "context", null, ObservationProvenance(ObservationSource.SYSTEM, null, 0L))
        coEvery { observationRepo.getObservationsForTimeWindow(userId, any(), any(), null, AssembleRecommendationContextUseCase.MAX_OBSERVATIONS, false) } returns Result.Success(listOf(observation))
            
        val pattern = Pattern("p1", userId, "fprint", PatternType.TIME_PATTERN, "Desc", null, null, 1.0f, 1, null, 0L, 0L, PatternStatus.ACTIVE, MemoryConfirmationState.SYSTEM_CONFIRMED, 0L, 0L)
        coEvery { patternRepo.getPatternsByStatus(userId, PatternStatus.ACTIVE, AssembleRecommendationContextUseCase.MAX_PATTERNS) } returns Result.Success(listOf(pattern))

        val confirmedBarrier = BarrierHypothesis("b1", userId, BarrierCategory.UNCERTAINTY, "Desc", BarrierConfirmationState.CONFIRMED, BarrierStatus.ACTIVE, "r1", 0L, 0L)
        val unconfirmedBarrier = BarrierHypothesis("b2", userId, BarrierCategory.UNCERTAINTY, "Desc", BarrierConfirmationState.UNCONFIRMED, BarrierStatus.ACTIVE, "r2", 0L, 0L)
        
        coEvery { barrierRepo.getActiveBarriersForUser(userId, AssembleRecommendationContextUseCase.EXCLUDED_BARRIER_STATES, AssembleRecommendationContextUseCase.MAX_BARRIERS) } returns Result.Success(listOf(confirmedBarrier, unconfirmedBarrier))
        
        val reflection = Reflection("r1", userId, null, null, "Content", null, 0L)
        coEvery { reflectionRepo.getReflectionsForTimeWindow(userId, any(), any(), AssembleRecommendationContextUseCase.MAX_REFLECTIONS, false) } returns Result.Success(listOf(reflection))

        val event = Event(id = "e1", userId = userId, type = EventType.INTERVENTION_OVERRIDDEN, entityType = "Intervention", entityId = "t1", occurredAt = 0L, recordedAt = 0L, source = EntitySource.SYSTEM, metadata = "type=BREAK_DOWN,recommendation=Do jumping jacks,correctedText=I have a broken leg")
        val malformedEvent = Event(id = "e2", userId = userId, type = EventType.INTERVENTION_OVERRIDDEN, entityType = "Intervention", entityId = "t2", occurredAt = 0L, recordedAt = 0L, source = EntitySource.SYSTEM, metadata = "invalidMetadataString")
        coEvery { eventRepo.getEventsForTimeWindow(userId, any(), any(), listOf(EventType.INTERVENTION_OVERRIDDEN), AssembleRecommendationContextUseCase.MAX_CORRECTIONS, false) } returns Result.Success(listOf(event, malformedEvent))

        // Act
        val result = useCase(userId)
        
        // Assert
        assertTrue(result is Result.Success)
        val context = (result as Result.Success).data
        
        assertEquals(userId, context.userId)
        
        assertEquals(1, context.activeGoals.size)
        assertEquals("g1", context.activeGoals.first().id)
        
        assertEquals(1, context.activeCommitments.size)
        assertEquals("c1", context.activeCommitments.first().id)
        
        assertEquals(1, context.recentObservations.size)
        assertEquals("value", context.recentObservations.first().value)
        
        assertEquals(1, context.activePatterns.size)
        assertEquals("p1", context.activePatterns.first().id)
        
        // Only confirmed and unconfirmed should remain
        assertEquals(2, context.activeBarriers.size)
        assertTrue(context.activeBarriers.any { it.id == "b1" })
        assertTrue(context.activeBarriers.any { it.id == "b2" })
        
        assertEquals(1, context.recentReflections.size)
        assertEquals("r1", context.recentReflections.first().id)
        
        // Corrections
        assertEquals(1, context.recentCorrections.size)
        val correction = context.recentCorrections.first()
        assertEquals("e1", correction.eventId)
        assertEquals("t1", correction.targetEntityId)
        assertEquals("BREAK_DOWN", correction.interventionType)
        assertEquals("Do jumping jacks", correction.recommendation)
        assertEquals("I have a broken leg", correction.correctedText)
    }

    @Test
    fun `assemble context fails if event query fails`() = runBlocking {
        val userId = "user1"
        
        coEvery { goalRepo.getActiveGoalsForUser(any(), any()) } returns Result.Success(emptyList())
        coEvery { commitmentRepo.getActiveCommitmentsForUser(any(), any(), any()) } returns Result.Success(emptyList())
        coEvery { observationRepo.getObservationsForTimeWindow(any(), any(), any(), any(), any(), any()) } returns Result.Success(emptyList())
        coEvery { patternRepo.getPatternsByStatus(any(), any(), any()) } returns Result.Success(emptyList())
        coEvery { barrierRepo.getActiveBarriersForUser(any(), any(), any()) } returns Result.Success(emptyList())
        coEvery { reflectionRepo.getReflectionsForTimeWindow(any(), any(), any(), any(), any()) } returns Result.Success(emptyList())
        
        coEvery { eventRepo.getEventsForTimeWindow(any(), any(), any(), any(), any(), any()) } returns Result.Failure(Exception("DB error"))
        
        val result = useCase(userId)
        assertTrue(result is Result.Failure)
        assertEquals("DB error", (result as Result.Failure).error.message)
    }
    
    @Test
    fun `unexpected repository failure propagates without swallowing`() = runBlocking {
        val userId = "user1"
        
        coEvery { goalRepo.getActiveGoalsForUser(userId, AssembleRecommendationContextUseCase.MAX_GOALS) } returns Result.Success(emptyList())
        val exception = Exception("DB error")
        coEvery { commitmentRepo.getActiveCommitmentsForUser(userId, any(), AssembleRecommendationContextUseCase.MAX_COMMITMENTS) } returns Result.Failure(exception)
        
        val result = useCase(userId)
        
        assertTrue(result is Result.Failure)
        assertEquals(exception, (result as Result.Failure).error)
    }
}
