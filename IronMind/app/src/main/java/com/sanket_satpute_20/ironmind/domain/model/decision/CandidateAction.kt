package com.sanket_satpute_20.ironmind.domain.model.decision

import com.sanket_satpute_20.ironmind.domain.model.AutonomyCapability

/**
 * Represents an action proposed by the system (e.g. AI or background worker)
 * that must be evaluated by the Decision Engine before execution.
 */
data class CandidateAction(
    val capability: AutonomyCapability,
    val isSafe: Boolean = true,
    val isReversible: Boolean = true
)
