package com.sanket_satpute_20.ironmind.domain.usecase.goal

import com.sanket_satpute_20.ironmind.domain.common.Clock
import com.sanket_satpute_20.ironmind.domain.common.IdGenerator
import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.Goal
import com.sanket_satpute_20.ironmind.domain.model.GoalStatus
import com.sanket_satpute_20.ironmind.domain.repository.GoalRepository

class CreateGoalUseCase(
    private val repository: GoalRepository,
    private val idGenerator: IdGenerator,
    private val clock: Clock
) {
    suspend operator fun invoke(
        userId: String,
        title: String,
        description: String,
        why: String,
        importance: Int,
        targetAt: Long?
    ): Result<Goal, Exception> {
        if (title.isBlank()) {
            return Result.Failure(IllegalArgumentException("Title cannot be blank"))
        }
        if (importance !in 1..10) {
            return Result.Failure(IllegalArgumentException("Importance must be between 1 and 10"))
        }

        val now = clock.currentTimeMillis()
        val goal = Goal(
            id = idGenerator.generateId(),
            userId = userId,
            title = title,
            description = description,
            why = why,
            importance = importance,
            status = GoalStatus.ACTIVE,
            targetAt = targetAt,
            startedAt = now,
            createdAt = now,
            updatedAt = now
        )

        return when (val result = repository.saveGoal(goal)) {
            is Result.Failure -> Result.Failure(result.error)
            is Result.Success -> Result.Success(goal)
        }
    }
}
