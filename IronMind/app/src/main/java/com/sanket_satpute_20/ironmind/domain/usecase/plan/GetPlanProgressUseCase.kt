package com.sanket_satpute_20.ironmind.domain.usecase.plan

import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.ProgressInfo
import com.sanket_satpute_20.ironmind.domain.model.Task
import com.sanket_satpute_20.ironmind.domain.repository.TaskRepository

class GetPlanProgressUseCase(
    private val taskRepository: TaskRepository
) {
    suspend operator fun invoke(planId: String, userId: String): Result<ProgressInfo, Exception> {
        return when (val result = taskRepository.getTasksForPlan(planId)) {
            is Result.Failure -> Result.Failure(result.error)
            is Result.Success -> {
                val progress = ProgressInfo.fromTasks(result.data, userId)
                Result.Success(progress)
            }
        }
    }

    fun calculate(tasks: List<Task>, userId: String? = null): ProgressInfo {
        return ProgressInfo.fromTasks(tasks, userId)
    }
}
