package com.sanket_satpute_20.ironmind.domain.engine

import com.sanket_satpute_20.ironmind.domain.common.Clock
import com.sanket_satpute_20.ironmind.domain.model.Commitment
import com.sanket_satpute_20.ironmind.domain.model.DueTaskItem
import com.sanket_satpute_20.ironmind.domain.model.Goal
import com.sanket_satpute_20.ironmind.domain.model.MemoryConfirmationState
import com.sanket_satpute_20.ironmind.domain.model.Plan
import com.sanket_satpute_20.ironmind.domain.model.PlanStatus
import com.sanket_satpute_20.ironmind.domain.model.action.ActionReviewStatus
import com.sanket_satpute_20.ironmind.domain.model.action.ProposedAction
import com.sanket_satpute_20.ironmind.domain.model.barrier.BarrierConfirmationState
import com.sanket_satpute_20.ironmind.domain.model.barrier.BarrierHypothesis
import com.sanket_satpute_20.ironmind.domain.model.briefing.AttentionReason
import com.sanket_satpute_20.ironmind.domain.model.briefing.DailyBriefing
import com.sanket_satpute_20.ironmind.domain.model.briefing.DiscoveredPatternItem
import com.sanket_satpute_20.ironmind.domain.model.briefing.EvidenceBackedBarrierItem
import com.sanket_satpute_20.ironmind.domain.model.briefing.GoalPlanAttentionItem
import com.sanket_satpute_20.ironmind.domain.model.context.EpistemicAuthority
import com.sanket_satpute_20.ironmind.domain.model.context.SourceFreshness
import com.sanket_satpute_20.ironmind.domain.model.context.UnifiedContext
import com.sanket_satpute_20.ironmind.domain.model.context.UsagePermissionStatus
import com.sanket_satpute_20.ironmind.domain.model.insight.InsightReviewStatus
import com.sanket_satpute_20.ironmind.domain.model.insight.PersonalInsight
import com.sanket_satpute_20.ironmind.domain.model.intervention.InterventionRecommendation
import com.sanket_satpute_20.ironmind.domain.model.pattern.Pattern

/**
 * Phase 3.5: Personal Intelligence Daily Briefing Assembly Engine.
 *
 * Deterministically constructs a comprehensive daily briefing answering:
 * 1. What requires my attention today? (attentionSummary)
 * 2. Which commitments or tasks are due or overdue? (overdueTasks, dueTodayTasks, activeCommitments)
 * 3. Which goals or plans need attention? (goalsAndPlansNeedingAttention)
 * 4. What relevant behavioural patterns have been discovered? (discoveredPatterns)
 * 5. What obstacles have sufficient evidence? (evidenceBackedBarriers)
 * 6. What should I consider doing next? (proposedNextActions)
 */
class DailyBriefingEngine(
    private val actionPlanningEngine: ActionPlanningEngine,
    private val clock: Clock
) {

    fun assembleBriefing(
        userId: String,
        unifiedContext: UnifiedContext,
        overdueTasks: List<DueTaskItem>,
        dueTodayTasks: List<DueTaskItem>,
        activeCommitments: List<Commitment>,
        goals: List<Goal>,
        plans: List<Plan>,
        patterns: List<Pattern>,
        barrierHypotheses: List<BarrierHypothesis>,
        recommendations: List<InterventionRecommendation>,
        insights: List<PersonalInsight>,
        existingActions: List<ProposedAction> = emptyList()
    ): DailyBriefing {
        val now = clock.currentTimeMillis()

        // -------------------------------------------------------------
        // Data Availability Note
        // -------------------------------------------------------------
        val dataAvailabilityNote = if (unifiedContext.digitalBehaviour.permissionStatus != UsagePermissionStatus.GRANTED) {
            "Usage Access permission is not granted. Digital phone behaviour context is currently unavailable."
        } else if (unifiedContext.digitalBehaviour.appUsages.isEmpty()) {
            "No app usage data recorded yet for the observation period."
        } else {
            null
        }

        // -------------------------------------------------------------
        // Goals and Plans Needing Attention
        // -------------------------------------------------------------
        val goalsAndPlansAttention = mutableListOf<GoalPlanAttentionItem>()

        // Check for active plans with overdue tasks
        for (plan in plans.filter { it.status == PlanStatus.ACTIVE }) {
            val overdueForPlan = overdueTasks.filter { it.task.planId == plan.id }
            if (overdueForPlan.isNotEmpty()) {
                goalsAndPlansAttention.add(
                    GoalPlanAttentionItem(
                        entityId = plan.id,
                        entityType = "PLAN",
                        title = plan.title,
                        reason = AttentionReason.OVERDUE_TASKS_PRESENT,
                        description = "Plan '${plan.title}' has ${overdueForPlan.size} overdue task(s) requiring rescheduling or review.",
                        overdueTasks = overdueForPlan.size
                    )
                )
            } else if (now - plan.updatedAt > 7L * 24 * 60 * 60 * 1000L) {
                // Stalled plan: active with no updates in > 7 days
                goalsAndPlansAttention.add(
                    GoalPlanAttentionItem(
                        entityId = plan.id,
                        entityType = "PLAN",
                        title = plan.title,
                        reason = AttentionReason.STALLED_PLAN,
                        description = "No recent progress recorded for plan '${plan.title}' in the last 7 days."
                    )
                )
            }
        }

        // -------------------------------------------------------------
        // Discovered Patterns (with strict authority tag)
        // -------------------------------------------------------------
        val patternItems = patterns.map { pattern ->
            val isConfirmed = pattern.confirmationState == MemoryConfirmationState.USER_CONFIRMED
            val authority = if (isConfirmed) EpistemicAuthority.USER_CONFIRMED_FACT else EpistemicAuthority.DERIVED_CALCULATION

            DiscoveredPatternItem(
                patternId = pattern.id,
                patternType = pattern.type.name,
                description = pattern.description,
                confidence = pattern.confidence,
                occurrences = pattern.evidenceCount,
                epistemicAuthority = authority,
                freshness = SourceFreshness.CURRENT,
                isUserConfirmed = isConfirmed
            )
        }

        // -------------------------------------------------------------
        // Evidence-Backed Barriers (Suppresses obstacles lacking evidence)
        // -------------------------------------------------------------
        val evidenceBackedBarriers = barrierHypotheses.filter { hypothesis ->
            hypothesis.status == com.sanket_satpute_20.ironmind.domain.model.barrier.BarrierStatus.ACTIVE &&
                hypothesis.confirmationState != BarrierConfirmationState.REJECTED &&
                hypothesis.confirmationState != BarrierConfirmationState.DISMISSED &&
                (hypothesis.evidenceCount >= 2 || hypothesis.confirmationState == BarrierConfirmationState.CONFIRMED)
        }.map { hypothesis ->
            val isConfirmed = hypothesis.confirmationState == BarrierConfirmationState.CONFIRMED
            val authority = if (isConfirmed) EpistemicAuthority.USER_CONFIRMED_FACT else EpistemicAuthority.AI_HYPOTHESIS

            EvidenceBackedBarrierItem(
                barrierId = hypothesis.id,
                category = hypothesis.category,
                description = hypothesis.description,
                confidence = hypothesis.confidence,
                supportingEvidenceCount = hypothesis.evidenceCount,
                isUserConfirmed = isConfirmed,
                epistemicAuthority = authority
            )
        }

        // -------------------------------------------------------------
        // Relevant Insights (Exclude dismissed insights)
        // -------------------------------------------------------------
        val relevantInsights = insights
            .filter { it.reviewStatus != InsightReviewStatus.DISMISSED }
            .sortedWith(
                compareBy<PersonalInsight> { authorityRank(it.epistemicAuthority) }
                    .thenByDescending { it.confidence }
            )

        // -------------------------------------------------------------
        // Proposed Next Actions
        // -------------------------------------------------------------
        val newlyPlannedActions = actionPlanningEngine.planActions(
            userId = userId,
            overdueTasks = overdueTasks,
            dueTodayTasks = dueTodayTasks,
            activeCommitments = activeCommitments,
            goalsAndPlansNeedingAttention = goalsAndPlansAttention,
            recommendations = recommendations,
            insights = relevantInsights,
            existingActions = existingActions
        )

        // Retain existing active actions (ACCEPTED, PROPOSED, EXECUTING, EXECUTION_FAILED, or EXECUTED awaiting outcome)
        val activeExisting = existingActions.filter {
            it.reviewStatus == ActionReviewStatus.PROPOSED ||
            it.reviewStatus == ActionReviewStatus.ACCEPTED ||
            it.reviewStatus == ActionReviewStatus.EXECUTING ||
            it.reviewStatus == ActionReviewStatus.EXECUTION_FAILED ||
            (it.reviewStatus == ActionReviewStatus.EXECUTED && it.outcomeId == null)
        }
        val allProposedActions = (activeExisting + newlyPlannedActions).distinctBy { it.id }

        // -------------------------------------------------------------
        // Attention Summary (Answer 1: What requires my attention today?)
        // -------------------------------------------------------------
        val summaryParts = mutableListOf<String>()

        if (overdueTasks.isNotEmpty()) {
            summaryParts.add("${overdueTasks.size} overdue task(s)")
        }
        if (dueTodayTasks.isNotEmpty()) {
            summaryParts.add("${dueTodayTasks.size} task(s) due today")
        }
        if (activeCommitments.isNotEmpty()) {
            summaryParts.add("${activeCommitments.size} active commitment(s)")
        }
        if (goalsAndPlansAttention.isNotEmpty()) {
            summaryParts.add("${goalsAndPlansAttention.size} plan(s) need attention")
        }

        val attentionSummary = if (summaryParts.isNotEmpty()) {
            "Today's Focus: ${summaryParts.joinToString(", ")}."
        } else {
            "Schedule is clear: No overdue tasks or pressing commitments for today."
        }

        return DailyBriefing(
            userId = userId,
            generatedAt = now,
            attentionSummary = attentionSummary,
            overdueTasks = overdueTasks,
            dueTodayTasks = dueTodayTasks,
            activeCommitments = activeCommitments,
            goalsAndPlansNeedingAttention = goalsAndPlansAttention,
            discoveredPatterns = patternItems,
            evidenceBackedBarriers = evidenceBackedBarriers,
            proposedNextActions = allProposedActions,
            relevantInsights = relevantInsights,
            dataAvailabilityNote = dataAvailabilityNote
        )
    }

    private fun authorityRank(authority: EpistemicAuthority): Int = when (authority) {
        EpistemicAuthority.USER_CONFIRMED_FACT -> 0
        EpistemicAuthority.SYSTEM_RECORDED_OBSERVATION -> 1
        EpistemicAuthority.DERIVED_CALCULATION -> 2
        EpistemicAuthority.AI_HYPOTHESIS -> 3
    }
}
