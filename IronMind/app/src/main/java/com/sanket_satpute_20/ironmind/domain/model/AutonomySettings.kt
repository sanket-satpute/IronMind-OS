package com.sanket_satpute_20.ironmind.domain.model

/**
 * Domain model representing the autonomy settings for a user.
 */
data class AutonomySettings(
    val userId: String,
    val levels: Map<AutonomyCapability, AutonomyLevel>,
    val isGlobalPauseActive: Boolean = false
) {
    /**
     * Gets the autonomy level for a specific capability.
     * If the global pause is active, this forcefully returns OFF.
     * Otherwise, falls back to a safe default (SUGGEST_ONLY).
     */
    fun getLevel(capability: AutonomyCapability): AutonomyLevel {
        if (isGlobalPauseActive) return AutonomyLevel.OFF
        return levels[capability] ?: AutonomyLevel.SUGGEST_ONLY
    }
}
