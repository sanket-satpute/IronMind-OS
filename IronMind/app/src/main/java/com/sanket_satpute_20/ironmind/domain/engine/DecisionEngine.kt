package com.sanket_satpute_20.ironmind.domain.engine

import com.sanket_satpute_20.ironmind.domain.model.decision.CandidateAction
import com.sanket_satpute_20.ironmind.domain.model.decision.DecisionResult
import com.sanket_satpute_20.ironmind.domain.model.decision.EvaluationContext

/**
 * The authoritative policy boundary for IronMind.
 * Evaluates whether a proposed action may be executed automatically, 
 * requires user permission, should only be suggested, or must stay silent.
 */
interface DecisionEngine {
    
    /**
     * Evaluates a candidate action against the current context and user autonomy settings.
     * 
     * @param userId The ID of the user.
     * @param candidate The proposed action to evaluate.
     * @param context External contextual state (permissions, cooldowns, overrides).
     * @return The authorized [DecisionResult].
     */
    suspend fun evaluate(
        userId: String,
        candidate: CandidateAction,
        context: EvaluationContext
    ): DecisionResult
}
