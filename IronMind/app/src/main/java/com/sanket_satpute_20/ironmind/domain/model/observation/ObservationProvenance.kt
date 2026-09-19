package com.sanket_satpute_20.ironmind.domain.model.observation

data class ObservationProvenance(
    val source: ObservationSource,
    val sourceReference: String?,
    val capturedAt: Long
)
