package com.sanket_satpute_20.ironmind.domain.repository

import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.Goal

interface GoalRepository : Repository {
    suspend fun saveGoal(goal: Goal): Result<Unit, Exception>
    suspend fun getGoal(id: String): Result<Goal?, Exception>
    suspend fun getGoalsForUser(userId: String): Result<List<Goal>, Exception>
    suspend fun searchGoals(userId: String, query: String): Result<List<Goal>, Exception>
}
