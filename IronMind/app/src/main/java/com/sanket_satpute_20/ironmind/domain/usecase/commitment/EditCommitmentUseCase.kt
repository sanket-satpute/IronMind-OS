package com.sanket_satpute_20.ironmind.domain.usecase.commitment

import com.sanket_satpute_20.ironmind.domain.common.Clock
import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.Commitment
import com.sanket_satpute_20.ironmind.domain.provider.ReminderScheduler
import com.sanket_satpute_20.ironmind.domain.repository.CommitmentRepository

class EditCommitmentUseCase(
    private val repository: CommitmentRepository,
    private val reminderScheduler: ReminderScheduler,
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

        // In EditCommitmentUseCase, if scheduledStartAt is null in the request, it doesn't clear the existing one.
        // Wait, the signature uses `scheduledStartAt: Long?`. In Kotlin, passing null means "don't change it", 
        // but how do we clear it? Usually we pass a wrapper or use a specific value. 
        // The existing logic is `scheduledStartAt = scheduledStartAt ?: commitment.scheduledStartAt`.
        // So we can only update it to a new value, not clear it here.
        val updatedScheduledStartAt = scheduledStartAt ?: commitment.scheduledStartAt

        val updatedCommitment = commitment.copy(
            title = title ?: commitment.title,
            description = description ?: commitment.description,
            priority = priority ?: commitment.priority,
            scheduledStartAt = updatedScheduledStartAt,
            scheduledEndAt = scheduledEndAt ?: commitment.scheduledEndAt,
            updatedAt = clock.currentTimeMillis()
        )

        return when (val saveResult = repository.saveCommitment(updatedCommitment)) {
            is Result.Failure -> Result.Failure(saveResult.error)
            is Result.Success -> {
                if (updatedScheduledStartAt != commitment.scheduledStartAt || (title != null && title != commitment.title)) {
                    if (updatedScheduledStartAt != null) {
                        reminderScheduler.scheduleReminder(updatedCommitment.id, updatedScheduledStartAt, updatedCommitment.title)
                    }
                }
                Result.Success(updatedCommitment)
            }
        }
    }
}
