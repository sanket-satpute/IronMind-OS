package com.sanket_satpute_20.ironmind.domain.model

data class ProgressInfo(
    val completedTasks: Int,
    val totalTasks: Int,
    val pendingTasks: Int,
    val percentage: Int
) {
    val progressFraction: Float
        get() = if (totalTasks > 0) completedTasks.toFloat() / totalTasks.toFloat() else 0f

    val isZeroTasks: Boolean
        get() = totalTasks == 0

    companion object {
        val ZERO = ProgressInfo(
            completedTasks = 0,
            totalTasks = 0,
            pendingTasks = 0,
            percentage = 0
        )

        /**
         * Calculates [ProgressInfo] from a collection of tasks.
         *
         * Task status classification rules:
         * - Completed: [TaskStatus.COMPLETED]
         * - Pending: [TaskStatus.PENDING], [TaskStatus.IN_PROGRESS], [TaskStatus.POSTPONED]
         * - Terminal non-completed: [TaskStatus.FAILED], [TaskStatus.CANCELLED] (counted in total, not completed or pending)
         * - If [userId] is specified, only tasks with matching userId are considered.
         * - If total tasks is 0, percentage is 0 (division-by-zero safe).
         */
        fun fromTasks(tasks: Collection<Task>, userId: String? = null): ProgressInfo {
            val userTasks = if (userId != null) {
                tasks.filter { it.userId == userId }
            } else {
                tasks.toList()
            }

            val total = userTasks.size
            if (total == 0) return ZERO

            val completed = userTasks.count { it.status == TaskStatus.COMPLETED }
            val pending = userTasks.count {
                it.status == TaskStatus.PENDING ||
                it.status == TaskStatus.IN_PROGRESS ||
                it.status == TaskStatus.POSTPONED
            }
            val percentage = (completed * 100) / total

            return ProgressInfo(
                completedTasks = completed,
                totalTasks = total,
                pendingTasks = pending,
                percentage = percentage
            )
        }
    }
}
