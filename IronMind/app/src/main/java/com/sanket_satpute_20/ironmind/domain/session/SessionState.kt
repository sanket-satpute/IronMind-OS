package com.sanket_satpute_20.ironmind.domain.session

/**
 * Represents the current user session state of the IronMind application.
 *
 * Guarantees a deterministic, non-null userId for all domain operations:
 * - [Initializing]: Initial cold-start state before cached or remote credentials resolve.
 * - [Authenticated]: Active authenticated session (typically anonymous Firebase user).
 * - [OfflineFallback]: Unauthenticated or offline fallback session, safely operating with legacy ID.
 */
sealed class SessionState {
    object Initializing : SessionState()
    data class Authenticated(val userId: String, val isAnonymous: Boolean) : SessionState()
    data class OfflineFallback(val userId: String = DEFAULT_LEGACY_USER_ID) : SessionState()
    data class ReconciliationFailed(val error: Throwable, val targetUserId: String) : SessionState()

    val currentUserId: String
        get() = when (this) {
            is Authenticated -> userId
            is OfflineFallback -> userId
            is Initializing -> DEFAULT_LEGACY_USER_ID
            is ReconciliationFailed -> DEFAULT_LEGACY_USER_ID
        }

    val isAuthenticated: Boolean
        get() = this is Authenticated

    val isReconciliationFailed: Boolean
        get() = this is ReconciliationFailed

    companion object {
        const val DEFAULT_LEGACY_USER_ID = "user-1"
    }
}
