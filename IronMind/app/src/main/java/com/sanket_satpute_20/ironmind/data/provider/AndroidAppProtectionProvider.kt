package com.sanket_satpute_20.ironmind.data.provider

import android.content.Context
import android.provider.Settings
import android.text.TextUtils
import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.provider.AppProtectionProvider
import com.sanket_satpute_20.ironmind.domain.session.UserSessionProvider
import com.sanket_satpute_20.ironmind.service.IronMindProtectionService

class AndroidAppProtectionProvider(
    private val context: Context,
    private val enforcementStore: ProtectionEnforcementStore = ProtectionEnforcementStore(context),
    private val userSessionProvider: UserSessionProvider? = null
) : AppProtectionProvider {

    override fun hasRequiredPermissions(): Boolean {
        return isAccessibilityServiceEnabled(context, IronMindProtectionService::class.java)
    }

    override suspend fun applyProtection(targetPackages: List<String>): Result<Unit, Exception> {
        return applyProtection(targetPackages, sessionId = null, userId = null, scheduledEndAt = null)
    }

    override suspend fun applyProtection(
        targetPackages: List<String>,
        sessionId: String?,
        userId: String?
    ): Result<Unit, Exception> {
        return applyProtection(targetPackages, sessionId, userId, scheduledEndAt = null)
    }

    override suspend fun applyProtection(
        targetPackages: List<String>,
        sessionId: String?,
        userId: String?,
        scheduledEndAt: Long?
    ): Result<Unit, Exception> {
        if (!hasRequiredPermissions()) {
            return Result.Failure(Exception("Missing required protection permissions: Accessibility Service must be enabled in system settings"))
        }

        val myPackage = context.packageName
        val sanitized = targetPackages.filter {
            it.isNotBlank() && it != myPackage && it != ProtectionEnforcementStore.IRONMIND_PACKAGE
        }.distinct()

        if (sanitized.isEmpty()) {
            return Result.Failure(IllegalArgumentException("No valid target packages provided for protection"))
        }

        val effectiveUserId = userId ?: userSessionProvider?.getActiveUserId() ?: "active_session"

        // Persist enforcement state with authoritative session identity and scheduledEndAt for recovery
        enforcementStore.saveEnforcementState(
            sessionId = sessionId,
            userId = effectiveUserId,
            isActive = true,
            packages = sanitized.toSet(),
            scheduledEndAt = scheduledEndAt
        )

        IronMindProtectionService.setActiveSessionIdentity(sessionId, effectiveUserId, scheduledEndAt)
        IronMindProtectionService.setProtectedPackages(sanitized)
        IronMindProtectionService.setProtectionActive(true)
        return Result.Success(Unit)
    }

    override suspend fun removeProtection(): Result<Unit, Exception> {
        enforcementStore.clearEnforcementState()
        IronMindProtectionService.setProtectionActive(false)
        IronMindProtectionService.setProtectedPackages(emptyList())
        IronMindProtectionService.setActiveSessionIdentity(null, null, null)
        return Result.Success(Unit)
    }

    override fun isProtectionActive(): Boolean {
        // Safe Epistemic Rule: If the user revoked accessibility permissions in settings,
        // enforcement cannot be active regardless of stale memory or persistent flags.
        if (!hasRequiredPermissions()) return false

        val activeUid = userSessionProvider?.getActiveUserId()
        val serviceActive = IronMindProtectionService.isProtectionActive()
        val serviceUser = IronMindProtectionService.getActiveUserId()

        if (serviceActive) {
            if (activeUid != null && serviceUser != null && serviceUser != activeUid) {
                return false
            }
            return true
        }

        val storedState = enforcementStore.getEnforcementState()
        if (storedState?.isActive == true && !storedState.sessionId.isNullOrBlank()) {
            if (activeUid != null && storedState.userId != activeUid) {
                return false
            }
            if (storedState.scheduledEndAt != null && System.currentTimeMillis() >= storedState.scheduledEndAt) {
                return false
            }
            return true
        }

        return false
    }

    override fun getEnforcedPackages(): Set<String> {
        if (!isProtectionActive()) return emptySet()

        val inMemory = IronMindProtectionService.getProtectedPackages()
        if (inMemory.isNotEmpty()) return inMemory

        return enforcementStore.getEnforcementState()?.protectedPackages ?: emptySet()
    }

    private fun isAccessibilityServiceEnabled(context: Context, accessibilityService: Class<*>): Boolean {
        val expectedComponentName = context.packageName + "/" + accessibilityService.name
        var accessibilityEnabled = 0
        try {
            accessibilityEnabled = Settings.Secure.getInt(
                context.contentResolver,
                Settings.Secure.ACCESSIBILITY_ENABLED
            )
        } catch (e: Settings.SettingNotFoundException) {
            // Setting not found
        }
        val stringColonSplitter = TextUtils.SimpleStringSplitter(':')
        if (accessibilityEnabled == 1) {
            val settingValue = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            )
            if (settingValue != null) {
                stringColonSplitter.setString(settingValue)
                while (stringColonSplitter.hasNext()) {
                    val accessibilityServiceStr = stringColonSplitter.next()
                    if (accessibilityServiceStr.equals(expectedComponentName, ignoreCase = true)) {
                        return true
                    }
                }
            }
        }
        return false
    }
}