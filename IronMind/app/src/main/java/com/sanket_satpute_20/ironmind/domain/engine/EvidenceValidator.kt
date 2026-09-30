package com.sanket_satpute_20.ironmind.domain.engine

import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.Event
import com.sanket_satpute_20.ironmind.domain.model.Reflection
import com.sanket_satpute_20.ironmind.domain.model.observation.Observation
import com.sanket_satpute_20.ironmind.domain.model.pattern.EvidenceReference
import com.sanket_satpute_20.ironmind.domain.model.pattern.EvidenceResolutionResult

class EvidenceValidator {
    
    fun validate(
        userId: String,
        resolvedEvidence: List<EvidenceResolutionResult>
    ): Result<List<ValidatedEvidence>, Exception> {
        // 1. Systemic Error Check
        val firstError = resolvedEvidence.filterIsInstance<EvidenceResolutionResult.Error>().firstOrNull()
        if (firstError != null) {
            return Result.Failure(firstError.error)
        }

        val validEvidence = mutableListOf<ValidatedEvidence>()

        // 2. Validate resolved evidence
        for (result in resolvedEvidence) {
            if (result is EvidenceResolutionResult.Resolved) {
                val entity = result.entity
                
                var timestampMs: Long? = null
                
                val isValid = when (entity) {
                    is Observation -> {
                        val hasOwnership = entity.userId == userId
                        val confidenceValid = entity.confidence == null || (entity.confidence >= 0.0f && entity.confidence <= 1.0f)
                        if (hasOwnership && confidenceValid) timestampMs = entity.occurredAt
                        hasOwnership && confidenceValid
                    }
                    is Event -> {
                        if (entity.userId == userId) timestampMs = entity.occurredAt
                        entity.userId == userId
                    }
                    is Reflection -> {
                        val hasOwnership = entity.userId == userId
                        val contentValid = entity.content.isNotBlank()
                        if (hasOwnership && contentValid) timestampMs = entity.createdAt
                        hasOwnership && contentValid
                    }
                    else -> false // Unsupported entity type
                }
                
                if (isValid && timestampMs != null) {
                    validEvidence.add(ValidatedEvidence(EvidenceReference(result.sourceId, result.sourceType), timestampMs))
                }
            }
        }

        // 3. Deduplicate preserving first occurrence order
        val deduplicated = validEvidence.distinctBy { it.reference.sourceId to it.reference.sourceType }
        
        return Result.Success(deduplicated)
    }
}
