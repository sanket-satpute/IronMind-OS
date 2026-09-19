package com.sanket_satpute_20.ironmind.domain.ai

/**
 * Sprint V2.9: A single hypothesised barrier candidate from the AI reasoning layer.
 *
 * CRITICAL CONTRACT RULES (AI_BEHAVIOR_CONTRACT.md, IRONMIND_IMPLEMENTATION_ROADMAP.md V2.9):
 *
 * 1. A barrier is a HYPOTHESIS, not a confirmed fact.
 * 2. [isConfirmed] is always false when produced by the AI.
 *    Only explicit user confirmation may change this.
 * 3. [description] must be tentative in language — phrased as a question or possibility,
 *    NEVER as a psychological assertion.
 *    ALLOWED: "I've noticed X happens often before Y. Could that be part of what is getting in the way?"
 *    FORBIDDEN: "You procrastinate because you are afraid."
 * 4. No identity labels permitted.
 */
data class BarrierCandidate(
    val category: BarrierCategory,
    val description: String,
    val isConfirmed: Boolean = false
)
