package com.sanket_satpute_20.ironmind.domain.usecase.ai

import com.sanket_satpute_20.ironmind.domain.ai.AIOutput
import com.sanket_satpute_20.ironmind.domain.common.Clock
import com.sanket_satpute_20.ironmind.domain.common.IdGenerator
import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.EntitySource
import com.sanket_satpute_20.ironmind.domain.model.Event
import com.sanket_satpute_20.ironmind.domain.model.EventType
import com.sanket_satpute_20.ironmind.domain.repository.EventRepository

/**
 * Sprint V2.11: Suggestion-Only Intelligence UI.
 *
 * Handles the user's explicit response to an intervention recommendation (Accept, Reject, Correct, Ignore).
 * Records the outcome into the Event system, satisfying the requirement:
 * "The user can: accept, reject, correct, ignore and the result is recorded."
 */
class HandleInterventionResultUseCase(
    private val eventRepository: EventRepository,
    private val clock: Clock,
    private val idGenerator: IdGenerator
) {
    enum class Action {
        ACCEPT,
        REJECT,
        CORRECT,
        IGNORE
    }

    suspend operator fun invoke(
        recommendation: AIOutput.InterventionRecommendation,
        action: Action,
        userId: String,
        correctedText: String? = null
    ): Result<Unit, Exception> {
        val eventType = when (action) {
            Action.ACCEPT -> EventType.INTERVENTION_ACCEPTED
            Action.REJECT -> EventType.INTERVENTION_DISMISSED
            Action.CORRECT -> EventType.INTERVENTION_OVERRIDDEN
            Action.IGNORE -> EventType.INTERVENTION_IGNORED
        }

        val metadata = buildString {
            append("type=${recommendation.interventionType}")
            append(",recommendation=${recommendation.recommendation}")
            if (correctedText != null) {
                append(",correctedText=$correctedText")
            }
        }

        val event = Event(
            id = idGenerator.generateId(),
            userId = userId,
            type = eventType,
            entityType = "Intervention",
            entityId = recommendation.targetEntityId,
            occurredAt = clock.currentTimeMillis(),
            recordedAt = clock.currentTimeMillis(),
            source = EntitySource.SYSTEM,
            metadata = metadata
        )

        val result = eventRepository.saveEvent(event)
        
        return when (result) {
            is Result.Success -> Result.Success(Unit)
            is Result.Failure -> Result.Failure(result.error)
        }
    }
}
