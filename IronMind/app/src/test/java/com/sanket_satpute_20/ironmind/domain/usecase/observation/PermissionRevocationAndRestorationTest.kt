package com.sanket_satpute_20.ironmind.domain.usecase.observation

import com.sanket_satpute_20.ironmind.domain.common.Clock
import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.engine.ContextEngineImpl
import com.sanket_satpute_20.ironmind.domain.model.AppUsageObservationSettings
import com.sanket_satpute_20.ironmind.domain.model.context.SourceAvailability
import com.sanket_satpute_20.ironmind.domain.model.context.UsagePermissionStatus
import com.sanket_satpute_20.ironmind.domain.model.observation.Observation
import com.sanket_satpute_20.ironmind.domain.model.observation.ObservationType
import com.sanket_satpute_20.ironmind.domain.provider.ActiveForegroundSession
import com.sanket_satpute_20.ironmind.domain.provider.AppUsageEvent
import com.sanket_satpute_20.ironmind.domain.provider.AppUsageObservationProvider
import com.sanket_satpute_20.ironmind.domain.provider.AppUsageReconstructionResult
import com.sanket_satpute_20.ironmind.domain.repository.AppUsageCheckpoint
import com.sanket_satpute_20.ironmind.domain.repository.AppUsageCheckpointStore
import com.sanket_satpute_20.ironmind.domain.repository.AppUsageObservationSettingsRepository
import com.sanket_satpute_20.ironmind.domain.usecase.digital.GetDailyAppUsageIntelligenceUseCase
import com.sanket_satpute_20.ironmind.testutil.fake.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.ZoneId

/**
 * Phase 3.6.6.4: Workstream B — Permission Revocation & Restoration Validation
 *
 * Verifies full end-to-end lifecycle across:
 * 1. Initial collection with permission granted.
 * 2. Permission revocation: checkpoint frozen, observations intact, ContextEngine reports PERMISSION_DENIED.
 * 3. Daily intelligence reports null duration (never false 0ms).
 * 4. Permission restored: catchup resumes cleanly from committed checkpoint without duplicates.
 */
class PermissionRevocationAndRestorationTest {

    private lateinit var settingsRepository: FakePermissionSettingsRepo
    private lateinit var observationRepository: FakeObservationRepository
    private lateinit var checkpointStore: FakePermissionCheckpointStore
    private lateinit var provider: FakePermissionUsageProvider
    private lateinit var idGenerator: FakeIdGenerator
    private lateinit var clock: FakeClock
    private lateinit var collectUseCase: CollectAppUsageObservationsUseCase
    private lateinit var contextEngine: ContextEngineImpl
    private lateinit var dailyIntelligenceUseCase: GetDailyAppUsageIntelligenceUseCase

    private val fixedZone = ZoneId.of("UTC")
    private val baseEpoch = 1700000000000L

    @Before
    fun setup() {
        settingsRepository = FakePermissionSettingsRepo()
        observationRepository = FakeObservationRepository()
        checkpointStore = FakePermissionCheckpointStore(observationRepository)
        provider = FakePermissionUsageProvider()
        idGenerator = FakeIdGenerator()
        clock = FakeClock()
        clock.setTime(baseEpoch)

        collectUseCase = CollectAppUsageObservationsUseCase(
            settingsRepository = settingsRepository,
            observationRepository = observationRepository,
            appUsageObservationProvider = provider,
            idGenerator = idGenerator,
            clock = clock,
            checkpointStore = checkpointStore,
            zoneId = fixedZone
        )

        contextEngine = ContextEngineImpl(
            clock = clock,
            commitmentRepository = FakeCommitmentRepository(),
            eventRepository = FakeEventRepository(),
            buildFactualContextSnapshotUseCase = io.mockk.mockk(relaxed = true),
            reflectionRepository = FakeReflectionRepository(),
            protectionRepository = FakeProtectionRepository(),
            goalRepository = FakeGoalRepository(),
            patternRepository = io.mockk.mockk(relaxed = true),
            appUsageObservationProvider = provider,
            appUsageSettingsRepository = settingsRepository,
            observationRepository = observationRepository,
            checkpointStore = checkpointStore
        )

        dailyIntelligenceUseCase = GetDailyAppUsageIntelligenceUseCase(
            appUsageObservationProvider = provider,
            appUsageSettingsRepository = settingsRepository,
            observationRepository = observationRepository,
            clock = clock
        )
    }

    @Test
    fun testCompletePermissionRevocationAndRestorationLifecycle() = runTest {
        val userId = "user-perm-cycle"
        val windowStart = baseEpoch - 3600_000L // 1h collected window

        // =====================================================================
        // Step 1: Initial state with permission granted. Collect 1 hour of usage.
        // =====================================================================
        provider.isGranted = true
        val event1 = AppUsageEvent("com.android.chrome", baseEpoch - 1800_000L, baseEpoch, 1800_000L) // 30 min
        provider.availableEvents = listOf(event1)

        val run1 = collectUseCase(userId)
        assertTrue(run1 is Result.Success)
        assertEquals(1, (run1 as Result.Success).data)

        // Verify checkpoint advanced
        val cp1 = (checkpointStore.getCheckpoint(userId) as Result.Success).data
        assertNotNull(cp1)
        assertEquals(baseEpoch, cp1!!.lastCollectedIntervalEndMs)

        // Verify ContextEngine reports AVAILABLE
        val ctxResult1 = contextEngine.getUnifiedContext(userId, windowStart, baseEpoch)
        assertTrue(ctxResult1 is Result.Success)
        val ctx1 = (ctxResult1 as Result.Success).data
        assertEquals(UsagePermissionStatus.GRANTED, ctx1.digitalBehaviour.permissionStatus)
        assertEquals(SourceAvailability.AVAILABLE, ctx1.digitalBehaviour.availability)
        assertEquals(1, ctx1.digitalBehaviour.appUsages.size)
        assertEquals(1800_000L, ctx1.digitalBehaviour.appUsages[0].totalDurationMs)

        // =====================================================================
        // Step 2: Permission Revoked. Device clock advances +2 hours.
        // =====================================================================
        provider.isGranted = false
        val twoHoursLater = baseEpoch + 7200_000L
        clock.setTime(twoHoursLater)

        val run2 = collectUseCase(userId)
        assertTrue(run2 is Result.Success)
        assertEquals(0, (run2 as Result.Success).data) // Skipped cleanly

        // Checkpoint MUST NOT advance during denied permission
        val cp2 = (checkpointStore.getCheckpoint(userId) as Result.Success).data
        assertEquals("Checkpoint must remain frozen at baseEpoch while permission is revoked", baseEpoch, cp2?.lastCollectedIntervalEndMs)

        // Existing observations remain intact in repository
        val storedObsRes = observationRepository.getObservations(userId)
        assertTrue(storedObsRes is Result.Success)
        val storedObs = (storedObsRes as Result.Success).data
        assertEquals(1, storedObs.size)
        assertEquals("com.android.chrome", storedObs[0].value)

        // ContextEngine must report PERMISSION_DENIED
        val ctxResult2 = contextEngine.getUnifiedContext(userId, windowStart, twoHoursLater)
        assertTrue(ctxResult2 is Result.Success)
        val ctx2 = (ctxResult2 as Result.Success).data
        assertEquals(UsagePermissionStatus.DENIED, ctx2.digitalBehaviour.permissionStatus)
        assertEquals(SourceAvailability.PERMISSION_DENIED, ctx2.digitalBehaviour.availability)
        assertTrue("appUsages must be empty during permission denial", ctx2.digitalBehaviour.appUsages.isEmpty())

        // DailyAppUsageIntelligence must report totalObservedDurationMs as null (never false 0ms)
        val dailyResult2 = dailyIntelligenceUseCase(userId, targetDayMillis = twoHoursLater, zoneId = fixedZone)
        assertTrue(dailyResult2 is Result.Success)
        val daily2 = (dailyResult2 as Result.Success).data
        assertEquals(UsagePermissionStatus.DENIED, daily2.permissionStatus)
        assertEquals(SourceAvailability.PERMISSION_DENIED, daily2.availability)
        assertNull("Duration must be null when permission is denied, never false zero", daily2.totalObservedDurationMs)

        // =====================================================================
        // Step 3: Permission Restored. Catchup collection resumes.
        // =====================================================================
        provider.isGranted = true
        val event2 = AppUsageEvent("com.android.settings", baseEpoch + 1000_000L, baseEpoch + 2000_000L, 1000_000L)
        provider.availableEvents = listOf(event1, event2)

        val run3 = collectUseCase(userId)
        assertTrue(run3 is Result.Success)
        assertEquals(1, (run3 as Result.Success).data) // Only collected the new event

        // Checkpoint advances to twoHoursLater
        val cp3 = (checkpointStore.getCheckpoint(userId) as Result.Success).data
        assertEquals(twoHoursLater, cp3?.lastCollectedIntervalEndMs)

        // Verify no duplicate observations
        val storedObsFinalRes = observationRepository.getObservations(userId)
        assertTrue(storedObsFinalRes is Result.Success)
        val storedObsFinal = (storedObsFinalRes as Result.Success).data
        assertEquals("Total observations must be exactly 2 (no duplicates)", 2, storedObsFinal.size)

        // ContextEngine returns AVAILABLE with both apps aggregated
        val ctxResult3 = contextEngine.getUnifiedContext(userId, windowStart, twoHoursLater)
        assertTrue(ctxResult3 is Result.Success)
        val ctx3 = (ctxResult3 as Result.Success).data
        assertEquals(UsagePermissionStatus.GRANTED, ctx3.digitalBehaviour.permissionStatus)
        assertEquals(SourceAvailability.AVAILABLE, ctx3.digitalBehaviour.availability)
        assertEquals(2, ctx3.digitalBehaviour.appUsages.size)
        assertEquals(1800_000L, ctx3.digitalBehaviour.appUsages[0].totalDurationMs)
        assertEquals(1000_000L, ctx3.digitalBehaviour.appUsages[1].totalDurationMs)
    }

    // --- Test Doubles ---

    class FakePermissionSettingsRepo : AppUsageObservationSettingsRepository {
        var isEnabled = true
        override suspend fun getSettings(userId: String) = Result.Success(AppUsageObservationSettings(userId, isEnabled))
        override suspend fun setEnabled(userId: String, isEnabled: Boolean): Result<Unit, Exception> {
            this.isEnabled = isEnabled
            return Result.Success(Unit)
        }
    }

    class FakePermissionUsageProvider : AppUsageObservationProvider {
        var isGranted = true
        var availableEvents = listOf<AppUsageEvent>()

        override fun isPermissionGranted() = isGranted
        override fun getAppUsageSince(fromTimeMillis: Long) = availableEvents

        override fun getAppUsageBetween(
            fromTimeMillis: Long,
            toTimeMillis: Long,
            initialActiveSession: ActiveForegroundSession?
        ): AppUsageReconstructionResult {
            if (!isGranted) return AppUsageReconstructionResult(emptyList(), initialActiveSession)
            val filtered = availableEvents.filter {
                it.startTimeMillis < toTimeMillis && it.endTimeMillis > fromTimeMillis
            }
            return AppUsageReconstructionResult(filtered, null)
        }
    }

    class FakePermissionCheckpointStore(private val observationRepository: FakeObservationRepository) : AppUsageCheckpointStore {
        val checkpoints = mutableMapOf<String, AppUsageCheckpoint>()

        override suspend fun getCheckpoint(userId: String) = Result.Success(checkpoints[userId])
        override suspend fun saveCheckpoint(checkpoint: AppUsageCheckpoint): Result<Unit, Exception> {
            checkpoints[checkpoint.userId] = checkpoint
            return Result.Success(Unit)
        }
        override suspend fun saveObservationsWithCheckpoint(
            observations: List<Observation>,
            checkpoint: AppUsageCheckpoint
        ): Result<Unit, Exception> {
            for (obs in observations) {
                observationRepository.insertObservation(obs)
            }
            checkpoints[checkpoint.userId] = checkpoint
            return Result.Success(Unit)
        }
        override suspend fun clearCheckpointForUser(userId: String): Result<Unit, Exception> {
            checkpoints.remove(userId)
            return Result.Success(Unit)
        }
    }
}
