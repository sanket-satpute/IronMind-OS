package com.sanket_satpute_20.ironmind.testutil.fake

import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.Goal
import com.sanket_satpute_20.ironmind.domain.repository.GoalRepository

class FakeGoalRepository : GoalRepository {
    private val goals = mutableMapOf<String, Goal>()
    var shouldFail = false

    override suspend fun saveGoal(goal: Goal): Result<Unit, Exception> {
        if (shouldFail) return Result.Failure(Exception("Fake failure"))
        goals[goal.id] = goal
        return Result.Success(Unit)
    }

    override suspend fun getGoal(id: String): Result<Goal?, Exception> {
        if (shouldFail) return Result.Failure(Exception("Fake failure"))
        return Result.Success(goals[id])
    }

    override suspend fun getGoalsForUser(userId: String): Result<List<Goal>, Exception> {
        if (shouldFail) return Result.Failure(Exception("Fake failure"))
        return Result.Success(goals.values.filter { it.userId == userId }.sortedByDescending { it.createdAt })
    }

    fun clear() {
        goals.clear()
    }
}
