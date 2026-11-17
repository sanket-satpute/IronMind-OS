package com.sanket_satpute_20.ironmind.execution.background

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.sanket_satpute_20.ironmind.data.local.IronMindDatabase
import com.sanket_satpute_20.ironmind.data.local.Jdk25CompatibleRobolectricTestRunner
import com.sanket_satpute_20.ironmind.data.repository.CorrectionReviewRepositoryImpl
import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.EntitySource
import com.sanket_satpute_20.ironmind.domain.model.Event
import com.sanket_satpute_20.ironmind.domain.model.EventType
import com.sanket_satpute_20.ironmind.domain.model.intervention.CausalHypothesisReference
import com.sanket_satpute_20.ironmind.domain.model.intervention.CausalHypothesisType
import com.sanket_satpute_20.ironmind.domain.model.intervention.CorrectionReviewDecision
import com.sanket_satpute_20.ironmind.domain.model.intervention.CorrectionReviewStatus
import com.sanket_satpute_20.ironmind.domain.model.intervention.CorrectionScope
import com.sanket_satpute_20.ironmind.domain.repository.EventRepository
import com.sanket_satpute_20.ironmind.domain.usecase.intervention.EvaluateCorrectionReviewDecisionUseCase
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(Jdk25CompatibleRobolectricTestRunner::class)
@Config(sdk = [34])
class CorrectionReviewEventPersistenceIntegrationTest {

    private lateinit var context: Context
    private lateinit var db: IronMindDatabase
    private lateinit var eventRepository: EventRepository
    private lateinit var evaluateDecisionUseCase: EvaluateCorrectionReviewDecisionUseCase
    private lateinit var correctionReviewRepository: CorrectionReviewRepositoryImpl
    private lateinit var processor: DefaultEventProcessor

    private val userId = "user-pers-123"
    private val eventId = "ev-pers-456"
    private val recId = "rec-pers-789"

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, IronMindDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        eventRepository = mockk(relaxed = true)
        evaluateDecisionUseCase = mockk(relaxed = true)

        correctionReviewRepository = CorrectionReviewRepositoryImpl(
            eventRepository = eventRepository,
            evaluateCorrectionReviewDecisionUseCase = evaluateDecisionUseCase,
            correctionReviewDao = db.correctionReviewDao()
        )

        processor = DefaultEventProcessor(
            evaluateCorrectionReviewDecisionUseCase = evaluateDecisionUseCase,
            correctionReviewRepository = correctionReviewRepository
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun createOverrideEvent() = Event(
        id = eventId,
        userId = userId,
        type = EventType.INTERVENTION_OVERRIDDEN,
        entityType = "Intervention",
        entityId = recId,
        occurredAt = 1000L,
        recordedAt = 1000L,
        source = EntitySource.SYSTEM,
        metadata = "scope=INFERENCE_CORRECTION,correctedText=Only walk on weekends",
        causationId = recId
    )

    @Test
    fun `processEvent persists correction review in Room before returning Success`() = runBlocking {
        val event = createOverrideEvent()

        coEvery {
            evaluateDecisionUseCase.evaluate(userId, eventId, recId)
        } returns CorrectionReviewDecision.DeferredForConsideration(
            userId = userId,
            eventId = eventId,
            recommendationId = recId,
            causalHypothesis = CausalHypothesisReference(id = "pat-walk", type = CausalHypothesisType.PATTERN),
            correctionScope = CorrectionScope.INFERENCE_CORRECTION,
            correctedText = "Only walk on weekends",
            actionTaken = false,
            hypothesisDeclaredFalse = false
        )

        val outcome = processor.processEvent(event)

        assertEquals(EventProcessingOutcome.Success, outcome)

        // Verify record is durably in Room SQLite
        val entity = db.correctionReviewDao().getBySourceEventId(eventId, userId)
        assertNotNull(entity)
        assertEquals("cr_${userId}_$eventId", entity?.id)
        assertEquals(userId, entity?.userId)
        assertEquals(eventId, entity?.sourceEventId)
        assertEquals(recId, entity?.recommendationId)
        assertEquals("Only walk on weekends", entity?.correctedText)
        assertEquals("DEFERRED_FOR_CONSIDERATION", entity?.decisionState)
        assertEquals(CorrectionReviewStatus.PENDING.name, entity?.reviewStatus)
        assertEquals(1000L, entity?.createdAt)
    }

    @Test
    fun `retry of event processing is idempotent and succeeds without duplicate`() = runBlocking {
        val event = createOverrideEvent()

        coEvery {
            evaluateDecisionUseCase.evaluate(userId, eventId, recId)
        } returns CorrectionReviewDecision.DeferredForConsideration(
            userId = userId,
            eventId = eventId,
            recommendationId = recId,
            causalHypothesis = CausalHypothesisReference(id = "pat-walk", type = CausalHypothesisType.PATTERN),
            correctionScope = CorrectionScope.INFERENCE_CORRECTION,
            correctedText = "Only walk on weekends",
            actionTaken = false,
            hypothesisDeclaredFalse = false
        )

        // First execution
        val outcome1 = processor.processEvent(event)
        assertEquals(EventProcessingOutcome.Success, outcome1)

        // Simulated WorkManager retry
        val outcome2 = processor.processEvent(event)
        assertEquals(EventProcessingOutcome.Success, outcome2)

        // Verify exactly one record exists in Room
        val records = db.correctionReviewDao().getAllForUser(userId)
        assertEquals(1, records.size)
    }
}

