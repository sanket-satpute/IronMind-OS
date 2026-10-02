package com.sanket_satpute_20.ironmind.domain.model.barrier

import com.sanket_satpute_20.ironmind.domain.ai.BarrierCategory

/**
 * Sprint V2.9: The domain representation of a Barrier Hypothesis.
 * Note: Domain confidence is intentionally omitted as per the contract.
 */
data class BarrierHypothesis(
    val id: String,
    val userId: String,
    val category: BarrierCategory,
    val description: String,
    val confirmationState: BarrierConfirmationState,
    val status: BarrierStatus,
    val sourceReflectionId: String,
    val firstObservedAt: Long,
    val lastObservedAt: Long
)
