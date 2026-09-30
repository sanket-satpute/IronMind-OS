package com.sanket_satpute_20.ironmind.domain.model.pattern

/**
 * Represents an unvalidated AI proposal for a behavioral pattern.
 * This is the semantic boundary between AI output and the domain's Pattern Engine.
 * 
 * An AI MAY propose these fields.
 * An AI MUST NOT propose ID, confidence, evidence references, status, or timestamps.
 */
data class PatternCandidate(
    val type: PatternType,
    val description: String,
    val conditions: String? = null,
    val predictedBehavior: String? = null,
    val contradictionSignal: String? = null, // e.g. an obsoletePatternId it thinks it contradicts
    val discoveryProposal: DiscoveryProposal? = null
)
