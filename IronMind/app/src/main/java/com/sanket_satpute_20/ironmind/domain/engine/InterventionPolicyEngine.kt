package com.sanket_satpute_20.ironmind.domain.engine

import com.sanket_satpute_20.ironmind.domain.ai.AIOutput

data class PolicyEvaluationResult(
    val isAllowed: Boolean,
    val isCooldownActive: Boolean,
    val isDuplicate: Boolean,
    val suppressionReason: String? = null
)

interface InterventionPolicyEngine {
    suspend fun evaluatePolicy(
        userId: String,
        candidate: AIOutput.InterventionRecommendation
    ): PolicyEvaluationResult
}
