package com.sanket_satpute_20.ironmind.domain.ai

import com.sanket_satpute_20.ironmind.domain.model.context.ContextSnapshot

/**
 * Represents a request sent to the IronMindAI reasoning layer.
 *
 * AI may not directly mutate domain state from this request.
 * AI output is a proposal only — must be validated and processed
 * through the appropriate policy/decision path before any domain action.
 *
 * Per AI_BEHAVIOR_CONTRACT.md §4, §5, §77.
 */
data class AIRequest(
    val userId: String,
    val input: String,
    val requestType: AIRequestType,
    val contextSnapshot: ContextSnapshot? = null
)
