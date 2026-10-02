package com.sanket_satpute_20.ironmind.domain.model.intervention

import com.sanket_satpute_20.ironmind.domain.ai.BarrierCategory
import com.sanket_satpute_20.ironmind.domain.model.CommitmentStatus
import com.sanket_satpute_20.ironmind.domain.model.GoalStatus
import com.sanket_satpute_20.ironmind.domain.model.barrier.BarrierConfirmationState
import com.sanket_satpute_20.ironmind.domain.model.observation.ObservationType
import com.sanket_satpute_20.ironmind.domain.model.pattern.PatternType

data class RecommendationContext(
    val userId: String,
    val activeGoals: List<ContextGoal>,
    val activeCommitments: List<ContextCommitment>,
    val recentObservations: List<ContextObservation>,
    val activePatterns: List<ContextPattern>,
    val activeBarriers: List<ContextBarrier>,
    val recentReflections: List<ContextReflection>
)

data class ContextGoal(
    val id: String,
    val title: String,
    val description: String,
    val status: GoalStatus,
    val targetAt: Long?
)

data class ContextCommitment(
    val id: String,
    val title: String,
    val description: String,
    val status: CommitmentStatus,
    val scheduledStartAt: Long?,
    val scheduledEndAt: Long?
)

data class ContextObservation(
    val id: String,
    val type: ObservationType,
    val value: String,
    val context: String,
    val occurredAt: Long
)

data class ContextPattern(
    val id: String,
    val type: PatternType,
    val description: String
)

data class ContextBarrier(
    val id: String,
    val category: BarrierCategory,
    val description: String,
    val confirmationState: BarrierConfirmationState
)

data class ContextReflection(
    val id: String,
    val content: String,
    val createdAt: Long
)
