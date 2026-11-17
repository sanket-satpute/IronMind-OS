package com.sanket_satpute_20.ironmind.domain.usecase.intervention

import com.sanket_satpute_20.ironmind.domain.common.Clock
import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.intervention.CausalHypothesisReference
import com.sanket_satpute_20.ironmind.domain.model.intervention.CausalHypothesisType
import com.sanket_satpute_20.ironmind.domain.model.intervention.CorrectionReviewItem
import com.sanket_satpute_20.ironmind.domain.model.intervention.CorrectionReviewStatus
import com.sanket_satpute_20.ironmind.domain.model.intervention.CorrectionScope
import com.sanket_satpute_20.ironmind.domain.repository.BarrierRepository
import com.sanket_satpute_20.ironmind.domain.repository.CorrectionReviewRepository
import com.sanket_satpute_20.ironmind.domain.repository.PatternRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class CorrectionReviewDecisionUseCasesTest {

    private lateinit var mockReviewRepo: CorrectionReviewRepository
    private lateinit var mockPatternRepo: PatternRepository
    private lateinit var mockBarrierRepo: BarrierRepository
    private lateinit var clock: Clock

    private lateinit var approveUseCase: ApproveCorrectionReviewUseCase
    private lateinit var refuteUseCase: RefuteCorrectionReviewUseCase
    private lateinit var dismissUseCase: DismissCorrectionReviewUseCase
    private lateinit var getPendingUseCase: GetPendingCorrectionReviewsUseCase

    private val userId = "user-123"
    private val reviewId = "review-abc"

    @Before
    fun setUp() {
        mockReviewRepo = mockk(relaxed = true)
        mockPatternRepo = mockk(relaxed = true)
        mockBarrierRepo = mockk(relaxed = true)
        clock = object : Clock {
            override fun currentTimeMillis(): Long = 5000L
        }

        approveUseCase = ApproveCorrectionReviewUseCase(mockReviewRepo, clock)
        refuteUseCase = RefuteCorrectionReviewUseCase(mockReviewRepo, clock)
        dismissUseCase = DismissCorrectionReviewUseCase(mockReviewRepo, clock)
        getPendingUseCase = GetPendingCorrectionReviewsUseCase(mockReviewRepo)
    }

    @Test
    fun `approve candidate delegates to repository with APPROVED status and timestamp`() = runBlocking {
        coEvery {
            mockReviewRepo.updateReviewDecision(userId, reviewId, CorrectionReviewStatus.APPROVED, "Verified", 5000L)
        } returns Result.Success(Unit)

        val result = approveUseCase(userId, reviewId, "Verified")

        assertTrue(result is Result.Success)
        coVerify(exactly = 1) {
            mockReviewRepo.updateReviewDecision(userId, reviewId, CorrectionReviewStatus.APPROVED, "Verified", 5000L)
        }

        // Epistemic safety invariant: Zero mutation to Pattern or Barrier repositories
        coVerify(exactly = 0) { mockPatternRepo.savePattern(any()) }
        coVerify(exactly = 0) { mockPatternRepo.updatePattern(any()) }
        coVerify(exactly = 0) { mockPatternRepo.deletePattern(any()) }
        coVerify(exactly = 0) { mockBarrierRepo.saveBarrier(any()) }
    }

    @Test
    fun `refute candidate delegates to repository with REFUTED status and timestamp`() = runBlocking {
        coEvery {
            mockReviewRepo.updateReviewDecision(userId, reviewId, CorrectionReviewStatus.REFUTED, "Situational only", 5000L)
        } returns Result.Success(Unit)

        val result = refuteUseCase(userId, reviewId, "Situational only")

        assertTrue(result is Result.Success)
        coVerify(exactly = 1) {
            mockReviewRepo.updateReviewDecision(userId, reviewId, CorrectionReviewStatus.REFUTED, "Situational only", 5000L)
        }

        // Epistemic safety invariant: Zero mutation to Pattern or Barrier repositories
        coVerify(exactly = 0) { mockPatternRepo.savePattern(any()) }
        coVerify(exactly = 0) { mockBarrierRepo.saveBarrier(any()) }
    }

    @Test
    fun `dismiss candidate delegates to repository with DISMISSED status and timestamp`() = runBlocking {
        coEvery {
            mockReviewRepo.updateReviewDecision(userId, reviewId, CorrectionReviewStatus.DISMISSED, null, 5000L)
        } returns Result.Success(Unit)

        val result = dismissUseCase(userId, reviewId, null)

        assertTrue(result is Result.Success)
        coVerify(exactly = 1) {
            mockReviewRepo.updateReviewDecision(userId, reviewId, CorrectionReviewStatus.DISMISSED, null, 5000L)
        }

        coVerify(exactly = 0) { mockPatternRepo.savePattern(any()) }
        coVerify(exactly = 0) { mockBarrierRepo.saveBarrier(any()) }
    }

    @Test
    fun `blank userId or reviewId fails closed`() = runBlocking {
        val blankUser = approveUseCase("", reviewId)
        assertTrue(blankUser is Result.Failure)

        val blankReview = refuteUseCase(userId, "")
        assertTrue(blankReview is Result.Failure)

        val blankDismiss = dismissUseCase(userId, "   ")
        assertTrue(blankDismiss is Result.Failure)

        coVerify(exactly = 0) { mockReviewRepo.updateReviewDecision(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `getPendingCorrectionReviewsUseCase delegates to repository`() = runBlocking {
        val item = CorrectionReviewItem(
            id = reviewId,
            userId = userId,
            sourceEventId = "ev-1",
            recommendationId = "rec-1",
            causalHypothesis = CausalHypothesisReference("pat-1", CausalHypothesisType.PATTERN),
            correctionScope = CorrectionScope.INFERENCE_CORRECTION,
            correctedText = "Walking only",
            decisionState = "DEFERRED_FOR_CONSIDERATION",
            reviewStatus = CorrectionReviewStatus.PENDING,
            createdAt = 1000L,
            updatedAt = 1000L
        )

        coEvery { mockReviewRepo.getPendingReviews(userId, 50) } returns Result.Success(listOf(item))

        val result = getPendingUseCase(userId, 50)

        assertTrue(result is Result.Success)
        assertEquals(1, (result as Result.Success).data.size)
        assertEquals(reviewId, result.data.first().id)
    }
}

