package com.sanket_satpute_20.ironmind.domain.model.experiment

/**
 * Tracks a behavioral or timing experiment proposed by the system and approved by the user.
 * Example: Try a 15-minute commitment vs a 30-minute commitment.
 */
data class ExperimentRecord(
    val id: String,
    val userId: String,
    val hypothesis: String,
    val activeVariation: String,
    val controlVariation: String,
    val targetMetric: String,
    val state: ExperimentState,
    val startedAt: Long,
    val endedAt: Long? = null,
    val outcomeSummary: String? = null
)
