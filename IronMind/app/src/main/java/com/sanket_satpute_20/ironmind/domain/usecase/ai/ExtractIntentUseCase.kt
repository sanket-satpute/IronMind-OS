package com.sanket_satpute_20.ironmind.domain.usecase.ai

import com.sanket_satpute_20.ironmind.domain.ai.AIOutput
import com.sanket_satpute_20.ironmind.domain.ai.AIRequest
import com.sanket_satpute_20.ironmind.domain.ai.AIRequestType
import com.sanket_satpute_20.ironmind.domain.ai.IronMindAI
import com.sanket_satpute_20.ironmind.domain.common.Result

/**
 * Sprint V2.6: Natural Language Intent Extraction.
 * 
 * Takes raw user input and uses the AI reasoning layer to extract a structured intent candidate.
 * It enforces the rule that AI output does NOT automatically become domain truth by returning a
 * string candidate (which the UI must present for confirmation) rather than modifying repositories.
 */
class ExtractIntentUseCase(
    private val ironMindAI: IronMindAI
) {
    /**
     * @param input Raw natural language text from the user.
     * @param userId The current user ID.
     * @return Result.Success containing the extracted intent string, or Result.Failure if extraction failed or confidence was too low.
     */
    suspend operator fun invoke(input: String, userId: String): Result<String, Exception> {
        val request = AIRequest(
            userId = userId,
            input = input,
            requestType = AIRequestType.INTENT_EXTRACTION
        )

        val aiResult = ironMindAI.process(request)

        return when (aiResult) {
            is Result.Success -> {
                val output = aiResult.data
                
                if (output is AIOutput.IntentOutput) {
                    if (output.confidence >= 0.5f && output.intentDescription.isNotBlank()) {
                        Result.Success(output.intentDescription)
                    } else {
                        Result.Failure(Exception("Extraction confidence too low or output empty."))
                    }
                } else {
                    Result.Failure(Exception("AI returned unexpected output type: ${output.javaClass.simpleName}"))
                }
            }
            is Result.Failure -> Result.Failure(aiResult.error)
        }
    }
}
