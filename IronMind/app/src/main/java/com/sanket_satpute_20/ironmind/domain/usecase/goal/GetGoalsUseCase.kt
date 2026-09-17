package com.sanket_satpute_20.ironmind.domain.usecase.goal

import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.Goal
import com.sanket_satpute_20.ironmind.domain.repository.GoalRepository

class GetGoalsUseCase(
    private val repository: GoalRepository
) {
    suspend operator fun invoke(userId: String): Result<List<Goal>, Exception> {
        return repository.getGoalsForUser(userId)
    }
}
