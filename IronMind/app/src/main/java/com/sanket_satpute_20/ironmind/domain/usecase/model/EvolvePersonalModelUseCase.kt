package com.sanket_satpute_20.ironmind.domain.usecase.model

import com.sanket_satpute_20.ironmind.domain.ai.AIOutput
import com.sanket_satpute_20.ironmind.domain.ai.AIRequest
import com.sanket_satpute_20.ironmind.domain.ai.AIRequestType
import com.sanket_satpute_20.ironmind.domain.ai.IronMindAI
import com.sanket_satpute_20.ironmind.domain.ai.isValid
import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.engine.ContextEngine
import com.sanket_satpute_20.ironmind.domain.model.pattern.PatternStatus
import com.sanket_satpute_20.ironmind.domain.repository.PatternRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Sprint V4.11: Personal Model Evolution.
 *
 * Evolve the structured understanding of the user by synthesizing reality
 * (intent, confirmed information, behavior, patterns) and decaying weak assumptions.
 */
class EvolvePersonalModelUseCase(
    private val ironMindAI: IronMindAI,
    private val contextEngine: ContextEngine,
    private val patternRepository: PatternRepository
) {
    suspend operator fun invoke(userId: String): Result<Unit, Exception> = withContext(Dispatchers.IO) {
        try {
            // 1. Gather comprehensive context representation
            val contextResult = contextEngine.getCurrentContext(userId)
            if (contextResult !is Result.Success) {
                return@withContext Result.Failure(Exception("Failed to gather context for model evolution."))
            }
            val contextSnapshot = contextResult.data

            // 2. Request AI evaluation of the current model against reality
            val requestInput = """
                Evaluate the provided context to evolve the personal model.
                1. Formulate new patterns if strong evidence supports them.
                2. Identify obsolete pattern IDs that contradict current reality or lack sustained evidence.
                Remember: The personal model must evolve dynamically when reality changes.
            """.trimIndent()

            val request = AIRequest(
                userId = userId,
                input = requestInput,
                requestType = AIRequestType.MODEL_EVOLUTION,
                contextSnapshot = contextSnapshot
            )

            val aiResult = ironMindAI.process(request)
            if (aiResult !is Result.Success) {
                return@withContext Result.Failure((aiResult as Result.Failure).error)
            }

            val aiOutput = aiResult.data
            if (!aiOutput.isValid() || aiOutput !is AIOutput.ModelEvolution) {
                return@withContext Result.Success(Unit)
            }

            // 3. Update Domain (PatternRepository)
            // Persist newly evolved patterns
            for (newPattern in aiOutput.evolvedPatterns) {
                patternRepository.savePattern(newPattern)
            }

            // Expire obsolete patterns (decay assumption)
            for (obsoleteId in aiOutput.obsoletePatternIds) {
                val existingResult = patternRepository.getPattern(obsoleteId)
                if (existingResult.isSuccess && existingResult.getOrNull() != null) {
                    val pattern = existingResult.getOrNull()!!
                    val updatedPattern = pattern.copy(
                        status = PatternStatus.EXPIRED,
                        confidence = 0f,
                        updatedAt = System.currentTimeMillis()
                    )
                    patternRepository.savePattern(updatedPattern)
                    println("IronMindLifecycle [PatternEngine] [PATTERN_EXPIRED] patternId=${obsoleteId} reason=ModelEvolution")
                }
            }

            println("IronMindLifecycle [PersonalModel] [EVOLVED] userId=$userId newPatterns=${aiOutput.evolvedPatterns.size} obsoletePatterns=${aiOutput.obsoletePatternIds.size}")
            Result.Success(Unit)

        } catch (e: Exception) {
            Result.Failure(e)
        }
    }
}
