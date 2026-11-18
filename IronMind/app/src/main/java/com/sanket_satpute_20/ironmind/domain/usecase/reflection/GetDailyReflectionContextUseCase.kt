package com.sanket_satpute_20.ironmind.domain.usecase.reflection

import com.sanket_satpute_20.ironmind.domain.common.Clock
import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.engine.ContextEngine
import com.sanket_satpute_20.ironmind.domain.model.Commitment
import com.sanket_satpute_20.ironmind.domain.model.CommitmentStatus
import com.sanket_satpute_20.ironmind.domain.model.context.UsagePermissionStatus
import com.sanket_satpute_20.ironmind.domain.model.reflection.DigitalBehaviourSummary
import com.sanket_satpute_20.ironmind.domain.model.reflection.ExecutionTimingContextSummary
import com.sanket_satpute_20.ironmind.domain.usecase.commitment.GetCommitmentsForDateRangeUseCase
import com.sanket_satpute_20.ironmind.ui.screens.reflection.DaySummary

/**
 * Use case that builds the authoritative end-of-day behavioral context for Night Reflection,
 * aligning observed digital phone behavior and temporal execution rhythms with user commitments.
 *
 * Epistemic constraints:
 * - Commitments are USER_CONFIRMED_FACT
 * - App usage observations are SYSTEM_RECORDED_OBSERVATION
 * - Execution timings and averages are DERIVED_CALCULATION
 * - Missing permission != zero usage: If usage permission is not granted, total duration is null.
 * - Non-causal: Observational overlap is reported as temporal intersection, not causation.
 */
class GetDailyReflectionContextUseCase(
    private val getCommitmentsForDateRangeUseCase: GetCommitmentsForDateRangeUseCase,
    private val contextEngine: ContextEngine? = null,
    private val clock: Clock
) {
    suspend operator fun invoke(
        userId: String,
        startTimeMs: Long,
        endTimeMs: Long
    ): Result<DaySummary, Exception> {
        if (userId.isBlank()) {
            return Result.Failure(IllegalArgumentException("userId cannot be blank"))
        }
        if (startTimeMs >= endTimeMs) {
            return Result.Failure(IllegalArgumentException("startTimeMs must be less than endTimeMs"))
        }

        // 1. Fetch commitments for date range
        val commitmentsResult = getCommitmentsForDateRangeUseCase(userId, startTimeMs, endTimeMs)
        if (commitmentsResult is Result.Failure) {
            return Result.Failure(commitmentsResult.error)
        }
        val commitments = (commitmentsResult as Result.Success).data
        if (commitments.any { it.userId != userId }) {
            return Result.Failure(SecurityException("Commitments do not belong to user $userId"))
        }

        val completed = commitments.filter { it.status == CommitmentStatus.COMPLETED }
        val missed = commitments.filter { it.status == CommitmentStatus.MISSED }
        val postponed = commitments.filter { it.status == CommitmentStatus.POSTPONED }

        // 2. Fetch unified context if context engine is configured
        var digitalBehaviour: DigitalBehaviourSummary? = null
        var executionTiming: ExecutionTimingContextSummary? = null

        if (contextEngine != null) {
            try {
                val unifiedResult = contextEngine.getUnifiedContext(
                    userId = userId,
                    windowStartTimeMs = startTimeMs,
                    windowEndTimeMs = endTimeMs
                )
                if (unifiedResult is Result.Success) {
                    val unified = unifiedResult.data

                    // Map Digital Behaviour
                    val digital = unified.digitalBehaviour
                    val isGranted = digital.permissionStatus == UsagePermissionStatus.GRANTED
                    val topApp = digital.appUsages.firstOrNull()
                    val focusOverlap = digital.usageTemporalSummary.focusOverlap

                    digitalBehaviour = DigitalBehaviourSummary(
                        isUsagePermissionGranted = isGranted,
                        availability = digital.availability,
                        totalObservedUsageDurationMs = if (isGranted) digital.usageTemporalSummary.totalObservedDurationMs else null,
                        topUsedAppPackage = if (isGranted) topApp?.packageName else null,
                        topUsedAppName = if (isGranted) topApp?.appName ?: topApp?.packageName else null,
                        topUsedAppDurationMs = if (isGranted) topApp?.totalDurationMs else null,
                        focusProtectionOverlapMs = if (isGranted) focusOverlap.overlappingUsageDurationMs else null,
                        focusSessionCount = focusOverlap.protectionSessionCount,
                        notes = digital.notes ?: digital.usageTemporalSummary.provenance.notes
                    )

                    // Map Execution Timing
                    val timing = unified.productivity.executionTiming
                    val mostActive = timing.totalExecutionTimeDistribution.maxByOrNull { it.value }?.key
                    executionTiming = ExecutionTimingContextSummary(
                        hasSufficientData = timing.hasSufficientData,
                        startedTaskCount = timing.startDelaySamples.size,
                        completedTaskCount = timing.completionDelaySamples.size,
                        postponedCount = timing.forwardPostponementCount,
                        averageStartDelayMs = timing.averageStartDelayMs,
                        averageCompletionDelayMs = timing.averageCompletionDelayMs,
                        mostActiveBucket = mostActive,
                        notes = timing.provenance.notes
                    )
                }
            } catch (e: Exception) {
                // Graceful degradation: failing to obtain contextual telemetry does not block reflection
                digitalBehaviour = null
                executionTiming = null
            }
        }

        val summary = DaySummary(
            completed = completed,
            missed = missed,
            postponed = postponed,
            digitalBehaviour = digitalBehaviour,
            executionTiming = executionTiming
        )

        return Result.Success(summary)
    }
}
