package com.sanket_satpute_20.ironmind.domain.engine

import com.sanket_satpute_20.ironmind.domain.ai.AIOutput
import com.sanket_satpute_20.ironmind.domain.ai.InterventionType
import com.sanket_satpute_20.ironmind.domain.common.Clock
import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.logging.IronLogger
import com.sanket_satpute_20.ironmind.domain.model.GoalStatus
import com.sanket_satpute_20.ironmind.domain.repository.GoalRepository
import com.sanket_satpute_20.ironmind.domain.repository.TaskRepository

class GoalResurfacingEngineImpl(
    private val goalRepository: GoalRepository,
    private val taskRepository: TaskRepository,
    private val interventionExecutionPipeline: InterventionExecutionPipeline,
    private val clock: Clock,
    private val logger: IronLogger,
    private val neglectThresholdMs: Long = 14 * 24 * 60 * 60 * 1000L // Default 14 days
) : GoalResurfacingEngine {

    override suspend fun evaluateNeglectedGoals(userId: String) {
        val now = clock.currentTimeMillis()
        
        val goalsResult = goalRepository.getGoalsForUser(userId)
        if (goalsResult is Result.Failure) return
        
        val activeGoals = (goalsResult as Result.Success).data.filter { it.status == GoalStatus.ACTIVE }

        for (goal in activeGoals) {
            val ageMs = now - goal.createdAt
            if (ageMs < neglectThresholdMs) continue // Goal is too new to be neglected

            // Find latest activity
            val tasksResult = taskRepository.getTasksForGoal(goal.id)
            val tasks = if (tasksResult is Result.Success) tasksResult.data else emptyList()
            
            val lastTaskUpdate = tasks.maxOfOrNull { it.updatedAt } ?: 0L
            val lastGoalUpdate = goal.updatedAt
            
            val latestActivity = maxOf(lastGoalUpdate, lastTaskUpdate)
            val inactivityMs = now - latestActivity

            if (inactivityMs > neglectThresholdMs) {
                // Goal is neglected
                logger.logLifecycle("GoalResurfacing", "DETECTED", mapOf("goalId" to goal.id, "inactivityDays" to (inactivityMs / (24 * 60 * 60 * 1000L)).toString()))

                val days = inactivityMs / (24 * 60 * 60 * 1000L)
                val recommendation = AIOutput.InterventionRecommendation(
                    interventionType = InterventionType.ASK,
                    recommendation = "Check on neglected goal: ${goal.title}",
                    reason = "You mentioned this goal $days days ago. You haven't worked on it recently. Is it still important?",
                    targetEntityId = goal.id,
                    confidence = 0.8f
                )

                // The pipeline will handle deduplication (so we don't ask every day) and cooldowns
                interventionExecutionPipeline.propose(userId, recommendation)
            }
        }
    }
}
