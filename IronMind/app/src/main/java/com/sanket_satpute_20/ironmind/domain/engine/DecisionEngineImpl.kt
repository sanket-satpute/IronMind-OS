package com.sanket_satpute_20.ironmind.domain.engine

import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.logging.IronLogger
import com.sanket_satpute_20.ironmind.domain.model.AutonomyLevel
import com.sanket_satpute_20.ironmind.domain.model.decision.CandidateAction
import com.sanket_satpute_20.ironmind.domain.model.decision.DecisionResult
import com.sanket_satpute_20.ironmind.domain.model.decision.EvaluationContext
import com.sanket_satpute_20.ironmind.domain.repository.AutonomySettingsRepository

class DecisionEngineImpl(
    private val autonomySettingsRepository: AutonomySettingsRepository,
    private val logger: IronLogger
) : DecisionEngine {

    override suspend fun evaluate(
        userId: String,
        candidate: CandidateAction,
        context: EvaluationContext
    ): DecisionResult {
        // 1. Hard blocks: Overrides, Cooldowns, Duplicates
        if (context.isUserOverrideActive) {
            logDecision(candidate, DecisionResult.STAY_SILENT, "USER_OVERRIDE_ACTIVE")
            return DecisionResult.STAY_SILENT
        }
        
        if (context.isCooldownActive) {
            logDecision(candidate, DecisionResult.STAY_SILENT, "COOLDOWN_ACTIVE")
            return DecisionResult.STAY_SILENT
        }
        
        if (context.isDuplicate) {
            logDecision(candidate, DecisionResult.STAY_SILENT, "DUPLICATE_ACTION")
            return DecisionResult.STAY_SILENT
        }

        // 2. Autonomy Settings Evaluation
        val settingsResult = autonomySettingsRepository.getSettings(userId)
        val level = settingsResult.getOrNull()?.getLevel(candidate.capability) ?: AutonomyLevel.SUGGEST_ONLY


        val finalResult = when (level) {
            AutonomyLevel.OFF -> DecisionResult.STAY_SILENT
            AutonomyLevel.SUGGEST_ONLY -> DecisionResult.SUGGEST
            AutonomyLevel.ASK_BEFORE_ACTION -> DecisionResult.ASK_USER
            AutonomyLevel.FULL_AUTO -> {
                // In FULL_AUTO, we must double-check safety, reversibility, and permissions
                if (!candidate.isSafe || !candidate.isReversible) {
                    DecisionResult.ASK_USER
                } else if (!context.hasRequiredPermissions) {
                    DecisionResult.ASK_USER
                } else {
                    DecisionResult.EXECUTE
                }
            }
        }

        logDecision(candidate, finalResult, "POLICY_EVALUATED_LEVEL_${level.name}")
        return finalResult
    }

    private fun logDecision(candidate: CandidateAction, result: DecisionResult, reason: String) {
        logger.logLifecycle(
            component = "Decision",
            event = "EVALUATED",
            parameters = mapOf(
                "capability" to candidate.capability.name,
                "result" to result.name,
                "reason" to reason
            )
        )
    }
}
