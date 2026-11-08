package com.sanket_satpute_20.ironmind.ui.screens.protection.restriction

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.sanket_satpute_20.ironmind.data.local.IronMindDatabase
import com.sanket_satpute_20.ironmind.data.local.dao.IronMindDao
import com.sanket_satpute_20.ironmind.data.local.entity.UserProfileEntity
import com.sanket_satpute_20.ironmind.data.provider.AndroidAppProtectionProvider
import com.sanket_satpute_20.ironmind.data.provider.ProtectionEnforcementStore
import com.sanket_satpute_20.ironmind.data.repository.EventRepositoryImpl
import com.sanket_satpute_20.ironmind.data.repository.ProtectionRepositoryImpl
import com.sanket_satpute_20.ironmind.domain.common.Clock
import com.sanket_satpute_20.ironmind.domain.common.IdGenerator
import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.EntitySource
import com.sanket_satpute_20.ironmind.domain.model.ProtectionSession
import com.sanket_satpute_20.ironmind.domain.model.ProtectionSessionStatus
import com.sanket_satpute_20.ironmind.domain.model.digital.AppRestrictionConfig
import com.sanket_satpute_20.ironmind.domain.model.digital.AppRestrictionConfigCodec
import com.sanket_satpute_20.ironmind.domain.provider.AppProtectionProvider
import com.sanket_satpute_20.ironmind.domain.provider.DiscoveredApp
import com.sanket_satpute_20.ironmind.domain.provider.InstalledAppDiscoveryProvider
import com.sanket_satpute_20.ironmind.domain.session.SessionState
import com.sanket_satpute_20.ironmind.domain.usecase.digital.GetAppRestrictionConfigsUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.digital.GetEligibleRestrictionsForEnforcementUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.digital.SaveAppRestrictionConfigUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.digital.ToggleAppRestrictionConfigUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.protection.AuthoritativeProtectionSessionRecoveryUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.protection.ProtectionRecoveryResult
import com.sanket_satpute_20.ironmind.domain.usecase.protection.StartProtectionSessionUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.protection.StopProtectionSessionUseCase
import com.sanket_satpute_20.ironmind.service.IronMindProtectionService
import com.sanket_satpute_20.ironmind.testutil.FakeUserSessionProvider
import com.sanket_satpute_20.ironmind.testutil.TestDispatcherRule
import com.sanket_satpute_20.ironmind.testutil.fake.FakeClock
import com.sanket_satpute_20.ironmind.testutil.fake.FakeIdGenerator
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Locale

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class AuthoritativeProtectionSessionRecoveryTest {

    @get:Rule
    val dispatcherRule = TestDispatcherRule()

    private lateinit var context: Context
    private lateinit var db: IronMindDatabase
    private lateinit var dao: IronMindDao
    private lateinit var protectionRepository: ProtectionRepositoryImpl
    private lateinit var eventRepository: EventRepositoryImpl
    private lateinit var store: ProtectionEnforcementStore
    private lateinit var userSessionProvider: FakeUserSessionProvider
    private lateinit var fakeDiscoveryProvider: FakeInstalledAppDiscoveryProvider
    private lateinit var fakeProtectionProvider: FakeAlwaysPermittedProtectionProvider
    private lateinit var clock: FakeClock
    private val idGenerator = FakeIdGenerator()

    private lateinit var getConfigsUseCase: GetAppRestrictionConfigsUseCase
    private lateinit var getEligibleUseCase: GetEligibleRestrictionsForEnforcementUseCase
    private lateinit var saveConfigUseCase: SaveAppRestrictionConfigUseCase
    private lateinit var toggleConfigUseCase: ToggleAppRestrictionConfigUseCase
    private lateinit var startProtectionSessionUseCase: StartProtectionSessionUseCase
    private lateinit var stopProtectionSessionUseCase: StopProtectionSessionUseCase
    private lateinit var recoveryUseCase: AuthoritativeProtectionSessionRecoveryUseCase

    private val testUserId = "user_authoritative_test"

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, IronMindDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.ironMindDao()

        dao.insertUserProfile(
            UserProfileEntity(
                id = testUserId,
                createdAt = 1_000_000L,
                updatedAt = 1_000_000L,
                displayName = "Test User",
                timezone = "UTC",
                createdFrom = "TEST",
                status = "ACTIVE",
                schemaVersion = 1
            )
        )

        protectionRepository = ProtectionRepositoryImpl(dao)
        eventRepository = EventRepositoryImpl(dao = dao, database = db)
        store = ProtectionEnforcementStore(context)
        store.clearEnforcementState()
        IronMindProtectionService.setProtectionActive(false)
        IronMindProtectionService.setProtectedPackages(emptySet())
        IronMindProtectionService.setActiveSessionIdentity(null, null)

        userSessionProvider = FakeUserSessionProvider(SessionState.Authenticated(testUserId, isAnonymous = false))
        fakeDiscoveryProvider = FakeInstalledAppDiscoveryProvider()
        fakeProtectionProvider = FakeAlwaysPermittedProtectionProvider(store)
        clock = FakeClock(currentTime = 1_000_000L)

        getConfigsUseCase = GetAppRestrictionConfigsUseCase(protectionRepository)
        getEligibleUseCase = GetEligibleRestrictionsForEnforcementUseCase(getConfigsUseCase, fakeDiscoveryProvider)
        saveConfigUseCase = SaveAppRestrictionConfigUseCase(protectionRepository, clock)
        toggleConfigUseCase = ToggleAppRestrictionConfigUseCase(protectionRepository, clock)

        startProtectionSessionUseCase = StartProtectionSessionUseCase(
            protectionRepository = protectionRepository,
            protectionProvider = fakeProtectionProvider,
            idGenerator = idGenerator,
            clock = clock,
            eventRepository = eventRepository
        )

        stopProtectionSessionUseCase = StopProtectionSessionUseCase(
            protectionRepository = protectionRepository,
            appProtectionProvider = fakeProtectionProvider,
            clock = clock,
            eventRepository = eventRepository
        )

        recoveryUseCase = AuthoritativeProtectionSessionRecoveryUseCase(
            protectionRepository = protectionRepository,
            userSessionProvider = userSessionProvider,
            clock = clock,
            appProtectionProvider = fakeProtectionProvider,
            enforcementStore = store,
            getEligibleRestrictionsForEnforcementUseCase = getEligibleUseCase
        )
    }

    @After
    fun tearDown() {
        store.clearEnforcementState()
        IronMindProtectionService.setProtectionActive(false)
        IronMindProtectionService.setProtectedPackages(emptySet())
        IronMindProtectionService.setActiveSessionIdentity(null, null)
        db.close()
    }

    // 1. Valid active session restores enforcement
    @Test
    fun `scenario 1 - valid active session in room authoritatively restores enforcement`() = runTest {
        saveConfigUseCase(testUserId, "com.target.app", "Target App", isEnabled = true)

        val session = ProtectionSession(
            id = "session_valid_active",
            userId = testUserId,
            startedAt = 1_000_000L,
            scheduledEndAt = 2_000_000L,
            endedAt = null,
            status = ProtectionSessionStatus.ACTIVE,
            source = EntitySource.USER,
            overrideAllowed = true,
            createdAt = 1_000_000L,
            updatedAt = 1_000_000L
        )
        protectionRepository.saveProtectionSession(session)

        store.saveEnforcementState(
            sessionId = session.id,
            userId = testUserId,
            isActive = true,
            packages = setOf("com.target.app")
        )

        // Simulate service reset before recovery
        IronMindProtectionService.setProtectionActive(false)
        IronMindProtectionService.setProtectedPackages(emptySet())

        val result = recoveryUseCase()
        assertTrue(result is ProtectionRecoveryResult.Restored)
        val restored = result as ProtectionRecoveryResult.Restored
        assertEquals(session.id, restored.sessionId)
        assertEquals(testUserId, restored.userId)
        assertTrue(restored.enforcedPackages.contains("com.target.app"))

        assertTrue(IronMindProtectionService.isProtectionActive())
        assertEquals(setOf("com.target.app"), IronMindProtectionService.getProtectedPackages())
        assertEquals(session.id, IronMindProtectionService.getActiveSessionId())
    }

    // 2. Missing session record prevents restoration
    @Test
    fun `scenario 2 - missing session record in room prevents restoration and fails closed`() = runTest {
        // Store points to a non-existent session
        store.saveEnforcementState(
            sessionId = "ghost_session_id",
            userId = testUserId,
            isActive = true,
            packages = setOf("com.target.app")
        )

        val result = recoveryUseCase()
        assertTrue(result is ProtectionRecoveryResult.FailedClosed)
        assertEquals(
            ProtectionRecoveryResult.FailClosedReason.SESSION_NOT_FOUND,
            (result as ProtectionRecoveryResult.FailedClosed).reason
        )

        assertFalse(IronMindProtectionService.isProtectionActive())
        assertTrue(IronMindProtectionService.getProtectedPackages().isEmpty())
        assertNull(store.getEnforcementState()?.sessionId)
    }

    // 3. Stopped session prevents restoration
    @Test
    fun `scenario 3 - stopped session in room prevents restoration and fails closed`() = runTest {
        val session = ProtectionSession(
            id = "session_completed",
            userId = testUserId,
            startedAt = 1_000_000L,
            scheduledEndAt = null,
            endedAt = 1_500_000L,
            status = ProtectionSessionStatus.COMPLETED,
            source = EntitySource.USER,
            overrideAllowed = true,
            createdAt = 1_000_000L,
            updatedAt = 1_500_000L
        )
        protectionRepository.saveProtectionSession(session)

        store.saveEnforcementState(
            sessionId = session.id,
            userId = testUserId,
            isActive = true,
            packages = setOf("com.target.app")
        )

        val result = recoveryUseCase()
        assertTrue(result is ProtectionRecoveryResult.FailedClosed)
        assertEquals(
            ProtectionRecoveryResult.FailClosedReason.SESSION_NOT_ACTIVE,
            (result as ProtectionRecoveryResult.FailedClosed).reason
        )

        assertFalse(IronMindProtectionService.isProtectionActive())
        assertTrue(IronMindProtectionService.getProtectedPackages().isEmpty())
    }

    // 4. Overridden session prevents restoration
    @Test
    fun `scenario 4 - overridden session in room prevents restoration and fails closed`() = runTest {
        val session = ProtectionSession(
            id = "session_overridden",
            userId = testUserId,
            startedAt = 1_000_000L,
            scheduledEndAt = null,
            endedAt = 1_200_000L,
            status = ProtectionSessionStatus.OVERRIDDEN,
            source = EntitySource.USER,
            overrideAllowed = true,
            createdAt = 1_000_000L,
            updatedAt = 1_200_000L
        )
        protectionRepository.saveProtectionSession(session)

        store.saveEnforcementState(
            sessionId = session.id,
            userId = testUserId,
            isActive = true,
            packages = setOf("com.target.app")
        )

        val result = recoveryUseCase()
        assertTrue(result is ProtectionRecoveryResult.FailedClosed)
        assertEquals(
            ProtectionRecoveryResult.FailClosedReason.SESSION_OVERRIDDEN,
            (result as ProtectionRecoveryResult.FailedClosed).reason
        )

        assertFalse(IronMindProtectionService.isProtectionActive())
        assertTrue(IronMindProtectionService.getProtectedPackages().isEmpty())
    }

    // 5. Expired session prevents restoration
    @Test
    fun `scenario 5 - expired session by clock is transitioned to EXPIRED and fails closed`() = runTest {
        val session = ProtectionSession(
            id = "session_expired_clock",
            userId = testUserId,
            startedAt = 1_000_000L,
            scheduledEndAt = 1_500_000L, // Expiration timestamp
            endedAt = null,
            status = ProtectionSessionStatus.ACTIVE,
            source = EntitySource.USER,
            overrideAllowed = true,
            createdAt = 1_000_000L,
            updatedAt = 1_000_000L
        )
        protectionRepository.saveProtectionSession(session)

        store.saveEnforcementState(
            sessionId = session.id,
            userId = testUserId,
            isActive = true,
            packages = setOf("com.target.app")
        )

        // Advance wall clock past scheduledEndAt
        clock.setTime(1_600_000L)

        val result = recoveryUseCase()
        assertTrue(result is ProtectionRecoveryResult.FailedClosed)
        assertEquals(
            ProtectionRecoveryResult.FailClosedReason.SESSION_EXPIRED,
            (result as ProtectionRecoveryResult.FailedClosed).reason
        )

        // Room entity must be updated to EXPIRED
        val dbSession = (protectionRepository.getProtectionSession(session.id) as Result.Success).data
        assertEquals(ProtectionSessionStatus.EXPIRED, dbSession.status)
        assertEquals(1_500_000L, dbSession.endedAt)

        assertFalse(IronMindProtectionService.isProtectionActive())
        assertTrue(IronMindProtectionService.getProtectedPackages().isEmpty())
    }

    // 6. Different user prevents restoration
    @Test
    fun `scenario 6 - session belonging to another user prevents restoration and fails closed`() = runTest {
        val sessionOtherUser = ProtectionSession(
            id = "session_other_user",
            userId = "user_foreign",
            startedAt = 1_000_000L,
            scheduledEndAt = 2_000_000L,
            endedAt = null,
            status = ProtectionSessionStatus.ACTIVE,
            source = EntitySource.USER,
            overrideAllowed = true,
            createdAt = 1_000_000L,
            updatedAt = 1_000_000L
        )
        protectionRepository.saveProtectionSession(sessionOtherUser)

        store.saveEnforcementState(
            sessionId = sessionOtherUser.id,
            userId = "user_foreign",
            isActive = true,
            packages = setOf("com.target.app")
        )

        // Active user is testUserId, not user_foreign
        val result = recoveryUseCase()
        assertTrue(result is ProtectionRecoveryResult.FailedClosed)
        assertEquals(
            ProtectionRecoveryResult.FailClosedReason.USER_MISMATCH,
            (result as ProtectionRecoveryResult.FailedClosed).reason
        )

        assertFalse(IronMindProtectionService.isProtectionActive())
        assertTrue(IronMindProtectionService.getProtectedPackages().isEmpty())
    }

    // 7. Missing or invalid session identity fails closed
    @Test
    fun `scenario 7 - missing or invalid session identity in store fails closed`() = runTest {
        store.saveEnforcementState(
            sessionId = null,
            userId = testUserId,
            isActive = true,
            packages = setOf("com.target.app")
        )

        val result = recoveryUseCase()
        assertTrue(result is ProtectionRecoveryResult.FailedClosed)
        assertEquals(
            ProtectionRecoveryResult.FailClosedReason.MISSING_SESSION_ID,
            (result as ProtectionRecoveryResult.FailedClosed).reason
        )

        assertFalse(IronMindProtectionService.isProtectionActive())
    }

    // 8. Updated restriction configurations are reflected after recovery
    @Test
    fun `scenario 8 - recalculated eligible packages filter out newly disabled restrictions`() = runTest {
        // User originally had app1 and app2 configured and enabled
        saveConfigUseCase(testUserId, "com.app.one", "App One", isEnabled = true)
        val saveRes2 = saveConfigUseCase(testUserId, "com.app.two", "App Two", isEnabled = true)
        val config2 = (saveRes2 as Result.Success).data

        val session = ProtectionSession(
            id = "session_recalculate_test",
            userId = testUserId,
            startedAt = 1_000_000L,
            scheduledEndAt = 2_000_000L,
            endedAt = null,
            status = ProtectionSessionStatus.ACTIVE,
            source = EntitySource.USER,
            overrideAllowed = true,
            createdAt = 1_000_000L,
            updatedAt = 1_000_000L
        )
        protectionRepository.saveProtectionSession(session)

        // Stale snapshot in store includes both
        store.saveEnforcementState(
            sessionId = session.id,
            userId = testUserId,
            isActive = true,
            packages = setOf("com.app.one", "com.app.two")
        )

        // Before recovery, user disabled app2
        toggleConfigUseCase(testUserId, config2.id, isEnabled = false)

        val result = recoveryUseCase()
        assertTrue(result is ProtectionRecoveryResult.Restored)
        val restored = result as ProtectionRecoveryResult.Restored

        // Only app1 remains eligible! app2 must be excluded.
        assertEquals(setOf("com.app.one"), restored.enforcedPackages)
        assertEquals(setOf("com.app.one"), IronMindProtectionService.getProtectedPackages())
    }

    // 9. Emergency Override remains effective after service reconnection
    @Test
    fun `scenario 9 - emergency override terminates session and reconnection never restores it`() = runTest {
        saveConfigUseCase(testUserId, "com.emergency.app", "Emergency App", isEnabled = true)

        idGenerator.nextId = "session_emergency_test"
        val startResult = startProtectionSessionUseCase(
            userId = testUserId,
            targetPackages = listOf("com.emergency.app")
        )
        assertTrue(startResult is Result.Success)
        val sessionId = (startResult as Result.Success).data.id

        assertTrue(IronMindProtectionService.isProtectionActive())

        // Trigger Emergency Override
        val stopResult = stopProtectionSessionUseCase(
            sessionId = sessionId,
            reason = StopProtectionSessionUseCase.StopReason.OVERRIDDEN
        )
        assertTrue(stopResult is Result.Success)
        assertFalse(IronMindProtectionService.isProtectionActive())

        // Verify session in Room is OVERRIDDEN
        val dbSession = (protectionRepository.getProtectionSession(sessionId) as Result.Success).data
        assertEquals(ProtectionSessionStatus.OVERRIDDEN, dbSession.status)

        // Simulate service reconnection after process death
        val recoveryResult = recoveryUseCase()
        assertTrue(
            recoveryResult is ProtectionRecoveryResult.NoEnforcementPending ||
            (recoveryResult is ProtectionRecoveryResult.FailedClosed &&
             (recoveryResult as ProtectionRecoveryResult.FailedClosed).reason == ProtectionRecoveryResult.FailClosedReason.SESSION_OVERRIDDEN)
        )

        assertFalse(IronMindProtectionService.isProtectionActive())
        assertTrue(IronMindProtectionService.getProtectedPackages().isEmpty())
    }

    // 10. Existing start and stop behavior remains correct
    @Test
    fun `scenario 10 - start and stop lifecycle updates Room and store consistently`() = runTest {
        saveConfigUseCase(testUserId, "com.normal.app", "Normal App", isEnabled = true)

        idGenerator.nextId = "session_normal_123"
        val startResult = startProtectionSessionUseCase(
            userId = testUserId,
            targetPackages = listOf("com.normal.app")
        )
        assertTrue(startResult is Result.Success)
        val session = (startResult as Result.Success).data

        // Authoritative Room verification
        val roomSession = (protectionRepository.getProtectionSession(session.id) as Result.Success).data
        assertEquals(ProtectionSessionStatus.ACTIVE, roomSession.status)
        assertNull(roomSession.endedAt)

        // Store verification
        val storeState = store.getEnforcementState()
        assertNotNull(storeState)
        assertEquals(session.id, storeState!!.sessionId)
        assertEquals(testUserId, storeState.userId)
        assertTrue(storeState.isActive)
        assertTrue(storeState.protectedPackages.contains("com.normal.app"))

        // Stop session
        val stopResult = stopProtectionSessionUseCase(
            sessionId = session.id,
            reason = StopProtectionSessionUseCase.StopReason.USER_INITIATED
        )
        assertTrue(stopResult is Result.Success)

        // Room must be COMPLETED
        val completedSession = (protectionRepository.getProtectionSession(session.id) as Result.Success).data
        assertEquals(ProtectionSessionStatus.COMPLETED, completedSession.status)
        assertNotNull(completedSession.endedAt)

        // Store must be inactive
        assertFalse(store.getEnforcementState()?.isActive == true)
        assertFalse(IronMindProtectionService.isProtectionActive())
    }

    // --- Helpers & Doubles ---

    private class FakeInstalledAppDiscoveryProvider : InstalledAppDiscoveryProvider {
        var apps = listOf(
            DiscoveredApp("com.target.app", "Target App"),
            DiscoveredApp("com.app.one", "App One"),
            DiscoveredApp("com.app.two", "App Two"),
            DiscoveredApp("com.emergency.app", "Emergency App"),
            DiscoveredApp("com.normal.app", "Normal App")
        )
        val installedPackages = apps.map { it.packageName }.toMutableSet()

        override fun getLaunchableApps(): List<DiscoveredApp> {
            return apps.sortedWith(
                compareBy<DiscoveredApp> { it.displayName.lowercase(Locale.ROOT) }
                    .thenBy { it.packageName }
            )
        }

        override fun isAppInstalled(packageName: String): Boolean = installedPackages.contains(packageName)

        override fun getAppDisplayName(packageName: String): String? =
            apps.find { it.packageName == packageName }?.displayName
    }

    private class FakeAlwaysPermittedProtectionProvider(
        private val store: ProtectionEnforcementStore
    ) : AppProtectionProvider {
        private var isActive = false
        private val enforced = mutableSetOf<String>()

        override fun hasRequiredPermissions(): Boolean = true

        override suspend fun applyProtection(targetPackages: List<String>): Result<Unit, Exception> {
            return applyProtection(targetPackages, sessionId = null, userId = null)
        }

        override suspend fun applyProtection(
            targetPackages: List<String>,
            sessionId: String?,
            userId: String?
        ): Result<Unit, Exception> {
            val sanitized = targetPackages.filter { it != ProtectionEnforcementStore.IRONMIND_PACKAGE }
            isActive = true
            enforced.clear()
            enforced.addAll(sanitized)
            store.saveEnforcementState(
                sessionId = sessionId,
                userId = userId ?: "active_session",
                isActive = true,
                packages = sanitized.toSet()
            )
            IronMindProtectionService.setActiveSessionIdentity(sessionId, userId)
            IronMindProtectionService.setProtectedPackages(sanitized)
            IronMindProtectionService.setProtectionActive(true)
            return Result.Success(Unit)
        }

        override suspend fun removeProtection(): Result<Unit, Exception> {
            isActive = false
            enforced.clear()
            store.clearEnforcementState()
            IronMindProtectionService.setProtectionActive(false)
            IronMindProtectionService.setProtectedPackages(emptySet())
            IronMindProtectionService.setActiveSessionIdentity(null, null)
            return Result.Success(Unit)
        }

        override fun isProtectionActive(): Boolean = isActive

        override fun getEnforcedPackages(): Set<String> = enforced.toSet()
    }
}
