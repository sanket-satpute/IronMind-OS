package com.sanket_satpute_20.ironmind.data.provider

import android.content.Context
import android.provider.Settings
import android.text.TextUtils
import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.provider.AppProtectionProvider
import com.sanket_satpute_20.ironmind.service.IronMindProtectionService

class AndroidAppProtectionProvider(
    private val context: Context
) : AppProtectionProvider {

    override fun hasRequiredPermissions(): Boolean {
        return isAccessibilityServiceEnabled(context, IronMindProtectionService::class.java)
    }

    override suspend fun applyProtection(targetPackages: List<String>): Result<Unit, Exception> {
        if (!hasRequiredPermissions()) {
            return Result.Failure(Exception("Missing required protection permissions"))
        }
        IronMindProtectionService.setProtectedPackages(targetPackages)
        IronMindProtectionService.setProtectionActive(true)
        return Result.Success(Unit)
    }

    override suspend fun removeProtection(): Result<Unit, Exception> {
        IronMindProtectionService.setProtectionActive(false)
        IronMindProtectionService.setProtectedPackages(emptyList())
        return Result.Success(Unit)
    }

    override fun isProtectionActive(): Boolean {
        return IronMindProtectionService.isProtectionActive()
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
            // Error finding setting
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
