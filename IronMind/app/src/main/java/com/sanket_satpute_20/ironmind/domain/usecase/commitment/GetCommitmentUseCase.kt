package com.sanket_satpute_20.ironmind.domain.usecase.commitment

import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.Commitment
import com.sanket_satpute_20.ironmind.domain.repository.CommitmentRepository

class GetCommitmentUseCase(
    private val repository: CommitmentRepository
) {
    suspend operator fun invoke(id: String): Result<Commitment?, Exception> {
        return repository.getCommitment(id)
    }
}
