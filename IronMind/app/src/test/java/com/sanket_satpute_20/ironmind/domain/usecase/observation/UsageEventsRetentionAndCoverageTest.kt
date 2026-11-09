package com.sanket_satpute_20.ironmind.domain.usecase.observation

import com.sanket_satpute_20.ironmind.domain.common.Clock
import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.engine.ContextEngineImpl
import com.sanket_satpute_20.ironmind.domain.model.AppUsageObservationSettings
import com.sanket_satpute_20.ironmind.domain.model.context.SourceAvailability
import com.sanket_satpute_20.ironmind.domain.model.observation.Observation
import com.sanket_satpute_20.ironmind.domain.provider.ActiveForegroundSession
import com.sanket_satpute_20.ironmind.domain.provider.AppUsageEvent
import com.sanket_satpute_20.ironmind.domain.provider.AppUsageObservationProvider
import com.sanket_satpute_20.ironmind.domain.provider.AppUsageReconstructionResult
import com.sanket_satpute_20.ironmind.domain.repository.AppUsageCheckpoint
import com.sanket_satpute_20.ironmind.domain.repository.AppUsageCheckpointStore
import com.sanket_satpute_20.ironmind.domain.repository.AppUsageObservationSettingsRepository
import com.sanket_satpute_20.ironmind.domain.usecase.digital.GetDailyAppUsageIntelligenceUseCase
import com.sanket_satpute_20.ironmind.testutil.fake.*
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.ZoneId

/**
 * Phase 3.6.6.4R: Section 2 — Android UsageEvents History & Retention Semantics Validation
 *
 * Verifies that the observation pipeline:
 * 1. Treats event availability as OS-managed and variable across devices/OEMs.
 * 2. Never invents missing historical sessions.
 * 3. Preserves explicit PARTIAL_HISTORY or STALE_DATA semantics.
 * 4. Ensures a checkpoint never falsely implies all earlier history was collected.
 * 5. Ensures permission denial produces null duration (never zero).
 * 6. Ensures missing or unavailable event history never becomes fabricated observations.
 */
class UsageEventsRetentionAndCoverageTest {

    private lateinit var observationRepository: FakeObservationRepository
    private lateinit var checkpointStore: FakeRetentionCheckpointStore
    private lateinit var settingsRepository: FakeRetentionSettingsRepo
    private lateinit var provider: FakeRetentionProvider
    private lateinit var clock: FakeClock
    private val zoneId = ZoneId.of("UTC")
    private val userId = "user-retention-test"

    @Before
    fun setup() {
        clock = FakeClock().apply { setTime(1700000000000L) }
        observationRepository = FakeObservationRepository()
        checkpointStore = FakeRetentionCheckpointStore(observationRepository)
        settingsRepository = FakeRetentionSettingsRepo()
        provider = FakeRetentionProvider()
    }

    private fun createUseCase(
        maxChunkDurationMillis: Long = 6 * 3600_000L,
        maxRetentionMillis: Long = 7L * 24 * 3600_000L
    ): CollectAppUsageObservationsUseCase {
        return CollectAppUsageObservationsUseCase(
            settingsRepository = settingsRepository,
            observationRepository = observationRepository,
            appUsageObservationProvider = provider,
            idGenerator = FakeIdGenerator(),
            clock = clock,
            checkpointStore = checkpointStore,
            zoneId = zoneId,
            defaultInitialLookbackMillis = 3600_000L,
            maxRetentionMillis = maxRetentionMillis,
            maxChunkDurationMillis = maxChunkDurationMillis
        )
    }

    @Test
    fun testUnavailableEventHistory_doesNotFabricateObservations_leavesRoomEmptyAndAdvancesCheckpoint() = runBlocking {
        // OS pruned all historical events (e.g. storage pressure or OEM policy)
        val now = 1700000000000L
        clock.setTime(now)

        checkpointStore.saveCheckpoint(AppUsageCheckpoint(userId, now - 3600_000L, null, null, now - 3600_000L))
        provider.reconstructionResult = AppUsageReconstructionResult(emptyList(), null)

        val useCase = createUseCase()
        val result = useCase(userId)

        assertTrue(result is Result.Success)
        assertEquals(0, (result as Result.Success).data)

        val storedObs = (observationRepository.getObservations(userId) as Result.Success).data
        assertEquals("No observations must be fabricated when OS history is unavailable", 0, storedObs.size)

        val finalCheckpoint = (checkpointStore.getCheckpoint(userId) as Result.Success).data
        assertEquals("Checkpoint advances safely to current window end", now, finalCheckpoint?.lastCollectedIntervalEndMs)
    }

    @Test
    fun testIncompleteHistoricalCoverage_reportsPartialHistoryInContextEngine() = runBlocking {
        val now = 1700000000000L
        val windowStart = now - 12 * 3600_000L
        val windowEnd = now
        clock.setTime(now)

        // Checkpoint only collected the last 2 hours [now - 2h, now]
        // Requested window is [now - 12h, now], meaning history from [now - 12h, now - 2h] was never collected
        checkpointStore.saveCheckpoint(
            AppUsageCheckpoint(
                userId = userId,
                lastCollectedIntervalEndMs = now - 1000L, // collection has NOT covered up to windowEnd
                activePackageName = null,
                activeSessionStartTimeMs = null,
                updatedAt = now - 1000L
            )
        )

        val contextEngine = ContextEngineImpl(
            clock = clock,
            commitmentRepository = FakeCommitmentRepository(),
            eventRepository = FakeEventRepository(),
            buildFactualContextSnapshotUseCase = mockk(relaxed = true),
            reflectionRepository = FakeReflectionRepository(),
            protectionRepository = FakeProtectionRepository(),
            goalRepository = FakeGoalRepository(),
            patternRepository = mockk(relaxed = true),
            appUsageObservationProvider = provider,
            appUsageSettingsRepository = settingsRepository,
            observationRepository = observationRepository,
            checkpointStore = checkpointStore
        )

        val contextResult = contextEngine.getUnifiedContext(userId, windowStart, windowEnd)
        assertTrue(contextResult is Result.Success)
        val unified = (contextResult as Result.Success).data

        assertEquals(
            "Incomplete collection history must strictly report PARTIAL_HISTORY",
            SourceAvailability.PARTIAL_HISTORY,
            unified.digitalBehaviour.availability
        )
    }

    @Test
    fun testStaleHistoricalCoverage_reportsStaleDataInContextEngine() = runBlocking {
        val now = 1700000000000L
        clock.setTime(now)

        // Checkpoint is 48 hours old (> 24 hour staleness threshold)
        val staleTime = now - 48 * 3600_000L
        checkpointStore.saveCheckpoint(
            AppUsageCheckpoint(
                userId = userId,
                lastCollectedIntervalEndMs = staleTime,
                activePackageName = null,
                activeSessionStartTimeMs = null,
                updatedAt = staleTime
            )
        )

        val contextEngine = ContextEngineImpl(
            clock = clock,
            commitmentRepository = FakeCommitmentRepository(),
            eventRepository = FakeEventRepository(),
            buildFactualContextSnapshotUseCase = mockk(relaxed = true),
            reflectionRepository = FakeReflectionRepository(),
            protectionRepository = FakeProtectionRepository(),
            goalRepository = FakeGoalRepository(),
            patternRepository = mockk(relaxed = true),
            appUsageObservationProvider = provider,
            appUsageSettingsRepository = settingsRepository,
            observationRepository = observationRepository,
            checkpointStore = checkpointStore
        )

        val contextResult = contextEngine.getUnifiedContext(userId, staleTime - 3600_000L, staleTime)
        assertTrue(contextResult is Result.Success)
        val unified = (contextResult as Result.Success).data

        assertEquals(
            "Checkpoint older than 24h must report STALE_DATA",
            SourceAvailability.STALE_DATA,
            unified.digitalBehaviour.availability
        )
    }

    @Test
    fun testPermissionDenied_reportsExplicitNullDurationAndDoesNotAdvanceCheckpoint() = runBlocking {
        val now = 1700000000000L
        val initialCheckpointTime = now - 5000_000L
        clock.setTime(now)

        provider.permissionGranted = false
        checkpointStore.saveCheckpoint(
            AppUsageCheckpoint(userId, initialCheckpointTime, null, null, initialCheckpointTime)
        )

        // 1. Ingestion use case exits cleanly with 0 collected and does NOT advance checkpoint on permission denied
        val collectUseCase = createUseCase()
        val collectResult = collectUseCase(userId)
        assertTrue("Collection must succeed with 0 collected when permission is skipped", collectResult is Result.Success)
        assertEquals(0, (collectResult as Result.Success).data)

        val finalCheckpoint = (checkpointStore.getCheckpoint(userId) as Result.Success).data
        assertEquals(
            "Checkpoint must freeze and NOT advance when permission is denied",
            initialCheckpointTime,
            finalCheckpoint?.lastCollectedIntervalEndMs
        )

        // 2. Intelligence use case reports explicit null duration (never 0ms)
        val dailyUseCase = GetDailyAppUsageIntelligenceUseCase(
            appUsageObservationProvider = provider,
            appUsageSettingsRepository = settingsRepository,
            observationRepository = observationRepository,
            clock = clock
        )
        val dailyResult = dailyUseCase(userId, now, zoneId)
        assertTrue(dailyResult is Result.Success)
        val daily = (dailyResult as Result.Success).data

        assertEquals(SourceAvailability.PERMISSION_DENIED, daily.availability)
        assertNull("totalObservedDurationMs must be strictly null on permission denial", daily.totalObservedDurationMs)
    }

    @Test
    fun testVariableOSRetentionWindow_boundedByLookbackCeilingWithoutAssumingFixedSevenDays() = runBlocking {
        // Variable OEM scenario: OEM only retains 3 days (e.g. 72 hours)
        val oemRetentionCeiling = 3L * 24 * 3600_000L // 3 days
        val now = 1700000000000L
        clock.setTime(now)

        // Checkpoint from 10 days ago (older than the 3-day OEM retention ceiling)
        val ancientCheckpoint = now - 10L * 24 * 3600_000L
        checkpointStore.saveCheckpoint(
            AppUsageCheckpoint(
                userId = userId,
                lastCollectedIntervalEndMs = ancientCheckpoint,
                activePackageName = "com.stale.active",
                activeSessionStartTimeMs = ancientCheckpoint,
                updatedAt = ancientCheckpoint
            )
        )

        val useCase = createUseCase(maxRetentionMillis = oemRetentionCeiling)
        val result = useCase(userId)
        assertTrue(result is Result.Success)

        val finalCheckpoint = (checkpointStore.getCheckpoint(userId) as Result.Success).data
        assertEquals("Final checkpoint advances to now", now, finalCheckpoint?.lastCollectedIntervalEndMs)
        // Stale active session from ancient checkpoint must be dropped
        assertNull("Ancient active session beyond retention ceiling must be cleared", finalCheckpoint?.activePackageName)
    }

    // --- Fakes ---

    class FakeRetentionProvider : AppUsageObservationProvider {
        var permissionGranted = true
        var reconstructionResult = AppUsageReconstructionResult(emptyList(), null)

        override fun isPermissionGranted(): Boolean = permissionGranted
        override fun getAppUsageSince(fromTimeMillis: Long): List<AppUsageEvent> = reconstructionResult.events
        override fun getAppUsageBetween(
            fromTimeMillis: Long,
            toTimeMillis: Long,
            initialActiveSession: ActiveForegroundSession?
        ): AppUsageReconstructionResult = reconstructionResult
    }

    class FakeRetentionSettingsRepo : AppUsageObservationSettingsRepository {
        override suspend fun getSettings(userId: String): Result<AppUsageObservationSettings, Exception> =
            Result.Success(AppUsageObservationSettings(userId = userId, isEnabled = true))
        override suspend fun setEnabled(userId: String, isEnabled: Boolean): Result<Unit, Exception> = Result.Success(Unit)
    }

    class FakeRetentionCheckpointStore(
        private val observationRepository: FakeObservationRepository
    ) : AppUsageCheckpointStore {
        private val checkpoints = mutableMapOf<String, AppUsageCheckpoint>()

        override suspend fun getCheckpoint(userId: String): Result<AppUsageCheckpoint?, Exception> =
            Result.Success(checkpoints[userId])

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
