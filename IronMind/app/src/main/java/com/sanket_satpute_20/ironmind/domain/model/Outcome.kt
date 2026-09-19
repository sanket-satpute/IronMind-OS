package com.sanket_satpute_20.ironmind.domain.model

data class Outcome(
    val id: String,
    val userId: String,
    val sourceEntityId: String,
    val sourceEntityType: String, // e.g. "COMMITMENT"
    val resultStatus: ResultStatus,
    val actualDurationMinutes: Int?,
    val completedAt: Long,
    val createdAt: Long,
    val schemaVersion: Int = 1
)
