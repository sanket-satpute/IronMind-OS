package com.sanket_satpute_20.ironmind.domain.engine

import com.sanket_satpute_20.ironmind.domain.common.Clock
import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.*
import com.sanket_satpute_20.ironmind.domain.model.barrier.BarrierConfirmationState
import com.sanket_satpute_20.ironmind.domain.model.barrier.BarrierHypothesis
import com.sanket_satpute_20.ironmind.domain.model.barrier.BarrierStatus
import com.sanket_satpute_20.ironmind.domain.model.context.*
import com.sanket_satpute_20.ironmind.domain.model.observation.Observation
import com.sanket_satpute_20.ironmind.domain.model.observation.ObservationProvenance
import com.sanket_satpute_20.ironmind.domain.model.observation.ObservationSource
import com.sanket_satpute_20.ironmind.domain.model.observation.ObservationType
import com.sanket_satpute_20.ironmind.domain.provider.AppUsageEvent
import com.sanket_satpute_20.ironmind.domain.provider.AppUsageObservationProvider
import com.sanket_satpute_20.ironmind.domain.repository.*
import com.sanket_satpute_20.ironmind.domain.usecase.goal.GetGoalProgressUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.observation.BuildFactualContextSnapshotUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.plan.GetPlanProgressUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.task.GetDueTasksUseCase
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import com.sanket_satpute_20.ironmind.testutil.fake.FakeObservationRepository

class UnifiedContextEngineTest {

    private lateinit var clock: TestClock
    private lateinit var goalRepository: TestGoalRepository
    private lateinit var planRepository: TestPlanRepository
    private lateinit var taskRepository: TestTaskRepository
    private lateinit var commitmentRepository: TestCommitmentRepository
    private lateinit var reflectionRepository: TestReflectionRepository
    private lateinit var outcomeRepository: TestOutcomeRepository
    private lateinit var eventRepository: TestEventRepository
    private lateinit var barrierRepository: TestBarrierRepository
    private lateinit var protectionRepository: TestProtectionRepository
    private lateinit var appUsageProvider: TestAppUsageProvider
    private lateinit var appUsageSettingsRepository: TestAppUsageSettingsRepository
    private lateinit var observationRepository: FakeObservationRepository
    private lateinit var checkpointStore: TestAppUsageCheckpointStore
    private lateinit var patternRepository: PatternRepository
    private lateinit var buildFactualContextSnapshotUseCase: BuildFactualContextSnapshotUseCase

    private lateinit var getGoalProgressUseCase: GetGoalProgressUseCase
    private lateinit var getPlanProgressUseCase: GetPlanProgressUseCase
    private lateinit var getDueTasksUseCase: GetDueTasksUseCase

    private lateinit var contextEngine: ContextEngineImpl

    private val baseTime = 1_700_000_000_000L

    @Before
    fun setUp() {
        clock = TestClock(baseTime)
        goalRepository = TestGoalRepository()
        planRepository = TestPlanRepository()
        taskRepository = TestTaskRepository()
        commitmentRepository = TestCommitmentRepository()
        reflectionRepository = TestReflectionRepository()
        outcomeRepository = TestOutcomeRepository()
        eventRepository = TestEventRepository()
        barrierRepository = TestBarrierRepository()
        protectionRepository = TestProtectionRepository()
        appUsageProvider = TestAppUsageProvider()
        appUsageSettingsRepository = TestAppUsageSettingsRepository()
        observationRepository = FakeObservationRepository()
        checkpointStore = TestAppUsageCheckpointStore()
        patternRepository = mockk(relaxed = true)
        buildFactualContextSnapshotUseCase = mockk(relaxed = true)

        getPlanProgressUseCase = GetPlanProgressUseCase(taskRepository)
        getGoalProgressUseCase = GetGoalProgressUseCase(planRepository, taskRepository)
        getDueTasksUseCase = GetDueTasksUseCase(taskRepository, planRepository, clock)

        contextEngine = ContextEngineImpl(
            clock = clock,
            commitmentRepository = commitmentRepository,
            eventRepository = eventRepository,
            buildFactualContextSnapshotUseCase = buildFactualContextSnapshotUseCase,
            reflectionRepository = reflectionRepository,
            protectionRepository = protectionRepository,
            goalRepository = goalRepository,
            patternRepository = patternRepository,
            memoryRepository = null,
            planRepository = planRepository,
            taskRepository = taskRepository,
            outcomeRepository = outcomeRepository,
            barrierRepository = barrierRepository,
            appUsageObservationProvider = appUsageProvider,
            appUsageSettingsRepository = appUsageSettingsRepository,
            observationRepository = observationRepository,
            checkpointStore = checkpointStore,
            getDueTasksUseCase = getDueTasksUseCase,
            getGoalProgressUseCase = getGoalProgressUseCase,
            getPlanProgressUseCase = getPlanProgressUseCase
        )
    }

    @Test
    fun testProductivityContextAssembly_fullHierarchyAndProgress() = runBlocking {
        val userId = "user-1"

        // Goals: 1 active, 1 paused, 1 completed
        val goalActive = Goal(
            id = "g1",
            userId = userId,
            title = "Master Kotlin",
            description = "Learn advanced Kotlin",
            why = "Career growth",
            importance = 9,
            status = GoalStatus.ACTIVE,
            createdAt = baseTime,
            updatedAt = baseTime
        )
        val goalPaused = Goal(
            id = "g2",
            userId = userId,
            title = "Marathon Prep",
            description = "Run 42k",
            why = "Fitness",
            importance = 7,
            status = GoalStatus.PAUSED,
            createdAt = baseTime - 1000,
            updatedAt = baseTime
        )
        val goalCompleted = Goal(
            id = "g3",
            userId = userId,
            title = "Read Book",
            description = "Finish Architecture book",
            why = "Knowledge",
            importance = 5,
            status = GoalStatus.COMPLETED,
            createdAt = baseTime - 2000,
            updatedAt = baseTime,
            completedAt = baseTime
        )
        goalRepository.goals = listOf(goalActive, goalPaused, goalCompleted)

        // Plans: linked to g1
        val plan1 = Plan(
            id = "p1",
            userId = userId,
            goalId = "g1",
            ambitionId = null,
            title = "Coroutines Mastery",
            description = "Deep dive coroutines",
            status = PlanStatus.ACTIVE,
            createdAt = baseTime,
            updatedAt = baseTime,
            source = EntitySource.USER
        )
        planRepository.plans = listOf(plan1)

        // Tasks: 2 under p1 (1 completed, 1 pending)
        val t1 = Task(
            id = "t1",
            userId = userId,
            goalId = "g1",
            planId = "p1",
            title = "Read Flow doc",
            description = "Flow internals",
            status = TaskStatus.COMPLETED,
            priority = 4,
            estimatedDurationMinutes = 60,
            dueAt = baseTime + 3600_000L,
            createdAt = baseTime,
            updatedAt = baseTime,
            completedAt = baseTime,
            source = EntitySource.USER
        )
        val t2 = Task(
            id = "t2",
            userId = userId,
            goalId = "g1",
            planId = "p1",
            title = "Build Channels sample",
            description = "Actor sample",
            status = TaskStatus.PENDING,
            priority = 3,
            estimatedDurationMinutes = 45,
            dueAt = baseTime - (86_400_000L * 2),
            createdAt = baseTime,
            updatedAt = baseTime,
            source = EntitySource.USER
        )
        taskRepository.tasks = listOf(t1, t2)

        // Commitments: 1 completed, 1 missed, 1 postponed
        val comm1 = Commitment(
            id = "c1",
            userId = userId,
            goalId = "g1",
            planId = "p1",
            taskId = "t1",
            title = "Read 10 pages",
            description = "Coroutines flow",
            committedAt = baseTime - 5000,
            status = CommitmentStatus.COMPLETED,
            priority = 3,
            source = EntitySource.USER,
            createdAt = baseTime,
            updatedAt = baseTime,
            completedAt = baseTime
        )
        val comm2 = Commitment(
            id = "c2",
            userId = userId,
            goalId = "g1",
            planId = "p1",
            taskId = "t2",
            title = "Write code sample",
            description = "Build sample",
            committedAt = baseTime - 4000,
            status = CommitmentStatus.MISSED,
            priority = 3,
            source = EntitySource.USER,
            createdAt = baseTime,
            updatedAt = baseTime,
            missedAt = baseTime
        )
        val comm3 = Commitment(
            id = "c3",
            userId = userId,
            title = "Meditation",
            description = "15m breathing",
            committedAt = baseTime - 3000,
            status = CommitmentStatus.POSTPONED,
            priority = 2,
            source = EntitySource.USER,
            createdAt = baseTime,
            updatedAt = baseTime,
            postponedAt = baseTime
        )
        commitmentRepository.commitments = listOf(comm1, comm2, comm3)

        // Reflections, Outcomes, Barriers
        val ref1 = Reflection(
            id = "r1",
            userId = userId,
            targetEntityId = null,
            targetEntityType = null,
            content = "Productive morning study session",
            sentiment = "positive",
            createdAt = baseTime
        )
        reflectionRepository.reflections = listOf(ref1)

        val out1 = Outcome(
            id = "o1",
            userId = userId,
            sourceEntityId = "c1",
            sourceEntityType = "COMMITMENT",
            resultStatus = ResultStatus.POSITIVE,
            actualDurationMinutes = 25,
            completedAt = baseTime,
            createdAt = baseTime
        )
        outcomeRepository.outcomes = listOf(out1)

        val bar1 = BarrierHypothesis(
            id = "b1",
            userId = userId,
            category = com.sanket_satpute_20.ironmind.domain.ai.BarrierCategory.DISTRACTION,
            description = "Late night distraction",
            confirmationState = BarrierConfirmationState.CONFIRMED,
            status = BarrierStatus.ACTIVE,
            sourceReflectionId = "r1",
            firstObservedAt = baseTime,
            lastObservedAt = baseTime
        )
        barrierRepository.barriers = listOf(bar1)

        val result = contextEngine.getUnifiedContext(userId, baseTime - 86_400_000L, baseTime)
        assertTrue(result is Result.Success)

        val context = (result as Result.Success).data
        val prod = context.productivity

        // Verify Goals
        assertEquals(1, prod.activeGoals.size)
        assertEquals(1, prod.pausedGoals.size)
        assertEquals(1, prod.completedGoals.size)
        assertEquals("Master Kotlin", prod.activeGoals[0].goal.title)
        assertEquals(50, prod.activeGoals[0].progress.percentage) // 1 completed out of 2 tasks = 50%
        assertEquals(EpistemicAuthority.USER_CONFIRMED_FACT, prod.activeGoals[0].authority)

        // Verify Plans
        assertEquals(1, prod.activePlans.size)
        assertEquals("Coroutines Mastery", prod.activePlans[0].plan.title)
        assertEquals("Master Kotlin", prod.activePlans[0].parentGoalTitle)
        assertEquals(50, prod.activePlans[0].progress.percentage)

        // Verify Tasks
        assertEquals(2, prod.taskSummary.allTasks.size)
        assertEquals(1, prod.taskSummary.completedTasks.size)
        assertEquals(1, prod.taskSummary.pendingTasks.size)
        assertEquals(1, prod.taskSummary.overdueTasks.size) // t2 dueAt < baseTime
        assertEquals("Build Channels sample", prod.taskSummary.overdueTasks[0].task.title)

        // Verify Commitments
        assertEquals(1, prod.commitmentSummary.completedCommitments.size)
        assertEquals(1, prod.commitmentSummary.missedCommitments.size)
        assertEquals(1, prod.commitmentSummary.postponedCommitments.size)

        // Verify Reflections, Outcomes, Barriers
        assertEquals(1, prod.recentReflections.size)
        assertEquals(1, prod.recentOutcomes.size)
        assertEquals(1, prod.userConfirmedBarriers.size)
        assertEquals(com.sanket_satpute_20.ironmind.domain.ai.BarrierCategory.DISTRACTION, prod.userConfirmedBarriers[0].category)
    }

    @Test
    fun testRepeatedMissedCommitments_detectedWhenMissCountAtLeastTwo() = runBlocking {
        val userId = "user-1"

        // 2 missed commitments with the same title / task
        val m1 = Commitment(
            id = "c1",
            userId = userId,
            taskId = "task-x",
            title = "Morning Run",
            description = "Run 5k",
            committedAt = baseTime - 86_400_000L,
            status = CommitmentStatus.MISSED,
            priority = 3,
            source = EntitySource.USER,
            createdAt = baseTime - 86_400_000L,
            updatedAt = baseTime - 86_400_000L,
            missedAt = baseTime - 86_400_000L
        )
        val m2 = Commitment(
            id = "c2",
            userId = userId,
            taskId = "task-x",
            title = "Morning Run",
            description = "Run 5k",
            committedAt = baseTime - 10_000L,
            status = CommitmentStatus.MISSED,
            priority = 3,
            source = EntitySource.USER,
            createdAt = baseTime - 10_000L,
            updatedAt = baseTime - 10_000L,
            missedAt = baseTime - 10_000L
        )
        commitmentRepository.commitments = listOf(m1, m2)

        val result = contextEngine.getUnifiedContext(userId, baseTime - 86_400_000L * 2, baseTime)
        assertTrue(result is Result.Success)

        val context = (result as Result.Success).data
        val repeated = context.productivity.commitmentSummary.repeatedMissedCommitments

        assertEquals(1, repeated.size)
        assertEquals("Morning Run", repeated[0].title)
        assertEquals("task-x", repeated[0].taskId)
        assertEquals(2, repeated[0].missCount)
        assertEquals(baseTime - 10_000L, repeated[0].lastMissedAt)
    }

    @Test
    fun testDigitalUsageContextAssembly_whenPermissionGranted() = runBlocking {
        val userId = "user-1"
        appUsageProvider.permissionGranted = true
        appUsageSettingsRepository.isEnabled = true

        val ev1 = AppUsageEvent("com.android.chrome", baseTime - 3600_000L, baseTime - 1800_000L, 1800_000L)
        val ev2 = AppUsageEvent("com.android.chrome", baseTime - 1800_000L, baseTime - 600_000L, 1200_000L)
        val ev3 = AppUsageEvent("com.slack", baseTime - 600_000L, baseTime, 600_000L)
        appUsageProvider.events = listOf(ev1, ev2, ev3)

        val obs1 = Observation(id = "o1", userId = userId, type = ObservationType.APP_USAGE_SESSION, source = ObservationSource.APP_USAGE, occurredAt = baseTime - 3600_000L, recordedAt = baseTime, subjectId = null, value = "com.android.chrome", context = "duration_ms=1800000", confidence = null, provenance = ObservationProvenance(ObservationSource.APP_USAGE, "UsageStatsManager", baseTime))
        val obs2 = Observation(id = "o2", userId = userId, type = ObservationType.APP_USAGE_SESSION, source = ObservationSource.APP_USAGE, occurredAt = baseTime - 1800_000L, recordedAt = baseTime, subjectId = null, value = "com.android.chrome", context = "duration_ms=1200000", confidence = null, provenance = ObservationProvenance(ObservationSource.APP_USAGE, "UsageStatsManager", baseTime))
        val obs3 = Observation(id = "o3", userId = userId, type = ObservationType.APP_USAGE_SESSION, source = ObservationSource.APP_USAGE, occurredAt = baseTime - 600_000L, recordedAt = baseTime, subjectId = null, value = "com.slack", context = "duration_ms=600000", confidence = null, provenance = ObservationProvenance(ObservationSource.APP_USAGE, "UsageStatsManager", baseTime))
        observationRepository.insertObservation(obs1)
        observationRepository.insertObservation(obs2)
        observationRepository.insertObservation(obs3)
        checkpointStore.saveCheckpoint(AppUsageCheckpoint(userId = userId, lastCollectedIntervalEndMs = baseTime, activePackageName = null, activeSessionStartTimeMs = null, updatedAt = baseTime))

        val protSession = ProtectionSession(
            id = "s1",
            userId = userId,
            startedAt = baseTime - 1800_000L,
            status = ProtectionSessionStatus.ACTIVE,
            source = EntitySource.USER,
            overrideAllowed = false,
            createdAt = baseTime - 1800_000L,
            updatedAt = baseTime - 1800_000L
        )
        val protRule = ProtectionRule(
            id = "r1",
            userId = userId,
            targetPackage = "com.social.app",
            triggerType = ProtectionTriggerType.MANUAL,
            conditions = "{}",
            enabled = true,
            priority = 1,
            createdAt = baseTime,
            updatedAt = baseTime
        )
        protectionRepository.activeSession = protSession
        protectionRepository.rules = listOf(protRule)

        val result = contextEngine.getUnifiedContext(userId, baseTime - 86_400_000L, baseTime)
        assertTrue(result is Result.Success)

        val context = (result as Result.Success).data
        val digital = context.digitalBehaviour

        assertEquals(UsagePermissionStatus.GRANTED, digital.permissionStatus)
        assertEquals(SourceAvailability.AVAILABLE, digital.availability)
        assertEquals(2, digital.appUsages.size)

        val chrome = digital.appUsages.first { it.packageName == "com.android.chrome" }
        assertEquals(3000_000L, chrome.totalDurationMs)
        assertEquals(2, chrome.sessionCount)
        assertEquals(baseTime - 3600_000L, chrome.firstObservedAt)
        assertEquals(baseTime - 600_000L, chrome.lastObservedAt)
        assertEquals(EpistemicAuthority.SYSTEM_RECORDED_OBSERVATION, chrome.authority)

        assertEquals(1, digital.focusActivity.activeSessions.size)
        assertEquals(1, digital.focusActivity.activeRules.size)
        assertEquals(1800_000L, digital.focusActivity.totalFocusDurationMs)
    }

    @Test
    fun testMissingUsagePermission_reportsDeniedAvailabilityWithoutFalseZeroClaim() = runBlocking {
        val userId = "user-1"
        appUsageProvider.permissionGranted = false // Denied in Android Settings
        appUsageSettingsRepository.isEnabled = true

        val result = contextEngine.getUnifiedContext(userId, baseTime - 86_400_000L, baseTime)
        assertTrue(result is Result.Success)

        val context = (result as Result.Success).data
        val digital = context.digitalBehaviour

        assertEquals(UsagePermissionStatus.DENIED, digital.permissionStatus)
        assertEquals(SourceAvailability.PERMISSION_DENIED, digital.availability)
        assertTrue(digital.appUsages.isEmpty())
        assertNotNull(digital.notes)
        assertTrue(digital.notes!!.contains("permission not granted"))

        // Provenance entry must explicitly reflect PERMISSION_DENIED
        val prov = context.provenanceMap["AppUsage"]
        assertNotNull(prov)
        assertEquals(SourceAvailability.PERMISSION_DENIED, prov!!.availability)
        assertFalse(prov.isPermissionGranted)
        assertTrue(context.isDegraded)
    }

    @Test
    fun testEmptyUsageData_whenPermissionGrantedButZeroEventsInWindow() = runBlocking {
        val userId = "user-1"
        appUsageProvider.permissionGranted = true
        appUsageSettingsRepository.isEnabled = true
        appUsageProvider.events = emptyList() // No events recorded in window

        // Prior observation proves collection history was active before windowStart
        val priorObs = Observation(
            id = "o-prior",
            userId = userId,
            type = ObservationType.APP_USAGE_SESSION,
            source = ObservationSource.APP_USAGE,
            occurredAt = baseTime - 86_400_000L - 1000L,
            recordedAt = baseTime - 86_400_000L - 1000L,
            subjectId = null,
            value = "com.prior.app",
            context = "duration_ms=1000",
            confidence = null,
            provenance = ObservationProvenance(ObservationSource.APP_USAGE, "UsageStatsManager", baseTime)
        )
        observationRepository.insertObservation(priorObs)
        checkpointStore.saveCheckpoint(AppUsageCheckpoint(userId = userId, lastCollectedIntervalEndMs = baseTime, activePackageName = null, activeSessionStartTimeMs = null, updatedAt = baseTime))

        val result = contextEngine.getUnifiedContext(userId, baseTime - 86_400_000L, baseTime)
        assertTrue(result is Result.Success)

        val context = (result as Result.Success).data
        val digital = context.digitalBehaviour

        assertEquals(UsagePermissionStatus.GRANTED, digital.permissionStatus)
        assertEquals(SourceAvailability.EMPTY_NO_DATA, digital.availability)
        assertTrue(digital.appUsages.isEmpty())

        val prov = context.provenanceMap["AppUsage"]
        assertNotNull(prov)
        assertEquals(SourceAvailability.EMPTY_NO_DATA, prov!!.availability)
        assertTrue(prov.isPermissionGranted)
    }

    @Test
    fun testStaleDataAndTimestamps_marksIsStaleTrueWhenOlderThanSevenDays() = runBlocking {
        val userId = "user-1"
        val eightDaysAgo = baseTime - (8 * 86_400_000L)

        // Goal recorded 8 days ago and never updated
        val oldGoal = Goal(
            id = "g-old",
            userId = userId,
            title = "Old Goal",
            description = "Old Description",
            why = "Reason",
            importance = 5,
            status = GoalStatus.ACTIVE,
            createdAt = eightDaysAgo,
            updatedAt = eightDaysAgo
        )
        goalRepository.goals = listOf(oldGoal)

        val result = contextEngine.getUnifiedContext(userId, baseTime - 86_400_000L, baseTime)
        assertTrue(result is Result.Success)

        val context = (result as Result.Success).data
        val goalProv = context.provenanceMap["Goals"]

        assertNotNull(goalProv)
        assertTrue(goalProv!!.isStale)
        assertEquals(eightDaysAgo, goalProv.lastRecordedAt)
        assertEquals(8 * 86_400_000L, goalProv.dataAgeMs)
        assertFalse(context.isFresh)
    }

    @Test
    fun testSessionOwnershipIsolation_excludesOtherUserRecordsCompletely() = runBlocking {
        val alice = "user-alice"
        val bob = "user-bob"

        goalRepository.goals = listOf(
            Goal(id = "g-alice", userId = alice, title = "Alice Goal", description = "Desc", why = "Why", importance = 8, status = GoalStatus.ACTIVE, createdAt = baseTime, updatedAt = baseTime),
            Goal(id = "g-bob", userId = bob, title = "Bob Goal", description = "Desc", why = "Why", importance = 8, status = GoalStatus.ACTIVE, createdAt = baseTime, updatedAt = baseTime)
        )
        planRepository.plans = listOf(
            Plan(id = "p-alice", userId = alice, goalId = "g-alice", title = "Alice Plan", description = "Desc", status = PlanStatus.ACTIVE, createdAt = baseTime, updatedAt = baseTime, source = EntitySource.USER),
            Plan(id = "p-bob", userId = bob, goalId = "g-bob", title = "Bob Plan", description = "Desc", status = PlanStatus.ACTIVE, createdAt = baseTime, updatedAt = baseTime, source = EntitySource.USER)
        )
        taskRepository.tasks = listOf(
            Task(id = "t-alice", userId = alice, goalId = "g-alice", planId = "p-alice", title = "Alice Task", description = "Desc", status = TaskStatus.PENDING, priority = 3, createdAt = baseTime, updatedAt = baseTime, source = EntitySource.USER),
            Task(id = "t-bob", userId = bob, goalId = "g-bob", planId = "p-bob", title = "Bob Task", description = "Desc", status = TaskStatus.PENDING, priority = 3, createdAt = baseTime, updatedAt = baseTime, source = EntitySource.USER)
        )
        commitmentRepository.commitments = listOf(
            Commitment(id = "c-alice", userId = alice, goalId = "g-alice", planId = "p-alice", taskId = "t-alice", title = "Alice Comm", description = "Desc", committedAt = baseTime, status = CommitmentStatus.COMMITTED, priority = 3, source = EntitySource.USER, createdAt = baseTime, updatedAt = baseTime),
            Commitment(id = "c-bob", userId = bob, goalId = "g-bob", planId = "p-bob", taskId = "t-bob", title = "Bob Comm", description = "Desc", committedAt = baseTime, status = CommitmentStatus.COMMITTED, priority = 3, source = EntitySource.USER, createdAt = baseTime, updatedAt = baseTime)
        )
        reflectionRepository.reflections = listOf(
            Reflection(id = "r-alice", userId = alice, targetEntityId = null, targetEntityType = null, content = "Alice reflection", sentiment = null, createdAt = baseTime),
            Reflection(id = "r-bob", userId = bob, targetEntityId = null, targetEntityType = null, content = "Bob reflection", sentiment = null, createdAt = baseTime)
        )

        // Query context for Alice
        val aliceResult = contextEngine.getUnifiedContext(alice, baseTime - 86_400_000L, baseTime)
        assertTrue(aliceResult is Result.Success)
        val aliceCtx = (aliceResult as Result.Success).data

        assertEquals(1, aliceCtx.productivity.activeGoals.size)
        assertEquals("Alice Goal", aliceCtx.productivity.activeGoals[0].goal.title)
        assertEquals(1, aliceCtx.productivity.activePlans.size)
        assertEquals("Alice Plan", aliceCtx.productivity.activePlans[0].plan.title)
        assertEquals(1, aliceCtx.productivity.taskSummary.allTasks.size)
        assertEquals("Alice Task", aliceCtx.productivity.taskSummary.allTasks[0].title)
        assertEquals(1, aliceCtx.productivity.commitmentSummary.activeCommitments.size)
        assertEquals("Alice Comm", aliceCtx.productivity.commitmentSummary.activeCommitments[0].title)
        assertEquals(1, aliceCtx.productivity.recentReflections.size)
        assertEquals("Alice reflection", aliceCtx.productivity.recentReflections[0].content)

        // Query context for Bob
        val bobResult = contextEngine.getUnifiedContext(bob, baseTime - 86_400_000L, baseTime)
        assertTrue(bobResult is Result.Success)
        val bobCtx = (bobResult as Result.Success).data

        assertEquals(1, bobCtx.productivity.activeGoals.size)
        assertEquals("Bob Goal", bobCtx.productivity.activeGoals[0].goal.title)
        assertEquals(1, bobCtx.productivity.activePlans.size)
        assertEquals("Bob Plan", bobCtx.productivity.activePlans[0].plan.title)
        assertEquals(1, bobCtx.productivity.taskSummary.allTasks.size)
        assertEquals("Bob Task", bobCtx.productivity.taskSummary.allTasks[0].title)
        assertEquals(1, bobCtx.productivity.commitmentSummary.activeCommitments.size)
        assertEquals("Bob Comm", bobCtx.productivity.commitmentSummary.activeCommitments[0].title)
    }

    @Test
    fun testEpistemicAuthorityDistinction_neverConflatesFactsWithHypotheses() = runBlocking {
        val userId = "user-1"
        appUsageProvider.permissionGranted = true
        appUsageSettingsRepository.isEnabled = true

        // Goal & Plan (Fact)
        val goal = Goal(id = "g1", userId = userId, title = "Workout", description = "Gym session", why = "Health", importance = 8, status = GoalStatus.ACTIVE, createdAt = baseTime, updatedAt = baseTime)
        goalRepository.goals = listOf(goal)
        val plan = Plan(id = "p1", userId = userId, goalId = "g1", title = "Hypertrophy", description = "Desc", status = PlanStatus.ACTIVE, createdAt = baseTime, updatedAt = baseTime, source = EntitySource.USER)
        planRepository.plans = listOf(plan)

        // Overdue Task (Fact) & High App Usage (Observation)
        val task = Task(id = "t1", userId = userId, goalId = "g1", planId = "p1", title = "Leg day workout", description = "Desc", status = TaskStatus.PENDING, priority = 4, dueAt = baseTime - 3600_000L, createdAt = baseTime, updatedAt = baseTime, source = EntitySource.USER)
        taskRepository.tasks = listOf(task)
        appUsageProvider.events = listOf(
            AppUsageEvent("com.social.app", baseTime - 1800_000L, baseTime, 1800_000L) // 30 min social usage
        )
        val obsSocial = Observation(
            id = "o-soc",
            userId = userId,
            type = ObservationType.APP_USAGE_SESSION,
            source = ObservationSource.APP_USAGE,
            occurredAt = baseTime - 1800_000L,
            recordedAt = baseTime,
            subjectId = null,
            value = "com.social.app",
            context = "duration_ms=1800000",
            confidence = null,
            provenance = ObservationProvenance(ObservationSource.APP_USAGE, "UsageStatsManager", baseTime)
        )
        observationRepository.insertObservation(obsSocial)
        checkpointStore.saveCheckpoint(
            AppUsageCheckpoint(
                userId = userId,
                lastCollectedIntervalEndMs = baseTime,
                activePackageName = null,
                activeSessionStartTimeMs = null,
                updatedAt = baseTime
            )
        )

        val result = contextEngine.getUnifiedContext(userId, baseTime - 86_400_000L, baseTime)
        assertTrue(result is Result.Success)

        val context = (result as Result.Success).data

        // 1. Goal & Plan authority
        assertEquals(EpistemicAuthority.USER_CONFIRMED_FACT, context.productivity.activeGoals[0].authority)
        assertEquals(EpistemicAuthority.USER_CONFIRMED_FACT, context.productivity.activePlans[0].authority)

        // 2. App usage observation authority
        assertEquals(EpistemicAuthority.SYSTEM_RECORDED_OBSERVATION, context.digitalBehaviour.appUsages[0].authority)

        // 3. Derived calculation authority (progress)
        assertTrue(context.productivity.activeGoals[0].progress.isZeroTasks.not() || context.productivity.activeGoals[0].progress.totalTasks == 1)

        // 4. Cross-domain correlation authority MUST be AI_HYPOTHESIS
        assertEquals(1, context.correlations.size)
        val correlation = context.correlations[0]
        assertEquals(EpistemicAuthority.AI_HYPOTHESIS, correlation.authority)
        assertEquals(CrossDomainCorrelationType.CONCURRENT_DIGITAL_ACTIVITY_DURING_PENDING_TASK, correlation.correlationType)
        assertTrue(correlation.disclaimer.contains("Tentative correlation hypothesis only"))
        assertTrue(correlation.disclaimer.contains("Does not imply causation"))
    }

    @Test
    fun testGracefulHandling_allSourcesEmpty_succeedsWithoutException() = runBlocking {
        val userId = "user-empty"
        appUsageProvider.permissionGranted = true
        appUsageSettingsRepository.isEnabled = true
        appUsageProvider.events = emptyList()

        val result = contextEngine.getUnifiedContext(userId, baseTime - 86_400_000L, baseTime)
        assertTrue(result is Result.Success)

        val context = (result as Result.Success).data
        assertTrue(context.productivity.activeGoals.isEmpty())
        assertTrue(context.productivity.activePlans.isEmpty())
        assertTrue(context.productivity.taskSummary.allTasks.isEmpty())
        assertTrue(context.productivity.commitmentSummary.todayCommitments.isEmpty())
        assertTrue(context.digitalBehaviour.appUsages.isEmpty())
        assertTrue(context.correlations.isEmpty())

        assertEquals(SourceAvailability.EMPTY_NO_DATA, context.provenanceMap["Goals"]!!.availability)
        assertEquals(SourceAvailability.EMPTY_NO_DATA, context.provenanceMap["Plans"]!!.availability)
        assertEquals(SourceAvailability.EMPTY_NO_DATA, context.provenanceMap["Tasks"]!!.availability)
        assertEquals(SourceAvailability.EMPTY_NO_DATA, context.provenanceMap["Commitments"]!!.availability)
        assertEquals(SourceAvailability.PARTIAL_HISTORY, context.provenanceMap["AppUsage"]!!.availability)
    }

    // --- Test Doubles ---

    class TestClock(var time: Long) : Clock {
        override fun currentTimeMillis() = time
    }

    class TestGoalRepository : GoalRepository {
        var goals = emptyList<Goal>()
        override suspend fun getGoalsForUser(userId: String): Result<List<Goal>, Exception> = Result.Success(goals.filter { it.userId == userId })
        override suspend fun getActiveGoalsForUser(userId: String, limit: Int): Result<List<Goal>, Exception> = Result.Success(goals.filter { it.userId == userId && it.status == GoalStatus.ACTIVE }.take(limit))
        override suspend fun getGoal(id: String): Result<Goal?, Exception> = Result.Success(goals.find { it.id == id })
        override suspend fun saveGoal(goal: Goal): Result<Unit, Exception> = Result.Success(Unit)
        override suspend fun searchGoals(userId: String, query: String): Result<List<Goal>, Exception> = Result.Success(emptyList())
    }

    class TestPlanRepository : PlanRepository {
        var plans = emptyList<Plan>()
        override suspend fun getPlan(id: String): Result<Plan?, Exception> = Result.Success(plans.find { it.id == id })
        override suspend fun getPlansForGoal(goalId: String): Result<List<Plan>, Exception> = Result.Success(plans.filter { it.goalId == goalId })
        override suspend fun getPlansForUser(userId: String): Result<List<Plan>, Exception> = Result.Success(plans.filter { it.userId == userId })
        override suspend fun savePlan(plan: Plan): Result<Unit, Exception> = Result.Success(Unit)
    }

    class TestTaskRepository : TaskRepository {
        var tasks = emptyList<Task>()
        override suspend fun getTask(id: String): Result<Task?, Exception> = Result.Success(tasks.find { it.id == id })
        override suspend fun getTasksForPlan(planId: String): Result<List<Task>, Exception> = Result.Success(tasks.filter { it.planId == planId })
        override suspend fun getTasksForGoal(goalId: String): Result<List<Task>, Exception> = Result.Success(tasks.filter { it.goalId == goalId })
        override suspend fun getTasksForUser(userId: String): Result<List<Task>, Exception> = Result.Success(tasks.filter { it.userId == userId })
        override suspend fun saveTask(task: Task): Result<Unit, Exception> = Result.Success(Unit)
        override suspend fun deleteTask(userId: String, taskId: String): Result<Unit, Exception> = Result.Success(Unit)
    }

    class TestCommitmentRepository : CommitmentRepository {
        var commitments = emptyList<Commitment>()
        override suspend fun getCommitment(id: String): Result<Commitment?, Exception> = Result.Success(commitments.find { it.id == id })
        override suspend fun getCommitmentsForUser(userId: String): Result<List<Commitment>, Exception> = Result.Success(commitments.filter { it.userId == userId })
        override suspend fun getActiveCommitmentsForUser(userId: String, statuses: List<CommitmentStatus>): Result<List<Commitment>, Exception> = Result.Success(commitments.filter { it.userId == userId && it.status in statuses })
        override suspend fun getActiveCommitmentsForUser(userId: String, statuses: List<CommitmentStatus>, limit: Int): Result<List<Commitment>, Exception> = Result.Success(commitments.filter { it.userId == userId && it.status in statuses }.take(limit))
        override suspend fun getCommitmentsForDateRange(userId: String, startTime: Long, endTime: Long): Result<List<Commitment>, Exception> = Result.Success(commitments.filter { it.userId == userId && it.createdAt in startTime..endTime })
        override suspend fun getCommitmentsForGoal(goalId: String): Result<List<Commitment>, Exception> = Result.Success(commitments.filter { it.goalId == goalId })
        override suspend fun getCommitmentsForPlan(planId: String): Result<List<Commitment>, Exception> = Result.Success(commitments.filter { it.planId == planId })
        override suspend fun getCommitmentsForTask(taskId: String): Result<List<Commitment>, Exception> = Result.Success(commitments.filter { it.taskId == taskId })
        override suspend fun saveCommitment(commitment: Commitment): Result<Unit, Exception> = Result.Success(Unit)
        override suspend fun searchCommitments(userId: String, query: String): Result<List<Commitment>, Exception> = Result.Success(emptyList())
    }

    class TestReflectionRepository : ReflectionRepository {
        var reflections = emptyList<Reflection>()
        override suspend fun getReflectionsForDateRange(userId: String, startTime: Long, endTime: Long): Result<List<Reflection>, Exception> = Result.Success(reflections.filter { it.userId == userId && it.createdAt in startTime..endTime })
        override suspend fun getReflection(id: String): Result<Reflection?, Exception> = Result.Success(reflections.find { it.id == id })
        override suspend fun getReflectionForUser(userId: String, id: String): Result<Reflection?, Exception> = Result.Success(reflections.find { it.userId == userId && it.id == id })
        override suspend fun saveReflection(reflection: Reflection): Result<Unit, Exception> = Result.Success(Unit)
        override suspend fun searchReflections(userId: String, query: String): Result<List<Reflection>, Exception> = Result.Success(emptyList())
        override suspend fun getReflectionsForTimeWindow(userId: String, startTime: Long, endTime: Long, limit: Int, orderAsc: Boolean): Result<List<Reflection>, Exception> = Result.Success(emptyList())
    }

    class TestOutcomeRepository : OutcomeRepository {
        var outcomes = emptyList<Outcome>()
        override suspend fun getOutcomeForSource(sourceEntityId: String): Result<Outcome?, Exception> = Result.Success(outcomes.find { it.sourceEntityId == sourceEntityId })
        override suspend fun getOutcomesForUser(userId: String): Result<List<Outcome>, Exception> = Result.Success(outcomes.filter { it.userId == userId })
        override suspend fun saveOutcome(outcome: Outcome): Result<Unit, Exception> = Result.Success(Unit)
    }

    class TestEventRepository : EventRepository {
        var events = emptyList<Event>()
        override suspend fun getEventsForUser(userId: String): Result<List<Event>, Exception> = Result.Success(events.filter { it.userId == userId })
        override suspend fun getEventsForEntity(entityId: String): Result<List<Event>, Exception> = Result.Success(events.filter { it.entityId == entityId })
        override suspend fun getEvent(id: String): Result<Event?, Exception> = Result.Success(events.find { it.id == id })
        override suspend fun getEventForUser(userId: String, id: String): Result<Event?, Exception> = Result.Success(events.find { it.userId == userId && it.id == id })
        override suspend fun getEventsForDateRange(userId: String, startTime: Long, endTime: Long): Result<List<Event>, Exception> = Result.Success(events.filter { it.userId == userId && it.occurredAt in startTime..endTime })
        override suspend fun saveEvent(event: Event): Result<Event, Exception> = Result.Success(event)
        override suspend fun searchEvents(userId: String, query: String): Result<List<Event>, Exception> = Result.Success(emptyList())
        override suspend fun getEventsForTimeWindow(userId: String, startTime: Long, endTime: Long, types: List<EventType>?, limit: Int, orderAsc: Boolean): Result<List<Event>, Exception> = Result.Success(emptyList())
        override suspend fun getEventsByCausationId(userId: String, causationId: String): Result<List<Event>, Exception> = Result.Success(emptyList())
    }

    class TestBarrierRepository : BarrierRepository {
        var barriers = emptyList<BarrierHypothesis>()
        override suspend fun getBarriersForUser(userId: String): Result<List<BarrierHypothesis>, Exception> = Result.Success(barriers.filter { it.userId == userId })
        override suspend fun getActiveBarriersForUser(userId: String, excludedStates: List<BarrierConfirmationState>, limit: Int): Result<List<BarrierHypothesis>, Exception> = Result.Success(barriers.filter { it.userId == userId && it.confirmationState !in excludedStates }.take(limit))
        override suspend fun getBarrier(id: String): Result<BarrierHypothesis?, Exception> = Result.Success(barriers.find { it.id == id })
        override suspend fun saveBarrier(barrier: BarrierHypothesis): Result<Unit, Exception> = Result.Success(Unit)
    }

    class TestProtectionRepository : ProtectionRepository {
        var activeSession: ProtectionSession? = null
        var rules = emptyList<ProtectionRule>()
        override suspend fun getActiveProtectionSessionsForUser(userId: String): Result<List<ProtectionSession>, Exception> = Result.Success(listOfNotNull(activeSession).filter { it.userId == userId })
        override suspend fun getProtectionRulesForUser(userId: String): Result<List<ProtectionRule>, Exception> = Result.Success(rules.filter { it.userId == userId })
        override suspend fun getProtectionSession(id: String): Result<ProtectionSession, Exception> = Result.Success(activeSession!!)
        override suspend fun getProtectionRule(id: String): Result<ProtectionRule, Exception> = Result.Success(rules.first())
        override suspend fun saveProtectionRule(rule: ProtectionRule): Result<Unit, Exception> = Result.Success(Unit)
        override suspend fun saveProtectionSession(session: ProtectionSession): Result<Unit, Exception> = Result.Success(Unit)
    }

    class TestAppUsageProvider : AppUsageObservationProvider {
        var permissionGranted: Boolean = true
        var events = emptyList<AppUsageEvent>()
        override fun isPermissionGranted(): Boolean = permissionGranted
        override fun getAppUsageSince(fromTimeMillis: Long): List<AppUsageEvent> = events.filter { it.endTimeMillis >= fromTimeMillis }
    }

    class TestAppUsageSettingsRepository : AppUsageObservationSettingsRepository {
        var isEnabled: Boolean = true
        override suspend fun getSettings(userId: String): Result<AppUsageObservationSettings, Exception> =
            Result.Success(AppUsageObservationSettings(userId, isEnabled))
        override suspend fun setEnabled(userId: String, isEnabled: Boolean): Result<Unit, Exception> {
            this.isEnabled = isEnabled
            return Result.Success(Unit)
        }
    }

    class TestAppUsageCheckpointStore : AppUsageCheckpointStore {
        val checkpoints = mutableMapOf<String, AppUsageCheckpoint>()
        override suspend fun getCheckpoint(userId: String): Result<AppUsageCheckpoint?, Exception> = Result.Success(checkpoints[userId])
        override suspend fun saveCheckpoint(checkpoint: AppUsageCheckpoint): Result<Unit, Exception> {
            checkpoints[checkpoint.userId] = checkpoint
            return Result.Success(Unit)
        }
        override suspend fun saveObservationsWithCheckpoint(observations: List<Observation>, checkpoint: AppUsageCheckpoint): Result<Unit, Exception> {
            checkpoints[checkpoint.userId] = checkpoint
            return Result.Success(Unit)
        }
        override suspend fun clearCheckpointForUser(userId: String): Result<Unit, Exception> {
            checkpoints.remove(userId)
            return Result.Success(Unit)
        }
    }
}
