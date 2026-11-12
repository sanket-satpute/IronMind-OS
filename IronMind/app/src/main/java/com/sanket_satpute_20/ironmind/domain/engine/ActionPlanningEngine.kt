package com.sanket_satpute_20.ironmind.domain.engine

import com.sanket_satpute_20.ironmind.domain.ai.InterventionType
import com.sanket_satpute_20.ironmind.domain.common.Clock
import com.sanket_satpute_20.ironmind.domain.common.IdGenerator
import com.sanket_satpute_20.ironmind.domain.model.Commitment
import com.sanket_satpute_20.ironmind.domain.model.DueTaskItem
import com.sanket_satpute_20.ironmind.domain.model.action.ActionCategory
import com.sanket_satpute_20.ironmind.domain.model.action.ActionPriority
import com.sanket_satpute_20.ironmind.domain.model.action.ActionReviewStatus
import com.sanket_satpute_20.ironmind.domain.model.action.ActionUrgency
import com.sanket_satpute_20.ironmind.domain.model.action.ProposedAction
import com.sanket_satpute_20.ironmind.domain.model.briefing.AttentionReason
import com.sanket_satpute_20.ironmind.domain.model.briefing.GoalPlanAttentionItem
import com.sanket_satpute_20.ironmind.domain.model.context.EpistemicAuthority
import com.sanket_satpute_20.ironmind.domain.model.context.SourceFreshness
import com.sanket_satpute_20.ironmind.domain.model.insight.InsightCategory
import com.sanket_satpute_20.ironmind.domain.model.insight.InsightReviewStatus
import com.sanket_satpute_20.ironmind.domain.model.insight.PersonalInsight
import com.sanket_satpute_20.ironmind.domain.model.intervention.InterventionRecommendation
import com.sanket_satpute_20.ironmind.domain.model.intervention.InterventionRecommendationStatus

/**
 * Phase 3.5: Deterministic Action Planning Engine.
 *
 * Converts eligible insights, existing intervention recommendations, due/overdue tasks,
 * and stalled goals/plans into prioritized, evidence-backed proposed next actions.
 *
 * Invariants:
 * 1. Strict evidence grounding: Does not manufacture actions without supporting evidence.
 * 2. Deduplication: Suppresses duplicate actions targeting the same entity.
 * 3. Conflict resolution: Detects and resolves conflicting recommendations conservatively.
 * 4. Stale data penalty: Downgrades priority for stale or degraded evidence.
 * 5. Respects user review state: Never re-proposes rejected or dismissed actions.
 */
class ActionPlanningEngine(
    private val clock: Clock,
    private val idGenerator: IdGenerator
) {

    fun planActions(
        userId: String,
        overdueTasks: List<DueTaskItem>,
        dueTodayTasks: List<DueTaskItem>,
        activeCommitments: List<Commitment>,
        goalsAndPlansNeedingAttention: List<GoalPlanAttentionItem>,
        recommendations: List<InterventionRecommendation>,
        insights: List<PersonalInsight>,
        existingActions: List<ProposedAction> = emptyList()
    ): List<ProposedAction> {
        val now = clock.currentTimeMillis()
        val candidateActions = mutableListOf<ProposedAction>()

        val existingActionKeys = existingActions.filter {
            it.reviewStatus == ActionReviewStatus.PROPOSED ||
                    it.reviewStatus == ActionReviewStatus.ACCEPTED ||
                    it.reviewStatus == ActionReviewStatus.REJECTED ||
                    it.reviewStatus == ActionReviewStatus.DISMISSED ||
                    it.reviewStatus == ActionReviewStatus.EXECUTING ||
                    it.reviewStatus == ActionReviewStatus.EXECUTED ||
                    it.reviewStatus == ActionReviewStatus.EXECUTION_FAILED ||
                    it.reviewStatus == ActionReviewStatus.DEFERRED
        }.flatMap { action ->
            listOfNotNull(
                action.id,
                action.targetEntityId?.let { "${action.category}_$it" },
                action.sourceInsightId?.let { "${action.category}_insight_$it" },
                action.sourceRecommendationId?.let { "act_rec_$it" }
            )
        }.toSet()

        // -------------------------------------------------------------
        // 1. Actions from Overdue Tasks (Reschedule or Decomposition)
        // -------------------------------------------------------------
        for (item in overdueTasks) {
            val task = item.task

            // Determine if large task (> 90 mins) warrants decomposition instead of pure reschedule
            val isLargeTask = (task.estimatedDurationMinutes ?: 0) >= 90
            val category = if (isLargeTask) ActionCategory.TASK_DECOMPOSITION else ActionCategory.TASK_RESCHEDULE
            val deterministicId = if (isLargeTask) "act_decomp_${task.id}" else "act_reschedule_${task.id}"
            val key = "${category}_${task.id}"

            if (deterministicId in existingActionKeys || key in existingActionKeys) continue

            val title = if (isLargeTask) "Break Down Overdue Task" else "Reschedule Overdue Task"
            val explanation = if (isLargeTask) {
                "Task '${task.title}' is overdue and estimated at ${task.estimatedDurationMinutes} minutes. Breaking it into smaller, manageable sub-tasks reduces execution friction."
            } else {
                "Task '${task.title}' was due on ${task.dueAt?.let { java.time.Instant.ofEpochMilli(it).toString().substring(0, 10) } ?: "earlier"}. Rescheduling it to a realistic time restores momentum."
            }

            val suggestedSubTasks = if (isLargeTask) {
                listOf(
                    "${task.title} - Step 1: Initial Setup",
                    "${task.title} - Step 2: Core Implementation",
                    "${task.title} - Step 3: Verification"
                )
            } else {
                emptyList()
            }

            candidateActions.add(
                ProposedAction(
                    id = deterministicId,
                    userId = userId,
                    category = category,
                    title = title,
                    explanation = explanation,
                    targetEntityId = task.id,
                    targetEntityType = "TASK",
                    priority = ActionPriority.CRITICAL,
                    urgency = ActionUrgency.IMMEDIATE,
                    supportingEvidenceIds = listOf("task_${task.id}"),
                    confidence = 0.95f,
                    epistemicAuthority = EpistemicAuthority.SYSTEM_RECORDED_OBSERVATION,
                    sourceProvenance = listOf("Room:tasks"),
                    sourceFreshness = SourceFreshness.CURRENT,
                    reviewStatus = ActionReviewStatus.PROPOSED,
                    createdAt = now,
                    suggestedSubTasks = suggestedSubTasks,
                    suggestedNewDueAt = now + (24 * 60 * 60 * 1000L) // Suggest tomorrow
                )
            )
        }

        // -------------------------------------------------------------
        // 2. Actions from Existing Pending Intervention Recommendations
        // -------------------------------------------------------------
        val pendingRecs = recommendations.filter { it.status == InterventionRecommendationStatus.PENDING }
        for (rec in pendingRecs) {
            val mappedCategory = when (rec.interventionType) {
                InterventionType.BREAK_DOWN -> ActionCategory.TASK_DECOMPOSITION
                InterventionType.RESCHEDULE -> ActionCategory.TASK_RESCHEDULE
                InterventionType.PROTECT -> ActionCategory.FOCUS_PROTECTION
                InterventionType.REFLECT -> ActionCategory.BEHAVIOUR_PATTERN_REVIEW
                InterventionType.CHALLENGE, InterventionType.ASK -> ActionCategory.GOAL_PLAN_RECONSIDERATION
                InterventionType.REDIRECT, InterventionType.RECOVER -> ActionCategory.COMMITMENT_REVIEW
                else -> ActionCategory.COMMITMENT_REVIEW
            }

            val deterministicId = "act_rec_${rec.id}"
            val key = "${mappedCategory}_${rec.targetEntityId}"

            if (deterministicId in existingActionKeys || key in existingActionKeys) continue

            val priority = when (rec.interventionType) {
                InterventionType.RESCHEDULE, InterventionType.BREAK_DOWN -> ActionPriority.HIGH
                InterventionType.PROTECT -> ActionPriority.HIGH
                InterventionType.CHALLENGE, InterventionType.RECOVER -> ActionPriority.MEDIUM
                else -> ActionPriority.LOW
            }

            val urgency = when (rec.interventionType) {
                InterventionType.RESCHEDULE, InterventionType.PROTECT -> ActionUrgency.TODAY
                InterventionType.BREAK_DOWN -> ActionUrgency.TODAY
                InterventionType.REFLECT -> ActionUrgency.THIS_WEEK
                else -> ActionUrgency.OPPORTUNISTIC
            }

            candidateActions.add(
                ProposedAction(
                    id = deterministicId,
                    userId = userId,
                    category = mappedCategory,
                    title = formatActionTitle(rec.interventionType),
                    explanation = rec.rationale,
                    sourceRecommendationId = rec.id,
                    targetEntityId = rec.targetEntityId,
                    targetEntityType = rec.targetEntityType,
                    priority = priority,
                    urgency = urgency,
                    confidence = 0.85f,
                    epistemicAuthority = rec.epistemicAuthority,
                    sourceProvenance = listOf("InterventionRecommendation:${rec.id}"),
                    sourceFreshness = SourceFreshness.CURRENT,
                    reviewStatus = ActionReviewStatus.PROPOSED,
                    createdAt = now,
                    expiresAt = rec.expiresAt
                )
            )
        }

        // -------------------------------------------------------------
        // 3. Actions from Stalled Goals / Plans
        // -------------------------------------------------------------
        for (attn in goalsAndPlansNeedingAttention) {
            val deterministicId = "act_plan_${attn.entityId}"
            val key = "${ActionCategory.GOAL_PLAN_RECONSIDERATION}_${attn.entityId}"

            if (deterministicId in existingActionKeys || key in existingActionKeys) continue

            val priority = if (attn.reason == AttentionReason.OVERDUE_TASKS_PRESENT) ActionPriority.HIGH else ActionPriority.MEDIUM
            val urgency = if (attn.reason == AttentionReason.OVERDUE_TASKS_PRESENT) ActionUrgency.TODAY else ActionUrgency.THIS_WEEK

            candidateActions.add(
                ProposedAction(
                    id = deterministicId,
                    userId = userId,
                    category = ActionCategory.GOAL_PLAN_RECONSIDERATION,
                    title = "Reconsider Stalled ${attn.entityType}: ${attn.title}",
                    explanation = attn.description,
                    targetEntityId = attn.entityId,
                    targetEntityType = attn.entityType,
                    priority = priority,
                    urgency = urgency,
                    supportingEvidenceIds = listOf("${attn.entityType}_${attn.entityId}"),
                    confidence = 0.80f,
                    epistemicAuthority = EpistemicAuthority.DERIVED_CALCULATION,
                    sourceProvenance = listOf("Room:${attn.entityType.lowercase()}s"),
                    sourceFreshness = SourceFreshness.CURRENT,
                    reviewStatus = ActionReviewStatus.PROPOSED,
                    createdAt = now
                )
            )
        }

        // -------------------------------------------------------------
        // 4. Actions from Verified Personal Insights
        // -------------------------------------------------------------
        val activeInsights = insights.filter {
            it.reviewStatus != InsightReviewStatus.DISMISSED &&
                    it.category != InsightCategory.DIGITAL_BEHAVIOUR // Digital behavior insights handled only if permission present
        }

        for (insight in activeInsights) {
            if (insight.linkedRecommendationId != null) continue // Already handled via recommendation

            val category = when (insight.category) {
                InsightCategory.FOCUS -> ActionCategory.FOCUS_PROTECTION
                InsightCategory.DIGITAL_BEHAVIOUR, InsightCategory.CROSS_DOMAIN -> ActionCategory.BEHAVIOUR_PATTERN_REVIEW
                InsightCategory.TASK_SCHEDULING -> ActionCategory.TASK_RESCHEDULE
                InsightCategory.GOAL_EXECUTION -> ActionCategory.GOAL_PLAN_RECONSIDERATION
                else -> ActionCategory.COMMITMENT_REVIEW
            }

            val deterministicId = "act_ins_${insight.id}"
            val key = "${category}_insight_${insight.id}"

            if (deterministicId in existingActionKeys || key in existingActionKeys) continue

            // Ensure insight has sufficient evidence before proposing action
            if (insight.supportingEvidenceIds.isEmpty() || insight.confidence < 0.60f) {
                continue // Suppress action when evidence is insufficient
            }

            val freshness = insight.sourceFreshness
            val priority = if (freshness == SourceFreshness.STALE) {
                ActionPriority.LOW // Downgrade priority for stale data
            } else {
                ActionPriority.MEDIUM
            }

            candidateActions.add(
                ProposedAction(
                    id = deterministicId,
                    userId = userId,
                    category = category,
                    title = "Act on Insight: ${insight.title}",
                    explanation = insight.explanation,
                    sourceInsightId = insight.id,
                    targetEntityId = null,
                    targetEntityType = null,
                    priority = priority,
                    urgency = ActionUrgency.THIS_WEEK,
                    supportingEvidenceIds = insight.supportingEvidenceIds,
                    confidence = insight.confidence,
                    epistemicAuthority = insight.epistemicAuthority,
                    sourceProvenance = insight.sourceProvenance,
                    sourceFreshness = freshness,
                    reviewStatus = ActionReviewStatus.PROPOSED,
                    createdAt = now
                )
            )
        }

        // -------------------------------------------------------------
        // 5. Conflict Resolution & Deduplication
        // -------------------------------------------------------------
        val resolvedActions = resolveConflicts(candidateActions)

        // -------------------------------------------------------------
        // 6. Deterministic Prioritization Sorting
        // -------------------------------------------------------------
        return resolvedActions.sortedWith(
            compareBy<ProposedAction> { it.priority.ordinal } // CRITICAL (0), HIGH (1), MEDIUM (2), LOW (3)
                .thenBy { it.urgency.ordinal } // IMMEDIATE (0), TODAY (1), THIS_WEEK (2), OPPORTUNISTIC (3)
                .thenBy { authorityRank(it.epistemicAuthority) } // USER_CONFIRMED_FACT (0) > SYSTEM_RECORDED_OBSERVATION (1) > DERIVED_CALCULATION (2) > AI_HYPOTHESIS (3)
                .thenByDescending { it.confidence }
        )
    }

    private fun authorityRank(authority: EpistemicAuthority): Int = when (authority) {
        EpistemicAuthority.USER_CONFIRMED_FACT -> 0
        EpistemicAuthority.SYSTEM_RECORDED_OBSERVATION -> 1
        EpistemicAuthority.DERIVED_CALCULATION -> 2
        EpistemicAuthority.AI_HYPOTHESIS -> 3
    }

    private fun formatActionTitle(type: InterventionType): String = when (type) {
        InterventionType.BREAK_DOWN -> "Break Down Complex Task"
        InterventionType.RESCHEDULE -> "Reschedule Competing Task"
        InterventionType.PROTECT -> "Protect Focus Time"
        InterventionType.REFLECT -> "Reflect on Behaviour Pattern"
        InterventionType.CHALLENGE -> "Re-evaluate Task Scope"
        InterventionType.ASK -> "Clarify Execution Priority"
        InterventionType.RECOVER -> "Recover Daily Momentum"
        InterventionType.REDIRECT -> "Redirect Focus to Priority Task"
        InterventionType.REMIND -> "Review Daily Commitment"
        else -> "Recommended Next Action"
    }

    /**
     * Detects and conservatively resolves conflicting actions targeting the same entity.
     * E.g., multiple reschedule suggestions for the same task.
     */
    private fun resolveConflicts(actions: List<ProposedAction>): List<ProposedAction> {
        val uniqueActions = mutableListOf<ProposedAction>()
        val seenEntityAction = mutableMapOf<String, ProposedAction>()

        for (action in actions) {
            val targetKey = if (action.targetEntityId != null) {
                "${action.category}_${action.targetEntityId}"
            } else {
                "${action.category}_${action.title}"
            }

            val existing = seenEntityAction[targetKey]
            if (existing == null) {
                seenEntityAction[targetKey] = action
                uniqueActions.add(action)
            } else {
                // Conflict detected: keep the one with higher priority or higher confidence
                if (action.priority.ordinal < existing.priority.ordinal ||
                    (action.priority.ordinal == existing.priority.ordinal && action.confidence > existing.confidence)) {
                    uniqueActions.remove(existing)
                    seenEntityAction[targetKey] = action
                    uniqueActions.add(action)
                }
            }
        }
        return uniqueActions
    }
}
