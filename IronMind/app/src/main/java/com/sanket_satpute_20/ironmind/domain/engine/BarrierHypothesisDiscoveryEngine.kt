package com.sanket_satpute_20.ironmind.domain.engine

import com.sanket_satpute_20.ironmind.domain.ai.BarrierCategory
import com.sanket_satpute_20.ironmind.domain.common.IdGenerator
import com.sanket_satpute_20.ironmind.domain.model.barrier.BarrierConfirmationState
import com.sanket_satpute_20.ironmind.domain.model.barrier.BarrierHypothesis
import com.sanket_satpute_20.ironmind.domain.model.barrier.BarrierStatus
import com.sanket_satpute_20.ironmind.domain.model.context.EpistemicAuthority
import com.sanket_satpute_20.ironmind.domain.model.context.SourceAvailability
import com.sanket_satpute_20.ironmind.domain.model.context.SourceFreshness
import com.sanket_satpute_20.ironmind.domain.model.context.UsagePermissionStatus
import com.sanket_satpute_20.ironmind.domain.model.context.UnifiedContext
import com.sanket_satpute_20.ironmind.domain.model.pattern.EvidenceReference
import com.sanket_satpute_20.ironmind.domain.model.pattern.Pattern
import com.sanket_satpute_20.ironmind.domain.model.pattern.PatternCategory
import com.sanket_satpute_20.ironmind.domain.model.pattern.PatternDiscoveryType
import com.sanket_satpute_20.ironmind.domain.model.pattern.category
import com.sanket_satpute_20.ironmind.domain.model.pattern.lifecycleState
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

data class InsufficientEvidenceBarrier(
    val category: BarrierCategory,
    val candidateReason: String,
    val failureReason: EvidenceSufficiencyReason
)

data class BarrierDiscoveryResult(
    val discoveredHypotheses: List<BarrierHypothesis>,
    val insufficientEvidenceHypotheses: List<InsufficientEvidenceBarrier>,
    val diagnosticNotes: List<String>
)

/**
 * Phase 3.3: Pure deterministic engine for discovering evidence-grounded Barrier Hypotheses.
 *
 * Enforces strict epistemic safety:
 * - A pattern is evidence, not automatically a barrier.
 * - Single isolated events (N=1) or candidate patterns NEVER generate a barrier hypothesis.
 * - Requires N >= 3 unique evidence items across >= 2 distinct calendar days via [EvidenceSufficiencyEvaluator].
 * - Cross-domain correlations remain non-causal (AI_HYPOTHESIS, confidence <= 0.75f).
 * - Missing PACKAGE_USAGE_STATS permission strictly emits ZERO digital/distraction hypotheses.
 * - Prevents duplicate hypothesis generation against existing user review states (CONFIRMED, REJECTED, DISMISSED).
 */
class BarrierHypothesisDiscoveryEngine(
    private val evidenceSufficiencyEvaluator: EvidenceSufficiencyEvaluator,
    private val idGenerator: IdGenerator
) {

    companion object {
        const val CROSS_DOMAIN_CONFIDENCE_CAP = 0.75f
        const val STALE_CONFIDENCE_DISCOUNT = 0.20f
    }

    fun discoverBarrierHypotheses(
        userId: String,
        unifiedContext: UnifiedContext,
        patterns: List<Pattern>,
        existingBarriers: List<BarrierHypothesis>,
        zoneId: ZoneId,
        evaluationTimeMs: Long
    ): BarrierDiscoveryResult {
        require(userId.isNotBlank()) { "userId must not be blank" }

        val discovered = mutableListOf<BarrierHypothesis>()
        val insufficient = mutableListOf<InsufficientEvidenceBarrier>()
        val diagnosticNotes = mutableListOf<String>()

        // 1. Existing barriers indexing for anti-duplication and review history preservation
        val userExistingBarriers = existingBarriers.filter { it.userId == userId }
        val confirmedCategories = userExistingBarriers
            .filter { it.confirmationState == BarrierConfirmationState.CONFIRMED }
            .map { it.category }
            .toSet()
        val rejectedOrDismissedCategories = userExistingBarriers
            .filter { it.confirmationState in setOf(BarrierConfirmationState.REJECTED, BarrierConfirmationState.DISMISSED) }
            .map { it.category }
            .toSet()
        val activeUnconfirmedCategories = userExistingBarriers
            .filter { it.confirmationState == BarrierConfirmationState.UNCONFIRMED && it.status == BarrierStatus.ACTIVE }
            .map { it.category }
            .toSet()

        // 2. Filter candidate patterns scoped to this user
        val userPatterns = patterns.filter { it.userId == userId }

        // Process Category A: EXCESSIVE_TASK_SIZE (from repeatedly postponed tasks with complexity/scope evidence)
        processPostponedTasksBarrier(
            userId = userId,
            unifiedContext = unifiedContext,
            patterns = userPatterns,
            zoneId = zoneId,
            evaluationTimeMs = evaluationTimeMs,
            confirmedCategories = confirmedCategories,
            rejectedOrDismissedCategories = rejectedOrDismissedCategories,
            activeUnconfirmedCategories = activeUnconfirmedCategories,
            discovered = discovered,
            insufficient = insufficient,
            notes = diagnosticNotes
        )

        // Process Category B: COMPETING_PRIORITIES (from repeatedly missed commitments with conflict evidence)
        processMissedCommitmentsBarrier(
            userId = userId,
            unifiedContext = unifiedContext,
            patterns = userPatterns,
            zoneId = zoneId,
            evaluationTimeMs = evaluationTimeMs,
            confirmedCategories = confirmedCategories,
            rejectedOrDismissedCategories = rejectedOrDismissedCategories,
            activeUnconfirmedCategories = activeUnconfirmedCategories,
            discovered = discovered,
            insufficient = insufficient,
            notes = diagnosticNotes
        )

        // Process Category C: SCHEDULING_MISMATCH (from repeatedly overdue tasks with scheduling-to-execution link)
        processOverdueTasksBarrier(
            userId = userId,
            unifiedContext = unifiedContext,
            patterns = userPatterns,
            zoneId = zoneId,
            evaluationTimeMs = evaluationTimeMs,
            confirmedCategories = confirmedCategories,
            rejectedOrDismissedCategories = rejectedOrDismissedCategories,
            activeUnconfirmedCategories = activeUnconfirmedCategories,
            discovered = discovered,
            insufficient = insufficient,
            notes = diagnosticNotes
        )

        // Process Category D: DISTRACTION (from app usage coinciding with pending work, permission gated)
        processDistractionBarrier(
            userId = userId,
            unifiedContext = unifiedContext,
            patterns = userPatterns,
            zoneId = zoneId,
            evaluationTimeMs = evaluationTimeMs,
            confirmedCategories = confirmedCategories,
            rejectedOrDismissedCategories = rejectedOrDismissedCategories,
            activeUnconfirmedCategories = activeUnconfirmedCategories,
            discovered = discovered,
            insufficient = insufficient,
            notes = diagnosticNotes
        )

        // Process Category E: ENVIRONMENTAL_FRICTION (from repeated focus interruptions, not generic abandonment)
        processFocusInterruptionsBarrier(
            userId = userId,
            unifiedContext = unifiedContext,
            patterns = userPatterns,
            zoneId = zoneId,
            evaluationTimeMs = evaluationTimeMs,
            confirmedCategories = confirmedCategories,
            rejectedOrDismissedCategories = rejectedOrDismissedCategories,
            activeUnconfirmedCategories = activeUnconfirmedCategories,
            discovered = discovered,
            insufficient = insufficient,
            notes = diagnosticNotes
        )

        // Process Category F: LOW_ENERGY (from time-of-day completion drops with fatigue/energy evidence)
        processLowEnergyBarrier(
            userId = userId,
            unifiedContext = unifiedContext,
            patterns = userPatterns,
            zoneId = zoneId,
            evaluationTimeMs = evaluationTimeMs,
            confirmedCategories = confirmedCategories,
            rejectedOrDismissedCategories = rejectedOrDismissedCategories,
            activeUnconfirmedCategories = activeUnconfirmedCategories,
            discovered = discovered,
            insufficient = insufficient,
            notes = diagnosticNotes
        )

        return BarrierDiscoveryResult(
            discoveredHypotheses = discovered,
            insufficientEvidenceHypotheses = insufficient,
            diagnosticNotes = diagnosticNotes
        )
    }

    private fun processPostponedTasksBarrier(
        userId: String,
        unifiedContext: UnifiedContext,
        patterns: List<Pattern>,
        zoneId: ZoneId,
        evaluationTimeMs: Long,
        confirmedCategories: Set<BarrierCategory>,
        rejectedOrDismissedCategories: Set<BarrierCategory>,
        activeUnconfirmedCategories: Set<BarrierCategory>,
        discovered: MutableList<BarrierHypothesis>,
        insufficient: MutableList<InsufficientEvidenceBarrier>,
        notes: MutableList<String>
    ) {
        val category = BarrierCategory.EXCESSIVE_TASK_SIZE
        if (category in confirmedCategories || category in rejectedOrDismissedCategories || category in activeUnconfirmedCategories) {
            notes.add("Barrier category $category suppressed by review history.")
            return
        }

        val pattern = patterns.firstOrNull {
            it.category != PatternCategory.CROSS_DOMAIN &&
            !it.conditions.orEmpty().contains("category=CROSS_DOMAIN") &&
            (it.description.contains("postponed", ignoreCase = true) ||
             it.conditions?.contains("REPEATEDLY_POSTPONED_TASKS") == true)
        } ?: return

        // Concern B hardening: Task postponement alone must NOT establish EXCESSIVE_TASK_SIZE.
        // Requires evidence of task scale, complexity, or scope hesitation.
        val patternHasSizeEvidence = pattern.description.contains("large", ignoreCase = true) ||
            pattern.description.contains("complex", ignoreCase = true) ||
            pattern.description.contains("multi-step", ignoreCase = true) ||
            pattern.description.contains("scope", ignoreCase = true) ||
            pattern.description.contains("size", ignoreCase = true) ||
            pattern.description.contains("breakdown", ignoreCase = true) ||
            pattern.conditions?.contains("COMPLEXITY") == true ||
            pattern.conditions?.contains("SIZE") == true

        val contextHasSizeEvidence = unifiedContext.productivity.taskSummary.postponedTasks.any { task ->
            task.title.contains("large", ignoreCase = true) ||
            task.title.contains("complex", ignoreCase = true) ||
            task.title.contains("multi-step", ignoreCase = true) ||
            task.description?.contains("large", ignoreCase = true) == true ||
            task.description?.contains("complex", ignoreCase = true) == true ||
            (task.estimatedDurationMinutes ?: 0) >= 60
        }

        val reflectionHasSizeEvidence = unifiedContext.productivity.recentReflections.any { r ->
            r.content.contains("too big", ignoreCase = true) ||
            r.content.contains("overwhelmed", ignoreCase = true) ||
            r.content.contains("task size", ignoreCase = true) ||
            r.content.contains("break down", ignoreCase = true)
        }

        if (!patternHasSizeEvidence && !contextHasSizeEvidence && !reflectionHasSizeEvidence) {
            insufficient.add(
                InsufficientEvidenceBarrier(
                    category = category,
                    candidateReason = "Task postponement alone without scope, subtask, or complexity evidence does not establish excessive task size",
                    failureReason = EvidenceSufficiencyReason.INSUFFICIENT_TASK_COMPLEXITY_EVIDENCE
                )
            )
            notes.add("Postponed task pattern detected, but lacked task complexity/scope evidence for EXCESSIVE_TASK_SIZE.")
            return
        }

        evaluateAndAddBarrier(
            userId = userId,
            category = category,
            title = "Task Initiation & Scope",
            description = "Tasks with larger scope or complexity have been repeatedly postponed across several days. Breaking tasks into smaller, clearly actionable increments may help reduce initiation friction.",
            pattern = pattern,
            zoneId = zoneId,
            evaluationTimeMs = evaluationTimeMs,
            isCrossDomain = false,
            discovered = discovered,
            insufficient = insufficient
        )
    }

    private fun processMissedCommitmentsBarrier(
        userId: String,
        unifiedContext: UnifiedContext,
        patterns: List<Pattern>,
        zoneId: ZoneId,
        evaluationTimeMs: Long,
        confirmedCategories: Set<BarrierCategory>,
        rejectedOrDismissedCategories: Set<BarrierCategory>,
        activeUnconfirmedCategories: Set<BarrierCategory>,
        discovered: MutableList<BarrierHypothesis>,
        insufficient: MutableList<InsufficientEvidenceBarrier>,
        notes: MutableList<String>
    ) {
        val category = BarrierCategory.COMPETING_PRIORITIES
        if (category in confirmedCategories || category in rejectedOrDismissedCategories || category in activeUnconfirmedCategories) {
            notes.add("Barrier category $category suppressed by review history.")
            return
        }

        val pattern = patterns.firstOrNull {
            it.category != PatternCategory.CROSS_DOMAIN &&
            !it.conditions.orEmpty().contains("category=CROSS_DOMAIN") &&
            (it.description.contains("missed commitments", ignoreCase = true) ||
             it.description.contains("competing", ignoreCase = true) ||
             it.conditions?.contains("REPEATEDLY_MISSED_COMMITMENTS") == true)
        } ?: return

        // Concern B hardening: Multiple commitments alone must NOT establish COMPETING_PRIORITIES without actual conflict evidence.
        val patternHasConflictEvidence = pattern.description.contains("competing", ignoreCase = true) ||
            pattern.description.contains("conflict", ignoreCase = true) ||
            pattern.description.contains("overlap", ignoreCase = true) ||
            pattern.description.contains("concurrent", ignoreCase = true) ||
            pattern.description.contains("multiple commitments", ignoreCase = true) ||
            pattern.description.contains("contention", ignoreCase = true) ||
            pattern.conditions?.contains("CONFLICT") == true ||
            pattern.conditions?.contains("COMPETING") == true

        val contextHasGoalContention = run {
            val activeCommitments = unifiedContext.productivity.commitmentSummary.activeCommitments
            val missedCommitments = unifiedContext.productivity.commitmentSummary.missedCommitments
            val goalsRepresented = activeCommitments.mapNotNull { it.goalId }.distinct()
            goalsRepresented.size >= 2 && missedCommitments.isNotEmpty()
        }

        val reflectionHasConflictEvidence = unifiedContext.productivity.recentReflections.any { r ->
            r.content.contains("competing", ignoreCase = true) ||
            r.content.contains("too many priorities", ignoreCase = true) ||
            r.content.contains("conflict", ignoreCase = true)
        }

        if (!patternHasConflictEvidence && !contextHasGoalContention && !reflectionHasConflictEvidence) {
            insufficient.add(
                InsufficientEvidenceBarrier(
                    category = category,
                    candidateReason = "Multiple commitments or missed commitments without actual schedule conflict or concurrency evidence do not establish competing priorities",
                    failureReason = EvidenceSufficiencyReason.INSUFFICIENT_CONCURRENCY_CONFLICT_EVIDENCE
                )
            )
            notes.add("Missed commitments detected, but lacked schedule conflict or goal contention evidence for COMPETING_PRIORITIES.")
            return
        }

        evaluateAndAddBarrier(
            userId = userId,
            category = category,
            title = "Competing Priorities",
            description = "Multiple active commitments across different priorities coincided with missed deadlines on the same days. Active commitments may be competing for the same execution windows.",
            pattern = pattern,
            zoneId = zoneId,
            evaluationTimeMs = evaluationTimeMs,
            isCrossDomain = false,
            discovered = discovered,
            insufficient = insufficient
        )
    }

    private fun processOverdueTasksBarrier(
        userId: String,
        unifiedContext: UnifiedContext,
        patterns: List<Pattern>,
        zoneId: ZoneId,
        evaluationTimeMs: Long,
        confirmedCategories: Set<BarrierCategory>,
        rejectedOrDismissedCategories: Set<BarrierCategory>,
        activeUnconfirmedCategories: Set<BarrierCategory>,
        discovered: MutableList<BarrierHypothesis>,
        insufficient: MutableList<InsufficientEvidenceBarrier>,
        notes: MutableList<String>
    ) {
        val category = BarrierCategory.SCHEDULING_MISMATCH
        if (category in confirmedCategories || category in rejectedOrDismissedCategories || category in activeUnconfirmedCategories) {
            notes.add("Barrier category $category suppressed by review history.")
            return
        }

        val pattern = patterns.firstOrNull {
            it.category != PatternCategory.CROSS_DOMAIN &&
            !it.conditions.orEmpty().contains("category=CROSS_DOMAIN") &&
            (it.description.contains("overdue", ignoreCase = true) ||
             it.conditions?.contains("REPEATEDLY_OVERDUE_TASKS") == true)
        } ?: return

        // Concern B hardening: Scheduling mismatch requires evidence connecting task scheduling and observed execution behaviour.
        val patternHasSchedulingLink = pattern.description.contains("morning", ignoreCase = true) ||
            pattern.description.contains("evening", ignoreCase = true) ||
            pattern.description.contains("afternoon", ignoreCase = true) ||
            pattern.description.contains("night", ignoreCase = true) ||
            pattern.description.contains("time window", ignoreCase = true) ||
            pattern.description.contains("scheduled during", ignoreCase = true) ||
            pattern.description.contains("mismatch", ignoreCase = true) ||
            pattern.description.contains("deadline alignment", ignoreCase = true) ||
            pattern.description.contains("peak", ignoreCase = true) ||
            pattern.type == com.sanket_satpute_20.ironmind.domain.model.pattern.PatternType.TIME_PATTERN

        val reflectionHasSchedulingLink = unifiedContext.productivity.recentReflections.any { r ->
            r.content.contains("wrong time", ignoreCase = true) ||
            r.content.contains("timing", ignoreCase = true) ||
            r.content.contains("schedule", ignoreCase = true) ||
            r.content.contains("rescheduled", ignoreCase = true)
        }

        if (!patternHasSchedulingLink && !reflectionHasSchedulingLink) {
            insufficient.add(
                InsufficientEvidenceBarrier(
                    category = category,
                    candidateReason = "Overdue tasks without evidence connecting task scheduling to observed execution behaviour do not establish scheduling mismatch",
                    failureReason = EvidenceSufficiencyReason.INSUFFICIENT_SCHEDULING_ALIGNMENT_EVIDENCE
                )
            )
            notes.add("Overdue task pattern detected, but lacked schedule-to-execution alignment evidence for SCHEDULING_MISMATCH.")
            return
        }

        evaluateAndAddBarrier(
            userId = userId,
            category = category,
            title = "Scheduling & Deadline Alignment",
            description = "Tasks are consistently reaching their due dates before completion. Scheduled deadlines may not align with available peak productivity windows.",
            pattern = pattern,
            zoneId = zoneId,
            evaluationTimeMs = evaluationTimeMs,
            isCrossDomain = false,
            discovered = discovered,
            insufficient = insufficient
        )
    }

    private fun processDistractionBarrier(
        userId: String,
        unifiedContext: UnifiedContext,
        patterns: List<Pattern>,
        zoneId: ZoneId,
        evaluationTimeMs: Long,
        confirmedCategories: Set<BarrierCategory>,
        rejectedOrDismissedCategories: Set<BarrierCategory>,
        activeUnconfirmedCategories: Set<BarrierCategory>,
        discovered: MutableList<BarrierHypothesis>,
        insufficient: MutableList<InsufficientEvidenceBarrier>,
        notes: MutableList<String>
    ) {
        // Strict Permission Gate: Never infer distraction if permission is denied or absent
        if (unifiedContext.digitalBehaviour.availability == SourceAvailability.PERMISSION_DENIED ||
            unifiedContext.digitalBehaviour.permissionStatus != UsagePermissionStatus.GRANTED
        ) {
            notes.add("Usage Access permission denied or absent; distraction hypothesis suppressed.")
            insufficient.add(
                InsufficientEvidenceBarrier(
                    category = BarrierCategory.DISTRACTION,
                    candidateReason = "Usage Access permission denied or absent; distraction hypothesis suppressed.",
                    failureReason = EvidenceSufficiencyReason.PERMISSION_OR_DIGITAL_DATA_MISSING
                )
            )
            return
        }

        val category = BarrierCategory.DISTRACTION
        if (category in confirmedCategories || category in rejectedOrDismissedCategories || category in activeUnconfirmedCategories) {
            notes.add("Barrier category $category suppressed by review history.")
            return
        }

        val pattern = patterns.firstOrNull {
            it.category == PatternCategory.CROSS_DOMAIN ||
            it.conditions?.contains("category=CROSS_DOMAIN") == true ||
            it.description.contains("App usage", ignoreCase = true)
        } ?: return

        // Must have valid digital usage evidence
        val hasDigitalEvidence = unifiedContext.digitalBehaviour.appUsages.isNotEmpty() ||
            pattern.evidenceReferences.orEmpty().isNotEmpty()

        if (!hasDigitalEvidence) {
            insufficient.add(
                InsufficientEvidenceBarrier(
                    category = category,
                    candidateReason = "Distraction requires valid digital app usage evidence",
                    failureReason = EvidenceSufficiencyReason.PERMISSION_OR_DIGITAL_DATA_MISSING
                )
            )
            return
        }

        val isStale = unifiedContext.digitalBehaviour.availability == SourceAvailability.STALE_DATA ||
                unifiedContext.provenanceMap["AppUsage"]?.isStale == true ||
                !unifiedContext.isFresh

        evaluateAndAddBarrier(
            userId = userId,
            category = category,
            title = "Digital Attention & Environmental Pull",
            description = "Phone usage was observed during planned focus or work intervals. Note: Observed correlation does not imply causation.",
            pattern = pattern,
            zoneId = zoneId,
            evaluationTimeMs = evaluationTimeMs,
            isCrossDomain = true,
            isSourceStale = isStale,
            discovered = discovered,
            insufficient = insufficient
        )
    }

    private fun processFocusInterruptionsBarrier(
        userId: String,
        unifiedContext: UnifiedContext,
        patterns: List<Pattern>,
        zoneId: ZoneId,
        evaluationTimeMs: Long,
        confirmedCategories: Set<BarrierCategory>,
        rejectedOrDismissedCategories: Set<BarrierCategory>,
        activeUnconfirmedCategories: Set<BarrierCategory>,
        discovered: MutableList<BarrierHypothesis>,
        insufficient: MutableList<InsufficientEvidenceBarrier>,
        notes: MutableList<String>
    ) {
        val category = BarrierCategory.ENVIRONMENTAL_FRICTION
        if (category in confirmedCategories || category in rejectedOrDismissedCategories || category in activeUnconfirmedCategories) {
            notes.add("Barrier category $category suppressed by review history.")
            return
        }

        // Concern B hardening: Repeated task abandonment alone must NOT establish ENVIRONMENTAL_FRICTION.
        // Requires evidence of environmental disruption or interrupted focus sessions.
        val pattern = patterns.firstOrNull {
            (it.description.contains("focus session", ignoreCase = true) &&
             it.description.contains("interrupted", ignoreCase = true)) ||
            it.description.contains("environmental", ignoreCase = true) ||
            it.description.contains("external interruption", ignoreCase = true) ||
            it.description.contains("workspace disruption", ignoreCase = true)
        }

        if (pattern == null) {
            val abandonmentPattern = patterns.firstOrNull {
                it.description.contains("abandoned", ignoreCase = true) ||
                it.description.contains("cancelled", ignoreCase = true)
            }
            if (abandonmentPattern != null) {
                insufficient.add(
                    InsufficientEvidenceBarrier(
                        category = category,
                        candidateReason = "Repeated task abandonment alone without focus session interruption or environmental disruption evidence does not establish environmental friction",
                        failureReason = EvidenceSufficiencyReason.INSUFFICIENT_ENVIRONMENTAL_DISRUPTION_EVIDENCE
                    )
                )
                notes.add("Task abandonment pattern detected, but lacked environmental or focus interruption evidence for ENVIRONMENTAL_FRICTION.")
            }
            return
        }

        evaluateAndAddBarrier(
            userId = userId,
            category = category,
            title = "Environmental & Focus Disruption",
            description = "Focus sessions were frequently interrupted or cancelled before completion. External notifications or workspace shifts may be disrupting concentration.",
            pattern = pattern,
            zoneId = zoneId,
            evaluationTimeMs = evaluationTimeMs,
            isCrossDomain = false,
            discovered = discovered,
            insufficient = insufficient
        )
    }

    private fun processLowEnergyBarrier(
        userId: String,
        unifiedContext: UnifiedContext,
        patterns: List<Pattern>,
        zoneId: ZoneId,
        evaluationTimeMs: Long,
        confirmedCategories: Set<BarrierCategory>,
        rejectedOrDismissedCategories: Set<BarrierCategory>,
        activeUnconfirmedCategories: Set<BarrierCategory>,
        discovered: MutableList<BarrierHypothesis>,
        insufficient: MutableList<InsufficientEvidenceBarrier>,
        notes: MutableList<String>
    ) {
        val category = BarrierCategory.LOW_ENERGY
        if (category in confirmedCategories || category in rejectedOrDismissedCategories || category in activeUnconfirmedCategories) {
            notes.add("Barrier category $category suppressed by review history.")
            return
        }

        // Concern B hardening: Late-day missed commitments alone must NOT establish LOW_ENERGY.
        // Must connect late-day timing with energy/fatigue evidence or completion drop rate.
        val pattern = patterns.firstOrNull {
            (it.description.contains("energy", ignoreCase = true) ||
             it.description.contains("fatigue", ignoreCase = true) ||
             it.description.contains("tired", ignoreCase = true) ||
             it.description.contains("completion drop", ignoreCase = true) ||
             it.description.contains("depleted", ignoreCase = true)) &&
            (it.description.contains("Night", ignoreCase = true) ||
             it.description.contains("Evening", ignoreCase = true) ||
             it.description.contains("Late-day", ignoreCase = true) ||
             it.description.contains("Task completion", ignoreCase = true))
        }

        if (pattern == null) {
            val lateDayMissedPattern = patterns.firstOrNull {
                (it.description.contains("Night", ignoreCase = true) ||
                 it.description.contains("Evening", ignoreCase = true) ||
                 it.description.contains("Late", ignoreCase = true)) &&
                it.description.contains("missed", ignoreCase = true)
            }
            if (lateDayMissedPattern != null) {
                insufficient.add(
                    InsufficientEvidenceBarrier(
                        category = category,
                        candidateReason = "Late-day missed commitments alone without energy or fatigue evidence do not establish low energy",
                        failureReason = EvidenceSufficiencyReason.INSUFFICIENT_ENERGY_EVIDENCE
                    )
                )
                notes.add("Late-day missed commitments detected, but lacked energy or fatigue evidence for LOW_ENERGY.")
            }
            return
        }

        evaluateAndAddBarrier(
            userId = userId,
            category = category,
            title = "Energy Window Mismatch",
            description = "Task completions decrease significantly during late-day windows compared to earlier peak windows. Consider scheduling demanding tasks during earlier hours.",
            pattern = pattern,
            zoneId = zoneId,
            evaluationTimeMs = evaluationTimeMs,
            isCrossDomain = false,
            discovered = discovered,
            insufficient = insufficient
        )
    }

    private fun evaluateAndAddBarrier(
        userId: String,
        category: BarrierCategory,
        title: String,
        description: String,
        pattern: Pattern,
        zoneId: ZoneId,
        evaluationTimeMs: Long,
        isCrossDomain: Boolean,
        isSourceStale: Boolean = false,
        discovered: MutableList<BarrierHypothesis>,
        insufficient: MutableList<InsufficientEvidenceBarrier>
    ) {
        val evidenceRefs = pattern.evidenceReferences.orEmpty()
        val distinctEvidenceCount = evidenceRefs.distinct().size

        // Build validated evidence timestamps
        val validatedItems = if (evidenceRefs.isNotEmpty()) {
            val spanMs = (pattern.lastObservedAt - pattern.firstObservedAt).coerceAtLeast(1L)
            evidenceRefs.distinct().mapIndexed { index, ref ->
                val ts = pattern.firstObservedAt + (index * (spanMs / evidenceRefs.size.coerceAtLeast(1)))
                ValidatedEvidence(reference = ref, timestampMs = ts)
            }
        } else {
            emptyList()
        }

        val sufficiencyResult = evidenceSufficiencyEvaluator.evaluate(validatedItems, zoneId)
        if (sufficiencyResult !is EvidenceSufficiencyResult.Sufficient) {
            val reason = when (sufficiencyResult) {
                is EvidenceSufficiencyResult.Insufficient -> sufficiencyResult.reason
                else -> EvidenceSufficiencyReason.INSUFFICIENT_UNIQUE_EVIDENCE
            }
            insufficient.add(
                InsufficientEvidenceBarrier(
                    category = category,
                    candidateReason = "Pattern '${pattern.description}' has insufficient evidence (${reason})",
                    failureReason = reason
                )
            )
            return
        }

        // Calculate distinct calendar days
        val distinctDays = validatedItems.map {
            ZonedDateTime.ofInstant(Instant.ofEpochMilli(it.timestampMs), zoneId)
                .truncatedTo(ChronoUnit.DAYS)
        }.distinct().count()

        var calculatedConfidence = pattern.confidence.coerceIn(0.1f, 1.0f)
        if (isCrossDomain) {
            calculatedConfidence = minOf(calculatedConfidence, CROSS_DOMAIN_CONFIDENCE_CAP)
        }
        if (isSourceStale) {
            calculatedConfidence = (calculatedConfidence - STALE_CONFIDENCE_DISCOUNT).coerceAtLeast(0.1f)
        }

        val hypothesis = BarrierHypothesis(
            id = idGenerator.generateId(),
            userId = userId,
            category = category,
            description = description,
            confirmationState = BarrierConfirmationState.UNCONFIRMED,
            status = BarrierStatus.ACTIVE,
            sourceReflectionId = "",
            firstObservedAt = pattern.firstObservedAt,
            lastObservedAt = pattern.lastObservedAt,
            title = title,
            supportingPatternIds = listOf(pattern.id),
            evidenceReferences = evidenceRefs,
            evidenceCount = distinctEvidenceCount,
            distinctDays = distinctDays,
            confidence = calculatedConfidence,
            epistemicAuthority = EpistemicAuthority.AI_HYPOTHESIS,
            sourceProvenance = listOf("Pattern:${pattern.id}"),
            sourceFreshness = if (isSourceStale) SourceFreshness.STALE else SourceFreshness.CURRENT
        )

        discovered.add(hypothesis)
    }
}
