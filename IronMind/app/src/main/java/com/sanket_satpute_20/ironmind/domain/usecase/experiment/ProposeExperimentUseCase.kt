package com.sanket_satpute_20.ironmind.domain.usecase.experiment

import com.sanket_satpute_20.ironmind.domain.ai.AIOutput
import com.sanket_satpute_20.ironmind.domain.ai.AIRequest
import com.sanket_satpute_20.ironmind.domain.ai.AIRequestType
import com.sanket_satpute_20.ironmind.domain.ai.IronMindAI
import com.sanket_satpute_20.ironmind.domain.ai.isValid
import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.context.ContextSnapshot
import com.sanket_satpute_20.ironmind.domain.model.experiment.ExperimentRecord
import com.sanket_satpute_20.ironmind.domain.model.experiment.ExperimentState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

import com.sanket_satpute_20.ironmind.domain.common.Clock
import com.sanket_satpute_20.ironmind.domain.common.IdGenerator

/**
 * Sprint V4.10: Experimental Learning.
 * 
 * Objective: Allow controlled experimentation where useful.
 * AI proposes an experiment (e.g. Try shorter commitment vs larger planned block)
 * which can then be presented to the user (per EXPERIMENTATION autonomy capability).
 */
class ProposeExperimentUseCase(
    private val ironMindAI: IronMindAI,
    private val idGenerator: IdGenerator,
    private val clock: Clock
) {
    suspend operator fun invoke(
        userId: String,
        contextSnapshot: ContextSnapshot
    ): Result<ExperimentRecord?, Exception> = withContext(Dispatchers.IO) {
        try {
            val requestInput = """
                Based on the user's recent behavior, propose a bounded, explainable, and non-manipulative 
                experiment to improve their outcomes.
                Example: Try shorter commitments (15m vs 30m) to see if completion rates improve.
                The experiment must be user-respectful and testable.
            """.trimIndent()

            val request = AIRequest(
                userId = userId,
                input = requestInput,
                requestType = AIRequestType.EXPERIMENT_PROPOSAL,
                contextSnapshot = contextSnapshot
            )

            val aiResult = ironMindAI.process(request)
            if (aiResult is Result.Failure) {
                return@withContext Result.Failure(aiResult.error)
            }

            val aiOutput = (aiResult as Result.Success).data
            
            if (!aiOutput.isValid() || aiOutput !is AIOutput.ExperimentProposal) {
                return@withContext Result.Success(null)
            }

            // Only propose if confidence is sufficient
            if (aiOutput.confidence < 0.6f) {
                return@withContext Result.Success(null)
            }

            val experimentRecord = ExperimentRecord(
                id = idGenerator.generateId(),
                userId = userId,
                hypothesis = aiOutput.hypothesis,
                activeVariation = aiOutput.activeVariation,
                controlVariation = aiOutput.controlVariation,
                targetMetric = aiOutput.targetMetric,
                state = ExperimentState.PROPOSED,
                startedAt = clock.currentTimeMillis()
            )

            Result.Success(experimentRecord)

        } catch (e: Exception) {
            Result.Failure(e)
        }
    }
}
