package com.sanket_satpute_20.ironmind.domain.model.observation

/**
 * A coarse location snapshot. Only latitude and longitude are stored.
 * No address resolution, no place names, no continuous tracking.
 * Precision is intentionally limited to coarse level to avoid surveillance.
 */
data class LocationSnapshot(
    val latitude: Double,
    val longitude: Double
)
