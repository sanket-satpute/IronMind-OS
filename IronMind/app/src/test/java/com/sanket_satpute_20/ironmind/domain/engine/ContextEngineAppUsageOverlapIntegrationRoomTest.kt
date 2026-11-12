package com.sanket_satpute_20.ironmind.domain.engine

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.sanket_satpute_20.ironmind.data.local.IronMindDatabase
import com.sanket_satpute_20.ironmind.data.local.Jdk25CompatibleRobolectricTestRunner
import com.sanket_satpute_20.ironmind.data.repository.ObservationRepositoryImpl
import com.sanket_satpute_20.ironmind.data.repository.ProtectionRepositoryImpl
import com.sanket_satpute_20.ironmind.data.repository.RoomAppUsageCheckpointStore
import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.AppUsageObservationSettings
import com.sanket_satpute_20.ironmind.domain.model.EntitySource
import com.sanket_satpute_20.ironmind.domain.model.ProtectionSession
import com.sanket_satpute_20.ironmind.domain.model.ProtectionSessionStatus
import com.sanket_satpute_20.ironmind.domain.model.Task
import com.sanket_satpute_20.ironmind.domain.model.TaskStatus
import com.sanket_satpute_20.ironmind.domain.model.context.FocusOverlapAvailability
import com.sanket_satpute_20.ironmind.domain.model.context.SourceAvailability
import com.sanket_satpute_20.ironmind.domain.model.context.UsagePermissionStatus
import com.sanket_satpute_20.ironmind.domain.model.observation.FactualContextSnapshot
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
import com.sanket_satpute_20.ironmind.domain.repository.CommitmentRepository
import com.sanket_satpute_20.ironmind.domain.repository.EventRepository
import com.sanket_satpute_20.ironmind.domain.repository.GoalRepository
import com.sanket_satpute_20.ironmind.domain.repository.PatternRepository
import com.sanket_satpute_20.ironmind.domain.repository.ProtectionRepository
import com.sanket_satpute_20.ironmind.domain.repository.ReflectionRepository
import com.sanket_satpute_20.ironmind.domain.repository.TaskRepository
import com.sanket_satpute_20.ironmind.domain.usecase.observation.BuildFactualContextSnapshotUseCase
import com.sanket_satpute_20.ironmind.testutil.fake.FakeClock
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.ZoneId
import java.time.ZonedDateTime

@RunWith(Jdk25CompatibleRobolectricTestRunner::class)
@Config(sdk = [34])
class ContextEngineAppUsageOverlapIntegrationRoomTest {

    private lateinit var context: Context
    private lateinit var db: IronMindDatabase
    private lateinit var observationRepository: ObservationRepositoryImpl
    private lateinit var protectionRepository: ProtectionRepositoryImpl
    private lateinit var checkpointStore: RoomAppUsageCheckpointStore
    private lateinit var clock: FakeClock

    private lateinit var commitmentRepository: CommitmentRepository
    private lateinit var eventRepository: EventRepository
    private lateinit var buildFactualContextSnapshotUseCase: BuildFactualContextSnapshotUseCase
    private lateinit var reflectionRepository: ReflectionRepository
    private lateinit var goalRepository: GoalRepository
    private lateinit var patternRepository: PatternRepository
    private lateinit var taskRepository: TaskRepository
    private lateinit var settingsRepository: AppUsageObservationSettingsRepository

    private val userA = "user-usage-a"
    private val userB = "user-usage-b"
    private val zoneUtc = ZoneId.of("UTC")

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, IronMindDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        observationRepository = ObservationRepositoryImpl(db.observationDao())
        protectionRepository = ProtectionRepositoryImpl(db.ironMindDao())
        checkpointStore = RoomAppUsageCheckpointStore(db, db.observationDao(), db.appUsageObservationCheckpointDao())
        clock = FakeClock(ZonedDateTime.of(2026, 3, 12, 12, 0, 0, 0, zoneUtc).toInstant().toEpochMilli())

        commitmentRepository = mockk(relaxed = true)
        eventRepository = mockk(relaxed = true)
        buildFactualContextSnapshotUseCase = mockk(relaxed = true)
        reflectionRepository = mockk(relaxed = true)
        goalRepository = mockk(relaxed = true)
        patternRepository = mockk(relaxed = true)
        taskRepository = mockk(relaxed = true)
        settingsRepository = mockk(relaxed = true)

        coEvery { commitmentRepository.getCommitmentsForUser(any()) } returns Result.Success(emptyList())
        coEvery { eventRepository.getEventsForTimeWindow(any(), any(), any(), any(), any(), any()) } returns Result.Success(emptyList())
        coEvery { buildFactualContextSnapshotUseCase(any(), any(), any()) } returns Result.Success(mockk<FactualContextSnapshot>(relaxed = true))
        coEvery { reflectionRepository.getReflectionsForDateRange(any(), any(), any()) } returns Result.Success(emptyList())
        coEvery { goalRepository.getGoalsForUser(any()) } returns Result.Success(emptyList())
        coEvery { patternRepository.getPatternsForUser(any()) } returns Result.Success(emptyList())
        coEvery { taskRepository.getTasksForUser(any()) } returns Result.Success(emptyList())
        coEvery { settingsRepository.getSettings(any()) } returns Result.Success(AppUsageObservationSettings(userId = userA, isEnabled = true))
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun provider(permissionGranted: Boolean = true) = object : AppUsageObservationProvider {
        override fun isPermissionGranted(): Boolean = permissionGranted
        override fun getAppUsageSince(fromTimeMillis: Long): List<AppUsageEvent> = emptyList()
        override fun getAppUsageBetween(fromTimeMillis: Long, toTimeMillis: Long, initialActiveSession: ActiveForegroundSession?): AppUsageReconstructionResult =
            AppUsageReconstructionResult(emptyList(), null)
    }

    private fun createEngine(
        permissionGranted: Boolean = true,
        protRepo: ProtectionRepository = protectionRepository
    ) = ContextEngineImpl(
        clock = clock,
        commitmentRepository = commitmentRepository,
        eventRepository = eventRepository,
        buildFactualContextSnapshotUseCase = buildFactualContextSnapshotUseCase,
        reflectionRepository = reflectionRepository,
        protectionRepository = protRepo,
        goalRepository = goalRepository,
        patternRepository = patternRepository,
        taskRepository = taskRepository,
        appUsageObservationProvider = provider(permissionGranted),
        appUsageSettingsRepository = settingsRepository,
        observationRepository = observationRepository,
        checkpointStore = checkpointStore,
        zoneId = zoneUtc
    )

    @Test
    fun `Room backed unified context clips boundary crossing intervals computes switching bursts focus overlap and preserves 3_6_7_6B timing`() = runBlocking {
        val windowEnd = clock.currentTimeMillis()
        val windowStart = windowEnd - 3_600_000L // 1 hour window

        // 1. Pre-window overlapping interval (starts 15m before windowStart, lasts 30m -> 15m clipped inside)
        // 2. Short burst of com.app.chat (45s) -> switch #1
        // 3. Longer com.app.docs session (10m) -> switch #2
        // 4. Another user's observation -> must remain isolated
        val obsPre = Observation(
            id = "obs-pre",
            userId = userA,
            type = ObservationType.APP_USAGE_SESSION,
            source = ObservationSource.APP_USAGE,
            occurredAt = windowStart - 900_000L,
            recordedAt = windowStart + 900_000L,
            subjectId = null,
            value = "com.app.video",
            context = "duration_ms=1800000",
            confidence = null,
            provenance = ObservationProvenance(ObservationSource.APP_USAGE, "UsageStatsManager", windowStart + 900_000L)
        )
        val obsBurst = Observation(
            id = "obs-burst",
            userId = userA,
            type = ObservationType.APP_USAGE_SESSION,
            source = ObservationSource.APP_USAGE,
            occurredAt = windowStart + 1_200_000L,
            recordedAt = windowStart + 1_245_000L,
            subjectId = null,
            value = "com.app.chat",
            context = "duration_ms=45000",
            confidence = null,
            provenance = ObservationProvenance(ObservationSource.APP_USAGE, "UsageStatsManager", windowStart + 1_245_000L)
        )
        val obsDocs = Observation(
            id = "obs-docs",
            userId = userA,
            type = ObservationType.APP_USAGE_SESSION,
            source = ObservationSource.APP_USAGE,
            occurredAt = windowStart + 1_500_000L,
            recordedAt = windowStart + 2_100_000L,
            subjectId = null,
            value = "com.app.docs",
            context = "duration_ms=600000",
            confidence = null,
            provenance = ObservationProvenance(ObservationSource.APP_USAGE, "UsageStatsManager", windowStart + 2_100_000L)
        )
        val obsUserB = obsBurst.copy(id = "obs-b", userId = userB, value = "com.app.leak")

        checkpointStore.saveObservationsWithCheckpoint(
            observations = listOf(obsPre, obsBurst, obsDocs, obsUserB),
            checkpoint = AppUsageCheckpoint(userA, windowEnd, null, null, windowEnd)
        )

        // Protection session covering [windowStart + 1_000_000L .. windowStart + 1_600_000L]:
        // Overlaps all of obsBurst (45s) and first 100s of obsDocs -> total overlap = 145s!
        assertTrue(
            protectionRepository.saveProtectionSession(
                ProtectionSession(
                    id = "prot-1",
                    userId = userA,
                    startedAt = windowStart + 1_000_000L,
                    scheduledEndAt = windowStart + 3_000_000L,
                    endedAt = windowStart + 1_600_000L,
                    status = ProtectionSessionStatus.OVERRIDDEN,
                    source = EntitySource.USER,
                    overrideAllowed = true,
                    createdAt = windowStart + 1_000_000L,
                    updatedAt = windowStart + 1_600_000L
                )
            ) is Result.Success
        )

        // Ensure Phase 3.6.7.6B executionTiming still populates cleanly alongside DigitalBehaviourContext
        coEvery { taskRepository.getTasksForUser(userA) } returns Result.Success(
            listOf(
                Task(
                    id = "task-hist",
                    userId = userA,
                    title = "Done",
                    description = "",
                    status = TaskStatus.COMPLETED,
                    priority = 2,
                    dueAt = windowStart + 2_000_000L,
                    completedAt = windowStart + 2_300_000L,
                    createdAt = windowStart,
                    updatedAt = windowStart + 2_300_000L,
                    source = EntitySource.USER
                )
            )
        )

        val result = createEngine().getUnifiedContext(userA, windowStart, windowEnd)
        assertTrue(result is Result.Success)
        val unified = (result as Result.Success).data
        val digital = unified.digitalBehaviour
        val summary = digital.usageTemporalSummary

        assertEquals(SourceAvailability.AVAILABLE, digital.availability)
        assertEquals(3, digital.usageIntervals.size)
        assertEquals(3, summary.totalIntervalCount)
        assertEquals(2, summary.appSwitchCount) // video -> chat -> docs
        assertEquals(1, summary.shortBurstCount) // 45s chat burst only; clipped 15m video raw=30m is NOT a burst!
        // Total clipped usage = 900s + 45s + 600s = 1_545_000ms
        assertEquals(1_545_000L, summary.totalObservedDurationMs)
        assertEquals(digital.appUsages.sumOf { it.totalDurationMs }, summary.totalObservedDurationMs)

        // Focus overlap = 45_000 + 100_000 = 145_000ms
        assertEquals(FocusOverlapAvailability.AVAILABLE, summary.focusOverlap.availability)
        assertEquals(145_000L, summary.focusOverlap.overlappingUsageDurationMs)
        assertEquals(1_400_000L, summary.focusOverlap.outsideProtectionUsageDurationMs)
        assertEquals(2, summary.focusOverlap.intersectingIntervalCount)

        // Phase 3.6.7.6B executionTiming unaffected
        assertEquals(300_000L, unified.productivity.executionTiming.averageCompletionDelayMs)
    }

    @Test
    fun `truncation at cap retains most recent intervals and denied permission or failed protection avoid false zero values`() = runBlocking {
        val windowEnd = clock.currentTimeMillis()
        val windowStart = windowEnd - 3_600_000L

        // Insert 505 observations (> ContextEngineImpl.MAX_WINDOW_APP_USAGE_OBSERVATIONS = 500)
        val obsList = (1..505).map { i ->
            val start = windowStart + i * 2_000L
            Observation(
                id = "obs-$i",
                userId = userA,
                type = ObservationType.APP_USAGE_SESSION,
                source = ObservationSource.APP_USAGE,
                occurredAt = start,
                recordedAt = start + 1_000L,
                subjectId = null,
                value = if (i % 2 == 0) "com.app.a" else "com.app.b",
                context = "duration_ms=1000",
                confidence = null,
                provenance = ObservationProvenance(ObservationSource.APP_USAGE, "UsageStatsManager", start + 1_000L)
            )
        }
        checkpointStore.saveObservationsWithCheckpoint(
            observations = obsList,
            checkpoint = AppUsageCheckpoint(userA, windowEnd, null, null, windowEnd)
        )

        // 1. Truncated + Failed protection repository -> overlap MUST be UNAVAILABLE (null, never 0L!)
        val failingProtectionRepo = mockk<ProtectionRepository>(relaxed = true)
        coEvery { failingProtectionRepo.getProtectionSessionsForUser(userA) } returns Result.Failure(RuntimeException("Protection DB error"))
        coEvery { failingProtectionRepo.getProtectionRulesForUser(userA) } returns Result.Failure(RuntimeException("Protection DB error"))

        val truncatedResult = createEngine(permissionGranted = true, protRepo = failingProtectionRepo)
            .getUnifiedContext(userA, windowStart, windowEnd)
        assertTrue(truncatedResult is Result.Success)
        val truncatedUnified = (truncatedResult as Result.Success).data
        assertEquals(500, truncatedUnified.digitalBehaviour.usageIntervals.size)
        // Verify most recent intervals retained (obs-6..obs-505, NOT oldest obs-1..obs-5)
        assertTrue(truncatedUnified.digitalBehaviour.usageIntervals.any { it.observationId == "obs-505" })
        assertFalse(truncatedUnified.digitalBehaviour.usageIntervals.any { it.observationId == "obs-1" })
        assertTrue(truncatedUnified.digitalBehaviour.usageTemporalSummary.provenance.isTruncated)
        assertTrue(truncatedUnified.provenanceMap["AppUsage"]?.notes?.contains("truncated", ignoreCase = true) == true)
        assertEquals(FocusOverlapAvailability.UNAVAILABLE, truncatedUnified.digitalBehaviour.usageTemporalSummary.focusOverlap.availability)
        assertNull(truncatedUnified.digitalBehaviour.usageTemporalSummary.focusOverlap.overlappingUsageDurationMs)
        assertEquals(SourceAvailability.UNAVAILABLE_HARDWARE, truncatedUnified.provenanceMap["FocusProtection"]?.availability)

        // 2. Denied permission -> usage durations/counts MUST be null (never 0 usage)
        val deniedResult = createEngine(permissionGranted = false)
            .getUnifiedContext(userA, windowStart, windowEnd)
        assertTrue(deniedResult is Result.Success)
        val deniedDigital = (deniedResult as Result.Success).data.digitalBehaviour
        assertEquals(UsagePermissionStatus.DENIED, deniedDigital.permissionStatus)
        assertEquals(SourceAvailability.PERMISSION_DENIED, deniedDigital.availability)
        assertFalse(deniedDigital.usageTemporalSummary.hasUsageData)
        assertNull(deniedDigital.usageTemporalSummary.totalObservedDurationMs)
        assertNull(deniedDigital.usageTemporalSummary.appSwitchCount)
        assertNull(deniedDigital.usageTemporalSummary.shortBurstCount)
        assertNotNull(deniedDigital.usageTemporalSummary.provenance.notes)
    }
}
