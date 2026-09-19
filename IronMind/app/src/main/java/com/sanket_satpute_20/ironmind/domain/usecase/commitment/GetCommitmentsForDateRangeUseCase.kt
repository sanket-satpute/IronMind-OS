package com.sanket_satpute_20.ironmind.domain.usecase.commitment

import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.Commitment
import com.sanket_satpute_20.ironmind.domain.repository.CommitmentRepository

class GetCommitmentsForDateRangeUseCase(
    private val repository: CommitmentRepository
) {
    suspend operator fun invoke(userId: String, startTime: Long, endTime: Long): Result<List<Commitment>, Exception> {
        return repository.getCommitmentsForDateRange(userId, startTime, endTime)
    }
}
