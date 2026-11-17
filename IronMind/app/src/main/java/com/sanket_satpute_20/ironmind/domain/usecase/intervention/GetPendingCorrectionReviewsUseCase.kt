package com.sanket_satpute_20.ironmind.domain.usecase.intervention

import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.intervention.CorrectionReviewItem
import com.sanket_satpute_20.ironmind.domain.repository.CorrectionReviewRepository
import kotlinx.coroutines.flow.Flow

class GetPendingCorrectionReviewsUseCase(
    private val correctionReviewRepository: CorrectionReviewRepository
) {
    suspend operator fun invoke(
        userId: String,
        limit: Int = 100
    ): Result<List<CorrectionReviewItem>, Exception> {
        if (userId.isBlank()) {
            return Result.Failure(IllegalArgumentException("userId must not be blank"))
        }
        return correctionReviewRepository.getPendingReviews(userId, limit)
    }

    fun observe(userId: String): Flow<List<CorrectionReviewItem>> {
        return correctionReviewRepository.observePendingReviews(userId)
    }
}
