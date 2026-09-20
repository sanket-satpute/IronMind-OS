package com.sanket_satpute_20.ironmind.domain.usecase.pattern

import com.sanket_satpute_20.ironmind.domain.ai.AIOutput
import com.sanket_satpute_20.ironmind.domain.ai.AIRequest
import com.sanket_satpute_20.ironmind.domain.ai.AIRequestType
import com.sanket_satpute_20.ironmind.domain.ai.IronMindAI
import com.sanket_satpute_20.ironmind.domain.ai.isValid
import com.sanket_satpute_20.ironmind.domain.common.Clock
import com.sanket_satpute_20.ironmind.domain.common.IdGenerator
import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.MemoryConfirmationState
import com.sanket_satpute_20.ironmind.domain.model.intervention.InterventionResolutionReason
import com.sanket_satpute_20.ironmind.domain.model.pattern.Pattern
import com.sanket_satpute_20.ironmind.domain.model.pattern.PatternStatus
import com.sanket_satpute_20.ironmind.domain.model.pattern.PatternType
import com.sanket_satpute_20.ironmind.domain.repository.InterventionRepository
import com.sanket_satpute_20.ironmind.domain.repository.PatternRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Sprint V4.8: Adaptive Timing.
 * 
 * Objective: Learn better intervention timing (useful time windows, bad interruption periods).
 * Constraint: Learning timing must not increase interruption frequency without policy authorization.
 * (This use case only learns patterns, it does not execute them.)
 */
class LearnInterventionTimingPatternUseCase(
    private val interventionRepository: InterventionRepository,
    private val patternRepository: PatternRepository,
    private val ironMindAI: IronMindAI,
    private val clock: Clock,
    private val idGenerator: IdGenerator
) {
    suspend operator fun invoke(
        userId: String,
        sinceTimestamp: Long
    ): Result<Pattern?, Exception> = withContext(Dispatchers.IO) {
        try {
            // 1. Fetch recent interventions
            val interventionsResult = interventionRepository.getRecentInterventions(userId, sinceTimestamp)
            if (interventionsResult is Result.Failure) {
                return@withContext Result.Failure(interventionsResult.error)
            }
            
            val recentInterventions = (interventionsResult as Result.Success).data

            // 2. Filter out technical failures
            val validInterventions = recentInterventions.filter {
                it.resolutionReason != null && it.resolutionReason != InterventionResolutionReason.TECHNICAL_FAILURE
            }

            if (validInterventions.isEmpty()) {
                return@withContext Result.Success(null)
            }

            // 3. Format interventions focusing on timing constraints for AI
            val timingContext = buildString {
                append("Historical Intervention Timing Data:\n")
                validInterventions.forEach { record ->
                    append("- Timestamp: ${record.createdAt}, ")
                    append("Type: ${record.type}, ")
                    append("Result: ${record.resolutionReason}\n")
                }
            }

            // 4. Request AI inference for timing patterns
            val request = AIRequest(
                userId = userId,
                input = "Analyze this intervention history for timing patterns. Identify useful time windows or bad interruption periods.\n\n$timingContext",
                requestType = AIRequestType.TIMING_LEARNING,
                contextSnapshot = null
            )

            val aiResult = ironMindAI.process(request)
            if (aiResult is Result.Failure) {
                return@withContext Result.Failure(aiResult.error)
            }
            
            val aiOutput = (aiResult as Result.Success).data
            
            if (!aiOutput.isValid()) {
                return@withContext Result.Failure(IllegalStateException("Invalid AI timing learning output"))
            }

            if (aiOutput is AIOutput.NoAction) {
                return@withContext Result.Success(null)
            }

            if (aiOutput !is AIOutput.PatternCandidate) {
                return@withContext Result.Failure(IllegalStateException("Unexpected AI output type: ${aiOutput.type}"))
            }

            // 5. Convert to TIME_PATTERN
            val now = clock.currentTimeMillis()
            val pattern = Pattern(
                id = idGenerator.generateId(),
                userId = userId,
                type = PatternType.TIME_PATTERN,
                description = aiOutput.patternDescription,
                conditions = null,
                predictedBehavior = null,
                confidence = aiOutput.confidence,
                evidenceCount = validInterventions.size,
                evidenceReferences = aiOutput.evidenceReferences,
                firstObservedAt = now,
                lastObservedAt = now,
                status = PatternStatus.ACTIVE,
                confirmationState = MemoryConfirmationState.UNCONFIRMED,
                createdAt = now,
                updatedAt = now
            )

            // 6. Save pattern
            val saveResult = patternRepository.savePattern(pattern)
            if (saveResult is Result.Failure) {
                return@withContext Result.Failure(saveResult.error)
            }

            // 7. Log lifecycle
            println("IronMindLifecycle [Pattern] [CREATED] type=TIME_PATTERN patternId=${pattern.id}")

            Result.Success(pattern)
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }
}
