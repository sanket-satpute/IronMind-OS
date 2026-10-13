package com.sanket_satpute_20.ironmind.domain.usecase.ai

import com.sanket_satpute_20.ironmind.domain.common.Clock
import com.sanket_satpute_20.ironmind.domain.common.IdGenerator
import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.EntitySource
import com.sanket_satpute_20.ironmind.domain.model.Event
import com.sanket_satpute_20.ironmind.domain.model.EventType
import com.sanket_satpute_20.ironmind.domain.model.intervention.CorrectionMetadataParser
import com.sanket_satpute_20.ironmind.domain.model.intervention.CorrectionScope
import com.sanket_satpute_20.ironmind.domain.model.intervention.InterventionRecommendation
import com.sanket_satpute_20.ironmind.domain.model.intervention.InterventionRecommendationStatus
import com.sanket_satpute_20.ironmind.domain.repository.EventRepository
import com.sanket_satpute_20.ironmind.execution.background.BackgroundExecutor

/**
 * Sprint V2.11: Suggestion-Only Intelligence UI.
 * Sprint V2.16-C1: Structured Correction Data Contract.
 * Sprint V2.16-C3.4: Safe Event Infrastructure and Erasure Hardening.
 *
 * Handles the user's explicit response to an intervention recommendation (Accept, Reject, Correct, Ignore).
 * Records the outcome into the Event system and learner status atomically.
 */
class HandleInterventionResultUseCase(
    private val eventRepository: EventRepository,
    private val updateInterventionRecommendationStatusUseCase: UpdateInterventionRecommendationStatusUseCase,
    private val clock: Clock,
    private val idGenerator: IdGenerator,
    private val backgroundExecutor: BackgroundExecutor? = null
) {
    enum class Action {
        ACCEPT,
        REJECT,
        CORRECT,
        IGNORE
    }

    suspend operator fun invoke(
        recommendation: InterventionRecommendation,
        action: Action,
        userId: String,
        correctedText: String? = null,
        scope: CorrectionScope = CorrectionScope.INTERVENTION_CORRECTION
    ): Result<Unit, Exception> {
        val eventType = when (action) {
            Action.ACCEPT -> EventType.INTERVENTION_ACCEPTED
            Action.REJECT -> EventType.INTERVENTION_DISMISSED
            Action.CORRECT -> EventType.INTERVENTION_OVERRIDDEN
            Action.IGNORE -> EventType.INTERVENTION_IGNORED
        }

        val mappedStatus = when (action) {
            Action.ACCEPT -> InterventionRecommendationStatus.ACCEPTED
            Action.REJECT -> InterventionRecommendationStatus.REJECTED
            Action.IGNORE -> InterventionRecommendationStatus.IGNORED
            Action.CORRECT -> null // Do not map CORRECT. Leave status unchanged (undefined handling per contract).
        }

        val metadata = if (action == Action.CORRECT) {
            CorrectionMetadataParser.serialize(
                scope = scope,
                type = recommendation.interventionType.name,
                recommendation = recommendation.suggestedAction,
                correctedText = correctedText
            )
        } else {
            buildString {
                append("type=${recommendation.interventionType}")
                append(",recommendation=${recommendation.suggestedAction}")
                if (correctedText != null) {
                    append(",correctedText=$correctedText")
                }
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
            metadata = metadata,
            causationId = recommendation.id
        )

        // Sprint V2.16-C3.4: Atomic Event + Status insertion with user-profile validation via EventRepository
        val persistResult = eventRepository.saveEventWithLearnerStatus(event)
        if (persistResult is Result.Failure) {
            return persistResult
        }

        if (backgroundExecutor != null) {
            try {
                backgroundExecutor.scheduleEventProcessing()
            } catch (e: Exception) {
                // Primary intervention flow must not depend on background event processing
                println("IronMindLifecycle [EventStatus] [SCHEDULE_FAILED] eventId=${event.id}")
            }
        }

        if (mappedStatus != null) {
            val statusResult = updateInterventionRecommendationStatusUseCase(recommendation.id, mappedStatus)
            if (statusResult is Result.Failure) {
                return statusResult
            }
        }
        
        return Result.Success(Unit)
    }
}
