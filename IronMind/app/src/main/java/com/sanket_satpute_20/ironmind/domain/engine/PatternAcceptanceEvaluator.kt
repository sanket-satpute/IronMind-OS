package com.sanket_satpute_20.ironmind.domain.engine

import com.sanket_satpute_20.ironmind.domain.model.MemoryConfirmationState
import com.sanket_satpute_20.ironmind.domain.model.pattern.Pattern
import com.sanket_satpute_20.ironmind.domain.model.pattern.PatternCandidate
import com.sanket_satpute_20.ironmind.domain.model.pattern.PatternStatus
import java.time.ZoneId
import kotlin.math.max

sealed class PatternAcceptanceResult {
    data class AcceptedNew(val pattern: Pattern) : PatternAcceptanceResult()
    data class UpdatedExisting(val pattern: Pattern) : PatternAcceptanceResult()
    object NoOp : PatternAcceptanceResult()
    data class Rejected(val reason: String) : PatternAcceptanceResult()
}

class PatternAcceptanceEvaluator(
    private val evidenceSufficiencyEvaluator: EvidenceSufficiencyEvaluator
) {
    fun evaluate(
        candidate: PatternCandidate,
        evidence: List<ValidatedEvidence>,
        existingPattern: Pattern?,
        newPatternId: String,
        currentTimeMillis: Long,
        zoneId: ZoneId,
        userId: String
    ): PatternAcceptanceResult {
        
        // 1. Candidate Validation Boundary
        if (candidate.description.isBlank()) {
            return PatternAcceptanceResult.Rejected("Blank description")
        }

        val normalizedDescription = candidate.description.trim()

        val fingerprint = "${userId}_${candidate.type.name}_${normalizedDescription.lowercase()}"

        // Deduplicate incoming evidence based on identity (sourceId, sourceType)
        val distinctIncomingEvidence = evidence.distinctBy { it.reference.sourceId to it.reference.sourceType }

        // If it's a DELETED pattern, treat as existingPattern = null
        val actualExistingPattern = if (existingPattern?.status == PatternStatus.DELETED) null else existingPattern

        if (actualExistingPattern == null) {
            // NEW Pattern Acceptance Path
            val sufficiencyResult = evidenceSufficiencyEvaluator.evaluate(distinctIncomingEvidence, zoneId)
            
            if (sufficiencyResult is EvidenceSufficiencyResult.Sufficient) {
                val minTimestamp = distinctIncomingEvidence.minOf { it.timestampMs }
                val maxTimestamp = distinctIncomingEvidence.maxOf { it.timestampMs }
                
                val pattern = Pattern(
                    id = newPatternId,
                    userId = userId,
                    fingerprint = fingerprint,
                    type = candidate.type,
                    description = normalizedDescription,
                    conditions = candidate.conditions,
                    predictedBehavior = candidate.predictedBehavior,
                    confidence = 0.8f,
                    evidenceCount = distinctIncomingEvidence.size,
                    evidenceReferences = distinctIncomingEvidence.map { it.reference },
                    firstObservedAt = minTimestamp,
                    lastObservedAt = maxTimestamp,
                    status = PatternStatus.ACTIVE,
                    confirmationState = MemoryConfirmationState.UNCONFIRMED,
                    createdAt = currentTimeMillis,
                    updatedAt = currentTimeMillis
                )
                return PatternAcceptanceResult.AcceptedNew(pattern)
            } else {
                return PatternAcceptanceResult.Rejected("Insufficient evidence")
            }
        } else {
            // EXISTING Pattern Reinforcement Path
            val existingEvidenceIds = actualExistingPattern.evidenceReferences?.map { it.sourceId to it.sourceType }?.toSet() ?: emptySet()
            
            val newEvidenceList = distinctIncomingEvidence.filter { (it.reference.sourceId to it.reference.sourceType) !in existingEvidenceIds }
            
            if (newEvidenceList.isEmpty()) {
                // EXPIRED pattern with NO new evidence is still a NoOp
                return PatternAcceptanceResult.NoOp
            }
            
            val mergedReferences = (actualExistingPattern.evidenceReferences ?: emptyList()) + newEvidenceList.map { it.reference }
            val newMaxTimestamp = newEvidenceList.maxOf { it.timestampMs }
            val updatedLastObservedAt = max(actualExistingPattern.lastObservedAt, newMaxTimestamp)
            
            val updatedPattern = actualExistingPattern.copy(
                description = normalizedDescription, // update in case of case/trim differences
                conditions = candidate.conditions ?: actualExistingPattern.conditions,
                predictedBehavior = candidate.predictedBehavior ?: actualExistingPattern.predictedBehavior,
                confidence = max(actualExistingPattern.confidence, 0.8f),
                evidenceCount = mergedReferences.size,
                evidenceReferences = mergedReferences,
                lastObservedAt = updatedLastObservedAt,
                status = PatternStatus.ACTIVE, // Revives EXPIRED patterns
                updatedAt = currentTimeMillis
            )
            
            return PatternAcceptanceResult.UpdatedExisting(updatedPattern)
        }
    }
}
