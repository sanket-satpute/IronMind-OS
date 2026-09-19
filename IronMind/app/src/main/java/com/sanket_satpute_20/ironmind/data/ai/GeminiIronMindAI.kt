package com.sanket_satpute_20.ironmind.data.ai

import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.generationConfig
import com.sanket_satpute_20.ironmind.domain.ai.AIOutput
import com.sanket_satpute_20.ironmind.domain.ai.AIOutputType
import com.sanket_satpute_20.ironmind.domain.ai.AIRequest
import com.sanket_satpute_20.ironmind.domain.ai.AIRequestType
import com.sanket_satpute_20.ironmind.domain.ai.IronMindAI
import com.sanket_satpute_20.ironmind.domain.ai.isValid
import com.sanket_satpute_20.ironmind.domain.common.Result
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException

/**
 * Concrete implementation of IronMindAI using Google Gemini.
 * 
 * Supports:
 * - Structured JSON generation
 * - Fallbacks, retries, timeouts
 * - Validation mapping
 */
class GeminiIronMindAI(
    apiKey: String,
    private val modelName: String = "gemini-1.5-flash",
    private val timeoutMs: Long = 15000L,
    private val maxRetries: Int = 2
) : IronMindAI {

    // model/version tracking (Sprint V2.5 requirement)
    private val activeModelVersion = modelName
    // prompt/version tracking (Sprint V2.5 requirement)
    private val promptVersion = 1

    private val generativeModel = GenerativeModel(
        modelName = activeModelVersion,
        apiKey = apiKey,
        generationConfig = generationConfig {
            responseMimeType = "application/json"
            temperature = 0.2f // Keep reasoning deterministic
        }
    )

    override suspend fun process(request: AIRequest): Result<AIOutput, Exception> {
        val prompt = buildPrompt(request)

        return executeWithRetry {
            withTimeout(timeoutMs) {
                val response = generativeModel.generateContent(prompt)
                val jsonText = response.text ?: throw IOException("Empty response from Gemini")
                parseAndValidateOutput(jsonText, request.requestType)
            }
        }
    }

    private suspend fun <T> executeWithRetry(block: suspend () -> T): Result<T, Exception> {
        var currentAttempt = 0
        var lastException: Exception? = null

        while (currentAttempt <= maxRetries) {
            try {
                return Result.Success(block())
            } catch (e: Exception) {
                lastException = e
                currentAttempt++
                if (currentAttempt <= maxRetries) {
                    delay(1000L * currentAttempt) // Exponential backoff
                }
            }
        }
        
        // Offline fallback / ultimate failure handling
        return Result.Failure(lastException ?: Exception("Unknown AI failure"))
    }

    private fun buildPrompt(request: AIRequest): String {
        return """
            [SYSTEM INSTRUCTION]
            You are the IronMind AI Reasoning Layer (Prompt Version: $promptVersion).
            Analyze the user input and provide a JSON response exactly matching the requested format.
            Confidence must be between 0.0 and 1.0.
            
            [CONTEXT]
            User ID: ${request.userId}
            Context Elements: ${request.contextSnapshot?.let { "Included" } ?: "None"}
            
            [USER INPUT]
            ${request.input}
            
            [REQUEST TYPE]
            ${request.requestType.name}
            
            Return JSON only. Format it as:
            {
                "type": "<Match AIOutputType for this request>",
                "confidence": 0.0 to 1.0,
                "reasoning": "Explain your logic",
                "schemaVersion": 1,
                // ... plus any type-specific fields ...
            }
        """.trimIndent()
    }

    companion object {
        fun parseAndValidateOutput(jsonText: String, requestType: AIRequestType): AIOutput {
            try {
                // Strip markdown formatting if the model accidentally returns it despite responseMimeType
                val cleanJson = jsonText.replace("```json", "").replace("```", "").trim()
                val jsonObject = JSONObject(cleanJson)
                
                val typeStr = jsonObject.optString("type")
                val confidence = jsonObject.optDouble("confidence", 0.0).toFloat()
                val reasoningStr = jsonObject.optString("reasoning", "")
                val reasoning = reasoningStr.ifEmpty { null }
                val schemaVersion = jsonObject.optInt("schemaVersion", 1)

                val output = when (typeStr) {
                    AIOutputType.INTENT.name -> AIOutput.IntentOutput(
                        intentDescription = jsonObject.optString("intentDescription", "Unknown"),
                        confidence = confidence,
                        reasoning = reasoning,
                        schemaVersion = schemaVersion
                    )
                    AIOutputType.REFLECTION_EXTRACTION.name -> AIOutput.ReflectionExtraction(
                        extractedText = jsonObject.optString("extractedText", ""),
                        confidence = confidence,
                        reasoning = reasoning,
                        schemaVersion = schemaVersion
                    )
                    AIOutputType.PATTERN_CANDIDATE.name -> AIOutput.PatternCandidate(
                        patternDescription = jsonObject.optString("patternDescription", ""),
                        confidence = confidence,
                        reasoning = reasoning,
                        schemaVersion = schemaVersion
                    )
                    AIOutputType.MEMORY_CANDIDATE.name -> AIOutput.MemoryCandidate(
                        candidateContent = jsonObject.optString("candidateContent", ""),
                        confidence = confidence,
                        reasoning = reasoning,
                        schemaVersion = schemaVersion
                    )
                    AIOutputType.INTERVENTION_RECOMMENDATION.name -> AIOutput.InterventionRecommendation(
                        recommendation = jsonObject.optString("recommendation", ""),
                        reason = jsonObject.optString("reason", ""),
                        targetEntityId = if (jsonObject.has("targetEntityId")) jsonObject.getString("targetEntityId") else null,
                        confidence = confidence,
                        reasoning = reasoning,
                        schemaVersion = schemaVersion
                    )
                    AIOutputType.SUMMARY.name -> AIOutput.Summary(
                        summaryText = jsonObject.optString("summaryText", ""),
                        confidence = confidence,
                        reasoning = reasoning,
                        schemaVersion = schemaVersion
                    )
                    AIOutputType.CLARIFICATION_REQUEST.name -> AIOutput.ClarificationRequest(
                        question = jsonObject.optString("question", ""),
                        confidence = confidence,
                        reasoning = reasoning,
                        schemaVersion = schemaVersion
                    )
                    else -> AIOutput.NoAction(
                        reason = "Unrecognized output type: $typeStr",
                        confidence = 1.0f,
                        reasoning = reasoning,
                        schemaVersion = schemaVersion
                    )
                }

                if (!output.isValid()) {
                    return AIOutput.NoAction(reason = "Invalid AI output generated by model.")
                }

                return output
            } catch (e: JSONException) {
                throw IOException("Failed to parse JSON response: $jsonText", e)
            }
        }
    }
}
