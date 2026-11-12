package com.sanket_satpute_20.ironmind.domain.engine

import com.sanket_satpute_20.ironmind.domain.common.Clock
import com.sanket_satpute_20.ironmind.domain.model.DueTaskItem
import com.sanket_satpute_20.ironmind.domain.model.EntitySource
import com.sanket_satpute_20.ironmind.domain.model.EventType
import com.sanket_satpute_20.ironmind.domain.model.Goal
import com.sanket_satpute_20.ironmind.domain.model.GoalStatus
import com.sanket_satpute_20.ironmind.domain.model.MemoryConfirmationState
import com.sanket_satpute_20.ironmind.domain.model.Plan
import com.sanket_satpute_20.ironmind.domain.model.PlanStatus
import com.sanket_satpute_20.ironmind.domain.model.ProgressInfo
import com.sanket_satpute_20.ironmind.domain.model.ProtectionSession
import com.sanket_satpute_20.ironmind.domain.model.ProtectionSessionStatus
import com.sanket_satpute_20.ironmind.domain.model.Task
import com.sanket_satpute_20.ironmind.domain.model.TaskStatus
import com.sanket_satpute_20.ironmind.domain.model.context.AppUsageInterval
import com.sanket_satpute_20.ironmind.domain.model.context.AppUsagePackageSummary
import com.sanket_satpute_20.ironmind.domain.model.context.AppUsageSummary
import com.sanket_satpute_20.ironmind.domain.model.context.AppUsageTemporalProvenance
import com.sanket_satpute_20.ironmind.domain.model.context.AppUsageTemporalSummary
import com.sanket_satpute_20.ironmind.domain.model.context.CommitmentContextSummary
import com.sanket_satpute_20.ironmind.domain.model.context.DigitalBehaviourContext
import com.sanket_satpute_20.ironmind.domain.model.context.EpistemicAuthority
import com.sanket_satpute_20.ironmind.domain.model.context.FocusActivitySummary
import com.sanket_satpute_20.ironmind.domain.model.context.FocusOverlapAvailability
import com.sanket_satpute_20.ironmind.domain.model.context.FocusProtectionOverlapSummary
import com.sanket_satpute_20.ironmind.domain.model.context.GoalContextItem
import com.sanket_satpute_20.ironmind.domain.model.context.PlanContextItem
import com.sanket_satpute_20.ironmind.domain.model.context.ProductivityContext
import com.sanket_satpute_20.ironmind.domain.model.context.RepeatedMissedCommitment
import com.sanket_satpute_20.ironmind.domain.model.context.SourceAvailability
import com.sanket_satpute_20.ironmind.domain.model.context.SourceProvenanceAndFreshness
import com.sanket_satpute_20.ironmind.domain.model.context.TaskContextSummary
import com.sanket_satpute_20.ironmind.domain.model.context.UnifiedContext
import com.sanket_satpute_20.ironmind.domain.model.context.UsagePermissionStatus
import com.sanket_satpute_20.ironmind.domain.model.pattern.EvidenceReference
import com.sanket_satpute_20.ironmind.domain.model.pattern.EvidenceSourceType
import com.sanket_satpute_20.ironmind.domain.model.pattern.Pattern
import com.sanket_satpute_20.ironmind.domain.model.pattern.PatternCategory
import com.sanket_satpute_20.ironmind.domain.model.pattern.PatternDiscoveryType
import com.sanket_satpute_20.ironmind.domain.model.pattern.PatternLifecycleState
import com.sanket_satpute_20.ironmind.domain.model.pattern.PatternStatus
import com.sanket_satpute_20.ironmind.domain.model.pattern.PatternType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class BehaviourPatternDiscoveryEngineTest {

    private val zoneId = ZoneId.of("UTC")
    private val userId = "user_test_42"
    private val baseTime = 1762473600000L // arbitrary fixed epoch (Nov 7, 2025)
    private val oneDayMs = 86_400_000L

    private lateinit var clock: FakeClock
    private lateinit var engine: BehaviourPatternDiscoveryEngine

    class FakeClock(var time: Long) : Clock {
        override fun currentTimeMillis() = time
    }

    @Before
    fun setUp() {
        clock = FakeClock(baseTime)
        engine = BehaviourPatternDiscoveryEngine(
            evidenceSufficiencyEvaluator = EvidenceSufficiencyEvaluator(),
            clock = clock
        )
    }

    private fun createTask(
        id: String,
        title: String = "Task $id",
        status: TaskStatus = TaskStatus.PENDING,
        priority: Int = 3,
        dueAt: Long? = null,
        createdAt: Long = baseTime,
        updatedAt: Long = baseTime,
        completedAt: Long? = null
    ): Task = Task(
        id = id,
        userId = userId,
        goalId = "g1",
        planId = "p1",
        title = title,
        description = "Task description",
        status = status,
        priority = priority,
        estimatedDurationMinutes = 30,
        dueAt = dueAt,
        createdAt = createdAt,
        updatedAt = updatedAt,
        completedAt = completedAt,
        source = EntitySource.USER
    )

    private fun createEmptyContext(
        targetUserId: String = userId,
        startTime: Long = baseTime - (7 * oneDayMs),
        endTime: Long = baseTime,
        permissionStatus: UsagePermissionStatus = UsagePermissionStatus.GRANTED,
        availability: SourceAvailability = SourceAvailability.AVAILABLE
    ): UnifiedContext {
        return UnifiedContext(
            userId = targetUserId,
            generatedAt = baseTime,
            windowStartTimeMs = startTime,
            windowEndTimeMs = endTime,
            productivity = ProductivityContext(),
            digitalBehaviour = DigitalBehaviourContext(
                permissionStatus = permissionStatus,
                availability = availability,
                windowStartTimeMs = startTime,
                windowEndTimeMs = endTime,
                appUsages = emptyList(),
                focusActivity = FocusActivitySummary()
            ),
            correlations = emptyList(),
            provenanceMap = mapOf(
                "Tasks" to SourceProvenanceAndFreshness("Tasks", SourceAvailability.AVAILABLE, true, baseTime, 0L, false),
                "Commitments" to SourceProvenanceAndFreshness("Commitments", SourceAvailability.AVAILABLE, true, baseTime, 0L, false),
                "AppUsage" to SourceProvenanceAndFreshness("AppUsage", availability, permissionStatus == UsagePermissionStatus.GRANTED, baseTime, 0L, false),
                "FocusProtection" to SourceProvenanceAndFreshness("FocusProtection", SourceAvailability.AVAILABLE, true, baseTime, 0L, false)
            ),
            isFresh = true,
            isDegraded = false
        )
    }

    // 1. Single observation does not establish a recurring pattern
    @Test
    fun testSingleObservation_doesNotEstablishRecurringPattern() {
        val singleTask = createTask(
            id = "task_postponed_1",
            title = "Single Postponed Task",
            status = TaskStatus.POSTPONED,
            priority = 3,
            dueAt = baseTime - 500L,
            createdAt = baseTime - 1000L,
            updatedAt = baseTime - 1000L
        )

        val context = createEmptyContext().copy(
            productivity = createEmptyContext().productivity.copy(
                taskSummary = TaskContextSummary(postponedTasks = listOf(singleTask))
            )
        )

        val patterns = engine.discoverPatterns(context, zoneId = zoneId)
        val postponedPattern = patterns.find { it.type == PatternDiscoveryType.REPEATEDLY_POSTPONED_TASKS }

        assertNotNull(postponedPattern)
        assertEquals(PatternLifecycleState.CANDIDATE, postponedPattern!!.lifecycleState)
        assertNotEquals(PatternLifecycleState.ESTABLISHED, postponedPattern.lifecycleState)
        assertEquals(BehaviourPatternDiscoveryEngine.CANDIDATE_CONFIDENCE, postponedPattern.confidence, 0.01f)
        assertEquals(1, postponedPattern.evidenceCount)
    }

    // 2. Repeated evidence advances pattern status
    @Test
    fun testRepeatedEvidence_advancesPatternStatus() {
        // N = 1 -> CANDIDATE
        val ev1 = listOf(EvidenceReference("e1", EvidenceSourceType.EVENT) to baseTime)
        val (state1, conf1) = engine.evaluateLifecycleStateAndConfidence(ev1, zoneId)
        assertEquals(PatternLifecycleState.CANDIDATE, state1)
        assertEquals(0.35f, conf1, 0.01f)

        // N = 2 on same day -> EMERGING
        val ev2 = listOf(
            EvidenceReference("e1", EvidenceSourceType.EVENT) to baseTime,
            EvidenceReference("e2", EvidenceSourceType.EVENT) to (baseTime + 3600_000L)
        )
        val (state2, conf2) = engine.evaluateLifecycleStateAndConfidence(ev2, zoneId)
        assertEquals(PatternLifecycleState.EMERGING, state2)
        assertEquals(0.60f, conf2, 0.01f)

        // N = 3 across 2 distinct calendar days -> ESTABLISHED
        val ev3 = listOf(
            EvidenceReference("e1", EvidenceSourceType.EVENT) to baseTime,
            EvidenceReference("e2", EvidenceSourceType.EVENT) to (baseTime + 3600_000L),
            EvidenceReference("e3", EvidenceSourceType.EVENT) to (baseTime + oneDayMs)
        )
        val (state3, conf3) = engine.evaluateLifecycleStateAndConfidence(ev3, zoneId)
        assertEquals(PatternLifecycleState.ESTABLISHED, state3)
        assertEquals(0.85f, conf3, 0.01f)
    }

    // 3. Insufficient evidence remains a candidate / emerging
    @Test
    fun testInsufficientEvidence_remainsEmergingWhenSameDay() {
        // 3 items but all on the EXACT SAME calendar day -> distinct days = 1 < 2 minimum
        val sameDayEvidence = listOf(
            EvidenceReference("t1", EvidenceSourceType.EVENT) to (baseTime + 1000L),
            EvidenceReference("t2", EvidenceSourceType.EVENT) to (baseTime + 2000L),
            EvidenceReference("t3", EvidenceSourceType.EVENT) to (baseTime + 3000L)
        )

        val (state, conf) = engine.evaluateLifecycleStateAndConfidence(sameDayEvidence, zoneId)
        assertEquals(PatternLifecycleState.EMERGING, state)
        assertNotEquals(PatternLifecycleState.ESTABLISHED, state)
        assertTrue(conf < 0.85f)
    }

    // 4. Missing Usage Access does not create zero-usage patterns
    @Test
    fun testMissingUsageAccess_doesNotCreateZeroUsagePatterns() {
        val context = createEmptyContext(
            permissionStatus = UsagePermissionStatus.DENIED,
            availability = SourceAvailability.PERMISSION_DENIED
        )

        val patterns = engine.discoverPatterns(context, zoneId = zoneId)

        // No digital behaviour patterns should be generated
        val digitalPatterns = patterns.filter { it.category == PatternCategory.DIGITAL_BEHAVIOUR }
        assertTrue("Expected 0 digital patterns when permission denied", digitalPatterns.isEmpty())
        assertFalse(patterns.any { it.description.contains("0 minutes", ignoreCase = true) })
    }

    // 5. Productivity patterns discovery
    @Test
    fun testProductivityPatterns_discovery() {
        // 3 postponed tasks across 2 days
        val t1 = createTask("p1", status = TaskStatus.POSTPONED, priority = 5, createdAt = baseTime, updatedAt = baseTime)
        val t2 = createTask("p2", status = TaskStatus.POSTPONED, priority = 5, createdAt = baseTime, updatedAt = baseTime + 1000L)
        val t3 = createTask("p3", status = TaskStatus.POSTPONED, priority = 5, createdAt = baseTime, updatedAt = baseTime + oneDayMs)

        // 3 completed tasks in morning (09:00 UTC) across 2 days
        val morningHourMs = 9 * 3600_000L
        val c1 = createTask("c1", status = TaskStatus.COMPLETED, priority = 5, createdAt = baseTime, updatedAt = baseTime + morningHourMs, completedAt = baseTime + morningHourMs)
        val c2 = createTask("c2", status = TaskStatus.COMPLETED, priority = 5, createdAt = baseTime, updatedAt = baseTime + morningHourMs + 1000L, completedAt = baseTime + morningHourMs + 1000L)
        val c3 = createTask("c3", status = TaskStatus.COMPLETED, priority = 5, createdAt = baseTime, updatedAt = baseTime + oneDayMs + morningHourMs, completedAt = baseTime + oneDayMs + morningHourMs)

        // Repeated missed commitment
        val rmc = RepeatedMissedCommitment("Daily Workout", "task_missed", "plan1", "g1", 3, baseTime + oneDayMs, listOf("Missed Monday", "Missed Tuesday", "Missed Wednesday"))

        // Active goal with progress
        val goal = Goal("g1", userId, null, "Fitness Goal", "Workout routine", "Health", 8, GoalStatus.ACTIVE, createdAt = baseTime, updatedAt = baseTime)
        val goalContextItem = GoalContextItem(goal, ProgressInfo(completedTasks = 3, totalTasks = 5, pendingTasks = 2, percentage = 60))

        val context = createEmptyContext().copy(
            productivity = createEmptyContext().productivity.copy(
                taskSummary = TaskContextSummary(
                    postponedTasks = listOf(t1, t2, t3),
                    completedTasks = listOf(c1, c2, c3),
                    overdueTasks = listOf(DueTaskItem(t1, "Plan 1", true))
                ),
                commitmentSummary = CommitmentContextSummary(
                    repeatedMissedCommitments = listOf(rmc)
                ),
                activeGoals = listOf(goalContextItem)
            )
        )

        val patterns = engine.discoverPatterns(context, zoneId = zoneId)

        val postponedPat = patterns.find { it.type == PatternDiscoveryType.REPEATEDLY_POSTPONED_TASKS }
        assertNotNull(postponedPat)
        assertEquals(PatternLifecycleState.ESTABLISHED, postponedPat!!.lifecycleState)

        val missedPat = patterns.find { it.type == PatternDiscoveryType.REPEATEDLY_MISSED_COMMITMENTS }
        assertNotNull(missedPat)

        val todPat = patterns.find { it.type == PatternDiscoveryType.TASK_COMPLETION_BY_TIME_OF_DAY }
        assertNotNull(todPat)
        assertTrue(todPat!!.description.contains("Morning", ignoreCase = true))

        val priorityPat = patterns.find { it.type == PatternDiscoveryType.TASK_COMPLETION_BY_PRIORITY }
        assertNotNull(priorityPat)
        assertTrue(priorityPat!!.description.contains("HIGH", ignoreCase = true))

        val goalPat = patterns.find { it.type == PatternDiscoveryType.GOAL_EXECUTION_CONSISTENCY }
        assertNotNull(goalPat)
    }

    // 6. Digital usage patterns when permission granted
    @Test
    fun testDigitalUsagePatterns_whenPermissionGranted() {
        val appUsage1 = AppUsageSummary(
            packageName = "com.android.chrome",
            appName = "Chrome",
            totalDurationMs = 45 * 60 * 1000L, // 45 minutes
            sessionCount = 8,
            firstObservedAt = baseTime,
            lastObservedAt = baseTime + 3600_000L
        )
        val appUsage2 = AppUsageSummary(
            packageName = "com.social.media",
            appName = "Social",
            totalDurationMs = 25 * 60 * 1000L, // 25 minutes
            sessionCount = 6,
            firstObservedAt = baseTime,
            lastObservedAt = baseTime + 3600_000L
        )

        val context = createEmptyContext().copy(
            digitalBehaviour = DigitalBehaviourContext(
                permissionStatus = UsagePermissionStatus.GRANTED,
                availability = SourceAvailability.AVAILABLE,
                appUsages = listOf(appUsage1, appUsage2),
                focusActivity = FocusActivitySummary()
            )
        )

        val patterns = engine.discoverPatterns(context, zoneId = zoneId)

        val freqAppPat = patterns.find { it.type == PatternDiscoveryType.FREQUENTLY_USED_APPLICATIONS }
        assertNotNull(freqAppPat)
        assertEquals(PatternCategory.DIGITAL_BEHAVIOUR, freqAppPat!!.category)

        val highUsagePat = patterns.find { it.type == PatternDiscoveryType.HIGH_USAGE_PERIODS }
        assertNotNull(highUsagePat) // 45 + 25 = 70 min >= 60 min threshold

        val sessionPat = patterns.find { it.type == PatternDiscoveryType.REPEATED_APP_USAGE_SESSIONS }
        assertNotNull(sessionPat) // sessionCount 8 >= 5
    }

    // 7. Cross-domain non-causal patterns
    @Test
    fun testCrossDomainNonCausalPatterns_strictAuthorityAndDisclaimer() {
        val pendingTask = createTask("t_pend", title = "Pending Report", status = TaskStatus.PENDING, priority = 4)
        val appUsage = AppUsageSummary("com.video.stream", null, 20 * 60 * 1000L, 2, baseTime, baseTime + 1000L)

        val context = createEmptyContext().copy(
            productivity = createEmptyContext().productivity.copy(
                taskSummary = TaskContextSummary(pendingTasks = listOf(pendingTask))
            ),
            digitalBehaviour = DigitalBehaviourContext(
                permissionStatus = UsagePermissionStatus.GRANTED,
                availability = SourceAvailability.AVAILABLE,
                appUsages = listOf(appUsage),
                focusActivity = FocusActivitySummary()
            )
        )

        val patterns = engine.discoverPatterns(context, zoneId = zoneId)

        val crossPat = patterns.find { it.type == PatternDiscoveryType.DIGITAL_USAGE_DURING_PLANNED_WORK }
        assertNotNull(crossPat)
        assertEquals(PatternCategory.CROSS_DOMAIN, crossPat!!.category)
        assertEquals(EpistemicAuthority.AI_HYPOTHESIS, crossPat.epistemicAuthority)
        assertTrue(crossPat.confidence <= 0.75f)
        assertNotNull(crossPat.nonCausalDisclaimer)
        assertTrue(crossPat.nonCausalDisclaimer!!.contains("Does not imply causation, intent, or confirmed distraction"))
    }

    // 8. Pattern weakening and inactivity
    @Test
    fun testPatternWeakeningAndInactivity() {
        val existingPattern = Pattern(
            id = "pat_prod_postponed_hist",
            userId = userId,
            fingerprint = "${userId}_PRODUCTIVITY_REPEATEDLY_POSTPONED_TASKS_repeated task postponements",
            type = PatternType.POSTPONEMENT_PATTERN,
            description = "Repeated task postponements observed across 5 tasks",
            conditions = null,
            predictedBehavior = null,
            confidence = 0.85f,
            evidenceCount = 5,
            evidenceReferences = emptyList(),
            firstObservedAt = baseTime - (20 * oneDayMs),
            lastObservedAt = baseTime - (5 * oneDayMs), // 5 days ago (< 14 days)
            status = PatternStatus.ACTIVE,
            confirmationState = MemoryConfirmationState.UNCONFIRMED,
            createdAt = baseTime - (20 * oneDayMs),
            updatedAt = baseTime - (5 * oneDayMs)
        )

        // Context with ZERO postponed tasks in current window
        val emptyContext = createEmptyContext()

        // Case A: 5 days ago (< 14 days threshold) -> WEAKENING
        clock.time = baseTime
        val patternsWeakening = engine.discoverPatterns(emptyContext, existingPatterns = listOf(existingPattern), zoneId = zoneId)
        val weakeningPat = patternsWeakening.find { it.id == existingPattern.id }
        assertNotNull(weakeningPat)
        assertEquals(PatternLifecycleState.WEAKENING, weakeningPat!!.lifecycleState)
        assertTrue(weakeningPat.confidence < existingPattern.confidence)

        // Case B: 25 days ago (> 14 days threshold) -> INACTIVE
        val staleExistingPattern = existingPattern.copy(lastObservedAt = baseTime - (25 * oneDayMs))
        val patternsInactive = engine.discoverPatterns(emptyContext, existingPatterns = listOf(staleExistingPattern), zoneId = zoneId)
        val inactivePat = patternsInactive.find { it.id == staleExistingPattern.id }
        assertNotNull(inactivePat)
        assertEquals(PatternLifecycleState.INACTIVE, inactivePat!!.lifecycleState)
        assertEquals(BehaviourPatternDiscoveryEngine.INACTIVE_CONFIDENCE, inactivePat.confidence, 0.01f)
    }

    // 9. Evidence provenance preservation
    @Test
    fun testEvidenceProvenancePreservation() {
        val task = createTask("t_prov", status = TaskStatus.POSTPONED, priority = 3)
        val context = createEmptyContext().copy(
            productivity = createEmptyContext().productivity.copy(
                taskSummary = TaskContextSummary(postponedTasks = listOf(task))
            )
        )

        val patterns = engine.discoverPatterns(context, zoneId = zoneId)
        val pat = patterns.find { it.type == PatternDiscoveryType.REPEATEDLY_POSTPONED_TASKS }

        assertNotNull(pat)
        assertEquals(listOf("Tasks", "Events"), pat!!.sourceProvenance)
        assertEquals(context.windowStartTimeMs, pat.observationWindow.startTimeMs)
        assertEquals(context.windowEndTimeMs, pat.observationWindow.endTimeMs)
        assertEquals("t_prov", pat.evidenceReferences.first().sourceId)
        assertEquals(EvidenceSourceType.EVENT, pat.evidenceReferences.first().sourceType)
    }

    // 10. User ownership isolation
    @Test
    fun testUserOwnershipIsolation() {
        val bobPattern = Pattern(
            id = "pat_bob",
            userId = "user_bob",
            fingerprint = "user_bob_PRODUCTIVITY_REPEATEDLY_POSTPONED_TASKS_bob",
            type = PatternType.POSTPONEMENT_PATTERN,
            description = "Bob postponed tasks",
            conditions = null,
            predictedBehavior = null,
            confidence = 0.85f,
            evidenceCount = 5,
            evidenceReferences = emptyList(),
            firstObservedAt = baseTime,
            lastObservedAt = baseTime,
            status = PatternStatus.ACTIVE,
            confirmationState = MemoryConfirmationState.UNCONFIRMED,
            createdAt = baseTime,
            updatedAt = baseTime
        )

        val aliceContext = createEmptyContext(targetUserId = "user_alice")
        val patterns = engine.discoverPatterns(aliceContext, existingPatterns = listOf(bobPattern), zoneId = zoneId)

        // All discovered patterns must belong to Alice; Bob's pattern must be completely excluded
        assertTrue(patterns.all { it.userId == "user_alice" })
        assertFalse(patterns.any { it.id == "pat_bob" })
    }

    // 11. Duplicate evidence prevention
    @Test
    fun testDuplicateEvidencePrevention() {
        // Raw evidence with duplicates
        val duplicateRawEvidence = listOf(
            EvidenceReference("t1", EvidenceSourceType.EVENT) to baseTime,
            EvidenceReference("t1", EvidenceSourceType.EVENT) to baseTime, // duplicate
            EvidenceReference("t2", EvidenceSourceType.EVENT) to (baseTime + oneDayMs)
        )

        val (state, conf) = engine.evaluateLifecycleStateAndConfidence(duplicateRawEvidence, zoneId)
        // Distinct items = 2, so EMERGING, NOT ESTABLISHED
        assertEquals(PatternLifecycleState.EMERGING, state)
        assertEquals(0.60f, conf, 0.01f)
    }

    // 12. Stale source data handling
    @Test
    fun testStaleSourceDataHandling_capsConfidenceAndFlagsMetadata() {
        val t1 = createTask("p1", status = TaskStatus.POSTPONED, priority = 4, createdAt = baseTime, updatedAt = baseTime)
        val t2 = createTask("p2", status = TaskStatus.POSTPONED, priority = 4, createdAt = baseTime, updatedAt = baseTime + 1000L)
        val t3 = createTask("p3", status = TaskStatus.POSTPONED, priority = 4, createdAt = baseTime, updatedAt = baseTime + oneDayMs)

        // Mark Tasks source as stale (> 7 days old)
        val staleProvenance = mapOf(
            "Tasks" to SourceProvenanceAndFreshness("Tasks", SourceAvailability.AVAILABLE, true, baseTime - (10 * oneDayMs), 10 * oneDayMs, true)
        )

        val context = createEmptyContext().copy(
            productivity = createEmptyContext().productivity.copy(
                taskSummary = TaskContextSummary(postponedTasks = listOf(t1, t2, t3))
            ),
            provenanceMap = staleProvenance,
            isFresh = false
        )

        val patterns = engine.discoverPatterns(context, zoneId = zoneId)
        val pat = patterns.find { it.type == PatternDiscoveryType.REPEATEDLY_POSTPONED_TASKS }

        assertNotNull(pat)
        assertEquals("true", pat!!.metadata["isSourceStale"])
        // Confidence should be capped at STALE_CONFIDENCE_CAP (0.50f) despite meeting count requirements
        assertTrue(pat.confidence <= BehaviourPatternDiscoveryEngine.STALE_CONFIDENCE_CAP)
    }

    // 13. Focus session pattern verification
    @Test
    fun testFocusPatterns_frequencyAndConsistency() {
        val session1 = ProtectionSession(
            id = "sess1",
            userId = userId,
            startedAt = baseTime,
            endedAt = baseTime + 25 * 60 * 1000L,
            status = ProtectionSessionStatus.COMPLETED,
            source = EntitySource.USER,
            overrideAllowed = true,
            createdAt = baseTime,
            updatedAt = baseTime + 25 * 60 * 1000L
        )
        val session2 = ProtectionSession(
            id = "sess2",
            userId = userId,
            startedAt = baseTime + oneDayMs,
            endedAt = baseTime + oneDayMs + 30 * 60 * 1000L,
            status = ProtectionSessionStatus.COMPLETED,
            source = EntitySource.USER,
            overrideAllowed = true,
            createdAt = baseTime + oneDayMs,
            updatedAt = baseTime + oneDayMs + 30 * 60 * 1000L
        )

        val context = createEmptyContext().copy(
            digitalBehaviour = DigitalBehaviourContext(
                permissionStatus = UsagePermissionStatus.GRANTED,
                availability = SourceAvailability.AVAILABLE,
                appUsages = emptyList(),
                focusActivity = FocusActivitySummary(
                    activeSessions = emptyList(),
                    recentSessions = listOf(session1, session2),
                    totalFocusDurationMs = 55 * 60 * 1000L,
                    activeRules = emptyList()
                )
            )
        )

        val patterns = engine.discoverPatterns(context, zoneId = zoneId)
        val freqPat = patterns.find { it.type == PatternDiscoveryType.FOCUS_SESSION_FREQUENCY_AND_DURATION }
        assertNotNull(freqPat)
        assertEquals(PatternCategory.FOCUS, freqPat!!.category)

        val consistencyPat = patterns.find { it.type == PatternDiscoveryType.FOCUS_CONSISTENCY_OVER_TIME }
        assertNotNull(consistencyPat)
    }

    // =========================================================================
    // Phase 3.6.7.8B.1 — App Usage Interval-Based Pattern Intelligence Tests
    // =========================================================================

    private fun createInterval(
        obsId: String,
        packageName: String,
        startMs: Long,
        endMs: Long,
        targetUserId: String = userId,
        rawStartMs: Long = startMs,
        rawEndMs: Long = endMs,
        isClipped: Boolean = false
    ): AppUsageInterval = AppUsageInterval(
        observationId = obsId,
        userId = targetUserId,
        packageName = packageName,
        rawStartTimeMs = rawStartMs,
        rawEndTimeMs = rawEndMs,
        rawDurationMs = rawEndMs - rawStartMs,
        startTimeMs = startMs,
        endTimeMs = endMs,
        durationMs = endMs - startMs,
        isClippedToWindow = isClipped,
        recordedAt = endMs
    )

    // 1. Multiple genuine intervals from the same package contribute distinct evidence
    @Test
    fun testMultipleGenuineIntervalsSamePackage_contributeDistinctEvidenceAndAdvanceSufficiency() {
        val day1Start = ZonedDateTime.of(2025, 11, 5, 9, 0, 0, 0, zoneId).toInstant().toEpochMilli()
        val day2Start = ZonedDateTime.of(2025, 11, 6, 10, 0, 0, 0, zoneId).toInstant().toEpochMilli()
        val day3Start = ZonedDateTime.of(2025, 11, 7, 11, 0, 0, 0, zoneId).toInstant().toEpochMilli()

        val intervals = listOf(
            createInterval("obs-1", "com.social.app", day1Start, day1Start + 10 * 60_000L),
            createInterval("obs-2", "com.social.app", day1Start + 3_600_000L, day1Start + 3_600_000L + 10 * 60_000L),
            createInterval("obs-3", "com.social.app", day2Start, day2Start + 10 * 60_000L),
            createInterval("obs-4", "com.social.app", day2Start + 3_600_000L, day2Start + 3_600_000L + 10 * 60_000L),
            createInterval("obs-5", "com.social.app", day3Start, day3Start + 10 * 60_000L)
        )

        val windowStart = day1Start - 3_600_000L
        val windowEnd = day3Start + 7_200_000L
        val baseCtx = createEmptyContext(startTime = windowStart, endTime = windowEnd)
        val context = baseCtx.copy(
            digitalBehaviour = baseCtx.digitalBehaviour.copy(
                usageIntervals = intervals,
                usageTemporalSummary = AppUsageTemporalSummary(
                    hasUsageData = true,
                    windowStartTimeMs = windowStart,
                    windowEndTimeMs = windowEnd,
                    effectiveStartTimeMs = windowStart,
                    totalIntervalCount = intervals.size,
                    provenance = AppUsageTemporalProvenance(usageSourceAvailability = SourceAvailability.AVAILABLE)
                )
            )
        )

        val patterns = engine.discoverPatterns(context, zoneId = zoneId)
        val freqPat = patterns.find { it.type == PatternDiscoveryType.FREQUENTLY_USED_APPLICATIONS }
        val repeatPat = patterns.find { it.type == PatternDiscoveryType.REPEATED_APP_USAGE_SESSIONS }

        assertNotNull(freqPat)
        assertEquals(PatternLifecycleState.ESTABLISHED, freqPat!!.lifecycleState)
        assertEquals(5, freqPat.evidenceCount)
        assertEquals(setOf("obs-1", "obs-2", "obs-3", "obs-4", "obs-5"), freqPat.evidenceReferences.map { it.sourceId }.toSet())
        assertTrue(freqPat.evidenceReferences.all { it.sourceType == EvidenceSourceType.OBSERVATION })
        assertEquals("usage_intervals", freqPat.metadata["evidenceBasis"])

        assertNotNull(repeatPat)
        assertEquals(PatternLifecycleState.ESTABLISHED, repeatPat!!.lifecycleState)
        assertEquals(5, repeatPat.evidenceCount)
    }

    // 2. Duplicate observations do not inflate sample counts or unioned durations
    @Test
    fun testDuplicateIntervalObservations_doNotInflateSampleCounts() {
        val day1Morning = ZonedDateTime.of(2025, 11, 5, 9, 0, 0, 0, zoneId).toInstant().toEpochMilli()
        val day2Morning = ZonedDateTime.of(2025, 11, 6, 9, 0, 0, 0, zoneId).toInstant().toEpochMilli()

        val intervals = listOf(
            createInterval("obs-dup-id", "com.video.app", day1Morning, day1Morning + 20 * 60_000L),
            // Exact duplicate observationId
            createInterval("obs-dup-id", "com.video.app", day1Morning, day1Morning + 20 * 60_000L),
            // Different ID but exact semantic duplicate (same package, rawStart, rawEnd)
            createInterval("obs-semantic-copy", "com.video.app", day1Morning, day1Morning + 20 * 60_000L),
            // Second genuine observation on Day 2
            createInterval("obs-genuine-2", "com.video.app", day2Morning, day2Morning + 20 * 60_000L)
        )

        val windowStart = day1Morning - 3_600_000L
        val windowEnd = day2Morning + 7_200_000L
        val baseCtx = createEmptyContext(startTime = windowStart, endTime = windowEnd)
        val context = baseCtx.copy(
            digitalBehaviour = baseCtx.digitalBehaviour.copy(usageIntervals = intervals)
        )

        val patterns = engine.discoverPatterns(context, zoneId = zoneId)
        val freqPat = patterns.find { it.type == PatternDiscoveryType.FREQUENTLY_USED_APPLICATIONS }

        assertNotNull(freqPat)
        assertEquals(2, freqPat!!.evidenceCount)
        // Must remain EMERGING (N=2 < 3 minimum), NOT ESTABLISHED from duplicates
        assertEquals(PatternLifecycleState.EMERGING, freqPat.lifecycleState)
        assertEquals("2400000", freqPat.metadata["durationMs"]) // 40m, not 80m
        assertNull(patterns.find { it.type == PatternDiscoveryType.REPEATED_APP_USAGE_SESSIONS })
    }

    // 3. Single-day evidence does not bypass multi-day sufficiency
    @Test
    fun testSingleDayMultipleIntervals_neverReachesEstablished() {
        val dayStart = ZonedDateTime.of(2025, 11, 7, 8, 0, 0, 0, zoneId).toInstant().toEpochMilli()
        // 5 genuine intervals (>= 5 threshold for B.3) all within the same UTC calendar day
        val sameDayIntervals = (0 until 5).map { idx ->
            val start = dayStart + idx * 3_600_000L
            createInterval("obs-same-$idx", "com.chat.app", start, start + 5 * 60_000L)
        }

        val windowStart = dayStart - 3_600_000L
        val windowEnd = dayStart + 10 * 3_600_000L
        val baseCtx = createEmptyContext(startTime = windowStart, endTime = windowEnd)
        val context = baseCtx.copy(
            digitalBehaviour = baseCtx.digitalBehaviour.copy(usageIntervals = sameDayIntervals)
        )

        val patterns = engine.discoverPatterns(context, zoneId = zoneId)
        val freqPat = patterns.find { it.type == PatternDiscoveryType.FREQUENTLY_USED_APPLICATIONS }
        val repeatPat = patterns.find { it.type == PatternDiscoveryType.REPEATED_APP_USAGE_SESSIONS }

        assertNotNull(freqPat)
        assertEquals(5, freqPat!!.evidenceCount)
        assertEquals(PatternLifecycleState.EMERGING, freqPat.lifecycleState)
        assertNotEquals(PatternLifecycleState.ESTABLISHED, freqPat.lifecycleState)

        assertNotNull(repeatPat)
        assertEquals(5, repeatPat!!.evidenceCount)
        assertEquals(PatternLifecycleState.EMERGING, repeatPat.lifecycleState)
        assertNotEquals(PatternLifecycleState.ESTABLISHED, repeatPat.lifecycleState)
    }

    // 4. Morning and night usage from the same package are attributed to their respective buckets
    @Test
    fun testSamePackageMorningAndNightUsage_attributedToRespectiveBucketsNotLastObservedAt() {
        val day1Morning = ZonedDateTime.of(2025, 11, 5, 8, 0, 0, 0, zoneId).toInstant().toEpochMilli()
        val day1Night = ZonedDateTime.of(2025, 11, 5, 23, 0, 0, 0, zoneId).toInstant().toEpochMilli()
        val day2Morning = ZonedDateTime.of(2025, 11, 6, 9, 0, 0, 0, zoneId).toInstant().toEpochMilli()
        val day2Morning2 = ZonedDateTime.of(2025, 11, 6, 10, 0, 0, 0, zoneId).toInstant().toEpochMilli()
        val day2Night = ZonedDateTime.of(2025, 11, 6, 23, 30, 0, 0, zoneId).toInstant().toEpochMilli()

        // Same package used 90m in Morning (3 intervals across 2 days) and 10m at Night (lastObservedAt is Night!)
        val intervals = listOf(
            createInterval("obs-m1", "com.reader.app", day1Morning, day1Morning + 30 * 60_000L),
            createInterval("obs-n1", "com.reader.app", day1Night, day1Night + 5 * 60_000L),
            createInterval("obs-m2", "com.reader.app", day2Morning, day2Morning + 30 * 60_000L),
            createInterval("obs-m3", "com.reader.app", day2Morning2, day2Morning2 + 30 * 60_000L),
            createInterval("obs-n2", "com.reader.app", day2Night, day2Night + 5 * 60_000L)
        )

        val windowStart = day1Morning - 3_600_000L
        val windowEnd = day2Night + 3_600_000L
        val baseCtx = createEmptyContext(startTime = windowStart, endTime = windowEnd)
        // Even if appUsages also exists with lastObservedAt = day2Night, usageIntervals must govern!
        val summaryWithNightLast = AppUsageSummary(
            packageName = "com.reader.app",
            totalDurationMs = 100 * 60_000L,
            sessionCount = 5,
            firstObservedAt = day1Morning,
            lastObservedAt = day2Night + 5 * 60_000L
        )
        val context = baseCtx.copy(
            digitalBehaviour = baseCtx.digitalBehaviour.copy(
                appUsages = listOf(summaryWithNightLast),
                usageIntervals = intervals
            )
        )

        val patterns = engine.discoverPatterns(context, zoneId = zoneId)
        val todPat = patterns.find { it.type == PatternDiscoveryType.USAGE_DISTRIBUTION_ACROSS_TIME_WINDOWS }

        assertNotNull(todPat)
        assertEquals("Morning", todPat!!.metadata["timeWindow"])
        assertEquals(PatternLifecycleState.ESTABLISHED, todPat.lifecycleState)
        assertEquals(setOf("obs-m1", "obs-m2", "obs-m3"), todPat.evidenceReferences.map { it.sourceId }.toSet())
        // Both Morning and Night buckets carry exact separate attribution for the same package
        assertEquals((90 * 60_000L).toString(), todPat.metadata["morningDurationMs"])
        assertEquals("3", todPat.metadata["morningEvidenceCount"])
        assertEquals((10 * 60_000L).toString(), todPat.metadata["nightDurationMs"])
        assertEquals("2", todPat.metadata["nightEvidenceCount"])
    }

    // 5. Boundary-crossing intervals are classified consistently with the existing bucket contract
    @Test
    fun testBoundaryCrossingIntervals_classifiedConsistentlyWithBucketContract() {
        // 11:50 -> 12:35 spans 10m in Morning (06..11) and 35m in Afternoon (12..16)
        val crossMorningStart = ZonedDateTime.of(2025, 11, 6, 11, 50, 0, 0, zoneId).toInstant().toEpochMilli()
        val crossMorningEnd = ZonedDateTime.of(2025, 11, 6, 12, 35, 0, 0, zoneId).toInstant().toEpochMilli()
        // 23:45 Day 1 -> 00:15 Day 2 stays entirely in Night (22..23 & 00..05) without double-counting obs ID
        val crossMidnightStart = ZonedDateTime.of(2025, 11, 6, 23, 45, 0, 0, zoneId).toInstant().toEpochMilli()
        val crossMidnightEnd = ZonedDateTime.of(2025, 11, 7, 0, 15, 0, 0, zoneId).toInstant().toEpochMilli()

        val intervals = listOf(
            createInterval("obs-boundary-noon", "com.browser.app", crossMorningStart, crossMorningEnd),
            createInterval("obs-boundary-mid", "com.browser.app", crossMidnightStart, crossMidnightEnd)
        )

        val windowStart = crossMorningStart - 3_600_000L
        val windowEnd = crossMidnightEnd + 3_600_000L
        val baseCtx = createEmptyContext(startTime = windowStart, endTime = windowEnd)
        val context = baseCtx.copy(
            digitalBehaviour = baseCtx.digitalBehaviour.copy(usageIntervals = intervals)
        )

        val patterns = engine.discoverPatterns(context, zoneId = zoneId)
        val todPat = patterns.find { it.type == PatternDiscoveryType.USAGE_DISTRIBUTION_ACROSS_TIME_WINDOWS }

        assertNotNull(todPat)
        // Afternoon (35m) > Night (30m) > Morning (10m)
        assertEquals("Afternoon", todPat!!.metadata["timeWindow"])
        assertEquals((10 * 60_000L).toString(), todPat.metadata["morningDurationMs"])
        assertEquals((35 * 60_000L).toString(), todPat.metadata["afternoonDurationMs"])
        assertEquals((30 * 60_000L).toString(), todPat.metadata["nightDurationMs"])
        // Single midnight-crossing observation counts as 1 Night evidence item, not 2
        assertEquals("1", todPat.metadata["nightEvidenceCount"])
    }

    // 6. Intervals outside the requested window do not contribute
    @Test
    fun testIntervalsOutsideRequestedWindow_excludedAndBoundaryIntervalsClipped() {
        val winStart = ZonedDateTime.of(2025, 11, 6, 10, 0, 0, 0, zoneId).toInstant().toEpochMilli()
        val winEnd = ZonedDateTime.of(2025, 11, 6, 18, 0, 0, 0, zoneId).toInstant().toEpochMilli()

        val strictlyBefore = createInterval("obs-before", "com.out.app", winStart - 7_200_000L, winStart - 3_600_000L)
        val strictlyAfter = createInterval("obs-after", "com.out.app", winEnd + 3_600_000L, winEnd + 7_200_000L)

        // All intervals outside window -> must NOT leak via legacy appUsages
        val baseCtx = createEmptyContext(startTime = winStart, endTime = winEnd)
        val outOnlyCtx = baseCtx.copy(
            digitalBehaviour = baseCtx.digitalBehaviour.copy(
                appUsages = listOf(AppUsageSummary("com.out.app", totalDurationMs = 90 * 60_000L, sessionCount = 6, firstObservedAt = winStart, lastObservedAt = winStart + 1000L)),
                usageIntervals = listOf(strictlyBefore, strictlyAfter)
            )
        )
        val outPatterns = engine.discoverPatterns(outOnlyCtx, zoneId = zoneId)
        assertTrue(outPatterns.none { it.category == PatternCategory.DIGITAL_BEHAVIOUR })

        // Boundary-overlapping interval [09:00..10:20] clipped to [10:00..10:20] = 20m (not 80m!)
        val straddling = createInterval("obs-straddle", "com.clip.app", winStart - 3_600_000L, winStart + 20 * 60_000L)
        val clipCtx = baseCtx.copy(
            digitalBehaviour = baseCtx.digitalBehaviour.copy(usageIntervals = listOf(strictlyBefore, straddling, strictlyAfter))
        )
        val clipPatterns = engine.discoverPatterns(clipCtx, zoneId = zoneId)
        val freqPat = clipPatterns.find { it.type == PatternDiscoveryType.FREQUENTLY_USED_APPLICATIONS }
        assertNotNull(freqPat)
        assertEquals(listOf("obs-straddle"), freqPat!!.evidenceReferences.map { it.sourceId })
        assertEquals((20 * 60_000L).toString(), freqPat.metadata["durationMs"])
        assertNull(clipPatterns.find { it.type == PatternDiscoveryType.HIGH_USAGE_PERIODS })
    }

    // 7. Partial history does not become complete history
    @Test
    fun testPartialHistory_preservedInMetadataAndEmptyPartialProducesZeroPatterns() {
        val baseCtx = createEmptyContext(availability = SourceAvailability.PARTIAL_HISTORY)
        val emptyPartialPatterns = engine.discoverPatterns(baseCtx, zoneId = zoneId)
        assertTrue(emptyPartialPatterns.none { it.category == PatternCategory.DIGITAL_BEHAVIOUR })

        val day1 = ZonedDateTime.of(2025, 11, 5, 9, 0, 0, 0, zoneId).toInstant().toEpochMilli()
        val day2 = ZonedDateTime.of(2025, 11, 6, 9, 0, 0, 0, zoneId).toInstant().toEpochMilli()
        val intervals = listOf(
            createInterval("obs-p1", "com.news.app", day1, day1 + 10 * 60_000L),
            createInterval("obs-p2", "com.news.app", day1 + 3_600_000L, day1 + 3_600_000L + 10 * 60_000L),
            createInterval("obs-p3", "com.news.app", day2, day2 + 10 * 60_000L)
        )
        val populatedPartialCtx = baseCtx.copy(
            digitalBehaviour = baseCtx.digitalBehaviour.copy(
                usageIntervals = intervals,
                usageTemporalSummary = AppUsageTemporalSummary(
                    hasUsageData = true,
                    totalIntervalCount = intervals.size,
                    provenance = AppUsageTemporalProvenance(usageSourceAvailability = SourceAvailability.PARTIAL_HISTORY)
                )
            )
        )
        val partialPatterns = engine.discoverPatterns(populatedPartialCtx, zoneId = zoneId)
            .filter { it.category == PatternCategory.DIGITAL_BEHAVIOUR }
        assertTrue(partialPatterns.isNotEmpty())
        partialPatterns.forEach { pat ->
            assertEquals("PARTIAL_HISTORY", pat.metadata["usageAvailability"])
            assertEquals("true", pat.metadata["isPartialHistory"])
            assertNotEquals("AVAILABLE", pat.metadata["usageAvailability"])
        }
    }

    // 8. Unavailable focus overlap remains distinct from genuine zero overlap
    @Test
    fun testUnavailableFocusOverlap_remainsDistinctFromZeroOverlap() {
        val day1 = ZonedDateTime.of(2025, 11, 5, 10, 0, 0, 0, zoneId).toInstant().toEpochMilli()
        val day2 = ZonedDateTime.of(2025, 11, 6, 10, 0, 0, 0, zoneId).toInstant().toEpochMilli()
        val intervals = listOf(
            createInterval("obs-u1", "com.social.app", day1, day1 + 20 * 60_000L),
            createInterval("obs-u2", "com.social.app", day1 + 3_600_000L, day1 + 3_600_000L + 10 * 60_000L),
            createInterval("obs-u3", "com.social.app", day2, day2 + 20 * 60_000L)
        )
        val winStart = day1 - 3_600_000L
        val winEnd = day2 + 7_200_000L
        val leakedOrRealSession = ProtectionSession(
            id = "sess-overlap",
            userId = userId,
            startedAt = day1 - 600_000L,
            scheduledEndAt = day1 + 30 * 60_000L,
            endedAt = day1 + 30 * 60_000L,
            status = ProtectionSessionStatus.COMPLETED,
            source = EntitySource.USER,
            overrideAllowed = true,
            createdAt = day1 - 600_000L,
            updatedAt = day1 + 30 * 60_000L
        )

        val baseCtx = createEmptyContext(startTime = winStart, endTime = winEnd)
        val unavailableCtx = baseCtx.copy(
            provenanceMap = baseCtx.provenanceMap + (
                "FocusProtection" to SourceProvenanceAndFreshness(
                    sourceName = "Room_Protection",
                    availability = SourceAvailability.UNAVAILABLE_HARDWARE,
                    isPermissionGranted = true,
                    lastRecordedAt = null,
                    dataAgeMs = null,
                    isStale = false
                )
                ),
            digitalBehaviour = baseCtx.digitalBehaviour.copy(
                usageIntervals = intervals,
                focusActivity = FocusActivitySummary(recentSessions = listOf(leakedOrRealSession)),
                usageTemporalSummary = AppUsageTemporalSummary(
                    hasUsageData = true,
                    focusOverlap = FocusProtectionOverlapSummary(
                        availability = FocusOverlapAvailability.UNAVAILABLE,
                        overlappingUsageDurationMs = null,
                        notes = "Protection session history unavailable"
                    ),
                    provenance = AppUsageTemporalProvenance(
                        usageSourceAvailability = SourceAvailability.AVAILABLE,
                        isProtectionHistoryAvailable = false
                    )
                )
            )
        )

        val unavailablePatterns = engine.discoverPatterns(unavailableCtx, zoneId = zoneId)
        assertNull(unavailablePatterns.find { it.type == PatternDiscoveryType.USAGE_DURING_SCHEDULED_FOCUS })
        val unavailableFreqPat = unavailablePatterns.find { it.type == PatternDiscoveryType.FREQUENTLY_USED_APPLICATIONS }
        assertNotNull(unavailableFreqPat)
        assertEquals("UNAVAILABLE", unavailableFreqPat!!.metadata["focusOverlapAvailability"])

        // Genuine zero overlap (protection available, usage outside session)
        val zeroOverlapSession = leakedOrRealSession.copy(
            id = "sess-non-overlapping",
            startedAt = day1 - 7_200_000L,
            scheduledEndAt = day1 - 5_400_000L,
            endedAt = day1 - 5_400_000L
        )
        val zeroOverlapCtx = baseCtx.copy(
            digitalBehaviour = baseCtx.digitalBehaviour.copy(
                usageIntervals = intervals,
                focusActivity = FocusActivitySummary(recentSessions = listOf(zeroOverlapSession)),
                usageTemporalSummary = AppUsageTemporalSummary(
                    hasUsageData = true,
                    focusOverlap = FocusProtectionOverlapSummary(
                        availability = FocusOverlapAvailability.AVAILABLE,
                        overlappingUsageDurationMs = 0L,
                        outsideProtectionUsageDurationMs = 50 * 60_000L,
                        intersectingIntervalCount = 0,
                        protectionSessionCount = 1,
                        mergedProtectionDurationMs = 30 * 60_000L
                    ),
                    provenance = AppUsageTemporalProvenance(
                        usageSourceAvailability = SourceAvailability.AVAILABLE,
                        isProtectionHistoryAvailable = true
                    )
                )
            )
        )

        val zeroOverlapPatterns = engine.discoverPatterns(zeroOverlapCtx, zoneId = zoneId)
        assertNull(zeroOverlapPatterns.find { it.type == PatternDiscoveryType.USAGE_DURING_SCHEDULED_FOCUS })
        val zeroFreqPat = zeroOverlapPatterns.find { it.type == PatternDiscoveryType.FREQUENTLY_USED_APPLICATIONS }
        assertNotNull(zeroFreqPat)
        assertEquals("AVAILABLE", zeroFreqPat!!.metadata["focusOverlapAvailability"])
    }

    // 9. Genuine focus overlap is represented accurately with traceable interval observationIds
    @Test
    fun testGenuineFocusOverlap_representedAccuratelyFromIntersectingIntervals() {
        val day1FocusStart = ZonedDateTime.of(2025, 11, 5, 9, 0, 0, 0, zoneId).toInstant().toEpochMilli()
        val day1FocusEnd = day1FocusStart + 60 * 60_000L
        val day2FocusStart = ZonedDateTime.of(2025, 11, 6, 9, 0, 0, 0, zoneId).toInstant().toEpochMilli()
        val day2FocusEnd = day2FocusStart + 60 * 60_000L

        val session1 = ProtectionSession(
            id = "sess-1", userId = userId, startedAt = day1FocusStart, scheduledEndAt = day1FocusEnd,
            endedAt = day1FocusEnd, status = ProtectionSessionStatus.COMPLETED, source = EntitySource.USER,
            overrideAllowed = true, createdAt = day1FocusStart, updatedAt = day1FocusEnd
        )
        val session2 = session1.copy(
            id = "sess-2", startedAt = day2FocusStart, scheduledEndAt = day2FocusEnd,
            endedAt = day2FocusEnd, createdAt = day2FocusStart, updatedAt = day2FocusEnd
        )

        val intervals = listOf(
            createInterval("obs-ov-1", "com.social.app", day1FocusStart + 10 * 60_000L, day1FocusStart + 25 * 60_000L), // 15m inside session1
            createInterval("obs-ov-2", "com.social.app", day1FocusStart + 40 * 60_000L, day1FocusEnd + 10 * 60_000L),    // 20m inside session1
            createInterval("obs-ov-3", "com.social.app", day2FocusStart + 15 * 60_000L, day2FocusStart + 30 * 60_000L), // 15m inside session2
            createInterval("obs-outside", "com.social.app", day2FocusEnd + 30 * 60_000L, day2FocusEnd + 50 * 60_000L)   // outside all sessions
        )

        val winStart = day1FocusStart - 3_600_000L
        val winEnd = day2FocusEnd + 7_200_000L
        val baseCtx = createEmptyContext(startTime = winStart, endTime = winEnd)
        val analyzed = AppUsageIntervalAnalyzer.analyze(
            userId = userId,
            observations = emptyList(),
            protectionSessions = listOf(session1, session2),
            windowStartMs = winStart,
            windowEndMs = winEnd,
            nowMs = winEnd,
            usageAvailability = SourceAvailability.AVAILABLE,
            isUsageSourceReadable = true,
            isProtectionHistoryAvailable = true
        )
        val context = baseCtx.copy(
            digitalBehaviour = baseCtx.digitalBehaviour.copy(
                usageIntervals = intervals,
                focusActivity = FocusActivitySummary(recentSessions = listOf(session1, session2)),
                usageTemporalSummary = analyzed.temporalSummary.copy(
                    hasUsageData = true,
                    focusOverlap = FocusProtectionOverlapSummary(
                        availability = FocusOverlapAvailability.AVAILABLE,
                        overlappingUsageDurationMs = 50 * 60_000L,
                        outsideProtectionUsageDurationMs = 30 * 60_000L,
                        intersectingIntervalCount = 3,
                        protectionSessionCount = 2
                    ),
                    packageSummaries = listOf(
                        AppUsagePackageSummary(
                            packageName = "com.social.app",
                            intervalCount = 4,
                            totalDurationMs = 80 * 60_000L,
                            shortBurstCount = 0,
                            firstObservedAt = intervals.first().startTimeMs,
                            lastObservedAt = intervals.last().endTimeMs,
                            overlapWithProtectionDurationMs = 50 * 60_000L
                        )
                    )
                )
            )
        )

        val patterns = engine.discoverPatterns(context, zoneId = zoneId)
        val overlapPat = patterns.find { it.type == PatternDiscoveryType.USAGE_DURING_SCHEDULED_FOCUS }

        assertNotNull(overlapPat)
        assertEquals(PatternLifecycleState.ESTABLISHED, overlapPat!!.lifecycleState)
        assertEquals(setOf("obs-ov-1", "obs-ov-2", "obs-ov-3"), overlapPat.evidenceReferences.map { it.sourceId }.toSet())
        assertFalse(overlapPat.evidenceReferences.any { it.sourceId == "obs-outside" })
        assertEquals((50 * 60_000L).toString(), overlapPat.metadata["overlappingUsageDurationMs"])
        assertEquals("AVAILABLE", overlapPat.metadata["focusOverlapAvailability"])
    }

    // 10. Expired ACTIVE protection sessions remain bounded by their effective end
    @Test
    fun testExpiredActiveProtectionSession_boundedByScheduledEndForUsageIntervals() {
        val focusStart = baseTime - 5_400_000L          // T - 90m
        val scheduledExpiry = baseTime - 3_600_000L     // T - 60m (expired!)
        val expiredActive = ProtectionSession(
            id = "sess-expired-active",
            userId = userId,
            startedAt = focusStart,
            scheduledEndAt = scheduledExpiry,
            endedAt = null,
            status = ProtectionSessionStatus.ACTIVE,
            source = EntitySource.USER,
            overrideAllowed = true,
            createdAt = focusStart,
            updatedAt = focusStart
        )

        val postExpiryInterval = createInterval(
            obsId = "obs-after-expiry",
            packageName = "com.example.chat",
            startMs = baseTime - 1_200_000L, // T - 20m (strictly AFTER scheduledExpiry T - 60m)
            endMs = baseTime - 600_000L
        )
        val baseCtx = createEmptyContext(startTime = baseTime - 7_200_000L, endTime = baseTime)
        val postExpiryCtx = baseCtx.copy(
            digitalBehaviour = baseCtx.digitalBehaviour.copy(
                usageIntervals = listOf(postExpiryInterval),
                focusActivity = FocusActivitySummary(recentSessions = listOf(expiredActive))
            )
        )
        assertNull(engine.discoverPatterns(postExpiryCtx, zoneId = zoneId).find { it.type == PatternDiscoveryType.USAGE_DURING_SCHEDULED_FOCUS })

        val insideEffectiveInterval = createInterval(
            obsId = "obs-inside-effective",
            packageName = "com.example.chat",
            startMs = baseTime - 4_800_000L, // T - 80m (inside [T-90m, T-60m])
            endMs = baseTime - 4_200_000L    // T - 70m
        )
        val insideCtx = baseCtx.copy(
            digitalBehaviour = baseCtx.digitalBehaviour.copy(
                usageIntervals = listOf(insideEffectiveInterval, postExpiryInterval),
                focusActivity = FocusActivitySummary(recentSessions = listOf(expiredActive))
            )
        )
        val overlapPat = engine.discoverPatterns(insideCtx, zoneId = zoneId).find {
            it.type == PatternDiscoveryType.USAGE_DURING_SCHEDULED_FOCUS
        }
        assertNotNull(overlapPat)
        assertEquals(listOf("obs-inside-effective"), overlapPat!!.evidenceReferences.map { it.sourceId })
        assertEquals((10 * 60_000L).toString(), overlapPat.metadata["overlappingUsageDurationMs"])
    }

    // 11. No false causal claims, fabricated evidence references, or cross-user leakage
    @Test
    fun testNoFalseCausalClaimsOrFabricatedEvidence_andIsolatesUsers() {
        val day1 = ZonedDateTime.of(2025, 11, 5, 9, 0, 0, 0, zoneId).toInstant().toEpochMilli()
        val day2 = ZonedDateTime.of(2025, 11, 6, 9, 0, 0, 0, zoneId).toInstant().toEpochMilli()
        val session = ProtectionSession(
            id = "sess-causal-check", userId = userId, startedAt = day1 - 600_000L,
            scheduledEndAt = day1 + 3_600_000L, endedAt = day1 + 3_600_000L,
            status = ProtectionSessionStatus.COMPLETED, source = EntitySource.USER,
            overrideAllowed = true, createdAt = day1 - 600_000L, updatedAt = day1 + 3_600_000L
        )
        val userIntervals = listOf(
            createInterval("obs-real-1", "com.social.app", day1, day1 + 25 * 60_000L),
            createInterval("obs-real-2", "com.social.app", day1 + 1_800_000L, day1 + 3_600_000L),
            createInterval("obs-real-3", "com.social.app", day2, day2 + 25 * 60_000L),
            // Foreign user interval must NEVER leak into evidence or patterns
            createInterval("obs-bob-leak", "com.bob.app", day2, day2 + 50 * 60_000L, targetUserId = "user_bob")
        )
        val winStart = day1 - 3_600_000L
        val winEnd = day2 + 7_200_000L
        val baseCtx = createEmptyContext(startTime = winStart, endTime = winEnd)
        val context = baseCtx.copy(
            productivity = baseCtx.productivity.copy(
                taskSummary = TaskContextSummary(pendingTasks = listOf(createTask("t-pend-1", updatedAt = day2)))
            ),
            digitalBehaviour = baseCtx.digitalBehaviour.copy(
                usageIntervals = userIntervals,
                focusActivity = FocusActivitySummary(recentSessions = listOf(session))
            )
        )

        val patterns = engine.discoverPatterns(context, zoneId = zoneId)
        val validIds = setOf("obs-real-1", "obs-real-2", "obs-real-3", "t-pend-1", "sess-causal-check")

        assertTrue(patterns.isNotEmpty())
        patterns.forEach { pat ->
            assertFalse("No foreign user evidence leaked", pat.evidenceReferences.any { it.sourceId == "obs-bob-leak" || it.sourceId == "com.bob.app" })
            assertTrue("All evidence references trace to real inputs", pat.evidenceReferences.all { it.sourceId in validIds })
            assertFalse("Description must not make causal distraction claims", pat.description.contains("caused distraction", ignoreCase = true))
            assertFalse("Description must not claim causation", pat.description.contains("because of this app", ignoreCase = true))
            if (pat.category == PatternCategory.CROSS_DOMAIN) {
                assertEquals(EpistemicAuthority.AI_HYPOTHESIS, pat.epistemicAuthority)
                assertNotNull(pat.nonCausalDisclaimer)
                assertTrue(pat.nonCausalDisclaimer!!.contains("Does not imply causation", ignoreCase = true))
            }
        }
    }
}
