package com.sanket_satpute_20.ironmind.domain.session

import com.sanket_satpute_20.ironmind.domain.common.Result
import kotlinx.coroutines.flow.StateFlow

/**
 * Central interface supplying the active user identity to ViewModels,
 * repositories, background workers, and AI request builders.
 */
interface UserSessionProvider {
    companion object {
        const val KEY_SESSION_GENERATION = "session_generation"
    }
    /**
     * Observable stream of session states.
     */
    val sessionState: StateFlow<SessionState>

    /**
     * Synchronously returns the currently active user ID.
     * Guaranteed never to return empty or null.
     */
    fun getActiveUserId(): String

    /**
     * Executes [action] with identity lock coordination.
     * Guarantees that [userId] remains constant for the entire logical operation,
     * and that no legacy-to-authenticated reconciliation can commit until [action] completes.
     * If reconciliation is currently in progress, suspends until reconciliation finishes
     * so that [action] receives the reconciled authenticated userId.
     */
    suspend fun <T> withUserSession(action: suspend (userId: String) -> T): T

    /**
     * Executes [action] with exclusive write access coordination.
     * Guarantees that all in-flight shared read operations complete before [action] starts,
     * blocks incoming shared read operations until [action] finishes, and provides the
     * active [userId] for the duration of [action].
     * If reconciliation or another exclusive operation is currently in progress, suspends
     * until it completes. If the session is [SessionState.Initializing], suspends until
     * initialization completes. Throws [IllegalStateException] if session is in
     * [SessionState.ReconciliationFailed].
     */
    suspend fun <T> withExclusiveAccess(action: suspend (userId: String) -> T): T

    /**
     * Executes [action] with exclusive write access coordination, optionally bounded by [drainTimeoutMs].
     * If [drainTimeoutMs] is > 0 and in-flight readers do not complete within the timeout,
     * throws [kotlinx.coroutines.TimeoutCancellationException] without executing [action].
     */
    suspend fun <T> withExclusiveAccess(
        drainTimeoutMs: Long,
        action: suspend (userId: String) -> T
    ): T = withExclusiveAccess(action)

    /**
     * Monotonically increasing generation number for the active session.
     * Advances whenever session identity transitions, preventing operations
     * captured under a previous or erased session from accessing subsequent sessions.
     */
    fun getSessionGeneration(): Long = 0L

    /**
     * Executes [action] validating that the current session generation matches [expectedGeneration].
     * If the session generation has advanced or been erased, rejects the operation immediately.
     */
    suspend fun <T> withUserSession(
        expectedGeneration: Long,
        action: suspend (userId: String) -> T
    ): T = withUserSession(action)

    /**
     * Marks [userId] at [generation] as actively undergoing erasure, closing admission to subsequent operations.
     */
    fun markUserErasing(userId: String, generation: Long = getSessionGeneration()) {}

    /**
     * Permanently marks [userId] at [generation] as erased in the admission fence.
     */
    fun markUserErased(userId: String, generation: Long = getSessionGeneration()) {}

    /**
     * Clears the in-progress erasure marker for [userId] at [generation] if erasure aborted/failed before completion.
     */
    fun clearUserErasing(userId: String, generation: Long = getSessionGeneration()) {}

    /**
     * Returns true if [userId] at [generation] is currently undergoing erasure or has been permanently erased.
     */
    fun isUserErased(userId: String, generation: Long = getSessionGeneration()): Boolean = false

    /**
     * Explicitly permits offline fallback if session is currently in [SessionState.Initializing].
     */
    fun permitOfflineFallback()

    /**
     * Retries reconciliation if the session is currently in [SessionState.ReconciliationFailed].
     */
    suspend fun retryReconciliation(): Result<ReconciliationResult, Exception>
}

/**
 * Safely executes [action] under session barrier coordination.
 * If this [UserSessionProvider] is null (e.g. in standalone tests or fallback mode),
 * executes [action] using [SessionState.DEFAULT_LEGACY_USER_ID].
 */
suspend fun <T> UserSessionProvider?.runWithSession(action: suspend (userId: String) -> T): T {
    return if (this != null) {
        this.withUserSession(action)
    } else {
        action(SessionState.DEFAULT_LEGACY_USER_ID)
    }
}

/**
 * Safely executes [action] under session barrier coordination with explicit [expectedGeneration] validation.
 * If this [UserSessionProvider] is null or [expectedGeneration] is invalid, fails closed and throws [IllegalStateException].
 */
suspend fun <T> UserSessionProvider?.runWithSession(
    expectedGeneration: Long,
    action: suspend (userId: String) -> T
): T {
    if (this == null) {
        throw IllegalStateException("UserSessionProvider is null; cannot validate session generation $expectedGeneration")
    }
    if (expectedGeneration <= 0L) {
        throw IllegalStateException("Cannot execute with invalid session generation $expectedGeneration")
    }
    return this.withUserSession(expectedGeneration, action)
}

/**
 * Safely executes [action] under exclusive session barrier coordination.
 * If this [UserSessionProvider] is null (e.g. in standalone tests or fallback mode),
 * executes [action] using [SessionState.DEFAULT_LEGACY_USER_ID].
 */
suspend fun <T> UserSessionProvider?.runWithExclusiveAccess(
    drainTimeoutMs: Long = 0L,
    action: suspend (userId: String) -> T
): T {
    return if (this != null) {
        this.withExclusiveAccess(drainTimeoutMs, action)
    } else {
        action(SessionState.DEFAULT_LEGACY_USER_ID)
    }
}
