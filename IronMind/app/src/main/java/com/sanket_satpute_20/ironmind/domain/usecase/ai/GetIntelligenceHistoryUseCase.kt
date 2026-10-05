package com.sanket_satpute_20.ironmind.domain.usecase.ai

import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.Event
import com.sanket_satpute_20.ironmind.domain.model.EventType
import com.sanket_satpute_20.ironmind.domain.model.intervention.IntelligenceHistoryItem
import com.sanket_satpute_20.ironmind.domain.model.intervention.IntelligenceHistoryResponse
import com.sanket_satpute_20.ironmind.domain.repository.EventRepository
import com.sanket_satpute_20.ironmind.domain.repository.InterventionRecommendationRepository

/**
 * Retrieves the user-facing intelligence history.
 *
 * This use case constructs a deterministic read model mapping recommendations 
 * to their latest causally-linked response event (ACCEPT, REJECT, IGNORE, CORRECT).
 * It intentionally scopes out AI debug context, returning only transparency metadata.
 */
class GetIntelligenceHistoryUseCase(
    private val interventionRecommendationRepository: InterventionRecommendationRepository,
    private val eventRepository: EventRepository
) {
    suspend operator fun invoke(
        userId: String,
        startTime: Long,
        endTime: Long,
        limit: Int
    ): Result<List<IntelligenceHistoryItem>, Exception> {
        val recommendationsResult = interventionRecommendationRepository.getRecommendationsForUser(
            userId = userId,
            startTime = startTime,
            endTime = endTime,
            limit = limit
        )

        if (recommendationsResult is Result.Failure) {
            return recommendationsResult
        }

        val recommendations = (recommendationsResult as Result.Success).data
        val historyItems = mutableListOf<IntelligenceHistoryItem>()

        for (recommendation in recommendations) {
            val eventsResult = eventRepository.getEventsByCausationId(userId, recommendation.id)
            if (eventsResult is Result.Failure) {
                return Result.Failure(eventsResult.error) // Fail-fast on repository error
            }

            val events = (eventsResult as Result.Success).data
            
            // Apply deterministic ordering to find the latest valid response
            val sortedEvents = events.sortedWith(compareBy<Event> { it.occurredAt }.thenBy { it.id })
            
            val effectiveEvent = sortedEvents.lastOrNull { event ->
                event.type == EventType.INTERVENTION_ACCEPTED ||
                event.type == EventType.INTERVENTION_DISMISSED ||
                event.type == EventType.INTERVENTION_IGNORED ||
                event.type == EventType.INTERVENTION_OVERRIDDEN
            }

            var effectiveResponse: IntelligenceHistoryResponse? = null
            var responseTimestamp: Long? = null
            var correctionText: String? = null

            if (effectiveEvent != null) {
                effectiveResponse = when (effectiveEvent.type) {
                    EventType.INTERVENTION_ACCEPTED -> IntelligenceHistoryResponse.ACCEPTED
                    EventType.INTERVENTION_DISMISSED -> IntelligenceHistoryResponse.REJECTED
                    EventType.INTERVENTION_IGNORED -> IntelligenceHistoryResponse.IGNORED
                    EventType.INTERVENTION_OVERRIDDEN -> IntelligenceHistoryResponse.CORRECTED
                    else -> null
                }
                
                responseTimestamp = effectiveEvent.occurredAt
                
                if (effectiveEvent.type == EventType.INTERVENTION_OVERRIDDEN) {
                    correctionText = extractCorrectedText(effectiveEvent.metadata)
                }
            }

            historyItems.add(
                IntelligenceHistoryItem(
                    recommendationTimestamp = recommendation.createdAt,
                    interventionType = recommendation.interventionType,
                    objective = recommendation.objective,
                    rationale = recommendation.rationale,
                    suggestedAction = recommendation.suggestedAction,
                    recommendationStatus = recommendation.status,
                    effectiveResponse = effectiveResponse,
                    responseTimestamp = responseTimestamp,
                    correctionText = correctionText
                )
            )
        }

        // Return results newest recommendation first. The repo handles sorting (createdAt DESC, id ASC).
        return Result.Success(historyItems)
    }

    private fun extractCorrectedText(metadata: String?): String? {
        if (metadata == null) return null
        val token = "correctedText="
        val startIndex = metadata.indexOf(token)
        if (startIndex == -1) return null
        
        // Find next comma to support "key=val,key2=val2" format, assuming comma is the delimiter
        val endIndex = metadata.indexOf(",", startIndex + token.length)
        return if (endIndex == -1) {
            metadata.substring(startIndex + token.length)
        } else {
            metadata.substring(startIndex + token.length, endIndex)
        }
    }
}
