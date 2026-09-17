package com.sanket_satpute_20.ironmind.testutil.fake

import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.Plan
import com.sanket_satpute_20.ironmind.domain.repository.PlanRepository

class FakePlanRepository : PlanRepository {
    private val plans = mutableMapOf<String, Plan>()
    var shouldFail = false

    override suspend fun savePlan(plan: Plan): Result<Unit, Exception> {
        if (shouldFail) return Result.Failure(Exception("Fake failure"))
        plans[plan.id] = plan
        return Result.Success(Unit)
    }

    override suspend fun getPlan(id: String): Result<Plan?, Exception> {
        if (shouldFail) return Result.Failure(Exception("Fake failure"))
        return Result.Success(plans[id])
    }

    override suspend fun getPlansForGoal(goalId: String): Result<List<Plan>, Exception> {
        if (shouldFail) return Result.Failure(Exception("Fake failure"))
        return Result.Success(plans.values.filter { it.goalId == goalId }.sortedByDescending { it.createdAt })
    }

    fun clear() {
        plans.clear()
    }
}
