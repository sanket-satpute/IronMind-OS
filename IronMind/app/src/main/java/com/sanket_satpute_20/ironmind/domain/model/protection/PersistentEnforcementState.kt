package com.sanket_satpute_20.ironmind.domain.model.protection

/**
 * Domain-owned representation of active app restriction enforcement state.
 *
 * Epistemic guarantee: An enforcement state can never independently authorize
 * app blocking without a non-blank originating [sessionId].
 * Furthermore, an enforcement state whose [scheduledEndAt] timestamp has passed
 * is authoritatively inactive.
 */
data class PersistentEnforcementState(
    val sessionId: String?,
    val userId: String,
    val isActive: Boolean,
    val packages: Set<String>,
    val scheduledEndAt: Long? = null,
    val enactedAt: Long = System.currentTimeMillis()
) {
    /**
     * Compatibility alias for existing callers referencing protectedPackages.
     */
    val protectedPackages: Set<String>
        get() = packages

    /**
     * Compatibility alias for existing callers referencing lastUpdatedEpochMs.
     */
    val lastUpdatedEpochMs: Long
        get() = enactedAt

    fun isExpired(currentTimeMillis: Long = System.currentTimeMillis()): Boolean {
        return scheduledEndAt != null && currentTimeMillis >= scheduledEndAt
    }
}
