package com.sanket_satpute_20.ironmind.domain.engine

import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.logging.IronLogger
import com.sanket_satpute_20.ironmind.domain.model.pattern.DiscoveryCriteria
import com.sanket_satpute_20.ironmind.domain.model.pattern.DiscoveryOrdering
import com.sanket_satpute_20.ironmind.domain.model.pattern.EvidenceReference
import com.sanket_satpute_20.ironmind.domain.model.pattern.EvidenceSourceType
import com.sanket_satpute_20.ironmind.domain.repository.EventRepository
import com.sanket_satpute_20.ironmind.domain.repository.ObservationRepository
import com.sanket_satpute_20.ironmind.domain.repository.ReflectionRepository

class EvidenceDiscovery(
    private val eventRepository: EventRepository,
    private val observationRepository: ObservationRepository,
    private val reflectionRepository: ReflectionRepository,
    private val logger: IronLogger
) {

    private data class DiscoveryCandidate(
        val reference: EvidenceReference,
        val timestamp: Long
    )

    suspend fun discover(criteria: DiscoveryCriteria): Result<List<EvidenceReference>, Exception> {
        // Source scope enforcement: zero authorized sources -> zero results
        if (criteria.sourceScope.isEmpty()) {
            logger.logLifecycle(
                "EvidenceDiscovery",
                "completed",
                mapOf(
                    "sourceScope" to "",
                    "ordering" to criteria.ordering.name,
                    "limit" to criteria.limit,
                    "resultCount" to 0,
                    "status" to "SUCCESS"
                )
            )
            return Result.Success(emptyList())
        }

        val candidates = mutableListOf<DiscoveryCandidate>()
        val isAsc = criteria.ordering == DiscoveryOrdering.TIMESTAMP_ASC

        // Fetch EVENT candidates
        if (criteria.sourceScope.contains(EvidenceSourceType.EVENT)) {
            when (val eventResult = eventRepository.getEventsForTimeWindow(
                userId = criteria.userId,
                startTime = criteria.startTimeMs,
                endTime = criteria.endTimeMs,
                types = criteria.eventTypes,
                limit = criteria.limit,
                orderAsc = isAsc
            )) {
                is Result.Failure -> return Result.Failure(eventResult.error)
                is Result.Success -> {
                    candidates.addAll(eventResult.data.map {
                        DiscoveryCandidate(EvidenceReference(it.id, EvidenceSourceType.EVENT), it.occurredAt)
                    })
                }
            }
        }

        // Fetch OBSERVATION candidates
        if (criteria.sourceScope.contains(EvidenceSourceType.OBSERVATION)) {
            // Null means all observation types; emptyList means no observation types (0 results).
            if (criteria.observationTypes != null && criteria.observationTypes.isEmpty()) {
                // By definition, no observation types authorized if it's an empty list. Do not query.
            } else {
                when (val obsResult = observationRepository.getObservationsForTimeWindow(
                    userId = criteria.userId,
                    startTimeMs = criteria.startTimeMs,
                    endTimeMs = criteria.endTimeMs,
                    types = criteria.observationTypes,
                    limit = criteria.limit,
                    orderAsc = isAsc
                )) {
                    is Result.Failure -> return Result.Failure(obsResult.error)
                    is Result.Success -> {
                        candidates.addAll(obsResult.data.map {
                            DiscoveryCandidate(EvidenceReference(it.id, EvidenceSourceType.OBSERVATION), it.occurredAt)
                        })
                    }
                }
            }
        }

        // Fetch REFLECTION candidates
        if (criteria.sourceScope.contains(EvidenceSourceType.REFLECTION)) {
            when (val refResult = reflectionRepository.getReflectionsForTimeWindow(
                userId = criteria.userId,
                startTime = criteria.startTimeMs,
                endTime = criteria.endTimeMs,
                limit = criteria.limit,
                orderAsc = isAsc
            )) {
                is Result.Failure -> return Result.Failure(refResult.error)
                is Result.Success -> {
                    candidates.addAll(refResult.data.map {
                        DiscoveryCandidate(EvidenceReference(it.id, EvidenceSourceType.REFLECTION), it.createdAt)
                    })
                }
            }
        }

        // Global sort and deduplication
        // canonical sourceType order: EVENT < OBSERVATION < REFLECTION
        val sourceTypeOrder = mapOf(
            EvidenceSourceType.EVENT to 0,
            EvidenceSourceType.OBSERVATION to 1,
            EvidenceSourceType.REFLECTION to 2,
            EvidenceSourceType.LEGACY_AMBIGUOUS to 3 // Should never happen
        )

        val comparator = Comparator<DiscoveryCandidate> { c1, c2 ->
            val timeCompare = if (isAsc) {
                c1.timestamp.compareTo(c2.timestamp)
            } else {
                c2.timestamp.compareTo(c1.timestamp) // DESC
            }
            if (timeCompare != 0) return@Comparator timeCompare

            val idCompare = c1.reference.sourceId.compareTo(c2.reference.sourceId) // ALWAYS ASC
            if (idCompare != 0) return@Comparator idCompare

            val typeOrder1 = sourceTypeOrder[c1.reference.sourceType] ?: 99
            val typeOrder2 = sourceTypeOrder[c2.reference.sourceType] ?: 99
            typeOrder1.compareTo(typeOrder2) // ALWAYS ASC
        }

        val sortedCandidates = candidates.sortedWith(comparator)

        // Deduplicate and apply final global limit
        val uniqueReferences = sortedCandidates.map { it.reference }.distinct()
        val finalResult = uniqueReferences.take(criteria.limit)

        logger.logLifecycle(
            "EvidenceDiscovery",
            "completed",
            mapOf(
                "sourceScope" to criteria.sourceScope.joinToString(",") { it.name },
                "ordering" to criteria.ordering.name,
                "limit" to criteria.limit,
                "resultCount" to finalResult.size,
                "status" to "SUCCESS"
            )
        )

        return Result.Success(finalResult)
    }
}
