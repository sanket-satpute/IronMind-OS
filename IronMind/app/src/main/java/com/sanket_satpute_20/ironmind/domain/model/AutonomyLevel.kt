package com.sanket_satpute_20.ironmind.domain.model

/**
 * Defines the autonomy level for a specific capability in IronMind.
 * Governed by AUTONOMY_POLICY.md
 */
enum class AutonomyLevel {
    /**
     * The system must not execute that capability automatically.
     */
    OFF,

    /**
     * The system may observe, analyze, recommend, and explain, but not automatically execute.
     */
    SUGGEST_ONLY,

    /**
     * The system may prepare the action but must obtain user confirmation before execution.
     */
    ASK_BEFORE_ACTION,

    /**
     * The system may execute the action automatically when all policy conditions are satisfied.
     */
    FULL_AUTO
}
