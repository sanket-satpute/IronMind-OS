package com.sanket_satpute_20.ironmind.domain.usecase.reflection

import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.engine.ContextEngine
import com.sanket_satpute_20.ironmind.domain.model.Commitment
import com.sanket_satpute_20.ironmind.domain.model.CommitmentStatus
import com.sanket_satpute_20.ironmind.domain.model.EntitySource
import com.sanket_satpute_20.ironmind.domain.model.context.*
import com.sanket_satpute_20.ironmind.domain.usecase.commitment.GetCommitmentsForDateRangeUseCase
import com.sanket_satpute_20.ironmind.testutil.fake.FakeClock
import com.sanket_satpute_20.ironmind.testutil.fake.FakeCommitmentRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class GetDailyReflectionContextUseCaseTest {

    private lateinit var commitmentRepository: FakeCommitmentRepository
    private lateinit var getCommitmentsForDateRangeUseCase: GetCommitmentsForDateRangeUseCase
    private lateinit var clock: FakeClock
    private val userId = "user-test-1"

    @Before
    fun setup() {
        commitmentRepository = FakeCommitmentRepository()
        getCommitmentsForDateRangeUseCase = GetCommitmentsForDateRangeUseCase(commitmentRepository)
        clock = FakeClock()
        clock.setTime(1700000000000L)
    }

    private suspend fun createCommitment(id: String, status: CommitmentStatus, uid: String = userId, timeMs: Long = clock.currentTimeMillis()) {
        val commitment = Commitment(
            id = id,
            userId = uid,
            title = "Commitment $id",
            description = "Description $id",
            priority = 1,
            source = EntitySource.USER,
            status = status,
            committedAt = timeMs,
            createdAt = timeMs,
            updatedAt = timeMs
        )
        commitmentRepository.saveCommitment(commitment)
    }

    @Test
    fun `successful aggregation returns commitments, digital behaviour and execution timing`() = runTest {
        val startMs = 1700000000000L
        val endMs = startMs + 86_400_000L

        createCommitment("c1", CommitmentStatus.COMPLETED, timeMs = startMs + 1000)
        createCommitment("c2", CommitmentStatus.MISSED, timeMs = startMs + 2000)
        createCommitment("c3", CommitmentStatus.POSTPONED, timeMs = startMs + 3000)

        val fakeContextEngine = object : ContextEngine {
            override suspend fun getCurrentContext(userId: String): Result<ContextSnapshot, Exception> =
                Result.Failure(UnsupportedOperationException())

            override suspend fun getUnifiedContext(
                userId: String,
                windowStartTimeMs: Long?,
                windowEndTimeMs: Long?
            ): Result<UnifiedContext, Exception> {
                val appSummary = AppUsageSummary(
                    packageName = "com.example.reader",
                    appName = "Book Reader",
                    totalDurationMs = 3600_000L,
                    sessionCount = 3,
                    firstObservedAt = startMs,
                    lastObservedAt = endMs
                )
                val temporalSummary = AppUsageTemporalSummary(
                    hasUsageData = true,
                    totalObservedDurationMs = 5400_000L,
                    focusOverlap = FocusProtectionOverlapSummary(
                        availability = FocusOverlapAvailability.AVAILABLE,
                        overlappingUsageDurationMs = 600_000L,
                        protectionSessionCount = 2
                    )
                )
                val digital = DigitalBehaviourContext(
                    permissionStatus = UsagePermissionStatus.GRANTED,
                    availability = SourceAvailability.AVAILABLE,
                    appUsages = listOf(appSummary),
                    usageTemporalSummary = temporalSummary
                )

                val timing = ExecutionTimingSummary(
                    hasSufficientData = true,
                    startDelaySamples = listOf(
                        TaskStartDelaySample("t1", startMs, startMs + 300_000L, 300_000L, "e1")
                    ),
                    averageStartDelayMs = 300_000L,
                    completionDelaySamples = listOf(
                        TaskCompletionDelaySample("t1", endMs, endMs, 0L, TemporalSampleSource.EVENT_DERIVED)
                    ),
                    forwardPostponementCount = 1,
                    totalExecutionTimeDistribution = mapOf(ExecutionTimeBucket.MORNING to 2)
                )

                return Result.Success(
                    UnifiedContext(
                        userId = userId,
                        generatedAt = clock.currentTimeMillis(),
                        windowStartTimeMs = windowStartTimeMs ?: startMs,
                        windowEndTimeMs = windowEndTimeMs ?: endMs,
                        productivity = ProductivityContext(executionTiming = timing),
                        digitalBehaviour = digital
                    )
                )
            }
        }

        val useCase = GetDailyReflectionContextUseCase(
            getCommitmentsForDateRangeUseCase = getCommitmentsForDateRangeUseCase,
            contextEngine = fakeContextEngine,
            clock = clock
        )

        val result = useCase(userId, startMs, endMs)
        assertTrue(result is Result.Success)
        val summary = (result as Result.Success).data

        assertEquals(1, summary.completed.size)
        assertEquals("c1", summary.completed[0].id)
        assertEquals(1, summary.missed.size)
        assertEquals("c2", summary.missed[0].id)
        assertEquals(1, summary.postponed.size)
        assertEquals("c3", summary.postponed[0].id)

        // Verify digital behaviour alignment
        val digital = summary.digitalBehaviour
        assertNotNull(digital)
        assertTrue(digital!!.isUsagePermissionGranted)
        assertEquals(5400_000L, digital.totalObservedUsageDurationMs)
        assertEquals("com.example.reader", digital.topUsedAppPackage)
        assertEquals("Book Reader", digital.topUsedAppName)
        assertEquals(3600_000L, digital.topUsedAppDurationMs)
        assertEquals(600_000L, digital.focusProtectionOverlapMs)
        assertEquals(2, digital.focusSessionCount)
        assertEquals(EpistemicAuthority.SYSTEM_RECORDED_OBSERVATION, digital.authority)

        // Verify execution timing alignment
        val timing = summary.executionTiming
        assertNotNull(timing)
        assertTrue(timing!!.hasSufficientData)
        assertEquals(1, timing.startedTaskCount)
        assertEquals(1, timing.completedTaskCount)
        assertEquals(1, timing.postponedCount)
        assertEquals(300_000L, timing.averageStartDelayMs)
        assertEquals(ExecutionTimeBucket.MORNING, timing.mostActiveBucket)
        assertEquals(EpistemicAuthority.DERIVED_CALCULATION, timing.authority)
    }

    @Test
    fun `missing usage permission leaves total duration null and does not claim zero usage`() = runTest {
        val startMs = 1700000000000L
        val endMs = startMs + 86_400_000L

        val fakeContextEngine = object : ContextEngine {
            override suspend fun getCurrentContext(userId: String): Result<ContextSnapshot, Exception> =
                Result.Failure(UnsupportedOperationException())

            override suspend fun getUnifiedContext(
                userId: String,
                windowStartTimeMs: Long?,
                windowEndTimeMs: Long?
            ): Result<UnifiedContext, Exception> {
                val digital = DigitalBehaviourContext(
                    permissionStatus = UsagePermissionStatus.DENIED,
                    availability = SourceAvailability.PERMISSION_DENIED,
                    notes = "Usage permission denied"
                )
                return Result.Success(
                    UnifiedContext(
                        userId = userId,
                        generatedAt = clock.currentTimeMillis(),
                        productivity = ProductivityContext(),
                        digitalBehaviour = digital
                    )
                )
            }
        }

        val useCase = GetDailyReflectionContextUseCase(
            getCommitmentsForDateRangeUseCase = getCommitmentsForDateRangeUseCase,
            contextEngine = fakeContextEngine,
            clock = clock
        )

        val result = useCase(userId, startMs, endMs)
        assertTrue(result is Result.Success)
        val summary = (result as Result.Success).data
        val digital = summary.digitalBehaviour

        assertNotNull(digital)
        assertFalse(digital!!.isUsagePermissionGranted)
        assertEquals(SourceAvailability.PERMISSION_DENIED, digital.availability)
        assertNull(digital.totalObservedUsageDurationMs)
        assertNull(digital.topUsedAppPackage)
        assertNull(digital.focusProtectionOverlapMs)
    }

    @Test
    fun `context engine failure degrades gracefully without crashing reflection`() = runTest {
        val startMs = 1700000000000L
        val endMs = startMs + 86_400_000L

        createCommitment("c1", CommitmentStatus.COMPLETED, timeMs = startMs + 500)

        val failingContextEngine = object : ContextEngine {
            override suspend fun getCurrentContext(userId: String): Result<ContextSnapshot, Exception> =
                Result.Failure(UnsupportedOperationException())

            override suspend fun getUnifiedContext(
                userId: String,
                windowStartTimeMs: Long?,
                windowEndTimeMs: Long?
            ): Result<UnifiedContext, Exception> =
                Result.Failure(IllegalStateException("Database timeout"))
        }

        val useCase = GetDailyReflectionContextUseCase(
            getCommitmentsForDateRangeUseCase = getCommitmentsForDateRangeUseCase,
            contextEngine = failingContextEngine,
            clock = clock
        )

        val result = useCase(userId, startMs, endMs)
        assertTrue(result is Result.Success)
        val summary = (result as Result.Success).data

        assertEquals(1, summary.completed.size)
        assertNull(summary.digitalBehaviour)
        assertNull(summary.executionTiming)
    }

    @Test
    fun `validation rejects blank userId and invalid time ranges`() = runTest {
        val useCase = GetDailyReflectionContextUseCase(
            getCommitmentsForDateRangeUseCase = getCommitmentsForDateRangeUseCase,
            contextEngine = null,
            clock = clock
        )

        val blankResult = useCase("   ", 1000L, 2000L)
        assertTrue(blankResult is Result.Failure)
        assertTrue((blankResult as Result.Failure).error is IllegalArgumentException)

        val invalidRangeResult = useCase(userId, 2000L, 1000L)
        assertTrue(invalidRangeResult is Result.Failure)
        assertTrue((invalidRangeResult as Result.Failure).error is IllegalArgumentException)

        val equalRangeResult = useCase(userId, 1000L, 1000L)
        assertTrue(equalRangeResult is Result.Failure)
        assertTrue((equalRangeResult as Result.Failure).error is IllegalArgumentException)
    }

    @Test
    fun `cross-user isolation enforces security check`() = runTest {
        val startMs = 1700000000000L
        val endMs = startMs + 86_400_000L

        // Commitment belonging to another user
        createCommitment("c-other", CommitmentStatus.COMPLETED, uid = "other-user", timeMs = startMs + 100)

        val useCase = GetDailyReflectionContextUseCase(
            getCommitmentsForDateRangeUseCase = getCommitmentsForDateRangeUseCase,
            contextEngine = null,
            clock = clock
        )

        // Querying for other-user returns success for that user
        val otherResult = useCase("other-user", startMs, endMs)
        assertTrue(otherResult is Result.Success)
        assertEquals(1, (otherResult as Result.Success).data.completed.size)

        // Querying for userId returns 0 commitments
        val userResult = useCase(userId, startMs, endMs)
        assertTrue(userResult is Result.Success)
        assertEquals(0, (userResult as Result.Success).data.completed.size)
    }
}
