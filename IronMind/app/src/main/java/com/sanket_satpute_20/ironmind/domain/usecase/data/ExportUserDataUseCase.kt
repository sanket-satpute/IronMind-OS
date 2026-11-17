package com.sanket_satpute_20.ironmind.domain.usecase.data

import com.sanket_satpute_20.ironmind.domain.common.Clock
import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.repository.DataExportRepository
import com.sanket_satpute_20.ironmind.domain.session.SessionState
import com.sanket_satpute_20.ironmind.domain.session.UserSessionProvider
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException

/**
 * Use case to coordinate an atomic, session-safe snapshot of all user-owned domain data.
 *
 * Guarantees & Lifecycle Safety (Phase 8.3.2 Stage 3A.2):
 * 1. Single-Flight Concurrency: Rejects overlapping concurrent export requests across the complete lifecycle.
 * 2. Continuous Read Barrier: Holds the session read barrier (withUserSession) continuously through
 *    session validation, snapshot retrieval, and file serialization/promotion.
 * 3. Erasure Synchronization: Account erasure (DeleteUserDataUseCase) will wait for in-flight exports
 *    to complete before beginning its destructive phase.
 * 4. Admission Fence Validation: Rejects operations for erased or initializing sessions.
 * 5. Cooperative Cancellation: Re-throws CancellationException directly, preserving coroutine cancellation semantics.
 */
class ExportUserDataUseCase(
    private val dataExportRepository: DataExportRepository,
    private val userSessionProvider: UserSessionProvider? = null,
    private val clock: Clock? = null
) {
    private val isExporting = AtomicBoolean(false)

    /**
     * Captures an in-memory snapshot of user data under session barrier coordination.
     */
    suspend operator fun invoke(requestedUserId: String? = null): Result<UserDataExport, Exception> {
        return executeInExportSession(requestedUserId) { _, snapshot ->
            Result.Success(snapshot)
        }
    }

    /**
     * Executes the complete user data export lifecycle within a continuous session read barrier.
     * Guarantees that single-flight locking and session read access remain active throughout [block].
     */
    suspend fun <T> executeInExportSession(
        requestedUserId: String? = null,
        block: suspend (userId: String, snapshot: UserDataExport) -> Result<T, Exception>
    ): Result<T, Exception> {
        // Enforce single-flight export coordination across the entire lifecycle
        if (!isExporting.compareAndSet(false, true)) {
            return Result.Failure(IllegalStateException("User data export is already in progress"))
        }

        try {
            // Step 1: Session state validation
            val sessionState = userSessionProvider?.sessionState?.value
            val isLegacyRequested = requestedUserId == SessionState.DEFAULT_LEGACY_USER_ID || requestedUserId == "legacy_user_v0"

            if (sessionState != null) {
                when (sessionState) {
                    is SessionState.Initializing -> {
                        return Result.Failure(
                            IllegalStateException("Cannot export user data while session is initializing")
                        )
                    }
                    is SessionState.ReconciliationFailed -> {
                        return Result.Failure(
                            IllegalStateException("Cannot export user data while session reconciliation is in failed state: ${sessionState.error.message}")
                        )
                    }
                    is SessionState.Authenticated -> {
                        if (isLegacyRequested) {
                            return Result.Failure(
                                IllegalArgumentException("Cannot export legacy user data when active session is authenticated as ${sessionState.userId}")
                            )
                        }
                        if (!requestedUserId.isNullOrBlank() && sessionState.userId != requestedUserId) {
                            return Result.Failure(
                                IllegalArgumentException("Cannot export data for user $requestedUserId: active session belongs to ${sessionState.userId}")
                            )
                        }
                    }
                    is SessionState.OfflineFallback -> {
                        if (!requestedUserId.isNullOrBlank() && !isLegacyRequested) {
                            return Result.Failure(
                                IllegalArgumentException("Cannot export authenticated user $requestedUserId: active session is unauthenticated offline fallback")
                            )
                        }
                    }
                }
            }

            val currentGeneration = userSessionProvider?.getSessionGeneration() ?: 0L
            val activeUserId = userSessionProvider?.getActiveUserId() ?: requestedUserId ?: SessionState.DEFAULT_LEGACY_USER_ID

            // Step 2: Check admission fence for erasure
            if (userSessionProvider?.isUserErased(activeUserId, currentGeneration) == true) {
                return Result.Failure(
                    IllegalStateException("Cannot export data for erased user $activeUserId at generation $currentGeneration")
                )
            }

            // Step 3: Execute complete export lifecycle under continuous session read barrier
            val result = if (userSessionProvider != null) {
                userSessionProvider.withUserSession(expectedGeneration = currentGeneration) { lockedUserId ->
                    if (userSessionProvider.isUserErased(lockedUserId, currentGeneration)) {
                        return@withUserSession Result.Failure(
                            IllegalStateException("Cannot export data for erased user $lockedUserId at generation $currentGeneration")
                        )
                    }

                    // 3a. Capture Room database snapshot (transaction finishes here)
                    val snapshot = when (val snapshotResult = dataExportRepository.captureUserSnapshot(lockedUserId)) {
                        is Result.Failure -> return@withUserSession snapshotResult
                        is Result.Success -> snapshotResult.data
                    }

                    // 3b. Verify admission fence before executing file writing block
                    if (userSessionProvider.isUserErased(lockedUserId, currentGeneration)) {
                        return@withUserSession Result.Failure(
                            IllegalStateException("Cannot export data for erased user $lockedUserId at generation $currentGeneration")
                        )
                    }

                    // 3c. Execute block (streaming serialization, promotion, retention) while barrier is held
                    val blockResult = block(lockedUserId, snapshot)

                    // 3d. Post-block admission validation
                    if (userSessionProvider.isUserErased(lockedUserId, currentGeneration)) {
                        return@withUserSession Result.Failure(
                            IllegalStateException("User $lockedUserId was erased during export execution")
                        )
                    }

                    blockResult
                }
            } else {
                when (val snapshotResult = dataExportRepository.captureUserSnapshot(activeUserId)) {
                    is Result.Failure -> snapshotResult
                    is Result.Success -> block(activeUserId, snapshotResult.data)
                }
            }

            return result
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return Result.Failure(e)
        } finally {
            isExporting.set(false)
        }
    }
}
