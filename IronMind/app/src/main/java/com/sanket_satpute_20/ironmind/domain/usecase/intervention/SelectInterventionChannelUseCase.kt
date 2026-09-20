package com.sanket_satpute_20.ironmind.domain.usecase.intervention

import com.sanket_satpute_20.ironmind.domain.ai.AIOutput
import com.sanket_satpute_20.ironmind.domain.ai.AIRequest
import com.sanket_satpute_20.ironmind.domain.ai.AIRequestType
import com.sanket_satpute_20.ironmind.domain.ai.IronMindAI
import com.sanket_satpute_20.ironmind.domain.ai.isValid
import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.intervention.InterventionChannel
import com.sanket_satpute_20.ironmind.domain.model.context.ContextSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Sprint V4.9: Adaptive Channel Selection.
 * 
 * Objective: Choose the least intrusive effective intervention channel.
 * Principle: Channel selection optimizes usefulness, not app engagement.
 */
class SelectInterventionChannelUseCase(
    private val ironMindAI: IronMindAI
) {
    suspend operator fun invoke(
        userId: String,
        interventionType: String,
        interventionReason: String,
        contextSnapshot: ContextSnapshot? = null
    ): Result<InterventionChannel, Exception> = withContext(Dispatchers.IO) {
        try {
            val requestInput = """
                Evaluate the optimal delivery channel for this intervention.
                Intervention Type: $interventionType
                Reason: $interventionReason
                
                Choose the least intrusive but effective channel from:
                IN_APP, NOTIFICATION, PROTECTION, SCHEDULING_SUGGESTION, OTHER.
                Optimization goal: usefulness, not engagement.
            """.trimIndent()

            val request = AIRequest(
                userId = userId,
                input = requestInput,
                requestType = AIRequestType.CHANNEL_SELECTION,
                contextSnapshot = contextSnapshot
            )

            val aiResult = ironMindAI.process(request)
            if (aiResult is Result.Failure) {
                // Fallback to IN_APP on AI failure to remain least intrusive
                return@withContext Result.Success(InterventionChannel.IN_APP)
            }

            val aiOutput = (aiResult as Result.Success).data

            if (!aiOutput.isValid()) {
                return@withContext Result.Success(InterventionChannel.IN_APP)
            }

            if (aiOutput is AIOutput.ChannelRecommendation) {
                return@withContext Result.Success(aiOutput.recommendedChannel)
            }

            // Fallback for unexpected valid types
            Result.Success(InterventionChannel.IN_APP)

        } catch (e: Exception) {
            // Safety fallback
            Result.Success(InterventionChannel.IN_APP)
        }
    }
}
