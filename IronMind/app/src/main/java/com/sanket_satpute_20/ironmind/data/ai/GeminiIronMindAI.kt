package com.sanket_satpute_20.ironmind.data.ai

import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.generationConfig
import com.sanket_satpute_20.ironmind.domain.ai.AIOutput
import com.sanket_satpute_20.ironmind.domain.ai.AIOutputType
import com.sanket_satpute_20.ironmind.domain.ai.AIRequest
import com.sanket_satpute_20.ironmind.domain.ai.AIRequestType
import com.sanket_satpute_20.ironmind.domain.ai.BarrierCandidate
import com.sanket_satpute_20.ironmind.domain.ai.BarrierCategory
import com.sanket_satpute_20.ironmind.domain.ai.IronMindAI
import com.sanket_satpute_20.ironmind.domain.ai.TaskCandidate
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
        val typeSpecificInstructions = when (request.requestType) {
            AIRequestType.PLAN_GENERATION -> """
                You are proposing a practical breakdown for a user goal.
                Consider current commitments, time constraints, user-confirmed goals, and realistic workload.
                Propose tasks ordered by priority. Mark one task as the immediate next action (isNextAction: true).
                Output JSON with type "PLAN" and a "proposedTasks" array. Each task must have:
                  title, description, estimatedDurationMinutes (nullable int), isNextAction (boolean).
                Also include "goalId" (pass through the goalId from context if provided).
            """.trimIndent()
            AIRequestType.BARRIER_UNDERSTANDING -> """
                You are identifying potential barriers that may be contributing to user inaction.
                CRITICAL RULES:
                - Barriers are HYPOTHESES only. Never assert them as confirmed psychological facts.
                - FORBIDDEN: "You procrastinate because you are afraid."
                - ALLOWED: "I've noticed X often happens before Y. Could that be part of what is getting in the way?"
                - Do NOT use identity labels ("You are a procrastinator", "You have anxiety").
                - Use tentative, questioning language in descriptions.
                Output JSON with type "BARRIER" and a "proposedBarriers" array. Each barrier must have:
                  category (one of: UNCERTAINTY, DISTRACTION, FEAR, BOREDOM, LACK_OF_CLARITY, ENVIRONMENTAL_FRICTION, LOW_ENERGY, EXCESSIVE_TASK_SIZE, COMPETING_PRIORITIES, SCHEDULING_MISMATCH),
                  description (tentative hypothesis phrased as a possibility or question).
                Do NOT include isConfirmed (always false from AI).
            """.trimIndent()
            else -> "Match the output type to the request type. Return relevant fields for that type."
        }

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
            
            [TYPE-SPECIFIC INSTRUCTIONS]
            $typeSpecificInstructions
            
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
                    AIOutputType.PLAN.name -> {
                        val tasksArray = jsonObject.optJSONArray("proposedTasks")
                        val tasks = mutableListOf<TaskCandidate>()
                        if (tasksArray != null) {
                            for (i in 0 until tasksArray.length()) {
                                val taskObj = tasksArray.getJSONObject(i)
                                tasks.add(
                                    TaskCandidate(
                                        title = taskObj.optString("title", ""),
                                        description = taskObj.optString("description", ""),
                                        estimatedDurationMinutes = if (taskObj.has("estimatedDurationMinutes") && !taskObj.isNull("estimatedDurationMinutes")) taskObj.getInt("estimatedDurationMinutes") else null,
                                        isNextAction = taskObj.optBoolean("isNextAction", false)
                                    )
                                )
                            }
                        }
                        AIOutput.PlanOutput(
                            goalId = jsonObject.optString("goalId", ""),
                            proposedTasks = tasks,
                            confidence = confidence,
                            reasoning = reasoning,
                            schemaVersion = schemaVersion
                        )
                    }
                    AIOutputType.BARRIER.name -> {
                        val barriersArray = jsonObject.optJSONArray("proposedBarriers")
                        val barriers = mutableListOf<BarrierCandidate>()
                        if (barriersArray != null) {
                            for (i in 0 until barriersArray.length()) {
                                val barrierObj = barriersArray.getJSONObject(i)
                                val categoryStr = barrierObj.optString("category", "")
                                val category = try {
                                    BarrierCategory.valueOf(categoryStr)
                                } catch (e: IllegalArgumentException) {
                                    BarrierCategory.UNCERTAINTY // Safe fallback
                                }
                                barriers.add(
                                    BarrierCandidate(
                                        category = category,
                                        description = barrierObj.optString("description", ""),
                                        isConfirmed = false // Always false from AI layer
                                    )
                                )
                            }
                        }
                        AIOutput.BarrierOutput(
                            proposedBarriers = barriers,
                            confidence = confidence,
                            reasoning = reasoning,
                            schemaVersion = schemaVersion
                        )
                    }
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
