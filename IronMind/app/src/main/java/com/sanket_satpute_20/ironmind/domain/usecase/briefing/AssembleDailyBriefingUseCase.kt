package com.sanket_satpute_20.ironmind.domain.usecase.briefing

import com.sanket_satpute_20.ironmind.domain.common.Clock
import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.engine.DailyBriefingEngine
import com.sanket_satpute_20.ironmind.domain.engine.PersonalIntelligenceOrchestrator
import com.sanket_satpute_20.ironmind.domain.model.Commitment
import com.sanket_satpute_20.ironmind.domain.model.DueTaskItem
import com.sanket_satpute_20.ironmind.domain.model.Goal
import com.sanket_satpute_20.ironmind.domain.model.Plan
import com.sanket_satpute_20.ironmind.domain.model.action.ProposedAction
import com.sanket_satpute_20.ironmind.domain.model.briefing.DailyBriefing
import com.sanket_satpute_20.ironmind.domain.model.insight.PersonalInsight
import com.sanket_satpute_20.ironmind.domain.model.intervention.InterventionRecommendation
import com.sanket_satpute_20.ironmind.domain.repository.ActionRepository
import com.sanket_satpute_20.ironmind.domain.repository.GoalRepository
import com.sanket_satpute_20.ironmind.domain.repository.InsightRepository
import com.sanket_satpute_20.ironmind.domain.repository.InterventionRecommendationRepository
import com.sanket_satpute_20.ironmind.domain.repository.PlanRepository
import com.sanket_satpute_20.ironmind.domain.usecase.action.ActionLifecycleUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.commitment.GetActiveCommitmentsUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.task.GetDueTasksUseCase
import java.time.ZoneId

/**
 * Phase 3.5 & 3.5.1: Use case to orchestrate intelligence and assemble the Daily Briefing for a user.
 * Hardened with durable action persistence and stable action identities.
 */
class AssembleDailyBriefingUseCase(
    private val actionRepository: ActionRepository? = null,
    private val dailyBriefingEngine: DailyBriefingEngine,
    private val personalIntelligenceOrchestrator: PersonalIntelligenceOrchestrator,
    private val getDueTasksUseCase: GetDueTasksUseCase? = null,
    private val getActiveCommitmentsUseCase: GetActiveCommitmentsUseCase? = null,
    private val goalRepository: GoalRepository? = null,
    private val planRepository: PlanRepository? = null,
    private val insightRepository: InsightRepository? = null,
    private val recommendationRepository: InterventionRecommendationRepository? = null,
    private val clock: Clock,
    private val actionLifecycleUseCase: ActionLifecycleUseCase? = null
) {

    suspend operator fun invoke(
        userId: String,
        windowStartTimeMs: Long? = null,
        windowEndTimeMs: Long? = null,
        zoneId: ZoneId = ZoneId.systemDefault(),
        existingActions: List<ProposedAction> = emptyList()
    ): Result<DailyBriefing, Exception> {
        if (userId.isBlank()) {
            return Result.Failure(IllegalArgumentException("userId cannot be blank"))
        }

        // Reconcile any interrupted executions left in EXECUTING state
        actionLifecycleUseCase?.reconcileInterruptedActions(userId)

        val now = clock.currentTimeMillis()
        val startMs = windowStartTimeMs ?: (now - (7L * 24 * 60 * 60 * 1000L))
        val endMs = windowEndTimeMs ?: now

        // 0. Load persisted actions for user
        val persistedActions = when (val res = actionRepository?.getActionsForUser(userId)) {
            is Result.Success -> res.data
            else -> emptyList()
        }
        val combinedExisting = (persistedActions + existingActions).distinctBy { it.id }

        // 1. Run master intelligence orchestrator
        val intelResult = when (val res = personalIntelligenceOrchestrator.orchestrate(
            userId = userId,
            windowStartTimeMs = startMs,
            windowEndTimeMs = endMs,
            zoneId = zoneId
        )) {
            is Result.Success -> res.data
            is Result.Failure -> return Result.Failure(res.error)
        }

        val unifiedContext = intelResult.pipelineResult.unifiedContext
        val activePatterns = intelResult.pipelineResult.activePatterns
        val barrierHypotheses = intelResult.pipelineResult.barrierHypotheses
        val allInsights = intelResult.allActiveInsights

        // 2. Due and overdue tasks
        val dueTasksResult = getDueTasksUseCase?.invoke(userId)
        val overdueTasks = if (dueTasksResult is Result.Success) dueTasksResult.data.overdue else emptyList()
        val dueTodayTasks = if (dueTasksResult is Result.Success) dueTasksResult.data.dueToday else emptyList()

        // 3. Active commitments
        val commitmentsResult = getActiveCommitmentsUseCase?.invoke(userId)
        val activeCommitments = if (commitmentsResult is Result.Success) commitmentsResult.data else emptyList()

        // 4. Goals and Plans
        val goalsResult = goalRepository?.getGoalsForUser(userId)
        val goals = if (goalsResult is Result.Success) goalsResult.data else emptyList()

        val plansResult = planRepository?.getPlansForUser(userId)
        val plans = if (plansResult is Result.Success) plansResult.data else emptyList()

        // 5. Recommendations
        val recsResult = recommendationRepository?.getRecommendationsForUser(userId, startMs, endMs, limit = 50)
        val recommendations = if (recsResult is Result.Success) recsResult.data else intelResult.pipelineResult.interventionRecommendations

        // 6. Assemble Daily Briefing
        val briefing = dailyBriefingEngine.assembleBriefing(
            userId = userId,
            unifiedContext = unifiedContext,
            overdueTasks = overdueTasks,
            dueTodayTasks = dueTodayTasks,
            activeCommitments = activeCommitments,
            goals = goals,
            plans = plans,
            patterns = activePatterns,
            barrierHypotheses = barrierHypotheses,
            recommendations = recommendations,
            insights = allInsights,
            existingActions = combinedExisting
        )

        // 7. Durably persist newly generated actions
        for (action in briefing.proposedNextActions) {
            if (persistedActions.none { it.id == action.id }) {
                actionRepository?.saveAction(action)
            }
        }

        return Result.Success(briefing)
    }
}
