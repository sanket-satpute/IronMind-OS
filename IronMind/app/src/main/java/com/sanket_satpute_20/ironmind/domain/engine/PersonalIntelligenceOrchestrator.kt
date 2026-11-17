package com.sanket_satpute_20.ironmind.domain.engine

import com.sanket_satpute_20.ironmind.domain.common.Clock
import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.insight.PersonalInsight
import com.sanket_satpute_20.ironmind.domain.repository.InsightRepository
import com.sanket_satpute_20.ironmind.domain.repository.InterventionRecommendationRepository
import com.sanket_satpute_20.ironmind.domain.repository.OutcomeRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.ZoneId

data class PersonalIntelligenceResult(
    val pipelineResult: PipelineExecutionResult,
    val newlyDiscoveredInsights: List<PersonalInsight>,
    val allActiveInsights: List<PersonalInsight>,
    val suppressedCount: Int,
    val diagnosticNotes: List<String>
)

/**
 * Phase 3.4: Master intelligence orchestrator connecting:
 * UnifiedContext → Pattern Discovery → Barrier Understanding → Intervention Reasoning → Personal Insights.
 *
 * Deterministic, on-demand, offline-first pipeline that avoids duplicate recommendations and insights,
 * strictly enforces epistemic authority distinctions, never automatically confirms barriers,
 * never executes interventions autonomously, and learns from verified intervention outcomes.
 */
class PersonalIntelligenceOrchestrator(
    private val barrierUnderstandingOrchestrator: BarrierUnderstandingAndInterventionOrchestrator,
    private val personalInsightDiscoveryEngine: PersonalInsightDiscoveryEngine,
    private val insightRepository: InsightRepository,
    private val outcomeRepository: OutcomeRepository,
    private val clock: Clock,
    private val recommendationRepository: InterventionRecommendationRepository? = null
) {

    suspend fun orchestrate(
        userId: String,
        windowStartTimeMs: Long,
        windowEndTimeMs: Long,
        zoneId: ZoneId
    ): Result<PersonalIntelligenceResult, Exception> = withContext(Dispatchers.IO) {
        try {
            require(userId.isNotBlank()) { "userId must not be blank" }

            val allDiagnostics = mutableListOf<String>()

            // Step 1 - 4: Run UnifiedContext -> Pattern -> Barrier -> Intervention pipeline
            val pipelineResult = when (val res = barrierUnderstandingOrchestrator.executePipeline(
                userId = userId,
                windowStartTimeMs = windowStartTimeMs,
                windowEndTimeMs = windowEndTimeMs,
                zoneId = zoneId
            )) {
                is Result.Success -> res.data
                is Result.Failure -> return@withContext Result.Failure(res.error)
            }

            allDiagnostics.addAll(pipelineResult.diagnosticNotes)

            // Step 5: Retrieve existing insights for deduplication and dismissal checking
            val existingInsights = when (val res = insightRepository.getInsightsForUser(userId)) {
                is Result.Success -> res.data
                is Result.Failure -> emptyList()
            }

            // Step 6: Retrieve outcomes for intervention outcome learning
            val outcomes = when (val res = outcomeRepository.getOutcomesForUser(userId)) {
                is Result.Success -> res.data
                is Result.Failure -> emptyList()
            }

            // Retrieve all relevant recommendations (historical + newly generated)
            val historicalRecs = if (recommendationRepository != null) {
                when (val res = recommendationRepository.getRecommendationsForUser(
                    userId = userId,
                    startTime = windowStartTimeMs - (30L * 24 * 60 * 60 * 1000),
                    endTime = windowEndTimeMs,
                    limit = 100
                )) {
                    is Result.Success -> res.data
                    is Result.Failure -> emptyList()
                }
            } else {
                emptyList()
            }
            val allRecommendations = (pipelineResult.interventionRecommendations + historicalRecs).distinctBy { it.id }

            // Step 7: Discover Personal Insights
            val discoveryResult = personalInsightDiscoveryEngine.discoverInsights(
                userId = userId,
                unifiedContext = pipelineResult.unifiedContext,
                patterns = pipelineResult.activePatterns,
                barrierHypotheses = pipelineResult.barrierHypotheses,
                recommendations = allRecommendations,
                outcomes = outcomes,
                existingInsights = existingInsights,
                zoneId = zoneId
            )

            allDiagnostics.addAll(discoveryResult.diagnosticNotes)

            // Step 8: Persist newly discovered insights
            for (insight in discoveryResult.discoveredInsights) {
                insightRepository.saveInsight(insight)
            }

            // Step 9: Re-fetch updated list of insights
            val updatedInsights = when (val res = insightRepository.getInsightsForUser(userId)) {
                is Result.Success -> res.data
                is Result.Failure -> existingInsights + discoveryResult.discoveredInsights
            }

            val finalResult = PersonalIntelligenceResult(
                pipelineResult = pipelineResult,
                newlyDiscoveredInsights = discoveryResult.discoveredInsights,
                allActiveInsights = updatedInsights,
                suppressedCount = discoveryResult.suppressedCount,
                diagnosticNotes = allDiagnostics
            )

            Result.Success(finalResult)
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }
}
