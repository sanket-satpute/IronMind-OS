package com.sanket_satpute_20.ironmind.domain.provider

data class DiscoveredApp(
    val packageName: String,
    val displayName: String,
    val isSystemApp: Boolean = false
)

interface InstalledAppDiscoveryProvider {
    /**
     * Retrieves launchable user applications available to this application.
     * Guaranteed to exclude IronMind itself and be deterministically sorted.
     */
    fun getLaunchableApps(): List<DiscoveredApp>

    /**
     * Checks if a package is currently installed and reachable on the device.
     */
    fun isAppInstalled(packageName: String): Boolean

    /**
     * Resolves the current user-facing display label for a package, or null if unresolvable.
     */
    fun getAppDisplayName(packageName: String): String?
}
