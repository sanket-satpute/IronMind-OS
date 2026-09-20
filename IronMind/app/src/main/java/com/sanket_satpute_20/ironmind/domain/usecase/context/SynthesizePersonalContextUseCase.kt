package com.sanket_satpute_20.ironmind.domain.usecase.context

import com.sanket_satpute_20.ironmind.domain.ai.AIRequest
import com.sanket_satpute_20.ironmind.domain.ai.AIRequestType
import com.sanket_satpute_20.ironmind.domain.ai.IronMindAI
import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.engine.ContextEngine
import com.sanket_satpute_20.ironmind.domain.model.context.PersonalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

import com.sanket_satpute_20.ironmind.domain.ai.AIOutput
import com.sanket_satpute_20.ironmind.domain.ai.isValid

class SynthesizePersonalContextUseCase(
    private val contextEngine: ContextEngine,
    private val ironMindAI: IronMindAI
) {
    suspend operator fun invoke(userId: String): Result<PersonalContext, Exception> = withContext(Dispatchers.IO) {
        try {
            // 1. Generate the deterministic raw snapshot of all dimensions
            val snapshotResult = contextEngine.getCurrentContext(userId)
            if (snapshotResult is Result.Failure) {
                return@withContext Result.Failure(snapshotResult.error)
            }
            val snapshot = (snapshotResult as Result.Success).data

            // 2. Request AI synthesis to form a coherent personal context
            val request = AIRequest(
                userId = userId,
                input = "Synthesize current personal context. Principle: Current explicit intent has higher authority than older inferred patterns.",
                requestType = AIRequestType.CONTEXT_SYNTHESIS,
                contextSnapshot = snapshot
            )
            
            val aiResult = ironMindAI.process(request)
            if (aiResult is Result.Failure) {
                return@withContext Result.Failure(aiResult.error)
            }
            
            val aiOutput = (aiResult as Result.Success).data
            
            if (!aiOutput.isValid()) {
                return@withContext Result.Failure(IllegalStateException("Invalid AI synthesis output"))
            }

            if (aiOutput !is AIOutput.ContextSynthesis) {
                return@withContext Result.Failure(IllegalStateException("Unexpected AI output type: \${aiOutput.type}"))
            }

            // 3. Extract synthesized content from AI output
            val personalContext = PersonalContext(
                timestamp = snapshot.timestamp,
                explicitIntent = aiOutput.explicitIntent,
                currentEnvironment = aiOutput.currentEnvironment,
                recentBehavior = aiOutput.recentBehavior,
                relevantPatterns = aiOutput.relevantPatterns,
                synthesizedSummary = aiOutput.synthesizedSummary
            )

            // 4. Emit standard lifecycle logging
            println("IronMindLifecycle [Context] [SYNTHESIZED] userId=\$userId timestamp=\${personalContext.timestamp}")

            Result.Success(personalContext)
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }
}
