package com.sanket_satpute_20.ironmind.domain.usecase.pattern

import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.logging.IronLogger
import com.sanket_satpute_20.ironmind.domain.model.pattern.PatternCandidate
import java.time.ZoneId

class EvaluatePatternCandidatesUseCase(
    private val evaluatePatternCandidateUseCase: EvaluatePatternCandidateUseCase,
    private val logger: IronLogger
) {
    suspend operator fun invoke(
        userId: String,
        candidates: List<PatternCandidate>,
        zoneId: ZoneId
    ): Result<Unit, Exception> {
        if (candidates.isEmpty()) {
            return Result.Success(Unit)
        }

        var expectedRejections = 0
        var systemFailures = 0
        var accepted = 0

        try {
            for (candidate in candidates) {
                when (evaluatePatternCandidateUseCase(userId = userId, candidate = candidate, zoneId = zoneId)) {
                    is EvaluatePatternCandidateResult.ExpectedRejection -> {
                        expectedRejections++
                    }
                    is EvaluatePatternCandidateResult.SystemFailure -> {
                        systemFailures++
                    }
                    is EvaluatePatternCandidateResult.Accepted -> {
                        accepted++
                    }
                }
            }

            logger.logLifecycle(
                component = "EvaluatePatternCandidatesUseCase",
                event = "batch_completed",
                parameters = mapOf(
                    "processedCount" to candidates.size.toString(),
                    "acceptedCount" to accepted.toString(),
                    "rejectionCount" to expectedRejections.toString(),
                    "systemFailureCount" to systemFailures.toString()
                )
            )

            return Result.Success(Unit)
        } catch (e: Exception) {
            logger.logLifecycle(
                component = "EvaluatePatternCandidatesUseCase",
                event = "batch_failed",
                parameters = mapOf(
                    "processedCount" to candidates.size.toString(),
                    "error" to (e.message ?: "Unknown orchestration error")
                )
            )
            return Result.Failure(e)
        }
    }
}
