package com.sanket_satpute_20.ironmind.domain.ai

/**
 * Sprint V2.10: Typed intervention candidates that the AI may recommend.
 *
 * These types are derived directly from the IronMind Implementation Roadmap V2.10.
 *
 * CRITICAL RULE: These are CANDIDATES only. The recommendation engine does NOT execute
 * any intervention. A candidate must flow through the Decision Engine and user policy
 * before any action is taken.
 *
 * Per AI_BEHAVIOR_CONTRACT.md §4, §5 and INTERVENTION_RULES.md.
 */
enum class InterventionType {
    /** Remind the user of a commitment or upcoming task. */
    REMIND,
    /** Redirect the user's attention to a higher-priority item. */
    REDIRECT,
    /** Protect a commitment from being disrupted or forgotten. */
    PROTECT,
    /** Suggest breaking a large task into smaller actions. */
    BREAK_DOWN,
    /** Offer reassurance when the user seems overwhelmed or stuck. */
    REASSURE,
    /** Gently challenge an assumption or avoidance pattern. */
    CHALLENGE,
    /** Ask a clarifying question to better understand the user's intent or state. */
    ASK,
    /** Help the user recover after a missed commitment. */
    RECOVER,
    /** Suggest rescheduling a task to a more suitable time. */
    RESCHEDULE,
    /** Prompt the user to reflect on progress, patterns, or outcomes. */
    REFLECT,
    /** Celebrate a meaningful achievement or completion. */
    CELEBRATE,
    /** Determine that no intervention is needed — stay silent. */
    STAY_SILENT
}
