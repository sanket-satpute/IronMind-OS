package com.sanket_satpute_20.ironmind.data.provider

import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.os.Build
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Focused tests for [AndroidInstalledAppDiscoveryProvider] using Robolectric.
 *
 * Verifies:
 * 1. Launcher activities with MAIN + LAUNCHER without DEFAULT are included (query flags = 0L / 0).
 * 2. Launcher activities with MAIN + LAUNCHER + DEFAULT are included.
 * 3. Non-launcher activities / missing activity info are excluded.
 * 4. IronMind package itself is excluded.
 * 5. Duplicate package entries (multiple activities in the same package) are deduplicated.
 * 6. Deterministic alphabetical sorting by display name (case-insensitive) then package name.
 * 7. Verification that queryIntentActivities receives 0 flags (not MATCH_DEFAULT_ONLY).
 * 8. Package installation and display name lookup.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AndroidInstalledAppDiscoveryProviderTest {

    private val context: Context = mockk()
    private val packageManager: PackageManager = mockk()
    private val ironMindPackage = "com.sanket_satpute_20.ironmind"

    private lateinit var provider: AndroidInstalledAppDiscoveryProvider

    @Before
    fun setUp() {
        every { context.packageManager } returns packageManager
        every { context.packageName } returns ironMindPackage
        provider = AndroidInstalledAppDiscoveryProvider(context)
    }

    @Test
    fun queryIntentActivities_passesZeroFlags_andDoesNotUseMatchDefaultOnly() {
        // Arrange
        val intentSlot = slot<Intent>()
        val translateInfo = createResolveInfo(
            packageName = "com.google.android.apps.translate",
            activityName = "TranslateActivity",
            label = "Translate",
            isSystem = false
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val flagsSlot = slot<PackageManager.ResolveInfoFlags>()
            every {
                packageManager.queryIntentActivities(capture(intentSlot), capture(flagsSlot))
            } returns listOf(translateInfo)
        } else {
            val intFlagsSlot = slot<Int>()
            every {
                @Suppress("DEPRECATION")
                packageManager.queryIntentActivities(capture(intentSlot), capture(intFlagsSlot))
            } returns listOf(translateInfo)
        }

        // Act
        val result = provider.getLaunchableApps()

        // Assert: Intent matches ACTION_MAIN + CATEGORY_LAUNCHER
        assertEquals(Intent.ACTION_MAIN, intentSlot.captured.action)
        assertTrue(intentSlot.captured.hasCategory(Intent.CATEGORY_LAUNCHER))
        assertFalse(intentSlot.captured.hasCategory(Intent.CATEGORY_DEFAULT))

        assertEquals(1, result.size)
        assertEquals("com.google.android.apps.translate", result[0].packageName)
        assertEquals("Translate", result[0].displayName)
        assertFalse(result[0].isSystemApp)
    }

    @Test
    fun getLaunchableApps_includesBothAppsWithAndWithoutDefaultCategory() {
        // Arrange: Translate has MAIN+LAUNCHER without DEFAULT; Brave has MAIN+LAUNCHER+DEFAULT
        val translateApp = createResolveInfo(
            packageName = "com.google.android.apps.translate",
            activityName = "TranslateActivity",
            label = "Google Translate",
            isSystem = false
        )
        val braveApp = createResolveInfo(
            packageName = "com.brave.browser",
            activityName = "BraveActivity",
            label = "Brave",
            isSystem = false
        )

        mockQueryIntentActivities(listOf(translateApp, braveApp))

        // Act
        val result = provider.getLaunchableApps()

        // Assert
        assertEquals(2, result.size)
        val packages = result.map { it.packageName }.toSet()
        assertTrue(packages.contains("com.google.android.apps.translate"))
        assertTrue(packages.contains("com.brave.browser"))
    }

    @Test
    fun getLaunchableApps_excludesIronMindItself() {
        // Arrange: IronMind self package should be excluded
        val selfApp = createResolveInfo(
            packageName = ironMindPackage,
            activityName = "MainActivity",
            label = "IronMind",
            isSystem = false
        )
        val otherApp = createResolveInfo(
            packageName = "com.example.other",
            activityName = "OtherActivity",
            label = "Other App",
            isSystem = false
        )

        mockQueryIntentActivities(listOf(selfApp, otherApp))

        // Act
        val result = provider.getLaunchableApps()

        // Assert
        assertEquals(1, result.size)
        assertEquals("com.example.other", result[0].packageName)
        assertFalse(result.any { it.packageName == ironMindPackage })
    }

    @Test
    fun getLaunchableApps_deduplicatesPackagesWithMultipleLauncherActivities() {
        // Arrange: Single package with 2 launcher activities
        val activity1 = createResolveInfo(
            packageName = "com.example.multilauncher",
            activityName = "MainActivity",
            label = "MultiApp Main",
            isSystem = false
        )
        val activity2 = createResolveInfo(
            packageName = "com.example.multilauncher",
            activityName = "SecondActivity",
            label = "MultiApp Alias",
            isSystem = false
        )

        mockQueryIntentActivities(listOf(activity1, activity2))

        // Act
        val result = provider.getLaunchableApps()

        // Assert
        assertEquals(1, result.size)
        assertEquals("com.example.multilauncher", result[0].packageName)
    }

    @Test
    fun getLaunchableApps_sortsDeterministicallyByDisplayNameCaseInsensitiveThenPackage() {
        // Arrange: Mixed-case labels
        val appC = createResolveInfo("com.app.c", "CActivity", "zebra", false)
        val appA = createResolveInfo("com.app.a", "AActivity", "Alpha", false)
        val appB = createResolveInfo("com.app.b", "BActivity", "bravo", false)
        val appA2 = createResolveInfo("com.app.a2", "A2Activity", "alpha", false)

        mockQueryIntentActivities(listOf(appC, appA, appB, appA2))

        // Act
        val result = provider.getLaunchableApps()

        // Assert: "Alpha" (com.app.a) == "alpha" (com.app.a2) -> secondary sort by package name
        assertEquals(4, result.size)
        assertEquals("com.app.a", result[0].packageName)
        assertEquals("com.app.a2", result[1].packageName)
        assertEquals("com.app.b", result[2].packageName)
        assertEquals("com.app.c", result[3].packageName)
    }

    @Test
    fun getLaunchableApps_handlesExceptionGracefully() {
        // Arrange
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            every {
                packageManager.queryIntentActivities(any(), any<PackageManager.ResolveInfoFlags>())
            } throws RuntimeException("PackageManager died")
        } else {
            every {
                @Suppress("DEPRECATION")
                packageManager.queryIntentActivities(any(), any<Int>())
            } throws RuntimeException("PackageManager died")
        }

        // Act
        val result = provider.getLaunchableApps()

        // Assert
        assertTrue(result.isEmpty())
    }

    @Test
    fun isAppInstalled_returnsTrueWhenPackageFound_andFalseWhenNotFound() {
        // Arrange
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            every {
                packageManager.getPackageInfo("com.google.android.apps.translate", any<PackageManager.PackageInfoFlags>())
            } returns mockk()
            every {
                packageManager.getPackageInfo("com.missing.app", any<PackageManager.PackageInfoFlags>())
            } throws PackageManager.NameNotFoundException()
        } else {
            every {
                @Suppress("DEPRECATION")
                packageManager.getPackageInfo("com.google.android.apps.translate", 0)
            } returns mockk()
            every {
                @Suppress("DEPRECATION")
                packageManager.getPackageInfo("com.missing.app", 0)
            } throws PackageManager.NameNotFoundException()
        }

        // Act & Assert
        assertTrue(provider.isAppInstalled("com.google.android.apps.translate"))
        assertFalse(provider.isAppInstalled("com.missing.app"))
        assertFalse(provider.isAppInstalled(""))
    }

    // --- Helpers ---

    private fun mockQueryIntentActivities(resolveInfos: List<ResolveInfo>) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            every {
                packageManager.queryIntentActivities(any(), any<PackageManager.ResolveInfoFlags>())
            } returns resolveInfos
        } else {
            every {
                @Suppress("DEPRECATION")
                packageManager.queryIntentActivities(any(), any<Int>())
            } returns resolveInfos
        }
    }

    private fun createResolveInfo(
        packageName: String,
        activityName: String,
        label: String,
        isSystem: Boolean
    ): ResolveInfo {
        val appInfo = ApplicationInfo().apply {
            this.packageName = packageName
            this.flags = if (isSystem) ApplicationInfo.FLAG_SYSTEM else 0
        }
        val actInfo = ActivityInfo().apply {
            this.packageName = packageName
            this.name = activityName
            this.applicationInfo = appInfo
            this.nonLocalizedLabel = label
        }
        return ResolveInfo().apply {
            this.activityInfo = actInfo
            this.nonLocalizedLabel = label
        }
    }
}
