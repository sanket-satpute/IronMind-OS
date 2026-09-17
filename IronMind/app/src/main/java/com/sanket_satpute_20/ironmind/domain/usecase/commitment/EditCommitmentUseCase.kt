package com.sanket_satpute_20.ironmind.domain.usecase.commitment

import com.sanket_satpute_20.ironmind.domain.common.Clock
import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.Commitment
import com.sanket_satpute_20.ironmind.domain.repository.CommitmentRepository

class EditCommitmentUseCase(
    private val repository: CommitmentRepository,
    private val clock: Clock
) {
    suspend operator fun invoke(
        commitmentId: String,
        title: String?,
        description: String?,
        priority: Int?,
        scheduledStartAt: Long?,
        scheduledEndAt: Long?
    ): Result<Commitment, Exception> {
        val existingResult = repository.getCommitment(commitmentId)
        if (existingResult is Result.Failure) return Result.Failure(existingResult.error)

        val commitment = (existingResult as Result.Success).data 
            ?: return Result.Failure(IllegalArgumentException("Commitment not found"))

        if (title != null && title.isBlank()) {
            return Result.Failure(IllegalArgumentException("Title cannot be blank"))
        }

        val updatedCommitment = commitment.copy(
            title = title ?: commitment.title,
            description = description ?: commitment.description,
            priority = priority ?: commitment.priority,
            scheduledStartAt = scheduledStartAt ?: commitment.scheduledStartAt,
            scheduledEndAt = scheduledEndAt ?: commitment.scheduledEndAt,
            updatedAt = clock.currentTimeMillis()
        )

        return when (val saveResult = repository.saveCommitment(updatedCommitment)) {
            is Result.Failure -> Result.Failure(saveResult.error)
            is Result.Success -> Result.Success(updatedCommitment)
        }
    }
}
