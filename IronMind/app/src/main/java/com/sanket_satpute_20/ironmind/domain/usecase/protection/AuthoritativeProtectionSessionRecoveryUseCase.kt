package com.sanket_satpute_20.ironmind.domain.usecase.protection

import com.sanket_satpute_20.ironmind.domain.common.Clock
import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.EntitySource
import com.sanket_satpute_20.ironmind.domain.model.Event
import com.sanket_satpute_20.ironmind.domain.model.EventType
import com.sanket_satpute_20.ironmind.domain.model.ProtectionSessionStatus
import com.sanket_satpute_20.ironmind.domain.model.protection.PersistentEnforcementState
import com.sanket_satpute_20.ironmind.domain.provider.AppProtectionProvider
import com.sanket_satpute_20.ironmind.domain.provider.ProtectionEnforcementStateStore
import com.sanket_satpute_20.ironmind.domain.repository.EventRepository
import com.sanket_satpute_20.ironmind.domain.repository.ProtectionRepository
import com.sanket_satpute_20.ironmind.domain.session.UserSessionProvider
import com.sanket_satpute_20.ironmind.domain.usecase.digital.GetEligibleRestrictionsForEnforcementUseCase
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
    private val enforcementStore: ProtectionEnforcementStateStore,
    private val getEligibleRestrictionsForEnforcementUseCase: GetEligibleRestrictionsForEnforcementUseCase? = null,
    private val eventRepository: EventRepository? = null
) {
    suspend operator fun invoke(): ProtectionRecoveryResult {
        try {
            // 1. Read persisted enforcement state from store
            val storedState = enforcementStore.getEnforcementState()
            val activeUserId = userSessionProvider.getActiveUserId()

            // 2. Identify originating session identity
            val sessionId = if (storedState != null) {
                if (storedState.sessionId.isNullOrBlank()) {
                    failClosed(clearStore = true)
                    return ProtectionRecoveryResult.FailedClosed(ProtectionRecoveryResult.FailClosedReason.MISSING_SESSION_ID)
                }
                storedState.sessionId
            } else {
                // Store has no pending enforcement record; check if Room has an active session for the authenticated user
                val activeSessionId = if (activeUserId.isNotBlank()) {
                    val activeSessionsResult = protectionRepository.getActiveProtectionSessionsForUser(activeUserId)
                    if (activeSessionsResult is Result.Success) {
                        activeSessionsResult.data.firstOrNull { it.status == ProtectionSessionStatus.ACTIVE && it.endedAt == null }?.id
                    } else null
                } else null

                if (activeSessionId == null) {
                    failClosed(clearStore = true)
                    return ProtectionRecoveryResult.NoEnforcementPending
                }
                activeSessionId
            }

            // 3. Validate accessibility permission is still granted
            if (!appProtectionProvider.hasRequiredPermissions()) {
                failClosed(
                    clearStore = false,
                    preservedSessionId = sessionId,
                    preservedUserId = storedState?.userId ?: activeUserId,
                    preservedScheduledEndAt = storedState?.scheduledEndAt
                )
                return ProtectionRecoveryResult.FailedClosed(ProtectionRecoveryResult.FailClosedReason.PERMISSION_REVOKED)
            }

            // 4. Validate current authenticated user
            if (activeUserId.isBlank() || (storedState?.userId != null && storedState.userId.isNotBlank() && activeUserId != storedState.userId)) {
                failClosed(clearStore = true)
                return ProtectionRecoveryResult.FailedClosed(ProtectionRecoveryResult.FailClosedReason.USER_MISMATCH)
            }

            // 5. Query authoritative session record from Room repository
            val sessionResult = protectionRepository.getProtectionSession(sessionId)
            if (sessionResult !is Result.Success) {
                failClosed(clearStore = true)
                return ProtectionRecoveryResult.FailedClosed(ProtectionRecoveryResult.FailClosedReason.SESSION_NOT_FOUND)
            }
            val session = sessionResult.data

            // 6. Validate session belongs to current user
            if (session.userId != activeUserId) {
                failClosed(clearStore = true)
                return ProtectionRecoveryResult.FailedClosed(ProtectionRecoveryResult.FailClosedReason.USER_MISMATCH)
            }

            // 7. Validate session status is genuinely ACTIVE
            if (session.status != ProtectionSessionStatus.ACTIVE || session.endedAt != null) {
                val reason = when (session.status) {
                    ProtectionSessionStatus.OVERRIDDEN -> ProtectionRecoveryResult.FailClosedReason.SESSION_OVERRIDDEN
                    ProtectionSessionStatus.EXPIRED -> ProtectionRecoveryResult.FailClosedReason.SESSION_EXPIRED
                    else -> ProtectionRecoveryResult.FailClosedReason.SESSION_NOT_ACTIVE
                }
                failClosed(clearStore = true)
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
                eventRepository?.saveEvent(
                    Event(
                        id = java.util.UUID.randomUUID().toString(),
                        userId = session.userId,
                        type = EventType.PROTECTION_ENDED,
                        entityType = "PROTECTION_SESSION",
                        entityId = session.id,
                        occurredAt = session.scheduledEndAt,
                        recordedAt = now,
                        source = EntitySource.SYSTEM
                    )
                )
                println("IronMindLifecycle [Protection] [SESSION_STOPPED] sessionId=${session.id} reason=EXPIRED")
                failClosed(clearStore = true)
                return ProtectionRecoveryResult.FailedClosed(ProtectionRecoveryResult.FailClosedReason.SESSION_EXPIRED)
            }

            // 9. Recalculate eligible packages from current saved configurations
            val storedPackages = storedState?.protectedPackages ?: emptySet()
            val packagesToEnforce: Set<String> = if (getEligibleRestrictionsForEnforcementUseCase != null) {
                when (val eligibleResult = getEligibleRestrictionsForEnforcementUseCase.invoke(activeUserId)) {
                    is Result.Success -> {
                        val currentlyEligible = eligibleResult.data.map { it.packageName }.toSet()
                        if (storedPackages.isNotEmpty()) {
                            storedPackages.filter { currentlyEligible.contains(it) }.toSet()
                        } else {
                            currentlyEligible
                        }
                    }
                    is Result.Failure -> storedPackages
                }
            } else {
                storedPackages
            }

            if (packagesToEnforce.isEmpty()) {
                failClosed(clearStore = true)
                return ProtectionRecoveryResult.FailedClosed(ProtectionRecoveryResult.FailClosedReason.NO_ELIGIBLE_PACKAGES)
            }

            // 10. Authorization confirmed! Restore enforcement through provider abstraction
            val applyResult = appProtectionProvider.applyProtection(
                targetPackages = packagesToEnforce.toList(),
                sessionId = session.id,
                userId = activeUserId,
                scheduledEndAt = session.scheduledEndAt
            )
            return when (applyResult) {
                is Result.Success -> {
                    enforcementStore.saveEnforcementState(
                        sessionId = session.id,
                        userId = activeUserId,
                        isActive = true,
                        packages = packagesToEnforce,
                        scheduledEndAt = session.scheduledEndAt
                    )
                    ProtectionRecoveryResult.Restored(
                        sessionId = session.id,
                        userId = activeUserId,
                        enforcedPackages = packagesToEnforce
                    )
                }
                is Result.Failure -> {
                    val reason = if (!appProtectionProvider.hasRequiredPermissions()) {
                        ProtectionRecoveryResult.FailClosedReason.PERMISSION_REVOKED
                    } else if (packagesToEnforce.isEmpty()) {
                        ProtectionRecoveryResult.FailClosedReason.NO_ELIGIBLE_PACKAGES
                    } else {
                        ProtectionRecoveryResult.FailClosedReason.INTERNAL_ERROR
                    }
                    failClosed(
                        clearStore = (reason != ProtectionRecoveryResult.FailClosedReason.PERMISSION_REVOKED),
                        preservedSessionId = session.id,
                        preservedUserId = activeUserId,
                        preservedScheduledEndAt = session.scheduledEndAt
                    )
                    ProtectionRecoveryResult.FailedClosed(reason)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            failClosed(clearStore = true)
            return ProtectionRecoveryResult.FailedClosed(ProtectionRecoveryResult.FailClosedReason.INTERNAL_ERROR)
        }
    }

    private suspend fun failClosed(
        clearStore: Boolean = true,
        preservedSessionId: String? = null,
        preservedUserId: String? = null,
        preservedScheduledEndAt: Long? = null
    ) {
        appProtectionProvider.removeProtection()
        if (clearStore) {
            enforcementStore.clearEnforcementState()
        } else {
            enforcementStore.saveEnforcementState(
                sessionId = preservedSessionId,
                userId = preservedUserId ?: "",
                isActive = false,
                packages = emptySet(),
                scheduledEndAt = preservedScheduledEndAt
            )
        }
    }
}
