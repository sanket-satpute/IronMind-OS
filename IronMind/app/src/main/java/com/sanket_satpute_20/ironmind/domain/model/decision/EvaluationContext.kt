package com.sanket_satpute_20.ironmind.domain.model.decision

/**
 * The contextual factors against which a CandidateAction is evaluated.
 * 
 * Includes permissions, explicit user overrides (e.g. suppression), 
 * cooldown state, and deduplication results.
 */
data class EvaluationContext(
    val hasRequiredPermissions: Boolean = true,
    val isUserOverrideActive: Boolean = false,
    val isCooldownActive: Boolean = false,
    val isDuplicate: Boolean = false
)
