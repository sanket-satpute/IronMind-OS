package com.sanket_satpute_20.ironmind.domain.engine

import com.sanket_satpute_20.ironmind.domain.ai.AIOutput
import com.sanket_satpute_20.ironmind.domain.common.Clock
import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.repository.InterventionRepository

class InterventionPolicyEngineImpl(
    private val interventionRepository: InterventionRepository,
    private val clock: Clock,
    private val cooldownMillis: Long = 15 * 60 * 1000L, // 15 minutes
    private val interruptionBudget: Int = 5
) : InterventionPolicyEngine {

    override suspend fun evaluatePolicy(
        userId: String,
        candidate: AIOutput.InterventionRecommendation
    ): PolicyEvaluationResult {
        val now = clock.currentTimeMillis()
        val startOfDay = getStartOfDay(now)

        // Fetch today's interventions
        val recentResult = interventionRepository.getRecentInterventions(userId, startOfDay)
        val recentInterventions = (recentResult as? Result.Success)?.data ?: emptyList()

        // 1. Check interruption budget
        val deliveredToday = recentInterventions.count { it.state.name == "DELIVERED" || it.state.name == "OUTCOME" || it.state.name == "RESPONSE" || it.state.name == "FAILED" || it.state.name == "TRIGGERED" }
        if (deliveredToday >= interruptionBudget) {
            return PolicyEvaluationResult(
                isAllowed = false,
                isCooldownActive = false,
                isDuplicate = false,
                suppressionReason = "Interruption budget exceeded ($deliveredToday/$interruptionBudget)"
            )
        }

        // 2. Check deduplication (same type and content today)
        val isDuplicate = recentInterventions.any { 
            it.type == candidate.interventionType && 
            it.title == candidate.recommendation && 
            it.description == candidate.reason 
        }
        if (isDuplicate) {
            return PolicyEvaluationResult(
                isAllowed = false,
                isCooldownActive = false,
                isDuplicate = true,
                suppressionReason = "Duplicate intervention"
            )
        }

        // 3. Check cooldowns (same type within cooldown window)
        val cutoffTime = now - cooldownMillis
        val recentSameType = recentInterventions.filter { 
            it.type == candidate.interventionType && it.createdAt >= cutoffTime
        }
        if (recentSameType.isNotEmpty()) {
            return PolicyEvaluationResult(
                isAllowed = false,
                isCooldownActive = true,
                isDuplicate = false,
                suppressionReason = "Cooldown active for type ${candidate.interventionType}"
            )
        }

        // 4. One-primary-intervention rule
        val activeResult = interventionRepository.getActiveInterventions(userId)
        val activeInterventions = (activeResult as? Result.Success)?.data ?: emptyList()
        if (activeInterventions.isNotEmpty()) {
            return PolicyEvaluationResult(
                isAllowed = false,
                isCooldownActive = false,
                isDuplicate = false,
                suppressionReason = "Another primary intervention is currently active"
            )
        }

        // Allowed
        return PolicyEvaluationResult(
            isAllowed = true,
            isCooldownActive = false,
            isDuplicate = false,
            suppressionReason = null
        )
    }

    private fun getStartOfDay(timeMillis: Long): Long {
        // A simple estimation for testing purposes
        return timeMillis - (timeMillis % (24 * 60 * 60 * 1000L))
    }
}
