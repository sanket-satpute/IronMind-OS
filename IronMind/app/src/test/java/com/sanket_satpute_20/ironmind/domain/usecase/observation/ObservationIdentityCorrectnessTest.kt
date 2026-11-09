package com.sanket_satpute_20.ironmind.domain.usecase.observation

import com.sanket_satpute_20.ironmind.domain.common.Clock
import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.AppUsageObservationSettings
import com.sanket_satpute_20.ironmind.domain.model.observation.Observation
import com.sanket_satpute_20.ironmind.domain.model.observation.ObservationSource
import com.sanket_satpute_20.ironmind.domain.model.observation.ObservationType
import com.sanket_satpute_20.ironmind.domain.provider.ActiveForegroundSession
import com.sanket_satpute_20.ironmind.domain.provider.AppUsageEvent
import com.sanket_satpute_20.ironmind.domain.provider.AppUsageObservationProvider
import com.sanket_satpute_20.ironmind.domain.provider.AppUsageReconstructionResult
import com.sanket_satpute_20.ironmind.domain.repository.AppUsageCheckpoint
import com.sanket_satpute_20.ironmind.domain.repository.AppUsageCheckpointStore
import com.sanket_satpute_20.ironmind.domain.repository.AppUsageObservationSettingsRepository
import com.sanket_satpute_20.ironmind.domain.repository.ObservationRepository
import com.sanket_satpute_20.ironmind.testutil.fake.FakeClock
import com.sanket_satpute_20.ironmind.testutil.fake.FakeIdGenerator
import com.sanket_satpute_20.ironmind.testutil.fake.FakeObservationRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

/**
 * Phase 3.6.6.4R: Section 1 — Observation Identity Correctness Validation
 *
 * Verifies source-level correctness of observation ID generation in the
 * Digital Behaviour Observation Pipeline:
 *
 * Formula:
 * deterministicId = UUID.nameUUIDFromBytes(
 *     "appusage_${userId}_${slice.packageName}_${slice.startTimeMillis}".toByteArray()
 * ).toString()
 *
 * Requirements:
 * 1. Open foreground session receiving same stable ID when extended in later collection.
 * 2. Idempotent result on retrying the same collection.
 * 3. Overlapping collection windows avoiding duplicate observations.
 * 4. Midnight-sliced observations are deterministic.
 * 5. Multi-user and multi-package isolation in generated IDs.
 * 6. Historical backfill avoiding duplicate records.
 * 7. Updating an active session preserves original observation identity.
 */
class ObservationIdentityCorrectnessTest {

    private lateinit var observationRepository: FakeObservationRepository
    private lateinit var checkpointStore: FakeIdentityCheckpointStore
    private lateinit var settingsRepository: FakeIdentitySettingsRepo
    private lateinit var provider: FakeIdentityAppUsageProvider
    private lateinit var clock: FakeClock
    private val zoneId = ZoneId.of("UTC")

    private val userId = "user-identity-test"

    @Before
    fun setup() {
        clock = FakeClock().apply { setTime(1700000000000L) }
        observationRepository = FakeObservationRepository()
        checkpointStore = FakeIdentityCheckpointStore(observationRepository)
        settingsRepository = FakeIdentitySettingsRepo()
        provider = FakeIdentityAppUsageProvider()
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
    fun testOpenForegroundSession_extendedInLaterCollection_maintainsIdenticalObservationIdAndUpdatesDuration() = runBlocking {
        // T0 = 1000: User opens YouTube
        // T1 = 5000: Collection 1 runs while YouTube is still in the foreground
        // T2 = 10000: YouTube closed, Collection 2 runs
        val t0 = 1700000001000L
        val t1 = 1700000005000L
        val t2 = 1700000010000L

        // Collection 1: YouTube still open at T1
        clock.setTime(t1)
        checkpointStore.saveCheckpoint(
            AppUsageCheckpoint(
                userId = userId,
                lastCollectedIntervalEndMs = t0 - 1000L,
                activePackageName = null,
                activeSessionStartTimeMs = null,
                updatedAt = t0 - 1000L
            )
        )

        provider.reconstructionResult = AppUsageReconstructionResult(
            events = listOf(
                AppUsageEvent(
                    packageName = "com.google.android.youtube",
                    startTimeMillis = t0,
                    endTimeMillis = t1,
                    totalDurationMillis = t1 - t0 // 4000ms
                )
            ),
            activeSession = ActiveForegroundSession(
                packageName = "com.google.android.youtube",
                startTimeMillis = t0
            )
        )

        val useCase = createUseCase()
        val result1 = useCase(userId)
        assertTrue(result1 is Result.Success)

        val allObsAfterRun1 = (observationRepository.getObservations(userId) as Result.Success).data
        assertEquals("Run 1 must store exactly 1 observation", 1, allObsAfterRun1.size)
        val initialObs = allObsAfterRun1[0]
        assertEquals("com.google.android.youtube", initialObs.value)
        assertEquals("duration_ms=4000", initialObs.context)
        assertEquals(t0, initialObs.occurredAt)

        // Expected deterministic ID based on (userId, package, startTime)
        val expectedId = UUID.nameUUIDFromBytes(
            "appusage_${userId}_com.google.android.youtube_$t0".toByteArray()
        ).toString()
        assertEquals("Observation ID must match deterministic formula", expectedId, initialObs.id)

        // Collection 2: YouTube closes at T2
        clock.setTime(t2)
        provider.reconstructionResult = AppUsageReconstructionResult(
            events = listOf(
                AppUsageEvent(
                    packageName = "com.google.android.youtube",
                    startTimeMillis = t0,
                    endTimeMillis = t2,
                    totalDurationMillis = t2 - t0 // 9000ms
                )
            ),
            activeSession = null
        )

        val result2 = useCase(userId)
        assertTrue(result2 is Result.Success)

        val allObsAfterRun2 = (observationRepository.getObservations(userId) as Result.Success).data
        assertEquals("Run 2 must NOT create duplicate observation rows in Room", 1, allObsAfterRun2.size)

        val finalObs = allObsAfterRun2[0]
        assertEquals("Final observation ID must be IDENTICAL to initial observation ID", initialObs.id, finalObs.id)
        assertEquals("Final observation duration must be updated to extended 9000ms", "duration_ms=9000", finalObs.context)
        assertEquals("Start timestamp occurredAt must remain invariant", t0, finalObs.occurredAt)
    }

    @Test
    fun testCollectionRetry_producesIdempotentResult() = runBlocking {
        val t0 = 1700000000000L
        val t1 = t0 + 3600_000L
        clock.setTime(t1)

        checkpointStore.saveCheckpoint(
            AppUsageCheckpoint(userId, t0, null, null, t0)
        )

        provider.reconstructionResult = AppUsageReconstructionResult(
            events = listOf(
                AppUsageEvent("com.android.chrome", t0 + 100_000L, t0 + 300_000L, 200_000L),
                AppUsageEvent("com.slack", t0 + 400_000L, t0 + 900_000L, 500_000L)
            ),
            activeSession = null
        )

        val useCase = createUseCase()

        // First execution
        val run1 = useCase(userId)
        assertTrue(run1 is Result.Success)
        val obs1 = (observationRepository.getObservations(userId) as Result.Success).data
        assertEquals(2, obs1.size)

        // Reset checkpoint to simulate retry of the same interval
        checkpointStore.saveCheckpoint(
            AppUsageCheckpoint(userId, t0, null, null, t0)
        )

        // Second execution (retry)
        val run2 = useCase(userId)
        assertTrue(run2 is Result.Success)
        val obs2 = (observationRepository.getObservations(userId) as Result.Success).data
        assertEquals("Retrying collection must produce identical row count without duplicates", 2, obs2.size)
        assertEquals(obs1.map { it.id }.toSet(), obs2.map { it.id }.toSet())
    }

    @Test
    fun testOverlappingCollectionWindows_avoidDuplicateObservations() = runBlocking {
        val t0 = 1700000000000L
        val t1 = t0 + 1800_000L
        val t2 = t0 + 3600_000L

        val event1 = AppUsageEvent("com.ide", t0 + 500_000L, t0 + 1000_000L, 500_000L)

        // Window 1: [t0, t1]
        clock.setTime(t1)
        checkpointStore.saveCheckpoint(AppUsageCheckpoint(userId, t0, null, null, t0))
        provider.reconstructionResult = AppUsageReconstructionResult(listOf(event1), null)

        val useCase = createUseCase()
        useCase(userId)

        // Window 2 overlaps: [t0 + 100_000, t2] re-including event1
        clock.setTime(t2)
        provider.reconstructionResult = AppUsageReconstructionResult(
            listOf(
                event1,
                AppUsageEvent("com.music", t1 + 100_000L, t1 + 500_000L, 400_000L)
            ),
            null
        )
        useCase(userId)

        val obs = (observationRepository.getObservations(userId) as Result.Success).data
        assertEquals("Overlapping window must not duplicate event1 (expected 2 total observations)", 2, obs.size)
        assertEquals(1, obs.count { it.value == "com.ide" })
        assertEquals(1, obs.count { it.value == "com.music" })
    }

    @Test
    fun testMidnightSlicedObservations_areDeterministic() = runBlocking {
        // Base time: 2026-11-09T23:30:00Z (crossing UTC midnight to 2026-11-10T00:30:00Z)
        val startDay = LocalDate.of(2026, 11, 9)
        val midnight = startDay.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli()
        val sessionStart = midnight - 1800_000L // 23:30
        val sessionEnd = midnight + 1800_000L   // 00:30

        clock.setTime(sessionEnd + 1000L)
        checkpointStore.saveCheckpoint(AppUsageCheckpoint(userId, sessionStart - 1000L, null, null, sessionStart))

        val crossMidnightEvent = AppUsageEvent("com.long.reading", sessionStart, sessionEnd, 3600_000L)
        provider.reconstructionResult = AppUsageReconstructionResult(listOf(crossMidnightEvent), null)

        val useCase = createUseCase()
        val result = useCase(userId)
        assertTrue(result is Result.Success)

        val obs = (observationRepository.getObservations(userId) as Result.Success).data
        assertEquals("Cross midnight event must be sliced into exactly 2 observations", 2, obs.size)

        val slice1 = obs.find { it.occurredAt == sessionStart }!!
        val slice2 = obs.find { it.occurredAt == midnight }!!

        val expectedId1 = UUID.nameUUIDFromBytes("appusage_${userId}_com.long.reading_$sessionStart".toByteArray()).toString()
        val expectedId2 = UUID.nameUUIDFromBytes("appusage_${userId}_com.long.reading_$midnight".toByteArray()).toString()

        assertEquals(expectedId1, slice1.id)
        assertEquals(expectedId2, slice2.id)
        assertNotEquals("Slice 1 and Slice 2 must have distinct deterministic IDs", slice1.id, slice2.id)
        assertEquals("duration_ms=1800000", slice1.context)
        assertEquals("duration_ms=1800000", slice2.context)
    }

    @Test
    fun testDifferentUsersAndPackages_areIsolatedInObservationId() {
        val t0 = 1700000000000L
        val pkg1 = "com.app.one"
        val pkg2 = "com.app.two"
        val user1 = "alice"
        val user2 = "bob"

        val idAliceApp1 = UUID.nameUUIDFromBytes("appusage_${user1}_${pkg1}_$t0".toByteArray()).toString()
        val idBobApp1 = UUID.nameUUIDFromBytes("appusage_${user2}_${pkg1}_$t0".toByteArray()).toString()
        val idAliceApp2 = UUID.nameUUIDFromBytes("appusage_${user1}_${pkg2}_$t0".toByteArray()).toString()

        assertNotEquals("Different users with identical package & time must have different IDs", idAliceApp1, idBobApp1)
        assertNotEquals("Same user with different packages must have different IDs", idAliceApp1, idAliceApp2)
    }

    @Test
    fun testHistoricalBackfill_doesNotProduceDuplicateRecords() = runBlocking {
        val t0 = 1700000000000L
        val t1 = t0 + 7200_000L
        clock.setTime(t1)

        checkpointStore.saveCheckpoint(AppUsageCheckpoint(userId, t0, null, null, t0))

        val historicalEvent = AppUsageEvent("com.history.app", t0 + 1000_000L, t0 + 2000_000L, 1000_000L)
        provider.reconstructionResult = AppUsageReconstructionResult(listOf(historicalEvent), null)

        val useCase = createUseCase()
        useCase(userId)

        val obs = (observationRepository.getObservations(userId) as Result.Success).data
        assertEquals(1, obs.size)

        val expectedId = UUID.nameUUIDFromBytes("appusage_${userId}_com.history.app_${t0 + 1000_000L}".toByteArray()).toString()
        assertEquals(expectedId, obs[0].id)
    }

    // --- Fakes ---

    class FakeIdentityAppUsageProvider : AppUsageObservationProvider {
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

    class FakeIdentitySettingsRepo : AppUsageObservationSettingsRepository {
        override suspend fun getSettings(userId: String): Result<AppUsageObservationSettings, Exception> =
            Result.Success(AppUsageObservationSettings(userId = userId, isEnabled = true))
        override suspend fun setEnabled(userId: String, isEnabled: Boolean): Result<Unit, Exception> = Result.Success(Unit)
    }

    class FakeIdentityCheckpointStore(
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
