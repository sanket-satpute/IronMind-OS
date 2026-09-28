package com.sanket_satpute_20.ironmind.domain.model.pattern

import com.sanket_satpute_20.ironmind.domain.model.EventType
import com.sanket_satpute_20.ironmind.domain.model.observation.ObservationType

/**
 * Represents an AI's proposed intent for discovering evidence to support a hypothesis.
 * 
 * CRITICAL AUTHORITY BOUNDARY:
 * This is a proposal ONLY. The Domain must validate this and convert it into
 * authoritative DiscoveryCriteria. The AI cannot execute queries.
 * 
 * PRIVACY RESTRICTIONS:
 * This proposal MUST NOT contain raw search strings, userIds, or specific evidence IDs.
 * 
 * TEMPORAL GAP:
 * Temporal vocabulary (e.g. LAST_30_DAYS) is currently undefined in the permanent contract.
 * Therefore, bounded temporal intent is intentionally deferred until a temporal policy exists.
 */
data class DiscoveryProposal(
    val sourceScope: List<EvidenceSourceType>? = null,
    val eventTypes: List<EventType>? = null,
    val observationTypes: List<ObservationType>? = null
    // val temporalIntent: String? = null // Deferred due to missing temporal vocabulary contract
)
