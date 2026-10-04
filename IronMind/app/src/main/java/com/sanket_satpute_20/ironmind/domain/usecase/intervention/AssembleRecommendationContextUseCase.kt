package com.sanket_satpute_20.ironmind.domain.usecase.intervention

import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.CommitmentStatus
import com.sanket_satpute_20.ironmind.domain.model.barrier.BarrierConfirmationState
import com.sanket_satpute_20.ironmind.domain.model.intervention.*
import com.sanket_satpute_20.ironmind.domain.model.pattern.PatternStatus
import com.sanket_satpute_20.ironmind.domain.repository.*

class AssembleRecommendationContextUseCase(
    private val goalRepository: GoalRepository,
    private val commitmentRepository: CommitmentRepository,
    private val observationRepository: ObservationRepository,
    private val patternRepository: PatternRepository,
    private val barrierRepository: BarrierRepository,
    private val reflectionRepository: ReflectionRepository,
    private val eventRepository: EventRepository,
    private val timeProvider: () -> Long
) {
    companion object {
        const val SEVEN_DAYS_MS = 7L * 24L * 60L * 60L * 1000L
        const val MAX_GOALS = 10
        const val MAX_COMMITMENTS = 20
        const val MAX_OBSERVATIONS = 50
        const val MAX_PATTERNS = 10
        const val MAX_BARRIERS = 10
        const val MAX_REFLECTIONS = 10
        const val MAX_CORRECTIONS = 10
        
        val ACTIVE_COMMITMENT_STATUSES = listOf(
            CommitmentStatus.PLANNED,
            CommitmentStatus.COMMITTED,
            CommitmentStatus.STARTED,
            CommitmentStatus.RECOVERED
        )
        
        val EXCLUDED_BARRIER_STATES = listOf(
            BarrierConfirmationState.REJECTED,
            BarrierConfirmationState.DISMISSED
        )
    }

    suspend operator fun invoke(userId: String): Result<RecommendationContext, Exception> {
        val referenceTimeMs = timeProvider()
        val windowStartMs = referenceTimeMs - SEVEN_DAYS_MS
        
        // 1. Bounded Goals
        val goalsResult = goalRepository.getActiveGoalsForUser(userId, MAX_GOALS)
        if (goalsResult is Result.Failure) return Result.Failure(goalsResult.error)
        val contextGoals = (goalsResult as Result.Success).data.map {
            ContextGoal(it.id, it.title, it.description, it.status, it.targetAt)
        }

        // 2. Bounded Commitments
        val commitmentsResult = commitmentRepository.getActiveCommitmentsForUser(
            userId = userId, 
            statuses = ACTIVE_COMMITMENT_STATUSES, 
            limit = MAX_COMMITMENTS
        )
        if (commitmentsResult is Result.Failure) return Result.Failure(commitmentsResult.error)
        val contextCommitments = (commitmentsResult as Result.Success).data.map {
            ContextCommitment(it.id, it.title, it.description, it.status, it.scheduledStartAt, it.scheduledEndAt)
        }
        
        // 3. Bounded Observations
        val observationsResult = observationRepository.getObservationsForTimeWindow(
            userId = userId,
            startTimeMs = windowStartMs,
            endTimeMs = referenceTimeMs,
            types = null,
            limit = MAX_OBSERVATIONS,
            orderAsc = false // newest first
        )
        if (observationsResult is Result.Failure) return Result.Failure(observationsResult.error)
        val contextObservations = (observationsResult as Result.Success).data.map {
            ContextObservation(it.id, it.type, it.value, it.context, it.occurredAt)
        }
        
        // 4. Bounded Patterns
        val patternsResult = patternRepository.getPatternsByStatus(userId, PatternStatus.ACTIVE, MAX_PATTERNS)
        if (patternsResult is Result.Failure) return Result.Failure(patternsResult.error)
        val contextPatterns = (patternsResult as Result.Success).data.map {
            ContextPattern(it.id, it.type, it.description)
        }
        
        // 5. Bounded Barriers
        val barriersResult = barrierRepository.getActiveBarriersForUser(
            userId = userId,
            excludedStates = EXCLUDED_BARRIER_STATES,
            limit = MAX_BARRIERS
        )
        if (barriersResult is Result.Failure) return Result.Failure(barriersResult.error)
        val contextBarriers = (barriersResult as Result.Success).data.map {
            ContextBarrier(it.id, it.category, it.description, it.confirmationState)
        }
            
        // 6. Bounded Reflections
        val reflectionsResult = reflectionRepository.getReflectionsForTimeWindow(
            userId = userId,
            startTime = windowStartMs,
            endTime = referenceTimeMs,
            limit = MAX_REFLECTIONS,
            orderAsc = false // newest first
        )
        if (reflectionsResult is Result.Failure) return Result.Failure(reflectionsResult.error)
        val contextReflections = (reflectionsResult as Result.Success).data.map {
            ContextReflection(it.id, it.content, it.createdAt)
        }
        
        // 7. Bounded Corrections
        val correctionsResult = eventRepository.getEventsForTimeWindow(
            userId = userId,
            startTime = windowStartMs,
            endTime = referenceTimeMs,
            types = listOf(com.sanket_satpute_20.ironmind.domain.model.EventType.INTERVENTION_OVERRIDDEN),
            limit = MAX_CORRECTIONS,
            orderAsc = false // newest first
        )
        if (correctionsResult is Result.Failure) return Result.Failure(correctionsResult.error)
        
        val contextCorrections = (correctionsResult as Result.Success).data.mapNotNull { event ->
            val metadataMap = event.metadata?.split(",")?.associate { 
                val parts = it.split("=", limit = 2)
                if (parts.size == 2) parts[0] to parts[1] else parts[0] to ""
            } ?: emptyMap()
            
            val type = metadataMap["type"]
            val recommendation = metadataMap["recommendation"]
            val correctedText = metadataMap["correctedText"]
            
            if (type == null || recommendation == null) {
                null
            } else {
                ContextCorrection(
                    eventId = event.id,
                    targetEntityId = event.entityId,
                    occurredAt = event.occurredAt,
                    interventionType = type,
                    recommendation = recommendation,
                    correctedText = correctedText?.takeIf { it.isNotEmpty() }
                )
            }
        }
        
        val context = RecommendationContext(
            userId = userId,
            activeGoals = contextGoals,
            activeCommitments = contextCommitments,
            recentObservations = contextObservations,
            activePatterns = contextPatterns,
            activeBarriers = contextBarriers,
            recentReflections = contextReflections,
            recentCorrections = contextCorrections
        )
        
        return Result.Success(context)
    }
}
