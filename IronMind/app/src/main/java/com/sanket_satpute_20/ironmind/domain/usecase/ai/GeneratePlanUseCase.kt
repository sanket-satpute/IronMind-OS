package com.sanket_satpute_20.ironmind.domain.usecase.ai

import com.sanket_satpute_20.ironmind.domain.ai.AIOutput
import com.sanket_satpute_20.ironmind.domain.ai.AIRequest
import com.sanket_satpute_20.ironmind.domain.ai.AIRequestType
import com.sanket_satpute_20.ironmind.domain.ai.IronMindAI
import com.sanket_satpute_20.ironmind.domain.common.Result

/**
 * Sprint V2.8: AI Planning.
 *
 * Accepts a goal description and context, and uses the AI reasoning layer to
 * propose a practical breakdown (plan → tasks → next action).
 *
 * CRITICAL RULE (AI_BEHAVIOR_CONTRACT.md §4, §5):
 * This use case returns a candidate [AIOutput.PlanOutput] only.
 * It does NOT persist anything to any repository.
 * The caller (UI/Presenter) must present the proposed plan to the user
 * for explicit confirmation before any domain mutation is permitted.
 */
class GeneratePlanUseCase(
    private val ironMindAI: IronMindAI
) {
    /**
     * @param goalId The ID of the goal to plan for.
     * @param goalDescription A textual description of the goal, for AI reasoning context.
     * @param userId The current user ID.
     * @param contextHints Optional context strings (e.g., known commitments, constraints).
     * @return Result.Success containing the [AIOutput.PlanOutput] candidate, or Result.Failure.
     */
    suspend operator fun invoke(
        goalId: String,
        goalDescription: String,
        userId: String,
        contextHints: List<String> = emptyList()
    ): Result<AIOutput.PlanOutput, Exception> {
        val contextBlock = if (contextHints.isNotEmpty()) {
            "\n\nAdditional context:\n" + contextHints.joinToString("\n") { "- $it" }
        } else ""

        val input = "Goal ID: $goalId\nGoal: $goalDescription$contextBlock"

        val request = AIRequest(
            userId = userId,
            input = input,
            requestType = AIRequestType.PLAN_GENERATION
        )

        val aiResult = ironMindAI.process(request)

        return when (aiResult) {
            is Result.Success -> {
                val output = aiResult.data

                if (output is AIOutput.PlanOutput) {
                    if (output.confidence >= 0.5f && output.proposedTasks.isNotEmpty()) {
                        Result.Success(output)
                    } else {
                        Result.Failure(Exception("AI plan confidence too low or no tasks proposed."))
                    }
                } else {
                    Result.Failure(Exception("AI returned unexpected output type: ${output.javaClass.simpleName}"))
                }
            }
            is Result.Failure -> Result.Failure(aiResult.error)
        }
    }
}
