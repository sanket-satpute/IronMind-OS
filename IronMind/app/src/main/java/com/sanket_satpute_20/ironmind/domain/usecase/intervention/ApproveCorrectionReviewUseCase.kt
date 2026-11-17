package com.sanket_satpute_20.ironmind.domain.usecase.intervention

import com.sanket_satpute_20.ironmind.domain.common.Clock
import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.intervention.CorrectionReviewStatus
import com.sanket_satpute_20.ironmind.domain.repository.CorrectionReviewRepository

class ApproveCorrectionReviewUseCase(
    private val correctionReviewRepository: CorrectionReviewRepository,
    private val clock: Clock
) {
    suspend operator fun invoke(
        userId: String,
        reviewId: String,
        feedback: String? = null
    ): Result<Unit, Exception> {
        if (userId.isBlank()) {
            return Result.Failure(IllegalArgumentException("userId must not be blank"))
        }
        if (reviewId.isBlank()) {
            return Result.Failure(IllegalArgumentException("reviewId must not be blank"))
        }

        val now = clock.currentTimeMillis()
        return correctionReviewRepository.updateReviewDecision(
            userId = userId,
            reviewId = reviewId,
            newStatus = CorrectionReviewStatus.APPROVED,
            feedback = feedback,
            reviewedAt = now
        )
    }
}
