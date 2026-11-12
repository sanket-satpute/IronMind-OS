package com.sanket_satpute_20.ironmind.domain.engine

import com.sanket_satpute_20.ironmind.domain.common.Clock
import com.sanket_satpute_20.ironmind.domain.model.EventType
import com.sanket_satpute_20.ironmind.domain.model.ProtectionSessionStatus
import com.sanket_satpute_20.ironmind.domain.model.context.AppUsageInterval
import com.sanket_satpute_20.ironmind.domain.model.context.DigitalBehaviourContext
import com.sanket_satpute_20.ironmind.domain.model.context.EpistemicAuthority
import com.sanket_satpute_20.ironmind.domain.model.context.FocusOverlapAvailability
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

    private data class TimeBucketSlice(
        val observationId: String,
        val packageName: String,
        val startTimeMs: Long,
        val endTimeMs: Long
    )

    private data class TimeBucketStats(
        val name: String,
        val priorityOrder: Int,
        val durationMs: Long,
        val distinctEvidence: List<Pair<EvidenceReference, Long>>
    )

    private fun isDigitalUsageReadable(digital: DigitalBehaviourContext): Boolean {
        if (digital.permissionStatus != UsagePermissionStatus.GRANTED) return false
        return when (digital.availability) {
            SourceAvailability.PERMISSION_DENIED,
            SourceAvailability.PERMISSION_NOT_REQUESTED,
            SourceAvailability.UNAVAILABLE_HARDWARE,
            SourceAvailability.EMPTY_NO_DATA -> false
            SourceAvailability.AVAILABLE,
            SourceAvailability.PARTIAL_HISTORY,
            SourceAvailability.STALE_DATA -> true
        }
    }

    private fun resolveEffectiveUsageAvailability(
        context: UnifiedContext,
        digital: DigitalBehaviourContext
    ): SourceAvailability {
        val provenanceAvailability = context.provenanceMap["AppUsage"]?.availability
        val temporalAvailability = digital.usageTemporalSummary.provenance.usageSourceAvailability
        return when {
            digital.availability != SourceAvailability.AVAILABLE -> digital.availability
            provenanceAvailability == SourceAvailability.PARTIAL_HISTORY ||
                provenanceAvailability == SourceAvailability.STALE_DATA -> provenanceAvailability
            temporalAvailability == SourceAvailability.PARTIAL_HISTORY ||
                temporalAvailability == SourceAvailability.STALE_DATA -> temporalAvailability
            else -> SourceAvailability.AVAILABLE
        }
    }

    private fun isFocusOverlapUnavailable(
        context: UnifiedContext,
        digital: DigitalBehaviourContext
    ): Boolean {
        val focusProvenance = context.provenanceMap["FocusProtection"]
        val temporal = digital.usageTemporalSummary
        val focusOverlap = temporal.focusOverlap
        return focusProvenance?.availability == SourceAvailability.UNAVAILABLE_HARDWARE ||
            !temporal.provenance.isProtectionHistoryAvailable ||
            (temporal.hasUsageData && focusOverlap.availability == FocusOverlapAvailability.UNAVAILABLE) ||
            (focusOverlap.availability == FocusOverlapAvailability.UNAVAILABLE && focusOverlap.notes != null)
    }

    private fun isFocusProtectionUnavailable(context: UnifiedContext): Boolean {
        val focusProvenance = context.provenanceMap["FocusProtection"]
        return focusProvenance?.availability == SourceAvailability.UNAVAILABLE_HARDWARE ||
            !context.digitalBehaviour.usageTemporalSummary.provenance.isProtectionHistoryAvailable
    }

    private fun buildUsageProvenanceMetadata(
        context: UnifiedContext,
        digital: DigitalBehaviourContext,
        effectiveAvailability: SourceAvailability,
        evidenceBasis: String
    ): Map<String, String> = buildMap {
        put("usageAvailability", effectiveAvailability.name)
        put("evidenceBasis", evidenceBasis)
        if (effectiveAvailability == SourceAvailability.PARTIAL_HISTORY) {
            put("isPartialHistory", "true")
        }
        val temporal = digital.usageTemporalSummary
        if (temporal.provenance.isTruncated) {
            put("isUsageTruncated", "true")
        }
        if (temporal.provenance.isLookbackClamped) {
            put("isLookbackClamped", "true")
        }
        when {
            isFocusOverlapUnavailable(context, digital) ->
                put("focusOverlapAvailability", FocusOverlapAvailability.UNAVAILABLE.name)
            temporal.hasUsageData || temporal.focusOverlap.availability != FocusOverlapAvailability.UNAVAILABLE ->
                put("focusOverlapAvailability", temporal.focusOverlap.availability.name)
        }
    }

    /**
     * Defensively enforces user isolation, requested-window clipping, and deterministic
     * deduplication by both source observation ID and semantic `(packageName, rawStart, rawEnd)`
     * so duplicate interval representations cannot inflate sample counts or duration totals.
     */
    private fun normalizeUsageIntervals(
        userId: String,
        rawIntervals: List<AppUsageInterval>,
        window: ObservationWindow
    ): List<AppUsageInterval> {
        if (rawIntervals.isEmpty()) return emptyList()
        val hasWindowBounds = window.endTimeMs > window.startTimeMs
        val effectiveWindowStart = if (hasWindowBounds) window.startTimeMs else Long.MIN_VALUE
        val effectiveWindowEnd = if (hasWindowBounds) window.endTimeMs else Long.MAX_VALUE

        val sorted = rawIntervals
            .asSequence()
            .filter {
                it.userId == userId &&
                    it.observationId.isNotBlank() &&
                    it.packageName.isNotBlank() &&
                    it.endTimeMs > it.startTimeMs &&
                    it.durationMs > 0L
            }
            .sortedWith(
                compareBy<AppUsageInterval> { it.startTimeMs }
                    .thenBy { it.endTimeMs }
                    .thenBy { it.packageName }
                    .thenBy { it.observationId }
            )
            .toList()

        val seenObservationIds = HashSet<String>()
        val seenSemantic = HashSet<Triple<String, Long, Long>>()
        val normalized = mutableListOf<AppUsageInterval>()

        for (interval in sorted) {
            val obsId = interval.observationId.trim()
            val pkg = interval.packageName.trim()
            if (!seenObservationIds.add(obsId)) continue

            val semanticStart = if (interval.rawEndTimeMs > interval.rawStartTimeMs) interval.rawStartTimeMs else interval.startTimeMs
            val semanticEnd = if (interval.rawEndTimeMs > interval.rawStartTimeMs) interval.rawEndTimeMs else interval.endTimeMs
            if (!seenSemantic.add(Triple(pkg, semanticStart, semanticEnd))) continue

            val clippedStart = maxOf(interval.startTimeMs, effectiveWindowStart)
            val clippedEnd = minOf(interval.endTimeMs, effectiveWindowEnd)
            if (clippedEnd <= clippedStart) continue

            normalized.add(
                interval.copy(
                    observationId = obsId,
                    packageName = pkg,
                    startTimeMs = clippedStart,
                    endTimeMs = clippedEnd,
                    durationMs = clippedEnd - clippedStart,
                    isClippedToWindow = interval.isClippedToWindow ||
                        clippedStart != interval.startTimeMs ||
                        clippedEnd != interval.endTimeMs
                )
            )
        }
        return normalized
    }

    private fun timeOfDayBucketForHour(hour: Int): String = when (hour) {
        in 6..11 -> "Morning"
        in 12..16 -> "Afternoon"
        in 17..21 -> "Evening"
        else -> "Night"
    }

    private fun nextTimeOfDayBoundaryMs(instantMs: Long, zoneId: ZoneId): Long {
        val zdt = Instant.ofEpochMilli(instantMs).atZone(zoneId)
        val date = zdt.toLocalDate()
        val nextZdt = when (zdt.hour) {
            in 0..5 -> date.atTime(6, 0).atZone(zoneId)
            in 6..11 -> date.atTime(12, 0).atZone(zoneId)
            in 12..16 -> date.atTime(17, 0).atZone(zoneId)
            in 17..21 -> date.atTime(22, 0).atZone(zoneId)
            else -> date.plusDays(1).atTime(0, 0).atZone(zoneId)
        }
        val candidate = nextZdt.toInstant().toEpochMilli()
        return if (candidate > instantMs) candidate else instantMs + 3_600_000L
    }

    private fun computeTimeOfDayBuckets(
        intervals: List<AppUsageInterval>,
        zoneId: ZoneId
    ): Map<String, TimeBucketStats> {
        val bucketSlices = linkedMapOf<String, MutableList<TimeBucketSlice>>(
            "Morning" to mutableListOf(),
            "Afternoon" to mutableListOf(),
            "Evening" to mutableListOf(),
            "Night" to mutableListOf()
        )
        for (interval in intervals) {
            var cursor = interval.startTimeMs
            while (cursor < interval.endTimeMs) {
                val hour = Instant.ofEpochMilli(cursor).atZone(zoneId).hour
                val bucket = timeOfDayBucketForHour(hour)
                val sliceEnd = minOf(interval.endTimeMs, nextTimeOfDayBoundaryMs(cursor, zoneId))
                if (sliceEnd > cursor) {
                    bucketSlices.getValue(bucket).add(
                        TimeBucketSlice(
                            observationId = interval.observationId,
                            packageName = interval.packageName,
                            startTimeMs = cursor,
                            endTimeMs = sliceEnd
                        )
                    )
                }
                cursor = sliceEnd
            }
        }
        val order = listOf("Morning", "Afternoon", "Evening", "Night")
        return order.mapIndexed { idx, bucketName ->
            val slices = bucketSlices.getValue(bucketName)
            val durationMs = AppUsageIntervalAnalyzer.unionDurationMs(slices.map { it.startTimeMs to it.endTimeMs })
            val distinctEvidence = slices
                .map { EvidenceReference(it.observationId, EvidenceSourceType.OBSERVATION) to it.startTimeMs }
                .distinctBy { it.first.sourceId to it.first.sourceType }
            bucketName to TimeBucketStats(
                name = bucketName,
                priorityOrder = idx,
                durationMs = durationMs,
                distinctEvidence = distinctEvidence
            )
        }.toMap()
    }

    private fun evaluateDigitalBehaviourPatterns(
        context: UnifiedContext,
        window: ObservationWindow,
        zoneId: ZoneId,
        currentTimeMs: Long,
        out: MutableList<DiscoveredPattern>
    ) {
        val digital = context.digitalBehaviour

        // ZERO FALSE CLAIMS: When permission is denied, unavailable, or empty, emit ZERO usage patterns.
        if (!isDigitalUsageReadable(digital)) {
            return
        }

        val effectiveAvailability = resolveEffectiveUsageAvailability(context, digital)
        val isStale = context.provenanceMap["AppUsage"]?.isStale == true ||
            effectiveAvailability == SourceAvailability.STALE_DATA

        val rawIntervals = digital.usageIntervals
        if (rawIntervals.isNotEmpty()) {
            val normalizedIntervals = normalizeUsageIntervals(context.userId, rawIntervals, window)
            if (normalizedIntervals.isEmpty()) {
                return
            }
            evaluateIntervalBasedDigitalBehaviourPatterns(
                context = context,
                digital = digital,
                intervals = normalizedIntervals,
                effectiveAvailability = effectiveAvailability,
                isStale = isStale,
                window = window,
                zoneId = zoneId,
                currentTimeMs = currentTimeMs,
                out = out
            )
            return
        }

        val appUsages = digital.appUsages
        if (appUsages.isEmpty()) {
            return
        }
        evaluateLegacySummaryDigitalBehaviourPatterns(
            context = context,
            digital = digital,
            effectiveAvailability = effectiveAvailability,
            isStale = isStale,
            window = window,
            zoneId = zoneId,
            currentTimeMs = currentTimeMs,
            out = out
        )
    }

    private fun evaluateIntervalBasedDigitalBehaviourPatterns(
        context: UnifiedContext,
        digital: DigitalBehaviourContext,
        intervals: List<AppUsageInterval>,
        effectiveAvailability: SourceAvailability,
        isStale: Boolean,
        window: ObservationWindow,
        zoneId: ZoneId,
        currentTimeMs: Long,
        out: MutableList<DiscoveredPattern>
    ) {
        val usageMeta = buildUsageProvenanceMetadata(context, digital, effectiveAvailability, "usage_intervals")
        val partialSuffix = if (effectiveAvailability == SourceAvailability.PARTIAL_HISTORY) {
            " (partial observation history)"
        } else {
            ""
        }

        val byPackage = intervals.groupBy { it.packageName }
        val packageDurations = byPackage.mapValues { (_, pkgIntervals) ->
            AppUsageIntervalAnalyzer.unionDurationMs(pkgIntervals.map { it.startTimeMs to it.endTimeMs })
        }

        // B.1: Frequently Used Applications (>= 15 minutes unioned duration OR >= 3 distinct intervals)
        val frequentApps = byPackage.entries
            .filter { (pkg, pkgIntervals) ->
                (packageDurations[pkg] ?: 0L) >= 15 * 60 * 1000L || pkgIntervals.size >= 3
            }
            .sortedWith(
                compareByDescending<Map.Entry<String, List<AppUsageInterval>>> { packageDurations[it.key] ?: 0L }
                    .thenBy { it.key }
            )
            .take(3)

        frequentApps.forEach { (pkg, pkgIntervals) ->
            val durationMs = packageDurations[pkg] ?: 0L
            val evidence = pkgIntervals.map {
                EvidenceReference(it.observationId, EvidenceSourceType.OBSERVATION) to it.startTimeMs
            }
            buildPatternIfEvidence(
                userId = context.userId,
                category = PatternCategory.DIGITAL_BEHAVIOUR,
                type = PatternDiscoveryType.FREQUENTLY_USED_APPLICATIONS,
                rawEvidence = evidence,
                window = window,
                zoneId = zoneId,
                descriptionBuilder = { count ->
                    "Frequent application usage observed for $pkg (${durationMs / 60000} mins across $count intervals)$partialSuffix."
                },
                sourceNames = listOf("AppUsage"),
                isSourceStale = isStale,
                metadata = usageMeta + mapOf(
                    "packageName" to pkg,
                    "durationMs" to durationMs.toString(),
                    "sessions" to pkgIntervals.size.toString()
                )
            )?.let { out.add(it) }
        }

        // B.2: High Usage Periods (Unioned total duration across all apps >= 60 minutes)
        val totalUsageDurationMs = AppUsageIntervalAnalyzer.unionDurationMs(
            intervals.map { it.startTimeMs to it.endTimeMs }
        )
        if (totalUsageDurationMs >= 60 * 60 * 1000L) {
            val highUsageEvidence = intervals.map {
                EvidenceReference(it.observationId, EvidenceSourceType.OBSERVATION) to it.startTimeMs
            }
            val distinctApps = byPackage.size
            buildPatternIfEvidence(
                userId = context.userId,
                category = PatternCategory.DIGITAL_BEHAVIOUR,
                type = PatternDiscoveryType.HIGH_USAGE_PERIODS,
                rawEvidence = highUsageEvidence,
                window = window,
                zoneId = zoneId,
                descriptionBuilder = { count ->
                    "High cumulative digital phone usage observed (${totalUsageDurationMs / 60000} minutes across $distinctApps apps and $count intervals)$partialSuffix."
                },
                sourceNames = listOf("AppUsage"),
                isSourceStale = isStale,
                metadata = usageMeta + mapOf(
                    "totalDurationMs" to totalUsageDurationMs.toString(),
                    "distinctApps" to distinctApps.toString()
                )
            )?.let { out.add(it) }
        }

        // B.3: Repeated App Usage Sessions (distinct interval count >= 5)
        byPackage.entries
            .filter { (_, pkgIntervals) -> pkgIntervals.size >= 5 }
            .sortedBy { it.key }
            .forEach { (pkg, pkgIntervals) ->
                val sessionEvidence = pkgIntervals.map {
                    EvidenceReference(it.observationId, EvidenceSourceType.OBSERVATION) to it.startTimeMs
                }
                buildPatternIfEvidence(
                    userId = context.userId,
                    category = PatternCategory.DIGITAL_BEHAVIOUR,
                    type = PatternDiscoveryType.REPEATED_APP_USAGE_SESSIONS,
                    rawEvidence = sessionEvidence,
                    window = window,
                    zoneId = zoneId,
                    descriptionBuilder = { count ->
                        "Repeated app usage intervals observed for $pkg ($count distinct intervals)$partialSuffix."
                    },
                    sourceNames = listOf("AppUsage"),
                    isSourceStale = isStale,
                    metadata = usageMeta + mapOf(
                        "packageName" to pkg,
                        "sessionCount" to pkgIntervals.size.toString()
                    )
                )?.let { out.add(it) }
            }

        // B.4: Usage Distribution Across Time Windows (interval-sliced, unioned, non-causal)
        val bucketStats = computeTimeOfDayBuckets(intervals, zoneId)
        val topUsageBin = bucketStats.values
            .filter { it.durationMs > 0L && it.distinctEvidence.isNotEmpty() }
            .maxWithOrNull(
                compareBy<TimeBucketStats> { it.durationMs }
                    .thenBy { it.distinctEvidence.size }
                    .thenByDescending { it.priorityOrder }
            )

        if (topUsageBin != null) {
            val bucketMetadata = buildMap {
                putAll(usageMeta)
                put("timeWindow", topUsageBin.name)
                put("topWindowDurationMs", topUsageBin.durationMs.toString())
                for (bucketName in listOf("Morning", "Afternoon", "Evening", "Night")) {
                    val stat = bucketStats.getValue(bucketName)
                    put("${bucketName.lowercase()}DurationMs", stat.durationMs.toString())
                    put("${bucketName.lowercase()}EvidenceCount", stat.distinctEvidence.size.toString())
                }
            }
            buildPatternIfEvidence(
                userId = context.userId,
                category = PatternCategory.DIGITAL_BEHAVIOUR,
                type = PatternDiscoveryType.USAGE_DISTRIBUTION_ACROSS_TIME_WINDOWS,
                rawEvidence = topUsageBin.distinctEvidence,
                window = window,
                zoneId = zoneId,
                descriptionBuilder = { count ->
                    "Digital phone usage concentrated during ${topUsageBin.name} hours (${topUsageBin.durationMs / 60000} mins across $count intervals)$partialSuffix. Temporal distribution only; does not imply causation."
                },
                sourceNames = listOf("AppUsage"),
                isSourceStale = isStale,
                metadata = bucketMetadata
            )?.let { out.add(it) }
        }

        // B.5: Usage During Scheduled Focus Periods (authoritative overlap / shared analyzer bounds)
        evaluateIntervalFocusOverlapPattern(
            context = context,
            digital = digital,
            intervals = intervals,
            usageMeta = usageMeta,
            partialSuffix = partialSuffix,
            isStale = isStale,
            window = window,
            zoneId = zoneId,
            currentTimeMs = currentTimeMs,
            out = out
        )
    }

    private fun evaluateIntervalFocusOverlapPattern(
        context: UnifiedContext,
        digital: DigitalBehaviourContext,
        intervals: List<AppUsageInterval>,
        usageMeta: Map<String, String>,
        partialSuffix: String,
        isStale: Boolean,
        window: ObservationWindow,
        zoneId: ZoneId,
        currentTimeMs: Long,
        out: MutableList<DiscoveredPattern>
    ) {
        if (isFocusOverlapUnavailable(context, digital)) {
            return
        }

        val temporal = digital.usageTemporalSummary
        val focusOverlap = temporal.focusOverlap

        if (focusOverlap.availability == FocusOverlapAvailability.EMPTY_NO_PROTECTION_SESSIONS) {
            return
        }
        if (focusOverlap.availability == FocusOverlapAvailability.AVAILABLE &&
            (focusOverlap.overlappingUsageDurationMs ?: 0L) == 0L
        ) {
            return
        }

        val effectiveStartMs = if (window.endTimeMs > window.startTimeMs && temporal.hasUsageData && temporal.effectiveStartTimeMs > window.startTimeMs) {
            temporal.effectiveStartTimeMs
        } else if (window.endTimeMs > window.startTimeMs) {
            window.startTimeMs
        } else {
            intervals.minOf { it.startTimeMs }
        }
        val effectiveEndMs = if (window.endTimeMs > window.startTimeMs) {
            window.endTimeMs
        } else {
            intervals.maxOf { it.endTimeMs }
        }

        val overlappingIntervals: List<AppUsageInterval>
        val overlapDurationMs: Long
        if (digital.focusActivity.recentSessions.isNotEmpty()) {
            val match = AppUsageIntervalAnalyzer.findProtectionOverlappingIntervals(
                userId = context.userId,
                intervals = intervals,
                protectionSessions = digital.focusActivity.recentSessions,
                windowStartMs = effectiveStartMs,
                windowEndMs = effectiveEndMs,
                nowMs = currentTimeMs
            )
            overlappingIntervals = match.overlappingIntervals
            overlapDurationMs = if (focusOverlap.availability == FocusOverlapAvailability.AVAILABLE &&
                focusOverlap.overlappingUsageDurationMs != null
            ) {
                focusOverlap.overlappingUsageDurationMs
            } else {
                match.overlappingDurationMs
            }
        } else if (focusOverlap.availability == FocusOverlapAvailability.AVAILABLE &&
            (focusOverlap.overlappingUsageDurationMs ?: 0L) > 0L
        ) {
            val overlappingPkgs = temporal.packageSummaries
                .filter { (it.overlapWithProtectionDurationMs ?: 0L) > 0L }
                .map { it.packageName }
                .toSet()
            overlappingIntervals = if (overlappingPkgs.isNotEmpty()) {
                intervals.filter { it.packageName in overlappingPkgs }
            } else {
                emptyList()
            }
            overlapDurationMs = focusOverlap.overlappingUsageDurationMs ?: 0L
        } else {
            overlappingIntervals = emptyList()
            overlapDurationMs = 0L
        }

        if (overlappingIntervals.isEmpty() || overlapDurationMs <= 0L) {
            return
        }

        val conflictEvidence = overlappingIntervals.map {
            EvidenceReference(it.observationId, EvidenceSourceType.OBSERVATION) to it.startTimeMs
        }
        val distinctApps = overlappingIntervals.map { it.packageName }.distinct().size
        buildPatternIfEvidence(
            userId = context.userId,
            category = PatternCategory.DIGITAL_BEHAVIOUR,
            type = PatternDiscoveryType.USAGE_DURING_SCHEDULED_FOCUS,
            rawEvidence = conflictEvidence,
            window = window,
            zoneId = zoneId,
            descriptionBuilder = { count ->
                "Digital activity overlapped temporally with focus protection periods (${overlapDurationMs / 60000} mins across $count intervals, $distinctApps apps)$partialSuffix. Temporal overlap only and does not imply causation or distraction."
            },
            sourceNames = listOf("AppUsage", "FocusProtection"),
            isSourceStale = isStale,
            metadata = usageMeta + mapOf(
                "focusOverlapAvailability" to FocusOverlapAvailability.AVAILABLE.name,
                "overlappingUsageDurationMs" to overlapDurationMs.toString(),
                "intersectingIntervalCount" to overlappingIntervals.size.toString(),
                "distinctAppCount" to distinctApps.toString()
            )
        )?.let { out.add(it) }
    }

    private fun evaluateLegacySummaryDigitalBehaviourPatterns(
        context: UnifiedContext,
        digital: DigitalBehaviourContext,
        effectiveAvailability: SourceAvailability,
        isStale: Boolean,
        window: ObservationWindow,
        zoneId: ZoneId,
        currentTimeMs: Long,
        out: MutableList<DiscoveredPattern>
    ) {
        val appUsages = digital.appUsages
        val usageMeta = buildUsageProvenanceMetadata(context, digital, effectiveAvailability, "app_usage_summary_fallback")

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
                metadata = usageMeta + mapOf("packageName" to app.packageName, "durationMs" to app.totalDurationMs.toString(), "sessions" to app.sessionCount.toString())
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
                metadata = usageMeta + mapOf("totalDurationMs" to totalUsageDurationMs.toString())
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
                metadata = usageMeta + mapOf("packageName" to app.packageName, "sessionCount" to app.sessionCount.toString())
            )?.let { out.add(it) }
        }

        // B.4: Usage Distribution Across Time Windows
        val appEvidence = appUsages.map {
            val timestamp = it.lastObservedAt ?: window.endTimeMs
            EvidenceReference(it.packageName, EvidenceSourceType.OBSERVATION) to timestamp
        }
        val usageBins = appEvidence.groupBy { pair ->
            val hour = ZonedDateTime.ofInstant(Instant.ofEpochMilli(pair.second), zoneId).hour
            timeOfDayBucketForHour(hour)
        }
        val topUsageBin = usageBins.maxByOrNull { it.value.size }
        if (topUsageBin != null && topUsageBin.value.isNotEmpty()) {
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
                metadata = usageMeta + mapOf("timeWindow" to topUsageBin.key)
            )?.let { out.add(it) }
        }

        // B.5: Usage During Scheduled Focus Periods (using verified effective session bounds)
        val focusSessions = digital.focusActivity.recentSessions
        if (!isFocusOverlapUnavailable(context, digital) && focusSessions.isNotEmpty()) {
            val overlappingUsage = appUsages.filter { usage ->
                val usageTime = usage.lastObservedAt ?: window.endTimeMs
                focusSessions.any { session ->
                    if (session.userId != context.userId) return@any false
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
                    isSourceStale = isStale,
                    metadata = usageMeta
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
        if (isFocusProtectionUnavailable(context)) {
            return
        }
        val focus = context.digitalBehaviour.focusActivity
        val recentSessions = focus.recentSessions.filter { it.userId == context.userId }
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
        val hasDigitalAccess = isDigitalUsageReadable(digital)
        val effectiveAvailability = resolveEffectiveUsageAvailability(context, digital)
        val isDigitalStale = context.provenanceMap["AppUsage"]?.isStale == true ||
            effectiveAvailability == SourceAvailability.STALE_DATA
        val isStale = context.provenanceMap.values.any { it.isStale } || isDigitalStale

        val rawIntervals = digital.usageIntervals
        val useIntervals = rawIntervals.isNotEmpty()
        val normalizedIntervals = if (hasDigitalAccess && useIntervals) {
            normalizeUsageIntervals(context.userId, rawIntervals, window)
        } else {
            emptyList()
        }
        val byPackage = normalizedIntervals.groupBy { it.packageName }
        val packageDurations = byPackage.mapValues { (_, pkgIntervals) ->
            AppUsageIntervalAnalyzer.unionDurationMs(pkgIntervals.map { it.startTimeMs to it.endTimeMs })
        }
        val usageMeta = buildUsageProvenanceMetadata(
            context = context,
            digital = digital,
            effectiveAvailability = effectiveAvailability,
            evidenceBasis = if (useIntervals) "usage_intervals" else "app_usage_summary_fallback"
        )

        // D.1: Digital Usage During Planned Work Periods
        if (hasDigitalAccess) {
            val pendingOrOverdueTasks = context.productivity.taskSummary.pendingTasks +
                context.productivity.taskSummary.overdueTasks.map { it.task }
            val digitalEvidence = if (useIntervals) {
                byPackage.entries
                    .filter { (pkg, _) -> (packageDurations[pkg] ?: 0L) >= 10 * 60 * 1000L }
                    .flatMap { it.value }
                    .map { EvidenceReference(it.observationId, EvidenceSourceType.OBSERVATION) to it.startTimeMs }
            } else {
                digital.appUsages
                    .filter { it.totalDurationMs >= 10 * 60 * 1000L }
                    .map {
                        val timestamp = it.lastObservedAt ?: window.endTimeMs
                        EvidenceReference(it.packageName, EvidenceSourceType.OBSERVATION) to timestamp
                    }
            }

            if (pendingOrOverdueTasks.isNotEmpty() && digitalEvidence.isNotEmpty()) {
                val crossEvidence = pendingOrOverdueTasks.map {
                    EvidenceReference(it.id, EvidenceSourceType.EVENT) to it.updatedAt
                } + digitalEvidence

                buildCrossDomainPattern(
                    userId = context.userId,
                    type = PatternDiscoveryType.DIGITAL_USAGE_DURING_PLANNED_WORK,
                    rawEvidence = crossEvidence,
                    window = window,
                    zoneId = zoneId,
                    description = "Concurrent digital phone activity observed while productivity tasks remained pending or overdue.",
                    sourceNames = listOf("Tasks", "AppUsage"),
                    isSourceStale = isStale,
                    disclaimer = NON_CAUSAL_DISCLAIMER,
                    metadata = usageMeta
                )?.let { out.add(it) }
            }
        }

        // D.2: App Usage Coinciding with Missed Commitments
        if (hasDigitalAccess) {
            val missed = context.productivity.commitmentSummary.missedCommitments
            val highUsageEvidence = if (useIntervals) {
                byPackage.entries
                    .filter { (pkg, _) -> (packageDurations[pkg] ?: 0L) >= 15 * 60 * 1000L }
                    .flatMap { it.value }
                    .map { EvidenceReference(it.observationId, EvidenceSourceType.OBSERVATION) to it.startTimeMs }
            } else {
                digital.appUsages
                    .filter { it.totalDurationMs >= 15 * 60 * 1000L }
                    .map {
                        val timestamp = it.lastObservedAt ?: window.endTimeMs
                        EvidenceReference(it.packageName, EvidenceSourceType.OBSERVATION) to timestamp
                    }
            }
            if (missed.isNotEmpty() && highUsageEvidence.isNotEmpty()) {
                val crossEvidence = missed.map {
                    EvidenceReference(it.id, EvidenceSourceType.EVENT) to it.committedAt
                } + highUsageEvidence

                buildCrossDomainPattern(
                    userId = context.userId,
                    type = PatternDiscoveryType.APP_USAGE_COINCIDING_WITH_MISSED_COMMITMENTS,
                    rawEvidence = crossEvidence,
                    window = window,
                    zoneId = zoneId,
                    description = "App usage coincided temporally with missed commitment observation window.",
                    sourceNames = listOf("Commitments", "AppUsage"),
                    isSourceStale = isStale,
                    disclaimer = NON_CAUSAL_DISCLAIMER,
                    metadata = usageMeta
                )?.let { out.add(it) }
            }
        }

        // D.3: Focus Activity Coinciding with Task Completion (bounded by effective protection end)
        val completedTasks = context.productivity.taskSummary.completedTasks
        val focusSessions = digital.focusActivity.recentSessions.filter { it.userId == context.userId }
        if (!isFocusProtectionUnavailable(context) && completedTasks.isNotEmpty() && focusSessions.isNotEmpty()) {
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
        val outcomeUsageEvidence = if (hasDigitalAccess) {
            if (useIntervals) {
                normalizedIntervals.map {
                    EvidenceReference(it.observationId, EvidenceSourceType.OBSERVATION) to it.startTimeMs
                }
            } else {
                digital.appUsages.map {
                    val timestamp = it.lastObservedAt ?: window.endTimeMs
                    EvidenceReference(it.packageName, EvidenceSourceType.OBSERVATION) to timestamp
                }
            }
        } else {
            emptyList()
        }
        if (hasDigitalAccess && context.productivity.recentOutcomes.isNotEmpty() && outcomeUsageEvidence.isNotEmpty()) {
            val crossEvidence = context.productivity.recentOutcomes.map {
                EvidenceReference(it.id, EvidenceSourceType.EVENT) to it.completedAt
            } + outcomeUsageEvidence

            buildCrossDomainPattern(
                userId = context.userId,
                type = PatternDiscoveryType.USAGE_WINDOW_EXECUTION_OUTCOME_CORRELATION,
                rawEvidence = crossEvidence,
                window = window,
                zoneId = zoneId,
                description = "Repeated temporal relationship observed between digital usage windows and execution outcomes.",
                sourceNames = listOf("Outcomes", "AppUsage"),
                isSourceStale = isStale,
                disclaimer = NON_CAUSAL_DISCLAIMER,
                metadata = usageMeta
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
