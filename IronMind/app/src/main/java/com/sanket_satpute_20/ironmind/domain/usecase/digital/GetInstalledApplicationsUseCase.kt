package com.sanket_satpute_20.ironmind.domain.usecase.digital

import com.sanket_satpute_20.ironmind.domain.provider.DiscoveredApp
import com.sanket_satpute_20.ironmind.domain.provider.InstalledAppDiscoveryProvider
import java.util.Locale

class GetInstalledApplicationsUseCase(
    private val appDiscoveryProvider: InstalledAppDiscoveryProvider
) {
    operator fun invoke(searchQuery: String = ""): List<DiscoveredApp> {
        val allApps = appDiscoveryProvider.getLaunchableApps()
        val query = searchQuery.trim().lowercase(Locale.ROOT)
        if (query.isBlank()) return allApps

        return allApps.filter { app ->
            app.displayName.lowercase(Locale.ROOT).contains(query) ||
            app.packageName.lowercase(Locale.ROOT).contains(query)
        }
    }
}
