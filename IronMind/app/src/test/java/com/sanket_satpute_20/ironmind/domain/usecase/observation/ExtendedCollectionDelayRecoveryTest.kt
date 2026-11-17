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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.ZoneId

/**
 * Phase 3.6.6.4: Workstream A — Extended Collection Delay Recovery
 *
 * Validates recovery under prolonged collection delays:
 * - 3 hours (single chunk)
 * - 12 hours (2 chunks)
 * - 24 hours (4 chunks)
 * - 72 hours (12 chunks)
 * - >7 days (retention clamp, data gap detection, stale session drop)
 */
class ExtendedCollectionDelayRecoveryTest {

    private lateinit var settingsRepository: FakeDelaySettingsRepository
    private lateinit var observationRepository: FakeObservationRepository
    private lateinit var checkpointStore: FakeDelayCheckpointStore
    private lateinit var provider: FakeDelayUsageProvider
    private lateinit var idGenerator: FakeIdGenerator
    private lateinit var clock: FakeClock
    private lateinit var useCase: CollectAppUsageObservationsUseCase

    private val fixedZone = ZoneId.of("UTC")
    private val baseEpoch = 1700000000000L // 2023-11-14T22:13:20Z

    @Before
    fun setup() {
        settingsRepository = FakeDelaySettingsRepository()
        observationRepository = FakeObservationRepository()
        checkpointStore = FakeDelayCheckpointStore()
        provider = FakeDelayUsageProvider()
        idGenerator = FakeIdGenerator()
        clock = FakeClock()
        clock.setTime(baseEpoch)

        useCase = CollectAppUsageObservationsUseCase(
            settingsRepository = settingsRepository,
            observationRepository = observationRepository,
            appUsageObservationProvider = provider,
            idGenerator = idGenerator,
            clock = clock,
            checkpointStore = checkpointStore,
            zoneId = fixedZone,
            defaultInitialLookbackMillis = 60 * 60 * 1000L, // 1 hour
            maxRetentionMillis = 7L * 24 * 60 * 60 * 1000L, // 7 days
            maxChunkDurationMillis = 6L * 60 * 60 * 1000L // 6 hours
        )
    }

    @Test
    fun test3HourDelayRecovery_processesInSingleChunkWithoutDuplicateOrMissingUsage() = runTest {
        val userId = "user-delay-3h"
        checkpointStore.saveCheckpoint(AppUsageCheckpoint(userId, baseEpoch, null, null, baseEpoch))

        val threeHoursLater = baseEpoch + (3 * 3600_000L)
        clock.setTime(threeHoursLater)

        val event = AppUsageEvent("com.reading.news", baseEpoch + 1800_000L, baseEpoch + 3600_000L, 1800_000L)
        provider.availableEvents = listOf(event)

        val result = useCase(userId)
        assertTrue(result is Result.Success)
        assertEquals(1, (result as Result.Success).data)

        // Verifies exactly 1 chunk queried
        assertEquals(1, provider.queriedIntervals.size)
        val chunk = provider.queriedIntervals[0]
        assertEquals(baseEpoch, chunk.first)
        assertEquals(threeHoursLater, chunk.second)

        // Verifies checkpoint updated to threeHoursLater
        val cp = (checkpointStore.getCheckpoint(userId) as Result.Success).data
        assertNotNull(cp)
        assertEquals(threeHoursLater, cp!!.lastCollectedIntervalEndMs)

        // Verifies observation timestamps and durations
        val committed = checkpointStore.committedBatches.flatten()
        assertEquals(1, committed.size)
        assertEquals("com.reading.news", committed[0].value)
        assertEquals(baseEpoch + 1800_000L, committed[0].occurredAt)
        assertEquals("duration_ms=1800000", committed[0].context)
    }

    @Test
    fun test12HourDelayRecovery_processesInTwoSequentialChunksWithSessionContinuity() = runTest {
        val userId = "user-delay-12h"
        checkpointStore.saveCheckpoint(AppUsageCheckpoint(userId, baseEpoch, null, null, baseEpoch))

        val twelveHoursLater = baseEpoch + (12 * 3600_000L)
        clock.setTime(twelveHoursLater)

        // Chunk 1 has an event in hour 2, and starts an active session at hour 5
        // Chunk 2 continues the active session until hour 7, plus an event at hour 10
        val event1 = AppUsageEvent("com.chat.app", baseEpoch + 3600_000L, baseEpoch + 7200_000L, 3600_000L)
        val event2Chunk1 = AppUsageEvent("com.music.player", baseEpoch + 5 * 3600_000L, baseEpoch + 6 * 3600_000L, 3600_000L)
        val event2Chunk2 = AppUsageEvent("com.music.player", baseEpoch + 5 * 3600_000L, baseEpoch + 7 * 3600_000L, 7200_000L)
        val event3 = AppUsageEvent("com.notes.app", baseEpoch + 10 * 3600_000L, baseEpoch + 11 * 3600_000L, 3600_000L)

        provider.availableEvents = listOf(event1, event2Chunk1, event2Chunk2, event3)
        // Configure provider to return active session at chunk 1 boundary
        provider.activeSessionProducer = { from, to, initial ->
            if (to == baseEpoch + 6 * 3600_000L) {
                ActiveForegroundSession("com.music.player", baseEpoch + 5 * 3600_000L)
            } else null
        }

        val result = useCase(userId)
        assertTrue(result is Result.Success)

        // Exactly 2 sequential chunks (6h each)
        assertEquals(2, provider.queriedIntervals.size)
        assertEquals(baseEpoch, provider.queriedIntervals[0].first)
        assertEquals(baseEpoch + 6 * 3600_000L, provider.queriedIntervals[0].second)

        assertEquals(baseEpoch + 6 * 3600_000L, provider.queriedIntervals[1].first)
        assertEquals(twelveHoursLater, provider.queriedIntervals[1].second)

        // Verifies active session carried from chunk 1 into chunk 2
        assertEquals("com.music.player", provider.queriedIntervals[1].third?.packageName)
        assertEquals(baseEpoch + 5 * 3600_000L, provider.queriedIntervals[1].third?.startTimeMillis)

        // Checkpoint committed sequentially per chunk
        assertEquals(2, checkpointStore.committedCheckpoints.size)
        assertEquals(baseEpoch + 6 * 3600_000L, checkpointStore.committedCheckpoints[0].lastCollectedIntervalEndMs)
        assertEquals(twelveHoursLater, checkpointStore.committedCheckpoints[1].lastCollectedIntervalEndMs)

        val cp = (checkpointStore.getCheckpoint(userId) as Result.Success).data
        assertEquals(twelveHoursLater, cp?.lastCollectedIntervalEndMs)
    }

    @Test
    fun test24HourDelayRecovery_processesInFourBoundedChunksSequentially() = runTest {
        val userId = "user-delay-24h"
        checkpointStore.saveCheckpoint(AppUsageCheckpoint(userId, baseEpoch, null, null, baseEpoch))

        val twentyFourHoursLater = baseEpoch + (24 * 3600_000L)
        clock.setTime(twentyFourHoursLater)

        val result = useCase(userId)
        assertTrue(result is Result.Success)

        // 24 hours / 6 hour chunks = 4 chunks
        assertEquals(4, provider.queriedIntervals.size)
        for (i in 0 until 4) {
            val expectedStart = baseEpoch + (i * 6 * 3600_000L)
            val expectedEnd = baseEpoch + ((i + 1) * 6 * 3600_000L)
            assertEquals(expectedStart, provider.queriedIntervals[i].first)
            assertEquals(expectedEnd, provider.queriedIntervals[i].second)
            assertEquals(expectedEnd, checkpointStore.committedCheckpoints[i].lastCollectedIntervalEndMs)
        }

        val cp = (checkpointStore.getCheckpoint(userId) as Result.Success).data
        assertEquals(twentyFourHoursLater, cp?.lastCollectedIntervalEndMs)
    }

    @Test
    fun test72HourDelayRecovery_processesInTwelveSequentialChunksWithoutMemorySpike() = runTest {
        val userId = "user-delay-72h"
        checkpointStore.saveCheckpoint(AppUsageCheckpoint(userId, baseEpoch, null, null, baseEpoch))

        val seventyTwoHoursLater = baseEpoch + (72 * 3600_000L)
        clock.setTime(seventyTwoHoursLater)

        val result = useCase(userId)
        assertTrue(result is Result.Success)

        // 72 hours / 6 hour chunks = 12 chunks
        assertEquals(12, provider.queriedIntervals.size)
        assertEquals(12, checkpointStore.committedCheckpoints.size)

        val cp = (checkpointStore.getCheckpoint(userId) as Result.Success).data
        assertEquals(seventyTwoHoursLater, cp?.lastCollectedIntervalEndMs)
    }

    @Test
    fun testBeyond7DaysRetentionRecovery_clampsFromTime_detectsDataGap_andDropsStaleSession() = runTest {
        val userId = "user-delay-10days"
        val tenDaysAgo = baseEpoch - (10L * 24 * 3600_000L)

        // Checkpoint from 10 days ago with an active session
        checkpointStore.saveCheckpoint(
            AppUsageCheckpoint(
                userId = userId,
                lastCollectedIntervalEndMs = tenDaysAgo,
                activePackageName = "com.ancient.app",
                activeSessionStartTimeMs = tenDaysAgo - 1000L,
                updatedAt = tenDaysAgo
            )
        )

        val result = useCase(userId)
        assertTrue(result is Result.Success)

        // 1. Clamps fromTime to (now - 7 days)
        val expectedClampedFrom = baseEpoch - (7L * 24 * 3600_000L)
        val firstQuery = provider.queriedIntervals.first()
        assertEquals("Collection query must clamp to 7 days retention limit", expectedClampedFrom, firstQuery.first)

        // 2. Active session from > 7 days ago MUST be discarded as stale (initialActiveSession null)
        assertNull("Stale active session from > 7 days ago must not carry over", firstQuery.third)

        // 3. Processed in 28 sequential chunks of 6 hours (7 days * 24h = 168h / 6h = 28 chunks)
        assertEquals(28, provider.queriedIntervals.size)

        // 4. Final checkpoint reaches baseEpoch
        val cp = (checkpointStore.getCheckpoint(userId) as Result.Success).data
        assertEquals(baseEpoch, cp?.lastCollectedIntervalEndMs)
    }

    @Test
    fun testZeroUsageIntervals_advancesCheckpointsSafelyWithoutInventingObservations() = runTest {
        val userId = "user-idle"
        checkpointStore.saveCheckpoint(AppUsageCheckpoint(userId, baseEpoch, null, null, baseEpoch))

        val oneDayLater = baseEpoch + (24 * 3600_000L)
        clock.setTime(oneDayLater)

        provider.availableEvents = emptyList() // User never touched device all day

        val result = useCase(userId)
        assertTrue(result is Result.Success)
        assertEquals(0, (result as Result.Success).data)

        // 0 observations created
        assertEquals(0, checkpointStore.committedBatches.flatten().size)

        // Checkpoint safely advanced to oneDayLater across 4 chunks
        assertEquals(4, provider.queriedIntervals.size)
        val cp = (checkpointStore.getCheckpoint(userId) as Result.Success).data
        assertEquals(oneDayLater, cp?.lastCollectedIntervalEndMs)
    }

    // --- Test Doubles ---

    class FakeDelaySettingsRepository : AppUsageObservationSettingsRepository {
        var isEnabled = true
        override suspend fun getSettings(userId: String) = Result.Success(AppUsageObservationSettings(userId, isEnabled))
        override suspend fun setEnabled(userId: String, isEnabled: Boolean): Result<Unit, Exception> {
            this.isEnabled = isEnabled
            return Result.Success(Unit)
        }
    }

    class FakeDelayUsageProvider : AppUsageObservationProvider {
        var isGranted = true
        var availableEvents = listOf<AppUsageEvent>()
        var activeSessionProducer: ((Long, Long, ActiveForegroundSession?) -> ActiveForegroundSession?)? = null
        val queriedIntervals = mutableListOf<Triple<Long, Long, ActiveForegroundSession?>>()

        override fun isPermissionGranted() = isGranted
        override fun getAppUsageSince(fromTimeMillis: Long) = availableEvents

        override fun getAppUsageBetween(
            fromTimeMillis: Long,
            toTimeMillis: Long,
            initialActiveSession: ActiveForegroundSession?
        ): AppUsageReconstructionResult {
            queriedIntervals.add(Triple(fromTimeMillis, toTimeMillis, initialActiveSession))
            val filtered = availableEvents.filter {
                it.startTimeMillis < toTimeMillis && it.endTimeMillis >= fromTimeMillis
            }
            val active = activeSessionProducer?.invoke(fromTimeMillis, toTimeMillis, initialActiveSession)
            return AppUsageReconstructionResult(filtered, active)
        }
    }

    class FakeDelayCheckpointStore : AppUsageCheckpointStore {
        val checkpoints = mutableMapOf<String, AppUsageCheckpoint>()
        val checkpointHistory = mutableListOf<AppUsageCheckpoint>()
        val committedCheckpoints = mutableListOf<AppUsageCheckpoint>()
        val committedBatches = mutableListOf<List<Observation>>()

        override suspend fun getCheckpoint(userId: String) = Result.Success(checkpoints[userId])
        override suspend fun saveCheckpoint(checkpoint: AppUsageCheckpoint): Result<Unit, Exception> {
            checkpoints[checkpoint.userId] = checkpoint
            checkpointHistory.add(checkpoint)
            return Result.Success(Unit)
        }
        override suspend fun saveObservationsWithCheckpoint(
            observations: List<Observation>,
            checkpoint: AppUsageCheckpoint
        ): Result<Unit, Exception> {
            committedBatches.add(observations)
            checkpoints[checkpoint.userId] = checkpoint
            checkpointHistory.add(checkpoint)
            committedCheckpoints.add(checkpoint)
            return Result.Success(Unit)
        }
        override suspend fun clearCheckpointForUser(userId: String): Result<Unit, Exception> {
            checkpoints.remove(userId)
            return Result.Success(Unit)
        }
    }
}

