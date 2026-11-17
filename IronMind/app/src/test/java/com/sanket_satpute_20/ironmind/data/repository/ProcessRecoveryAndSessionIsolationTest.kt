package com.sanket_satpute_20.ironmind.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.sanket_satpute_20.ironmind.data.local.IronMindDatabase
import com.sanket_satpute_20.ironmind.data.local.Jdk25CompatibleRobolectricTestRunner
import com.sanket_satpute_20.ironmind.data.local.entity.toDomain
import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.AppUsageObservationSettings
import com.sanket_satpute_20.ironmind.domain.model.observation.Observation
import com.sanket_satpute_20.ironmind.domain.model.observation.ObservationProvenance
import com.sanket_satpute_20.ironmind.domain.model.observation.ObservationSource
import com.sanket_satpute_20.ironmind.domain.model.observation.ObservationType
import com.sanket_satpute_20.ironmind.domain.provider.ActiveForegroundSession
import com.sanket_satpute_20.ironmind.domain.provider.AppUsageEvent
import com.sanket_satpute_20.ironmind.domain.provider.AppUsageObservationProvider
import com.sanket_satpute_20.ironmind.domain.provider.AppUsageReconstructionResult
import com.sanket_satpute_20.ironmind.domain.repository.AppUsageCheckpoint
import com.sanket_satpute_20.ironmind.domain.repository.AppUsageObservationSettingsRepository
import com.sanket_satpute_20.ironmind.domain.session.SessionState
import com.sanket_satpute_20.ironmind.domain.session.UserSessionProvider
import com.sanket_satpute_20.ironmind.domain.usecase.observation.CollectAppUsageObservationsUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.observation.ExecuteObservationCollectionUseCase
import com.sanket_satpute_20.ironmind.infrastructure.worker.ObservationWorker
import com.sanket_satpute_20.ironmind.testutil.FakeUserSessionProvider
import com.sanket_satpute_20.ironmind.testutil.fake.FakeClock
import com.sanket_satpute_20.ironmind.testutil.fake.FakeIdGenerator
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.ZoneId

/**
 * Phase 3.6.6.4: Workstream C & D — Process Recovery and Session Isolation Validation
 *
 * Uses real Room database to validate:
 * 1. Process termination & reconstruction: checkpoints and observations survive across simulated processes.
 * 2. Multi-user isolation: checkpoints and active foreground sessions remain strictly partitioned.
 * 3. Session generation verification: stale or mismatched session attempts fail-closed.
 */
@RunWith(Jdk25CompatibleRobolectricTestRunner::class)
@Config(sdk = [34])
class ProcessRecoveryAndSessionIsolationTest {

    private lateinit var database: IronMindDatabase
    private lateinit var checkpointStore: RoomAppUsageCheckpointStore
    private lateinit var observationRepository: ObservationRepositoryImpl

    private val baseEpoch = 1700000000000L

    @Before
    fun setUp() {
        val context: Context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, IronMindDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        checkpointStore = RoomAppUsageCheckpointStore(
            database = database,
            observationDao = database.observationDao(),
            checkpointDao = database.appUsageObservationCheckpointDao()
        )
        observationRepository = ObservationRepositoryImpl(database.observationDao())
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun testProcessRestartRecovery_checkpointAndObservationsSurviveAndResumeFromStore() = runBlocking {
        val userId = "user-restart"

        // =====================================================================
        // Process A: Collect observations and persist checkpoint in Room
        // =====================================================================
        val clockA = FakeClock().apply { setTime(baseEpoch) }
        val providerA = FakeProcessUsageProvider()
        val settingsA = FakeProcessSettingsRepo()

        val obs1 = Observation(
            id = "obs-process-1",
            userId = userId,
            type = ObservationType.APP_USAGE_SESSION,
            source = ObservationSource.APP_USAGE,
            occurredAt = baseEpoch - 1800_000L,
            recordedAt = baseEpoch,
            subjectId = null,
            value = "com.process.alpha",
            context = "duration_ms=1800000",
            confidence = null,
            provenance = ObservationProvenance(ObservationSource.APP_USAGE, "UsageStatsManager", baseEpoch)
        )
        val checkpointA = AppUsageCheckpoint(
            userId = userId,
            lastCollectedIntervalEndMs = baseEpoch,
            activePackageName = "com.process.alpha",
            activeSessionStartTimeMs = baseEpoch - 1800_000L,
            updatedAt = baseEpoch
        )

        checkpointStore.saveObservationsWithCheckpoint(listOf(obs1), checkpointA)

        // =====================================================================
        // Simulate Process Death: New repository instances & use case created
        // pointing at the persistent Room SQLite database
        // =====================================================================
        val restoredCheckpointStore = RoomAppUsageCheckpointStore(
            database = database,
            observationDao = database.observationDao(),
            checkpointDao = database.appUsageObservationCheckpointDao()
        )
        val restoredObservationRepo = ObservationRepositoryImpl(database.observationDao())

        // 1. Verify restored process reads the exact persistent checkpoint
        val cpResult = restoredCheckpointStore.getCheckpoint(userId)
        assertTrue(cpResult is Result.Success)
        val restoredCp = (cpResult as Result.Success).data
        assertNotNull("Checkpoint must survive process termination", restoredCp)
        assertEquals(baseEpoch, restoredCp!!.lastCollectedIntervalEndMs)
        assertEquals("com.process.alpha", restoredCp.activePackageName)
        assertEquals(baseEpoch - 1800_000L, restoredCp.activeSessionStartTimeMs)

        // 2. Verify restored process reads previously saved observations
        val obsResult = restoredObservationRepo.getObservations(userId)
        assertTrue(obsResult is Result.Success)
        val restoredObs = (obsResult as Result.Success).data
        assertEquals(1, restoredObs.size)
        assertEquals("com.process.alpha", restoredObs[0].value)

        // 3. New process performs next collection starting from committed checkpoint
        val clockB = FakeClock().apply { setTime(baseEpoch + 3600_000L) }
        val providerB = FakeProcessUsageProvider()
        val useCaseB = CollectAppUsageObservationsUseCase(
            settingsRepository = settingsA,
            observationRepository = restoredObservationRepo,
            appUsageObservationProvider = providerB,
            idGenerator = FakeIdGenerator(),
            clock = clockB,
            checkpointStore = restoredCheckpointStore,
            zoneId = ZoneId.of("UTC")
        )

        val runB = useCaseB(userId)
        assertTrue(runB is Result.Success)

        // Verify providerB received fromTime = baseEpoch and active session
        val call = providerB.queriedIntervals.lastOrNull()
        assertNotNull(call)
        assertEquals("Next collection must resume exactly from committed checkpoint", baseEpoch, call!!.first)
        assertEquals(baseEpoch + 3600_000L, call.second)
        assertEquals("com.process.alpha", call.third?.packageName)

        // Verify updated checkpoint
        val cpFinal = (restoredCheckpointStore.getCheckpoint(userId) as Result.Success).data
        assertEquals(baseEpoch + 3600_000L, cpFinal?.lastCollectedIntervalEndMs)
    }

    @Test
    fun testUserIsolation_checkpointsAndActiveSessionsAreStrictlyPartitioned() = runBlocking {
        val userAlice = "user-alice"
        val userBob = "user-bob"

        // 1. Alice has active session in her checkpoint
        val cpAlice = AppUsageCheckpoint(
            userId = userAlice,
            lastCollectedIntervalEndMs = baseEpoch,
            activePackageName = "com.alice.diary",
            activeSessionStartTimeMs = baseEpoch - 1000L,
            updatedAt = baseEpoch
        )
        checkpointStore.saveCheckpoint(cpAlice)

        // 2. Bob has distinct checkpoint with no active session
        val cpBob = AppUsageCheckpoint(
            userId = userBob,
            lastCollectedIntervalEndMs = baseEpoch - 10_000L,
            activePackageName = null,
            activeSessionStartTimeMs = null,
            updatedAt = baseEpoch - 10_000L
        )
        checkpointStore.saveCheckpoint(cpBob)

        val clock = FakeClock().apply { setTime(baseEpoch + 5000L) }
        val provider = FakeProcessUsageProvider()
        val settings = FakeProcessSettingsRepo()

        val useCase = CollectAppUsageObservationsUseCase(
            settingsRepository = settings,
            observationRepository = observationRepository,
            appUsageObservationProvider = provider,
            idGenerator = FakeIdGenerator(),
            clock = clock,
            checkpointStore = checkpointStore,
            zoneId = ZoneId.of("UTC")
        )

        // Bob collects
        useCase(userBob)
        val bobCall = provider.queriedIntervals.last()
        assertEquals(baseEpoch - 10_000L, bobCall.first)
        assertNull("Alice's active session must NOT leak into Bob's collection query", bobCall.third)

        // Alice collects
        useCase(userAlice)
        val aliceCall = provider.queriedIntervals.last()
        assertEquals(baseEpoch, aliceCall.first)
        assertEquals("com.alice.diary", aliceCall.third?.packageName)

        // Verify independent checkpoint progression
        val bobCpFinal = (checkpointStore.getCheckpoint(userBob) as Result.Success).data
        val aliceCpFinal = (checkpointStore.getCheckpoint(userAlice) as Result.Success).data

        assertEquals(baseEpoch + 5000L, bobCpFinal?.lastCollectedIntervalEndMs)
        assertEquals(baseEpoch + 5000L, aliceCpFinal?.lastCollectedIntervalEndMs)
    }

    @Test
    fun testSessionGenerationWorkerEnforcement_staleGenerationFailsClosed() = runBlocking {
        val fakeSessionProvider = FakeUserSessionProvider(SessionState.Authenticated("user-session", true))
        fakeCollectorGeneration = fakeSessionProvider.getSessionGeneration() // 1L

        val executeUseCase = io.mockk.mockk<ExecuteObservationCollectionUseCase>()
        val context: Context = ApplicationProvider.getApplicationContext()
        val workerParams = io.mockk.mockk<androidx.work.WorkerParameters>(relaxed = true)

        // Supply a stale/mismatched generation 999L
        every { workerParams.inputData } returns androidx.work.Data.Builder()
            .putLong(ObservationWorker.KEY_SESSION_GENERATION, 999L)
            .build()

        val worker = ObservationWorker(context, workerParams, executeUseCase, fakeSessionProvider)
        val workResult = worker.doWork()

        // Stale generation must be rejected with failure without calling collection use case
        assertEquals(androidx.work.ListenableWorker.Result.failure(), workResult)
        io.mockk.coVerify(exactly = 0) { executeUseCase.invoke(any()) }
    }

    companion object {
        var fakeCollectorGeneration = 0L
    }

    // --- Test Doubles ---

    class FakeProcessSettingsRepo : AppUsageObservationSettingsRepository {
        override suspend fun getSettings(userId: String) = Result.Success(AppUsageObservationSettings(userId, isEnabled = true))
        override suspend fun setEnabled(userId: String, isEnabled: Boolean) = Result.Success(Unit)
    }

    class FakeProcessUsageProvider : AppUsageObservationProvider {
        val queriedIntervals = mutableListOf<Triple<Long, Long, ActiveForegroundSession?>>()
        override fun isPermissionGranted() = true
        override fun getAppUsageSince(fromTimeMillis: Long) = emptyList<AppUsageEvent>()
        override fun getAppUsageBetween(
            fromTimeMillis: Long,
            toTimeMillis: Long,
            initialActiveSession: ActiveForegroundSession?
        ): AppUsageReconstructionResult {
            queriedIntervals.add(Triple(fromTimeMillis, toTimeMillis, initialActiveSession))
            return AppUsageReconstructionResult(emptyList(), initialActiveSession)
        }
    }
}

