package com.sanket_satpute_20.ironmind.domain.usecase.goal

import com.sanket_satpute_20.ironmind.domain.common.Clock
import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.Goal
import com.sanket_satpute_20.ironmind.domain.repository.GoalRepository

class EditGoalUseCase(
    private val repository: GoalRepository,
    private val clock: Clock
) {
    suspend operator fun invoke(
        goalId: String,
        title: String?,
        description: String?,
        why: String?,
        importance: Int?,
        targetAt: Long?
    ): Result<Goal, Exception> {
        val existingGoalResult = repository.getGoal(goalId)
        if (existingGoalResult is Result.Failure) {
            return Result.Failure(existingGoalResult.error)
        }

        val existingGoal = (existingGoalResult as Result.Success).data 
            ?: return Result.Failure(IllegalArgumentException("Goal not found"))

        if (title != null && title.isBlank()) {
            return Result.Failure(IllegalArgumentException("Title cannot be blank"))
        }
        if (importance != null && importance !in 1..10) {
            return Result.Failure(IllegalArgumentException("Importance must be between 1 and 10"))
        }

        val updatedGoal = existingGoal.copy(
            title = title ?: existingGoal.title,
            description = description ?: existingGoal.description,
            why = why ?: existingGoal.why,
            importance = importance ?: existingGoal.importance,
            targetAt = targetAt ?: existingGoal.targetAt,
            updatedAt = clock.currentTimeMillis()
        )

        return when (val result = repository.saveGoal(updatedGoal)) {
            is Result.Failure -> Result.Failure(result.error)
            is Result.Success -> Result.Success(updatedGoal)
        }
    }
}
