package com.sanket_satpute_20.ironmind.domain.model

data class DueTaskItem(
    val task: Task,
    val planTitle: String? = null,
    val isOverdue: Boolean
)

data class DueTasksResult(
    val dueToday: List<DueTaskItem>,
    val overdue: List<DueTaskItem>
)
