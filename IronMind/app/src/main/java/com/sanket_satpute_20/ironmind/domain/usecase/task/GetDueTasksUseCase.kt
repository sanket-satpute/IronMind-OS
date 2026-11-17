package com.sanket_satpute_20.ironmind.domain.usecase.task

import com.sanket_satpute_20.ironmind.domain.common.Clock
import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.DueTaskItem
import com.sanket_satpute_20.ironmind.domain.model.DueTasksResult
import com.sanket_satpute_20.ironmind.domain.model.Task
import com.sanket_satpute_20.ironmind.domain.model.TaskStatus
import com.sanket_satpute_20.ironmind.domain.repository.PlanRepository
import com.sanket_satpute_20.ironmind.domain.repository.TaskRepository
import java.time.Instant
import java.time.ZoneId

class GetDueTasksUseCase(
    private val taskRepository: TaskRepository,
    private val planRepository: PlanRepository? = null,
    private val clock: Clock? = null
) {
    suspend operator fun invoke(
        userId: String,
        referenceTimeMillis: Long = clock?.currentTimeMillis() ?: System.currentTimeMillis(),
        zoneId: ZoneId = ZoneId.systemDefault()
    ): Result<DueTasksResult, Exception> {
        if (userId.isBlank()) {
            return Result.Failure(IllegalArgumentException("userId cannot be blank"))
        }

        val tasksResult = taskRepository.getTasksForUser(userId)
        if (tasksResult is Result.Failure) {
            return Result.Failure(tasksResult.error)
        }

        val allTasks = (tasksResult as Result.Success).data

        // Calculate local day boundaries
        val localDate = Instant.ofEpochMilli(referenceTimeMillis).atZone(zoneId).toLocalDate()
        val startOfToday = localDate.atStartOfDay(zoneId).toInstant().toEpochMilli()
        val startOfTomorrow = localDate.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli()

        // Filter: active tasks belonging to userId with dueAt specified
        val candidateTasks = allTasks.filter { task ->
            task.userId == userId &&
                task.dueAt != null &&
                task.status != TaskStatus.COMPLETED &&
                task.status != TaskStatus.CANCELLED
        }

        val dueTodayTasks = mutableListOf<Task>()
        val overdueTasks = mutableListOf<Task>()

        for (task in candidateTasks) {
            val dueAt = task.dueAt!!
            when {
                dueAt < startOfToday -> overdueTasks.add(task)
                dueAt < startOfTomorrow -> dueTodayTasks.add(task)
                // Tasks due on or after startOfTomorrow are future-due and excluded from today/overdue
            }
        }

        // Cache plan titles for distinct planIds to avoid redundant lookups
        val planTitleCache = mutableMapOf<String, String?>()
        if (planRepository != null) {
            val distinctPlanIds = (dueTodayTasks + overdueTasks).mapNotNull { it.planId }.distinct()
            for (planId in distinctPlanIds) {
                val planResult = planRepository.getPlan(planId)
                if (planResult is Result.Success) {
                    planTitleCache[planId] = planResult.data?.title
                }
            }
        }

        val dueTodayItems = dueTodayTasks
            .sortedBy { it.dueAt }
            .map { task ->
                DueTaskItem(
                    task = task,
                    planTitle = task.planId?.let { planTitleCache[it] },
                    isOverdue = false
                )
            }

        val overdueItems = overdueTasks
            .sortedBy { it.dueAt }
            .map { task ->
                DueTaskItem(
                    task = task,
                    planTitle = task.planId?.let { planTitleCache[it] },
                    isOverdue = true
                )
            }

        return Result.Success(
            DueTasksResult(
                dueToday = dueTodayItems,
                overdue = overdueItems
            )
        )
    }
}
