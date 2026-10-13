package com.sanket_satpute_20.ironmind.execution.background

import com.sanket_satpute_20.ironmind.domain.model.EntitySource
import com.sanket_satpute_20.ironmind.domain.model.Event
import com.sanket_satpute_20.ironmind.domain.model.EventType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultEventProcessorTest {

    private val processor = DefaultEventProcessor()

    private fun createEvent(type: EventType, metadata: String? = null) = Event(
        id = "event-1",
        userId = "user-123",
        type = type,
        entityType = "Intervention",
        entityId = "rec-1",
        occurredAt = 1000L,
        recordedAt = 1000L,
        source = EntitySource.SYSTEM,
        metadata = metadata,
        causationId = "rec-1"
    )

    @Test
    fun `T-C3_5-12 INTERVENTION_OVERRIDDEN returns SuccessNoChange UNVERIFIED_CORRECTION_SAFE_NO_OP with zero side effects`() = runBlocking {
        val event = createEvent(
            type = EventType.INTERVENTION_OVERRIDDEN,
            metadata = "scope=INTERVENTION_CORRECTION,type=HABIT,recommendation=Drink water,correctedText=Take medication"
        )

        val outcome = processor.processEvent(event)

        assertTrue(outcome is EventProcessingOutcome.SuccessNoChange)
        assertEquals("UNVERIFIED_CORRECTION_SAFE_NO_OP", (outcome as EventProcessingOutcome.SuccessNoChange).reason)
    }

    @Test
    fun `telemetry intervention events return SuccessNoChange INTERVENTION_TELEMETRY_SAFE_NO_OP`() = runBlocking {
        val acceptedEvent = createEvent(type = EventType.INTERVENTION_ACCEPTED)
        val dismissedEvent = createEvent(type = EventType.INTERVENTION_DISMISSED)
        val ignoredEvent = createEvent(type = EventType.INTERVENTION_IGNORED)

        val outcomeAccepted = processor.processEvent(acceptedEvent)
        val outcomeDismissed = processor.processEvent(dismissedEvent)
        val outcomeIgnored = processor.processEvent(ignoredEvent)

        assertTrue(outcomeAccepted is EventProcessingOutcome.SuccessNoChange)
        assertEquals("INTERVENTION_TELEMETRY_SAFE_NO_OP", (outcomeAccepted as EventProcessingOutcome.SuccessNoChange).reason)

        assertTrue(outcomeDismissed is EventProcessingOutcome.SuccessNoChange)
        assertEquals("INTERVENTION_TELEMETRY_SAFE_NO_OP", (outcomeDismissed as EventProcessingOutcome.SuccessNoChange).reason)

        assertTrue(outcomeIgnored is EventProcessingOutcome.SuccessNoChange)
        assertEquals("INTERVENTION_TELEMETRY_SAFE_NO_OP", (outcomeIgnored as EventProcessingOutcome.SuccessNoChange).reason)
    }

    @Test
    fun `unhandled event type returns UnsupportedEventType`() = runBlocking {
        val unhandledEvent = createEvent(type = EventType.GOAL_CREATED)
        val outcome = processor.processEvent(unhandledEvent)

        assertTrue(outcome is EventProcessingOutcome.UnsupportedEventType)
        assertEquals(EventType.GOAL_CREATED, (outcome as EventProcessingOutcome.UnsupportedEventType).eventType)
    }
}
