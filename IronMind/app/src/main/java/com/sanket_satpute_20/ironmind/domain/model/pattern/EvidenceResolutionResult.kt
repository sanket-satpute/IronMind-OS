package com.sanket_satpute_20.ironmind.domain.model.pattern

sealed class EvidenceResolutionResult {
    data class Resolved(val sourceId: String, val sourceType: EvidenceSourceType, val entity: Any) : EvidenceResolutionResult()
    data class Missing(val sourceId: String, val sourceType: EvidenceSourceType) : EvidenceResolutionResult()
    data class Unsupported(val sourceId: String, val sourceType: EvidenceSourceType) : EvidenceResolutionResult()
    data class Error(val sourceId: String, val sourceType: EvidenceSourceType, val error: Exception) : EvidenceResolutionResult()
    data class Ambiguous(val sourceId: String, val sourceType: EvidenceSourceType) : EvidenceResolutionResult()
    data class OwnershipMismatch(val sourceId: String, val sourceType: EvidenceSourceType) : EvidenceResolutionResult()
}
