package com.sanket_satpute_20.ironmind.domain.model

/**
 * Domain model representing the autonomy settings for a user.
 */
data class AutonomySettings(
    val userId: String,
    val levels: Map<AutonomyCapability, AutonomyLevel>
) {
    /**
     * Gets the autonomy level for a specific capability.
     * If not explicitly set, falls back to a safe default (SUGGEST_ONLY).
     */
    fun getLevel(capability: AutonomyCapability): AutonomyLevel {
        return levels[capability] ?: AutonomyLevel.SUGGEST_ONLY
    }
}
