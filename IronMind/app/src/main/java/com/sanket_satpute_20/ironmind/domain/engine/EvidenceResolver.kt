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
    suspend fun resolve(userId: String, reference: EvidenceReference): EvidenceResolutionResult {
        return try {
            when (reference.sourceType) {
                EvidenceSourceType.OBSERVATION -> resolveObservation(userId, reference)
                EvidenceSourceType.EVENT -> resolveEvent(userId, reference)
                EvidenceSourceType.REFLECTION -> resolveReflection(userId, reference)
                EvidenceSourceType.LEGACY_AMBIGUOUS -> resolveLegacy(userId, reference)
                else -> {
                    println("IronMindLifecycle [EvidenceResolver] [UNSUPPORTED] userId=$userId sourceType=${reference.sourceType}")
                    EvidenceResolutionResult.Unsupported(reference.sourceId, reference.sourceType)
                }
            }
        } catch (e: Exception) {
            println("IronMindLifecycle [EvidenceResolver] [ERROR] userId=$userId sourceType=${reference.sourceType} sourceId=${reference.sourceId} error=${e.message}")
            EvidenceResolutionResult.Error(reference.sourceId, reference.sourceType, e)
        }
    }

    private suspend fun resolveObservation(userId: String, reference: EvidenceReference): EvidenceResolutionResult {
        return when (val result = observationRepository.getObservation(userId, reference.sourceId)) {
            is Result.Success -> {
                println("IronMindLifecycle [EvidenceResolver] [RESOLVED] userId=$userId sourceType=${EvidenceSourceType.OBSERVATION} sourceId=${reference.sourceId}")
                EvidenceResolutionResult.Resolved(reference.sourceId, EvidenceSourceType.OBSERVATION, result.data)
            }
            is Result.Failure -> {
                if (result.error.message?.contains("not found", ignoreCase = true) == true) {
                    println("IronMindLifecycle [EvidenceResolver] [MISSING] userId=$userId sourceType=${EvidenceSourceType.OBSERVATION} sourceId=${reference.sourceId}")
                    EvidenceResolutionResult.Missing(reference.sourceId, EvidenceSourceType.OBSERVATION)
                } else {
                    println("IronMindLifecycle [EvidenceResolver] [ERROR] userId=$userId sourceType=${EvidenceSourceType.OBSERVATION} sourceId=${reference.sourceId} error=${result.error.message}")
                    EvidenceResolutionResult.Error(reference.sourceId, EvidenceSourceType.OBSERVATION, result.error)
                }
            }
        }
    }

    private suspend fun resolveEvent(userId: String, reference: EvidenceReference): EvidenceResolutionResult {
        return when (val result = eventRepository.getEventForUser(userId, reference.sourceId)) {
            is Result.Success -> {
                if (result.data != null) {
                    println("IronMindLifecycle [EvidenceResolver] [RESOLVED] userId=$userId sourceType=${EvidenceSourceType.EVENT} sourceId=${reference.sourceId}")
                    EvidenceResolutionResult.Resolved(reference.sourceId, EvidenceSourceType.EVENT, result.data)
                } else {
                    println("IronMindLifecycle [EvidenceResolver] [MISSING] userId=$userId sourceType=${EvidenceSourceType.EVENT} sourceId=${reference.sourceId}")
                    EvidenceResolutionResult.Missing(reference.sourceId, EvidenceSourceType.EVENT)
                }
            }
            is Result.Failure -> {
                println("IronMindLifecycle [EvidenceResolver] [ERROR] userId=$userId sourceType=${EvidenceSourceType.EVENT} sourceId=${reference.sourceId} error=${result.error.message}")
                EvidenceResolutionResult.Error(reference.sourceId, EvidenceSourceType.EVENT, result.error)
            }
        }
    }

    private suspend fun resolveReflection(userId: String, reference: EvidenceReference): EvidenceResolutionResult {
        return when (val result = reflectionRepository.getReflectionForUser(userId, reference.sourceId)) {
            is Result.Success -> {
                if (result.data != null) {
                    println("IronMindLifecycle [EvidenceResolver] [RESOLVED] userId=$userId sourceType=${EvidenceSourceType.REFLECTION} sourceId=${reference.sourceId}")
                    EvidenceResolutionResult.Resolved(reference.sourceId, EvidenceSourceType.REFLECTION, result.data)
                } else {
                    println("IronMindLifecycle [EvidenceResolver] [MISSING] userId=$userId sourceType=${EvidenceSourceType.REFLECTION} sourceId=${reference.sourceId}")
                    EvidenceResolutionResult.Missing(reference.sourceId, EvidenceSourceType.REFLECTION)
                }
            }
            is Result.Failure -> {
                println("IronMindLifecycle [EvidenceResolver] [ERROR] userId=$userId sourceType=${EvidenceSourceType.REFLECTION} sourceId=${reference.sourceId} error=${result.error.message}")
                EvidenceResolutionResult.Error(reference.sourceId, EvidenceSourceType.REFLECTION, result.error)
            }
        }
    }

    private suspend fun resolveLegacy(userId: String, reference: EvidenceReference): EvidenceResolutionResult {
        println("IronMindLifecycle [EvidenceResolver] [AMBIGUOUS] userId=$userId sourceId=${reference.sourceId}")
        return EvidenceResolutionResult.Ambiguous(reference.sourceId, EvidenceSourceType.LEGACY_AMBIGUOUS)
    }
}
