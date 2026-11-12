package com.sanket_satpute_20.ironmind.domain.model.context

import com.sanket_satpute_20.ironmind.domain.model.Commitment
import com.sanket_satpute_20.ironmind.domain.model.DueTaskItem
import com.sanket_satpute_20.ironmind.domain.model.Event
import com.sanket_satpute_20.ironmind.domain.model.Goal
import com.sanket_satpute_20.ironmind.domain.model.Outcome
import com.sanket_satpute_20.ironmind.domain.model.Plan
import com.sanket_satpute_20.ironmind.domain.model.ProgressInfo
import com.sanket_satpute_20.ironmind.domain.model.Reflection
import com.sanket_satpute_20.ironmind.domain.model.Task
import com.sanket_satpute_20.ironmind.domain.model.barrier.BarrierHypothesis
import com.sanket_satpute_20.ironmind.domain.model.observation.Observation

data class GoalContextItem(
    val goal: Goal,
    val progress: ProgressInfo,
    val statusChangeDescription: String? = null,
    val authority: EpistemicAuthority = EpistemicAuthority.USER_CONFIRMED_FACT
)

data class PlanContextItem(
    val plan: Plan,
    val parentGoalTitle: String? = null,
    val progress: ProgressInfo,
    val authority: EpistemicAuthority = EpistemicAuthority.USER_CONFIRMED_FACT
)

data class TaskContextSummary(
    val allTasks: List<Task> = emptyList(),
    val pendingTasks: List<Task> = emptyList(),
    val inProgressTasks: List<Task> = emptyList(),
    val completedTasks: List<Task> = emptyList(),
    val overdueTasks: List<DueTaskItem> = emptyList(),
    val dueTodayTasks: List<DueTaskItem> = emptyList(),
    val postponedTasks: List<Task> = emptyList(),
    val failedTasks: List<Task> = emptyList(),
    val cancelledTasks: List<Task> = emptyList()
)

data class RepeatedMissedCommitment(
    val title: String,
    val taskId: String?,
    val planId: String?,
    val goalId: String?,
    val missCount: Int,
    val lastMissedAt: Long,
    val evidenceSummaries: List<String> = emptyList()
)

data class CommitmentContextSummary(
    val todayCommitments: List<Commitment> = emptyList(),
    val activeCommitments: List<Commitment> = emptyList(),
    val completedCommitments: List<Commitment> = emptyList(),
    val missedCommitments: List<Commitment> = emptyList(),
    val postponedCommitments: List<Commitment> = emptyList(),
    val repeatedMissedCommitments: List<RepeatedMissedCommitment> = emptyList()
)

data class ProductivityContext(
    val activeGoals: List<GoalContextItem> = emptyList(),
    val pausedGoals: List<GoalContextItem> = emptyList(),
    val completedGoals: List<GoalContextItem> = emptyList(),
    val activePlans: List<PlanContextItem> = emptyList(),
    val pausedPlans: List<PlanContextItem> = emptyList(),
    val completedPlans: List<PlanContextItem> = emptyList(),
    val taskSummary: TaskContextSummary = TaskContextSummary(),
    val commitmentSummary: CommitmentContextSummary = CommitmentContextSummary(),
    val recentReflections: List<Reflection> = emptyList(),
    val recentEvents: List<Event> = emptyList(),
    val userConfirmedObservations: List<Observation> = emptyList(),
    val recentOutcomes: List<Outcome> = emptyList(),
    val userConfirmedBarriers: List<BarrierHypothesis> = emptyList(),
    val executionTiming: ExecutionTimingSummary = ExecutionTimingSummary()
)
