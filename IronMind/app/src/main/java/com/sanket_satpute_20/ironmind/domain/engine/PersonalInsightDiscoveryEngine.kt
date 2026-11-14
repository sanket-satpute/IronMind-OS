package com.sanket_satpute_20.ironmind.domain.engine

import com.sanket_satpute_20.ironmind.domain.common.Clock
import com.sanket_satpute_20.ironmind.domain.common.IdGenerator
import com.sanket_satpute_20.ironmind.domain.model.EventType
import com.sanket_satpute_20.ironmind.domain.model.Outcome
import com.sanket_satpute_20.ironmind.domain.model.ResultStatus
import com.sanket_satpute_20.ironmind.domain.model.TaskStatus
import com.sanket_satpute_20.ironmind.domain.model.barrier.BarrierHypothesis
import com.sanket_satpute_20.ironmind.domain.model.context.EpistemicAuthority
import com.sanket_satpute_20.ironmind.domain.model.context.ExecutionTimeBucket
import com.sanket_satpute_20.ironmind.domain.model.context.ExecutionTimingSummary
import com.sanket_satpute_20.ironmind.domain.model.context.SourceAvailability
import com.sanket_satpute_20.ironmind.domain.model.context.SourceFreshness
import com.sanket_satpute_20.ironmind.domain.model.context.TemporalSampleSource
import com.sanket_satpute_20.ironmind.domain.model.context.UnifiedContext
import com.sanket_satpute_20.ironmind.domain.model.context.UsagePermissionStatus
import com.sanket_satpute_20.ironmind.domain.model.insight.ExplainabilityBlock
import com.sanket_satpute_20.ironmind.domain.model.insight.InsightCategory
import com.sanket_satpute_20.ironmind.domain.model.insight.InsightReviewStatus
import com.sanket_satpute_20.ironmind.domain.model.insight.PersonalInsight
import com.sanket_satpute_20.ironmind.domain.model.intervention.InterventionRecommendation
import com.sanket_satpute_20.ironmind.domain.model.intervention.InterventionRecommendationStatus
import com.sanket_satpute_20.ironmind.domain.model.pattern.Pattern
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToLong

data class InsightDiscoveryResult(
    val discoveredInsights: List<PersonalInsight>,
    val suppressedCount: Int,
    val diagnosticNotes: List<String>
)

/**
 * Phase 3.4: Deterministic, evidence-grounded Personal Insight Discovery Engine.
 *
 * Synthesizes actionable, explainable insights across 7 categories:
 * - PRODUCTIVITY
 * - GOAL_EXECUTION
 * - TASK_SCHEDULING
 * - DIGITAL_BEHAVIOUR
 * - FOCUS
 * - CROSS_DOMAIN
 * - INTERVENTION_OUTCOME
 *
 * Safeguards:
 * 1. Respects Usage Access permission: suppresses digital insights when permission missing.
 * 2. Non-causal framing for cross-domain correlations (correlation != causation).
 * 3. Learning from intervention outcomes: recommendation acceptance is NEVER treated as success;
 *    outcomes without measurements are explicitly marked UNKNOWN; small sample sizes (<3) are flagged.
 * 4. Deduplication: respects dismissed insights and never regenerates duplicate active insights.
 * 5. Full explainability: every insight includes structured ExplainabilityBlock.
 */
class PersonalInsightDiscoveryEngine(
    private val clock: Clock,
    private val idGenerator: IdGenerator
) {

    fun discoverInsights(
        userId: String,
        unifiedContext: UnifiedContext,
        patterns: List<Pattern>,
        barrierHypotheses: List<BarrierHypothesis>,
        recommendations: List<InterventionRecommendation>,
        outcomes: List<Outcome>,
        existingInsights: List<PersonalInsight>,
        zoneId: ZoneId
    ): InsightDiscoveryResult {
        val candidates = mutableListOf<PersonalInsight>()
        val diagnosticNotes = mutableListOf<String>()
        var suppressedCount = 0

        val windowStart = unifiedContext.windowStartTimeMs
        val windowEnd = if (unifiedContext.windowEndTimeMs > 0) unifiedContext.windowEndTimeMs else clock.currentTimeMillis()
        val now = clock.currentTimeMillis()

        // -------------------------------------------------------------
        // 1. PRODUCTIVITY INSIGHTS
        // -------------------------------------------------------------
        val commitmentSummary = unifiedContext.productivity.commitmentSummary
        val totalCommitments = commitmentSummary.completedCommitments.size +
                commitmentSummary.missedCommitments.size +
                commitmentSummary.postponedCommitments.size

        if (totalCommitments >= 3) {
            val completedCount = commitmentSummary.completedCommitments.size
            val completionRate = (completedCount.toFloat() / totalCommitments.toFloat()) * 100f
            val formattedRate = String.format("%.0f", completionRate)

            val insight = PersonalInsight(
                id = idGenerator.generateId(),
                userId = userId,
                category = InsightCategory.PRODUCTIVITY,
                title = "Commitment Completion Rhythm",
                explanation = "Completed $completedCount of $totalCommitments commitments ($formattedRate%) during the observation window.",
                explainabilityBlock = ExplainabilityBlock(
                    whatWasObserved = "$completedCount completed, ${commitmentSummary.missedCommitments.size} missed, and ${commitmentSummary.postponedCommitments.size} postponed commitments recorded.",
                    observationPeriod = formatPeriod(windowStart, windowEnd, zoneId),
                    supportingEvidenceDescription = "Evidence from ${totalCommitments} commitment records.",
                    whyItMatters = "Provides an objective baseline of execution consistency over time.",
                    uncertaintyOrLimitations = "Does not account for external scheduling friction or unexpected personal commitments.",
                    suggestedNextSteps = if (completionRate >= 80f) {
                        "Current daily commitment workload matches execution capacity well."
                    } else {
                        "Consider scheduling fewer commitments per day or breaking them into smaller chunks."
                    }
                ),
                supportingEvidenceIds = commitmentSummary.completedCommitments.map { it.id },
                observationWindowStartMs = windowStart,
                observationWindowEndMs = windowEnd,
                firstObservedAt = windowStart,
                lastObservedAt = windowEnd,
                confidence = 0.85f,
                epistemicAuthority = EpistemicAuthority.DERIVED_CALCULATION,
                sourceProvenance = listOf("Room:commitment"),
                sourceFreshness = if (unifiedContext.isFresh) SourceFreshness.CURRENT else SourceFreshness.STALE,
                createdAt = now,
                updatedAt = now
            )
            candidates.add(insight)
        } else {
            diagnosticNotes.add("Productivity insight suppressed: Insufficient commitments (N=$totalCommitments, required >= 3)")
        }

        // -------------------------------------------------------------
        // 2. GOAL EXECUTION INSIGHTS
        // -------------------------------------------------------------
        val activeGoals = unifiedContext.productivity.activeGoals
        if (activeGoals.isNotEmpty()) {
            val totalGoalTasks = activeGoals.sumOf { it.progress.totalTasks }
            val completedGoalTasks = activeGoals.sumOf { it.progress.completedTasks }

            if (totalGoalTasks >= 3) {
                val goalProgressRate = (completedGoalTasks.toFloat() / totalGoalTasks.toFloat()) * 100f
                val insight = PersonalInsight(
                    id = idGenerator.generateId(),
                    userId = userId,
                    category = InsightCategory.GOAL_EXECUTION,
                    title = "Active Goal Progress Status",
                    explanation = "$completedGoalTasks of $totalGoalTasks tasks completed across ${activeGoals.size} active goal(s) (${String.format("%.0f", goalProgressRate)}%).",
                    explainabilityBlock = ExplainabilityBlock(
                        whatWasObserved = "Progress tracked across ${activeGoals.size} active goals: ${activeGoals.joinToString(", ") { it.goal.title }}.",
                        observationPeriod = formatPeriod(windowStart, windowEnd, zoneId),
                        supportingEvidenceDescription = "$completedGoalTasks completed out of $totalGoalTasks associated plan tasks.",
                        whyItMatters = "Helps identify whether active goals are moving forward or stalling.",
                        uncertaintyOrLimitations = "Task count alone does not capture relative task complexity or milestones.",
                        suggestedNextSteps = "Review pending tasks on goals with low progress to keep momentum active."
                    ),
                    supportingEvidenceIds = activeGoals.map { it.goal.id },
                    observationWindowStartMs = windowStart,
                    observationWindowEndMs = windowEnd,
                    firstObservedAt = windowStart,
                    lastObservedAt = windowEnd,
                    confidence = 0.80f,
                    epistemicAuthority = EpistemicAuthority.DERIVED_CALCULATION,
                    sourceProvenance = listOf("Room:goal", "Room:plan", "Room:task"),
                    sourceFreshness = if (unifiedContext.isFresh) SourceFreshness.CURRENT else SourceFreshness.STALE,
                    createdAt = now,
                    updatedAt = now
                )
                candidates.add(insight)
            }
        }

        // -------------------------------------------------------------
        // 3. TASK SCHEDULING INSIGHTS (WINDOW-SCOPED TEMPORAL INTELLIGENCE)
        // -------------------------------------------------------------
        val timing = resolveExecutionTiming(userId, unifiedContext, windowStart, windowEnd, zoneId)

        // 3.1: Task Completion Timing Rhythm (time-of-day distribution)
        evaluateCompletionTimingRhythmInsight(
            timing = timing,
            unifiedContext = unifiedContext,
            userId = userId,
            windowStart = windowStart,
            windowEnd = windowEnd,
            now = now,
            zoneId = zoneId,
            candidates = candidates,
            diagnosticNotes = diagnosticNotes
        )

        // 3.2: Task Start Schedule Alignment (planned vs actual start delay)
        evaluateStartDelayScheduleInsight(
            timing = timing,
            unifiedContext = unifiedContext,
            userId = userId,
            windowStart = windowStart,
            windowEnd = windowEnd,
            now = now,
            zoneId = zoneId,
            candidates = candidates,
            diagnosticNotes = diagnosticNotes
        )

        // 3.3: Task Completion Schedule Alignment (planned vs actual completion delay)
        evaluateCompletionDelayScheduleInsight(
            timing = timing,
            unifiedContext = unifiedContext,
            userId = userId,
            windowStart = windowStart,
            windowEnd = windowEnd,
            now = now,
            zoneId = zoneId,
            candidates = candidates,
            diagnosticNotes = diagnosticNotes
        )

        // -------------------------------------------------------------
        // 4. DIGITAL BEHAVIOUR INSIGHTS (STRICT USAGE ACCESS GATING)
        // -------------------------------------------------------------
        val digitalContext = unifiedContext.digitalBehaviour
        if (digitalContext.permissionStatus != UsagePermissionStatus.GRANTED ||
            digitalContext.availability != SourceAvailability.AVAILABLE
        ) {
            diagnosticNotes.add("Digital behaviour insights suppressed: Usage Access permission not granted or source unavailable.")
        } else {
            val appUsages = digitalContext.appUsages
            if (appUsages.isNotEmpty()) {
                val totalDurationMinutes = appUsages.sumOf { it.totalDurationMs } / 60000L
                val topApp = appUsages.maxByOrNull { it.totalDurationMs }

                if (totalDurationMinutes >= 15L && topApp != null) {
                    val topAppName = topApp.appName ?: topApp.packageName
                    val topAppMinutes = topApp.totalDurationMs / 60000L

                    val freshness = if (unifiedContext.isFresh) SourceFreshness.CURRENT else SourceFreshness.STALE
                    val uncertaintyNote = if (freshness == SourceFreshness.STALE) {
                        "Digital usage data is STALE (> 24 hours old). Recent app interactions may not be reflected."
                    } else {
                        "App usage does not differentiate between productive, neutral, and distracting usage."
                    }

                    val insight = PersonalInsight(
                        id = idGenerator.generateId(),
                        userId = userId,
                        category = InsightCategory.DIGITAL_BEHAVIOUR,
                        title = "Digital Phone Usage Summary",
                        explanation = "Recorded $totalDurationMinutes minutes of app usage. Top application was $topAppName with $topAppMinutes minutes.",
                        explainabilityBlock = ExplainabilityBlock(
                            whatWasObserved = "System recorded $totalDurationMinutes minutes across ${appUsages.size} tracked applications.",
                            observationPeriod = formatPeriod(windowStart, windowEnd, zoneId),
                            supportingEvidenceDescription = "Calculated from system app usage events ($topAppName accounted for ${topAppMinutes}m).",
                            whyItMatters = "Awareness of device engagement helps spot unintended screen time drift.",
                            uncertaintyOrLimitations = uncertaintyNote,
                            suggestedNextSteps = "Review whether current device time aligns with your daily priorities."
                        ),
                        observationWindowStartMs = windowStart,
                        observationWindowEndMs = windowEnd,
                        firstObservedAt = windowStart,
                        lastObservedAt = windowEnd,
                        confidence = if (freshness == SourceFreshness.CURRENT) 0.85f else 0.40f,
                        epistemicAuthority = EpistemicAuthority.SYSTEM_RECORDED_OBSERVATION,
                        sourceProvenance = listOf("Android:UsageStatsManager"),
                        sourceFreshness = freshness,
                        createdAt = now,
                        updatedAt = now
                    )
                    candidates.add(insight)
                }
            }
        }

        // -------------------------------------------------------------
        // 5. FOCUS INSIGHTS
        // -------------------------------------------------------------
        val focusSessions = digitalContext.focusActivity.recentSessions
        if (focusSessions.size >= 2) {
            val completedSessions = focusSessions.count { it.status.name == "COMPLETED" || it.endedAt != null }
            val totalMinutes = digitalContext.focusActivity.totalFocusDurationMs / 60000L

            val insight = PersonalInsight(
                id = idGenerator.generateId(),
                userId = userId,
                category = InsightCategory.FOCUS,
                title = "Focus Protection Engagement",
                explanation = "Engaged in ${focusSessions.size} focus session(s) totaling $totalMinutes minutes ($completedSessions completed).",
                explainabilityBlock = ExplainabilityBlock(
                    whatWasObserved = "${focusSessions.size} protection sessions initiated during the observation window.",
                    observationPeriod = formatPeriod(windowStart, windowEnd, zoneId),
                    supportingEvidenceDescription = "Aggregated from ${focusSessions.size} recorded focus sessions.",
                    whyItMatters = "Protected focus blocks safeguard uninterrupted deep work.",
                    uncertaintyOrLimitations = "Session completion reflects technical activation, not subjective depth of attention.",
                    suggestedNextSteps = "Maintain dedicated focus windows for complex tasks."
                ),
                supportingEvidenceIds = focusSessions.map { it.id },
                observationWindowStartMs = windowStart,
                observationWindowEndMs = windowEnd,
                firstObservedAt = windowStart,
                lastObservedAt = windowEnd,
                confidence = 0.85f,
                epistemicAuthority = EpistemicAuthority.DERIVED_CALCULATION,
                sourceProvenance = listOf("Room:protection_session"),
                sourceFreshness = if (unifiedContext.isFresh) SourceFreshness.CURRENT else SourceFreshness.STALE,
                createdAt = now,
                updatedAt = now
            )
            candidates.add(insight)
        }

        // -------------------------------------------------------------
        // 6. CROSS-DOMAIN INSIGHTS (STRICT NON-CAUSAL FRAMING)
        // -------------------------------------------------------------
        for (correlation in unifiedContext.correlations) {
            val insight = PersonalInsight(
                id = idGenerator.generateId(),
                userId = userId,
                category = InsightCategory.CROSS_DOMAIN,
                title = "Observed Co-occurrence: ${correlation.title}",
                explanation = correlation.description,
                explainabilityBlock = ExplainabilityBlock(
                    whatWasObserved = correlation.description,
                    observationPeriod = formatPeriod(correlation.observedWindowStartMs, correlation.observedWindowEndMs, zoneId),
                    supportingEvidenceDescription = "Co-occurrence detected between ${correlation.productivityEntityType} '${correlation.productivityEntityTitle}' and digital activity (${correlation.digitalAppPackage ?: "general"}).",
                    whyItMatters = "Identifies potential environmental or behavioral friction points.",
                    uncertaintyOrLimitations = "CRITICAL LIMITATION: Correlation does NOT imply causation. ${correlation.disclaimer}",
                    suggestedNextSteps = "Observe whether device placement or notification management during tasks reduces friction."
                ),
                supportingEvidenceIds = listOf(correlation.productivityEntityId),
                observationWindowStartMs = correlation.observedWindowStartMs,
                observationWindowEndMs = correlation.observedWindowEndMs,
                firstObservedAt = correlation.observedWindowStartMs,
                lastObservedAt = correlation.observedWindowEndMs,
                confidence = correlation.confidence,
                epistemicAuthority = EpistemicAuthority.AI_HYPOTHESIS,
                sourceProvenance = listOf("CrossDomainCorrelator", "Room:task", "Android:UsageStatsManager"),
                sourceFreshness = if (unifiedContext.isFresh) SourceFreshness.CURRENT else SourceFreshness.STALE,
                createdAt = now,
                updatedAt = now
            )
            candidates.add(insight)
        }

        // -------------------------------------------------------------
        // 7. INTERVENTION OUTCOME INSIGHTS (LEARNING FROM INTERVENTIONS)
        // -------------------------------------------------------------
        for (rec in recommendations) {
            if (rec.status == InterventionRecommendationStatus.ACCEPTED) {
                // Find matching outcomes
                val matchingOutcomes = outcomes.filter {
                    it.sourceEntityId == rec.id ||
                            (rec.targetEntityId != null && it.sourceEntityId == rec.targetEntityId)
                }

                if (matchingOutcomes.isEmpty()) {
                    // ACCEPTED BUT NO OUTCOME MEASURED: EFFECTIVENESS UNKNOWN!
                    val insight = PersonalInsight(
                        id = idGenerator.generateId(),
                        userId = userId,
                        category = InsightCategory.INTERVENTION_OUTCOME,
                        title = "Intervention Accepted — Outcome Not Measured",
                        explanation = "Recommendation '${rec.suggestedAction}' was accepted by the user. However, no subsequent outcome has been recorded or measured.",
                        explainabilityBlock = ExplainabilityBlock(
                            whatWasObserved = "User accepted recommendation '${rec.suggestedAction}'. No measured outcome record found in local store.",
                            observationPeriod = formatPeriod(rec.createdAt, now, zoneId),
                            supportingEvidenceDescription = "Recommendation ID: ${rec.id}, Status: ACCEPTED, Target: ${rec.targetEntityType ?: "general"}.",
                            whyItMatters = "Acceptance indicates user willingness to try an intervention, but effectiveness requires verified outcomes.",
                            uncertaintyOrLimitations = "EFFECTIVENESS IS UNKNOWN: Acceptance of a recommendation must NEVER be treated as successful intervention.",
                            suggestedNextSteps = "Execute the intervention and record the outcome upon completion."
                        ),
                        linkedRecommendationId = rec.id,
                        observationWindowStartMs = rec.createdAt,
                        observationWindowEndMs = now,
                        firstObservedAt = rec.createdAt,
                        lastObservedAt = now,
                        confidence = 0.20f,
                        epistemicAuthority = EpistemicAuthority.AI_HYPOTHESIS,
                        sourceProvenance = listOf("Room:intervention_recommendation"),
                        sourceFreshness = SourceFreshness.CURRENT,
                        createdAt = now,
                        updatedAt = now
                    )
                    candidates.add(insight)
                } else if (matchingOutcomes.size < 3) {
                    // SAMPLE SIZE TOO SMALL
                    val insight = PersonalInsight(
                        id = idGenerator.generateId(),
                        userId = userId,
                        category = InsightCategory.INTERVENTION_OUTCOME,
                        title = "Intervention Measurement: Insufficient Samples",
                        explanation = "Only ${matchingOutcomes.size} outcome(s) recorded for '${rec.suggestedAction}'. At least 3 measured outcomes are required to evaluate effectiveness reliably.",
                        explainabilityBlock = ExplainabilityBlock(
                            whatWasObserved = "${matchingOutcomes.size} outcome record(s) linked to '${rec.suggestedAction}'.",
                            observationPeriod = formatPeriod(rec.createdAt, now, zoneId),
                            supportingEvidenceDescription = "Outcomes: ${matchingOutcomes.joinToString { it.resultStatus.name }}.",
                            whyItMatters = "Prevents premature conclusions from anecdotal single-instance results.",
                            uncertaintyOrLimitations = "EFFECTIVENESS IS UNKNOWN due to insufficient sample size (N=${matchingOutcomes.size}, required >= 3).",
                            suggestedNextSteps = "Continue testing this intervention across multiple sessions to build reliable evidence."
                        ),
                        linkedRecommendationId = rec.id,
                        observationWindowStartMs = rec.createdAt,
                        observationWindowEndMs = now,
                        firstObservedAt = rec.createdAt,
                        lastObservedAt = now,
                        confidence = 0.40f,
                        epistemicAuthority = EpistemicAuthority.AI_HYPOTHESIS,
                        sourceProvenance = listOf("Room:intervention_recommendation", "Room:outcome"),
                        sourceFreshness = SourceFreshness.CURRENT,
                        createdAt = now,
                        updatedAt = now
                    )
                    candidates.add(insight)
                } else {
                    // N >= 3 OUTCOMES: MEASURE EFFECTIVENESS
                    val successCount = matchingOutcomes.count { it.resultStatus == ResultStatus.POSITIVE }
                    val rate = (successCount.toFloat() / matchingOutcomes.size.toFloat()) * 100f

                    val insight = PersonalInsight(
                        id = idGenerator.generateId(),
                        userId = userId,
                        category = InsightCategory.INTERVENTION_OUTCOME,
                        title = "Measured Intervention Effectiveness: ${rec.interventionType}",
                        explanation = "Across ${matchingOutcomes.size} measured outcomes for '${rec.suggestedAction}', $successCount were successful (${String.format("%.0f", rate)}%).",
                        explainabilityBlock = ExplainabilityBlock(
                            whatWasObserved = "$successCount successful outcomes out of ${matchingOutcomes.size} verified trials.",
                            observationPeriod = formatPeriod(rec.createdAt, now, zoneId),
                            supportingEvidenceDescription = "${matchingOutcomes.size} verified outcome records: $successCount SUCCESS, ${matchingOutcomes.size - successCount} OTHER.",
                            whyItMatters = "Evidence-based evaluation of whether this intervention reliably aids task completion.",
                            uncertaintyOrLimitations = "Applies specifically to context similar to the measured trials.",
                            suggestedNextSteps = if (rate >= 60f) {
                                "Intervention shows positive measured efficacy. Recommended for continued use."
                            } else {
                                "Intervention shows mixed results. Consider exploring alternative strategies."
                            }
                        ),
                        linkedRecommendationId = rec.id,
                        observationWindowStartMs = rec.createdAt,
                        observationWindowEndMs = now,
                        firstObservedAt = rec.createdAt,
                        lastObservedAt = now,
                        confidence = 0.80f,
                        epistemicAuthority = EpistemicAuthority.DERIVED_CALCULATION,
                        sourceProvenance = listOf("Room:intervention_recommendation", "Room:outcome"),
                        sourceFreshness = SourceFreshness.CURRENT,
                        createdAt = now,
                        updatedAt = now
                    )
                    candidates.add(insight)
                }
            }
        }

        // -------------------------------------------------------------
        // 8. DEDUPLICATION & DISMISSED INSIGHT SUPPRESSION
        // -------------------------------------------------------------
        val finalInsights = mutableListOf<PersonalInsight>()

        for (candidate in candidates) {
            // Check if dismissed previously
            val isDismissed = existingInsights.any { existing ->
                existing.category == candidate.category &&
                        existing.reviewStatus == InsightReviewStatus.DISMISSED &&
                        (existing.title == candidate.title ||
                                (existing.linkedRecommendationId != null && existing.linkedRecommendationId == candidate.linkedRecommendationId))
            }

            if (isDismissed) {
                suppressedCount++
                diagnosticNotes.add("Insight suppressed: Previously dismissed by user ('${candidate.title}')")
                continue
            }

            // Check if already active/unreviewed or useful
            val isDuplicate = existingInsights.any { existing ->
                existing.category == candidate.category &&
                        existing.reviewStatus != InsightReviewStatus.DISMISSED &&
                        (existing.title == candidate.title ||
                                (existing.linkedRecommendationId != null && existing.linkedRecommendationId == candidate.linkedRecommendationId))
            }

            if (isDuplicate) {
                suppressedCount++
                diagnosticNotes.add("Insight deduplicated: Active insight already exists ('${candidate.title}')")
                continue
            }

            finalInsights.add(candidate)
        }

        return InsightDiscoveryResult(
            discoveredInsights = finalInsights,
            suppressedCount = suppressedCount,
            diagnosticNotes = diagnosticNotes
        )
    }

    private fun formatPeriod(startMs: Long, endMs: Long, zoneId: ZoneId): String {
        if (startMs <= 0L) return "Recent period"
        val formatter = DateTimeFormatter.ofPattern("MMM dd").withZone(zoneId)
        val startStr = formatter.format(Instant.ofEpochMilli(startMs))
        val endStr = formatter.format(Instant.ofEpochMilli(endMs))
        return if (startStr == endStr) startStr else "$startStr - $endStr"
    }

    private fun resolveExecutionTiming(
        userId: String,
        unifiedContext: UnifiedContext,
        windowStart: Long,
        windowEnd: Long,
        zoneId: ZoneId
    ): ExecutionTimingSummary {
        val existing = unifiedContext.productivity.executionTiming
        if (existing.windowEndTimeMs > 0L || existing.hasSufficientData) {
            return existing
        }
        val allTasks = unifiedContext.productivity.taskSummary.allTasks.ifEmpty {
            unifiedContext.productivity.taskSummary.completedTasks
        }
        if (allTasks.isNotEmpty() || unifiedContext.productivity.recentEvents.isNotEmpty()) {
            return ExecutionTimingAnalyzer.analyze(
                userId = userId,
                tasks = allTasks,
                events = unifiedContext.productivity.recentEvents,
                windowStartMs = windowStart,
                windowEndMs = windowEnd,
                zoneId = zoneId,
                eventRepositoryAvailable = unifiedContext.provenanceMap["Events"]?.availability != SourceAvailability.UNAVAILABLE_HARDWARE
            )
        }
        return existing
    }

    private data class CompletedTaskTiming(
        val taskId: String,
        val completedTime: Long,
        val source: TemporalSampleSource
    )

    private fun evaluateCompletionTimingRhythmInsight(
        timing: ExecutionTimingSummary,
        unifiedContext: UnifiedContext,
        userId: String,
        windowStart: Long,
        windowEnd: Long,
        now: Long,
        zoneId: ZoneId,
        candidates: MutableList<PersonalInsight>,
        diagnosticNotes: MutableList<String>
    ) {
        val completedEvents = unifiedContext.productivity.recentEvents
            .filter { it.type == EventType.TASK_COMPLETED && it.occurredAt in windowStart..windowEnd }
        val completedTaskObjects = unifiedContext.productivity.taskSummary.completedTasks
            .filter { it.status == TaskStatus.COMPLETED && it.completedAt != null && it.completedAt in windowStart..windowEnd }

        val distinctCompleted = mutableMapOf<String, CompletedTaskTiming>()

        // 1. Event-derived completions take priority
        for (event in completedEvents) {
            val taskId = event.entityId ?: event.id
            if (taskId !in distinctCompleted) {
                distinctCompleted[taskId] = CompletedTaskTiming(
                    taskId = taskId,
                    completedTime = event.occurredAt,
                    source = TemporalSampleSource.EVENT_DERIVED
                )
            }
        }

        // 2. Samples from timing.completionDelaySamples within window
        for (sample in timing.completionDelaySamples) {
            if (sample.completedAt in windowStart..windowEnd && sample.taskId !in distinctCompleted) {
                distinctCompleted[sample.taskId] = CompletedTaskTiming(
                    taskId = sample.taskId,
                    completedTime = sample.completedAt,
                    source = sample.source
                )
            }
        }

        // 3. Historical Task.completedAt fallback where status == COMPLETED
        for (task in completedTaskObjects) {
            val completedAt = task.completedAt ?: continue
            if (completedAt in windowStart..windowEnd && task.id !in distinctCompleted) {
                distinctCompleted[task.id] = CompletedTaskTiming(
                    taskId = task.id,
                    completedTime = completedAt,
                    source = TemporalSampleSource.TASK_TIMESTAMP_FALLBACK
                )
            }
        }

        val completedList = distinctCompleted.values.toList()

        val distinctDays = completedList
            .map { Instant.ofEpochMilli(it.completedTime).atZone(zoneId).toLocalDate() }
            .distinct()
            .size

        if (completedList.size < 3 || distinctDays < 2) {
            diagnosticNotes.add(
                "Task scheduling timing rhythm insight suppressed: Insufficient window-scoped completed tasks " +
                    "(N=${completedList.size}, distinctDays=$distinctDays, required N>=3 across >=2 days)"
            )
            return
        }

        val byBucket = completedList.groupBy { ExecutionTimingAnalyzer.toExecutionBucket(it.completedTime, zoneId) }
        val morningCount = byBucket[ExecutionTimeBucket.MORNING]?.size ?: 0
        val afternoonCount = byBucket[ExecutionTimeBucket.AFTERNOON]?.size ?: 0
        val eveningCount = byBucket[ExecutionTimeBucket.EVENING]?.size ?: 0
        val nightCount = byBucket[ExecutionTimeBucket.NIGHT]?.size ?: 0

        val total = completedList.size
        val morningRatio = morningCount.toFloat() / total.toFloat()
        val afternoonEveningCount = afternoonCount + eveningCount
        val afternoonEveningRatio = afternoonEveningCount.toFloat() / total.toFloat()
        val nightRatio = nightCount.toFloat() / total.toFloat()

        val preferenceDescription = when {
            morningRatio >= 0.60f -> "morning hours (06:00 - 12:00)"
            afternoonEveningRatio >= 0.60f -> "afternoon or evening hours (12:00 - 22:00)"
            nightRatio >= 0.50f -> "night hours (22:00 - 06:00)"
            else -> "a balanced distribution throughout the day"
        }

        val explanation = when {
            morningRatio >= 0.60f ->
                "$morningCount of $total tasks were completed during morning hours, indicating consistent execution during morning hours (06:00 - 12:00)."
            afternoonEveningRatio >= 0.60f ->
                "$afternoonEveningCount of $total tasks were completed during afternoon or evening hours, indicating consistent execution during afternoon or evening hours (12:00 - 22:00)."
            nightRatio >= 0.50f ->
                "$nightCount of $total tasks were completed during night hours, indicating consistent execution during night hours (22:00 - 06:00)."
            else ->
                "Task completions were distributed across multiple times of day without a single dominant window."
        }

        val hasEvents = completedList.any { it.source == TemporalSampleSource.EVENT_DERIVED }
        val hasFallbacks = completedList.any { it.source == TemporalSampleSource.TASK_TIMESTAMP_FALLBACK }
        val sourceProvenance = when {
            hasEvents && hasFallbacks -> listOf("Room:event", "Room:task")
            hasEvents -> listOf("Room:event", "Room:task")
            else -> listOf("Room:task")
        }

        val limitationNotes = buildList {
            add("Observed execution timing reflects recorded completions; it does not indicate cognitive limitation or lack of capacity during other hours.")
            if (timing.provenance.isEventHistoryTruncated) {
                add("Event history query reached capacity; some lifecycle events within the window may be omitted.")
            }
            if (hasFallbacks && !hasEvents) {
                add("Derived from historical task completion records; start timestamps were not available.")
            }
        }.joinToString(" ")

        val insight = PersonalInsight(
            id = idGenerator.generateId(),
            userId = userId,
            category = InsightCategory.TASK_SCHEDULING,
            title = "Task Completion Timing Rhythm",
            explanation = explanation,
            explainabilityBlock = ExplainabilityBlock(
                whatWasObserved = "Task completions in the observed period clustered predominantly during $preferenceDescription ($morningCount Morning, $afternoonCount Afternoon, $eveningCount Evening, $nightCount Night across $distinctDays calendar days).",
                observationPeriod = formatPeriod(windowStart, windowEnd, zoneId),
                supportingEvidenceDescription = "Calculated from completion timestamps of $total window-scoped tasks across $distinctDays distinct calendar days.",
                whyItMatters = "Helps align demanding commitments with periods of highest demonstrated execution.",
                uncertaintyOrLimitations = limitationNotes,
                suggestedNextSteps = "Consider scheduling high-priority or complex tasks during your peak completion hours."
            ),
            supportingEvidenceIds = completedList.map { it.taskId },
            observationWindowStartMs = windowStart,
            observationWindowEndMs = windowEnd,
            firstObservedAt = completedList.minOf { it.completedTime },
            lastObservedAt = completedList.maxOf { it.completedTime },
            confidence = if (!unifiedContext.isFresh || timing.provenance.isEventHistoryTruncated) 0.70f else if (hasEvents) 0.80f else 0.75f,
            epistemicAuthority = EpistemicAuthority.AI_HYPOTHESIS,
            sourceProvenance = sourceProvenance,
            sourceFreshness = if (unifiedContext.isFresh) SourceFreshness.CURRENT else SourceFreshness.STALE,
            createdAt = now,
            updatedAt = now
        )
        candidates.add(insight)
    }

    private fun evaluateStartDelayScheduleInsight(
        timing: ExecutionTimingSummary,
        unifiedContext: UnifiedContext,
        userId: String,
        windowStart: Long,
        windowEnd: Long,
        now: Long,
        zoneId: ZoneId,
        candidates: MutableList<PersonalInsight>,
        diagnosticNotes: MutableList<String>
    ) {
        val distinctStartTasks = timing.startDelaySamples
            .filter { it.startedAt in windowStart..windowEnd }
            .distinctBy { it.taskId }

        val distinctStartDays = distinctStartTasks
            .map { Instant.ofEpochMilli(it.startedAt).atZone(zoneId).toLocalDate() }
            .distinct()
            .size

        if (distinctStartTasks.size < 3 || distinctStartDays < 2) {
            diagnosticNotes.add(
                "Task start schedule alignment insight suppressed: Insufficient samples across distinct days " +
                    "(N=${distinctStartTasks.size}, distinctDays=$distinctStartDays, required N>=3 across >=2 days)"
            )
            return
        }

        val delays = distinctStartTasks.map { it.delayMs }
        val medianDelayMs = computeMedian(delays)
        val avgDelayMs = delays.average().roundToLong()

        val explanation = when {
            medianDelayMs > 60_000L ->
                "Tasks in the observed period were often started later than planned (median delay of ${formatDuration(medianDelayMs)} across ${distinctStartTasks.size} tasks)."
            medianDelayMs < -60_000L ->
                "Tasks in the observed period were often started earlier than planned (median lead of ${formatDuration(-medianDelayMs)} across ${distinctStartTasks.size} tasks)."
            else ->
                "Tasks in the observed period were generally started closely aligned with their scheduled start times across ${distinctStartTasks.size} tasks."
        }

        val limitationNotes = buildList {
            add("The available records show a recurring difference between planned and recorded execution times. Reflects temporal variance only and does not establish intent or execution quality.")
            if (timing.provenance.unmeasuredStartsMissingScheduleCount > 0) {
                add("${timing.provenance.unmeasuredStartsMissingScheduleCount} task(s) started without a scheduled start time were excluded from delay calculation.")
            }
            if (timing.provenance.isEventHistoryTruncated) {
                add("Event history query reached limit; some lifecycle events within the window may be omitted.")
            }
        }.joinToString(" ")

        val insight = PersonalInsight(
            id = idGenerator.generateId(),
            userId = userId,
            category = InsightCategory.TASK_SCHEDULING,
            title = "Task Start Schedule Alignment",
            explanation = explanation,
            explainabilityBlock = ExplainabilityBlock(
                whatWasObserved = "Measured start times for ${distinctStartTasks.size} scheduled tasks across $distinctStartDays calendar days relative to planned start times (median delta: ${formatDuration(medianDelayMs)}, average delta: ${formatDuration(avgDelayMs)}).",
                observationPeriod = formatPeriod(windowStart, windowEnd, zoneId),
                supportingEvidenceDescription = "Derived from TASK_STARTED lifecycle events across tasks: ${distinctStartTasks.joinToString { it.taskId }}.",
                whyItMatters = "Understanding differences between planned and actual start times helps improve daily scheduling accuracy.",
                uncertaintyOrLimitations = limitationNotes,
                suggestedNextSteps = "Consider adding buffer time between scheduled tasks or adjusting planned start times to match actual execution rhythm."
            ),
            supportingEvidenceIds = distinctStartTasks.map { it.taskId },
            observationWindowStartMs = windowStart,
            observationWindowEndMs = windowEnd,
            firstObservedAt = distinctStartTasks.minOf { it.startedAt },
            lastObservedAt = distinctStartTasks.maxOf { it.startedAt },
            confidence = if (!unifiedContext.isFresh || timing.provenance.isEventHistoryTruncated) 0.70f else 0.85f,
            epistemicAuthority = EpistemicAuthority.DERIVED_CALCULATION,
            sourceProvenance = listOf("Room:event", "Room:task"),
            sourceFreshness = if (unifiedContext.isFresh) SourceFreshness.CURRENT else SourceFreshness.STALE,
            createdAt = now,
            updatedAt = now
        )
        candidates.add(insight)
    }

    private fun evaluateCompletionDelayScheduleInsight(
        timing: ExecutionTimingSummary,
        unifiedContext: UnifiedContext,
        userId: String,
        windowStart: Long,
        windowEnd: Long,
        now: Long,
        zoneId: ZoneId,
        candidates: MutableList<PersonalInsight>,
        diagnosticNotes: MutableList<String>
    ) {
        val distinctCompletionTasks = timing.completionDelaySamples
            .filter { it.completedAt in windowStart..windowEnd }
            .distinctBy { it.taskId }

        val distinctCompletionDays = distinctCompletionTasks
            .map { Instant.ofEpochMilli(it.completedAt).atZone(zoneId).toLocalDate() }
            .distinct()
            .size

        if (distinctCompletionTasks.size < 3 || distinctCompletionDays < 2) {
            diagnosticNotes.add(
                "Task completion schedule alignment insight suppressed: Insufficient samples across distinct days " +
                    "(N=${distinctCompletionTasks.size}, distinctDays=$distinctCompletionDays, required N>=3 across >=2 days)"
            )
            return
        }

        val delays = distinctCompletionTasks.map { it.delayMs }
        val medianDelayMs = computeMedian(delays)
        val avgDelayMs = delays.average().roundToLong()

        val explanation = when {
            medianDelayMs > 60_000L ->
                "Tasks in the observed period were often completed after their scheduled due time (median delta of ${formatDuration(medianDelayMs)} across ${distinctCompletionTasks.size} tasks)."
            medianDelayMs < -60_000L ->
                "Tasks in the observed period were often completed ahead of their scheduled due time (median lead of ${formatDuration(-medianDelayMs)} across ${distinctCompletionTasks.size} tasks)."
            else ->
                "Tasks in the observed period were completed closely aligned with their scheduled due dates across ${distinctCompletionTasks.size} tasks."
        }

        val hasEvents = distinctCompletionTasks.any { it.source == TemporalSampleSource.EVENT_DERIVED }
        val hasFallbacks = distinctCompletionTasks.any { it.source == TemporalSampleSource.TASK_TIMESTAMP_FALLBACK }
        val sourceProvenance = when {
            hasEvents && hasFallbacks -> listOf("Room:event", "Room:task")
            hasEvents -> listOf("Room:event", "Room:task")
            else -> listOf("Room:task")
        }

        val limitationNotes = buildList {
            add("The available records show a recurring difference between planned and recorded execution times. Reflects temporal variance only and does not establish intent or execution quality.")
            if (!timing.provenance.eventRepositoryAvailable) {
                add("Event repository was unavailable; completion timestamps were taken from task completion records where available.")
            } else if (hasFallbacks) {
                add("Includes ${timing.provenance.timestampFallbackCompletionCount} historical task record(s) lacking lifecycle events.")
            }
            if (timing.provenance.unmeasuredCompletionsMissingDueDateCount > 0) {
                add("${timing.provenance.unmeasuredCompletionsMissingDueDateCount} completed task(s) lacked due dates and were excluded from delay calculations.")
            }
            if (timing.provenance.isEventHistoryTruncated) {
                add("Event history query reached limit; some lifecycle events may be omitted.")
            }
        }.joinToString(" ")

        val confidence = when {
            !timing.provenance.eventRepositoryAvailable -> 0.60f
            hasFallbacks && !hasEvents -> 0.70f
            timing.provenance.isEventHistoryTruncated || !unifiedContext.isFresh -> 0.70f
            else -> 0.85f
        }

        val insight = PersonalInsight(
            id = idGenerator.generateId(),
            userId = userId,
            category = InsightCategory.TASK_SCHEDULING,
            title = "Task Completion Schedule Alignment",
            explanation = explanation,
            explainabilityBlock = ExplainabilityBlock(
                whatWasObserved = "Measured completion times for ${distinctCompletionTasks.size} tasks across $distinctCompletionDays calendar days relative to planned due dates (median delta: ${formatDuration(medianDelayMs)}, average delta: ${formatDuration(avgDelayMs)}).",
                observationPeriod = formatPeriod(windowStart, windowEnd, zoneId),
                supportingEvidenceDescription = "Derived from ${if (hasEvents) "TASK_COMPLETED lifecycle events" else ""}${if (hasEvents && hasFallbacks) " and " else ""}${if (hasFallbacks) "task completion records" else ""} across tasks: ${distinctCompletionTasks.joinToString { it.taskId }}.",
                whyItMatters = "Assessing planned versus actual completion times helps identify realistic task duration expectations.",
                uncertaintyOrLimitations = limitationNotes,
                suggestedNextSteps = "Consider reviewing task duration estimates or setting interim checkpoints for complex commitments."
            ),
            supportingEvidenceIds = distinctCompletionTasks.map { it.taskId },
            observationWindowStartMs = windowStart,
            observationWindowEndMs = windowEnd,
            firstObservedAt = distinctCompletionTasks.minOf { it.completedAt },
            lastObservedAt = distinctCompletionTasks.maxOf { it.completedAt },
            confidence = confidence,
            epistemicAuthority = EpistemicAuthority.DERIVED_CALCULATION,
            sourceProvenance = sourceProvenance,
            sourceFreshness = if (unifiedContext.isFresh) SourceFreshness.CURRENT else SourceFreshness.STALE,
            createdAt = now,
            updatedAt = now
        )
        candidates.add(insight)
    }

    private fun computeMedian(values: List<Long>): Long {
        if (values.isEmpty()) return 0L
        val sorted = values.sorted()
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 1) {
            sorted[mid]
        } else {
            ((sorted[mid - 1].toDouble() + sorted[mid].toDouble()) / 2.0).roundToLong()
        }
    }

    private fun formatDuration(durationMs: Long): String {
        val absMs = kotlin.math.abs(durationMs)
        val minutes = absMs / 60_000L
        val hours = minutes / 60L
        val remMinutes = minutes % 60L
        return when {
            hours > 0L && remMinutes > 0L -> "${hours}h ${remMinutes}m"
            hours > 0L -> "${hours}h"
            minutes > 0L -> "${minutes}m"
            else -> "<1m"
        }
    }
}

