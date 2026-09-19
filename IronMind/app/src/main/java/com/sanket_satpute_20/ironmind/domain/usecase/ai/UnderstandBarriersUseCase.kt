package com.sanket_satpute_20.ironmind.domain.usecase.ai

import com.sanket_satpute_20.ironmind.domain.ai.AIOutput
import com.sanket_satpute_20.ironmind.domain.ai.AIRequest
import com.sanket_satpute_20.ironmind.domain.ai.AIRequestType
import com.sanket_satpute_20.ironmind.domain.ai.IronMindAI
import com.sanket_satpute_20.ironmind.domain.common.Result

/**
 * Sprint V2.9: Barrier Understanding.
 *
 * Takes raw user input and uses the AI reasoning layer to surface potential barriers
 * that may be contributing to user inaction, as hypotheses.
 *
 * CRITICAL CONTRACT RULES (IRONMIND_IMPLEMENTATION_ROADMAP.md V2.9, AI_BEHAVIOR_CONTRACT.md §4, §5):
 * - A barrier is a HYPOTHESIS unless explicitly confirmed by the user.
 * - This use case returns a [AIOutput.BarrierOutput] candidate only.
 * - It does NOT persist anything to any repository.
 * - All [BarrierCandidate] entries carry isConfirmed=false.
 * - The caller must present candidates for explicit user confirmation before any
 *   domain mutation is permitted.
 */
class UnderstandBarriersUseCase(
    private val ironMindAI: IronMindAI
) {
    /**
     * @param input Raw natural language input from the user (e.g., reflection or context description).
     * @param userId The current user ID.
     * @return Result.Success containing the [AIOutput.BarrierOutput] candidate, or Result.Failure.
     */
    suspend operator fun invoke(
        input: String,
        userId: String
    ): Result<AIOutput.BarrierOutput, Exception> {
        val request = AIRequest(
            userId = userId,
            input = input,
            requestType = AIRequestType.BARRIER_UNDERSTANDING
        )

        val aiResult = ironMindAI.process(request)

        return when (aiResult) {
            is Result.Success -> {
                val output = aiResult.data

                if (output is AIOutput.BarrierOutput) {
                    if (output.confidence >= 0.5f && output.proposedBarriers.isNotEmpty()) {
                        Result.Success(output)
                    } else {
                        Result.Failure(Exception("Barrier understanding confidence too low or no barriers proposed."))
                    }
                } else {
                    Result.Failure(Exception("AI returned unexpected output type: ${output.javaClass.simpleName}"))
                }
            }
            is Result.Failure -> Result.Failure(aiResult.error)
        }
    }
}
