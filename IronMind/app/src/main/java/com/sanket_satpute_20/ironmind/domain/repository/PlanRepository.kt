package com.sanket_satpute_20.ironmind.domain.repository

import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.Plan

interface PlanRepository : Repository {
    suspend fun savePlan(plan: Plan): Result<Unit, Exception>
    suspend fun getPlan(id: String): Result<Plan?, Exception>
    suspend fun getPlansForGoal(goalId: String): Result<List<Plan>, Exception>
}
