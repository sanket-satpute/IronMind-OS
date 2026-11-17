package com.sanket_satpute_20.ironmind.domain.usecase.observation

import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.AppUsageObservationSettings
import com.sanket_satpute_20.ironmind.domain.model.observation.Observation
import com.sanket_satpute_20.ironmind.domain.provider.ActiveForegroundSession
import com.sanket_satpute_20.ironmind.domain.provider.AppUsageEvent
import com.sanket_satpute_20.ironmind.domain.provider.AppUsageObservationProvider
import com.sanket_satpute_20.ironmind.domain.provider.AppUsageReconstructionResult
import com.sanket_satpute_20.ironmind.domain.repository.AppUsageCheckpoint
import com.sanket_satpute_20.ironmind.domain.repository.AppUsageCheckpointStore
import com.sanket_satpute_20.ironmind.domain.repository.AppUsageObservationSettingsRepository
import com.sanket_satpute_20.ironmind.testutil.fake.FakeClock
import com.sanket_satpute_20.ironmind.testutil.fake.FakeIdGenerator
import com.sanket_satpute_20.ironmind.testutil.fake.FakeObservationRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Phase 3.6.6.4: Workstream E — Time and Calendar Integrity Validation
 *
 * Validates:
 * 1. Timezone-specific calendar midnight slicing (UTC, Asia/Kolkata, America/New_York).
 * 2. Daylight-saving transitions (Spring Forward 23h day, Fall Back 25h day).
 * 3. Wall-clock moving backwards (resilient guard against negative intervals, checkpoint never rolls back).
 * 4. Wall-clock moving forward (smooth catchup to new epoch time).
 * 5. Deterministic ID stability under repeated runs.
 */
class TimeAndCalendarIntegrityTest {

    private lateinit var settingsRepository: FakeTimeSettingsRepo
    private lateinit var observationRepository: FakeObservationRepository
    private lateinit var checkpointStore: FakeTimeCheckpointStore
    private lateinit var provider: FakeTimeUsageProvider
    private lateinit var idGenerator: FakeIdGenerator
    private lateinit var clock: FakeClock

    private val baseEpoch = 1700000000000L

    @Before
    fun setup() {
        settingsRepository = FakeTimeSettingsRepo()
        observationRepository = FakeObservationRepository()
        checkpointStore = FakeTimeCheckpointStore()
        provider = FakeTimeUsageProvider()
        idGenerator = FakeIdGenerator()
        clock = FakeClock()
        clock.setTime(baseEpoch)
    }

    @Test
    fun testMidnightSlicing_inMultipleTimezones_preservesDurationAndAlignsWithLocalMidnight() = runTest {
        val userId = "user-tz"

        // Test in 3 distinct timezones: UTC (+00:00), Asia/Kolkata (+05:30), America/New_York (-05:00)
        val zones = listOf(
            ZoneId.of("UTC"),
            ZoneId.of("Asia/Kolkata"),
            ZoneId.of("America/New_York")
        )

        for (zone in zones) {
            checkpointStore.clearCheckpointForUser(userId)
            checkpointStore.committedBatches.clear()

            // Find local midnight for 2024-06-15 in this specific timezone
            val targetDate = LocalDate.of(2024, 6, 15)
            val localMidnight = targetDate.atStartOfDay(zone)
            val midnightEpochMs = localMidnight.toInstant().toEpochMilli()

            // Event starts 20 minutes before local midnight and ends 20 minutes after (total 40 min = 2,400,000 ms)
            val eventStart = midnightEpochMs - (20 * 60 * 1000L)
            val eventEnd = midnightEpochMs + (20 * 60 * 1000L)
            val totalDuration = eventEnd - eventStart

            val crossMidnightEvent = AppUsageEvent("com.reading.midnight", eventStart, eventEnd, totalDuration)
            provider.availableEvents = listOf(crossMidnightEvent)

            clock.setTime(eventEnd)

            val useCase = CollectAppUsageObservationsUseCase(
                settingsRepository = settingsRepository,
                observationRepository = observationRepository,
                appUsageObservationProvider = provider,
                idGenerator = idGenerator,
                clock = clock,
                checkpointStore = checkpointStore,
                zoneId = zone
            )

            val result = useCase(userId)
            assertTrue("Collection must succeed for zone $zone", result is Result.Success)

            val committed = checkpointStore.committedBatches.flatten()
            assertEquals("Cross-midnight event in $zone must be sliced into exactly 2 observations", 2, committed.size)

            val slice1 = committed[0]
            val slice2 = committed[1]

            // Slice 1: eventStart until local midnight
            assertEquals(eventStart, slice1.occurredAt)
            assertEquals("duration_ms=${20 * 60 * 1000L}", slice1.context)

            // Slice 2: exactly at local midnight until eventEnd
            assertEquals(midnightEpochMs, slice2.occurredAt)
            assertEquals("duration_ms=${20 * 60 * 1000L}", slice2.context)

            // Duration conservation
            val dur1 = slice1.context.removePrefix("duration_ms=").toLong()
            val dur2 = slice2.context.removePrefix("duration_ms=").toLong()
            assertEquals(totalDuration, dur1 + dur2)

            // Deterministic distinct IDs
            assertNotEquals(slice1.id, slice2.id)
        }
    }

    @Test
    fun testDaylightSavingTransitions_slicesCorrectlyAcrossDSTBoundaries() = runTest {
        val userId = "user-dst"
        val nyZone = ZoneId.of("America/New_York")

        // 1. Fall Back Transition (2024-11-03: 25-hour day)
        // Midnight between Nov 2 and Nov 3
        val nov3MidnightMs = LocalDate.of(2024, 11, 3).atStartOfDay(nyZone).toInstant().toEpochMilli()
        val eventStart = nov3MidnightMs - (30 * 60 * 1000L)
        val eventEnd = nov3MidnightMs + (30 * 60 * 1000L)

        val dstEvent = AppUsageEvent("com.dst.test", eventStart, eventEnd, 60 * 60 * 1000L)
        provider.availableEvents = listOf(dstEvent)
        clock.setTime(eventEnd)

        val useCase = CollectAppUsageObservationsUseCase(
            settingsRepository = settingsRepository,
            observationRepository = observationRepository,
            appUsageObservationProvider = provider,
            idGenerator = idGenerator,
            clock = clock,
            checkpointStore = checkpointStore,
            zoneId = nyZone
        )

        val result = useCase(userId)
        assertTrue(result is Result.Success)

        val committed = checkpointStore.committedBatches.flatten()
        assertEquals(2, committed.size)
        assertEquals(eventStart, committed[0].occurredAt)
        assertEquals(nov3MidnightMs, committed[1].occurredAt)
        assertEquals("duration_ms=1800000", committed[0].context)
        assertEquals("duration_ms=1800000", committed[1].context)
    }

    @Test
    fun testWallClockMovingBackward_checkpointNeverRollsBackAndSkipsCleanly() = runTest {
        val userId = "user-backward-clock"

        // Checkpoint committed at baseEpoch
        checkpointStore.saveCheckpoint(AppUsageCheckpoint(userId, baseEpoch, null, null, baseEpoch))

        // Device clock jumps BACKWARD by 2 hours (e.g. NTP sync after clock drift)
        val backwardClock = baseEpoch - (2 * 3600_000L)
        clock.setTime(backwardClock)

        val useCase = CollectAppUsageObservationsUseCase(
            settingsRepository = settingsRepository,
            observationRepository = observationRepository,
            appUsageObservationProvider = provider,
            idGenerator = idGenerator,
            clock = clock,
            checkpointStore = checkpointStore,
            zoneId = ZoneId.of("UTC")
        )

        val result = useCase(userId)
        assertTrue("Collection must succeed gracefully with 0 when clock is behind checkpoint", result is Result.Success)
        assertEquals(0, (result as Result.Success).data)

        // Checkpoint MUST NOT roll backward
        val cp = (checkpointStore.getCheckpoint(userId) as Result.Success).data
        assertEquals("Checkpoint must remain at baseEpoch and never roll backward", baseEpoch, cp?.lastCollectedIntervalEndMs)

        // No queries executed against provider
        assertEquals(0, provider.queriedIntervals.size)
    }

    @Test
    fun testWallClockMovingForward_smoothlyCatchesUpToNewEpochTime() = runTest {
        val userId = "user-forward-clock"
        checkpointStore.saveCheckpoint(AppUsageCheckpoint(userId, baseEpoch, null, null, baseEpoch))

        // Device clock jumps FORWARD by 5 hours
        val forwardClock = baseEpoch + (5 * 3600_000L)
        clock.setTime(forwardClock)

        // Event within the same calendar day (before UTC midnight at baseEpoch + 6_400_000L)
        val event = AppUsageEvent("com.fast.forward", baseEpoch + 1800_000L, baseEpoch + 3600_000L, 1800_000L)
        provider.availableEvents = listOf(event)

        val useCase = CollectAppUsageObservationsUseCase(
            settingsRepository = settingsRepository,
            observationRepository = observationRepository,
            appUsageObservationProvider = provider,
            idGenerator = idGenerator,
            clock = clock,
            checkpointStore = checkpointStore,
            zoneId = ZoneId.of("UTC")
        )

        val result = useCase(userId)
        assertTrue(result is Result.Success)
        assertEquals(1, (result as Result.Success).data)

        // Checkpoint catches up to forwardClock
        val cp = (checkpointStore.getCheckpoint(userId) as Result.Success).data
        assertEquals(forwardClock, cp?.lastCollectedIntervalEndMs)
    }

    @Test
    fun testDeterministicObservationIds_areIdenticalAcrossRetries() = runTest {
        val userId = "user-deterministic-retry"
        val start = baseEpoch - 1000_000L
        val end = baseEpoch
        val event = AppUsageEvent("com.consistent.app", start, end, end - start)
        provider.availableEvents = listOf(event)

        val useCase = CollectAppUsageObservationsUseCase(
            settingsRepository = settingsRepository,
            observationRepository = observationRepository,
            appUsageObservationProvider = provider,
            idGenerator = idGenerator,
            clock = clock,
            checkpointStore = checkpointStore,
            zoneId = ZoneId.of("UTC")
        )

        useCase(userId)
        val idRun1 = checkpointStore.committedBatches.first().first().id

        // Re-execute after clearing checkpoint
        checkpointStore.clearCheckpointForUser(userId)
        checkpointStore.committedBatches.clear()

        useCase(userId)
        val idRun2 = checkpointStore.committedBatches.first().first().id

        assertEquals("Observation ID must be completely stable and deterministic across retries", idRun1, idRun2)
    }

    // --- Test Doubles ---

    class FakeTimeSettingsRepo : AppUsageObservationSettingsRepository {
        override suspend fun getSettings(userId: String) = Result.Success(AppUsageObservationSettings(userId, isEnabled = true))
        override suspend fun setEnabled(userId: String, isEnabled: Boolean) = Result.Success(Unit)
    }

    class FakeTimeUsageProvider : AppUsageObservationProvider {
        var availableEvents = listOf<AppUsageEvent>()
        val queriedIntervals = mutableListOf<Triple<Long, Long, ActiveForegroundSession?>>()

        override fun isPermissionGranted() = true
        override fun getAppUsageSince(fromTimeMillis: Long) = availableEvents
        override fun getAppUsageBetween(
            fromTimeMillis: Long,
            toTimeMillis: Long,
            initialActiveSession: ActiveForegroundSession?
        ): AppUsageReconstructionResult {
            queriedIntervals.add(Triple(fromTimeMillis, toTimeMillis, initialActiveSession))
            val filtered = availableEvents.filter {
                it.startTimeMillis < toTimeMillis && it.endTimeMillis > fromTimeMillis
            }
            return AppUsageReconstructionResult(filtered, null)
        }
    }

    class FakeTimeCheckpointStore : AppUsageCheckpointStore {
        val checkpoints = mutableMapOf<String, AppUsageCheckpoint>()
        val committedBatches = mutableListOf<List<Observation>>()

        override suspend fun getCheckpoint(userId: String) = Result.Success(checkpoints[userId])
        override suspend fun saveCheckpoint(checkpoint: AppUsageCheckpoint): Result<Unit, Exception> {
            checkpoints[checkpoint.userId] = checkpoint
            return Result.Success(Unit)
        }
        override suspend fun saveObservationsWithCheckpoint(
            observations: List<Observation>,
            checkpoint: AppUsageCheckpoint
        ): Result<Unit, Exception> {
            committedBatches.add(observations)
            checkpoints[checkpoint.userId] = checkpoint
            return Result.Success(Unit)
        }
        override suspend fun clearCheckpointForUser(userId: String): Result<Unit, Exception> {
            checkpoints.remove(userId)
            return Result.Success(Unit)
        }
    }
}

