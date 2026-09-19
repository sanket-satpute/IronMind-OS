package com.sanket_satpute_20.ironmind.domain.usecase.commitment

import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.Commitment
import com.sanket_satpute_20.ironmind.domain.model.CommitmentStatus
import com.sanket_satpute_20.ironmind.domain.repository.CommitmentRepository

class GetActiveCommitmentsUseCase(
    private val repository: CommitmentRepository
) {
    suspend operator fun invoke(userId: String): Result<List<Commitment>, Exception> {
        return repository.getActiveCommitmentsForUser(
            userId = userId,
            statuses = listOf(
                CommitmentStatus.COMMITTED,
                CommitmentStatus.STARTED
            )
        )
    }
}
