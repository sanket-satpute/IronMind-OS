package com.sanket_satpute_20.ironmind.testutil.fake

import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.provider.AppProtectionProvider

class FakeAppProtectionProvider : AppProtectionProvider {
    var permissionsGranted = true
    var isActive = false
    val protectedPackages = mutableListOf<String>()
    var shouldFailRemoval = false

    override fun hasRequiredPermissions(): Boolean {
        return permissionsGranted
    }

    override suspend fun applyProtection(targetPackages: List<String>): Result<Unit, Exception> {
        return if (permissionsGranted) {
            isActive = true
            protectedPackages.clear()
            protectedPackages.addAll(targetPackages)
            Result.Success(Unit)
        } else {
            Result.Failure(Exception("Missing permissions"))
        }
    }

    override suspend fun removeProtection(): Result<Unit, Exception> {
        if (shouldFailRemoval) {
            return Result.Failure(Exception("Simulated removal failure"))
        }
        isActive = false
        protectedPackages.clear()
        return Result.Success(Unit)
    }

    override fun isProtectionActive(): Boolean {
        return isActive
    }
}
