package com.sanket_satpute_20.ironmind.domain.ai

/**
 * Sprint V2.8: A single proposed task candidate from the AI planning layer.
 *
 * This is a PROPOSAL only. It must not be persisted directly as domain state.
 * The user must explicitly confirm these candidates before any task is created.
 *
 * Per AI_BEHAVIOR_CONTRACT.md §4, §5.
 */
data class TaskCandidate(
    val title: String,
    val description: String,
    val estimatedDurationMinutes: Int? = null,
    val isNextAction: Boolean = false
)
