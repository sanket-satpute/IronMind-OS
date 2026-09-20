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
 * Sprint V4.7: Intervention Learning.
 * 
 * Objective: Learn which interventions tend to help under which conditions.
 * Critical distinction: Do not learn from technical failures as if they were behavioral failures.
 */
class LearnInterventionResponsePatternUseCase(
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

            // 2. Filter out technical failures and incomplete interventions
            val validInterventions = recentInterventions.filter {
                it.resolutionReason != null && it.resolutionReason != InterventionResolutionReason.TECHNICAL_FAILURE
            }

            if (validInterventions.isEmpty()) {
                // Not enough data to learn anything
                return@withContext Result.Success(null)
            }

            // 3. Format interventions for AI
            val interventionContext = buildString {
                append("Historical Interventions for Learning:\n")
                validInterventions.forEach { record ->
                    append("- [${record.createdAt}] Type: ${record.type}, ")
                    append("Title: ${record.title}, ")
                    append("Result: ${record.resolutionReason}\n")
                }
            }

            // 4. Request AI inference
            val request = AIRequest(
                userId = userId,
                input = "Analyze this intervention history and propose a pattern regarding which interventions help or fail under which conditions.\n\n$interventionContext",
                requestType = AIRequestType.INTERVENTION_LEARNING,
                contextSnapshot = null // Optional context snapshot could be passed, but text is sufficient
            )

            val aiResult = ironMindAI.process(request)
            if (aiResult is Result.Failure) {
                return@withContext Result.Failure(aiResult.error)
            }
            
            val aiOutput = (aiResult as Result.Success).data
            
            if (!aiOutput.isValid()) {
                return@withContext Result.Failure(IllegalStateException("Invalid AI intervention learning output"))
            }

            // AI might decide NO_ACTION is appropriate if no clear pattern exists
            if (aiOutput is AIOutput.NoAction) {
                return@withContext Result.Success(null)
            }

            if (aiOutput !is AIOutput.PatternCandidate) {
                return@withContext Result.Failure(IllegalStateException("Unexpected AI output type: ${aiOutput.type}"))
            }

            // 5. Convert AI candidate to domain Pattern
            val now = clock.currentTimeMillis()
            val pattern = Pattern(
                id = idGenerator.generateId(),
                userId = userId,
                type = PatternType.INTERVENTION_RESPONSE_PATTERN,
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
            println("IronMindLifecycle [Pattern] [CREATED] type=INTERVENTION_RESPONSE_PATTERN patternId=${pattern.id}")

            Result.Success(pattern)
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }
}
