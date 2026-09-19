package com.sanket_satpute_20.ironmind.domain.ai

/**
 * Sprint V2.9: Potential barrier categories that the AI may hypothesise.
 *
 * These categories are derived directly from the IronMind Implementation Roadmap V2.9.
 *
 * CRITICAL RULE: A barrier category is a contextual possibility — never a confirmed
 * psychological fact. IronMind must never assert these as certainties about the user.
 *
 * Per AI_BEHAVIOR_CONTRACT.md: no identity labels, no fake certainty.
 */
enum class BarrierCategory {
    UNCERTAINTY,
    DISTRACTION,
    FEAR,
    BOREDOM,
    LACK_OF_CLARITY,
    ENVIRONMENTAL_FRICTION,
    LOW_ENERGY,
    EXCESSIVE_TASK_SIZE,
    COMPETING_PRIORITIES,
    SCHEDULING_MISMATCH
}
