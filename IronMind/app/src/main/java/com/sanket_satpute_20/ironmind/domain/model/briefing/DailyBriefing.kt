package com.sanket_satpute_20.ironmind.domain.model.briefing

import com.sanket_satpute_20.ironmind.domain.model.Commitment
import com.sanket_satpute_20.ironmind.domain.model.DueTaskItem
import com.sanket_satpute_20.ironmind.domain.model.action.ProposedAction
import com.sanket_satpute_20.ironmind.domain.model.insight.PersonalInsight

/**
 * Phase 3.5: Personal Intelligence Daily Briefing.
 *
 * Provides evidence-grounded answers to the user's daily questions:
 * 1. What requires my attention today? (attentionSummary)
 * 2. Which commitments or tasks are due or overdue? (overdueTasks, dueTodayTasks, activeCommitments)
 * 3. Which goals or plans need attention? (goalsAndPlansNeedingAttention)
 * 4. What relevant behavioural patterns have been discovered? (discoveredPatterns)
 * 5. What obstacles have sufficient evidence? (evidenceBackedBarriers)
 * 6. What should I consider doing next? (proposedNextActions)
 */
data class DailyBriefing(
    val userId: String,
    val generatedAt: Long,
    val attentionSummary: String,
    val overdueTasks: List<DueTaskItem> = emptyList(),
    val dueTodayTasks: List<DueTaskItem> = emptyList(),
    val activeCommitments: List<Commitment> = emptyList(),
    val goalsAndPlansNeedingAttention: List<GoalPlanAttentionItem> = emptyList(),
    val discoveredPatterns: List<DiscoveredPatternItem> = emptyList(),
    val evidenceBackedBarriers: List<EvidenceBackedBarrierItem> = emptyList(),
    val proposedNextActions: List<ProposedAction> = emptyList(),
    val relevantInsights: List<PersonalInsight> = emptyList(),
    val dataAvailabilityNote: String? = null
)
