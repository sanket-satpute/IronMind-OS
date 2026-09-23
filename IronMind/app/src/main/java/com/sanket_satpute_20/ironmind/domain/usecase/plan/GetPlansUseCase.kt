package com.sanket_satpute_20.ironmind.domain.usecase.plan

import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.Plan
import com.sanket_satpute_20.ironmind.domain.repository.PlanRepository

class GetPlansUseCase(
    private val repository: PlanRepository
) {
    suspend operator fun invoke(goalId: String): Result<List<Plan>, Exception> {
        return repository.getPlansForGoal(goalId)
    }
}
