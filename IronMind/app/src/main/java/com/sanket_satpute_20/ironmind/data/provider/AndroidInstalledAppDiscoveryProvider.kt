package com.sanket_satpute_20.ironmind.data.provider

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import com.sanket_satpute_20.ironmind.domain.provider.DiscoveredApp
import com.sanket_satpute_20.ironmind.domain.provider.InstalledAppDiscoveryProvider
import java.util.Locale

class AndroidInstalledAppDiscoveryProvider(
    private val context: Context
) : InstalledAppDiscoveryProvider {

    override fun getLaunchableApps(): List<DiscoveredApp> {
        val pm = context.packageManager
        val ironMindPackage = context.packageName

        val launcherIntent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }

        val resolveInfos = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.queryIntentActivities(
                    launcherIntent,
                    PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong())
                )
            } else {
                @Suppress("DEPRECATION")
                pm.queryIntentActivities(launcherIntent, PackageManager.MATCH_DEFAULT_ONLY)
            }
        } catch (e: Exception) {
            emptyList()
        }

        return resolveInfos
            .mapNotNull { resolveInfo ->
                val activityInfo = resolveInfo.activityInfo ?: return@mapNotNull null
                val pkgName = activityInfo.packageName ?: return@mapNotNull null

                // Exclude IronMind itself
                if (pkgName == ironMindPackage) return@mapNotNull null

                val label = try {
                    resolveInfo.loadLabel(pm)?.toString()?.trim()
                } catch (e: Exception) {
                    null
                } ?: pkgName

                val isSystem = try {
                    val appInfo = activityInfo.applicationInfo
                    (appInfo.flags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) != 0
                } catch (e: Exception) {
                    false
                }

                DiscoveredApp(
                    packageName = pkgName,
                    displayName = label,
                    isSystemApp = isSystem
                )
            }
            .distinctBy { it.packageName }
            .sortedWith(
                compareBy<DiscoveredApp> { it.displayName.lowercase(Locale.ROOT) }
                    .thenBy { it.packageName }
            )
    }

    override fun isAppInstalled(packageName: String): Boolean {
        if (packageName.isBlank()) return false
        val pm = context.packageManager
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0L))
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(packageName, 0)
            }
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        } catch (e: Exception) {
            false
        }
    }

    override fun getAppDisplayName(packageName: String): String? {
        if (packageName.isBlank()) return null
        val pm = context.packageManager
        return try {
            val appInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getApplicationInfo(packageName, PackageManager.ApplicationInfoFlags.of(0L))
            } else {
                @Suppress("DEPRECATION")
                pm.getApplicationInfo(packageName, 0)
            }
            pm.getApplicationLabel(appInfo).toString()
        } catch (e: Exception) {
            null
        }
    }
}
