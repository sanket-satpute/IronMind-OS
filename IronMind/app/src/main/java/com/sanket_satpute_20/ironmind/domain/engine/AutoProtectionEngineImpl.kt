package com.sanket_satpute_20.ironmind.domain.engine

import com.sanket_satpute_20.ironmind.domain.common.Clock
import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.logging.IronLogger
import com.sanket_satpute_20.ironmind.domain.model.AutonomyCapability
import com.sanket_satpute_20.ironmind.domain.model.EntitySource
import com.sanket_satpute_20.ironmind.domain.model.decision.CandidateAction
import com.sanket_satpute_20.ironmind.domain.model.decision.DecisionResult
import com.sanket_satpute_20.ironmind.domain.model.decision.EvaluationContext
import com.sanket_satpute_20.ironmind.domain.model.protection.ProtectionCandidate
import com.sanket_satpute_20.ironmind.domain.model.protection.ProtectionResult
import com.sanket_satpute_20.ironmind.domain.usecase.protection.StartProtectionSessionUseCase

class AutoProtectionEngineImpl(
    private val decisionEngine: DecisionEngine,
    private val startProtectionSessionUseCase: StartProtectionSessionUseCase,
    private val clock: Clock,
    private val logger: IronLogger
) : AutoProtectionEngine {

    companion object {
        const val MAX_DURATION_MS = 4 * 60 * 60 * 1000L // 4 hours
    }

    override suspend fun protect(userId: String, candidate: ProtectionCandidate): ProtectionResult {
        if (candidate.targetPackages.isEmpty()) {
            logger.logLifecycle("Protection", "REJECTED_CONFLICT", mapOf("reason" to "no_targets_specified"))
            return ProtectionResult.Conflict("No targets specified for protection.")
        }

        if (candidate.durationMs <= 0 || candidate.durationMs > MAX_DURATION_MS) {
            logger.logLifecycle("Protection", "REJECTED_CONFLICT", mapOf("reason" to "invalid_duration"))
            return ProtectionResult.Conflict("Duration must be between 1 and 4 hours.")
        }

        val candidateAction = CandidateAction(
            capability = AutonomyCapability.PROTECTION,
            isSafe = true,
            isReversible = true
        )

        val context = EvaluationContext(
            hasRequiredPermissions = true,
            isUserOverrideActive = false,
            isCooldownActive = false,
            isDuplicate = false
        )

        val decision = decisionEngine.evaluate(userId, candidateAction, context)

        return when (decision) {
            DecisionResult.EXECUTE -> {
                val scheduledEndAt = clock.currentTimeMillis() + candidate.durationMs
                
                val result = startProtectionSessionUseCase(
                    userId = userId,
                    ruleId = candidate.ruleId,
                    commitmentId = candidate.commitmentId,
                    taskId = candidate.taskId,
                    scheduledEndAt = scheduledEndAt,
                    overrideAllowed = true, // Must be reversible
                    targetPackages = candidate.targetPackages,
                    source = EntitySource.AI
                )

                if (result is Result.Success) {
                    logger.logLifecycle("Protection", "AUTO_PROTECTED", mapOf("sessionId" to result.data.id))
                    ProtectionResult.Protected(result.data)
                } else {
                    val errorMsg = (result as Result.Failure).error.message ?: "Failed to start protection session"
                    logger.logLifecycle("Protection", "REJECTED_ERROR", mapOf("reason" to errorMsg))
                    ProtectionResult.Conflict(errorMsg)
                }
            }
            DecisionResult.ASK_USER -> {
                logger.logLifecycle("Protection", "REQUIRES_PERMISSION", mapOf("title" to candidate.title))
                ProtectionResult.RequiresPermission
            }
            DecisionResult.SUGGEST, DecisionResult.STAY_SILENT -> {
                logger.logLifecycle("Protection", "NOT_ALLOWED", mapOf("decision" to decision.name))
                ProtectionResult.NotAllowed
            }
        }
    }
}
