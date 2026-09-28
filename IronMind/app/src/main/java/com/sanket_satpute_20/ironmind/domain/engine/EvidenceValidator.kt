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
    ): Result<List<EvidenceReference>, Exception> {
        // 1. Systemic Error Check
        val firstError = resolvedEvidence.filterIsInstance<EvidenceResolutionResult.Error>().firstOrNull()
        if (firstError != null) {
            return Result.Failure(firstError.error)
        }

        val validReferences = mutableListOf<EvidenceReference>()

        // 2. Validate resolved evidence
        for (result in resolvedEvidence) {
            if (result is EvidenceResolutionResult.Resolved) {
                val entity = result.entity
                
                val isValid = when (entity) {
                    is Observation -> {
                        val hasOwnership = entity.userId == userId
                        val confidenceValid = entity.confidence == null || (entity.confidence >= 0.0f && entity.confidence <= 1.0f)
                        hasOwnership && confidenceValid
                    }
                    is Event -> {
                        entity.userId == userId
                    }
                    is Reflection -> {
                        val hasOwnership = entity.userId == userId
                        val contentValid = entity.content.isNotBlank()
                        hasOwnership && contentValid
                    }
                    else -> false // Unsupported entity type
                }
                
                if (isValid) {
                    validReferences.add(EvidenceReference(result.sourceId, result.sourceType))
                }
            }
        }

        // 3. Deduplicate preserving first occurrence order
        val deduplicated = validReferences.distinct()
        
        return Result.Success(deduplicated)
    }
}
