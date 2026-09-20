package com.sanket_satpute_20.ironmind.domain.engine

import com.sanket_satpute_20.ironmind.domain.ai.AIOutput
import com.sanket_satpute_20.ironmind.domain.ai.InterventionType
import com.sanket_satpute_20.ironmind.domain.common.Clock
import com.sanket_satpute_20.ironmind.domain.common.IdGenerator
import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.logging.IronLogger
import com.sanket_satpute_20.ironmind.domain.model.AutonomyCapability
import com.sanket_satpute_20.ironmind.domain.model.decision.CandidateAction
import com.sanket_satpute_20.ironmind.domain.model.decision.DecisionResult
import com.sanket_satpute_20.ironmind.domain.model.decision.EvaluationContext
import com.sanket_satpute_20.ironmind.domain.model.intervention.InterventionRecord
import com.sanket_satpute_20.ironmind.domain.model.intervention.InterventionResolutionReason
import com.sanket_satpute_20.ironmind.domain.model.intervention.InterventionState
import com.sanket_satpute_20.ironmind.domain.repository.InterventionRepository

class InterventionExecutionPipelineImpl(
    private val decisionEngine: DecisionEngine,
    private val interventionRepository: InterventionRepository,
    private val idGenerator: IdGenerator,
    private val clock: Clock,
    private val logger: IronLogger
) : InterventionExecutionPipeline {

    override suspend fun propose(
        userId: String,
        candidate: AIOutput.InterventionRecommendation
    ): Result<InterventionRecord, Exception> {
        val now = clock.currentTimeMillis()

        // 1. Create PROPOSED record
        val record = InterventionRecord(
            id = idGenerator.generateId(),
            userId = userId,
            type = candidate.interventionType,
            state = InterventionState.PROPOSED,
            title = candidate.recommendation,
            description = candidate.reason,
            createdAt = now,
            updatedAt = now
        )

        // If candidate is STAY_SILENT, immediately suppress
        if (candidate.interventionType == InterventionType.STAY_SILENT) {
            val suppressed = record.copy(state = InterventionState.SUPPRESSED, updatedAt = now)
            interventionRepository.save(suppressed)
            logTransition(suppressed)
            return Result.Success(suppressed)
        }

        // Save proposed
        interventionRepository.save(record)
        logTransition(record)

        // 2. Evaluate via DecisionEngine
        val action = CandidateAction(
            capability = AutonomyCapability.PROACTIVE_NOTIFICATIONS,
            isSafe = true,
            isReversible = true
        )
        val context = EvaluationContext(
            hasRequiredPermissions = true,
            isUserOverrideActive = false,
            isCooldownActive = false, // Sprint V3.8 will manage cooldowns
            isDuplicate = false
        )

        val decision = decisionEngine.evaluate(userId, action, context)

        // 3. Move to APPROVED or SUPPRESSED based on decision
        val newState = if (decision == DecisionResult.EXECUTE) {
            InterventionState.APPROVED
        } else {
            InterventionState.SUPPRESSED
        }

        val updatedRecord = record.copy(state = newState, updatedAt = clock.currentTimeMillis())
        val saveResult = interventionRepository.save(updatedRecord)

        return if (saveResult is Result.Success) {
            logTransition(updatedRecord)
            Result.Success(updatedRecord)
        } else {
            Result.Failure(Exception("Failed to update record to $newState"))
        }
    }

    override suspend fun trigger(id: String): Result<InterventionRecord, Exception> {
        return transitionState(id, InterventionState.TRIGGERED, listOf(InterventionState.APPROVED))
    }

    override suspend fun deliver(id: String): Result<InterventionRecord, Exception> {
        return transitionState(id, InterventionState.DELIVERED, listOf(InterventionState.TRIGGERED))
    }

    override suspend fun resolve(
        id: String,
        reason: InterventionResolutionReason
    ): Result<InterventionRecord, Exception> {
        val getResult = interventionRepository.getById(id)
        if (getResult is Result.Failure) return Result.Failure(getResult.error)
        val record = (getResult as Result.Success).data ?: return Result.Failure(Exception("Intervention not found: $id"))

        if (record.state == InterventionState.OUTCOME || record.state == InterventionState.FAILED) {
            return Result.Failure(Exception("Intervention already resolved: ${record.state}"))
        }

        val now = clock.currentTimeMillis()
        
        // FAILED state for technical/behavioral failure
        val finalState = when (reason) {
            InterventionResolutionReason.TECHNICAL_FAILURE,
            InterventionResolutionReason.BEHAVIORAL_FAILURE -> InterventionState.FAILED
            else -> InterventionState.OUTCOME // Ignored, dismissed, overridden, success -> OUTCOME
        }

        // Technically the roadmap says RESPONSE -> OUTCOME. We can jump to OUTCOME or FAILED directly as resolution.
        
        val updatedRecord = record.copy(
            state = finalState,
            resolutionReason = reason,
            updatedAt = now
        )

        val saveResult = interventionRepository.save(updatedRecord)
        return if (saveResult is Result.Success) {
            logTransition(updatedRecord)
            Result.Success(updatedRecord)
        } else {
            Result.Failure(Exception("Failed to resolve intervention $id"))
        }
    }

    private suspend fun transitionState(
        id: String,
        newState: InterventionState,
        allowedPreviousStates: List<InterventionState>
    ): Result<InterventionRecord, Exception> {
        val getResult = interventionRepository.getById(id)
        if (getResult is Result.Failure) return Result.Failure(getResult.error)
        val record = (getResult as Result.Success).data ?: return Result.Failure(Exception("Intervention not found: $id"))

        if (record.state !in allowedPreviousStates) {
            return Result.Failure(Exception("Invalid state transition from ${record.state} to $newState"))
        }

        val updatedRecord = record.copy(state = newState, updatedAt = clock.currentTimeMillis())
        val saveResult = interventionRepository.save(updatedRecord)

        return if (saveResult is Result.Success) {
            logTransition(updatedRecord)
            Result.Success(updatedRecord)
        } else {
            Result.Failure(Exception("Failed to update state to $newState"))
        }
    }

    private fun logTransition(record: InterventionRecord) {
        logger.logLifecycle("Intervention", "STATE_CHANGED", mapOf("id" to record.id, "state" to record.state.name))
    }
}
