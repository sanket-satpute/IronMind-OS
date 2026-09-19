package com.sanket_satpute_20.ironmind.data.ai

import com.sanket_satpute_20.ironmind.domain.ai.AIOutput
import com.sanket_satpute_20.ironmind.domain.ai.AIRequest
import com.sanket_satpute_20.ironmind.domain.ai.IronMindAI
import com.sanket_satpute_20.ironmind.domain.common.Result

/**
 * A stub implementation of IronMindAI for wiring the application
 * before a real provider is integrated in Sprint V2.5.
 *
 * It safely returns a valid NO_ACTION response for all requests,
 * ensuring the app remains compilable and crash-free without doing
 * any real AI processing.
 */
class StubIronMindAI : IronMindAI {
    override suspend fun process(request: AIRequest): Result<AIOutput, Exception> {
        val output = AIOutput.NoAction(
            reason = "AI processing is not yet wired to a real provider.",
            reasoning = "StubIronMindAI always returns NO_ACTION.",
            schemaVersion = 1,
            confidence = 1.0f
        )
        return Result.Success(output)
    }
}
