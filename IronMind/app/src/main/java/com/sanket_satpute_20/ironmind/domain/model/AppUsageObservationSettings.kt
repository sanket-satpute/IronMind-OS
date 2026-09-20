package com.sanket_satpute_20.ironmind.domain.model

/**
 * Domain model representing the user's preference for app usage observation.
 * Defaults to disabled — the user must explicitly enable it.
 */
data class AppUsageObservationSettings(
    val userId: String,
    val isEnabled: Boolean = false
)
