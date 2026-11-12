package com.sanket_satpute_20.ironmind.domain.engine

import com.sanket_satpute_20.ironmind.domain.common.Clock
import com.sanket_satpute_20.ironmind.domain.model.EventType
import com.sanket_satpute_20.ironmind.domain.model.ProtectionSessionStatus
import com.sanket_satpute_20.ironmind.domain.model.context.EpistemicAuthority
import com.sanket_satpute_20.ironmind.domain.model.context.SourceAvailability
import com.sanket_satpute_20.ironmind.domain.model.context.UnifiedContext
import com.sanket_satpute_20.ironmind.domain.model.context.UsagePermissionStatus
import com.sanket_satpute_20.ironmind.domain.model.pattern.DiscoveredPattern
import com.sanket_satpute_20.ironmind.domain.model.pattern.EvidenceReference
import com.sanket_satpute_20.ironmind.domain.model.pattern.EvidenceSourceType
import com.sanket_satpute_20.ironmind.domain.model.pattern.ObservationWindow
import com.sanket_satpute_20.ironmind.domain.model.pattern.Pattern
import com.sanket_satpute_20.ironmind.domain.model.pattern.PatternCategory
import com.sanket_satpute_20.ironmind.domain.model.pattern.PatternDiscoveryType
import com.sanket_satpute_20.ironmind.domain.model.pattern.PatternLifecycleState
import com.sanket_satpute_20.ironmind.domain.model.pattern.PatternStatus
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Deterministic Behaviour Pattern Discovery Engine.
 * Part of IronMind-OS Phase 3.2.
 *
 * Discovers evidence-backed recurring patterns across:
 * 1. Personal productivity behaviour
 * 2. Digital phone usage behaviour (strictly permission-gated)
 * 3. Focus and protection activity
 * 4. Cross-domain non-causal correlations
 *
 * Epistemic & Safety Invariants:
 * - Deterministic domain component: zero AI API calls, zero cloud inference.
 * - Does NOT diagnose the user or infer motivation, laziness, addiction, or mental health.
 * - Single observation (N=1) NEVER establishes a recurring pattern (classified strictly as CANDIDATE).
 * - Repeated evidence advances lifecycle states: CANDIDATE -> EMERGING -> ESTABLISHED.
 * - Minimum evidence sufficiency: N >= 3 unique evidence items across >= 2 distinct calendar days.
 * - Missing or denied usage permissions reduce evidence availability and produce ZERO false 0-usage patterns.
 * - Cross-domain patterns are strictly EpistemicAuthority.AI_HYPOTHESIS with confidence < 1.0f and a mandatory disclaimer.
 */
class BehaviourPatternDiscoveryEngine(
    private val evidenceSufficiencyEvaluator: EvidenceSufficiencyEvaluator = EvidenceSufficiencyEvaluator(),
    private val clock: Clock = object : Clock {
        override fun currentTimeMillis() = System.currentTimeMillis()
    }
) {

    companion object {
        const val CANDIDATE_CONFIDENCE = 0.35f
        const val EMERGING_CONFIDENCE = 0.60f
        const val ESTABLISHED_CONFIDENCE = 0.85f
        const val WEAKENING_CONFIDENCE = 0.40f
        const val INACTIVE_CONFIDENCE = 0.10f

        const val NON_CAUSAL_DISCLAIMER =
            "Tentative correlation hypothesis only. Does not imply causation, intent, or confirmed distraction."
        const val FOCUS_NON_CAUSAL_DISCLAIMER =
            "Tentative correlation hypothesis only. Does not imply causation or exclusive attribution."

        const val INACTIVITY_THRESHOLD_MS = 14L * 24L * 60L * 60L * 1000L // 14 days
        const val STALE_CONFIDENCE_CAP = 0.50f
    }

    /**
     * Discovers behaviour patterns for the target user within the supplied UnifiedContext.
     *
     * @param unifiedContext The assembled unified personal and digital behaviour context.
     * @param existingPatterns Historical patterns already recorded for the user, used to evaluate weakening/inactivity.
     * @param zoneId Timezone used for calendar-day boundary evaluations.
     */
    fun discoverPatterns(
        unifiedContext: UnifiedContext,
        existingPatterns: List<Pattern> = emptyList(),
        zoneId: ZoneId = ZoneId.systemDefault()
    ): List<DiscoveredPattern> {
        val userId = unifiedContext.userId
        val window = ObservationWindow(unifiedContext.windowStartTimeMs, unifiedContext.windowEndTimeMs)
        val currentTimeMs = clock.currentTimeMillis()

        // Isolation: Enforce user ownership
        val userExistingPatterns = existingPatterns.filter { it.userId == userId }

        val discovered = mutableListOf<DiscoveredPattern>()

        // 1. Productivity Patterns
        evaluateProductivityPatterns(unifiedContext, window, zoneId, discovered)

        // 2. Digital Behaviour Patterns (Strictly Permission-Gated)
        evaluateDigitalBehaviourPatterns(unifiedContext, window, zoneId, currentTimeMs, discovered)

        // 3. Focus Patterns
        evaluateFocusPatterns(unifiedContext, window, zoneId, currentTimeMs, discovered)

        // 4. Cross-Domain Patterns (Strictly Non-Causal)
        evaluateCrossDomainPatterns(unifiedContext, window, zoneId, currentTimeMs, discovered)

        // 5. Evaluate Lifecycle Transitions Against Existing Patterns (Weakening / Inactivity)
        val finalPatterns = evaluateLifecycleTransitions(
            discovered = discovered,
            existingPatterns = userExistingPatterns,
            window = window,
            currentTimeMs = currentTimeMs
        )

        return finalPatterns
    }

    // =========================================================================
    // Category A: Productivity Patterns
    // =========================================================================

    private fun evaluateProductivityPatterns(
        context: UnifiedContext,
        window: ObservationWindow,
        zoneId: ZoneId,
        out: MutableList<DiscoveredPattern>
    ) {
        val prod = context.productivity
        val isStale = context.provenanceMap["Tasks"]?.isStale == true ||
            context.provenanceMap["Commitments"]?.isStale == true

        // A.1: Repeatedly Postponed Tasks
        val postponedEvidence = mutableListOf<Pair<EvidenceReference, Long>>()
        prod.taskSummary.postponedTasks.forEach { task ->
            postponedEvidence.add(EvidenceReference(task.id, EvidenceSourceType.EVENT) to task.updatedAt)
        }
        prod.recentEvents.filter { it.type == EventType.TASK_POSTPONED }.forEach { event ->
            val refId = event.entityId ?: event.id
            postponedEvidence.add(EvidenceReference(refId, EvidenceSourceType.EVENT) to event.occurredAt)
        }

        buildPatternIfEvidence(
            userId = context.userId,
            category = PatternCategory.PRODUCTIVITY,
            type = PatternDiscoveryType.REPEATEDLY_POSTPONED_TASKS,
            rawEvidence = postponedEvidence,
            window = window,
            zoneId = zoneId,
            descriptionBuilder = { count -> "Repeated task postponements observed across $count tasks/events." },
            sourceNames = listOf("Tasks", "Events"),
            isSourceStale = isStale
        )?.let { out.add(it) }

        // A.2: Repeatedly Missed Commitments
        val missedCommitmentEvidence = mutableListOf<Pair<EvidenceReference, Long>>()
        prod.commitmentSummary.repeatedMissedCommitments.forEach { rmc ->
            val refId = rmc.taskId ?: rmc.title
            missedCommitmentEvidence.add(EvidenceReference(refId, EvidenceSourceType.EVENT) to rmc.lastMissedAt)
        }
        prod.commitmentSummary.missedCommitments.forEach { com ->
            missedCommitmentEvidence.add(EvidenceReference(com.id, EvidenceSourceType.EVENT) to com.committedAt)
        }
        prod.recentEvents.filter { it.type == EventType.COMMITMENT_MISSED }.forEach { ev ->
            val refId = ev.entityId ?: ev.id
            missedCommitmentEvidence.add(EvidenceReference(refId, EvidenceSourceType.EVENT) to ev.occurredAt)
        }

        buildPatternIfEvidence(
            userId = context.userId,
            category = PatternCategory.PRODUCTIVITY,
            type = PatternDiscoveryType.REPEATEDLY_MISSED_COMMITMENTS,
            rawEvidence = missedCommitmentEvidence,
            window = window,
            zoneId = zoneId,
            descriptionBuilder = { count -> "Repeated missed commitments observed across $count instances." },
            sourceNames = listOf("Commitments", "Events"),
            isSourceStale = isStale
        )?.let { out.add(it) }

        // A.3: Task Completion by Time of Day
        val completedEvidence = mutableListOf<Pair<EvidenceReference, Long>>()
        prod.taskSummary.completedTasks.forEach { task ->
            val timestamp = task.completedAt ?: task.updatedAt
            completedEvidence.add(EvidenceReference(task.id, EvidenceSourceType.EVENT) to timestamp)
        }
        prod.recentEvents.filter { it.type == EventType.TASK_COMPLETED }.forEach { ev ->
            val refId = ev.entityId ?: ev.id
            completedEvidence.add(EvidenceReference(refId, EvidenceSourceType.EVENT) to ev.occurredAt)
        }

        val distinctCompleted = completedEvidence.distinctBy { it.first.sourceId to it.first.sourceType }
        if (distinctCompleted.isNotEmpty()) {
            val hourBins = distinctCompleted.groupBy { pair ->
                val hour = ZonedDateTime.ofInstant(Instant.ofEpochMilli(pair.second), zoneId).hour
                when (hour) {
                    in 6..11 -> "Morning"
                    in 12..16 -> "Afternoon"
                    in 17..21 -> "Evening"
                    else -> "Night"
                }
            }

            val topBin = hourBins.maxByOrNull { it.value.size }
            if (topBin != null && topBin.value.size >= 1) {
                val ratio = topBin.value.size.toFloat() / distinctCompleted.size.toFloat()
                if (ratio >= 0.50f || topBin.value.size >= 2) {
                    buildPatternIfEvidence(
                        userId = context.userId,
                        category = PatternCategory.PRODUCTIVITY,
                        type = PatternDiscoveryType.TASK_COMPLETION_BY_TIME_OF_DAY,
                        rawEvidence = topBin.value,
                        window = window,
                        zoneId = zoneId,
                        descriptionBuilder = { count ->
                            "Task completions concentrated during ${topBin.key} hours ($count of ${distinctCompleted.size} completions)."
                        },
                        sourceNames = listOf("Tasks", "Events"),
                        isSourceStale = isStale,
                        metadata = mapOf("timeOfDay" to topBin.key, "concentrationRatio" to ratio.toString())
                    )?.let { out.add(it) }
                }
            }
        }

        // A.4: Task Completion by Priority
        val completedTasks = prod.taskSummary.completedTasks
        if (completedTasks.isNotEmpty()) {
            val byPriority = completedTasks.groupBy { it.priority }
            val topPriority = byPriority.maxByOrNull { it.value.size }
            if (topPriority != null && topPriority.value.size >= 1) {
                val priorityEvidence = topPriority.value.map {
                    EvidenceReference(it.id, EvidenceSourceType.EVENT) to (it.completedAt ?: it.updatedAt)
                }
                val priorityName = when (topPriority.key) {
                    in 1..2 -> "LOW"
                    3 -> "MEDIUM"
                    else -> "HIGH"
                }
                buildPatternIfEvidence(
                    userId = context.userId,
                    category = PatternCategory.PRODUCTIVITY,
                    type = PatternDiscoveryType.TASK_COMPLETION_BY_PRIORITY,
                    rawEvidence = priorityEvidence,
                    window = window,
                    zoneId = zoneId,
                    descriptionBuilder = { count ->
                        "Predominant execution of $priorityName priority tasks ($count completed tasks)."
                    },
                    sourceNames = listOf("Tasks"),
                    isSourceStale = isStale,
                    metadata = mapOf("priority" to priorityName, "priorityLevel" to topPriority.key.toString())
                )?.let { out.add(it) }
            }
        }

        // A.5: Repeatedly Overdue Tasks
        val overdueEvidence = prod.taskSummary.overdueTasks.map {
            EvidenceReference(it.task.id, EvidenceSourceType.EVENT) to (it.task.dueAt ?: it.task.updatedAt)
        }
        buildPatternIfEvidence(
            userId = context.userId,
            category = PatternCategory.PRODUCTIVITY,
            type = PatternDiscoveryType.REPEATEDLY_OVERDUE_TASKS,
            rawEvidence = overdueEvidence,
            window = window,
            zoneId = zoneId,
            descriptionBuilder = { count -> "Tasks repeatedly becoming overdue ($count overdue tasks observed)." },
            sourceNames = listOf("Tasks"),
            isSourceStale = isStale
        )?.let { out.add(it) }

        // A.6: Goal Execution Consistency
        val progressingGoals = prod.activeGoals.filter { it.progress.completedTasks > 0 }
        if (progressingGoals.isNotEmpty()) {
            val goalEvidence = progressingGoals.map {
                EvidenceReference(it.goal.id, EvidenceSourceType.EVENT) to it.goal.updatedAt
            }
            buildPatternIfEvidence(
                userId = context.userId,
                category = PatternCategory.PRODUCTIVITY,
                type = PatternDiscoveryType.GOAL_EXECUTION_CONSISTENCY,
                rawEvidence = goalEvidence,
                window = window,
                zoneId = zoneId,
                descriptionBuilder = { count ->
                    "Consistent goal progress maintained across $count active goals."
                },
                sourceNames = listOf("Goals", "Tasks"),
                isSourceStale = isStale
            )?.let { out.add(it) }
        }

        // A.7: Plan Completion Trends
        val progressingPlans = prod.completedPlans + prod.activePlans.filter { it.progress.percentage >= 50 }
        if (progressingPlans.isNotEmpty()) {
            val planEvidence = progressingPlans.map {
                EvidenceReference(it.plan.id, EvidenceSourceType.EVENT) to it.plan.updatedAt
            }
            buildPatternIfEvidence(
                userId = context.userId,
                category = PatternCategory.PRODUCTIVITY,
                type = PatternDiscoveryType.PLAN_COMPLETION_TRENDS,
                rawEvidence = planEvidence,
                window = window,
                zoneId = zoneId,
                descriptionBuilder = { count ->
                    "Favorable plan progression observed ($count plans completed or >50% progressed)."
                },
                sourceNames = listOf("Plans", "Tasks"),
                isSourceStale = isStale
            )?.let { out.add(it) }
        }
    }

    // =========================================================================
    // Category B: Digital Behaviour Patterns (Strictly Permission-Gated)
    // =========================================================================

    private fun evaluateDigitalBehaviourPatterns(
        context: UnifiedContext,
        window: ObservationWindow,
        zoneId: ZoneId,
        currentTimeMs: Long,
        out: MutableList<DiscoveredPattern>
    ) {
        val digital = context.digitalBehaviour

        // ZERO FALSE CLAIMS: When permission is denied, unavailable, or missing, DO NOT GENERATE USAGE PATTERNS!
        if (digital.permissionStatus != UsagePermissionStatus.GRANTED ||
            digital.availability == SourceAvailability.PERMISSION_DENIED ||
            digital.availability == SourceAvailability.UNAVAILABLE_HARDWARE
        ) {
            return
        }

        val appUsages = digital.appUsages
        if (appUsages.isEmpty()) {
            return
        }

        val isStale = context.provenanceMap["AppUsage"]?.isStale == true

        // B.1: Frequently Used Applications (>= 15 minutes or >= 3 sessions)
        val frequentApps = appUsages.filter { it.totalDurationMs >= 15 * 60 * 1000L || it.sessionCount >= 3 }
        frequentApps.sortedByDescending { it.totalDurationMs }.take(3).forEach { app ->
            val timestamp = app.lastObservedAt ?: window.endTimeMs
            val evidence = listOf(EvidenceReference(app.packageName, EvidenceSourceType.OBSERVATION) to timestamp)
            buildPatternIfEvidence(
                userId = context.userId,
                category = PatternCategory.DIGITAL_BEHAVIOUR,
                type = PatternDiscoveryType.FREQUENTLY_USED_APPLICATIONS,
                rawEvidence = evidence,
                window = window,
                zoneId = zoneId,
                descriptionBuilder = {
                    "Frequent application usage observed for ${app.packageName} (${app.totalDurationMs / 60000} mins across ${app.sessionCount} sessions)."
                },
                sourceNames = listOf("AppUsage"),
                isSourceStale = isStale,
                metadata = mapOf("packageName" to app.packageName, "durationMs" to app.totalDurationMs.toString(), "sessions" to app.sessionCount.toString())
            )?.let { out.add(it) }
        }

        // B.2: High Usage Periods (Total duration across all apps >= 60 minutes)
        val totalUsageDurationMs = appUsages.sumOf { it.totalDurationMs }
        if (totalUsageDurationMs >= 60 * 60 * 1000L) {
            val highUsageEvidence = appUsages.map {
                val timestamp = it.lastObservedAt ?: window.endTimeMs
                EvidenceReference(it.packageName, EvidenceSourceType.OBSERVATION) to timestamp
            }
            buildPatternIfEvidence(
                userId = context.userId,
                category = PatternCategory.DIGITAL_BEHAVIOUR,
                type = PatternDiscoveryType.HIGH_USAGE_PERIODS,
                rawEvidence = highUsageEvidence,
                window = window,
                zoneId = zoneId,
                descriptionBuilder = { count ->
                    "High cumulative digital phone usage observed (${totalUsageDurationMs / 60000} minutes across $count apps)."
                },
                sourceNames = listOf("AppUsage"),
                isSourceStale = isStale,
                metadata = mapOf("totalDurationMs" to totalUsageDurationMs.toString())
            )?.let { out.add(it) }
        }

        // B.3: Repeated App Usage Sessions (sessionCount >= 5)
        appUsages.filter { it.sessionCount >= 5 }.forEach { app ->
            val timestamp = app.lastObservedAt ?: window.endTimeMs
            val sessionEvidence = listOf(EvidenceReference(app.packageName, EvidenceSourceType.OBSERVATION) to timestamp)
            buildPatternIfEvidence(
                userId = context.userId,
                category = PatternCategory.DIGITAL_BEHAVIOUR,
                type = PatternDiscoveryType.REPEATED_APP_USAGE_SESSIONS,
                rawEvidence = sessionEvidence,
                window = window,
                zoneId = zoneId,
                descriptionBuilder = {
                    "Repeated app launch sessions observed for ${app.packageName} (${app.sessionCount} distinct sessions)."
                },
                sourceNames = listOf("AppUsage"),
                isSourceStale = isStale,
                metadata = mapOf("packageName" to app.packageName, "sessionCount" to app.sessionCount.toString())
            )?.let { out.add(it) }
        }

        // B.4: Usage Distribution Across Time Windows
        val appEvidence = appUsages.map {
            val timestamp = it.lastObservedAt ?: window.endTimeMs
            EvidenceReference(it.packageName, EvidenceSourceType.OBSERVATION) to timestamp
        }
        val usageBins = appEvidence.groupBy { pair ->
            val hour = ZonedDateTime.ofInstant(Instant.ofEpochMilli(pair.second), zoneId).hour
            when (hour) {
                in 6..11 -> "Morning"
                in 12..16 -> "Afternoon"
                in 17..21 -> "Evening"
                else -> "Night"
            }
        }
        val topUsageBin = usageBins.maxByOrNull { it.value.size }
        if (topUsageBin != null && topUsageBin.value.size >= 1) {
            buildPatternIfEvidence(
                userId = context.userId,
                category = PatternCategory.DIGITAL_BEHAVIOUR,
                type = PatternDiscoveryType.USAGE_DISTRIBUTION_ACROSS_TIME_WINDOWS,
                rawEvidence = topUsageBin.value,
                window = window,
                zoneId = zoneId,
                descriptionBuilder = {
                    "Digital phone usage concentrated during ${topUsageBin.key} hours."
                },
                sourceNames = listOf("AppUsage"),
                isSourceStale = isStale,
                metadata = mapOf("timeWindow" to topUsageBin.key)
            )?.let { out.add(it) }
        }

        // B.5: Usage During Scheduled Focus Periods (using verified effective session bounds)
        val focusSessions = digital.focusActivity.recentSessions
        if (focusSessions.isNotEmpty()) {
            val overlappingUsage = appUsages.filter { usage ->
                val usageTime = usage.lastObservedAt ?: window.endTimeMs
                focusSessions.any { session ->
                    val effEnd = AppUsageIntervalAnalyzer.effectiveProtectionEndMs(session, currentTimeMs)
                        ?: return@any false
                    usageTime in session.startedAt..effEnd
                }
            }
            if (overlappingUsage.isNotEmpty()) {
                val conflictEvidence = overlappingUsage.map {
                    val timestamp = it.lastObservedAt ?: window.endTimeMs
                    EvidenceReference(it.packageName, EvidenceSourceType.OBSERVATION) to timestamp
                }
                buildPatternIfEvidence(
                    userId = context.userId,
                    category = PatternCategory.DIGITAL_BEHAVIOUR,
                    type = PatternDiscoveryType.USAGE_DURING_SCHEDULED_FOCUS,
                    rawEvidence = conflictEvidence,
                    window = window,
                    zoneId = zoneId,
                    descriptionBuilder = { count ->
                        "Digital activity detected concurrently during scheduled focus protection periods ($count apps)."
                    },
                    sourceNames = listOf("AppUsage", "FocusProtection"),
                    isSourceStale = isStale
                )?.let { out.add(it) }
            }
        }
    }

    // =========================================================================
    // Category C: Focus Patterns
    // =========================================================================

    private fun evaluateFocusPatterns(
        context: UnifiedContext,
        window: ObservationWindow,
        zoneId: ZoneId,
        currentTimeMs: Long,
        out: MutableList<DiscoveredPattern>
    ) {
        val focus = context.digitalBehaviour.focusActivity
        val recentSessions = focus.recentSessions
        if (recentSessions.isEmpty()) {
            return
        }

        val isStale = context.provenanceMap["FocusProtection"]?.isStale == true

        // C.1: Focus Session Frequency and Duration
        val focusEvidence = recentSessions.map {
            EvidenceReference(it.id, EvidenceSourceType.OBSERVATION) to it.startedAt
        }
        val totalMinutes = focus.totalFocusDurationMs / 60000
        buildPatternIfEvidence(
            userId = context.userId,
            category = PatternCategory.FOCUS,
            type = PatternDiscoveryType.FOCUS_SESSION_FREQUENCY_AND_DURATION,
            rawEvidence = focusEvidence,
            window = window,
            zoneId = zoneId,
            descriptionBuilder = { count ->
                "Regular focus protection sessions maintained ($count sessions, $totalMinutes total minutes)."
            },
            sourceNames = listOf("FocusProtection"),
            isSourceStale = isStale,
            metadata = mapOf("totalMinutes" to totalMinutes.toString())
        )?.let { out.add(it) }

        // C.2: Focus During Task Completion (bounded by effective protection end)
        val completedTasks = context.productivity.taskSummary.completedTasks
        val tasksCompletedDuringFocus = completedTasks.filter { task ->
            val completedTime = task.completedAt ?: task.updatedAt
            recentSessions.any { session ->
                val effEnd = AppUsageIntervalAnalyzer.effectiveProtectionEndMs(session, currentTimeMs)
                    ?: return@any false
                completedTime in (session.startedAt - 15 * 60 * 1000L)..effEnd
            }
        }
        if (tasksCompletedDuringFocus.isNotEmpty()) {
            val taskFocusEvidence = tasksCompletedDuringFocus.map {
                EvidenceReference(it.id, EvidenceSourceType.EVENT) to (it.completedAt ?: it.updatedAt)
            }
            buildPatternIfEvidence(
                userId = context.userId,
                category = PatternCategory.FOCUS,
                type = PatternDiscoveryType.FOCUS_DURING_TASK_COMPLETION,
                rawEvidence = taskFocusEvidence,
                window = window,
                zoneId = zoneId,
                descriptionBuilder = { count ->
                    "Task completions coinciding with active focus protection sessions ($count instances)."
                },
                sourceNames = listOf("Tasks", "FocusProtection"),
                isSourceStale = isStale
            )?.let { out.add(it) }
        }

        // C.3: Repeated Interruption Patterns (Early termination or CANCELLED / OVERRIDDEN)
        val interruptedSessions = recentSessions.filter {
            it.status == ProtectionSessionStatus.CANCELLED || it.status == ProtectionSessionStatus.OVERRIDDEN
        }
        if (interruptedSessions.isNotEmpty()) {
            val interruptionEvidence = interruptedSessions.map {
                EvidenceReference(it.id, EvidenceSourceType.OBSERVATION) to it.startedAt
            }
            buildPatternIfEvidence(
                userId = context.userId,
                category = PatternCategory.FOCUS,
                type = PatternDiscoveryType.REPEATED_FOCUS_INTERRUPTIONS,
                rawEvidence = interruptionEvidence,
                window = window,
                zoneId = zoneId,
                descriptionBuilder = { count ->
                    "Focus session interruptions recorded ($count sessions interrupted)."
                },
                sourceNames = listOf("FocusProtection"),
                isSourceStale = isStale
            )?.let { out.add(it) }
        }

        // C.4: Focus Consistency Over Time (Sessions across distinct days)
        val distinctSessionDays = recentSessions.map {
            ZonedDateTime.ofInstant(Instant.ofEpochMilli(it.startedAt), zoneId).toLocalDate()
        }.distinct().count()

        if (distinctSessionDays >= 2) {
            buildPatternIfEvidence(
                userId = context.userId,
                category = PatternCategory.FOCUS,
                type = PatternDiscoveryType.FOCUS_CONSISTENCY_OVER_TIME,
                rawEvidence = focusEvidence,
                window = window,
                zoneId = zoneId,
                descriptionBuilder = {
                    "Consistent focus routine maintained across $distinctSessionDays calendar days."
                },
                sourceNames = listOf("FocusProtection"),
                isSourceStale = isStale,
                metadata = mapOf("distinctDays" to distinctSessionDays.toString())
            )?.let { out.add(it) }
        }
    }

    // =========================================================================
    // Category D: Cross-Domain Patterns (Strictly Non-Causal!)
    // =========================================================================

    private fun evaluateCrossDomainPatterns(
        context: UnifiedContext,
        window: ObservationWindow,
        zoneId: ZoneId,
        currentTimeMs: Long,
        out: MutableList<DiscoveredPattern>
    ) {
        val digital = context.digitalBehaviour
        // If digital permissions are denied, cross-domain patterns involving app usage cannot be evaluated
        val hasDigitalAccess = digital.permissionStatus == UsagePermissionStatus.GRANTED &&
            digital.availability != SourceAvailability.PERMISSION_DENIED

        val isStale = context.provenanceMap.values.any { it.isStale }

        // D.1: Digital Usage During Planned Work Periods
        if (hasDigitalAccess) {
            val pendingOrOverdueTasks = context.productivity.taskSummary.pendingTasks +
                context.productivity.taskSummary.overdueTasks.map { it.task }
            val nonWorkAppUsage = digital.appUsages.filter { it.totalDurationMs >= 10 * 60 * 1000L }

            if (pendingOrOverdueTasks.isNotEmpty() && nonWorkAppUsage.isNotEmpty()) {
                val crossEvidence = pendingOrOverdueTasks.map {
                    EvidenceReference(it.id, EvidenceSourceType.EVENT) to it.updatedAt
                } + nonWorkAppUsage.map {
                    val timestamp = it.lastObservedAt ?: window.endTimeMs
                    EvidenceReference(it.packageName, EvidenceSourceType.OBSERVATION) to timestamp
                }

                buildCrossDomainPattern(
                    userId = context.userId,
                    type = PatternDiscoveryType.DIGITAL_USAGE_DURING_PLANNED_WORK,
                    rawEvidence = crossEvidence,
                    window = window,
                    zoneId = zoneId,
                    description = "Concurrent digital phone activity observed while productivity tasks remained pending or overdue.",
                    sourceNames = listOf("Tasks", "AppUsage"),
                    isSourceStale = isStale,
                    disclaimer = NON_CAUSAL_DISCLAIMER
                )?.let { out.add(it) }
            }
        }

        // D.2: App Usage Coinciding with Missed Commitments
        if (hasDigitalAccess) {
            val missed = context.productivity.commitmentSummary.missedCommitments
            val highUsageApps = digital.appUsages.filter { it.totalDurationMs >= 15 * 60 * 1000L }
            if (missed.isNotEmpty() && highUsageApps.isNotEmpty()) {
                val crossEvidence = missed.map {
                    EvidenceReference(it.id, EvidenceSourceType.EVENT) to it.committedAt
                } + highUsageApps.map {
                    val timestamp = it.lastObservedAt ?: window.endTimeMs
                    EvidenceReference(it.packageName, EvidenceSourceType.OBSERVATION) to timestamp
                }

                buildCrossDomainPattern(
                    userId = context.userId,
                    type = PatternDiscoveryType.APP_USAGE_COINCIDING_WITH_MISSED_COMMITMENTS,
                    rawEvidence = crossEvidence,
                    window = window,
                    zoneId = zoneId,
                    description = "App usage coincided temporally with missed commitment observation window.",
                    sourceNames = listOf("Commitments", "AppUsage"),
                    isSourceStale = isStale,
                    disclaimer = NON_CAUSAL_DISCLAIMER
                )?.let { out.add(it) }
            }
        }

        // D.3: Focus Activity Coinciding with Task Completion (bounded by effective protection end)
        val completedTasks = context.productivity.taskSummary.completedTasks
        val focusSessions = digital.focusActivity.recentSessions
        if (completedTasks.isNotEmpty() && focusSessions.isNotEmpty()) {
            val taskCoincidence = completedTasks.filter { task ->
                val taskTime = task.completedAt ?: task.updatedAt
                focusSessions.any { session ->
                    val effEnd = AppUsageIntervalAnalyzer.effectiveProtectionEndMs(session, currentTimeMs)
                        ?: return@any false
                    taskTime in (session.startedAt - 15 * 60 * 1000L)..effEnd
                }
            }

            if (taskCoincidence.isNotEmpty()) {
                val crossEvidence = taskCoincidence.map {
                    EvidenceReference(it.id, EvidenceSourceType.EVENT) to (it.completedAt ?: it.updatedAt)
                } + focusSessions.map {
                    EvidenceReference(it.id, EvidenceSourceType.OBSERVATION) to it.startedAt
                }

                buildCrossDomainPattern(
                    userId = context.userId,
                    type = PatternDiscoveryType.FOCUS_ACTIVITY_COINCIDING_WITH_TASK_COMPLETION,
                    rawEvidence = crossEvidence,
                    window = window,
                    zoneId = zoneId,
                    description = "Task completions coincided temporally with active focus protection periods.",
                    sourceNames = listOf("Tasks", "FocusProtection"),
                    isSourceStale = isStale,
                    disclaimer = FOCUS_NON_CAUSAL_DISCLAIMER
                )?.let { out.add(it) }
            }
        }

        // D.4: Usage Window Execution Outcome Correlation
        if (hasDigitalAccess && context.productivity.recentOutcomes.isNotEmpty() && digital.appUsages.isNotEmpty()) {
            val crossEvidence = context.productivity.recentOutcomes.map {
                EvidenceReference(it.id, EvidenceSourceType.EVENT) to it.completedAt
            } + digital.appUsages.map {
                val timestamp = it.lastObservedAt ?: window.endTimeMs
                EvidenceReference(it.packageName, EvidenceSourceType.OBSERVATION) to timestamp
            }

            buildCrossDomainPattern(
                userId = context.userId,
                type = PatternDiscoveryType.USAGE_WINDOW_EXECUTION_OUTCOME_CORRELATION,
                rawEvidence = crossEvidence,
                window = window,
                zoneId = zoneId,
                description = "Repeated temporal relationship observed between digital usage windows and execution outcomes.",
                sourceNames = listOf("Outcomes", "AppUsage"),
                isSourceStale = isStale,
                disclaimer = NON_CAUSAL_DISCLAIMER
            )?.let { out.add(it) }
        }
    }

    // =========================================================================
    // Lifecycle Transitions (Weakening / Inactivity)
    // =========================================================================

    private fun evaluateLifecycleTransitions(
        discovered: List<DiscoveredPattern>,
        existingPatterns: List<Pattern>,
        window: ObservationWindow,
        currentTimeMs: Long
    ): List<DiscoveredPattern> {
        val result = discovered.toMutableList()
        val discoveredFingerprints = discovered.map {
            "${it.userId}_${it.category.name}_${it.type.name}"
        }.toSet()

        for (existing in existingPatterns) {
            // Match against discovered patterns
            val matchingType = PatternDiscoveryType.values().find {
                existing.fingerprint.contains(it.name)
            } ?: continue

            val key = "${existing.userId}_${matchingType.category.name}_${matchingType.name}"
            if (key !in discoveredFingerprints) {
                // Not observed in current window!
                val timeSinceLast = currentTimeMs - existing.lastObservedAt
                if (timeSinceLast > INACTIVITY_THRESHOLD_MS || existing.status == PatternStatus.EXPIRED || existing.status == PatternStatus.INACTIVE) {
                    // Transition to INACTIVE
                    result.add(
                        DiscoveredPattern(
                            id = existing.id,
                            userId = existing.userId,
                            category = matchingType.category,
                            type = matchingType,
                            description = "${existing.description} (Inactive in observation window)",
                            observationWindow = window,
                            evidenceReferences = existing.evidenceReferences ?: emptyList(),
                            evidenceCount = existing.evidenceCount,
                            firstObservedAt = existing.firstObservedAt,
                            lastObservedAt = existing.lastObservedAt,
                            confidence = INACTIVE_CONFIDENCE,
                            lifecycleState = PatternLifecycleState.INACTIVE,
                            epistemicAuthority = EpistemicAuthority.DERIVED_CALCULATION,
                            sourceProvenance = listOf("HistoricalPatterns"),
                            metadata = mapOf("reason" to "inactivity_threshold_exceeded")
                        )
                    )
                } else if (existing.confidence >= 0.7f && existing.status == PatternStatus.ACTIVE) {
                    // Transition to WEAKENING: Previously active/established, but zero occurrences in recent window
                    val weakenedConfidence = maxOf(0.20f, existing.confidence * 0.60f)
                    result.add(
                        DiscoveredPattern(
                            id = existing.id,
                            userId = existing.userId,
                            category = matchingType.category,
                            type = matchingType,
                            description = "${existing.description} (Weakening: declining evidence)",
                            observationWindow = window,
                            evidenceReferences = existing.evidenceReferences ?: emptyList(),
                            evidenceCount = existing.evidenceCount,
                            firstObservedAt = existing.firstObservedAt,
                            lastObservedAt = existing.lastObservedAt,
                            confidence = weakenedConfidence,
                            lifecycleState = PatternLifecycleState.WEAKENING,
                            epistemicAuthority = EpistemicAuthority.DERIVED_CALCULATION,
                            sourceProvenance = listOf("HistoricalPatterns"),
                            metadata = mapOf("reason" to "declining_evidence")
                        )
                    )
                }
            }
        }

        return result
    }

    // =========================================================================
    // Evidence & Lifecycle Evaluation Helpers
    // =========================================================================

    private fun buildPatternIfEvidence(
        userId: String,
        category: PatternCategory,
        type: PatternDiscoveryType,
        rawEvidence: List<Pair<EvidenceReference, Long>>,
        window: ObservationWindow,
        zoneId: ZoneId,
        descriptionBuilder: (count: Int) -> String,
        sourceNames: List<String>,
        isSourceStale: Boolean = false,
        metadata: Map<String, String> = emptyMap()
    ): DiscoveredPattern? {
        if (rawEvidence.isEmpty()) return null

        // Deduplicate evidence items by identity
        val distinctEvidence = rawEvidence.distinctBy { it.first.sourceId to it.first.sourceType }
        if (distinctEvidence.isEmpty()) return null

        val (lifecycleState, rawConfidence) = evaluateLifecycleStateAndConfidence(distinctEvidence, zoneId)
        val finalConfidence = if (isSourceStale) minOf(rawConfidence, STALE_CONFIDENCE_CAP) else rawConfidence

        val firstObserved = distinctEvidence.minOf { it.second }
        val lastObserved = distinctEvidence.maxOf { it.second }

        val epistemicAuthority = when (category) {
            PatternCategory.DIGITAL_BEHAVIOUR, PatternCategory.FOCUS -> EpistemicAuthority.SYSTEM_RECORDED_OBSERVATION
            PatternCategory.CROSS_DOMAIN -> EpistemicAuthority.AI_HYPOTHESIS
            PatternCategory.PRODUCTIVITY -> EpistemicAuthority.DERIVED_CALCULATION
        }

        val enrichedMetadata = metadata.toMutableMap()
        if (isSourceStale) {
            enrichedMetadata["isSourceStale"] = "true"
        }

        val id = "pat_${category.name.lowercase().take(4)}_${type.name.lowercase().take(12)}_${userId.take(8)}"

        return DiscoveredPattern(
            id = id,
            userId = userId,
            category = category,
            type = type,
            description = descriptionBuilder(distinctEvidence.size),
            observationWindow = window,
            evidenceReferences = distinctEvidence.map { it.first },
            evidenceCount = distinctEvidence.size,
            firstObservedAt = firstObserved,
            lastObservedAt = lastObserved,
            confidence = finalConfidence,
            lifecycleState = lifecycleState,
            epistemicAuthority = epistemicAuthority,
            sourceProvenance = sourceNames,
            nonCausalDisclaimer = null,
            metadata = enrichedMetadata
        )
    }

    private fun buildCrossDomainPattern(
        userId: String,
        type: PatternDiscoveryType,
        rawEvidence: List<Pair<EvidenceReference, Long>>,
        window: ObservationWindow,
        zoneId: ZoneId,
        description: String,
        sourceNames: List<String>,
        isSourceStale: Boolean = false,
        disclaimer: String,
        metadata: Map<String, String> = emptyMap()
    ): DiscoveredPattern? {
        if (rawEvidence.isEmpty()) return null

        val distinctEvidence = rawEvidence.distinctBy { it.first.sourceId to it.first.sourceType }
        if (distinctEvidence.isEmpty()) return null

        val (lifecycleState, rawConfidence) = evaluateLifecycleStateAndConfidence(distinctEvidence, zoneId)
        // Cross-domain confidence is strictly capped below 1.0f (max 0.75f)
        val boundedConfidence = minOf(rawConfidence, 0.75f)
        val finalConfidence = if (isSourceStale) minOf(boundedConfidence, STALE_CONFIDENCE_CAP) else boundedConfidence

        val firstObserved = distinctEvidence.minOf { it.second }
        val lastObserved = distinctEvidence.maxOf { it.second }

        val enrichedMetadata = metadata.toMutableMap()
        if (isSourceStale) enrichedMetadata["isSourceStale"] = "true"

        val id = "pat_cross_${type.name.lowercase().take(12)}_${userId.take(8)}"

        return DiscoveredPattern(
            id = id,
            userId = userId,
            category = PatternCategory.CROSS_DOMAIN,
            type = type,
            description = description,
            observationWindow = window,
            evidenceReferences = distinctEvidence.map { it.first },
            evidenceCount = distinctEvidence.size,
            firstObservedAt = firstObserved,
            lastObservedAt = lastObserved,
            confidence = finalConfidence,
            lifecycleState = lifecycleState,
            epistemicAuthority = EpistemicAuthority.AI_HYPOTHESIS,
            sourceProvenance = sourceNames,
            nonCausalDisclaimer = disclaimer,
            metadata = enrichedMetadata
        )
    }

    /**
     * Determines pattern lifecycle state and conservative confidence from validated evidence.
     *
     * State Rules:
     * - N = 1 -> CANDIDATE (conservative confidence, never recurring).
     * - N = 2 or single calendar day -> EMERGING (evidence accumulating).
     * - N >= 3 AND >= 2 distinct calendar days -> ESTABLISHED (meets conservative sufficiency).
     */
    fun evaluateLifecycleStateAndConfidence(
        distinctEvidence: List<Pair<EvidenceReference, Long>>,
        zoneId: ZoneId
    ): Pair<PatternLifecycleState, Float> {
        val count = distinctEvidence.size
        if (count <= 1) {
            return PatternLifecycleState.CANDIDATE to CANDIDATE_CONFIDENCE
        }

        val validatedList = distinctEvidence.map { ValidatedEvidence(it.first, it.second) }
        val sufficiencyResult = evidenceSufficiencyEvaluator.evaluate(validatedList, zoneId)

        return if (sufficiencyResult is EvidenceSufficiencyResult.Sufficient) {
            PatternLifecycleState.ESTABLISHED to ESTABLISHED_CONFIDENCE
        } else {
            // N >= 2 but insufficient calendar days (or N = 2) -> EMERGING
            PatternLifecycleState.EMERGING to EMERGING_CONFIDENCE
        }
    }
}
