package com.sanket_satpute_20.ironmind.domain.model.intervention

import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.CommitmentStatus
import com.sanket_satpute_20.ironmind.domain.model.GoalStatus
import com.sanket_satpute_20.ironmind.domain.repository.CommitmentRepository
import com.sanket_satpute_20.ironmind.domain.repository.GoalRepository

enum class TargetCompletionResult {
    COMPLETED,
    NOT_COMPLETED,
    UNKNOWN,
    COMPLETION_UNDEFINED
}

/**
 * Deterministically resolves whether a specific recommendation target is completed.
 * This is an authoritative domain lookup, explicitly user-scoped, designed to avoid
 * semantic guessing or AI usage.
 */
class TargetCompletionResolver(
    private val goalRepository: GoalRepository,
    private val commitmentRepository: CommitmentRepository
) {
    suspend fun resolve(
        userId: String,
        targetEntityType: String?,
        targetEntityId: String?
    ): TargetCompletionResult {
        if (targetEntityId == null || targetEntityType == null) {
            return TargetCompletionResult.UNKNOWN
        }

        return when (targetEntityType.uppercase()) {
            "GOAL" -> resolveGoalCompletion(userId, targetEntityId)
            "COMMITMENT" -> resolveCommitmentCompletion(userId, targetEntityId)
            "REFLECTION" -> TargetCompletionResult.COMPLETION_UNDEFINED
            else -> TargetCompletionResult.UNKNOWN
        }
    }

    private suspend fun resolveGoalCompletion(userId: String, targetEntityId: String): TargetCompletionResult {
        return when (val result = goalRepository.getGoal(targetEntityId)) {
            is Result.Success -> {
                val goal = result.data
                if (goal == null || goal.userId != userId) {
                    TargetCompletionResult.UNKNOWN
                } else if (goal.status == GoalStatus.COMPLETED) {
                    TargetCompletionResult.COMPLETED
                } else {
                    TargetCompletionResult.NOT_COMPLETED
                }
            }
            is Result.Failure -> TargetCompletionResult.UNKNOWN
        }
    }

    private suspend fun resolveCommitmentCompletion(userId: String, targetEntityId: String): TargetCompletionResult {
        return when (val result = commitmentRepository.getCommitment(targetEntityId)) {
            is Result.Success -> {
                val commitment = result.data
                if (commitment == null || commitment.userId != userId) {
                    TargetCompletionResult.UNKNOWN
                } else if (commitment.status == CommitmentStatus.COMPLETED) {
                    TargetCompletionResult.COMPLETED
                } else {
                    TargetCompletionResult.NOT_COMPLETED
                }
            }
            is Result.Failure -> TargetCompletionResult.UNKNOWN
        }
    }
}
