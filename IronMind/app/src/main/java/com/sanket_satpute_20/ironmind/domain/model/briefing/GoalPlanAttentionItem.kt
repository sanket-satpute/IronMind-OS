package com.sanket_satpute_20.ironmind.domain.model.briefing

enum class AttentionReason {
    STALLED_PLAN,
    OVERDUE_TASKS_PRESENT,
    NO_TASKS_SCHEDULED,
    LOW_COMPLETION_RATE
}

/**
 * Phase 3.5: Represents a Goal or Plan that requires user attention today.
 */
data class GoalPlanAttentionItem(
    val entityId: String,
    val entityType: String, // "GOAL" or "PLAN"
    val title: String,
    val reason: AttentionReason,
    val description: String,
    val totalTasks: Int = 0,
    val completedTasks: Int = 0,
    val pendingTasks: Int = 0,
    val overdueTasks: Int = 0
)
