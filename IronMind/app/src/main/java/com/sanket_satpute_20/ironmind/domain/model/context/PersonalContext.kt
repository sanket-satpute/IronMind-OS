package com.sanket_satpute_20.ironmind.domain.model.context

/**
 * Represents the coherent synthesized current context of the user.
 * 
 * This model captures the principle:
 * "Current explicit intent has higher authority than older inferred patterns."
 * 
 * It combines raw observations (TIME, LOCATION, CALENDAR, CURRENT COMMITMENT,
 * RECENT BEHAVIOR, RECENT REFLECTION, APP CONTEXT, HISTORY, PATTERNS, USER INTENT)
 * into a single unified semantic state.
 */
data class PersonalContext(
    val timestamp: Long,
    val explicitIntent: String?,
    val currentEnvironment: String?,
    val recentBehavior: String?,
    val relevantPatterns: String?,
    val synthesizedSummary: String
)
