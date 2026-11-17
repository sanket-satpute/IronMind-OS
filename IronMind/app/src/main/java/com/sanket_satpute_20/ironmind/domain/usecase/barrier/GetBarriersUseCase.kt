package com.sanket_satpute_20.ironmind.domain.usecase.barrier

import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.barrier.BarrierHypothesis
import com.sanket_satpute_20.ironmind.domain.repository.BarrierRepository
import kotlinx.coroutines.flow.Flow

class GetBarriersUseCase(
    private val barrierRepository: BarrierRepository
) {
    fun observe(userId: String): Flow<List<BarrierHypothesis>> {
        return barrierRepository.observeBarriersForUser(userId)
    }

    suspend operator fun invoke(userId: String): Result<List<BarrierHypothesis>, Exception> {
        if (userId.isBlank()) {
            return Result.Failure(IllegalArgumentException("userId must not be blank"))
        }
        return barrierRepository.getBarriersForUser(userId)
    }
}
