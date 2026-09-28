package com.sanket_satpute_20.ironmind.domain.engine

import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.pattern.EvidenceReference
import com.sanket_satpute_20.ironmind.domain.model.pattern.EvidenceResolutionResult
import com.sanket_satpute_20.ironmind.domain.model.pattern.EvidenceSourceType
import com.sanket_satpute_20.ironmind.domain.repository.EventRepository
import com.sanket_satpute_20.ironmind.domain.repository.ObservationRepository
import com.sanket_satpute_20.ironmind.domain.repository.ReflectionRepository

class EvidenceResolver(
    private val observationRepository: ObservationRepository,
    private val eventRepository: EventRepository,
    private val reflectionRepository: ReflectionRepository
) {
    suspend fun resolve(reference: EvidenceReference): EvidenceResolutionResult {
        return try {
            when (reference.sourceType) {
                EvidenceSourceType.OBSERVATION -> {
                    val result = observationRepository.getObservationById(reference.sourceId)
                    if (result is Result.Success) {
                        println("IronMindLifecycle [EvidenceResolver] [RESOLVED] sourceType=OBSERVATION sourceId=${reference.sourceId}")
                        EvidenceResolutionResult.Resolved(reference.sourceId, reference.sourceType, result.data)
                    } else {
                        println("IronMindLifecycle [EvidenceResolver] [MISSING] sourceType=OBSERVATION sourceId=${reference.sourceId}")
                        EvidenceResolutionResult.Missing(reference.sourceId, reference.sourceType)
                    }
                }
                EvidenceSourceType.EVENT -> {
                    val result = eventRepository.getEvent(reference.sourceId)
                    if (result is Result.Success && result.data != null) {
                        println("IronMindLifecycle [EvidenceResolver] [RESOLVED] sourceType=EVENT sourceId=${reference.sourceId}")
                        EvidenceResolutionResult.Resolved(reference.sourceId, reference.sourceType, result.data)
                    } else {
                        println("IronMindLifecycle [EvidenceResolver] [MISSING] sourceType=EVENT sourceId=${reference.sourceId}")
                        EvidenceResolutionResult.Missing(reference.sourceId, reference.sourceType)
                    }
                }
                EvidenceSourceType.REFLECTION -> {
                    val result = reflectionRepository.getReflection(reference.sourceId)
                    if (result is Result.Success && result.data != null) {
                        println("IronMindLifecycle [EvidenceResolver] [RESOLVED] sourceType=REFLECTION sourceId=${reference.sourceId}")
                        EvidenceResolutionResult.Resolved(reference.sourceId, reference.sourceType, result.data)
                    } else {
                        println("IronMindLifecycle [EvidenceResolver] [MISSING] sourceType=REFLECTION sourceId=${reference.sourceId}")
                        EvidenceResolutionResult.Missing(reference.sourceId, reference.sourceType)
                    }
                }
                else -> {
                    println("IronMindLifecycle [EvidenceResolver] [UNSUPPORTED] sourceType=${reference.sourceType}")
                    EvidenceResolutionResult.Unsupported(reference.sourceId, reference.sourceType)
                }
            }
        } catch (e: Exception) {
            println("IronMindLifecycle [EvidenceResolver] [ERROR] sourceType=${reference.sourceType} sourceId=${reference.sourceId} error=${e.message}")
            EvidenceResolutionResult.Error(reference.sourceId, reference.sourceType, e)
        }
    }
}
