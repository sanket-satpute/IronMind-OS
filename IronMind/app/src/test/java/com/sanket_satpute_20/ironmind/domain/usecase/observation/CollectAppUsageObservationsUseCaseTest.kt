package com.sanket_satpute_20.ironmind.domain.usecase.observation

import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.AppUsageObservationSettings
import com.sanket_satpute_20.ironmind.domain.model.observation.Observation
import com.sanket_satpute_20.ironmind.domain.model.observation.ObservationType
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.ZoneId

class CollectAppUsageObservationsUseCaseTest {

    private lateinit var settingsRepository: FakeAppUsageObservationSettingsRepository
    private lateinit var observationRepository: FakeObservationRepository
    private lateinit var checkpointStore: FakeAppUsageCheckpointStore
    private lateinit var appUsageObservationProvider: FakeAppUsageObservationProvider
    private lateinit var idGenerator: FakeIdGenerator
    private lateinit var clock: FakeClock
    private lateinit var useCase: CollectAppUsageObservationsUseCase

    private val fixedZone = ZoneId.of("UTC")
    private val baseEpoch = 1700000000000L // 2023-11-14T22:13:20Z

    @Before
    fun setup() {
        settingsRepository = FakeAppUsageObservationSettingsRepository()
        observationRepository = FakeObservationRepository()
        checkpointStore = FakeAppUsageCheckpointStore()
        appUsageObservationProvider = FakeAppUsageObservationProvider()
        idGenerator = FakeIdGenerator()
        clock = FakeClock()
        clock.setTime(baseEpoch)

        useCase = CollectAppUsageObservationsUseCase(
            settingsRepository = settingsRepository,
            observationRepository = observationRepository,
            appUsageObservationProvider = appUsageObservationProvider,
            idGenerator = idGenerator,
            clock = clock,
            checkpointStore = checkpointStore,
            zoneId = fixedZone,
            defaultInitialLookbackMillis = 60 * 60 * 1000L, // 1 hour
            maxRetentionMillis = 7L * 24 * 60 * 60 * 1000L,
            maxChunkDurationMillis = 6L * 60 * 60 * 1000L // 6 hours
        )
    }

    // =========================================================================
    // 1. Checkpoint Lifecycle & Atomicity
    // =========================================================================

    @Test
    fun `initial collection creates checkpoint for active user`() = runTest {
        val userId = "user-init"
        val event = AppUsageEvent("com.android.chrome", baseEpoch - 10000, baseEpoch, 10000)
        appUsageObservationProvider.mockEvents = listOf(event)

        val result = useCase(userId)
        assertTrue(result is Result.Success)

        val cp = (checkpointStore.getCheckpoint(userId) as Result.Success).data
        assertNotNull("Checkpoint must be created after initial collection", cp)
        assertEquals(userId, cp!!.userId)
        assertEquals(baseEpoch, cp.lastCollectedIntervalEndMs)
    }

    @Test
    fun `independent checkpoints for different users`() = runTest {
        val user1 = "user-1"
        val user2 = "user-2"

        clock.setTime(1000L)
        useCase(user1)

        clock.setTime(2000L)
        useCase(user2)

        val cp1 = (checkpointStore.getCheckpoint(user1) as Result.Success).data
        val cp2 = (checkpointStore.getCheckpoint(user2) as Result.Success).data

        assertEquals(1000L, cp1?.lastCollectedIntervalEndMs)
        assertEquals(2000L, cp2?.lastCollectedIntervalEndMs)
    }

    @Test
    fun `successful collection advances checkpoint to current clock time`() = runTest {
        val userId = "user-advance"
        // Seed initial checkpoint at baseEpoch
        checkpointStore.saveCheckpoint(AppUsageCheckpoint(userId, baseEpoch, null, null, baseEpoch))

        val newTime = baseEpoch + 3600_000L // +1 hour
        clock.setTime(newTime)

        val result = useCase(userId)
        assertTrue(result is Result.Success)

        val cp = (checkpointStore.getCheckpoint(userId) as Result.Success).data
        assertEquals(newTime, cp?.lastCollectedIntervalEndMs)
    }

    @Test
    fun `failed collection does not advance checkpoint`() = runTest {
        val userId = "user-fail-collect"
        checkpointStore.saveCheckpoint(AppUsageCheckpoint(userId, baseEpoch, null, null, baseEpoch))

        val newTime = baseEpoch + 3600_000L
        clock.setTime(newTime)

        appUsageObservationProvider.shouldThrow = true

        val result = useCase(userId)
        assertTrue("Must return failure when provider throws", result is Result.Failure)

        val cp = (checkpointStore.getCheckpoint(userId) as Result.Success).data
        assertEquals("Checkpoint must not advance on provider failure", baseEpoch, cp?.lastCollectedIntervalEndMs)
    }

    @Test
    fun `failed persistence does not advance checkpoint and rolls back`() = runTest {
        val userId = "user-fail-persist"
        checkpointStore.saveCheckpoint(AppUsageCheckpoint(userId, baseEpoch, null, null, baseEpoch))

        val newTime = baseEpoch + 3600_000L
        clock.setTime(newTime)

        val event = AppUsageEvent("com.fail.app", baseEpoch + 1000, baseEpoch + 2000, 1000)
        appUsageObservationProvider.mockEvents = listOf(event)
        checkpointStore.shouldFailTransaction = true

        val result = useCase(userId)
        assertTrue("Must return failure on persistence error", result is Result.Failure)

        val cp = (checkpointStore.getCheckpoint(userId) as Result.Success).data
        assertEquals("Checkpoint must remain at baseEpoch after transaction rollback", baseEpoch, cp?.lastCollectedIntervalEndMs)
        assertEquals("No observations must be committed if transaction fails", 0, checkpointStore.committedObservationBatches.size)
    }

    // =========================================================================
    // 2. Boundary Reconstruction & Foreground Sessions
    // =========================================================================

    @Test
    fun `active session before checkpoint is passed to query window`() = runTest {
        val userId = "user-resume-before"
        // Active session started at baseEpoch - 1000, not paused at baseEpoch
        checkpointStore.saveCheckpoint(
            AppUsageCheckpoint(
                userId = userId,
                lastCollectedIntervalEndMs = baseEpoch,
                activePackageName = "com.active.app",
                activeSessionStartTimeMs = baseEpoch - 1000,
                updatedAt = baseEpoch
            )
        )

        val newTime = baseEpoch + 2000L
        clock.setTime(newTime)

        useCase(userId)

        // Verify provider received the active session state
        val lastCall = appUsageObservationProvider.recordedCalls.lastOrNull()
        assertNotNull(lastCall)
        assertEquals(baseEpoch, lastCall!!.first) // fromTime
        assertEquals(newTime, lastCall.second)   // toTime
        assertEquals("com.active.app", lastCall.third?.packageName)
        assertEquals(baseEpoch - 1000, lastCall.third?.startTimeMillis)
    }

    @Test
    fun `session longer than 30 minutes updates duration without duplicating row`() = runTest {
        val userId = "user-long-session"
        val sessionStart = baseEpoch
        val firstCollectionTime = sessionStart + 35 * 60 * 1000L // 35 minutes later
        clock.setTime(firstCollectionTime)

        // Session still open at 35 min
        appUsageObservationProvider.mockEvents = listOf(
            AppUsageEvent("com.reading.app", sessionStart, firstCollectionTime, 35 * 60 * 1000L)
        )
        appUsageObservationProvider.mockActiveSession = ActiveForegroundSession("com.reading.app", sessionStart)

        useCase(userId)

        val batch1 = checkpointStore.committedObservationBatches.last()
        assertEquals(1, batch1.size)
        val id1 = batch1.first().id
        assertEquals("duration_ms=2100000", batch1.first().context)

        // Second collection: 60 minutes later, app paused at 60 min
        val secondCollectionTime = sessionStart + 60 * 60 * 1000L
        clock.setTime(secondCollectionTime)
        appUsageObservationProvider.mockEvents = listOf(
            AppUsageEvent("com.reading.app", sessionStart, secondCollectionTime, 60 * 60 * 1000L)
        )
        appUsageObservationProvider.mockActiveSession = null

        useCase(userId)

        val batch2 = checkpointStore.committedObservationBatches.last()
        assertEquals(1, batch2.size)
        val id2 = batch2.first().id
        assertEquals("duration_ms=3600000", batch2.first().context)

        assertEquals("Observation ID must be identical across updates of the same session", id1, id2)
    }

    @Test
    fun `session spanning midnight is split into daily bounded observations`() = runTest {
        val userId = "user-midnight"
        // Midnight in UTC for 2023-11-15T00:00:00Z:
        // 1700000000000L = 2023-11-14T22:13:20Z. Next midnight = 1700006400000L (2023-11-15T00:00:00Z)
        val midnightMs = 1700006400000L

        val eventStart = midnightMs - 15 * 60 * 1000L // 23:45
        val eventEnd = midnightMs + 15 * 60 * 1000L   // 00:15 (next day)
        val totalDuration = 30 * 60 * 1000L

        val spanningEvent = AppUsageEvent(
            packageName = "com.night.owl",
            startTimeMillis = eventStart,
            endTimeMillis = eventEnd,
            totalDurationMillis = totalDuration
        )

        clock.setTime(eventEnd)
        appUsageObservationProvider.mockEvents = listOf(spanningEvent)

        useCase(userId)

        val committedObs = checkpointStore.committedObservationBatches.flatten()
        assertEquals("Cross-midnight event must be split into 2 daily slices", 2, committedObs.size)

        val day1Obs = committedObs.first { it.occurredAt == eventStart }
        val day2Obs = committedObs.first { it.occurredAt == midnightMs }

        assertEquals("duration_ms=${15 * 60 * 1000L}", day1Obs.context)
        assertEquals("duration_ms=${15 * 60 * 1000L}", day2Obs.context)
        assertEquals("com.night.owl", day1Obs.value)
        assertEquals("com.night.owl", day2Obs.value)
        assertNotEquals("Each daily slice must have a distinct deterministic ID", day1Obs.id, day2Obs.id)
    }

    @Test
    fun `open session at collection boundary preserves active state in checkpoint`() = runTest {
        val userId = "user-open-boundary"
        val start = baseEpoch
        val end = baseEpoch + 15 * 60 * 1000L
        clock.setTime(end)

        appUsageObservationProvider.mockEvents = listOf(
            AppUsageEvent("com.youtube.app", start, end, end - start)
        )
        appUsageObservationProvider.mockActiveSession = ActiveForegroundSession("com.youtube.app", start)

        useCase(userId)

        val cp = (checkpointStore.getCheckpoint(userId) as Result.Success).data
        assertEquals("com.youtube.app", cp?.activePackageName)
        assertEquals(start, cp?.activeSessionStartTimeMs)
    }

    // =========================================================================
    // 3. Idempotency & Deterministic Deduplication
    // =========================================================================

    @Test
    fun `SAME INPUT produces SAME ID`() = runTest {
        val userId = "user-idempotent-1"
        val event = AppUsageEvent("com.instagram.android", clock.currentTimeMillis() - 10000, clock.currentTimeMillis(), 10000)
        appUsageObservationProvider.mockEvents = listOf(event)

        useCase(userId)
        val batch1 = checkpointStore.committedObservationBatches.last()
        val id1 = batch1.first().id

        // Re-run collection with identical input
        checkpointStore.clearCheckpointForUser(userId)
        useCase(userId)
        val batch2 = checkpointStore.committedObservationBatches.last()
        val id2 = batch2.first().id

        assertEquals("Observation IDs should be completely identical for the exact same input", id1, id2)
    }

    @Test
    fun `DIFFERENT APP produces DIFFERENT ID`() = runTest {
        val userId = "user-idempotent-2"
        val event1 = AppUsageEvent("com.instagram.android", baseEpoch - 2000L, baseEpoch - 1000L, 1000L)
        val event2 = AppUsageEvent("com.twitter.android", baseEpoch - 2000L, baseEpoch - 1000L, 1000L)

        appUsageObservationProvider.mockEvents = listOf(event1, event2)
        useCase(userId)

        val batch = checkpointStore.committedObservationBatches.last()
        assertEquals(2, batch.size)
        assertNotEquals("Observation IDs must differ when package names differ", batch[0].id, batch[1].id)
    }

    @Test
    fun `DIFFERENT START TIME produces DIFFERENT ID`() = runTest {
        val userId = "user-idempotent-3"
        val event1 = AppUsageEvent("com.instagram.android", baseEpoch - 4000L, baseEpoch - 3000L, 1000L)
        val event2 = AppUsageEvent("com.instagram.android", baseEpoch - 2000L, baseEpoch - 1000L, 1000L)

        appUsageObservationProvider.mockEvents = listOf(event1, event2)
        useCase(userId)

        val batch = checkpointStore.committedObservationBatches.last()
        assertEquals(2, batch.size)
        assertNotEquals("Observation IDs must differ when start times differ", batch[0].id, batch[1].id)
    }

    // =========================================================================
    // 4. Delayed Recovery & Chunking
    // =========================================================================

    @Test
    fun `three hour delayed collection backfills missing window`() = runTest {
        val userId = "user-delayed-3h"
        checkpointStore.saveCheckpoint(AppUsageCheckpoint(userId, baseEpoch, null, null, baseEpoch))

        val threeHoursLater = baseEpoch + 3 * 60 * 60 * 1000L
        clock.setTime(threeHoursLater)

        val eventInGap = AppUsageEvent("com.podcast.app", baseEpoch + 3600_000L, baseEpoch + 5400_000L, 1800_000L)
        appUsageObservationProvider.mockEvents = listOf(eventInGap)

        val result = useCase(userId)
        assertTrue(result is Result.Success)

        val cp = (checkpointStore.getCheckpoint(userId) as Result.Success).data
        assertEquals(threeHoursLater, cp?.lastCollectedIntervalEndMs)

        val savedObs = checkpointStore.committedObservationBatches.flatten()
        assertEquals(1, savedObs.size)
        assertEquals("com.podcast.app", savedObs.first().value)
    }

    @Test
    fun `delayed collection longer than chunk size processes in sequential chunks`() = runTest {
        val userId = "user-multi-chunk"
        checkpointStore.saveCheckpoint(AppUsageCheckpoint(userId, baseEpoch, null, null, baseEpoch))

        // 14 hours later (exceeds maxChunkDurationMillis of 6 hours -> 3 chunks: 6h + 6h + 2h)
        val fourteenHoursLater = baseEpoch + 14 * 60 * 60 * 1000L
        clock.setTime(fourteenHoursLater)

        useCase(userId)

        // Verify provider was queried in 3 chunks
        assertEquals(3, appUsageObservationProvider.recordedCalls.size)
        val call1 = appUsageObservationProvider.recordedCalls[0]
        val call2 = appUsageObservationProvider.recordedCalls[1]
        val call3 = appUsageObservationProvider.recordedCalls[2]

        assertEquals(baseEpoch, call1.first)
        assertEquals(baseEpoch + 6 * 3600_000L, call1.second)

        assertEquals(baseEpoch + 6 * 3600_000L, call2.first)
        assertEquals(baseEpoch + 12 * 3600_000L, call2.second)

        assertEquals(baseEpoch + 12 * 3600_000L, call3.first)
        assertEquals(fourteenHoursLater, call3.second)

        val cp = (checkpointStore.getCheckpoint(userId) as Result.Success).data
        assertEquals(fourteenHoursLater, cp?.lastCollectedIntervalEndMs)
    }

    @Test
    fun `collection beyond Android retention clamps fromTime honestly`() = runTest {
        val userId = "user-retention"
        val tenDaysAgo = baseEpoch - 10L * 24 * 3600_000L
        checkpointStore.saveCheckpoint(AppUsageCheckpoint(userId, tenDaysAgo, null, null, tenDaysAgo))

        useCase(userId)

        val firstCall = appUsageObservationProvider.recordedCalls.first()
        val expectedClampedFrom = baseEpoch - 7L * 24 * 3600_000L // clamped to max retention (7 days)
        assertEquals(expectedClampedFrom, firstCall.first)
    }

    // =========================================================================
    // 5. Epistemic Behavior
    // =========================================================================

    @Test
    fun `permission denied returns success zero without creating observations or advancing checkpoint`() = runTest {
        val userId = "user-perm-denied"
        checkpointStore.saveCheckpoint(AppUsageCheckpoint(userId, baseEpoch, null, null, baseEpoch))
        appUsageObservationProvider.permissionGranted = false

        clock.setTime(baseEpoch + 3600_000L)

        val result = useCase(userId)
        assertTrue(result is Result.Success)
        assertEquals(0, (result as Result.Success).data)

        val cp = (checkpointStore.getCheckpoint(userId) as Result.Success).data
        assertEquals("Checkpoint must not advance when permission is denied", baseEpoch, cp?.lastCollectedIntervalEndMs)
        assertEquals("No observations must be created on permission denial", 0, checkpointStore.committedObservationBatches.size)
    }

    @Test
    fun `user consent disabled returns success zero without advancing checkpoint`() = runTest {
        val userId = "user-consent-disabled"
        checkpointStore.saveCheckpoint(AppUsageCheckpoint(userId, baseEpoch, null, null, baseEpoch))
        settingsRepository.settings = AppUsageObservationSettings(userId, isEnabled = false)

        clock.setTime(baseEpoch + 3600_000L)

        val result = useCase(userId)
        assertTrue(result is Result.Success)
        assertEquals(0, (result as Result.Success).data)

        val cp = (checkpointStore.getCheckpoint(userId) as Result.Success).data
        assertEquals("Checkpoint must not advance when user consent is disabled", baseEpoch, cp?.lastCollectedIntervalEndMs)
    }

    // =========================================================================
    // Inline Test Fakes
    // =========================================================================

    class FakeAppUsageObservationSettingsRepository : AppUsageObservationSettingsRepository {
        var settings = AppUsageObservationSettings("user-1", isEnabled = true)
        override suspend fun getSettings(userId: String): Result<AppUsageObservationSettings, Exception> {
            return Result.Success(settings.copy(userId = userId))
        }
        override suspend fun setEnabled(userId: String, isEnabled: Boolean): Result<Unit, Exception> {
            this.settings = this.settings.copy(userId = userId, isEnabled = isEnabled)
            return Result.Success(Unit)
        }
    }

    class FakeAppUsageObservationProvider : AppUsageObservationProvider {
        var permissionGranted = true
        var shouldThrow = false
        var mockEvents = emptyList<AppUsageEvent>()
        var mockActiveSession: ActiveForegroundSession? = null
        val recordedCalls = mutableListOf<Triple<Long, Long, ActiveForegroundSession?>>()

        override fun isPermissionGranted(): Boolean = permissionGranted

        override fun getAppUsageSince(fromTimeMillis: Long): List<AppUsageEvent> = mockEvents

        override fun getAppUsageBetween(
            fromTimeMillis: Long,
            toTimeMillis: Long,
            initialActiveSession: ActiveForegroundSession?
        ): AppUsageReconstructionResult {
            if (shouldThrow) throw IllegalStateException("Provider simulated failure")
            if (!permissionGranted) return AppUsageReconstructionResult(emptyList(), initialActiveSession)

            recordedCalls.add(Triple(fromTimeMillis, toTimeMillis, initialActiveSession))
            val filteredEvents = mockEvents.filter {
                it.startTimeMillis < toTimeMillis && it.endTimeMillis >= fromTimeMillis
            }
            return AppUsageReconstructionResult(filteredEvents, mockActiveSession)
        }
    }

    class FakeAppUsageCheckpointStore : AppUsageCheckpointStore {
        val checkpoints = mutableMapOf<String, AppUsageCheckpoint>()
        var shouldFailTransaction = false
        val committedObservationBatches = mutableListOf<List<Observation>>()

        override suspend fun getCheckpoint(userId: String): Result<AppUsageCheckpoint?, Exception> {
            return Result.Success(checkpoints[userId])
        }

        override suspend fun saveCheckpoint(checkpoint: AppUsageCheckpoint): Result<Unit, Exception> {
            checkpoints[checkpoint.userId] = checkpoint
            return Result.Success(Unit)
        }

        override suspend fun saveObservationsWithCheckpoint(
            observations: List<Observation>,
            checkpoint: AppUsageCheckpoint
        ): Result<Unit, Exception> {
            if (shouldFailTransaction) {
                return Result.Failure(IllegalStateException("Simulated transaction rollback"))
            }
            committedObservationBatches.add(observations)
            checkpoints[checkpoint.userId] = checkpoint
            return Result.Success(Unit)
        }

        override suspend fun clearCheckpointForUser(userId: String): Result<Unit, Exception> {
            checkpoints.remove(userId)
            return Result.Success(Unit)
        }
    }
}
