package com.sanket_satpute_20.ironmind.domain.engine

import com.sanket_satpute_20.ironmind.domain.common.Clock
import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.barrier.BarrierHypothesis
import com.sanket_satpute_20.ironmind.domain.model.context.UnifiedContext
import com.sanket_satpute_20.ironmind.domain.model.intervention.InterventionRecommendation
import com.sanket_satpute_20.ironmind.domain.model.pattern.DiscoveredPattern
import com.sanket_satpute_20.ironmind.domain.model.pattern.Pattern
import com.sanket_satpute_20.ironmind.domain.model.pattern.PatternLifecycleState
import com.sanket_satpute_20.ironmind.domain.model.pattern.lifecycleState
import com.sanket_satpute_20.ironmind.domain.repository.BarrierRepository
import com.sanket_satpute_20.ironmind.domain.repository.InterventionRecommendationRepository
import com.sanket_satpute_20.ironmind.domain.repository.PatternRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.ZoneId

data class PipelineExecutionResult(
    val unifiedContext: UnifiedContext,
    val discoveredPatterns: List<DiscoveredPattern>,
    val activePatterns: List<Pattern>,
    val barrierHypotheses: List<BarrierHypothesis>,
    val interventionRecommendations: List<InterventionRecommendation>,
    val insufficientEvidenceBarriers: List<InsufficientEvidenceBarrier>,
    val diagnosticNotes: List<String>
)

/**
 * Phase 3.3: Master orchestrator connecting:
 * UnifiedContext → DiscoveredPatterns → Barrier Hypotheses → Evidence Evaluation → Intervention Recommendations.
 *
 * Deterministic, offline-first, on-demand pipeline callable without background polling.
 */
class BarrierUnderstandingAndInterventionOrchestrator(
    private val contextEngine: ContextEngine,
    private val patternEngine: PatternEngine,
    private val barrierHypothesisDiscoveryEngine: BarrierHypothesisDiscoveryEngine,
    private val interventionReasoningEngine: InterventionReasoningEngine,
    private val patternRepository: PatternRepository,
    private val barrierRepository: BarrierRepository,
    private val interventionRecommendationRepository: InterventionRecommendationRepository,
    private val clock: Clock
) {

    suspend fun executePipeline(
        userId: String,
        windowStartTimeMs: Long,
        windowEndTimeMs: Long,
        zoneId: ZoneId
    ): Result<PipelineExecutionResult, Exception> = withContext(Dispatchers.IO) {
        try {
            require(userId.isNotBlank()) { "userId must not be blank" }

            val diagnosticNotes = mutableListOf<String>()

            // Step 1: Assemble Unified Context
            val contextResult = contextEngine.getUnifiedContext(
                userId = userId,
                windowStartTimeMs = windowStartTimeMs,
                windowEndTimeMs = windowEndTimeMs
            )
            if (contextResult is Result.Failure) {
                return@withContext Result.Failure(contextResult.error)
            }
            val unifiedContext = (contextResult as Result.Success).data

            // Step 2: Discover Behaviour Patterns
            val patternResult = patternEngine.discoverPatterns(unifiedContext, zoneId)
            val newlyDiscovered = if (patternResult is Result.Success) patternResult.data else emptyList()

            // Persist newly discovered established/emerging patterns
            for (dp in newlyDiscovered) {
                if (dp.lifecycleState == PatternLifecycleState.ESTABLISHED || dp.lifecycleState == PatternLifecycleState.EMERGING) {
                    patternRepository.savePattern(dp.toDomainPattern(clock.currentTimeMillis()))
                }
            }

            // Retrieve all patterns for user
            val existingPatternsResult = patternRepository.getPatternsForUser(userId)
            val allPatterns = if (existingPatternsResult is Result.Success) {
                existingPatternsResult.data
            } else {
                newlyDiscovered.map { dp -> dp.toDomainPattern(clock.currentTimeMillis()) }
            }

            // Step 3: Discover Barrier Hypotheses
            val existingBarriersResult = barrierRepository.getBarriersForUser(userId)
            val existingBarriers = if (existingBarriersResult is Result.Success) {
                existingBarriersResult.data
            } else {
                emptyList()
            }

            val barrierDiscoveryResult = barrierHypothesisDiscoveryEngine.discoverBarrierHypotheses(
                userId = userId,
                unifiedContext = unifiedContext,
                patterns = allPatterns,
                existingBarriers = existingBarriers,
                zoneId = zoneId,
                evaluationTimeMs = windowEndTimeMs
            )

            diagnosticNotes.addAll(barrierDiscoveryResult.diagnosticNotes)

            // Persist newly discovered barrier hypotheses
            for (barrier in barrierDiscoveryResult.discoveredHypotheses) {
                barrierRepository.saveBarrier(barrier)
            }

            val allBarriers = existingBarriers + barrierDiscoveryResult.discoveredHypotheses
            val activeBarriers = allBarriers.filter {
                it.userId == userId &&
                    it.status == com.sanket_satpute_20.ironmind.domain.model.barrier.BarrierStatus.ACTIVE &&
                    it.confirmationState != com.sanket_satpute_20.ironmind.domain.model.barrier.BarrierConfirmationState.REJECTED &&
                    it.confirmationState != com.sanket_satpute_20.ironmind.domain.model.barrier.BarrierConfirmationState.DISMISSED
            }

            // Step 4: Generate Intervention Recommendations
            val historicalRecommendationsResult = interventionRecommendationRepository.getRecommendationsForUser(
                userId = userId,
                startTime = windowStartTimeMs - (7L * 24 * 60 * 60 * 1000), // 7 days prior
                endTime = windowEndTimeMs,
                limit = 100
            )
            val historicalRecommendations = if (historicalRecommendationsResult is Result.Success) {
                historicalRecommendationsResult.data
            } else {
                emptyList()
            }

            val recommendations = interventionReasoningEngine.generateRecommendations(
                userId = userId,
                barrierHypotheses = activeBarriers,
                historicalRecommendations = historicalRecommendations,
                currentTimeMs = clock.currentTimeMillis()
            )

            // Persist new pending recommendations
            for (rec in recommendations) {
                interventionRecommendationRepository.saveRecommendation(rec)
            }

            val result = PipelineExecutionResult(
                unifiedContext = unifiedContext,
                discoveredPatterns = newlyDiscovered,
                activePatterns = allPatterns,
                barrierHypotheses = activeBarriers,
                interventionRecommendations = recommendations,
                insufficientEvidenceBarriers = barrierDiscoveryResult.insufficientEvidenceHypotheses,
                diagnosticNotes = diagnosticNotes
            )

            Result.Success(result)
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }
}
