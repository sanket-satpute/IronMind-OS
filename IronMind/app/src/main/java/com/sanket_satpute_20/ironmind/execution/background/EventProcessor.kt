package com.sanket_satpute_20.ironmind.execution.background

import com.sanket_satpute_20.ironmind.domain.model.Event
import com.sanket_satpute_20.ironmind.domain.model.EventType

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
 * Default event processor for Sprint V2.16-C3.4.
 * Implements safe no-op handling for current unverified correction events and intervention telemetry,
 * recording processing completion without modifying Memory, Patterns, or Barriers.
 */
class DefaultEventProcessor : EventProcessor {
    override suspend fun processEvent(event: Event): EventProcessingOutcome {
        return when (event.type) {
            EventType.INTERVENTION_OVERRIDDEN -> {
                // V2.16-C3.4 Safe no-op handling for current unverified correction events.
                // Current production UI transmits boilerplate text ("User marked as corrected")
                // with scope=INTERVENTION_CORRECTION and source=EntitySource.SYSTEM.
                // These must be marked completed without modifying Memory, Patterns, or Barriers.
                EventProcessingOutcome.SuccessNoChange("UNVERIFIED_CORRECTION_SAFE_NO_OP")
            }
            EventType.INTERVENTION_ACCEPTED,
            EventType.INTERVENTION_DISMISSED,
            EventType.INTERVENTION_IGNORED -> {
                // Non-learning intervention telemetry events complete as safe no-ops
                EventProcessingOutcome.SuccessNoChange("INTERVENTION_TELEMETRY_SAFE_NO_OP")
            }
            else -> EventProcessingOutcome.UnsupportedEventType(event.type)
        }
    }
}
