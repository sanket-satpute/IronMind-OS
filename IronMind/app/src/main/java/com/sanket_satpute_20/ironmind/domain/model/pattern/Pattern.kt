package com.sanket_satpute_20.ironmind.domain.model.pattern

import com.sanket_satpute_20.ironmind.domain.model.MemoryConfirmationState

data class Pattern(
    val id: String,
    val userId: String,
    val type: PatternType,
    val description: String,
    val conditions: String?,
    val predictedBehavior: String?,
    val confidence: Float,
    val evidenceCount: Int,
    val evidenceReferences: List<String>?,
    val firstObservedAt: Long,
    val lastObservedAt: Long,
    val status: PatternStatus,
    val confirmationState: MemoryConfirmationState,
    val createdAt: Long,
    val updatedAt: Long
)
