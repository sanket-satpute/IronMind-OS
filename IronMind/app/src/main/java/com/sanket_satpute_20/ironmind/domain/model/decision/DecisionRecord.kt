package com.sanket_satpute_20.ironmind.domain.model.decision

import com.sanket_satpute_20.ironmind.domain.model.AutonomyCapability
import com.sanket_satpute_20.ironmind.domain.model.AutonomyLevel

/**
 * A persisted record of a decision made by the Decision Engine.
 * Provides transparency and auditability for autonomous actions.
 */
data class DecisionRecord(
    val id: String,
    val timestamp: Long,
    val capability: AutonomyCapability,
    val action: String, // Short description of what was proposed
    val trigger: String? = null,
    val source: String? = null,
    val contextSummary: String? = null,
    val policy: String? = null,
    val autonomyLevel: AutonomyLevel,
    val reasoning: String? = null,
    val confidence: Float? = null,
    val result: DecisionResult,
    val failureReason: String? = null,
    val userResponse: String? = null
)
