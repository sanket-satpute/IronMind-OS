package com.sanket_satpute_20.ironmind.domain.session

import com.sanket_satpute_20.ironmind.domain.common.Result

data class ReconciliationResult(
    val isReconciled: Boolean,
    val recordsUpdated: Int,
    val message: String
)

/**
 * Reconciles local pre-auth legacy data (e.g. "user-1") with an authentic
 * Firebase UID in a single atomic database transaction.
 */
interface UserIdentityReconciler {
    /**
     * Executes reconciliation from [legacyUserId] to [authenticatedUserId].
     *
     * Invariants:
     * - Must execute in a single atomic transaction.
     * - Failures must roll back all changes completely without data loss.
     * - Operations must be idempotent.
     * - Tables with composite or primary keys on userId must be merged without constraint violations.
     * - Queued outbox payloads containing legacy userId must be rewritten.
     */
    suspend fun reconcile(
        legacyUserId: String = SessionState.DEFAULT_LEGACY_USER_ID,
        authenticatedUserId: String
    ): Result<ReconciliationResult, Exception>
}

/**
 * Thrown when identity reconciliation detects one or more pattern fingerprints
 * that exist under both the legacy user and target user, preventing a unique-index violation
 * while preserving all historical pattern and evidence data intact.
 */
class PatternFingerprintCollisionException(
    message: String,
    val conflictingFingerprints: List<String>
) : IllegalStateException(message)
