package com.sanket_satpute_20.ironmind.domain.usecase.protection

import com.sanket_satpute_20.ironmind.data.provider.ProtectionEnforcementStore
import com.sanket_satpute_20.ironmind.domain.common.Clock
import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.ProtectionSessionStatus
import com.sanket_satpute_20.ironmind.domain.provider.AppProtectionProvider
import com.sanket_satpute_20.ironmind.domain.repository.ProtectionRepository
import com.sanket_satpute_20.ironmind.domain.session.UserSessionProvider
import com.sanket_satpute_20.ironmind.domain.usecase.digital.GetEligibleRestrictionsForEnforcementUseCase
import com.sanket_satpute_20.ironmind.service.IronMindProtectionService
import kotlinx.coroutines.CancellationException

sealed interface ProtectionRecoveryResult {
    data class Restored(
        val sessionId: String,
        val userId: String,
        val enforcedPackages: Set<String>
    ) : ProtectionRecoveryResult

    data class FailedClosed(
        val reason: FailClosedReason
    ) : ProtectionRecoveryResult

    object NoEnforcementPending : ProtectionRecoveryResult

    enum class FailClosedReason {
        MISSING_STORED_STATE,
        MISSING_SESSION_ID,
        SESSION_NOT_FOUND,
        SESSION_NOT_ACTIVE,
        SESSION_EXPIRED,
        SESSION_OVERRIDDEN,
        USER_MISMATCH,
        PERMISSION_REVOKED,
        NO_ELIGIBLE_PACKAGES,
        INTERNAL_ERROR
    }
}

/**
 * Authoritatively validates and recovers protection enforcement state across service
 * reconnections, process restarts, and app initialization.
 *
 * Epistemic & Safety Invariants:
 * 1. SharedPreferences can never independently authorize app blocking.
 * 2. Originating session must exist in [ProtectionRepository] (Room database).
 * 3. Session status must be genuinely [ProtectionSessionStatus.ACTIVE] with endedAt == null.
 * 4. Session must strictly match the authenticated user from [UserSessionProvider].
 * 5. Expired sessions (currentTimeMillis >= scheduledEndAt) are transitioned to EXPIRED in Room and fail closed.
 * 6. Overridden sessions (from Emergency Override) strictly fail closed.
 * 7. Eligible packages are recalculated from saved configurations rather than blindly trusting stale snapshots.
 * 8. On any validation failure or ambiguity, enforcement fails closed: clears store and service remains idle.
 */
class AuthoritativeProtectionSessionRecoveryUseCase(
    private val protectionRepository: ProtectionRepository,
    private val userSessionProvider: UserSessionProvider,
    private val clock: Clock,
    private val appProtectionProvider: AppProtectionProvider,
    private val enforcementStore: ProtectionEnforcementStore,
    private val getEligibleRestrictionsForEnforcementUseCase: GetEligibleRestrictionsForEnforcementUseCase? = null
) {
    suspend operator fun invoke(): ProtectionRecoveryResult {
        try {
            // 1. Read persisted enforcement state from store
            val storedState = enforcementStore.getEnforcementState()
            if (storedState == null) {
                failClosed()
                return ProtectionRecoveryResult.NoEnforcementPending
            }

            // 2. Validate originating session identity is present
            val sessionId = storedState.sessionId
            if (sessionId.isNullOrBlank()) {
                failClosed()
                return ProtectionRecoveryResult.FailedClosed(ProtectionRecoveryResult.FailClosedReason.MISSING_SESSION_ID)
            }

            // 3. Validate accessibility permission is still granted
            if (!appProtectionProvider.hasRequiredPermissions()) {
                failClosed()
                return ProtectionRecoveryResult.FailedClosed(ProtectionRecoveryResult.FailClosedReason.PERMISSION_REVOKED)
            }

            // 4. Validate current authenticated user
            val activeUserId = userSessionProvider.getActiveUserId()
            if (activeUserId.isBlank() || activeUserId != storedState.userId) {
                failClosed()
                return ProtectionRecoveryResult.FailedClosed(ProtectionRecoveryResult.FailClosedReason.USER_MISMATCH)
            }

            // 5. Query authoritative session record from Room repository
            val sessionResult = protectionRepository.getProtectionSession(sessionId)
            if (sessionResult !is Result.Success) {
                failClosed()
                return ProtectionRecoveryResult.FailedClosed(ProtectionRecoveryResult.FailClosedReason.SESSION_NOT_FOUND)
            }
            val session = sessionResult.data

            // 6. Validate session belongs to current user
            if (session.userId != activeUserId) {
                failClosed()
                return ProtectionRecoveryResult.FailedClosed(ProtectionRecoveryResult.FailClosedReason.USER_MISMATCH)
            }

            // 7. Validate session status is genuinely ACTIVE
            if (session.status != ProtectionSessionStatus.ACTIVE || session.endedAt != null) {
                val reason = when (session.status) {
                    ProtectionSessionStatus.OVERRIDDEN -> ProtectionRecoveryResult.FailClosedReason.SESSION_OVERRIDDEN
                    ProtectionSessionStatus.EXPIRED -> ProtectionRecoveryResult.FailClosedReason.SESSION_EXPIRED
                    else -> ProtectionRecoveryResult.FailClosedReason.SESSION_NOT_ACTIVE
                }
                failClosed()
                return ProtectionRecoveryResult.FailedClosed(reason)
            }

            // 8. Validate expiration by wall clock
            val now = clock.currentTimeMillis()
            if (session.scheduledEndAt != null && now >= session.scheduledEndAt) {
                // Authoritatively mark session as EXPIRED in Room
                val expiredSession = session.copy(
                    status = ProtectionSessionStatus.EXPIRED,
                    endedAt = session.scheduledEndAt,
                    updatedAt = now
                )
                protectionRepository.saveProtectionSession(expiredSession)
                failClosed()
                return ProtectionRecoveryResult.FailedClosed(ProtectionRecoveryResult.FailClosedReason.SESSION_EXPIRED)
            }

            // 9. Recalculate eligible packages from current saved configurations
            val storedPackages = storedState.protectedPackages
            val packagesToEnforce: Set<String> = if (getEligibleRestrictionsForEnforcementUseCase != null) {
                when (val eligibleResult = getEligibleRestrictionsForEnforcementUseCase.invoke(activeUserId)) {
                    is Result.Success -> {
                        val currentlyEligible = eligibleResult.data.map { it.packageName }.toSet()
                        // Intersect stored packages with currently enabled and installed restrictions
                        storedPackages.filter { currentlyEligible.contains(it) }.toSet()
                    }
                    is Result.Failure -> storedPackages
                }
            } else {
                storedPackages
            }

            if (packagesToEnforce.isEmpty()) {
                failClosed()
                return ProtectionRecoveryResult.FailedClosed(ProtectionRecoveryResult.FailClosedReason.NO_ELIGIBLE_PACKAGES)
            }

            // 10. Authorization confirmed! Restore enforcement
            IronMindProtectionService.setActiveSessionIdentity(session.id, activeUserId)
            IronMindProtectionService.setProtectedPackages(packagesToEnforce)
            IronMindProtectionService.setProtectionActive(true)
            enforcementStore.saveEnforcementState(
                sessionId = session.id,
                userId = activeUserId,
                isActive = true,
                packages = packagesToEnforce
            )

            return ProtectionRecoveryResult.Restored(
                sessionId = session.id,
                userId = activeUserId,
                enforcedPackages = packagesToEnforce
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            failClosed()
            return ProtectionRecoveryResult.FailedClosed(ProtectionRecoveryResult.FailClosedReason.INTERNAL_ERROR)
        }
    }

    private fun failClosed() {
        enforcementStore.clearEnforcementState()
        IronMindProtectionService.setProtectionActive(false)
        IronMindProtectionService.setProtectedPackages(emptySet())
        IronMindProtectionService.setActiveSessionIdentity(null, null)
    }
}
