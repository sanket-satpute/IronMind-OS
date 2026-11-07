package com.sanket_satpute_20.ironmind.ui.screens.protection.restriction

import com.sanket_satpute_20.ironmind.domain.model.digital.AppRestrictionConfig
import com.sanket_satpute_20.ironmind.domain.provider.DiscoveredApp

data class AppRestrictionUiState(
    val isLoading: Boolean = false,
    val searchQuery: String = "",
    val installedApps: List<DiscoveredApp> = emptyList(),
    val configuredRestrictions: List<AppRestrictionConfig> = emptyList(),
    val selectedPackageNames: Set<String> = emptySet(),
    val activeEnforcedPackageNames: Set<String> = emptySet(),
    val uninstalledConfiguredPackages: Set<String> = emptySet(),
    val isAccessibilityPermissionGranted: Boolean = false,
    val errorMessage: String? = null
) {
    val configuredPackages: Set<String>
        get() = configuredRestrictions.map { it.packageName }.toSet()

    val enabledConfiguredPackages: Set<String>
        get() = configuredRestrictions.filter { it.isEnabled }.map { it.packageName }.toSet()
}
