package com.sanket_satpute_20.ironmind.domain.engine

import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.logging.IronLogger
import com.sanket_satpute_20.ironmind.domain.model.AutonomyLevel
import com.sanket_satpute_20.ironmind.domain.model.decision.CandidateAction
import com.sanket_satpute_20.ironmind.domain.model.decision.DecisionResult
import com.sanket_satpute_20.ironmind.domain.model.decision.EvaluationContext
import com.sanket_satpute_20.ironmind.domain.repository.AutonomySettingsRepository
import com.sanket_satpute_20.ironmind.domain.repository.DecisionRecordRepository
import com.sanket_satpute_20.ironmind.domain.model.decision.DecisionRecord
import java.util.UUID

class DecisionEngineImpl(
    private val autonomySettingsRepository: AutonomySettingsRepository,
    private val decisionRecordRepository: DecisionRecordRepository,
    private val logger: IronLogger
) : DecisionEngine {

    override suspend fun evaluate(
        userId: String,
        candidate: CandidateAction,
        context: EvaluationContext
    ): DecisionResult {
        // 1. Hard blocks: Overrides, Cooldowns, Duplicates
        if (context.isUserOverrideActive) {
            return recordAndReturnEarly(candidate, DecisionResult.STAY_SILENT, "USER_OVERRIDE_ACTIVE")
        }
        
        if (context.isCooldownActive) {
            return recordAndReturnEarly(candidate, DecisionResult.STAY_SILENT, "COOLDOWN_ACTIVE")
        }
        
        if (context.isDuplicate) {
            return recordAndReturnEarly(candidate, DecisionResult.STAY_SILENT, "DUPLICATE_ACTION")
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

        val resultToLogAndSave = finalResult

        // 3. Persist Decision Record
        val record = DecisionRecord(
            id = UUID.randomUUID().toString(),
            timestamp = System.currentTimeMillis(),
            capability = candidate.capability,
            action = "Evaluated Action", // Or candidate.action if we had one
            autonomyLevel = level,
            reasoning = "POLICY_EVALUATED_LEVEL_${level.name}",
            result = resultToLogAndSave
        )
        
        // This runs in place, assuming it returns fast or in CoroutineScope
        decisionRecordRepository.saveDecision(record)

        logDecision(candidate, resultToLogAndSave, "POLICY_EVALUATED_LEVEL_${level.name}")
        return resultToLogAndSave
    }

    private suspend fun recordAndReturnEarly(
        candidate: CandidateAction, 
        result: DecisionResult, 
        reason: String
    ): DecisionResult {
        val record = DecisionRecord(
            id = UUID.randomUUID().toString(),
            timestamp = System.currentTimeMillis(),
            capability = candidate.capability,
            action = "Evaluated Action",
            autonomyLevel = AutonomyLevel.OFF, // Default when failing early
            reasoning = reason,
            result = result
        )
        decisionRecordRepository.saveDecision(record)
        logDecision(candidate, result, reason)
        return result
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
