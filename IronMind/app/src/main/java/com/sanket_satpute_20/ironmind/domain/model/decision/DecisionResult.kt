package com.sanket_satpute_20.ironmind.domain.model.decision

/**
 * The outcome produced by the Decision Engine.
 */
enum class DecisionResult {
    /**
     * The action is authorized and should be executed autonomously.
     */
    EXECUTE,
    
    /**
     * The action cannot be performed automatically and requires explicit user confirmation.
     */
    ASK_USER,
    
    /**
     * The action is downgraded to a mere suggestion (no active prompt or execution).
     */
    SUGGEST,
    
    /**
     * The action is blocked (by cooldown, override, duplicate, or settings). Do nothing.
     */
    STAY_SILENT
}
