package com.sanket_satpute_20.ironmind.domain.engine

import com.sanket_satpute_20.ironmind.domain.ai.BarrierCategory
import com.sanket_satpute_20.ironmind.domain.common.IdGenerator
import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.EntitySource
import com.sanket_satpute_20.ironmind.domain.model.Event
import com.sanket_satpute_20.ironmind.domain.model.EventType
import com.sanket_satpute_20.ironmind.domain.model.Goal
import com.sanket_satpute_20.ironmind.domain.model.ProtectionSession
import com.sanket_satpute_20.ironmind.domain.model.ProtectionSessionStatus
import com.sanket_satpute_20.ironmind.domain.model.Task
import com.sanket_satpute_20.ironmind.domain.model.TaskStatus
import com.sanket_satpute_20.ironmind.domain.model.context.AppUsageSummary
import com.sanket_satpute_20.ironmind.domain.model.context.CrossDomainCorrelationType
import com.sanket_satpute_20.ironmind.domain.model.context.DigitalBehaviourContext
import com.sanket_satpute_20.ironmind.domain.model.context.EpistemicAuthority
import com.sanket_satpute_20.ironmind.domain.model.context.FocusActivitySummary
import com.sanket_satpute_20.ironmind.domain.model.context.ProductivityContext
import com.sanket_satpute_20.ironmind.domain.model.context.SourceAvailability
import com.sanket_satpute_20.ironmind.domain.model.context.SourceProvenanceAndFreshness
import com.sanket_satpute_20.ironmind.domain.model.context.TaskContextSummary
import com.sanket_satpute_20.ironmind.domain.model.context.UnifiedContext
import com.sanket_satpute_20.ironmind.domain.model.context.UsagePermissionStatus
import com.sanket_satpute_20.ironmind.domain.model.observation.FactualContextSnapshot
import com.sanket_satpute_20.ironmind.domain.model.pattern.EvidenceReference
import com.sanket_satpute_20.ironmind.domain.model.pattern.EvidenceSourceType
import com.sanket_satpute_20.ironmind.domain.model.pattern.Pattern
import com.sanket_satpute_20.ironmind.domain.model.pattern.PatternCategory
import com.sanket_satpute_20.ironmind.domain.model.pattern.PatternDiscoveryType
import com.sanket_satpute_20.ironmind.domain.model.pattern.PatternStatus
import com.sanket_satpute_20.ironmind.domain.model.pattern.PatternType
import com.sanket_satpute_20.ironmind.domain.model.MemoryConfirmationState
import com.sanket_satpute_20.ironmind.domain.repository.ActionRepository
import com.sanket_satpute_20.ironmind.domain.repository.BarrierRepository
import com.sanket_satpute_20.ironmind.domain.repository.CommitmentRepository
import com.sanket_satpute_20.ironmind.domain.repository.EventRepository
import com.sanket_satpute_20.ironmind.domain.repository.GoalRepository
import com.sanket_satpute_20.ironmind.domain.repository.InsightRepository
import com.sanket_satpute_20.ironmind.domain.repository.InterventionRecommendationRepository
import com.sanket_satpute_20.ironmind.domain.repository.OutcomeRepository
import com.sanket_satpute_20.ironmind.domain.repository.PatternRepository
import com.sanket_satpute_20.ironmind.domain.repository.PlanRepository
import com.sanket_satpute_20.ironmind.domain.repository.ProtectionRepository
import com.sanket_satpute_20.ironmind.domain.repository.ReflectionRepository
import com.sanket_satpute_20.ironmind.domain.repository.TaskRepository
import com.sanket_satpute_20.ironmind.domain.usecase.briefing.AssembleDailyBriefingUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.observation.BuildFactualContextSnapshotUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.task.GetDueTasksUseCase
import com.sanket_satpute_20.ironmind.testutil.fake.FakeClock
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class ContextEngineCorrectnessRemediationTest {

    private lateinit var clock: FakeClock
    private lateinit var commitmentRepository: CommitmentRepository
    private lateinit var eventRepository: EventRepository
    private lateinit var buildFactualContextSnapshotUseCase: BuildFactualContextSnapshotUseCase
    private lateinit var reflectionRepository: ReflectionRepository
    private lateinit var protectionRepository: ProtectionRepository
    private lateinit var goalRepository: GoalRepository
    private lateinit var patternRepository: PatternRepository
    private lateinit var planRepository: PlanRepository
    private lateinit var taskRepository: TaskRepository

    private val userId = "user-3678a"

    @Before
    fun setUp() {
        clock = FakeClock(ZonedDateTime.of(2026, 3, 15, 19, 30, 0, 0, ZoneId.of("UTC")).toInstant().toEpochMilli())
        commitmentRepository = mockk(relaxed = true)
        eventRepository = mockk(relaxed = true)
        buildFactualContextSnapshotUseCase = mockk(relaxed = true)
        reflectionRepository = mockk(relaxed = true)
        protectionRepository = mockk(relaxed = true)
        goalRepository = mockk(relaxed = true)
        patternRepository = mockk(relaxed = true)
        planRepository = mockk(relaxed = true)
        taskRepository = mockk(relaxed = true)

        coEvery { commitmentRepository.getCommitmentsForUser(any()) } returns Result.Success(emptyList())
        coEvery { eventRepository.getEventsForTimeWindow(any(), any(), any(), any(), any(), any()) } returns Result.Success(emptyList())
        coEvery { buildFactualContextSnapshotUseCase(any(), any(), any()) } returns Result.Success(mockk<FactualContextSnapshot>(relaxed = true))
        coEvery { reflectionRepository.getReflectionsForDateRange(any(), any(), any()) } returns Result.Success(emptyList())
        coEvery { protectionRepository.getProtectionSessionsForUser(any()) } returns Result.Success(emptyList())
        coEvery { protectionRepository.getActiveProtectionSessionsForUser(any()) } returns Result.Success(emptyList())
        coEvery { protectionRepository.getProtectionRulesForUser(any()) } returns Result.Success(emptyList())
        coEvery { goalRepository.getGoalsForUser(any()) } returns Result.Success(emptyList())
        coEvery { patternRepository.getPatternsForUser(any()) } returns Result.Success(emptyList())
        coEvery { planRepository.getPlansForUser(any()) } returns Result.Success(emptyList())
        coEvery { taskRepository.getTasksForUser(any()) } returns Result.Success(emptyList())
    }

    private fun createEngine(
        plansRepo: PlanRepository? = planRepository,
        tasksRepo: TaskRepository? = taskRepository,
        getDueTasks: GetDueTasksUseCase? = null,
        zoneId: ZoneId = ZoneId.of("UTC")
    ) = ContextEngineImpl(
        clock = clock,
        commitmentRepository = commitmentRepository,
        eventRepository = eventRepository,
        buildFactualContextSnapshotUseCase = buildFactualContextSnapshotUseCase,
        reflectionRepository = reflectionRepository,
        protectionRepository = protectionRepository,
        goalRepository = goalRepository,
        patternRepository = patternRepository,
        planRepository = plansRepo,
        taskRepository = tasksRepo,
        getDueTasksUseCase = getDueTasks,
        zoneId = zoneId
    )

    @Test
    fun `F02 distinguishes core productivity repository failures and null repositories from genuine empty data`() = runBlocking {
        val now = clock.currentTimeMillis()
        val windowStart = now - 86_400_000L

        // Goals succeed non-empty; Tasks, Commitments, Reflections fail; Plans nullable
        val goal = Goal(
            id = "g1", userId = userId, title = "Goal", description = "", why = "Focus",
            importance = 2, status = com.sanket_satpute_20.ironmind.domain.model.GoalStatus.ACTIVE,
            createdAt = now - 1000L, updatedAt = now - 500L
        )
        coEvery { goalRepository.getGoalsForUser(userId) } returns Result.Success(listOf(goal))
        coEvery { taskRepository.getTasksForUser(userId) } returns Result.Failure(RuntimeException("Tasks SQL error"))
        coEvery { commitmentRepository.getCommitmentsForUser(userId) } returns Result.Failure(RuntimeException("Commitments SQL error"))
        coEvery { reflectionRepository.getReflectionsForDateRange(userId, windowStart, now) } returns Result.Failure(RuntimeException("Reflections SQL error"))

        val failedRes = createEngine(plansRepo = null).getUnifiedContext(userId, windowStart, now)
        assertTrue(failedRes is Result.Success)
        val failedCtx = (failedRes as Result.Success).data

        assertEquals(SourceAvailability.AVAILABLE, failedCtx.provenanceMap["Goals"]?.availability)
        assertEquals(SourceAvailability.UNAVAILABLE_HARDWARE, failedCtx.provenanceMap["Plans"]?.availability)
        assertTrue(failedCtx.provenanceMap["Plans"]?.notes?.contains("not configured", ignoreCase = true) == true)
        assertEquals(SourceAvailability.UNAVAILABLE_HARDWARE, failedCtx.provenanceMap["Tasks"]?.availability)
        assertEquals(SourceAvailability.UNAVAILABLE_HARDWARE, failedCtx.provenanceMap["Commitments"]?.availability)
        assertEquals(SourceAvailability.UNAVAILABLE_HARDWARE, failedCtx.provenanceMap["Reflections"]?.availability)
        // Unaffected valid Goals context and global freshness remain preserved
        assertEquals(1, failedCtx.productivity.activeGoals.size)
        assertTrue(failedCtx.isFresh)

        // When Goal fails while others are genuine empty Result.Success(emptyList())
        coEvery { goalRepository.getGoalsForUser(userId) } returns Result.Failure(RuntimeException("Goals SQL error"))
        coEvery { taskRepository.getTasksForUser(userId) } returns Result.Success(emptyList())
        coEvery { commitmentRepository.getCommitmentsForUser(userId) } returns Result.Success(emptyList())
        coEvery { reflectionRepository.getReflectionsForDateRange(userId, windowStart, now) } returns Result.Success(emptyList())

        val emptyVsFailedRes = createEngine().getUnifiedContext(userId, windowStart, now)
        val emptyVsFailedCtx = (emptyVsFailedRes as Result.Success).data
        assertEquals(SourceAvailability.UNAVAILABLE_HARDWARE, emptyVsFailedCtx.provenanceMap["Goals"]?.availability)
        assertEquals(SourceAvailability.EMPTY_NO_DATA, emptyVsFailedCtx.provenanceMap["Plans"]?.availability)
        assertEquals(SourceAvailability.EMPTY_NO_DATA, emptyVsFailedCtx.provenanceMap["Tasks"]?.availability)
        assertEquals(SourceAvailability.EMPTY_NO_DATA, emptyVsFailedCtx.provenanceMap["Commitments"]?.availability)
        assertEquals(SourceAvailability.EMPTY_NO_DATA, emptyVsFailedCtx.provenanceMap["Reflections"]?.availability)
    }

    @Test
    fun `F03 bounds expired ACTIVE protection sessions and only correlates tasks completed inside effective interval`() = runBlocking {
        val now = clock.currentTimeMillis()
        val windowStart = now - 7_200_000L // 2h ago

        // Session expired 1 hour ago in real time (scheduledEndAt = now - 3600s), still ACTIVE in DB before recovery
        val expiredActiveSession = ProtectionSession(
            id = "sess-exp",
            userId = userId,
            startedAt = now - 5_400_000L,        // T - 90m
            scheduledEndAt = now - 3_600_000L,   // T - 60m (expired!)
            endedAt = null,
            status = ProtectionSessionStatus.ACTIVE,
            source = EntitySource.USER,
            overrideAllowed = true,
            createdAt = now - 5_400_000L,
            updatedAt = now - 5_400_000L
        )
        // Completed task OUTSIDE session (after scheduled expiry, at T - 15m)
        val taskAfter = Task(
            id = "t-after", userId = userId, title = "After expiry", description = "",
            status = TaskStatus.COMPLETED, priority = 2,
            createdAt = windowStart, updatedAt = now - 900_000L, completedAt = now - 900_000L,
            source = EntitySource.USER
        )
        // Completed task BEFORE session (strictly outside even C.2/D.3 -15m tolerance: T - 110m)
        val taskBefore = taskAfter.copy(id = "t-before", title = "Before start", completedAt = now - 6_600_000L)
        // Legacy completed task missing completedAt -> must NOT correlate via updatedAt/status in Heuristic 2
        val taskNoCompletedAt = taskAfter.copy(id = "t-null", title = "Null completedAt", completedAt = null, updatedAt = now - 4_500_000L)

        coEvery { protectionRepository.getProtectionSessionsForUser(userId) } returns Result.Success(listOf(expiredActiveSession))
        coEvery { taskRepository.getTasksForUser(userId) } returns Result.Success(listOf(taskAfter, taskBefore, taskNoCompletedAt))

        val outsideRes = createEngine().getUnifiedContext(userId, windowStart, now)
        val outsideCtx = (outsideRes as Result.Success).data

        // Expired ACTIVE session must NOT be listed in activeSessions and must cap focus duration to 30m (not 90m)
        assertTrue(outsideCtx.digitalBehaviour.focusActivity.activeSessions.isEmpty())
        assertEquals(1_800_000L, outsideCtx.digitalBehaviour.focusActivity.totalFocusDurationMs)
        assertFalse(outsideCtx.correlations.any { it.correlationType == CrossDomainCorrelationType.FOCUS_PROTECTION_DURING_COMPLETED_TASK })

        // Task actually completed INSIDE [T-90m, T-60m] -> emits correlation!
        val taskInside = taskAfter.copy(id = "t-inside", title = "Completed inside session", completedAt = now - 4_200_000L)
        coEvery { taskRepository.getTasksForUser(userId) } returns Result.Success(listOf(taskAfter, taskBefore, taskInside))

        val insideCtx = (createEngine().getUnifiedContext(userId, windowStart, now) as Result.Success).data
        val focusCorr = insideCtx.correlations.firstOrNull { it.correlationType == CrossDomainCorrelationType.FOCUS_PROTECTION_DURING_COMPLETED_TASK }
        assertNotNull(focusCorr)
        assertEquals("t-inside", focusCorr!!.productivityEntityId)
        assertEquals(1_800_000L, focusCorr.digitalUsageDurationMs)

        // Pattern engine B.5 / C.2 / D.3 respect expired ACTIVE bound (usage & completion after expiry T-60m must NOT trigger!)
        val postExpiryPatternCtx = outsideCtx.copy(
            productivity = outsideCtx.productivity.copy(
                taskSummary = TaskContextSummary(completedTasks = listOf(taskAfter, taskBefore))
            ),
            digitalBehaviour = outsideCtx.digitalBehaviour.copy(
                permissionStatus = UsagePermissionStatus.GRANTED,
                availability = SourceAvailability.AVAILABLE,
                appUsages = listOf(AppUsageSummary("com.example.chat", totalDurationMs = 300_000L, sessionCount = 1, firstObservedAt = now - 900_000L, lastObservedAt = now - 600_000L))
            )
        )
        val patternEngine = BehaviourPatternDiscoveryEngine(clock = clock)
        val noTaskPatterns = patternEngine.discoverPatterns(postExpiryPatternCtx, zoneId = ZoneId.of("UTC"))
        assertNull(noTaskPatterns.find { it.type == PatternDiscoveryType.USAGE_DURING_SCHEDULED_FOCUS })
        assertNull(noTaskPatterns.find { it.type == PatternDiscoveryType.FOCUS_DURING_TASK_COMPLETION })
        assertNull(noTaskPatterns.find { it.type == PatternDiscoveryType.FOCUS_ACTIVITY_COINCIDING_WITH_TASK_COMPLETION })

        // Ongoing ACTIVE session with scheduledEndAt == null stays active up to now, and terminal OVERRIDDEN uses endedAt
        val openActive = expiredActiveSession.copy(id = "sess-open", startedAt = now - 1_200_000L, scheduledEndAt = null)
        val overriddenEarlier = ProtectionSession(
            id = "sess-ovr", userId = userId, startedAt = now - 5_400_000L, scheduledEndAt = now,
            endedAt = now - 4_800_000L, status = ProtectionSessionStatus.OVERRIDDEN,
            source = EntitySource.USER, overrideAllowed = true, createdAt = now - 5_400_000L, updatedAt = now - 4_800_000L
        )
        coEvery { protectionRepository.getProtectionSessionsForUser(userId) } returns Result.Success(listOf(openActive, overriddenEarlier))
        coEvery { taskRepository.getTasksForUser(userId) } returns Result.Success(listOf(taskAfter)) // completed at T-15m inside openActive [T-20m..T]

        val openActiveCtx = (createEngine().getUnifiedContext(userId, windowStart, now) as Result.Success).data
        assertEquals(listOf("sess-open"), openActiveCtx.digitalBehaviour.focusActivity.activeSessions.map { it.id })
        assertTrue(openActiveCtx.correlations.any { it.correlationType == CrossDomainCorrelationType.FOCUS_PROTECTION_DURING_COMPLETED_TASK })
    }

    @Test
    fun `F04 propagates injected profile ZoneId and referenceTime consistently across UnifiedContext DailyBriefing and CurrentContext`() = runBlocking {
        // Sunday 2026-03-15 19:30 UTC == Monday 2026-03-16 01:00 Asia/Kolkata!
        // A task due at 2026-03-15 22:00 UTC (2026-03-16 03:30 IST):
        // - In UTC: 2026-03-15 22:00 is DUE TODAY
        // - In Asia/Kolkata: 2026-03-16 03:30 is DUE TODAY (on Monday Mar 16)
        // A task due at 2026-03-15 17:00 UTC (2026-03-15 22:30 IST):
        // - In UTC: 2026-03-15 17:00 is DUE TODAY (Sunday Mar 15)
        // - In Asia/Kolkata: 2026-03-15 22:30 is OVERDUE (since local date is already Monday Mar 16!)
        val istZone = ZoneId.of("Asia/Kolkata")
        val utcZone = ZoneId.of("UTC")
        val dueAtBoundary = ZonedDateTime.of(2026, 3, 15, 17, 0, 0, 0, utcZone).toInstant().toEpochMilli()

        val task = Task(
            id = "t-tz", userId = userId, title = "Midnight boundary task", description = "",
            status = TaskStatus.PENDING, priority = 2, dueAt = dueAtBoundary,
            createdAt = dueAtBoundary - 10_000L, updatedAt = dueAtBoundary - 10_000L, source = EntitySource.USER
        )
        coEvery { taskRepository.getTasksForUser(userId) } returns Result.Success(listOf(task))

        val dueUseCase = GetDueTasksUseCase(taskRepository = taskRepository, clock = clock)
        val now = clock.currentTimeMillis()
        val windowStart = now - 86_400_000L

        val istContext = (createEngine(getDueTasks = dueUseCase, zoneId = istZone).getUnifiedContext(userId, windowStart, now) as Result.Success).data
        val utcContext = (createEngine(getDueTasks = dueUseCase, zoneId = utcZone).getUnifiedContext(userId, windowStart, now) as Result.Success).data

        assertEquals("In IST Monday 01:00, Sunday 22:30 IST task must be overdue", 1, istContext.productivity.taskSummary.overdueTasks.size)
        assertEquals(0, istContext.productivity.taskSummary.dueTodayTasks.size)
        assertEquals("In UTC Sunday 19:30, Sunday 17:00 UTC task must be dueToday", 0, utcContext.productivity.taskSummary.overdueTasks.size)
        assertEquals(1, utcContext.productivity.taskSummary.dueTodayTasks.size)

        // AssembleDailyBriefingUseCase must classify identically using the passed zoneId and clock reference time
        val orchestrator = mockk<PersonalIntelligenceOrchestrator>(relaxed = true)
        coEvery { orchestrator.orchestrate(userId, any(), any(), istZone) } returns Result.Success(
            PersonalIntelligenceResult(
                pipelineResult = PipelineExecutionResult(
                    unifiedContext = istContext,
                    discoveredPatterns = emptyList(),
                    activePatterns = emptyList(),
                    barrierHypotheses = emptyList(),
                    interventionRecommendations = emptyList(),
                    insufficientEvidenceBarriers = emptyList(),
                    diagnosticNotes = emptyList()
                ),
                newlyDiscoveredInsights = emptyList(),
                allActiveInsights = emptyList(),
                suppressedCount = 0,
                diagnosticNotes = emptyList()
            )
        )
        val briefingUseCase = AssembleDailyBriefingUseCase(
            dailyBriefingEngine = DailyBriefingEngine(ActionPlanningEngine(clock, object : IdGenerator { override fun generateId() = "a-1" }), clock),
            personalIntelligenceOrchestrator = orchestrator,
            getDueTasksUseCase = dueUseCase,
            clock = clock
        )
        val briefing = (briefingUseCase(userId, windowStartTimeMs = windowStart, windowEndTimeMs = now, zoneId = istZone) as Result.Success).data
        assertEquals(istContext.productivity.taskSummary.overdueTasks.map { it.task.id }, briefing.overdueTasks.map { it.task.id })
        assertEquals(istContext.productivity.taskSummary.dueTodayTasks.map { it.task.id }, briefing.dueTodayTasks.map { it.task.id })

        // ContextEngineImpl.getCurrentContext must use injected zoneId for dayOfWeek AND bounded getEventsForTimeWindow (not getEventsForUser!)
        val istSnap = (createEngine(zoneId = istZone).getCurrentContext(userId) as Result.Success).data
        val utcSnap = (createEngine(zoneId = utcZone).getCurrentContext(userId) as Result.Success).data
        assertEquals(2, istSnap.dayOfWeek) // Monday = Calendar.MONDAY (2) in IST
        assertEquals(1, utcSnap.dayOfWeek) // Sunday = Calendar.SUNDAY (1) in UTC
        coVerify(atLeast = 1) { eventRepository.getEventsForTimeWindow(userId, now - 86_400_000L, now + 1L, null, any(), false) }
        coVerify(exactly = 0) { eventRepository.getEventsForUser(any()) }
    }

    @Test
    fun `BarrierHypothesisDiscoveryEngine reads authoritative AppUsage provenance key for staleness`() {
        val now = clock.currentTimeMillis()
        val windowStart = now - 2 * 86_400_000L

        val pattern = Pattern(
            id = "pat-cross", userId = userId, fingerprint = "cross_usage",
            type = PatternType.CONTEXT_PATTERN, description = "App usage during work",
            conditions = "category=CROSS_DOMAIN", predictedBehavior = null,
            confidence = 0.85f, evidenceCount = 4,
            evidenceReferences = listOf(
                EvidenceReference("e1", EvidenceSourceType.OBSERVATION),
                EvidenceReference("e2", EvidenceSourceType.OBSERVATION),
                EvidenceReference("e3", EvidenceSourceType.OBSERVATION),
                EvidenceReference("e4", EvidenceSourceType.OBSERVATION)
            ),
            firstObservedAt = windowStart, lastObservedAt = now,
            status = PatternStatus.ACTIVE, confirmationState = MemoryConfirmationState.UNCONFIRMED,
            createdAt = windowStart, updatedAt = now
        )
        val staleAppUsageProvenance = SourceProvenanceAndFreshness(
            sourceName = "Room_Observation", availability = SourceAvailability.AVAILABLE,
            isPermissionGranted = true, lastRecordedAt = windowStart, dataAgeMs = 6 * 3600_000L, isStale = true
        )
        val ctx = UnifiedContext(
            userId = userId, generatedAt = now, windowStartTimeMs = windowStart, windowEndTimeMs = now,
            digitalBehaviour = DigitalBehaviourContext(
                permissionStatus = UsagePermissionStatus.GRANTED,
                availability = SourceAvailability.AVAILABLE,
                appUsages = listOf(AppUsageSummary("com.example.social", totalDurationMs = 900_000L, sessionCount = 3, firstObservedAt = windowStart, lastObservedAt = now))
            ),
            provenanceMap = mapOf("AppUsage" to staleAppUsageProvenance),
            isFresh = true
        )

        val engine = BarrierHypothesisDiscoveryEngine(EvidenceSufficiencyEvaluator(), object : IdGenerator { override fun generateId() = "bar-1" })
        val result = engine.discoverBarrierHypotheses(
            userId = userId,
            unifiedContext = ctx,
            patterns = listOf(pattern),
            existingBarriers = emptyList(),
            zoneId = ZoneId.of("UTC"),
            evaluationTimeMs = now
        )

        val distraction = result.discoveredHypotheses.firstOrNull { it.category == BarrierCategory.DISTRACTION }
        assertNotNull(distraction)
        // Baseline confidence 0.75f discounted by STALE_CONFIDENCE_DISCOUNT (0.20f) => <= 0.55f because AppUsage provenance is stale
        assertTrue("Stale AppUsage provenance must discount distraction confidence (was ${distraction!!.confidence})", distraction.confidence <= 0.56f)
    }
}
