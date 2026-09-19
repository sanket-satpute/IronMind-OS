package com.sanket_satpute_20.ironmind.domain.ai

import com.sanket_satpute_20.ironmind.domain.common.Result

/**
 * The provider-independent AI reasoning interface for IronMind.
 *
 * This is the single abstraction behind which all AI provider implementations are hidden.
 * All product logic must depend only on this interface — never on any concrete provider.
 *
 * Architectural constraints (from AI_BEHAVIOR_CONTRACT.md and SYSTEM_ARCHITECTURE.md):
 * - AI may NOT directly execute Android actions (§5)
 * - AI may NOT directly modify protected domain state (§4)
 * - AI may NOT silently change user autonomy (§6)
 * - AI output is always a PROPOSAL — must be validated and processed through
 *   the appropriate policy/decision path before any domain action (§4, §81)
 * - Structured output must be validated before downstream processing (§81, §82, §83)
 *
 * Responsibilities (from Sprint V2.4 roadmap):
 * - summarize, classify, extract, plan, recommend, reason
 * - propose memory candidates
 * - propose patterns
 */
interface IronMindAI {
    /**
     * Processes a reasoning request and returns a structured AI output.
     *
     * The output is a PROPOSAL only. Callers must validate the output using [AIOutput.isValid]
     * and route it through the appropriate policy/decision path before taking any domain action.
     *
     * @param request The structured request containing user input, context, and request type.
     * @return A Result containing a validated AIOutput, or an Exception on failure.
     */
    suspend fun process(request: AIRequest): Result<AIOutput, Exception>
}
