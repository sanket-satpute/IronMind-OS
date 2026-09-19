package com.sanket_satpute_20.ironmind.domain.model.observation

data class Observation(
    val id: String,
    val userId: String,
    val type: ObservationType,
    val source: ObservationSource,
    val occurredAt: Long,
    val recordedAt: Long,
    val subjectId: String?,
    val value: String,
    val context: String,
    val confidence: Float?,
    val provenance: ObservationProvenance,
    val schemaVersion: Int = 1
)
