package com.sanket_satpute_20.ironmind.domain.usecase.goal

import com.sanket_satpute_20.ironmind.domain.common.Clock
import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.Goal
import com.sanket_satpute_20.ironmind.domain.model.GoalStatus
import com.sanket_satpute_20.ironmind.domain.repository.GoalRepository

class UpdateGoalStatusUseCase(
    private val repository: GoalRepository,
    private val clock: Clock
) {
    suspend operator fun invoke(
        goalId: String,
        newStatus: GoalStatus
    ): Result<Goal, Exception> {
        val existingGoalResult = repository.getGoal(goalId)
        if (existingGoalResult is Result.Failure) {
            return Result.Failure(existingGoalResult.error)
        }

        val existingGoal = (existingGoalResult as Result.Success).data 
            ?: return Result.Failure(IllegalArgumentException("Goal not found"))

        // Don't update if status is already the same
        if (existingGoal.status == newStatus) {
            return Result.Success(existingGoal)
        }

        val now = clock.currentTimeMillis()
        
        // Handle completion timestamp
        val completedAt = if (newStatus == GoalStatus.COMPLETED || newStatus == GoalStatus.ENDED) {
            existingGoal.completedAt ?: now
        } else {
            // If transitioned back to active, remove completion timestamp
            null
        }

        val updatedGoal = existingGoal.copy(
            status = newStatus,
            completedAt = completedAt,
            updatedAt = now
        )

        return when (val result = repository.saveGoal(updatedGoal)) {
            is Result.Failure -> Result.Failure(result.error)
            is Result.Success -> Result.Success(updatedGoal)
        }
    }
}
