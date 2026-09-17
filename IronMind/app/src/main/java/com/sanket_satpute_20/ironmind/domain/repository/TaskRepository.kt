package com.sanket_satpute_20.ironmind.domain.repository

import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.Task

interface TaskRepository : Repository {
    suspend fun saveTask(task: Task): Result<Unit, Exception>
    suspend fun getTask(id: String): Result<Task?, Exception>
    suspend fun getTasksForPlan(planId: String): Result<List<Task>, Exception>
    suspend fun getTasksForGoal(goalId: String): Result<List<Task>, Exception>
}
