package com.sanket_satpute_20.ironmind.testutil.fake

import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.Task
import com.sanket_satpute_20.ironmind.domain.repository.TaskRepository

class FakeTaskRepository : TaskRepository {
    private val tasks = mutableMapOf<String, Task>()
    var shouldFail = false

    override suspend fun saveTask(task: Task): Result<Unit, Exception> {
        if (shouldFail) return Result.Failure(Exception("Fake failure"))
        tasks[task.id] = task
        return Result.Success(Unit)
    }

    override suspend fun getTask(id: String): Result<Task?, Exception> {
        if (shouldFail) return Result.Failure(Exception("Fake failure"))
        return Result.Success(tasks[id])
    }

    override suspend fun getTasksForPlan(planId: String): Result<List<Task>, Exception> {
        if (shouldFail) return Result.Failure(Exception("Fake failure"))
        return Result.Success(tasks.values.filter { it.planId == planId }.sortedBy { it.createdAt })
    }

    override suspend fun getTasksForGoal(goalId: String): Result<List<Task>, Exception> {
        if (shouldFail) return Result.Failure(Exception("Fake failure"))
        return Result.Success(tasks.values.filter { it.goalId == goalId }.sortedBy { it.createdAt })
    }

    override suspend fun deleteTask(userId: String, taskId: String): Result<Unit, Exception> {
        if (shouldFail) return Result.Failure(Exception("Fake failure"))
        val existing = tasks[taskId] ?: return Result.Failure(IllegalArgumentException("Task not found: $taskId"))
        if (existing.userId != userId) return Result.Failure(IllegalStateException("Task $taskId does not belong to user $userId"))
        tasks.remove(taskId)
        return Result.Success(Unit)
    }

    override suspend fun getTasksForUser(userId: String): Result<List<Task>, Exception> {
        if (shouldFail) return Result.Failure(Exception("Fake failure"))
        return Result.Success(tasks.values.filter { it.userId == userId }.sortedByDescending { it.createdAt })
    }

    fun clear() {
        tasks.clear()
    }
}
