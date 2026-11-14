package com.sanket_satpute_20.ironmind.domain.provider

import com.sanket_satpute_20.ironmind.domain.model.protection.PersistentEnforcementState

/**
 * Domain-owned persistent store contract for active app restriction enforcement.
 *
 * Epistemic guarantee: Provides synchronous, low-latency persistent state access
 * intended for authoritative crash recovery across Accessibility Service reconnections,
 * process restarts, and app initialization without requiring Room database queries.
 */
interface ProtectionEnforcementStateStore {

    /**
     * Retrieves the currently stored enforcement state, or null if no valid user session is stored.
     */
    fun getEnforcementState(): PersistentEnforcementState?

    /**
     * Persists authoritative enforcement state.
     *
     * @param sessionId originating protection session ID
     * @param userId authenticated user ID
     * @param isActive whether protection is actively enforced
     * @param packages set of package names to enforce
     * @param scheduledEndAt optional expiration timestamp in epoch milliseconds
     */
    fun saveEnforcementState(
        sessionId: String?,
        userId: String,
        isActive: Boolean,
        packages: Set<String>,
        scheduledEndAt: Long? = null
    )

    /**
     * Overload for persisting enforcement state without a specific session ID.
     */
    fun saveEnforcementState(userId: String, isActive: Boolean, packages: Set<String>) {
        saveEnforcementState(sessionId = null, userId = userId, isActive = isActive, packages = packages, scheduledEndAt = null)
    }

    /**
     * Clears any persisted enforcement state, marking it authoritatively inactive.
     */
    fun clearEnforcementState()
}

