package com.sanket_satpute_20.ironmind.domain.engine

import com.sanket_satpute_20.ironmind.domain.common.Clock
import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.context.ContextSnapshot
import com.sanket_satpute_20.ironmind.domain.model.Commitment
import com.sanket_satpute_20.ironmind.domain.model.CommitmentStatus
import com.sanket_satpute_20.ironmind.domain.model.Event
import com.sanket_satpute_20.ironmind.domain.model.Goal
import com.sanket_satpute_20.ironmind.domain.model.MemoryConfirmationState
import com.sanket_satpute_20.ironmind.domain.model.pattern.PatternStatus
import com.sanket_satpute_20.ironmind.domain.model.observation.FactualContextSnapshot
import com.sanket_satpute_20.ironmind.domain.model.observation.AppUsageObservationAggregation
import com.sanket_satpute_20.ironmind.domain.model.observation.ActivityObservationAggregation
import com.sanket_satpute_20.ironmind.domain.model.observation.CalendarObservationAggregation
import com.sanket_satpute_20.ironmind.domain.model.observation.LocationObservationAggregation
import com.sanket_satpute_20.ironmind.domain.model.observation.NotificationObservationAggregation
import com.sanket_satpute_20.ironmind.domain.model.ProtectionSession
import com.sanket_satpute_20.ironmind.domain.model.Reflection
import com.sanket_satpute_20.ironmind.domain.model.AppUsageObservationSettings
import com.sanket_satpute_20.ironmind.domain.model.context.EpistemicAuthority
import com.sanket_satpute_20.ironmind.domain.model.context.SourceAvailability
import com.sanket_satpute_20.ironmind.domain.model.context.UsagePermissionStatus
import com.sanket_satpute_20.ironmind.domain.model.observation.Observation
import com.sanket_satpute_20.ironmind.domain.model.observation.ObservationProvenance
import com.sanket_satpute_20.ironmind.domain.model.observation.ObservationSource
import com.sanket_satpute_20.ironmind.domain.model.observation.ObservationType
import com.sanket_satpute_20.ironmind.domain.provider.AppUsageEvent
import com.sanket_satpute_20.ironmind.domain.provider.AppUsageObservationProvider
import com.sanket_satpute_20.ironmind.domain.repository.*
import com.sanket_satpute_20.ironmind.domain.usecase.observation.BuildFactualContextSnapshotUseCase
import com.sanket_satpute_20.ironmind.testutil.fake.FakeObservationRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Calendar

class ContextEngineImplTest {

    private lateinit var clock: FakeClock
    private lateinit var commitmentRepository: FakeCommitmentRepository
    private lateinit var eventRepository: FakeEventRepository
    private lateinit var buildFactualContextSnapshotUseCase: BuildFactualContextSnapshotUseCase
    private lateinit var reflectionRepository: FakeReflectionRepository
    private lateinit var protectionRepository: FakeProtectionRepository
    private lateinit var goalRepository: FakeGoalRepository
    private lateinit var patternRepository: FakePatternRepository
    private lateinit var engine: ContextEngineImpl

    class FakeClock(var time: Long) : Clock {
        override fun currentTimeMillis() = time
    }
    class FakeCommitmentRepository : CommitmentRepository {
        var commitments = emptyList<Commitment>()
        var getCommitmentsForUserResult: Result<List<Commitment>, Exception>? = null
        override suspend fun getCommitment(id: String): Result<Commitment?, Exception> = Result.Failure(Exception())
        override suspend fun getCommitmentsForUser(userId: String): Result<List<Commitment>, Exception> =
            getCommitmentsForUserResult ?: Result.Success(commitments)
        override suspend fun getActiveCommitmentsForUser(userId: String, statuses: List<CommitmentStatus>): Result<List<Commitment>, Exception> = Result.Success(commitments)
        override suspend fun getActiveCommitmentsForUser(userId: String, statuses: List<CommitmentStatus>, limit: Int): Result<List<Commitment>, Exception> = Result.Success(commitments.take(limit))
        override suspend fun getCommitmentsForDateRange(userId: String, startTime: Long, endTime: Long): Result<List<Commitment>, Exception> = Result.Success(commitments)
        override suspend fun getCommitmentsForGoal(goalId: String): Result<List<Commitment>, Exception> = Result.Success(commitments)
        override suspend fun getCommitmentsForPlan(planId: String): Result<List<Commitment>, Exception> = Result.Success(commitments)
        override suspend fun getCommitmentsForTask(taskId: String): Result<List<Commitment>, Exception> = Result.Success(commitments)
        override suspend fun saveCommitment(commitment: Commitment): Result<Unit, Exception> = Result.Success(Unit)
        override suspend fun searchCommitments(userId: String, query: String): Result<List<Commitment>, Exception> = Result.Success(commitments)
    }
    class FakeEventRepository : EventRepository {
        var events = emptyList<Event>()
        override suspend fun getEventsForUser(userId: String): Result<List<Event>, Exception> = Result.Success(events)
        override suspend fun getEventsForEntity(entityId: String): Result<List<Event>, Exception> = Result.Success(events)
        override suspend fun getEvent(id: String): Result<Event?, Exception> = Result.Failure(Exception())
        override suspend fun getEventForUser(userId: String, id: String): Result<Event?, Exception> = Result.Failure(Exception())
        override suspend fun getEventsForDateRange(userId: String, startTime: Long, endTime: Long): Result<List<Event>, Exception> = Result.Success(events)
        override suspend fun saveEvent(event: Event): Result<Event, Exception> = Result.Success(event)
        override suspend fun searchEvents(userId: String, query: String): Result<List<Event>, Exception> = Result.Success(events)
    
    override suspend fun getEventsForTimeWindow(userId: String, startTime: Long, endTime: Long, types: List<com.sanket_satpute_20.ironmind.domain.model.EventType>?, limit: Int, orderAsc: Boolean): com.sanket_satpute_20.ironmind.domain.common.Result<List<com.sanket_satpute_20.ironmind.domain.model.Event>, Exception> = com.sanket_satpute_20.ironmind.domain.common.Result.Success(emptyList())
    override suspend fun getEventsByCausationId(userId: String, causationId: String): Result<List<Event>, Exception> = Result.Success(emptyList())
}

    class FakeReflectionRepository : ReflectionRepository {
        var reflections = emptyList<Reflection>()
        var getReflectionsForDateRangeCallCount = 0
        override suspend fun getReflectionsForDateRange(userId: String, startTime: Long, endTime: Long): Result<List<Reflection>, Exception> {
            getReflectionsForDateRangeCallCount++
            return Result.Success(reflections)
        }
        override suspend fun getReflection(id: String): Result<Reflection?, Exception> = Result.Failure(Exception())
        override suspend fun getReflectionForUser(userId: String, id: String): Result<Reflection?, Exception> = Result.Failure(Exception())
        override suspend fun saveReflection(reflection: Reflection): Result<Unit, Exception> = Result.Success(Unit)
        override suspend fun searchReflections(userId: String, query: String): Result<List<Reflection>, Exception> = Result.Success(reflections)
    
    override suspend fun getReflectionsForTimeWindow(userId: String, startTime: Long, endTime: Long, limit: Int, orderAsc: Boolean): com.sanket_satpute_20.ironmind.domain.common.Result<List<com.sanket_satpute_20.ironmind.domain.model.Reflection>, Exception> = com.sanket_satpute_20.ironmind.domain.common.Result.Success(emptyList())
}
    class FakeProtectionRepository : ProtectionRepository {
        var activeSession: ProtectionSession? = null
        override suspend fun getProtectionRule(id: String): Result<com.sanket_satpute_20.ironmind.domain.model.ProtectionRule, Exception> = Result.Failure(Exception())
        override suspend fun getProtectionRulesForUser(userId: String): Result<List<com.sanket_satpute_20.ironmind.domain.model.ProtectionRule>, Exception> = Result.Success(emptyList())
        override suspend fun saveProtectionRule(rule: com.sanket_satpute_20.ironmind.domain.model.ProtectionRule): Result<Unit, Exception> = Result.Success(Unit)

        override suspend fun getProtectionSession(id: String): Result<ProtectionSession, Exception> = Result.Failure(Exception())
        override suspend fun getActiveProtectionSessionsForUser(userId: String): Result<List<ProtectionSession>, Exception> = Result.Success(listOfNotNull(activeSession))
        override suspend fun saveProtectionSession(session: ProtectionSession): Result<Unit, Exception> = Result.Success(Unit)
    }
    class FakeGoalRepository : GoalRepository {
        var goals = emptyList<Goal>()
        override suspend fun getGoalsForUser(userId: String): Result<List<Goal>, Exception> = Result.Success(goals)
        override suspend fun getGoal(id: String): Result<Goal?, Exception> = Result.Failure(Exception())
        override suspend fun saveGoal(goal: Goal): Result<Unit, Exception> = Result.Success(Unit)
        override suspend fun searchGoals(userId: String, query: String): Result<List<Goal>, Exception> = Result.Success(goals)
        override suspend fun getActiveGoalsForUser(userId: String, limit: Int): Result<List<Goal>, Exception> = Result.Success(goals.take(limit))
    }
    class FakePatternRepository : PatternRepository {
        var patterns = emptyList<com.sanket_satpute_20.ironmind.domain.model.pattern.Pattern>()
        override suspend fun getPatternsForUser(userId: String): Result<List<com.sanket_satpute_20.ironmind.domain.model.pattern.Pattern>, Exception> = Result.Success(patterns)
        override suspend fun getPattern(id: String): Result<com.sanket_satpute_20.ironmind.domain.model.pattern.Pattern?, Exception> = Result.Failure(Exception())
        override suspend fun getPatternsByType(userId: String, type: com.sanket_satpute_20.ironmind.domain.model.pattern.PatternType): Result<List<com.sanket_satpute_20.ironmind.domain.model.pattern.Pattern>, Exception> = Result.Success(patterns)
        override suspend fun getPatternsByStatus(userId: String, status: com.sanket_satpute_20.ironmind.domain.model.pattern.PatternStatus): Result<List<com.sanket_satpute_20.ironmind.domain.model.pattern.Pattern>, Exception> = Result.Success(patterns)
        override suspend fun getPatternsByStatus(userId: String, status: com.sanket_satpute_20.ironmind.domain.model.pattern.PatternStatus, limit: Int): Result<List<com.sanket_satpute_20.ironmind.domain.model.pattern.Pattern>, Exception> = Result.Success(patterns.take(limit))
        override suspend fun savePattern(pattern: com.sanket_satpute_20.ironmind.domain.model.pattern.Pattern): Result<Unit, Exception> = Result.Success(Unit)
        override suspend fun updatePattern(pattern: com.sanket_satpute_20.ironmind.domain.model.pattern.Pattern): Result<Unit, Exception> = Result.Success(Unit)
        override suspend fun getPatternByFingerprint(userId: String, fingerprint: String): Result<com.sanket_satpute_20.ironmind.domain.model.pattern.Pattern?, Exception> = Result.Success(null)
        override suspend fun updatePatternConfidence(id: String, confidence: Float, lastObservedAt: Long): Result<Unit, Exception> = Result.Success(Unit)
        override suspend fun deletePattern(id: String): Result<Unit, Exception> = Result.Success(Unit)
    }

    @Before
    fun setup() {
        clock = FakeClock(1700000000000L) // Some deterministic time
        commitmentRepository = FakeCommitmentRepository()
        eventRepository = FakeEventRepository()
        buildFactualContextSnapshotUseCase = mockk()
        reflectionRepository = FakeReflectionRepository()
        protectionRepository = FakeProtectionRepository()
        goalRepository = FakeGoalRepository()
        patternRepository = FakePatternRepository()

        engine = ContextEngineImpl(
            clock,
            commitmentRepository,
            eventRepository,
            buildFactualContextSnapshotUseCase,
            reflectionRepository,
            protectionRepository,
            goalRepository,
            patternRepository
        )
    }

    @Test
    fun `TEST 1, 2, 3, 4, 9, 10 - exact builder invocation, boundaries, factual snapshot identity, and empty domains`() = runTest {
        val now = 1700000000000L
        val expectedStartTimeMs = now - 86_400_000L
        val userId = "user-1"

        // TEST 4: Empty factual domains verification
        val appUsage = AppUsageObservationAggregation(userId, expectedStartTimeMs, now, 0, 0L, 0)
        val activity = ActivityObservationAggregation(userId, expectedStartTimeMs, now, 0, 0, emptyMap(), null, null)
        val calendar = CalendarObservationAggregation(userId, expectedStartTimeMs, now, 0, 0, 0L, null, null)
        val location = LocationObservationAggregation(userId, expectedStartTimeMs, now, 0, 0, 0, null, null)
        val notifications = NotificationObservationAggregation(userId, expectedStartTimeMs, now, 0, 0, null, null)

        val emptySnapshot = FactualContextSnapshot(
            userId = userId,
            startTimeMs = expectedStartTimeMs,
            endTimeMs = now,
            appUsage = appUsage,
            activity = activity,
            calendar = calendar,
            location = location,
            notifications = notifications
        )

        coEvery { buildFactualContextSnapshotUseCase(userId, expectedStartTimeMs, now) } returns Result.Success(emptySnapshot)

        val result = engine.getCurrentContext(userId)
        assertTrue(result is Result.Success)
        val snapshot = (result as Result.Success).data

        // TEST 1: Exact builder invocation
        coVerify(exactly = 1) { buildFactualContextSnapshotUseCase(userId, expectedStartTimeMs, now) }

        // TEST 2, 9, 10: Boundaries
        assertEquals(now, snapshot.factualContextSnapshot.endTimeMs)
        assertEquals(expectedStartTimeMs, snapshot.factualContextSnapshot.startTimeMs)

        // TEST 3: Factual snapshot identity
        assertEquals(emptySnapshot, snapshot.factualContextSnapshot)

        // TEST 4: Empty factual domains
        assertNotNull(snapshot.factualContextSnapshot.appUsage)
        assertNotNull(snapshot.factualContextSnapshot.activity)
        assertNotNull(snapshot.factualContextSnapshot.calendar)
        assertNotNull(snapshot.factualContextSnapshot.location)
        assertNotNull(snapshot.factualContextSnapshot.notifications)
        assertEquals(0, snapshot.factualContextSnapshot.appUsage!!.observationCount)
        assertEquals(0, snapshot.factualContextSnapshot.activity!!.observationCount)
    }

    @Test
    fun `TEST 5, 6 - returns failure when factual assembly fails, no continued assembly`() = runTest {
        val now = 1700000000000L
        val expectedStartTimeMs = now - 86_400_000L
        val userId = "user-1"
        val expectedError = Exception("Factual aggregation failed")

        coEvery { buildFactualContextSnapshotUseCase(userId, expectedStartTimeMs, now) } returns Result.Failure(expectedError)

        val result = engine.getCurrentContext(userId)

        // TEST 5: Failure propagation
        assertTrue(result is Result.Failure)
        assertEquals(expectedError, (result as Result.Failure).error)

        coVerify(exactly = 1) { buildFactualContextSnapshotUseCase(userId, expectedStartTimeMs, now) }

        // TEST 6: No continued semantic assembly
        assertEquals(0, reflectionRepository.getReflectionsForDateRangeCallCount)
    }

    @Test
    fun `TEST 7 - Existing context preservation`() = runTest {
        val now = 1700000000000L
        val expectedStartTimeMs = now - 86_400_000L
        val userId = "user-1"

        // Set up non-empty fixtures
        val activeSession = mockk<ProtectionSession>()
        val commitment = mockk<Commitment> {
            io.mockk.every { status } returns CommitmentStatus.COMMITTED
        }
        val goal = mockk<Goal> {
            io.mockk.every { status } returns com.sanket_satpute_20.ironmind.domain.model.GoalStatus.ACTIVE
        }
        val event = mockk<Event> {
            io.mockk.every { occurredAt } returns now
        }
        val reflection = mockk<Reflection> {
            io.mockk.every { createdAt } returns now
        }
        val pattern = com.sanket_satpute_20.ironmind.domain.model.pattern.Pattern(
            id = "pat-1",
            userId = userId,
            fingerprint = "fp-1",
            type = com.sanket_satpute_20.ironmind.domain.model.pattern.PatternType.TIME_PATTERN,
            description = "Test pattern",
            conditions = null,
            predictedBehavior = null,
            confidence = 0.8f,
            evidenceCount = 1,
            evidenceReferences = null,
            firstObservedAt = now,
            lastObservedAt = now,
            status = PatternStatus.ACTIVE,
            confirmationState = MemoryConfirmationState.USER_CONFIRMED,
            createdAt = now,
            updatedAt = now
        )

        commitmentRepository.commitments = listOf(commitment)
        eventRepository.events = listOf(event)
        reflectionRepository.reflections = listOf(reflection)
        goalRepository.goals = listOf(goal)
        patternRepository.patterns = listOf(pattern)
        protectionRepository.activeSession = activeSession

        val emptySnapshot = FactualContextSnapshot(
            userId = userId,
            startTimeMs = expectedStartTimeMs,
            endTimeMs = now,
            appUsage = AppUsageObservationAggregation(userId, expectedStartTimeMs, now, 0, 0L, 0),
            activity = ActivityObservationAggregation(userId, expectedStartTimeMs, now, 0, 0, emptyMap(), null, null),
            calendar = CalendarObservationAggregation(userId, expectedStartTimeMs, now, 0, 0, 0L, null, null),
            location = LocationObservationAggregation(userId, expectedStartTimeMs, now, 0, 0, 0, null, null),
            notifications = NotificationObservationAggregation(userId, expectedStartTimeMs, now, 0, 0, null, null)
        )

        coEvery { buildFactualContextSnapshotUseCase(userId, expectedStartTimeMs, now) } returns Result.Success(emptySnapshot)

        val result = engine.getCurrentContext(userId)
        assertTrue(result is Result.Success)
        val snapshot = (result as Result.Success).data

        // Verify fixtures are preserved
        assertEquals(1, snapshot.activeCommitments.size)
        assertEquals(commitment, snapshot.activeCommitments.first())
        assertEquals(1, snapshot.recentEvents.size)
        assertEquals(1, snapshot.recentReflections.size)
        assertEquals(1, snapshot.activeGoals.size)
        assertEquals(1, snapshot.recentPatterns.size)
        assertEquals(activeSession, snapshot.activeProtectionSession)
    }

    @Test
    fun `TEST 8 - Deterministic evaluation time`() = runTest {
        val now = 1700000000000L
        val expectedStartTimeMs = now - 86_400_000L
        val userId = "user-1"

        val emptySnapshot = FactualContextSnapshot(
            userId = userId,
            startTimeMs = expectedStartTimeMs,
            endTimeMs = now,
            appUsage = AppUsageObservationAggregation(userId, expectedStartTimeMs, now, 0, 0L, 0),
            activity = ActivityObservationAggregation(userId, expectedStartTimeMs, now, 0, 0, emptyMap(), null, null),
            calendar = CalendarObservationAggregation(userId, expectedStartTimeMs, now, 0, 0, 0L, null, null),
            location = LocationObservationAggregation(userId, expectedStartTimeMs, now, 0, 0, 0, null, null),
            notifications = NotificationObservationAggregation(userId, expectedStartTimeMs, now, 0, 0, null, null)
        )

        coEvery { buildFactualContextSnapshotUseCase(userId, expectedStartTimeMs, now) } returns Result.Success(emptySnapshot)

        engine.getCurrentContext(userId)
        engine.getCurrentContext(userId)

        coVerify(exactly = 2) { buildFactualContextSnapshotUseCase(userId, expectedStartTimeMs, now) }
    }

    @Test
    fun `active commitments filter includes actionable commitments and excludes terminal or inactive ones`() = runTest {
        val now = 1700000000000L
        val expectedStartTimeMs = now - 86_400_000L
        val userId = "user-active-filter"

        val planned = mockk<Commitment> { io.mockk.every { status } returns CommitmentStatus.PLANNED }
        val committed = mockk<Commitment> { io.mockk.every { status } returns CommitmentStatus.COMMITTED }
        val started = mockk<Commitment> { io.mockk.every { status } returns CommitmentStatus.STARTED }
        val recovered = mockk<Commitment> { io.mockk.every { status } returns CommitmentStatus.RECOVERED }
        val completed = mockk<Commitment> { io.mockk.every { status } returns CommitmentStatus.COMPLETED }
        val postponed = mockk<Commitment> { io.mockk.every { status } returns CommitmentStatus.POSTPONED }
        val missed = mockk<Commitment> { io.mockk.every { status } returns CommitmentStatus.MISSED }
        val abandoned = mockk<Commitment> { io.mockk.every { status } returns CommitmentStatus.ABANDONED }

        commitmentRepository.commitments = listOf(
            planned, committed, started, recovered,
            completed, postponed, missed, abandoned
        )

        val emptySnapshot = FactualContextSnapshot(
            userId = userId,
            startTimeMs = expectedStartTimeMs,
            endTimeMs = now,
            appUsage = AppUsageObservationAggregation(userId, expectedStartTimeMs, now, 0, 0L, 0),
            activity = ActivityObservationAggregation(userId, expectedStartTimeMs, now, 0, 0, emptyMap(), null, null),
            calendar = CalendarObservationAggregation(userId, expectedStartTimeMs, now, 0, 0, 0L, null, null),
            location = LocationObservationAggregation(userId, expectedStartTimeMs, now, 0, 0, 0, null, null),
            notifications = NotificationObservationAggregation(userId, expectedStartTimeMs, now, 0, 0, null, null)
        )
        coEvery { buildFactualContextSnapshotUseCase(userId, expectedStartTimeMs, now) } returns Result.Success(emptySnapshot)

        val result = engine.getCurrentContext(userId)
        assertTrue(result is Result.Success)
        val snapshot = (result as Result.Success).data

        // 1. Actionable commitments appear in snapshot
        assertEquals(4, snapshot.activeCommitments.size)
        assertTrue("PLANNED should be included", snapshot.activeCommitments.contains(planned))
        assertTrue("COMMITTED should be included", snapshot.activeCommitments.contains(committed))
        assertTrue("STARTED should be included", snapshot.activeCommitments.contains(started))
        assertTrue("RECOVERED should be included", snapshot.activeCommitments.contains(recovered))

        // 2, 3, 4, 5. Terminal and inactive statuses are excluded
        assertTrue("COMPLETED must be excluded", !snapshot.activeCommitments.contains(completed))
        assertTrue("POSTPONED must be excluded", !snapshot.activeCommitments.contains(postponed))
        assertTrue("MISSED must be excluded", !snapshot.activeCommitments.contains(missed))
        assertTrue("ABANDONED must be excluded", !snapshot.activeCommitments.contains(abandoned))
    }

    @Test
    fun `active commitments filter handles empty commitment collection gracefully`() = runTest {
        val now = 1700000000000L
        val expectedStartTimeMs = now - 86_400_000L
        val userId = "user-empty-commitments"

        // 6. An empty commitment collection remains handled correctly
        commitmentRepository.commitments = emptyList()

        val emptySnapshot = FactualContextSnapshot(
            userId = userId,
            startTimeMs = expectedStartTimeMs,
            endTimeMs = now,
            appUsage = AppUsageObservationAggregation(userId, expectedStartTimeMs, now, 0, 0L, 0),
            activity = ActivityObservationAggregation(userId, expectedStartTimeMs, now, 0, 0, emptyMap(), null, null),
            calendar = CalendarObservationAggregation(userId, expectedStartTimeMs, now, 0, 0, 0L, null, null),
            location = LocationObservationAggregation(userId, expectedStartTimeMs, now, 0, 0, 0, null, null),
            notifications = NotificationObservationAggregation(userId, expectedStartTimeMs, now, 0, 0, null, null)
        )
        coEvery { buildFactualContextSnapshotUseCase(userId, expectedStartTimeMs, now) } returns Result.Success(emptySnapshot)

        val result = engine.getCurrentContext(userId)
        assertTrue(result is Result.Success)
        val snapshot = (result as Result.Success).data

        assertTrue("Empty commitments should produce empty activeCommitments list", snapshot.activeCommitments.isEmpty())
    }

    @Test
    fun `active commitments filter handles commitment repository failure gracefully`() = runTest {
        val now = 1700000000000L
        val expectedStartTimeMs = now - 86_400_000L
        val userId = "user-repo-failure"

        commitmentRepository.getCommitmentsForUserResult = Result.Failure(Exception("DB query timeout"))

        val emptySnapshot = FactualContextSnapshot(
            userId = userId,
            startTimeMs = expectedStartTimeMs,
            endTimeMs = now,
            appUsage = AppUsageObservationAggregation(userId, expectedStartTimeMs, now, 0, 0L, 0),
            activity = ActivityObservationAggregation(userId, expectedStartTimeMs, now, 0, 0, emptyMap(), null, null),
            calendar = CalendarObservationAggregation(userId, expectedStartTimeMs, now, 0, 0, 0L, null, null),
            location = LocationObservationAggregation(userId, expectedStartTimeMs, now, 0, 0, 0, null, null),
            notifications = NotificationObservationAggregation(userId, expectedStartTimeMs, now, 0, 0, null, null)
        )
        coEvery { buildFactualContextSnapshotUseCase(userId, expectedStartTimeMs, now) } returns Result.Success(emptySnapshot)

        val result = engine.getCurrentContext(userId)
        assertTrue(result is Result.Success)
        val snapshot = (result as Result.Success).data

        assertTrue("Repository failure should result in empty activeCommitments list without crashing", snapshot.activeCommitments.isEmpty())
    }

    // =========================================================================
    // Phase 3.6.6.3: Unified Digital Behaviour & Observation Ingestion Tests
    // =========================================================================

    private class FakeTestAppUsageProvider(var permissionGranted: Boolean = true) : AppUsageObservationProvider {
        override fun isPermissionGranted(): Boolean = permissionGranted
        override fun getAppUsageSince(fromTimeMillis: Long): List<AppUsageEvent> = emptyList()
    }

    private class FakeTestAppUsageSettingsRepo(var isEnabled: Boolean = true) : AppUsageObservationSettingsRepository {
        override suspend fun getSettings(userId: String): Result<AppUsageObservationSettings, Exception> =
            Result.Success(AppUsageObservationSettings(userId, isEnabled = isEnabled))
        override suspend fun setEnabled(userId: String, isEnabled: Boolean): Result<Unit, Exception> {
            this.isEnabled = isEnabled
            return Result.Success(Unit)
        }
    }

    @Test
    fun `getUnifiedContext consumes APP_USAGE_SESSION observations from observationRepository when present`() = runTest {
        val now = 1700000000000L
        val windowStart = now - 86_400_000L
        val userId = "user-unified-obs"

        val obsRepo = FakeObservationRepository()
        val appUsageProvider = FakeTestAppUsageProvider(permissionGranted = true)
        val appUsageSettingsRepo = FakeTestAppUsageSettingsRepo(isEnabled = true)

        val obs1 = Observation(
            id = "obs-1",
            userId = userId,
            type = ObservationType.APP_USAGE_SESSION,
            source = ObservationSource.APP_USAGE,
            occurredAt = windowStart + 10_000L,
            recordedAt = now,
            subjectId = null,
            value = "com.google.android.apps.translate",
            context = "duration_ms=120000",
            confidence = null,
            provenance = ObservationProvenance(ObservationSource.APP_USAGE, "UsageStatsManager", now)
        )
        val obs2 = Observation(
            id = "obs-2",
            userId = userId,
            type = ObservationType.APP_USAGE_SESSION,
            source = ObservationSource.APP_USAGE,
            occurredAt = windowStart + 200_000L,
            recordedAt = now,
            subjectId = null,
            value = "com.google.android.apps.translate",
            context = "duration_ms=60000",
            confidence = null,
            provenance = ObservationProvenance(ObservationSource.APP_USAGE, "UsageStatsManager", now)
        )
        val obs3 = Observation(
            id = "obs-3",
            userId = userId,
            type = ObservationType.APP_USAGE_SESSION,
            source = ObservationSource.APP_USAGE,
            occurredAt = windowStart + 400_000L,
            recordedAt = now,
            subjectId = null,
            value = "com.android.settings",
            context = "duration_ms=30000",
            confidence = null,
            provenance = ObservationProvenance(ObservationSource.APP_USAGE, "UsageStatsManager", now)
        )

        obsRepo.insertObservation(obs1)
        obsRepo.insertObservation(obs2)
        obsRepo.insertObservation(obs3)

        val customEngine = ContextEngineImpl(
            clock = clock,
            commitmentRepository = commitmentRepository,
            eventRepository = eventRepository,
            buildFactualContextSnapshotUseCase = buildFactualContextSnapshotUseCase,
            reflectionRepository = reflectionRepository,
            protectionRepository = protectionRepository,
            goalRepository = goalRepository,
            patternRepository = patternRepository,
            appUsageObservationProvider = appUsageProvider,
            appUsageSettingsRepository = appUsageSettingsRepo,
            observationRepository = obsRepo
        )

        val result = customEngine.getUnifiedContext(userId, windowStart, now)
        assertTrue("getUnifiedContext should succeed", result is Result.Success)
        val unified = (result as Result.Success).data
        val digitalContext = unified.digitalBehaviour

        assertEquals(UsagePermissionStatus.GRANTED, digitalContext.permissionStatus)
        assertEquals(SourceAvailability.AVAILABLE, digitalContext.availability)
        assertEquals(2, digitalContext.appUsages.size)

        // Deterministic ordering: highest duration first
        val topApp = digitalContext.appUsages[0]
        assertEquals("com.google.android.apps.translate", topApp.packageName)
        assertEquals(180_000L, topApp.totalDurationMs)
        assertEquals(2, topApp.sessionCount)
        assertEquals(windowStart + 10_000L, topApp.firstObservedAt)
        assertEquals(windowStart + 260_000L, topApp.lastObservedAt)
        assertEquals(EpistemicAuthority.SYSTEM_RECORDED_OBSERVATION, topApp.authority)

        val secondApp = digitalContext.appUsages[1]
        assertEquals("com.android.settings", secondApp.packageName)
        assertEquals(30_000L, secondApp.totalDurationMs)
        assertEquals(1, secondApp.sessionCount)
        assertEquals(EpistemicAuthority.SYSTEM_RECORDED_OBSERVATION, secondApp.authority)

        assertEquals("Room_Observation", unified.provenanceMap["AppUsage"]?.sourceName)
    }

    @Test
    fun `getUnifiedContext returns EMPTY_NO_DATA when permission granted but no observations exist`() = runTest {
        val now = 1700000000000L
        val windowStart = now - 86_400_000L
        val userId = "user-unified-empty"

        val obsRepo = FakeObservationRepository()
        val appUsageProvider = FakeTestAppUsageProvider(permissionGranted = true)
        val appUsageSettingsRepo = FakeTestAppUsageSettingsRepo(isEnabled = true)

        val customEngine = ContextEngineImpl(
            clock = clock,
            commitmentRepository = commitmentRepository,
            eventRepository = eventRepository,
            buildFactualContextSnapshotUseCase = buildFactualContextSnapshotUseCase,
            reflectionRepository = reflectionRepository,
            protectionRepository = protectionRepository,
            goalRepository = goalRepository,
            patternRepository = patternRepository,
            appUsageObservationProvider = appUsageProvider,
            appUsageSettingsRepository = appUsageSettingsRepo,
            observationRepository = obsRepo
        )

        val result = customEngine.getUnifiedContext(userId, windowStart, now)
        assertTrue(result is Result.Success)
        val unified = (result as Result.Success).data
        val digitalContext = unified.digitalBehaviour

        assertEquals(UsagePermissionStatus.GRANTED, digitalContext.permissionStatus)
        assertEquals(SourceAvailability.EMPTY_NO_DATA, digitalContext.availability)
        assertTrue("appUsages should be empty when no observations exist", digitalContext.appUsages.isEmpty())
    }

    @Test
    fun `getUnifiedContext returns PERMISSION_DENIED and never false zero when permission is revoked`() = runTest {
        val now = 1700000000000L
        val windowStart = now - 86_400_000L
        val userId = "user-unified-denied"

        val obsRepo = FakeObservationRepository()
        val appUsageProvider = FakeTestAppUsageProvider(permissionGranted = false)
        val appUsageSettingsRepo = FakeTestAppUsageSettingsRepo(isEnabled = true)

        val customEngine = ContextEngineImpl(
            clock = clock,
            commitmentRepository = commitmentRepository,
            eventRepository = eventRepository,
            buildFactualContextSnapshotUseCase = buildFactualContextSnapshotUseCase,
            reflectionRepository = reflectionRepository,
            protectionRepository = protectionRepository,
            goalRepository = goalRepository,
            patternRepository = patternRepository,
            appUsageObservationProvider = appUsageProvider,
            appUsageSettingsRepository = appUsageSettingsRepo,
            observationRepository = obsRepo
        )

        val result = customEngine.getUnifiedContext(userId, windowStart, now)
        assertTrue(result is Result.Success)
        val unified = (result as Result.Success).data
        val digitalContext = unified.digitalBehaviour

        assertEquals(UsagePermissionStatus.DENIED, digitalContext.permissionStatus)
        assertEquals(SourceAvailability.PERMISSION_DENIED, digitalContext.availability)
        assertTrue("appUsages must be empty on permission denied", digitalContext.appUsages.isEmpty())
    }
}
