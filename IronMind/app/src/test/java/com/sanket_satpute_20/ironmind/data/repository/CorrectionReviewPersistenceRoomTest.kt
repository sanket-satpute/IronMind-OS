package com.sanket_satpute_20.ironmind.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.sanket_satpute_20.ironmind.data.local.IronMindDatabase
import com.sanket_satpute_20.ironmind.data.local.Jdk25CompatibleRobolectricTestRunner
import com.sanket_satpute_20.ironmind.data.local.dao.CorrectionReviewDao
import com.sanket_satpute_20.ironmind.data.local.entity.CorrectionReviewEntity
import com.sanket_satpute_20.ironmind.data.local.entity.toDomainModel
import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.intervention.CausalHypothesisReference
import com.sanket_satpute_20.ironmind.domain.model.intervention.CausalHypothesisType
import com.sanket_satpute_20.ironmind.domain.model.intervention.CorrectionReviewItem
import com.sanket_satpute_20.ironmind.domain.model.intervention.CorrectionReviewStatus
import com.sanket_satpute_20.ironmind.domain.model.intervention.CorrectionScope
import com.sanket_satpute_20.ironmind.domain.repository.EventRepository
import com.sanket_satpute_20.ironmind.domain.usecase.intervention.EvaluateCorrectionReviewDecisionUseCase
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(Jdk25CompatibleRobolectricTestRunner::class)
@Config(sdk = [34])
class CorrectionReviewPersistenceRoomTest {

    private lateinit var context: Context
    private lateinit var db: IronMindDatabase
    private lateinit var dao: CorrectionReviewDao
    private lateinit var repository: CorrectionReviewRepositoryImpl

    private val user1 = "user-alice"
    private val user2 = "user-bob"

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, IronMindDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.correctionReviewDao()

        val mockEventRepo = mockk<EventRepository>(relaxed = true)
        val mockEvalUseCase = mockk<EvaluateCorrectionReviewDecisionUseCase>(relaxed = true)

        repository = CorrectionReviewRepositoryImpl(
            eventRepository = mockEventRepo,
            evaluateCorrectionReviewDecisionUseCase = mockEvalUseCase,
            correctionReviewDao = dao
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun createItem(
        id: String = "cr-1",
        userId: String = user1,
        sourceEventId: String = "event-1",
        status: CorrectionReviewStatus = CorrectionReviewStatus.PENDING,
        createdAt: Long = 1000L
    ) = CorrectionReviewItem(
        id = id,
        userId = userId,
        sourceEventId = sourceEventId,
        recommendationId = "rec-1",
        causalHypothesis = CausalHypothesisReference(id = "pat-walk", type = CausalHypothesisType.PATTERN),
        correctionScope = CorrectionScope.INFERENCE_CORRECTION,
        correctedText = "Only morning walks",
        decisionState = "DEFERRED_FOR_CONSIDERATION",
        reviewStatus = status,
        createdAt = createdAt,
        updatedAt = createdAt
    )

    @Test
    fun `persistCandidate inserts record durably in Room`() = runBlocking {
        val item = createItem()
        val result = repository.persistCandidate(item)

        assertTrue(result is Result.Success)

        val retrieved = repository.getReview(user1, item.id)
        assertTrue(retrieved is Result.Success)
        val data = (retrieved as Result.Success).data
        assertNotNull(data)
        assertEquals(item.id, data?.id)
        assertEquals(user1, data?.userId)
        assertEquals("Only morning walks", data?.correctedText)
        assertEquals(CorrectionReviewStatus.PENDING, data?.reviewStatus)
    }

    @Test
    fun `unique constraint on userId and sourceEventId prevents duplicates and preserves existing status`() = runBlocking {
        val item1 = createItem(id = "cr-1", sourceEventId = "event-unique")
        val insert1 = repository.persistCandidate(item1)
        assertTrue(insert1 is Result.Success)

        // User approves the review
        val approveResult = repository.updateReviewDecision(
            userId = user1,
            reviewId = "cr-1",
            newStatus = CorrectionReviewStatus.APPROVED,
            feedback = "Verified correction",
            reviewedAt = 2000L
        )
        assertTrue(approveResult is Result.Success)

        // Retry of event processing attempts to re-persist candidate with same (userId, sourceEventId)
        val itemRetry = createItem(id = "cr-different-id", sourceEventId = "event-unique")
        val insertRetry = repository.persistCandidate(itemRetry)
        assertTrue(insertRetry is Result.Success) // Handled idempotently

        // Verify existing APPROVED status is preserved and not overwritten to PENDING
        val record = repository.getReview(user1, "cr-1")
        val reviewData = (record as Result.Success).data
        assertNotNull(reviewData)
        assertEquals(CorrectionReviewStatus.APPROVED, reviewData?.reviewStatus)
        assertEquals("Verified correction", reviewData?.reviewFeedback)
        assertEquals(2000L, reviewData?.reviewedAt)
    }

    @Test
    fun `user isolation strictly isolates candidate reads and updates`() = runBlocking {
        val aliceItem = createItem(id = "cr-alice", userId = user1, sourceEventId = "event-alice")
        val bobItem = createItem(id = "cr-bob", userId = user2, sourceEventId = "event-bob")

        repository.persistCandidate(aliceItem)
        repository.persistCandidate(bobItem)

        // Bob cannot read Alice's review
        val bobLookupAlice = repository.getReview(user2, "cr-alice")
        assertTrue(bobLookupAlice is Result.Success)
        assertNull((bobLookupAlice as Result.Success).data)

        // Alice cannot read Bob's review
        val aliceLookupBob = repository.getReview(user1, "cr-bob")
        assertTrue(aliceLookupBob is Result.Success)
        assertNull((aliceLookupBob as Result.Success).data)

        // Bob cannot update Alice's review
        val bobUpdateAlice = repository.updateReviewDecision(
            userId = user2,
            reviewId = "cr-alice",
            newStatus = CorrectionReviewStatus.APPROVED,
            reviewedAt = 2000L
        )
        assertTrue(bobUpdateAlice is Result.Failure)

        // Pending reviews for Alice only returns Alice's items
        val alicePending = repository.getPendingReviews(user1)
        assertTrue(alicePending is Result.Success)
        val aliceList = (alicePending as Result.Success).data
        assertEquals(1, aliceList.size)
        assertEquals("cr-alice", aliceList.first().id)
    }

    @Test
    fun `state transitions enforce valid lifecycle and reject invalid mutations`() = runBlocking {
        val item = createItem(id = "cr-lifecycle")
        repository.persistCandidate(item)

        // Valid transition: PENDING -> APPROVED
        val approve = repository.updateReviewDecision(
            userId = user1,
            reviewId = "cr-lifecycle",
            newStatus = CorrectionReviewStatus.APPROVED,
            reviewedAt = 2000L
        )
        assertTrue(approve is Result.Success)

        // Idempotent retry: APPROVED -> APPROVED succeeds
        val idempotentApprove = repository.updateReviewDecision(
            userId = user1,
            reviewId = "cr-lifecycle",
            newStatus = CorrectionReviewStatus.APPROVED,
            reviewedAt = 3000L
        )
        assertTrue(idempotentApprove is Result.Success)

        // Invalid transition: APPROVED -> REFUTED fails closed
        val invalidTransition = repository.updateReviewDecision(
            userId = user1,
            reviewId = "cr-lifecycle",
            newStatus = CorrectionReviewStatus.REFUTED,
            reviewedAt = 4000L
        )
        assertTrue(invalidTransition is Result.Failure)
        assertTrue((invalidTransition as Result.Failure).error.message?.contains("Cannot transition") == true)
    }

    @Test
    fun `observePendingReviews emits reactive updates`() = runBlocking {
        val flow = repository.observePendingReviews(user1)
        assertEquals(0, flow.first().size)

        repository.persistCandidate(createItem(id = "cr-flow-1", sourceEventId = "ev-1"))
        assertEquals(1, flow.first().size)

        repository.updateReviewDecision(
            userId = user1,
            reviewId = "cr-flow-1",
            newStatus = CorrectionReviewStatus.DISMISSED,
            reviewedAt = 2000L
        )
        assertEquals(0, flow.first().size)
    }

    @Test
    fun `deleteAllForUser completely deletes target user records and preserves other users`() = runBlocking {
        repository.persistCandidate(createItem(id = "cr-alice", userId = user1, sourceEventId = "ev-alice"))
        repository.persistCandidate(createItem(id = "cr-bob", userId = user2, sourceEventId = "ev-bob"))

        val deleteResult = repository.deleteAllForUser(user1)
        assertTrue(deleteResult is Result.Success)

        val aliceItems = repository.getPendingReviews(user1)
        assertTrue(aliceItems is Result.Success)
        assertTrue((aliceItems as Result.Success).data.isEmpty())

        val bobItems = repository.getPendingReviews(user2)
        assertTrue(bobItems is Result.Success)
        assertEquals(1, (bobItems as Result.Success).data.size)
    }
}

