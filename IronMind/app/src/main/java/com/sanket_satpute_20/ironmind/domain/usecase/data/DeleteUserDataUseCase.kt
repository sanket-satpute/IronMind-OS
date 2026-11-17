package com.sanket_satpute_20.ironmind.domain.usecase.data

import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.repository.AuthRepository
import com.sanket_satpute_20.ironmind.domain.repository.DataManagementRepository
import com.sanket_satpute_20.ironmind.domain.repository.UserDataExportCacheManager
import com.sanket_satpute_20.ironmind.domain.session.SessionState
import com.sanket_satpute_20.ironmind.domain.session.UserSessionProvider
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException

/**
 * Use case to execute permanent, atomic user data erasure.
 *
 * Epistemic & Privacy Guarantees (Phase 8.3.2 Stage 3A.2):
 * 1. Single-Flight Erasure: Rejects overlapping concurrent erasure requests.
 * 2. Identity Verification: Validates target user against active session state.
 * 3. Admission Fencing: Immediately marks the user as erasing to reject new read/write operations.
 * 4. Exclusive Barrier Coordination: Acquires exclusive write access and waits for all active readers
 *    (including in-flight data exports) to drain before beginning deletion.
 * 5. Complete Local Purge: Deletes all Room database tables AND all internal cache export files
 *    belonging to the erased user.
 * 6. Local vs External Data Boundary: Local erasure guarantees complete purging of all database rows
 *    and internal cache files (/data/user/0/<package>/cache/exports). Files previously shared, copied,
 *    or saved by the user outside the application's sandbox (e.g. Downloads folder, external storage,
 *    or cloud storage) reside in the user's external OS storage domain and are outside the application's
 *    sandboxed security boundary.
 * 7. Rollback & Failure Reporting: If database deletion or export file cleanup fails, marks the operation
 *    as failed and clears the in-progress erasure fence rather than falsely claiming complete erasure.
 */
class DeleteUserDataUseCase(
    private val dataManagementRepository: DataManagementRepository,
    private val userSessionProvider: UserSessionProvider? = null,
    private val authRepository: AuthRepository? = null,
    private val exportCacheManager: UserDataExportCacheManager? = null,
    private val drainTimeoutMs: Long = DEFAULT_DRAIN_TIMEOUT_MS
) {
    companion object {
        const val DEFAULT_DRAIN_TIMEOUT_MS: Long = 10_000L
    }

    private val isErasing = AtomicBoolean(false)

    suspend operator fun invoke(userId: String): Result<Unit, Exception> {
        if (userId.isBlank()) {
            return Result.Failure(IllegalArgumentException("Target userId cannot be blank"))
        }

        // Enforce single-flight erasure coordination
        if (!isErasing.compareAndSet(false, true)) {
            return Result.Failure(IllegalStateException("User data erasure is already in progress"))
        }

        try {
            // Step 1: Validate target user against active session identity
            val sessionState = userSessionProvider?.sessionState?.value
            val isLegacyUser = userId == SessionState.DEFAULT_LEGACY_USER_ID || userId == "legacy_user_v0"

            if (sessionState != null) {
                when (sessionState) {
                    is SessionState.Authenticated -> {
                        if (isLegacyUser) {
                            return Result.Failure(
                                IllegalArgumentException("Cannot erase default_legacy_user when active session is authenticated as ${sessionState.userId}")
                            )
                        }
                        if (sessionState.userId != userId) {
                            return Result.Failure(
                                IllegalArgumentException("Cannot erase data for user $userId: active session belongs to ${sessionState.userId}")
                            )
                        }
                    }
                    is SessionState.OfflineFallback -> {
                        if (!isLegacyUser) {
                            return Result.Failure(
                                IllegalArgumentException("Cannot erase authenticated user $userId: active session is unauthenticated offline fallback")
                            )
                        }
                    }
                    is SessionState.Initializing -> {
                        return Result.Failure(
                            IllegalStateException("Cannot erase user data while session is initializing")
                        )
                    }
                    is SessionState.ReconciliationFailed -> {
                        return Result.Failure(
                            IllegalStateException("Cannot erase user data while session reconciliation is in failed state: ${sessionState.error.message}")
                        )
                    }
                }
            }

            // Step 2: Establish admission fence to reject incoming operations for target user at current generation
            val targetGeneration = userSessionProvider?.getSessionGeneration() ?: 0L
            userSessionProvider?.markUserErasing(userId, targetGeneration)

            // Step 3: Pre-cancellation of all active/scheduled user background work
            dataManagementRepository.cancelAllUserWorkers()

            // Step 4: Acquire exclusive write barrier access with bounded reader drainage
            val deletionResult = try {
                if (userSessionProvider != null) {
                    userSessionProvider.withExclusiveAccess(drainTimeoutMs = drainTimeoutMs) {
                        val dbResult = dataManagementRepository.deleteAllUserData(userId)
                        if (dbResult is Result.Failure) return@withExclusiveAccess dbResult

                        if (exportCacheManager != null) {
                            val cacheResult = exportCacheManager.purgeUserExports(userId)
                            if (cacheResult is Result.Failure) {
                                return@withExclusiveAccess Result.Failure(
                                    IllegalStateException("Failed to purge user export files during data erasure: ${cacheResult.error.message}", cacheResult.error)
                                )
                            }
                        }
                        Result.Success(Unit)
                    }
                } else {
                    val dbResult = dataManagementRepository.deleteAllUserData(userId)
                    if (dbResult is Result.Failure) return dbResult

                    if (exportCacheManager != null) {
                        val cacheResult = exportCacheManager.purgeUserExports(userId)
                        if (cacheResult is Result.Failure) {
                            return Result.Failure(
                                IllegalStateException("Failed to purge user export files during data erasure: ${cacheResult.error.message}", cacheResult.error)
                            )
                        }
                    }
                    Result.Success(Unit)
                }
            } catch (e: TimeoutCancellationException) {
                userSessionProvider?.clearUserErasing(userId, targetGeneration)
                return Result.Failure(
                    IllegalStateException("Timed out waiting for in-flight operations to drain during data erasure", e)
                )
            } catch (e: CancellationException) {
                userSessionProvider?.clearUserErasing(userId, targetGeneration)
                throw e
            } catch (e: Exception) {
                userSessionProvider?.clearUserErasing(userId, targetGeneration)
                return Result.Failure(e)
            }

            if (deletionResult is Result.Failure) {
                userSessionProvider?.clearUserErasing(userId, targetGeneration)
                return deletionResult
            }

            // Step 5: Mark user session generation permanently erased in admission fence
            userSessionProvider?.markUserErased(userId, targetGeneration)

            // Step 6: Session invalidation OUTSIDE the held write barrier to prevent auth-collector deadlock
            if (!isLegacyUser && authRepository != null) {
                val signOutResult = authRepository.signOut()
                if (signOutResult is Result.Failure) {
                    println("IronMindLifecycle [Erasure] [SIGN_OUT_NON_FATAL] error=${signOutResult.error.message}")
                }
            }

            // Step 7: Final WorkManager cancellation sweep
            dataManagementRepository.cancelAllUserWorkers()

            // Step 8: Permit offline fallback if currently initializing
            userSessionProvider?.permitOfflineFallback()

            return Result.Success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return Result.Failure(e)
        } finally {
            isErasing.set(false)
        }
    }
}
