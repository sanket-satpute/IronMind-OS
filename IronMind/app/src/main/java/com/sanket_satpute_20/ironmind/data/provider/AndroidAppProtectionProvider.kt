package com.sanket_satpute_20.ironmind.data.provider

import android.content.Context
import android.provider.Settings
import android.text.TextUtils
import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.provider.AppProtectionProvider
import com.sanket_satpute_20.ironmind.service.IronMindProtectionService

class AndroidAppProtectionProvider(
    private val context: Context,
    private val enforcementStore: ProtectionEnforcementStore = ProtectionEnforcementStore(context)
) : AppProtectionProvider {

    override fun hasRequiredPermissions(): Boolean {
        return isAccessibilityServiceEnabled(context, IronMindProtectionService::class.java)
    }

    override suspend fun applyProtection(targetPackages: List<String>): Result<Unit, Exception> {
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

        // Persist enforcement state for service recovery across process termination
        enforcementStore.saveEnforcementState(
            userId = "active_session",
            isActive = true,
            packages = sanitized.toSet()
        )

        IronMindProtectionService.setProtectedPackages(sanitized)
        IronMindProtectionService.setProtectionActive(true)
        return Result.Success(Unit)
    }

    override suspend fun removeProtection(): Result<Unit, Exception> {
        enforcementStore.clearEnforcementState()
        IronMindProtectionService.setProtectionActive(false)
        IronMindProtectionService.setProtectedPackages(emptyList())
        return Result.Success(Unit)
    }

    override fun isProtectionActive(): Boolean {
        // Safe Epistemic Rule: If the user revoked accessibility permissions in settings,
        // enforcement cannot be active regardless of stale memory flags.
        if (!hasRequiredPermissions()) return false

        return IronMindProtectionService.isProtectionActive() ||
                (enforcementStore.getEnforcementState()?.isActive == true)
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
