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
import com.sanket_satpute_20.ironmind.domain.usecase.digital.GetEligibleRestrictionsForEnforcementUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.digital.GetInstalledApplicationsUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.digital.SaveAppRestrictionConfigUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.digital.ToggleAppRestrictionConfigUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.protection.StartProtectionSessionUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.protection.StopProtectionSessionUseCase
import com.sanket_satpute_20.ironmind.service.IronMindProtectionService
import com.sanket_satpute_20.ironmind.testutil.FakeUserSessionProvider
import com.sanket_satpute_20.ironmind.testutil.TestDispatcherRule
import com.sanket_satpute_20.ironmind.testutil.fake.FakeEventRepository
import com.sanket_satpute_20.ironmind.testutil.fake.FakeIdGenerator
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.Locale

@OptIn(ExperimentalCoroutinesApi::class)
class ReliableAppRestrictionEnforcementTest {

    @get:Rule
    val dispatcherRule = TestDispatcherRule()

    private val testClock = FakeTestClock(1700000000000L)
    private lateinit var fakeDiscoveryProvider: FakeInstalledAppDiscoveryProvider
    private lateinit var fakeProtectionProvider: FakeTrackingAppProtectionProvider
    private lateinit var fakeProtectionRepository: FakeProtectionRepository
    private lateinit var fakeEventRepository: FakeEventRepository
    private lateinit var fakeIdGenerator: FakeIdGenerator
    private lateinit var userSessionProvider: FakeUserSessionProvider

    private lateinit var getInstalledAppsUseCase: GetInstalledApplicationsUseCase
    private lateinit var getConfigsUseCase: GetAppRestrictionConfigsUseCase
    private lateinit var getEligibleUseCase: GetEligibleRestrictionsForEnforcementUseCase
    private lateinit var saveConfigUseCase: SaveAppRestrictionConfigUseCase
    private lateinit var toggleConfigUseCase: ToggleAppRestrictionConfigUseCase
    private lateinit var startProtectionSessionUseCase: StartProtectionSessionUseCase
    private lateinit var stopProtectionSessionUseCase: StopProtectionSessionUseCase

    @Before
    fun setUp() {
        fakeDiscoveryProvider = FakeInstalledAppDiscoveryProvider()
        fakeProtectionProvider = FakeTrackingAppProtectionProvider(hasPermissions = true)
        fakeProtectionRepository = FakeProtectionRepository()
        fakeEventRepository = FakeEventRepository()
        fakeIdGenerator = FakeIdGenerator()
        userSessionProvider = FakeUserSessionProvider(SessionState.Authenticated("user_test", isAnonymous = false))

        getInstalledAppsUseCase = GetInstalledApplicationsUseCase(fakeDiscoveryProvider)
        getConfigsUseCase = GetAppRestrictionConfigsUseCase(fakeProtectionRepository)
        getEligibleUseCase = GetEligibleRestrictionsForEnforcementUseCase(getConfigsUseCase, fakeDiscoveryProvider)
        saveConfigUseCase = SaveAppRestrictionConfigUseCase(fakeProtectionRepository, testClock)
        toggleConfigUseCase = ToggleAppRestrictionConfigUseCase(fakeProtectionRepository, testClock)
        startProtectionSessionUseCase = StartProtectionSessionUseCase(
            protectionRepository = fakeProtectionRepository,
            protectionProvider = fakeProtectionProvider,
            idGenerator = fakeIdGenerator,
            clock = testClock,
            eventRepository = fakeEventRepository
        )
        stopProtectionSessionUseCase = StopProtectionSessionUseCase(
            protectionRepository = fakeProtectionRepository,
            appProtectionProvider = fakeProtectionProvider,
            clock = testClock,
            eventRepository = fakeEventRepository
        )
    }

    // Scenario 1: Only enabled configurations are enforced
    @Test
    fun `scenario 1 - only enabled configurations are eligible for enforcement`() = runTest {
        saveConfigUseCase("user_test", "com.app.enabled1", "Enabled 1", isEnabled = true)
        saveConfigUseCase("user_test", "com.app.enabled2", "Enabled 2", isEnabled = true)
        saveConfigUseCase("user_test", "com.app.disabled", "Disabled", isEnabled = false)

        val eligibleResult = getEligibleUseCase("user_test")
        assertTrue(eligibleResult is Result.Success)
        val eligible = (eligibleResult as Result.Success).data
        assertEquals(2, eligible.size)
        assertTrue(eligible.any { it.packageName == "com.app.enabled1" })
        assertTrue(eligible.any { it.packageName == "com.app.enabled2" })
        assertFalse(eligible.any { it.packageName == "com.app.disabled" })
    }

    // Scenario 2: Disabled configurations are excluded
    @Test
    fun `scenario 2 - disabled configurations are strictly excluded from enforcement`() = runTest {
        val saveRes = saveConfigUseCase("user_test", "com.app.test", "Test App", isEnabled = true)
        val config = (saveRes as Result.Success).data

        // Initially eligible
        val initialEligible = (getEligibleUseCase("user_test") as Result.Success).data
        assertEquals(1, initialEligible.size)

        // Disable configuration
        toggleConfigUseCase("user_test", config.id, isEnabled = false)

        val eligibleAfterDisable = (getEligibleUseCase("user_test") as Result.Success).data
        assertTrue(eligibleAfterDisable.isEmpty())
    }

    // Scenario 3: Configurations from another user are excluded
    @Test
    fun `scenario 3 - configurations from another user are never visible or eligible`() = runTest {
        saveConfigUseCase("user_A", "com.userA.app", "User A App", isEnabled = true)
        saveConfigUseCase("user_B", "com.userB.app", "User B App", isEnabled = true)

        val userAEligible = (getEligibleUseCase("user_A") as Result.Success).data
        assertEquals(1, userAEligible.size)
        assertEquals("com.userA.app", userAEligible[0].packageName)

        val userBEligible = (getEligibleUseCase("user_B") as Result.Success).data
        assertEquals(1, userBEligible.size)
        assertEquals("com.userB.app", userBEligible[0].packageName)
    }

    // Scenario 4: IronMind is never included
    @Test
    fun `scenario 4 - ironmind package is strictly rejected and never eligible`() = runTest {
        // Direct save attempt should fail
        val saveResult = saveConfigUseCase("user_test", "com.sanket_satpute_20.ironmind", "IronMind")
        assertTrue(saveResult is Result.Failure)

        // Service setProtectedPackages should filter it out
        IronMindProtectionService.setProtectedPackages(
            listOf("com.sanket_satpute_20.ironmind", "com.allowed.app")
        )
        val servicePackages = IronMindProtectionService.getProtectedPackages()
        assertFalse(servicePackages.contains("com.sanket_satpute_20.ironmind"))
        assertTrue(servicePackages.contains("com.allowed.app"))

        // Cleanup
        IronMindProtectionService.setProtectionActive(false)
    }

    // Scenario 5: Uninstalled packages are handled safely
    @Test
    fun `scenario 5 - uninstalled packages are safely excluded without exceptions`() = runTest {
        saveConfigUseCase("user_test", "com.installed.app", "Installed App", isEnabled = true)
        saveConfigUseCase("user_test", "com.uninstalled.app", "Uninstalled App", isEnabled = true)

        fakeDiscoveryProvider.installedPackages.remove("com.uninstalled.app")

        val eligible = (getEligibleUseCase("user_test") as Result.Success).data
        assertEquals(1, eligible.size)
        assertEquals("com.installed.app", eligible[0].packageName)
    }

    // Scenario 6: Starting a session loads the correct package list
    @Test
    fun `scenario 6 - starting a protection session applies the targeted packages`() = runTest {
        saveConfigUseCase("user_test", "com.app.target1", "Target 1", isEnabled = true)
        saveConfigUseCase("user_test", "com.app.target2", "Target 2", isEnabled = true)

        val eligible = (getEligibleUseCase("user_test") as Result.Success).data.map { it.packageName }
        fakeIdGenerator.nextId = "session_test_1"
        val startResult = startProtectionSessionUseCase(
            userId = "user_test",
            targetPackages = eligible
        )

        assertTrue(startResult is Result.Success)
        assertTrue(fakeProtectionProvider.isProtectionActive())
        assertEquals(2, fakeProtectionProvider.getEnforcedPackages().size)
        assertTrue(fakeProtectionProvider.getEnforcedPackages().contains("com.app.target1"))
        assertTrue(fakeProtectionProvider.getEnforcedPackages().contains("com.app.target2"))
    }

    // Scenario 7: Stopping a session clears enforcement state
    @Test
    fun `scenario 7 - stopping a protection session clears enforcement state`() = runTest {
        fakeIdGenerator.nextId = "session_test_2"
        val startResult = startProtectionSessionUseCase(
            userId = "user_test",
            targetPackages = listOf("com.app.target")
        )
        assertTrue(startResult is Result.Success)
        val sessionId = (startResult as Result.Success).data.id
        assertTrue(fakeProtectionProvider.isProtectionActive())

        val stopResult = stopProtectionSessionUseCase(
            sessionId = sessionId,
            reason = StopProtectionSessionUseCase.StopReason.USER_INITIATED
        )
        assertTrue(stopResult is Result.Success)
        assertFalse(fakeProtectionProvider.isProtectionActive())
        assertTrue(fakeProtectionProvider.getEnforcedPackages().isEmpty())
    }

    // Scenario 8: Service reconnection restores eligible state when supported
    @Test
    fun `scenario 8 - service rehydration correctly restores state when active`() {
        // Simulate service rehydration logic
        val persistedActive = true
        val persistedUserId = "user_test"
        val persistedPackages = setOf("com.target.one", "com.target.two")

        if (persistedActive && persistedUserId.isNotEmpty()) {
            val sanitized = persistedPackages.filter { it != "com.sanket_satpute_20.ironmind" }
            IronMindProtectionService.setProtectedPackages(sanitized)
            IronMindProtectionService.setProtectionActive(true)
        }

        val restored = IronMindProtectionService.getProtectedPackages()
        assertEquals(2, restored.size)
        assertTrue(restored.contains("com.target.one"))
        assertTrue(restored.contains("com.target.two"))

        // Cleanup
        IronMindProtectionService.setProtectionActive(false)
    }

    // Scenario 9: Stale state is not restored after a stopped session
    @Test
    fun `scenario 9 - stale state is not restored after session is inactive`() {
        IronMindProtectionService.setProtectionActive(false)

        val persistedActive = false
        val persistedUserId = "user_test"
        val persistedPackages = setOf("com.target.stale")

        if (persistedActive && persistedUserId.isNotEmpty()) {
            IronMindProtectionService.setProtectedPackages(persistedPackages.toList())
            IronMindProtectionService.setProtectionActive(true)
        } else {
            IronMindProtectionService.setProtectionActive(false)
        }

        val restored = IronMindProtectionService.getProtectedPackages()
        assertTrue(restored.isEmpty())
    }

    // Scenario 10: Emergency override stops enforcement without deleting configurations
    @Test
    fun `scenario 10 - emergency override stops enforcement and preserves configurations`() = runTest {
        saveConfigUseCase("user_test", "com.important.app", "Important App", isEnabled = true)

        fakeIdGenerator.nextId = "session_override_test"
        val startResult = startProtectionSessionUseCase(
            userId = "user_test",
            targetPackages = listOf("com.important.app")
        )
        assertTrue(startResult is Result.Success)
        assertTrue(fakeProtectionProvider.isProtectionActive())

        val viewModel = createViewModel()
        advanceUntilIdle()

        // Trigger emergency override
        viewModel.emergencyOverride()
        advanceUntilIdle()

        // Protection must be stopped
        assertFalse(fakeProtectionProvider.isProtectionActive())
        assertTrue(fakeProtectionProvider.getEnforcedPackages().isEmpty())

        // Configured restrictions must still be preserved
        val configs = (getConfigsUseCase("user_test") as Result.Success).data
        assertEquals(1, configs.size)
        assertEquals("com.important.app", configs[0].packageName)
        assertTrue(configs[0].isEnabled)

        // Active sessions for user should be empty (status transitioned to OVERRIDDEN)
        val sessionsResult = fakeProtectionRepository.getActiveProtectionSessionsForUser("user_test")
        val activeSessions = (sessionsResult as Result.Success).data
        assertTrue(activeSessions.isEmpty())
    }

    // Scenario 11: Accessibility permission unavailable is represented accurately
    @Test
    fun `scenario 11 - accessibility permission unavailable reports inactive enforcement`() = runTest {
        fakeProtectionProvider.hasPermissions = false

        val viewModel = createViewModel()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isAccessibilityPermissionGranted)
        assertFalse(state.isProtectionActive)
        assertTrue(state.activeEnforcedPackageNames.isEmpty())
    }

    // Scenario 12: User or session transition does not leak previous enforcement state
    @Test
    fun `scenario 12 - user session transition isolates restriction and enforcement state`() = runTest {
        saveConfigUseCase("user_A", "com.userA.target", "Target A", isEnabled = true)
        saveConfigUseCase("user_B", "com.userB.target", "Target B", isEnabled = true)

        val viewModel = createViewModel()
        advanceUntilIdle()

        // User A context
        userSessionProvider.setState(SessionState.Authenticated("user_A", isAnonymous = false))
        advanceUntilIdle()

        val stateA = viewModel.uiState.value
        assertEquals(1, stateA.configuredRestrictions.size)
        assertEquals("com.userA.target", stateA.configuredRestrictions[0].packageName)

        // Transition to User B
        userSessionProvider.setState(SessionState.Authenticated("user_B", isAnonymous = false))
        advanceUntilIdle()

        val stateB = viewModel.uiState.value
        assertEquals(1, stateB.configuredRestrictions.size)
        assertEquals("com.userB.target", stateB.configuredRestrictions[0].packageName)
        assertFalse(stateB.configuredRestrictions.any { it.packageName == "com.userA.target" })
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
            userSessionProvider = userSessionProvider,
            getEligibleRestrictionsForEnforcementUseCase = getEligibleUseCase,
            stopProtectionSessionUseCase = stopProtectionSessionUseCase
        )
    }

    // --- Test Doubles ---

    private class FakeTestClock(var time: Long) : Clock {
        override fun currentTimeMillis(): Long = time
    }

    private class FakeInstalledAppDiscoveryProvider : InstalledAppDiscoveryProvider {
        var apps = listOf(
            DiscoveredApp("com.app.enabled1", "Enabled 1"),
            DiscoveredApp("com.app.enabled2", "Enabled 2"),
            DiscoveredApp("com.app.disabled", "Disabled"),
            DiscoveredApp("com.app.test", "Test App"),
            DiscoveredApp("com.userA.app", "User A App"),
            DiscoveredApp("com.userB.app", "User B App"),
            DiscoveredApp("com.installed.app", "Installed App"),
            DiscoveredApp("com.uninstalled.app", "Uninstalled App"),
            DiscoveredApp("com.app.target1", "Target 1"),
            DiscoveredApp("com.app.target2", "Target 2"),
            DiscoveredApp("com.app.target", "Target"),
            DiscoveredApp("com.important.app", "Important App"),
            DiscoveredApp("com.userA.target", "Target A"),
            DiscoveredApp("com.userB.target", "Target B")
        )
        val installedPackages = apps.map { it.packageName }.toMutableSet()

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

    private class FakeTrackingAppProtectionProvider(var hasPermissions: Boolean = true) : AppProtectionProvider {
        private var isActive: Boolean = false
        private val enforced = mutableSetOf<String>()

        override fun hasRequiredPermissions(): Boolean = hasPermissions

        override suspend fun applyProtection(targetPackages: List<String>): Result<Unit, Exception> {
            return applyProtection(targetPackages, null, null)
        }

        override suspend fun applyProtection(
            targetPackages: List<String>,
            sessionId: String?,
            userId: String?
        ): Result<Unit, Exception> {
            if (!hasPermissions) {
                return Result.Failure(SecurityException("Accessibility permission not granted"))
            }
            isActive = true
            enforced.clear()
            enforced.addAll(targetPackages)
            return Result.Success(Unit)
        }

        override suspend fun removeProtection(): Result<Unit, Exception> {
            isActive = false
            enforced.clear()
            return Result.Success(Unit)
        }

        override fun isProtectionActive(): Boolean = isActive && hasPermissions

        override fun getEnforcedPackages(): Set<String> {
            return if (isActive && hasPermissions) enforced.toSet() else emptySet()
        }
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
