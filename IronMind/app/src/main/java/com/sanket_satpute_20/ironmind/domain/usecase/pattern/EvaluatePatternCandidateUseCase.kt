package com.sanket_satpute_20.ironmind.domain.usecase.pattern

import com.sanket_satpute_20.ironmind.domain.common.Clock
import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.engine.EvidenceDiscovery
import com.sanket_satpute_20.ironmind.domain.engine.EvidenceResolver
import com.sanket_satpute_20.ironmind.domain.engine.EvidenceValidator
import com.sanket_satpute_20.ironmind.domain.engine.PatternAcceptanceResult
import com.sanket_satpute_20.ironmind.domain.logging.IronLogger
import com.sanket_satpute_20.ironmind.domain.model.pattern.DiscoveryCriteria
import com.sanket_satpute_20.ironmind.domain.model.pattern.DiscoveryOrdering
import com.sanket_satpute_20.ironmind.domain.model.pattern.PatternCandidate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

sealed class EvaluatePatternCandidateResult {
    data class ExpectedRejection(val reason: String) : EvaluatePatternCandidateResult()
    data class SystemFailure(val exception: Exception) : EvaluatePatternCandidateResult()
    data class Accepted(val acceptanceResult: PatternAcceptanceResult) : EvaluatePatternCandidateResult()
}

class EvaluatePatternCandidateUseCase(
    private val clock: Clock,
    private val evidenceDiscovery: EvidenceDiscovery,
    private val evidenceResolver: EvidenceResolver,
    private val evidenceValidator: EvidenceValidator,
    private val patternAcceptanceUseCase: PatternAcceptanceUseCase,
    private val logger: IronLogger
) {
    suspend operator fun invoke(
        userId: String,
        candidate: PatternCandidate,
        zoneId: ZoneId
    ): EvaluatePatternCandidateResult {
        return try {
            // 1. Initial Validation
            val proposal = candidate.discoveryProposal
            if (proposal == null) {
                logRejection(candidate, "Null discovery proposal")
                return EvaluatePatternCandidateResult.ExpectedRejection("Null discovery proposal")
            }

            if (candidate.description.isBlank()) {
                logRejection(candidate, "Invalid candidate: blank description")
                return EvaluatePatternCandidateResult.ExpectedRejection("Invalid candidate: blank description")
            }

            // 2. Domain-Owned Temporal Policy
            val endTimeMs = clock.currentTimeMillis()
            val startTimeMs = endTimeMs - ChronoUnit.DAYS.duration.toMillis() * 30

            // 3. Construct Discovery Criteria
            val criteria = DiscoveryCriteria(
                userId = userId,
                startTimeMs = startTimeMs,
                endTimeMs = endTimeMs,
                sourceScope = proposal.sourceScope ?: emptyList(),
                eventTypes = proposal.eventTypes,
                observationTypes = proposal.observationTypes,
                limit = 50,
                ordering = DiscoveryOrdering.TIMESTAMP_DESC
            )

            // 4. Evidence Discovery
            val discoveryResult = evidenceDiscovery.discover(criteria)
            if (discoveryResult is Result.Failure) {
                logSystemFailure(candidate, "Discovery repository failure", discoveryResult.error)
                return EvaluatePatternCandidateResult.SystemFailure(discoveryResult.error)
            }
            val discoveredReferences = (discoveryResult as Result.Success).data

            if (discoveredReferences.isEmpty()) {
                logRejection(candidate, "Zero evidence discovered")
                return EvaluatePatternCandidateResult.ExpectedRejection("Zero evidence discovered")
            }

            // 5. Evidence Resolution
            val resolutionResults = discoveredReferences.map { ref ->
                evidenceResolver.resolve(userId, ref)
            }

            // 6. Evidence Validation
            val validationResult = evidenceValidator.validate(userId, resolutionResults)
            if (validationResult is Result.Failure) {
                logSystemFailure(candidate, "Evidence resolution/validation error", validationResult.error)
                return EvaluatePatternCandidateResult.SystemFailure(validationResult.error)
            }
            val validatedEvidence = (validationResult as Result.Success).data

            if (validatedEvidence.isEmpty()) {
                logRejection(candidate, "No valid evidence after resolution and validation")
                return EvaluatePatternCandidateResult.ExpectedRejection("No valid evidence after resolution and validation")
            }

            // 7. Sufficiency & 8. Acceptance (Handled sequentially in PatternAcceptanceUseCase)
            val acceptanceResultWrapper = patternAcceptanceUseCase.invoke(
                userId = userId,
                candidate = candidate,
                evidence = validatedEvidence,
                currentTimeMillis = endTimeMs,
                zoneId = zoneId
            )

            if (acceptanceResultWrapper is Result.Failure) {
                logSystemFailure(candidate, "Pattern persistence failure", acceptanceResultWrapper.error)
                return EvaluatePatternCandidateResult.SystemFailure(acceptanceResultWrapper.error)
            }

            val acceptanceResult = (acceptanceResultWrapper as Result.Success).data

            if (acceptanceResult is PatternAcceptanceResult.Rejected) {
                logRejection(candidate, "Acceptance rejected: ${acceptanceResult.reason}")
                return EvaluatePatternCandidateResult.ExpectedRejection(acceptanceResult.reason)
            }

            // Log Success
            val patternId = when (acceptanceResult) {
                is PatternAcceptanceResult.AcceptedNew -> acceptanceResult.pattern.id
                is PatternAcceptanceResult.UpdatedExisting -> acceptanceResult.pattern.id
                else -> null
            }
            logger.logLifecycle(
                component = "EvaluatePatternCandidateUseCase",
                event = "completed",
                parameters = mapOf(
                    "type" to candidate.type.name,
                    "result" to acceptanceResult.javaClass.simpleName,
                    "evidenceCount" to validatedEvidence.size.toString(),
                    "patternId" to (patternId ?: "null")
                )
            )

            EvaluatePatternCandidateResult.Accepted(acceptanceResult)
        } catch (e: Exception) {
            logSystemFailure(candidate, "Unhandled system error", e)
            EvaluatePatternCandidateResult.SystemFailure(e)
        }
    }

    private fun logRejection(candidate: PatternCandidate, reason: String) {
        logger.logLifecycle(
            component = "EvaluatePatternCandidateUseCase",
            event = "rejected",
            parameters = mapOf(
                "type" to candidate.type.name,
                "reason" to reason
            )
        )
    }

    private fun logSystemFailure(candidate: PatternCandidate, category: String, e: Exception) {
        logger.logLifecycle(
            component = "EvaluatePatternCandidateUseCase",
            event = "system_failure",
            parameters = mapOf(
                "type" to candidate.type.name,
                "category" to category,
                "error" to (e.message ?: "Unknown")
            )
        )
    }
}
