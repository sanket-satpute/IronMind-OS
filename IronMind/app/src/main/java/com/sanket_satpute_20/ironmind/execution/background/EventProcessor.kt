package com.sanket_satpute_20.ironmind.execution.background

import com.sanket_satpute_20.ironmind.domain.logging.IronLogger
import com.sanket_satpute_20.ironmind.domain.model.Event
import com.sanket_satpute_20.ironmind.domain.model.EventType
import com.sanket_satpute_20.ironmind.domain.model.intervention.CorrectionIneligibilityReason
import com.sanket_satpute_20.ironmind.domain.model.intervention.CorrectionReviewDecision
import com.sanket_satpute_20.ironmind.domain.model.intervention.InterventionResolutionReason
import com.sanket_satpute_20.ironmind.domain.model.intervention.InterventionState
import com.sanket_satpute_20.ironmind.domain.repository.CorrectionReviewRepository
import com.sanket_satpute_20.ironmind.domain.repository.InterventionRepository
import com.sanket_satpute_20.ironmind.domain.usecase.intervention.EvaluateCorrectionReviewDecisionUseCase

sealed class EventProcessingOutcome {
    object Success : EventProcessingOutcome()
    data class SuccessNoChange(val reason: String) : EventProcessingOutcome()
    data class RetryableFailure(val message: String) : EventProcessingOutcome()
    data class PermanentFailure(val message: String) : EventProcessingOutcome()
    data class UnsupportedEventType(val eventType: EventType) : EventProcessingOutcome()
}

interface EventProcessor {
    suspend fun processEvent(event: Event): EventProcessingOutcome
}

/**
 * Event processor for Sprint V2.16-C3.4 / Phase 7 / Phase 8.3.1.
 * Implements deterministic correction-event evaluation via [EvaluateCorrectionReviewDecisionUseCase]
 * and links intervention outcome events to [InterventionRepository] for closed-loop learning.
 */
class DefaultEventProcessor(
    private val evaluateCorrectionReviewDecisionUseCase: EvaluateCorrectionReviewDecisionUseCase? = null,
    private val correctionReviewRepository: CorrectionReviewRepository? = null,
    private val interventionRepository: InterventionRepository? = null,
    private val logger: IronLogger? = null
) : EventProcessor {
    override suspend fun processEvent(event: Event): EventProcessingOutcome {
        return when (event.type) {
            EventType.INTERVENTION_OVERRIDDEN -> {
                resolveInterventionIfPresent(
                    userId = event.userId,
                    targetId = event.causationId ?: event.entityId,
                    reason = InterventionResolutionReason.OVERRIDDEN,
                    occurredAt = event.occurredAt
                )

                if (evaluateCorrectionReviewDecisionUseCase != null) {
                    val decision = evaluateCorrectionReviewDecisionUseCase.evaluate(
                        userId = event.userId,
                        eventId = event.id,
                        recommendationId = event.causationId
                    )
                    when (decision) {
                        is CorrectionReviewDecision.DeferredForConsideration -> {
                            val reviewItem = com.sanket_satpute_20.ironmind.domain.model.intervention.CorrectionReviewItem(
                                id = "cr_${event.userId}_${event.id}",
                                userId = event.userId,
                                sourceEventId = event.id,
                                recommendationId = decision.recommendationId,
                                causalHypothesis = decision.causalHypothesis,
                                correctionScope = decision.correctionScope,
                                correctedText = decision.correctedText,
                                decisionState = "DEFERRED_FOR_CONSIDERATION",
                                reviewStatus = com.sanket_satpute_20.ironmind.domain.model.intervention.CorrectionReviewStatus.PENDING,
                                createdAt = event.occurredAt,
                                updatedAt = event.occurredAt
                            )
                            val persistResult = correctionReviewRepository?.persistCandidate(reviewItem)
                            if (persistResult is com.sanket_satpute_20.ironmind.domain.common.Result.Failure) {
                                logger?.logLifecycle("EventProcessor", "CANDIDATE_PERSISTENCE_FAILED", mapOf("eventId" to event.id, "error" to (persistResult.error.message ?: "UNKNOWN")))
                                EventProcessingOutcome.RetryableFailure("FAILED_TO_PERSIST_CANDIDATE: ${persistResult.error.message}")
                            } else {
                                val verifyResult = correctionReviewRepository?.verifyCandidatePersisted(event.userId, event.id)
                                if (verifyResult is com.sanket_satpute_20.ironmind.domain.common.Result.Failure) {
                                    logger?.logLifecycle("EventProcessor", "CANDIDATE_VERIFICATION_FAILED", mapOf("eventId" to event.id, "error" to (verifyResult.error.message ?: "UNKNOWN")))
                                    EventProcessingOutcome.RetryableFailure("FAILED_TO_VERIFY_CANDIDATE: ${verifyResult.error.message}")
                                } else {
                                    logger?.logLifecycle(
                                        "EventProcessor",
                                        "CORRECTION_DEFERRED",
                                        mapOf("eventId" to event.id, "userId" to event.userId)
                                    )
                                    EventProcessingOutcome.Success
                                }
                            }
                        }
                        is CorrectionReviewDecision.NotReviewable -> {
                            when (decision.ineligibilityReason) {
                                CorrectionIneligibilityReason.DATA_RETRIEVAL_FAILED -> {
                                    EventProcessingOutcome.RetryableFailure(decision.detail ?: "DATA_RETRIEVAL_FAILED")
                                }
                                CorrectionIneligibilityReason.EVENT_OWNERSHIP_MISMATCH -> {
                                    EventProcessingOutcome.PermanentFailure(decision.detail ?: "EVENT_OWNERSHIP_MISMATCH")
                                }
                                else -> {
                                    EventProcessingOutcome.SuccessNoChange("INELIGIBLE: ${decision.ineligibilityReason?.name ?: "UNKNOWN"}")
                                }
                            }
                        }
                    }
                } else {
                    // Safe no-op handling when evaluation pipeline is unconfigured
                    EventProcessingOutcome.SuccessNoChange("UNVERIFIED_CORRECTION_SAFE_NO_OP")
                }
            }
            EventType.INTERVENTION_ACCEPTED -> {
                if (interventionRepository == null) {
                    EventProcessingOutcome.SuccessNoChange("INTERVENTION_TELEMETRY_SAFE_NO_OP")
                } else {
                    handleInterventionAccepted(
                        userId = event.userId,
                        targetId = event.causationId ?: event.entityId,
                        occurredAt = event.occurredAt
                    )
                    EventProcessingOutcome.Success
                }
            }
            EventType.INTERVENTION_DISMISSED -> {
                if (interventionRepository == null) {
                    EventProcessingOutcome.SuccessNoChange("INTERVENTION_TELEMETRY_SAFE_NO_OP")
                } else {
                    resolveInterventionIfPresent(
                        userId = event.userId,
                        targetId = event.causationId ?: event.entityId,
                        reason = InterventionResolutionReason.DISMISSED,
                        occurredAt = event.occurredAt
                    )
                    EventProcessingOutcome.Success
                }
            }
            EventType.INTERVENTION_IGNORED -> {
                if (interventionRepository == null) {
                    EventProcessingOutcome.SuccessNoChange("INTERVENTION_TELEMETRY_SAFE_NO_OP")
                } else {
                    resolveInterventionIfPresent(
                        userId = event.userId,
                        targetId = event.causationId ?: event.entityId,
                        reason = InterventionResolutionReason.IGNORED,
                        occurredAt = event.occurredAt
                    )
                    EventProcessingOutcome.Success
                }
            }
            EventType.OUTCOME_RECORDED -> {
                if (interventionRepository == null) {
                    EventProcessingOutcome.SuccessNoChange("OUTCOME_TELEMETRY_SAFE_NO_OP")
                } else {
                    resolveInterventionIfPresent(
                        userId = event.userId,
                        targetId = event.causationId ?: event.entityId,
                        reason = InterventionResolutionReason.SUCCESS,
                        occurredAt = event.occurredAt
                    )
                    EventProcessingOutcome.Success
                }
            }
            EventType.TASK_COMPLETED,
            EventType.COMMITMENT_COMPLETED -> {
                if (interventionRepository != null && !event.causationId.isNullOrBlank()) {
                    resolveInterventionIfPresent(
                        userId = event.userId,
                        targetId = event.causationId,
                        reason = InterventionResolutionReason.SUCCESS,
                        occurredAt = event.occurredAt
                    )
                    EventProcessingOutcome.Success
                } else {
                    EventProcessingOutcome.SuccessNoChange("COMPLETION_TELEMETRY_SAFE_NO_OP")
                }
            }
            else -> EventProcessingOutcome.UnsupportedEventType(event.type)
        }
    }

    private suspend fun handleInterventionAccepted(
        userId: String,
        targetId: String?,
        occurredAt: Long
    ) {
        val repo = interventionRepository ?: return
        if (targetId.isNullOrBlank()) return
        val result = repo.getById(targetId)
        if (result is com.sanket_satpute_20.ironmind.domain.common.Result.Success && result.data != null) {
            val record = result.data!!
            if (record.userId == userId) {
                // Acceptance marks user response; outcome remains unverified until verified outcome or completion
                if (record.state != InterventionState.OUTCOME && record.state != InterventionState.FAILED) {
                    val updated = record.copy(
                        state = InterventionState.RESPONSE,
                        resolutionReason = null,
                        updatedAt = occurredAt
                    )
                    repo.save(updated)
                    logger?.logLifecycle(
                        "EventProcessor",
                        "INTERVENTION_ACCEPTED",
                        mapOf("id" to targetId, "state" to InterventionState.RESPONSE.name)
                    )
                }
            }
        }
    }

    private suspend fun resolveInterventionIfPresent(
        userId: String,
        targetId: String?,
        reason: InterventionResolutionReason,
        occurredAt: Long
    ) {
        val repo = interventionRepository ?: return
        if (targetId.isNullOrBlank()) return
        val result = repo.getById(targetId)
        if (result is com.sanket_satpute_20.ironmind.domain.common.Result.Success && result.data != null) {
            val record = result.data!!
            if (record.userId == userId) {
                if (record.state != InterventionState.OUTCOME && record.state != InterventionState.FAILED) {
                    val finalState = when (reason) {
                        InterventionResolutionReason.TECHNICAL_FAILURE,
                        InterventionResolutionReason.BEHAVIORAL_FAILURE -> InterventionState.FAILED
                        else -> InterventionState.OUTCOME
                    }
                    val updated = record.copy(
                        state = finalState,
                        resolutionReason = reason,
                        updatedAt = occurredAt
                    )
                    repo.save(updated)
                    logger?.logLifecycle(
                        "EventProcessor",
                        "INTERVENTION_RESOLVED",
                        mapOf("id" to targetId, "reason" to reason.name, "state" to finalState.name)
                    )
                }
            }
        }
    }
}
