package com.sanket_satpute_20.ironmind.domain.provider

import com.sanket_satpute_20.ironmind.domain.common.Result

interface AppProtectionProvider {
    fun hasRequiredPermissions(): Boolean
    suspend fun applyProtection(targetPackages: List<String>): Result<Unit, Exception>
    suspend fun removeProtection(): Result<Unit, Exception>
    fun isProtectionActive(): Boolean
}
