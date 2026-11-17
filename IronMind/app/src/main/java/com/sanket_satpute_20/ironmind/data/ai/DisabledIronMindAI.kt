package com.sanket_satpute_20.ironmind.data.ai

import com.sanket_satpute_20.ironmind.domain.ai.AIOutput
import com.sanket_satpute_20.ironmind.domain.ai.AIRequest
import com.sanket_satpute_20.ironmind.domain.ai.IronMindAI
import com.sanket_satpute_20.ironmind.domain.common.Result

/**
 * Fail-closed implementation of [IronMindAI] used in isolated test/validation environments.
 * Strictly guarantees zero remote calls to Firebase AI Logic or Cloudflare Workers.
 */
class DisabledIronMindAI(
    private val reason: String = "AI inference is explicitly disabled in the isolated test environment."
) : IronMindAI {

    override suspend fun process(request: AIRequest): Result<AIOutput, Exception> {
        return Result.Failure(SecurityException("AI_INFERENCE_DISABLED: $reason (requestType=${request.requestType})"))
    }
}
