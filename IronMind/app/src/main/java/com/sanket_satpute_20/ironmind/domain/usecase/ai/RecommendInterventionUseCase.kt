package com.sanket_satpute_20.ironmind.domain.usecase.ai

import com.sanket_satpute_20.ironmind.domain.ai.AIOutput
import com.sanket_satpute_20.ironmind.domain.ai.AIRequest
import com.sanket_satpute_20.ironmind.domain.ai.AIRequestType
import com.sanket_satpute_20.ironmind.domain.ai.IronMindAI
import com.sanket_satpute_20.ironmind.domain.common.Result

/**
 * Sprint V2.10: Intervention Recommendation Engine.
 *
 * Uses the AI reasoning layer to generate a single typed intervention candidate
 * appropriate for the user's current context.
 *
 * CRITICAL CONTRACT RULES (IRONMIND_IMPLEMENTATION_ROADMAP.md V2.10, INTERVENTION_RULES.md,
 * AI_BEHAVIOR_CONTRACT.md §4, §5):
 * - This use case returns a CANDIDATE only. It does NOT execute any intervention.
 * - It does NOT call any repository.
 * - The caller must route the candidate through the Decision Engine and user policy
 *   before any action is taken.
 * - STAY_SILENT is a valid candidate — the caller must respect it.
 */
class RecommendInterventionUseCase(
    private val ironMindAI: IronMindAI
) {
    /**
     * @param input Context description: what the user is experiencing, recent events, or trigger.
     * @param userId The current user ID.
     * @return Result.Success containing the [AIOutput.InterventionRecommendation] candidate, or Result.Failure.
     */
    suspend operator fun invoke(
        input: String,
        userId: String
    ): Result<AIOutput.InterventionRecommendation, Exception> {
        val request = AIRequest(
            userId = userId,
            input = input,
            requestType = AIRequestType.INTERVENTION_SUGGESTION
        )

        val aiResult = ironMindAI.process(request)

        return when (aiResult) {
            is Result.Success -> {
                val output = aiResult.data

                if (output is AIOutput.InterventionRecommendation) {
                    if (output.confidence >= 0.5f) {
                        Result.Success(output)
                    } else {
                        Result.Failure(Exception("Intervention recommendation confidence too low."))
                    }
                } else {
                    Result.Failure(Exception("AI returned unexpected output type: ${output.javaClass.simpleName}"))
                }
            }
            is Result.Failure -> Result.Failure(aiResult.error)
        }
    }
}
