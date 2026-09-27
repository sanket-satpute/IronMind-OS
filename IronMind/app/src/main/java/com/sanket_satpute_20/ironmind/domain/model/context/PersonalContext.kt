package com.sanket_satpute_20.ironmind.domain.model.context

/**
 * Represents the coherent synthesized current context of the user.
 *
 * "Authoritative explicit intent resides in domains like Goals and Commitments. The AI synthesis
 * provides non-authoritative hypotheses (like inferredIntentHypothesis)."
 *
 * It combines raw observations (TIME, LOCATION, CALENDAR, CURRENT COMMITMENT,
 * RECENT BEHAVIOR, RECENT REFLECTION, APP CONTEXT, HISTORY, PATTERNS, USER INTENT)
 * into a single unified semantic state.
 */
data class PersonalContext(
    val timestamp: Long,

    /**
     * An AI-derived hypothesis of what the user's intent might be, based on contextual aggregation.
     * This is non-authoritative, not user-confirmed, and sits at the "weak inference" / "AI hypothesis"
     * level of the authority hierarchy.
     * It must NEVER be permitted to silently override explicit user input (e.g. from Goals or Reflections).
     */
    val inferredIntentHypothesis: String?,
    val currentEnvironment: String?,
    val recentBehavior: String?,
    val relevantPatterns: String?,
    val synthesizedSummary: String
)
