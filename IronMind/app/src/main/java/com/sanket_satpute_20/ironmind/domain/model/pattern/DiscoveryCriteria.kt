package com.sanket_satpute_20.ironmind.domain.model.pattern

import com.sanket_satpute_20.ironmind.domain.model.EventType
import com.sanket_satpute_20.ironmind.domain.model.observation.ObservationType

enum class DiscoveryOrdering {
    TIMESTAMP_DESC,
    TIMESTAMP_ASC
}

/**
 * Domain-owned, authoritative, executable criteria for Evidence Discovery.
 * 
 * This object is created by the Domain after validating a DiscoveryProposal.
 * It contains the explicit constraints required to deterministically query repositories.
 * 
 * TIMESTAMPS:
 * start is INCLUSIVE (>=).
 * end is EXCLUSIVE (<).
 * start MUST be < end.
 */
data class DiscoveryCriteria(
    val userId: String,
    val startTimeMs: Long,
    val endTimeMs: Long,
    val sourceScope: List<EvidenceSourceType>,
    val eventTypes: List<EventType>?,
    val observationTypes: List<ObservationType>?,
    val limit: Int,
    val ordering: DiscoveryOrdering
)
