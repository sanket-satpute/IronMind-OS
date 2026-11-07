package com.sanket_satpute_20.ironmind.ui.screens.protection.restriction

import com.sanket_satpute_20.ironmind.domain.common.Clock
import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.ProtectionRule
import com.sanket_satpute_20.ironmind.domain.model.ProtectionSession
import com.sanket_satpute_20.ironmind.domain.model.ProtectionSessionStatus
import com.sanket_satpute_20.ironmind.domain.model.digital.AppRestrictionConfig
import com.sanket_satpute_20.ironmind.domain.model.digital.AppRestrictionConfigCodec
import com.sanket_satpute_20.ironmind.domain.provider.AppProtectionProvider
import com.sanket_satpute_20.ironmind.domain.provider.DiscoveredApp
import com.sanket_satpute_20.ironmind.domain.provider.InstalledAppDiscoveryProvider
import com.sanket_satpute_20.ironmind.domain.repository.ProtectionRepository
import com.sanket_satpute_20.ironmind.domain.session.SessionState
import com.sanket_satpute_20.ironmind.domain.usecase.digital.GetAppRestrictionConfigsUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.digital.GetInstalledApplicationsUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.digital.SaveAppRestrictionConfigUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.digital.ToggleAppRestrictionConfigUseCase
import com.sanket_satpute_20.ironmind.testutil.FakeUserSessionProvider
import com.sanket_satpute_20.ironmind.testutil.TestDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.Locale

@OptIn(ExperimentalCoroutinesApi::class)
class AppRestrictionFoundationTest {

    @get:Rule
    val dispatcherRule = TestDispatcherRule()

    private val testClock = FakeTestClock(1700000000000L)
    private lateinit var fakeDiscoveryProvider: FakeInstalledAppDiscoveryProvider
    private lateinit var fakeProtectionProvider: FakeAppProtectionProvider
    private lateinit var fakeProtectionRepository: FakeProtectionRepository
    private lateinit var userSessionProvider: FakeUserSessionProvider

    private lateinit var getInstalledAppsUseCase: GetInstalledApplicationsUseCase
    private lateinit var getConfigsUseCase: GetAppRestrictionConfigsUseCase
    private lateinit var saveConfigUseCase: SaveAppRestrictionConfigUseCase
    private lateinit var toggleConfigUseCase: ToggleAppRestrictionConfigUseCase

    @Before
    fun setUp() {
        fakeDiscoveryProvider = FakeInstalledAppDiscoveryProvider()
        fakeProtectionProvider = FakeAppProtectionProvider(hasPermissions = true)
        fakeProtectionRepository = FakeProtectionRepository()
        userSessionProvider = FakeUserSessionProvider(SessionState.Authenticated("user_1", isAnonymous = false))

        getInstalledAppsUseCase = GetInstalledApplicationsUseCase(fakeDiscoveryProvider)
        getConfigsUseCase = GetAppRestrictionConfigsUseCase(fakeProtectionRepository)
        saveConfigUseCase = SaveAppRestrictionConfigUseCase(fakeProtectionRepository, testClock)
        toggleConfigUseCase = ToggleAppRestrictionConfigUseCase(fakeProtectionRepository, testClock)
    }

    @Test
    fun `installed app filtering excludes IronMind itself and rejects blank package`() = runTest {
        // Attempt to restrict IronMind itself
        val ironMindResult = saveConfigUseCase(
            userId = "user_1",
            packageName = "com.sanket_satpute_20.ironmind",
            appDisplayName = "IronMind"
        )
        assertTrue(ironMindResult is Result.Failure)
        assertTrue(ironMindResult.errorOrNull() is IllegalArgumentException)

        // Attempt blank package
        val blankResult = saveConfigUseCase(
            userId = "user_1",
            packageName = "   ",
            appDisplayName = "Blank"
        )
        assertTrue(blankResult is Result.Failure)
    }

    @Test
    fun `deterministic sorting orders launchable applications by display name then package`() {
        fakeDiscoveryProvider.apps = listOf(
            DiscoveredApp("com.zeta", "Zeta App"),
            DiscoveredApp("com.alpha.b", "Alpha App"),
            DiscoveredApp("com.alpha.a", "Alpha App"),
            DiscoveredApp("com.beta", "beta app")
        )

        val results = getInstalledAppsUseCase()
        assertEquals(4, results.size)
        // Alpha App (com.alpha.a before com.alpha.b) -> beta app -> Zeta App
        assertEquals("com.alpha.a", results[0].packageName)
        assertEquals("com.alpha.b", results[1].packageName)
        assertEquals("com.beta", results[2].packageName)
        assertEquals("com.zeta", results[3].packageName)
    }

    @Test
    fun `search filters installed apps by display name or package name case-insensitively`() {
        fakeDiscoveryProvider.apps = listOf(
            DiscoveredApp("com.google.android.youtube", "YouTube"),
            DiscoveredApp("com.twitter.android", "X"),
            DiscoveredApp("com.instagram.android", "Instagram")
        )

        val matchByName = getInstalledAppsUseCase("you")
        assertEquals(1, matchByName.size)
        assertEquals("com.google.android.youtube", matchByName[0].packageName)

        val matchByPackage = getInstalledAppsUseCase("twitter")
        assertEquals(1, matchByPackage.size)
        assertEquals("com.twitter.android", matchByPackage[0].packageName)

        val noMatch = getInstalledAppsUseCase("nonexistent")
        assertTrue(noMatch.isEmpty())
    }

    @Test
    fun `selection and deselection allows batch saving of configurations`() = runTest {
        fakeDiscoveryProvider.apps = listOf(
            DiscoveredApp("com.youtube", "YouTube"),
            DiscoveredApp("com.twitter", "Twitter")
        )

        val viewModel = createViewModel()
        advanceUntilIdle()

        // Select YouTube
        viewModel.toggleAppSelection("com.youtube")
        assertTrue(viewModel.uiState.value.selectedPackageNames.contains("com.youtube"))

        // Select Twitter
        viewModel.toggleAppSelection("com.twitter")
        assertEquals(2, viewModel.uiState.value.selectedPackageNames.size)

        // Deselect YouTube
        viewModel.toggleAppSelection("com.youtube")
        assertFalse(viewModel.uiState.value.selectedPackageNames.contains("com.youtube"))
        assertTrue(viewModel.uiState.value.selectedPackageNames.contains("com.twitter"))

        // Save selected restrictions
        viewModel.saveSelectedRestrictions()
        advanceUntilIdle()

        // Selection should be cleared, configured restrictions should contain Twitter
        assertTrue(viewModel.uiState.value.selectedPackageNames.isEmpty())
        val configs = viewModel.uiState.value.configuredRestrictions
        assertEquals(1, configs.size)
        assertEquals("com.twitter", configs[0].packageName)
        assertEquals("Twitter", configs[0].appDisplayName)
        assertTrue(configs[0].isEnabled)
    }

    @Test
    fun `configuration persistence codec preserves all fields and round-trips correctly`() = runTest {
        val original = AppRestrictionConfig(
            id = "restr_user1_pkg",
            userId = "user_1",
            packageName = "com.sample.app",
            appDisplayName = "Sample App",
            isEnabled = true,
            createdAt = 1000L,
            updatedAt = 2000L,
            scheduleReferenceId = "sched_123"
        )

        val rule = AppRestrictionConfigCodec.toProtectionRule(original)
        assertEquals("restr_user1_pkg", rule.id)
        assertEquals("com.sample.app", rule.targetPackage)
        assertTrue(rule.enabled)

        val roundTripped = AppRestrictionConfigCodec.fromProtectionRule(rule)
        assertNotNull(roundTripped)
        assertEquals(original.id, roundTripped!!.id)
        assertEquals(original.userId, roundTripped.userId)
        assertEquals(original.packageName, roundTripped.packageName)
        assertEquals(original.appDisplayName, roundTripped.appDisplayName)
        assertEquals(original.isEnabled, roundTripped.isEnabled)
        assertEquals(original.scheduleReferenceId, roundTripped.scheduleReferenceId)
    }

    @Test
    fun `user isolation prevents cross-user visibility and modification`() = runTest {
        saveConfigUseCase("user_1", "com.user1.app", "User 1 App")
        saveConfigUseCase("user_2", "com.user2.app", "User 2 App")

        val user1Configs = getConfigsUseCase("user_1")
        assertTrue(user1Configs is Result.Success)
        val u1List = (user1Configs as Result.Success).data
        assertEquals(1, u1List.size)
        assertEquals("com.user1.app", u1List[0].packageName)

        val user2Configs = getConfigsUseCase("user_2")
        assertTrue(user2Configs is Result.Success)
        val u2List = (user2Configs as Result.Success).data
        assertEquals(1, u2List.size)
        assertEquals("com.user2.app", u2List[0].packageName)

        // User 2 cannot toggle user 1's rule
        val u1RuleId = u1List[0].id
        val unauthorizedToggle = toggleConfigUseCase("user_2", u1RuleId, false)
        assertTrue(unauthorizedToggle is Result.Failure)
        assertTrue(unauthorizedToggle.errorOrNull() is SecurityException)

        // ViewModel session transition test
        val viewModel = createViewModel()
        advanceUntilIdle()
        assertEquals(1, viewModel.uiState.value.configuredRestrictions.size)

        // Switch session to user_2
        userSessionProvider.setState(SessionState.Authenticated("user_2", isAnonymous = false))
        advanceUntilIdle()

        val u2State = viewModel.uiState.value
        assertEquals(1, u2State.configuredRestrictions.size)
        assertEquals("com.user2.app", u2State.configuredRestrictions[0].packageName)
    }

    @Test
    fun `disabled configurations toggle isEnabled flag and update timestamps`() = runTest {
        val saveRes = saveConfigUseCase("user_1", "com.toggle.app", "Toggle App", isEnabled = true)
        val config = (saveRes as Result.Success).data

        // Advance time and disable
        testClock.time = 1700000050000L
        val toggleRes = toggleConfigUseCase("user_1", config.id, isEnabled = false)
        assertTrue(toggleRes is Result.Success)

        val updatedConfigs = (getConfigsUseCase("user_1") as Result.Success).data
        assertEquals(1, updatedConfigs.size)
        assertFalse(updatedConfigs[0].isEnabled)
        assertEquals(1700000050000L, updatedConfigs[0].updatedAt)
    }

    @Test
    fun `missing or uninstalled packages are detected and surfaced in UI state`() = runTest {
        // App is configured
        saveConfigUseCase("user_1", "com.uninstalled.app", "Ghost App")

        // Discovery provider says it's NOT installed
        fakeDiscoveryProvider.installedPackages.remove("com.uninstalled.app")

        val viewModel = createViewModel()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(1, state.configuredRestrictions.size)
        assertTrue(state.uninstalledConfiguredPackages.contains("com.uninstalled.app"))
    }

    @Test
    fun `accessibility permission unavailable reports status accurately without blocking configuration`() = runTest {
        fakeProtectionProvider.hasPermissions = false

        val viewModel = createViewModel()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isAccessibilityPermissionGranted)

        // User can still select and configure restrictions safely
        viewModel.toggleAppSelection("com.offline.app")
        viewModel.saveSelectedRestrictions()
        advanceUntilIdle()

        assertEquals(1, viewModel.uiState.value.configuredRestrictions.size)
        assertEquals("com.offline.app", viewModel.uiState.value.configuredRestrictions[0].packageName)
    }

    private fun createViewModel(): AppRestrictionViewModel {
        return AppRestrictionViewModel(
            getInstalledApplicationsUseCase = getInstalledAppsUseCase,
            getAppRestrictionConfigsUseCase = getConfigsUseCase,
            saveAppRestrictionConfigUseCase = saveConfigUseCase,
            toggleAppRestrictionConfigUseCase = toggleConfigUseCase,
            installedAppDiscoveryProvider = fakeDiscoveryProvider,
            appProtectionProvider = fakeProtectionProvider,
            protectionRepository = fakeProtectionRepository,
            userSessionProvider = userSessionProvider
        )
    }

    // --- Test Doubles ---

    private class FakeTestClock(var time: Long) : Clock {
        override fun currentTimeMillis(): Long = time
    }

    private class FakeInstalledAppDiscoveryProvider : InstalledAppDiscoveryProvider {
        var apps = listOf(
            DiscoveredApp("com.youtube", "YouTube"),
            DiscoveredApp("com.twitter", "Twitter"),
            DiscoveredApp("com.uninstalled.app", "Ghost App"),
            DiscoveredApp("com.offline.app", "Offline App")
        )
        val installedPackages = mutableSetOf("com.youtube", "com.twitter", "com.offline.app")

        override fun getLaunchableApps(): List<DiscoveredApp> {
            return apps.sortedWith(
                compareBy<DiscoveredApp> { it.displayName.lowercase(Locale.ROOT) }
                    .thenBy { it.packageName }
            )
        }

        override fun isAppInstalled(packageName: String): Boolean {
            return installedPackages.contains(packageName)
        }

        override fun getAppDisplayName(packageName: String): String? {
            return apps.find { it.packageName == packageName }?.displayName
        }
    }

    private class FakeAppProtectionProvider(var hasPermissions: Boolean = true) : AppProtectionProvider {
        private var isActive: Boolean = false

        override fun hasRequiredPermissions(): Boolean = hasPermissions

        override suspend fun applyProtection(targetPackages: List<String>): Result<Unit, Exception> {
            isActive = true
            return Result.Success(Unit)
        }

        override suspend fun removeProtection(): Result<Unit, Exception> {
            isActive = false
            return Result.Success(Unit)
        }

        override fun isProtectionActive(): Boolean = isActive
    }

    private class FakeProtectionRepository : ProtectionRepository {
        private val rules = mutableListOf<ProtectionRule>()
        private val sessions = mutableListOf<ProtectionSession>()

        override suspend fun saveProtectionRule(rule: ProtectionRule): Result<Unit, Exception> {
            rules.removeIf { it.id == rule.id }
            rules.add(rule)
            return Result.Success(Unit)
        }

        override suspend fun getProtectionRule(id: String): Result<ProtectionRule, Exception> {
            val r = rules.find { it.id == id }
            return if (r != null) Result.Success(r) else Result.Failure(NoSuchElementException("Rule $id not found"))
        }

        override suspend fun getProtectionRulesForUser(userId: String): Result<List<ProtectionRule>, Exception> {
            return Result.Success(rules.filter { it.userId == userId })
        }

        override suspend fun saveProtectionSession(session: ProtectionSession): Result<Unit, Exception> {
            sessions.removeIf { it.id == session.id }
            sessions.add(session)
            return Result.Success(Unit)
        }

        override suspend fun getProtectionSession(id: String): Result<ProtectionSession, Exception> {
            val s = sessions.find { it.id == id }
            return if (s != null) Result.Success(s) else Result.Failure(NoSuchElementException("Session $id not found"))
        }

        override suspend fun getActiveProtectionSessionsForUser(userId: String): Result<List<ProtectionSession>, Exception> {
            return Result.Success(sessions.filter { it.userId == userId && it.status == ProtectionSessionStatus.ACTIVE })
        }
    }
}
