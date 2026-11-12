package com.sanket_satpute_20.ironmind.domain.usecase.model

import com.sanket_satpute_20.ironmind.domain.ai.AIOutput
import com.sanket_satpute_20.ironmind.domain.ai.AIRequest
import com.sanket_satpute_20.ironmind.domain.ai.AIRequestType
import com.sanket_satpute_20.ironmind.domain.ai.IronMindAI
import com.sanket_satpute_20.ironmind.domain.ai.isValid
import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.engine.ContextEngine
import com.sanket_satpute_20.ironmind.domain.model.MemoryConfirmationState
import com.sanket_satpute_20.ironmind.domain.model.MemoryStatus
import com.sanket_satpute_20.ironmind.domain.model.pattern.PatternStatus
import com.sanket_satpute_20.ironmind.domain.repository.PatternRepository
import com.sanket_satpute_20.ironmind.domain.repository.UserProfileRepository
import com.sanket_satpute_20.ironmind.domain.usecase.pattern.EvaluatePatternCandidatesUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.ZoneId

/**
 * Sprint V4.11 / Phase 7: Personal Model Evolution.
 *
 * Evolve the structured understanding of the user by synthesizing reality
 * (intent, confirmed information, behavior, patterns) and decaying weak assumptions.
 * Candidates proposed by AI are passed to [EvaluatePatternCandidatesUseCase]
 * which discovers, validates evidence, checks confidence thresholds, and persists
 * accepted patterns into [PatternRepository].
 */
class EvolvePersonalModelUseCase(
    private val ironMindAI: IronMindAI,
    private val contextEngine: ContextEngine,
    private val patternRepository: PatternRepository,
    private val evaluatePatternCandidatesUseCase: EvaluatePatternCandidatesUseCase? = null,
    private val userProfileRepository: UserProfileRepository? = null
) {
    suspend operator fun invoke(userId: String): Result<Unit, Exception> = withContext(Dispatchers.IO) {
        try {
            if (userId.isBlank()) {
                return@withContext Result.Failure(IllegalArgumentException("userId must not be blank"))
            }

            // 1. Gather comprehensive context representation
            val contextResult = contextEngine.getCurrentContext(userId)
            if (contextResult !is Result.Success) {
                return@withContext Result.Failure(Exception("Failed to gather context for model evolution."))
            }
            val contextSnapshot = contextResult.data
            if (contextSnapshot.factualContextSnapshot.userId.isNotBlank() &&
                contextSnapshot.factualContextSnapshot.userId != userId
            ) {
                return@withContext Result.Failure(IllegalStateException("Context snapshot user mismatch for userId=$userId"))
            }

            // Avoid unnecessary AI calls when there is insufficient new evidence.
            // Eligible memories participate in evidence context, but memories alone cannot bypass primary evidence requirements.
            val hasValidAppUsage = (contextSnapshot.factualContextSnapshot.appUsage?.observationCount ?: 0) > 0
            val hasValidActivity = contextSnapshot.factualContextSnapshot.activity?.let { agg ->
                agg.observationCount > 0 && agg.perStateObservationCounts.any { (state, count) ->
                    count > 0 && !state.equals("UNKNOWN", ignoreCase = true)
                }
            } ?: false

            val hasPrimaryEvidence = contextSnapshot.recentEvents.isNotEmpty() ||
                contextSnapshot.recentReflections.isNotEmpty() ||
                contextSnapshot.recentPatterns.isNotEmpty() ||
                contextSnapshot.activeCommitments.isNotEmpty() ||
                contextSnapshot.activeGoals.isNotEmpty() ||
                hasValidAppUsage ||
                hasValidActivity

            val eligibleMemories = contextSnapshot.activeMemories.filter {
                it.userId == userId &&
                it.status !in setOf(MemoryStatus.EXPIRED, MemoryStatus.DELETED, MemoryStatus.INACTIVE) &&
                it.confirmationState != MemoryConfirmationState.USER_REJECTED
            }

            // Primary evidence is strictly required; memories alone cannot trigger model evolution
            val hasEvidence = hasPrimaryEvidence

            if (!hasEvidence) {
                return@withContext Result.Success(Unit)
            }

            // 2. Request AI evaluation of the current model against reality
            val requestInput = """
                Evaluate the provided context to evolve the personal model.
                1. Propose PatternCandidates if strong evidence supports them.
                2. If a candidate contradicts an existing pattern, include the existing pattern's ID as contradictionSignal.
                Remember: You do not have authority to declare facts; you may only propose candidates.
            """.trimIndent()

            val request = AIRequest(
                userId = userId,
                input = requestInput,
                requestType = AIRequestType.MODEL_EVOLUTION,
                contextSnapshot = contextSnapshot
            )

            val aiResult = ironMindAI.process(request)
            if (aiResult !is Result.Success) {
                return@withContext Result.Failure((aiResult as Result.Failure).error)
            }

            val aiOutput = aiResult.data
            if (!aiOutput.isValid() || aiOutput !is AIOutput.ModelEvolution) {
                return@withContext Result.Success(Unit)
            }

            // 3. Domain Learning Boundary
            // AI outputs PatternCandidates, not confirmed Patterns.
            // Candidates are evaluated against historical evidence before acceptance and persistence.
            val candidates = aiOutput.evolvedCandidates

            if (candidates.isNotEmpty() && evaluatePatternCandidatesUseCase != null) {
                val profileResult = userProfileRepository?.getProfile(userId)
                val zoneId = if (profileResult is Result.Success && profileResult.data?.timezone != null) {
                    try {
                        ZoneId.of(profileResult.data!!.timezone)
                    } catch (_: Exception) {
                        ZoneId.systemDefault()
                    }
                } else {
                    ZoneId.systemDefault()
                }

                val evalResult = evaluatePatternCandidatesUseCase(
                    userId = userId,
                    candidates = candidates,
                    zoneId = zoneId
                )
                if (evalResult is Result.Failure) {
                    return@withContext Result.Failure(evalResult.error)
                }
            } else {
                println("IronMindLifecycle [PatternLearning] [CANDIDATES_RECEIVED] userId=$userId candidateCount=${candidates.size}")
            }

            Result.Success(Unit)

        } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) {
            Result.Failure(e)
        }
    }
}
