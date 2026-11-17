package com.sanket_satpute_20.ironmind.domain.usecase.goal

import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.ProgressInfo
import com.sanket_satpute_20.ironmind.domain.model.Task
import com.sanket_satpute_20.ironmind.domain.repository.PlanRepository
import com.sanket_satpute_20.ironmind.domain.repository.TaskRepository

class GetGoalProgressUseCase(
    private val planRepository: PlanRepository,
    private val taskRepository: TaskRepository
) {
    suspend operator fun invoke(goalId: String, userId: String): Result<ProgressInfo, Exception> {
        return try {
            val plansResult = planRepository.getPlansForGoal(goalId)
            val plans = if (plansResult is Result.Success) {
                plansResult.data.filter { it.userId == userId }
            } else {
                emptyList()
            }

            val tasksById = mutableMapOf<String, Task>()

            // 1. Collect tasks from all plans under this goal
            for (plan in plans) {
                val planTasksResult = taskRepository.getTasksForPlan(plan.id)
                if (planTasksResult is Result.Success) {
                    for (task in planTasksResult.data) {
                        if (task.userId == userId) {
                            tasksById[task.id] = task
                        }
                    }
                }
            }

            // 2. Collect tasks directly associated with the goal
            val directTasksResult = taskRepository.getTasksForGoal(goalId)
            if (directTasksResult is Result.Success) {
                for (task in directTasksResult.data) {
                    if (task.userId == userId) {
                        tasksById[task.id] = task
                    }
                }
            }

            val progress = ProgressInfo.fromTasks(tasksById.values, userId)
            Result.Success(progress)
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }
}
