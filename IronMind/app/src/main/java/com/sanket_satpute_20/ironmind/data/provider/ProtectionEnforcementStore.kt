package com.sanket_satpute_20.ironmind.data.provider

import android.content.Context
import android.content.SharedPreferences
import com.sanket_satpute_20.ironmind.domain.common.Clock
import com.sanket_satpute_20.ironmind.domain.model.protection.PersistentEnforcementState
import com.sanket_satpute_20.ironmind.domain.provider.ProtectionEnforcementStateStore

/**
 * Thread-safe persistent store for active app restriction enforcement.
 * Ensures the Accessibility Service can rehydrate protected package boundaries
 * when rebound or recreated after Android OS process termination.
 *
 * Epistemic guarantee: A persistent enforcement state can never independently
 * authorize app blocking without a non-blank originating [sessionId].
 * Furthermore, an enforcement state whose [scheduledEndAt] timestamp has passed
 * is authoritatively inactive.
 */
class ProtectionEnforcementStore(
    context: Context,
    private val clock: Clock? = null
) : ProtectionEnforcementStateStore {
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun now(): Long = clock?.currentTimeMillis() ?: Companion.clock?.currentTimeMillis() ?: System.currentTimeMillis()

    override fun saveEnforcementState(
        sessionId: String?,
        userId: String,
        isActive: Boolean,
        packages: Set<String>,
        scheduledEndAt: Long?
    ) {
        val filtered = packages.filter { it.isNotBlank() && it != IRONMIND_PACKAGE }.toSet()
        val isExpired = scheduledEndAt != null && now() >= scheduledEndAt
        val genuinelyActive = isActive && filtered.isNotEmpty() && !sessionId.isNullOrBlank() && !isExpired
        prefs.edit()
            .putString(KEY_SESSION_ID, sessionId)
            .putString(KEY_USER_ID, userId)
            .putBoolean(KEY_IS_ACTIVE, genuinelyActive)
            .putStringSet(KEY_PACKAGES, if (genuinelyActive) filtered else emptySet())
            .putLong(KEY_SCHEDULED_END_AT, scheduledEndAt ?: -1L)
            .putLong(KEY_LAST_UPDATED, now())
            .apply()
    }

    override fun saveEnforcementState(userId: String, isActive: Boolean, packages: Set<String>) {
        saveEnforcementState(sessionId = null, userId = userId, isActive = isActive, packages = packages, scheduledEndAt = null)
    }

    override fun clearEnforcementState() {
        prefs.edit()
            .putString(KEY_SESSION_ID, null)
            .putBoolean(KEY_IS_ACTIVE, false)
            .putStringSet(KEY_PACKAGES, emptySet())
            .putString(KEY_USER_ID, null)
            .putLong(KEY_SCHEDULED_END_AT, -1L)
            .putLong(KEY_LAST_UPDATED, System.currentTimeMillis())
            .apply()
    }

    override fun getEnforcementState(): PersistentEnforcementState? {
        val isActive = prefs.getBoolean(KEY_IS_ACTIVE, false)
        val userId = prefs.getString(KEY_USER_ID, null) ?: return null
        val sessionId = prefs.getString(KEY_SESSION_ID, null)
        val packages = prefs.getStringSet(KEY_PACKAGES, emptySet())?.filter {
            it.isNotBlank() && it != IRONMIND_PACKAGE
        }?.toSet() ?: emptySet()
        val scheduledEndAtRaw = prefs.getLong(KEY_SCHEDULED_END_AT, -1L)
        val scheduledEndAt = if (scheduledEndAtRaw > 0L) scheduledEndAtRaw else null
        val lastUpdated = prefs.getLong(KEY_LAST_UPDATED, 0L)

        val hasValidSession = !sessionId.isNullOrBlank()
        val isExpired = scheduledEndAt != null && now() >= scheduledEndAt
        val genuinelyActive = isActive && packages.isNotEmpty() && hasValidSession && !isExpired

        return PersistentEnforcementState(
            sessionId = sessionId,
            userId = userId,
            isActive = genuinelyActive,
            packages = if (genuinelyActive) packages else emptySet(),
            scheduledEndAt = scheduledEndAt,
            enactedAt = lastUpdated
        )
    }

    companion object {
        var clock: Clock? = null

        const val IRONMIND_PACKAGE = "com.sanket_satpute_20.ironmind"
        private const val PREFS_NAME = "ironmind_protection_enforcement"
        private const val KEY_SESSION_ID = "originating_session_id"
        private const val KEY_USER_ID = "active_user_id"
        private const val KEY_IS_ACTIVE = "is_active"
        private const val KEY_PACKAGES = "protected_packages"
        private const val KEY_SCHEDULED_END_AT = "scheduled_end_at_epoch_ms"
        private const val KEY_LAST_UPDATED = "last_updated_epoch_ms"
    }
}