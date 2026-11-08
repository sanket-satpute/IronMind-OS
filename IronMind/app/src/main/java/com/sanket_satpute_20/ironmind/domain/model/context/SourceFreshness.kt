package com.sanket_satpute_20.ironmind.domain.model.context

enum class SourceAvailability {
    AVAILABLE,
    PERMISSION_DENIED,
    PERMISSION_NOT_REQUESTED,
    UNAVAILABLE_HARDWARE,
    EMPTY_NO_DATA,
    STALE_DATA,
    PARTIAL_HISTORY
}

enum class SourceFreshness {
    CURRENT,
    STALE,
    EXPIRED
}

enum class UsagePermissionStatus {
    GRANTED,
    DENIED,
    UNAVAILABLE
}

data class SourceProvenanceAndFreshness(
    val sourceName: String,
    val availability: SourceAvailability,
    val isPermissionGranted: Boolean,
    val lastRecordedAt: Long?,
    val dataAgeMs: Long?,
    val isStale: Boolean,
    val notes: String? = null
)

