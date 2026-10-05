package com.sanket_satpute_20.ironmind.domain.usecase.ai

import com.sanket_satpute_20.ironmind.domain.ai.InterventionObjective
import com.sanket_satpute_20.ironmind.domain.ai.InterventionType
import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.EntitySource
import com.sanket_satpute_20.ironmind.domain.model.Event
import com.sanket_satpute_20.ironmind.domain.model.EventType
import com.sanket_satpute_20.ironmind.domain.model.intervention.IntelligenceHistoryResponse
import com.sanket_satpute_20.ironmind.domain.model.intervention.InterventionRecommendation
import com.sanket_satpute_20.ironmind.domain.model.intervention.InterventionRecommendationStatus
import com.sanket_satpute_20.ironmind.testutil.fake.FakeEventRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class GetIntelligenceHistoryUseCaseTest {

    private lateinit var recommendationRepo: FakeInterventionRecommendationRepository
    private lateinit var eventRepo: FakeEventRepository
    private lateinit var useCase: GetIntelligenceHistoryUseCase

    @Before
    fun setup() {
        recommendationRepo = FakeInterventionRecommendationRepository()
        eventRepo = FakeEventRepository()
        useCase = GetIntelligenceHistoryUseCase(recommendationRepo, eventRepo)
    }

    private fun dummyRec(
        id: String,
        userId: String = "user-1",
        createdAt: Long = 1000L,
        status: InterventionRecommendationStatus = InterventionRecommendationStatus.PENDING
    ) = InterventionRecommendation(
        id = id,
        userId = userId,
        interventionType = InterventionType.BREAK_DOWN,
        objective = InterventionObjective.INITIATE_ACTION,
        targetEntityId = "target-1",
        targetEntityType = "TASK",
        rationale = "Because.",
        suggestedAction = "Do something.",
        status = status,
        createdAt = createdAt,
        expiresAt = null
    )

    private fun dummyEvent(
        id: String,
        userId: String = "user-1",
        type: EventType,
        causationId: String? = null,
        occurredAt: Long = 1500L,
        metadata: String? = null,
        entityId: String? = null
    ) = Event(
        id = id,
        userId = userId,
        type = type,
        entityType = "Intervention",
        entityId = entityId,
        occurredAt = occurredAt,
        recordedAt = occurredAt,
        source = EntitySource.USER,
        metadata = metadata,
        causationId = causationId
    )

    @Test
    fun `Empty history returns empty list`() = runTest {
        val result = useCase("user-1", 0L, 10000L, 50)
        assertTrue(result is Result.Success)
        assertEquals(0, (result as Result.Success).data.size)
    }

    @Test
    fun `Single pending recommendation with no events returns history item with no effective response`() = runTest {
        recommendationRepo.saveRecommendation(dummyRec("rec-1"))

        val result = useCase("user-1", 0L, 10000L, 50)
        assertTrue(result is Result.Success)
        
        val items = (result as Result.Success).data
        assertEquals(1, items.size)
        val item = items[0]
        
        assertEquals(InterventionRecommendationStatus.PENDING, item.recommendationStatus)
        assertNull(item.effectiveResponse)
        assertNull(item.responseTimestamp)
        assertNull(item.correctionText)
    }

    @Test
    fun `Accepted recommendation`() = runTest {
        recommendationRepo.saveRecommendation(dummyRec("rec-1", status = InterventionRecommendationStatus.ACCEPTED))
        eventRepo.saveEvent(dummyEvent("evt-1", type = EventType.INTERVENTION_ACCEPTED, causationId = "rec-1"))

        val result = useCase("user-1", 0L, 10000L, 50)
        val items = (result as Result.Success).data
        assertEquals(1, items.size)
        assertEquals(IntelligenceHistoryResponse.ACCEPTED, items[0].effectiveResponse)
        assertEquals(1500L, items[0].responseTimestamp)
    }

    @Test
    fun `Rejected or dismissed recommendation`() = runTest {
        recommendationRepo.saveRecommendation(dummyRec("rec-1", status = InterventionRecommendationStatus.REJECTED))
        eventRepo.saveEvent(dummyEvent("evt-1", type = EventType.INTERVENTION_DISMISSED, causationId = "rec-1"))

        val result = useCase("user-1", 0L, 10000L, 50)
        val items = (result as Result.Success).data
        assertEquals(IntelligenceHistoryResponse.REJECTED, items[0].effectiveResponse)
    }

    @Test
    fun `Ignored recommendation`() = runTest {
        recommendationRepo.saveRecommendation(dummyRec("rec-1", status = InterventionRecommendationStatus.IGNORED))
        eventRepo.saveEvent(dummyEvent("evt-1", type = EventType.INTERVENTION_IGNORED, causationId = "rec-1"))

        val result = useCase("user-1", 0L, 10000L, 50)
        val items = (result as Result.Success).data
        assertEquals(IntelligenceHistoryResponse.IGNORED, items[0].effectiveResponse)
    }

    @Test
    fun `Corrected recommendation`() = runTest {
        recommendationRepo.saveRecommendation(dummyRec("rec-1", status = InterventionRecommendationStatus.PENDING))
        eventRepo.saveEvent(dummyEvent("evt-1", type = EventType.INTERVENTION_OVERRIDDEN, causationId = "rec-1", metadata = "type=BREAK_DOWN,correctedText=Do it better,other=xyz"))

        val result = useCase("user-1", 0L, 10000L, 50)
        val items = (result as Result.Success).data
        assertEquals(IntelligenceHistoryResponse.CORRECTED, items[0].effectiveResponse)
        assertEquals("Do it better", items[0].correctionText)
    }

    @Test
    fun `Multiple response events - latest causally linked response wins`() = runTest {
        recommendationRepo.saveRecommendation(dummyRec("rec-1", status = InterventionRecommendationStatus.ACCEPTED))
        eventRepo.saveEvent(dummyEvent("evt-1", type = EventType.INTERVENTION_IGNORED, causationId = "rec-1", occurredAt = 1000L))
        eventRepo.saveEvent(dummyEvent("evt-2", type = EventType.INTERVENTION_ACCEPTED, causationId = "rec-1", occurredAt = 2000L))

        val result = useCase("user-1", 0L, 10000L, 50)
        val items = (result as Result.Success).data
        assertEquals(IntelligenceHistoryResponse.ACCEPTED, items[0].effectiveResponse)
        assertEquals(2000L, items[0].responseTimestamp)
    }

    @Test
    fun `Null causationId response is not linked`() = runTest {
        recommendationRepo.saveRecommendation(dummyRec("rec-1", status = InterventionRecommendationStatus.PENDING))
        eventRepo.saveEvent(dummyEvent("evt-1", type = EventType.INTERVENTION_ACCEPTED, causationId = null)) // Old uncategorized event

        val result = useCase("user-1", 0L, 10000L, 50)
        val items = (result as Result.Success).data
        assertNull(items[0].effectiveResponse) // Unlinked
    }

    @Test
    fun `Similar targetEntityId but different causationId is not linked`() = runTest {
        recommendationRepo.saveRecommendation(dummyRec("rec-1", status = InterventionRecommendationStatus.PENDING))
        eventRepo.saveEvent(dummyEvent("evt-1", type = EventType.INTERVENTION_ACCEPTED, causationId = "rec-OTHER", entityId = "target-1"))

        val result = useCase("user-1", 0L, 10000L, 50)
        val items = (result as Result.Success).data
        assertNull(items[0].effectiveResponse) // Unlinked
    }

    @Test
    fun `Multiple recommendations are independently correlated and ordering remains newest-first`() = runTest {
        recommendationRepo.saveRecommendation(dummyRec("rec-1", createdAt = 1000L, status = InterventionRecommendationStatus.IGNORED))
        recommendationRepo.saveRecommendation(dummyRec("rec-2", createdAt = 2000L, status = InterventionRecommendationStatus.ACCEPTED))
        
        eventRepo.saveEvent(dummyEvent("evt-1", type = EventType.INTERVENTION_IGNORED, causationId = "rec-1"))
        eventRepo.saveEvent(dummyEvent("evt-2", type = EventType.INTERVENTION_ACCEPTED, causationId = "rec-2"))

        val result = useCase("user-1", 0L, 10000L, 50)
        val items = (result as Result.Success).data
        assertEquals(2, items.size)
        // Newest first based on createdAt
        assertEquals(2000L, items[0].recommendationTimestamp)
        assertEquals(IntelligenceHistoryResponse.ACCEPTED, items[0].effectiveResponse)
        
        assertEquals(1000L, items[1].recommendationTimestamp)
        assertEquals(IntelligenceHistoryResponse.IGNORED, items[1].effectiveResponse)
    }

    @Test
    fun `User scoping is preserved`() = runTest {
        recommendationRepo.saveRecommendation(dummyRec("rec-1", userId = "user-1"))
        recommendationRepo.saveRecommendation(dummyRec("rec-2", userId = "user-2")) // different user

        val result = useCase("user-1", 0L, 10000L, 50)
        val items = (result as Result.Success).data
        assertEquals(1, items.size) // Only user-1's rec
    }

    @Test
    fun `Limit is passed to recommendation repository`() = runTest {
        recommendationRepo.saveRecommendation(dummyRec("rec-1", createdAt = 1000L))
        recommendationRepo.saveRecommendation(dummyRec("rec-2", createdAt = 2000L))
        recommendationRepo.saveRecommendation(dummyRec("rec-3", createdAt = 3000L))

        val result = useCase("user-1", 0L, 10000L, 2)
        val items = (result as Result.Success).data
        assertEquals(2, items.size)
        assertEquals(3000L, items[0].recommendationTimestamp) // Newest
        assertEquals(2000L, items[1].recommendationTimestamp)
    }

    @Test
    fun `Malformed correction metadata does not crash`() = runTest {
        recommendationRepo.saveRecommendation(dummyRec("rec-1", status = InterventionRecommendationStatus.PENDING))
        eventRepo.saveEvent(dummyEvent("evt-1", type = EventType.INTERVENTION_OVERRIDDEN, causationId = "rec-1", metadata = "malformed"))

        val result = useCase("user-1", 0L, 10000L, 50)
        val items = (result as Result.Success).data
        assertEquals(IntelligenceHistoryResponse.CORRECTED, items[0].effectiveResponse)
        assertNull(items[0].correctionText) // Returns null if not found instead of crashing
    }

    @Test
    fun `Recommendation repository failure fails fast`() = runTest {
        recommendationRepo.shouldFail = true
        val result = useCase("user-1", 0L, 10000L, 50)
        assertTrue(result is Result.Failure)
    }

    @Test
    fun `Event repository failure fails fast`() = runTest {
        recommendationRepo.saveRecommendation(dummyRec("rec-1"))
        eventRepo.shouldFail = true
        val result = useCase("user-1", 0L, 10000L, 50)
        assertTrue(result is Result.Failure)
    }

    @Test
    fun `Persisted recommendation status remains authoritative even when response event type differs`() = runTest {
        // Recommendation persisted as ACCEPTED, but a later OVERRIDDEN event also exists.
        // The read model must expose the persisted status (ACCEPTED) as recommendationStatus,
        // while the effectiveResponse reflects the latest causal event (CORRECTED).
        recommendationRepo.saveRecommendation(dummyRec("rec-1", status = InterventionRecommendationStatus.ACCEPTED))
        eventRepo.saveEvent(dummyEvent("evt-1", type = EventType.INTERVENTION_ACCEPTED, causationId = "rec-1", occurredAt = 1000L))
        eventRepo.saveEvent(dummyEvent("evt-2", type = EventType.INTERVENTION_OVERRIDDEN, causationId = "rec-1", occurredAt = 2000L, metadata = "type=BREAK_DOWN,correctedText=Better idea"))

        val result = useCase("user-1", 0L, 10000L, 50)
        val items = (result as Result.Success).data
        assertEquals(1, items.size)
        // Persisted status is authoritative
        assertEquals(InterventionRecommendationStatus.ACCEPTED, items[0].recommendationStatus)
        // Effective response reflects the latest causal event
        assertEquals(IntelligenceHistoryResponse.CORRECTED, items[0].effectiveResponse)
        assertEquals("Better idea", items[0].correctionText)
    }

    @Test
    fun `No AI or internal fields leak into the read model`() = runTest {
        // Verify the IntelligenceHistoryItem exposes only user-facing fields.
        // The recommendation has targetEntityId, but it must NOT appear in the read model.
        recommendationRepo.saveRecommendation(dummyRec("rec-1"))

        val result = useCase("user-1", 0L, 10000L, 50)
        val items = (result as Result.Success).data
        assertEquals(1, items.size)
        val item = items[0]

        // Verify only user-facing fields are present by asserting their values
        assertEquals(1000L, item.recommendationTimestamp)
        assertEquals(InterventionType.BREAK_DOWN, item.interventionType)
        assertEquals(InterventionObjective.INITIATE_ACTION, item.objective)
        assertEquals("Because.", item.rationale)
        assertEquals("Do something.", item.suggestedAction)
        assertEquals(InterventionRecommendationStatus.PENDING, item.recommendationStatus)
        assertNull(item.effectiveResponse)
        assertNull(item.responseTimestamp)
        assertNull(item.correctionText)

        // Compile-time guarantee: IntelligenceHistoryItem has exactly 9 properties.
        // If someone adds an internal field, this count changes and fails.
        val propertyCount = item::class.members.count { it is kotlin.reflect.KProperty<*> }
        assertEquals(
            "IntelligenceHistoryItem should have exactly 9 properties",
            9,
            propertyCount
        )
    }

    @Test
    fun `Null metadata on override produces null correction text`() = runTest {
        recommendationRepo.saveRecommendation(dummyRec("rec-1"))
        eventRepo.saveEvent(dummyEvent("evt-1", type = EventType.INTERVENTION_OVERRIDDEN, causationId = "rec-1", metadata = null))

        val result = useCase("user-1", 0L, 10000L, 50)
        val items = (result as Result.Success).data
        assertEquals(IntelligenceHistoryResponse.CORRECTED, items[0].effectiveResponse)
        assertNull(items[0].correctionText)
    }

    @Test
    fun `Correction text at end of metadata string is extracted correctly`() = runTest {
        recommendationRepo.saveRecommendation(dummyRec("rec-1"))
        eventRepo.saveEvent(dummyEvent("evt-1", type = EventType.INTERVENTION_OVERRIDDEN, causationId = "rec-1", metadata = "type=BREAK_DOWN,recommendation=Do something.,correctedText=My correction"))

        val result = useCase("user-1", 0L, 10000L, 50)
        val items = (result as Result.Success).data
        assertEquals("My correction", items[0].correctionText)
    }
}
