package com.sanket_satpute_20.ironmind.domain.engine

import com.sanket_satpute_20.ironmind.domain.model.pattern.EvidenceReference
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

/**
 * The input for the EvidenceSufficiencyEvaluator.
 * It contains the identity of the evidence and the authoritative timestamp.
 */
data class ValidatedEvidence(
    val reference: EvidenceReference,
    val timestampMs: Long
)

enum class EvidenceSufficiencyReason {
    INSUFFICIENT_UNIQUE_EVIDENCE,
    INSUFFICIENT_DISTINCT_CALENDAR_DAYS
}

sealed class EvidenceSufficiencyResult {
    object Sufficient : EvidenceSufficiencyResult()
    data class Insufficient(val reason: EvidenceSufficiencyReason) : EvidenceSufficiencyResult()
    data class Error(val exception: Throwable) : EvidenceSufficiencyResult()
}

/**
 * Domain boundary evaluator for Evidence Sufficiency.
 * This is a pure function with no repository dependencies.
 */
class EvidenceSufficiencyEvaluator {

    companion object {
        const val MINIMUM_UNIQUE_EVIDENCE_COUNT = 3
        const val MINIMUM_DISTINCT_CALENDAR_DAYS = 2
    }

    fun evaluate(
        evidence: List<ValidatedEvidence>,
        evaluationTimezone: ZoneId
    ): EvidenceSufficiencyResult {
        try {
            // 2. Deduplicate by EvidenceReference identity.
            val distinctEvidence = evidence.distinctBy { it.reference }

            // 3. Determine unique evidence count.
            // 4. If unique count < 3: return INSUFFICIENT.
            if (distinctEvidence.size < MINIMUM_UNIQUE_EVIDENCE_COUNT) {
                println("IronMindLifecycle [EvidenceSufficiency] [EVALUATED] uniqueEvidenceCount=${distinctEvidence.size} distinctCalendarDays=0 result=INSUFFICIENT(${EvidenceSufficiencyReason.INSUFFICIENT_UNIQUE_EVIDENCE})")
                return EvidenceSufficiencyResult.Insufficient(EvidenceSufficiencyReason.INSUFFICIENT_UNIQUE_EVIDENCE)
            }

            // 5. Convert each evidence timestamp to a calendar date using the supplied execution-context timezone.
            // 6. Count distinct calendar dates.
            val distinctCalendarDays = distinctEvidence.map {
                ZonedDateTime.ofInstant(Instant.ofEpochMilli(it.timestampMs), evaluationTimezone)
                    .truncatedTo(ChronoUnit.DAYS)
            }.distinct().count()

            // 7. If distinct calendar days < 2: return INSUFFICIENT.
            val result = if (distinctCalendarDays < MINIMUM_DISTINCT_CALENDAR_DAYS) {
                EvidenceSufficiencyResult.Insufficient(EvidenceSufficiencyReason.INSUFFICIENT_DISTINCT_CALENDAR_DAYS)
            } else {
                EvidenceSufficiencyResult.Sufficient
            }

            val resultName = when (result) {
                is EvidenceSufficiencyResult.Sufficient -> "SUFFICIENT"
                is EvidenceSufficiencyResult.Insufficient -> "INSUFFICIENT(${result.reason})"
                else -> "UNKNOWN"
            }

            println("IronMindLifecycle [EvidenceSufficiency] [EVALUATED] uniqueEvidenceCount=${distinctEvidence.size} distinctCalendarDays=$distinctCalendarDays result=$resultName")

            // 8. Otherwise: return SUFFICIENT.
            return result
        } catch (e: Exception) {
            println("IronMindLifecycle [EvidenceSufficiency] [EVALUATED] uniqueEvidenceCount=0 distinctCalendarDays=0 result=ERROR")
            return EvidenceSufficiencyResult.Error(e)
        }
    }
}
