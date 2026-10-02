package com.sanket_satpute_20.ironmind.domain.engine

import com.sanket_satpute_20.ironmind.domain.ai.AIOutput
import com.sanket_satpute_20.ironmind.domain.common.Clock
import com.sanket_satpute_20.ironmind.domain.common.IdGenerator
import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.Reflection
import com.sanket_satpute_20.ironmind.domain.model.barrier.BarrierConfirmationState
import com.sanket_satpute_20.ironmind.domain.model.barrier.BarrierHypothesis
import com.sanket_satpute_20.ironmind.domain.model.barrier.BarrierStatus
import com.sanket_satpute_20.ironmind.domain.repository.BarrierRepository
import com.sanket_satpute_20.ironmind.domain.usecase.ai.UnderstandBarriersUseCase

/**
 * Sprint V2.9.2: Dedicated orchestrator for Barrier Understanding.
 * Connects AI extraction to Domain state.
 */
class BarrierUnderstandingOrchestrator(
    private val understandBarriersUseCase: UnderstandBarriersUseCase,
    private val barrierRepository: BarrierRepository,
    private val idGenerator: IdGenerator,
    private val clock: Clock
) {

    suspend fun processReflection(reflection: Reflection): Result<Unit, Exception> {
        // UnderstandBarriersUseCase accepts raw content and userId
        val aiResult = understandBarriersUseCase(reflection.content, reflection.userId)
        
        if (aiResult is Result.Failure) {
            // Failure is returned directly. Handled by caller to NOT break pattern processing.
            return Result.Failure(aiResult.error)
        }

        val aiOutput = (aiResult as Result.Success).data
        if (aiOutput !is AIOutput.BarrierOutput) {
            return Result.Failure(IllegalArgumentException("Expected BarrierOutput but got ${aiOutput.type}"))
        }

        val now = clock.currentTimeMillis()
        var hasFailures = false
        var firstError: Exception? = null

        for (candidate in aiOutput.proposedBarriers) {
            val hypothesis = BarrierHypothesis(
                id = idGenerator.generateId(),
                userId = reflection.userId,
                category = candidate.category,
                description = candidate.description,
                confirmationState = BarrierConfirmationState.UNCONFIRMED,
                status = BarrierStatus.ACTIVE,
                sourceReflectionId = reflection.id,
                firstObservedAt = now,
                lastObservedAt = now
            )
            
            val saveResult = barrierRepository.saveBarrier(hypothesis)
            if (saveResult is Result.Failure) {
                hasFailures = true
                if (firstError == null) {
                    firstError = saveResult.error
                }
            }
        }

        return if (hasFailures) {
            Result.Failure(firstError ?: Exception("Unknown error saving barriers"))
        } else {
            Result.Success(Unit)
        }
    }
}
