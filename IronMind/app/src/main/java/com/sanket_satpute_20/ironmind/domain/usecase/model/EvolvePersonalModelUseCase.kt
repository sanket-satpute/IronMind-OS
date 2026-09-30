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
                1. Propose PatternCandidates if strong evidence supports them.
                2. If a candidate contradicts an existing pattern, include the existing pattern's ID as contradictionSignal.
                Remember: You do not have authority to declare facts; you may only propose candidates.
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

            // 3. Domain Learning Boundary
            // Sprint 11C: AI outputs PatternCandidates, not Patterns.
            // DO NOT directly persist these candidates.
            // The future Pattern Engine will validate evidence, calculate confidence, and manage state.

            val candidates = aiOutput.evolvedCandidates

            // Temporary transitional behavior: Log candidates instead of blindly saving
            println("IronMindLifecycle [PatternLearning] [CANDIDATES_RECEIVED] userId=$userId candidateCount=${candidates.size}")


            Result.Success(Unit)

        } catch (e: Exception) {
            Result.Failure(e)
        }
    }
}
