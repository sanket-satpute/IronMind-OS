package com.sanket_satpute_20.ironmind.domain.engine

import com.sanket_satpute_20.ironmind.domain.common.Clock
import com.sanket_satpute_20.ironmind.domain.common.IdGenerator
import com.sanket_satpute_20.ironmind.domain.model.EntitySource
import com.sanket_satpute_20.ironmind.domain.model.Event
import com.sanket_satpute_20.ironmind.domain.model.EventType
import com.sanket_satpute_20.ironmind.domain.model.Task
import com.sanket_satpute_20.ironmind.domain.model.TaskStatus
import com.sanket_satpute_20.ironmind.domain.model.context.DigitalBehaviourContext
import com.sanket_satpute_20.ironmind.domain.model.context.EpistemicAuthority
import com.sanket_satpute_20.ironmind.domain.model.context.ExecutionTimeBucket
import com.sanket_satpute_20.ironmind.domain.model.context.ExecutionTimingProvenance
import com.sanket_satpute_20.ironmind.domain.model.context.ExecutionTimingSummary
import com.sanket_satpute_20.ironmind.domain.model.context.FocusActivitySummary
import com.sanket_satpute_20.ironmind.domain.model.context.ProductivityContext
import com.sanket_satpute_20.ironmind.domain.model.context.SourceAvailability
import com.sanket_satpute_20.ironmind.domain.model.context.SourceProvenanceAndFreshness
import com.sanket_satpute_20.ironmind.domain.model.context.TaskCompletionDelaySample
import com.sanket_satpute_20.ironmind.domain.model.context.TaskContextSummary
import com.sanket_satpute_20.ironmind.domain.model.context.TaskStartDelaySample
import com.sanket_satpute_20.ironmind.domain.model.context.TemporalSampleSource
import com.sanket_satpute_20.ironmind.domain.model.context.UnifiedContext
import com.sanket_satpute_20.ironmind.domain.model.context.UsagePermissionStatus
import com.sanket_satpute_20.ironmind.domain.model.insight.InsightCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class PersonalInsightDiscoveryEngineTest {

    private val zoneId = ZoneId.of("UTC")
    private val userId = "user_timing_test"
    private val baseTime = 1762473600000L // arbitrary fixed epoch (Nov 7, 2025 00:00 UTC)
    private val oneDayMs = 86_400_000L

    private lateinit var clock: FakeClock
    private lateinit var engine: PersonalInsightDiscoveryEngine
    private var idCounter = 0

    class FakeClock(var time: Long) : Clock {
        override fun currentTimeMillis() = time
    }

    @Before
    fun setUp() {
        clock = FakeClock(baseTime)
        idCounter = 0
        engine = PersonalInsightDiscoveryEngine(
            clock = clock,
            idGenerator = object : IdGenerator {
                override fun generateId(): String = "ins-${++idCounter}"
            }
        )
    }

    private fun createTask(
        id: String,
        scheduledAt: Long? = null,
        dueAt: Long? = null,
        completedAt: Long? = null,
        status: TaskStatus = TaskStatus.COMPLETED,
        updatedAt: Long = baseTime
    ): Task = Task(
        id = id,
        userId = userId,
        goalId = "g1",
        planId = "p1",
        title = "Task $id",
        description = "Description for $id",
        status = status,
        priority = 3,
        estimatedDurationMinutes = 30,
        scheduledAt = scheduledAt,
        dueAt = dueAt,
        completedAt = completedAt,
        createdAt = baseTime - 14 * oneDayMs,
        updatedAt = updatedAt,
        source = EntitySource.USER
    )

    private fun createEmptyContext(
        startTime: Long = baseTime - 7 * oneDayMs,
        endTime: Long = baseTime,
        timingSummary: ExecutionTimingSummary = ExecutionTimingSummary(
            windowStartTimeMs = startTime,
            windowEndTimeMs = endTime,
            timeZoneId = zoneId.id
        )
    ): UnifiedContext {
        return UnifiedContext(
            userId = userId,
            generatedAt = baseTime,
            windowStartTimeMs = startTime,
            windowEndTimeMs = endTime,
            productivity = ProductivityContext(
                executionTiming = timingSummary
            ),
            digitalBehaviour = DigitalBehaviourContext(
                permissionStatus = UsagePermissionStatus.GRANTED,
                availability = SourceAvailability.AVAILABLE,
                windowStartTimeMs = startTime,
                windowEndTimeMs = endTime,
                appUsages = emptyList(),
                focusActivity = FocusActivitySummary()
            ),
            correlations = emptyList(),
            provenanceMap = mapOf(
                "Tasks" to SourceProvenanceAndFreshness("Tasks", SourceAvailability.AVAILABLE, true, baseTime, 0L, false),
                "Events" to SourceProvenanceAndFreshness("Events", SourceAvailability.AVAILABLE, true, baseTime, 0L, false)
            ),
            isFresh = true,
            isDegraded = false
        )
    }

    // 1. A task outside the analysis window does not influence TASK_SCHEDULING
    @Test
    fun testTaskOutsideAnalysisWindow_doesNotInfluenceTaskScheduling() {
        val windowStart = baseTime - 7 * oneDayMs
        val windowEnd = baseTime

        // 3 tasks completed OUTSIDE window (e.g. 10 days ago), only 1 inside
        val outside1 = createTask("t-out-1", completedAt = windowStart - 3 * oneDayMs)
        val outside2 = createTask("t-out-2", completedAt = windowStart - 2 * oneDayMs)
        val outside3 = createTask("t-out-3", completedAt = windowStart - 1 * oneDayMs)
        val inside1 = createTask("t-in-1", completedAt = windowStart + oneDayMs)

        val context = createEmptyContext(startTime = windowStart, endTime = windowEnd).copy(
            productivity = ProductivityContext(
                taskSummary = TaskContextSummary(
                    completedTasks = listOf(outside1, outside2, outside3, inside1)
                ),
                executionTiming = ExecutionTimingSummary(
                    windowStartTimeMs = windowStart,
                    windowEndTimeMs = windowEnd,
                    timeZoneId = zoneId.id
                )
            )
        )

        val result = engine.discoverInsights(userId, context, emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), zoneId)
        val schedulingInsights = result.discoveredInsights.filter { it.category == InsightCategory.TASK_SCHEDULING }

        // Only 1 task inside window -> insufficient evidence (N=1 < 3) -> suppressed!
        assertTrue(schedulingInsights.isEmpty())
        assertTrue(result.diagnosticNotes.any { it.contains("Insufficient window-scoped") })
    }

    // 2. Window-scoped execution timing is used instead of lifetime completed-task history
    @Test
    fun testWindowScopedTimingUsed_insteadOfLifetimeCompletedTasks() {
        val windowStart = baseTime - 3 * oneDayMs
        val windowEnd = baseTime

        // 20 lifetime completed tasks from months ago, 0 inside window
        val oldTasks = (1..20).map { idx ->
            createTask("t-old-$idx", completedAt = baseTime - (30 + idx) * oneDayMs)
        }

        val context = createEmptyContext(startTime = windowStart, endTime = windowEnd).copy(
            productivity = ProductivityContext(
                taskSummary = TaskContextSummary(completedTasks = oldTasks),
                executionTiming = ExecutionTimingSummary(
                    windowStartTimeMs = windowStart,
                    windowEndTimeMs = windowEnd,
                    timeZoneId = zoneId.id
                )
            )
        )

        val result = engine.discoverInsights(userId, context, emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), zoneId)
        val schedulingInsights = result.discoveredInsights.filter { it.category == InsightCategory.TASK_SCHEDULING }

        assertTrue("No scheduling insights generated when window has 0 tasks despite 20 lifetime tasks", schedulingInsights.isEmpty())
    }

    // 3. Planned versus actual start delay is correctly represented
    @Test
    fun testPlannedVsActualStartDelay_correctlyRepresented() {
        val windowStart = baseTime - 7 * oneDayMs
        val windowEnd = baseTime

        val day1Start = windowStart + oneDayMs + 9 * 3600_000L // 09:00 UTC
        val day2Start = windowStart + 2 * oneDayMs + 10 * 3600_000L // 10:00 UTC
        val day3Start = windowStart + 3 * oneDayMs + 11 * 3600_000L // 11:00 UTC

        // 3 tasks with 30-min start delays across 3 distinct days
        val startSamples = listOf(
            TaskStartDelaySample("t-1", scheduledAt = day1Start, startedAt = day1Start + 1_800_000L, delayMs = 1_800_000L, eventId = "ev-s1"),
            TaskStartDelaySample("t-2", scheduledAt = day2Start, startedAt = day2Start + 1_800_000L, delayMs = 1_800_000L, eventId = "ev-s2"),
            TaskStartDelaySample("t-3", scheduledAt = day3Start, startedAt = day3Start + 1_800_000L, delayMs = 1_800_000L, eventId = "ev-s3")
        )

        val timing = ExecutionTimingSummary(
            hasSufficientData = true,
            windowStartTimeMs = windowStart,
            windowEndTimeMs = windowEnd,
            timeZoneId = zoneId.id,
            startDelaySamples = startSamples,
            averageStartDelayMs = 1_800_000L,
            medianStartDelayMs = 1_800_000L
        )

        val context = createEmptyContext(startTime = windowStart, endTime = windowEnd, timingSummary = timing)
        val result = engine.discoverInsights(userId, context, emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), zoneId)

        val startInsight = result.discoveredInsights.find { it.title == "Task Start Schedule Alignment" }
        assertNotNull(startInsight)
        assertEquals(InsightCategory.TASK_SCHEDULING, startInsight!!.category)
        assertEquals(EpistemicAuthority.DERIVED_CALCULATION, startInsight.epistemicAuthority)
        assertTrue(startInsight.explanation.contains("often started later than planned"))
        assertTrue(startInsight.explanation.contains("30m"))
        assertEquals(listOf("t-1", "t-2", "t-3"), startInsight.supportingEvidenceIds)
    }

    // 4. Planned versus actual completion delay is correctly represented
    @Test
    fun testPlannedVsActualCompletionDelay_correctlyRepresented() {
        val windowStart = baseTime - 7 * oneDayMs
        val windowEnd = baseTime

        val day1Due = windowStart + oneDayMs + 17 * 3600_000L // 17:00 UTC
        val day2Due = windowStart + 2 * oneDayMs + 17 * 3600_000L
        val day3Due = windowStart + 3 * oneDayMs + 17 * 3600_000L

        // 3 tasks completed 45 mins late across 3 distinct calendar days
        val completionSamples = listOf(
            TaskCompletionDelaySample("t-1", dueAt = day1Due, completedAt = day1Due + 2_700_000L, delayMs = 2_700_000L, source = TemporalSampleSource.EVENT_DERIVED, eventId = "ev-c1"),
            TaskCompletionDelaySample("t-2", dueAt = day2Due, completedAt = day2Due + 2_700_000L, delayMs = 2_700_000L, source = TemporalSampleSource.EVENT_DERIVED, eventId = "ev-c2"),
            TaskCompletionDelaySample("t-3", dueAt = day3Due, completedAt = day3Due + 2_700_000L, delayMs = 2_700_000L, source = TemporalSampleSource.EVENT_DERIVED, eventId = "ev-c3")
        )

        val timing = ExecutionTimingSummary(
            hasSufficientData = true,
            windowStartTimeMs = windowStart,
            windowEndTimeMs = windowEnd,
            timeZoneId = zoneId.id,
            completionDelaySamples = completionSamples,
            averageCompletionDelayMs = 2_700_000L,
            medianCompletionDelayMs = 2_700_000L
        )

        val context = createEmptyContext(startTime = windowStart, endTime = windowEnd, timingSummary = timing)
        val result = engine.discoverInsights(userId, context, emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), zoneId)

        val completionInsight = result.discoveredInsights.find { it.title == "Task Completion Schedule Alignment" }
        assertNotNull(completionInsight)
        assertEquals(InsightCategory.TASK_SCHEDULING, completionInsight!!.category)
        assertEquals(EpistemicAuthority.DERIVED_CALCULATION, completionInsight.epistemicAuthority)
        assertTrue(completionInsight.explanation.contains("often completed after their scheduled due time"))
        assertTrue(completionInsight.explanation.contains("45m"))
        assertEquals(listOf("t-1", "t-2", "t-3"), completionInsight.supportingEvidenceIds)
    }

    // 5. Missing actual start timestamps do not get fabricated
    @Test
    fun testMissingActualStartTimestamps_notFabricatedFromCreatedAtOrUpdatedAt() {
        val windowStart = baseTime - 7 * oneDayMs
        val windowEnd = baseTime

        // Tasks have scheduledAt, createdAt, updatedAt, but NO TASK_STARTED event
        val t1 = createTask("t-1", scheduledAt = windowStart + oneDayMs, updatedAt = windowStart + oneDayMs + 1000L)
        val t2 = createTask("t-2", scheduledAt = windowStart + 2 * oneDayMs, updatedAt = windowStart + 2 * oneDayMs + 1000L)
        val t3 = createTask("t-3", scheduledAt = windowStart + 3 * oneDayMs, updatedAt = windowStart + 3 * oneDayMs + 1000L)

        // executionTiming has empty startDelaySamples because no start events exist
        val timing = ExecutionTimingSummary(
            windowStartTimeMs = windowStart,
            windowEndTimeMs = windowEnd,
            timeZoneId = zoneId.id,
            startDelaySamples = emptyList(),
            provenance = ExecutionTimingProvenance(unmeasuredStartsMissingScheduleCount = 0)
        )

        val context = createEmptyContext(startTime = windowStart, endTime = windowEnd, timingSummary = timing).copy(
            productivity = ProductivityContext(
                taskSummary = TaskContextSummary(allTasks = listOf(t1, t2, t3)),
                executionTiming = timing
            )
        )

        val result = engine.discoverInsights(userId, context, emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), zoneId)
        val startInsight = result.discoveredInsights.find { it.title == "Task Start Schedule Alignment" }

        // Start delay insight MUST NOT be generated by fabricating start times from createdAt/updatedAt
        assertNull(startInsight)
        assertTrue(result.diagnosticNotes.any { it.contains("Task start schedule alignment insight suppressed") })
    }

    // 6. updatedAt is not treated as task completion
    @Test
    fun testUpdatedAt_isNotTreatedAsTaskCompletion() {
        val windowStart = baseTime - 7 * oneDayMs
        val windowEnd = baseTime

        // Tasks with completedAt = null, but status = COMPLETED and updatedAt in window
        val t1 = createTask("t-1", completedAt = null, status = TaskStatus.COMPLETED, updatedAt = windowStart + oneDayMs)
        val t2 = createTask("t-2", completedAt = null, status = TaskStatus.COMPLETED, updatedAt = windowStart + 2 * oneDayMs)
        val t3 = createTask("t-3", completedAt = null, status = TaskStatus.COMPLETED, updatedAt = windowStart + 3 * oneDayMs)

        val context = createEmptyContext(startTime = windowStart, endTime = windowEnd).copy(
            productivity = ProductivityContext(
                taskSummary = TaskContextSummary(completedTasks = listOf(t1, t2, t3))
            )
        )

        val result = engine.discoverInsights(userId, context, emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), zoneId)
        val rhythmInsight = result.discoveredInsights.find { it.title == "Task Completion Timing Rhythm" }

        assertNull("updatedAt must NOT be used as completion time", rhythmInsight)
    }

    // 7. Task and event representations do not double-count one task
    @Test
    fun testTaskAndEventRepresentations_doNotDoubleCountSingleTask() {
        val windowStart = baseTime - 7 * oneDayMs
        val windowEnd = baseTime
        val day1 = windowStart + oneDayMs + 10 * 3600_000L
        val day2 = windowStart + 2 * oneDayMs + 10 * 3600_000L

        // t-1 has both a Task object in completedTasks and a TASK_COMPLETED Event
        val t1 = createTask("t-1", completedAt = day1)
        val t2 = createTask("t-2", completedAt = day2)
        val ev1 = Event("ev-1", userId, EventType.TASK_COMPLETED, "TASK", "t-1", day1, day1, source = EntitySource.USER)
        val ev2 = Event("ev-2", userId, EventType.TASK_COMPLETED, "TASK", "t-2", day2, day2, source = EntitySource.USER)

        // Only 2 distinct tasks (t-1, t-2), each represented twice -> total unique tasks = 2 (< 3 minimum)
        val context = createEmptyContext(startTime = windowStart, endTime = windowEnd).copy(
            productivity = ProductivityContext(
                taskSummary = TaskContextSummary(completedTasks = listOf(t1, t2)),
                recentEvents = listOf(ev1, ev2)
            )
        )

        val result = engine.discoverInsights(userId, context, emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), zoneId)
        val rhythmInsight = result.discoveredInsights.find { it.title == "Task Completion Timing Rhythm" }

        // Must NOT double-count to 4; distinct task count is 2 < 3, so insight is suppressed
        assertNull(rhythmInsight)
        assertTrue(result.diagnosticNotes.any { it.contains("N=2") })
    }

    // 8. Multiple events from one task do not inflate independent task sample counts
    @Test
    fun testMultipleEventsFromSingleTask_doNotInflateSampleCounts() {
        val windowStart = baseTime - 7 * oneDayMs
        val windowEnd = baseTime
        val day1 = windowStart + oneDayMs + 9 * 3600_000L
        val day2 = windowStart + 2 * oneDayMs + 9 * 3600_000L

        // Task t-1 started 3 times across multiple days (e.g. paused/resumed)
        val startSamples = listOf(
            TaskStartDelaySample("t-1", scheduledAt = day1, startedAt = day1 + 600_000L, delayMs = 600_000L, eventId = "ev-s1a"),
            TaskStartDelaySample("t-1", scheduledAt = day1, startedAt = day1 + 1_200_000L, delayMs = 1_200_000L, eventId = "ev-s1b"),
            TaskStartDelaySample("t-1", scheduledAt = day2, startedAt = day2 + 600_000L, delayMs = 600_000L, eventId = "ev-s1c")
        )

        val timing = ExecutionTimingSummary(
            hasSufficientData = true,
            windowStartTimeMs = windowStart,
            windowEndTimeMs = windowEnd,
            timeZoneId = zoneId.id,
            startDelaySamples = startSamples
        )

        val context = createEmptyContext(startTime = windowStart, endTime = windowEnd, timingSummary = timing)
        val result = engine.discoverInsights(userId, context, emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), zoneId)
        val startInsight = result.discoveredInsights.find { it.title == "Task Start Schedule Alignment" }

        // Only 1 unique task ID ("t-1") -> sample count is 1 < 3 -> suppressed
        assertNull(startInsight)
        assertTrue(result.diagnosticNotes.any { it.contains("N=1") })
    }

    // 9. Repeated samples from one day do not bypass multi-day sufficiency
    @Test
    fun testRepeatedSamplesSingleDay_neverBypassesMultiDaySufficiency() {
        val windowStart = baseTime - 7 * oneDayMs
        val windowEnd = baseTime
        val singleDayMs = windowStart + oneDayMs // All within the same calendar day

        // 5 distinct tasks, all executed on the EXACT same day
        val samples = (1..5).map { idx ->
            val time = singleDayMs + idx * 3600_000L
            TaskCompletionDelaySample(
                taskId = "t-same-$idx",
                dueAt = time - 1_800_000L,
                completedAt = time,
                delayMs = 1_800_000L,
                source = TemporalSampleSource.EVENT_DERIVED,
                eventId = "ev-c-$idx"
            )
        }

        val timing = ExecutionTimingSummary(
            hasSufficientData = true,
            windowStartTimeMs = windowStart,
            windowEndTimeMs = windowEnd,
            timeZoneId = zoneId.id,
            completionDelaySamples = samples
        )

        val context = createEmptyContext(startTime = windowStart, endTime = windowEnd, timingSummary = timing)
        val result = engine.discoverInsights(userId, context, emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), zoneId)
        val completionInsight = result.discoveredInsights.find { it.title == "Task Completion Schedule Alignment" }

        // distinctDays = 1 < 2 minimum -> suppressed!
        assertNull(completionInsight)
        assertTrue(result.diagnosticNotes.any { it.contains("distinctDays=1") })
    }

    // 10. Event-derived and fallback-derived provenance remain distinguishable
    @Test
    fun testEventDerivedAndFallbackDerivedProvenance_remainDistinguishable() {
        val windowStart = baseTime - 7 * oneDayMs
        val windowEnd = baseTime
        val day1 = windowStart + oneDayMs + 10 * 3600_000L
        val day2 = windowStart + 2 * oneDayMs + 10 * 3600_000L
        val day3 = windowStart + 3 * oneDayMs + 10 * 3600_000L

        // Case A: Pure EVENT_DERIVED
        val eventSamples = listOf(
            TaskCompletionDelaySample("t-e1", day1, day1 + 600_000L, 600_000L, TemporalSampleSource.EVENT_DERIVED, "ev-1"),
            TaskCompletionDelaySample("t-e2", day2, day2 + 600_000L, 600_000L, TemporalSampleSource.EVENT_DERIVED, "ev-2"),
            TaskCompletionDelaySample("t-e3", day3, day3 + 600_000L, 600_000L, TemporalSampleSource.EVENT_DERIVED, "ev-3")
        )
        val eventTiming = ExecutionTimingSummary(
            hasSufficientData = true,
            windowStartTimeMs = windowStart,
            windowEndTimeMs = windowEnd,
            timeZoneId = zoneId.id,
            completionDelaySamples = eventSamples,
            provenance = ExecutionTimingProvenance(eventDerivedCompletionCount = 3)
        )
        val eventResult = engine.discoverInsights(userId, createEmptyContext(startTime = windowStart, endTime = windowEnd, timingSummary = eventTiming), emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), zoneId)
        val eventInsight = eventResult.discoveredInsights.find { it.title == "Task Completion Schedule Alignment" }
        assertNotNull(eventInsight)
        assertEquals(listOf("Room:event", "Room:task"), eventInsight!!.sourceProvenance)
        assertEquals(0.85f, eventInsight.confidence, 0.01f)

        // Case B: Pure TASK_TIMESTAMP_FALLBACK
        val fallbackSamples = listOf(
            TaskCompletionDelaySample("t-f1", day1, day1 + 600_000L, 600_000L, TemporalSampleSource.TASK_TIMESTAMP_FALLBACK, null),
            TaskCompletionDelaySample("t-f2", day2, day2 + 600_000L, 600_000L, TemporalSampleSource.TASK_TIMESTAMP_FALLBACK, null),
            TaskCompletionDelaySample("t-f3", day3, day3 + 600_000L, 600_000L, TemporalSampleSource.TASK_TIMESTAMP_FALLBACK, null)
        )
        val fallbackTiming = ExecutionTimingSummary(
            hasSufficientData = true,
            windowStartTimeMs = windowStart,
            windowEndTimeMs = windowEnd,
            timeZoneId = zoneId.id,
            completionDelaySamples = fallbackSamples,
            provenance = ExecutionTimingProvenance(timestampFallbackCompletionCount = 3)
        )
        val fallbackResult = engine.discoverInsights(userId, createEmptyContext(startTime = windowStart, endTime = windowEnd, timingSummary = fallbackTiming), emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), zoneId)
        val fallbackInsight = fallbackResult.discoveredInsights.find { it.title == "Task Completion Schedule Alignment" }
        assertNotNull(fallbackInsight)
        assertEquals(listOf("Room:task"), fallbackInsight!!.sourceProvenance)
        assertEquals(0.70f, fallbackInsight.confidence, 0.01f)
        assertTrue(fallbackInsight.explainabilityBlock.uncertaintyOrLimitations.contains("Includes 3 historical task record(s)"))
    }

    // 11. Missing timing data does not become zero delay
    @Test
    fun testMissingTimingData_doesNotBecomeZeroDelay() {
        val windowStart = baseTime - 7 * oneDayMs
        val windowEnd = baseTime

        // 3 completed tasks with no scheduledAt and no dueAt
        val t1 = createTask("t-1", scheduledAt = null, dueAt = null, completedAt = windowStart + oneDayMs)
        val t2 = createTask("t-2", scheduledAt = null, dueAt = null, completedAt = windowStart + 2 * oneDayMs)
        val t3 = createTask("t-3", scheduledAt = null, dueAt = null, completedAt = windowStart + 3 * oneDayMs)

        val timing = ExecutionTimingSummary(
            windowStartTimeMs = windowStart,
            windowEndTimeMs = windowEnd,
            timeZoneId = zoneId.id,
            startDelaySamples = emptyList(),
            completionDelaySamples = emptyList(),
            provenance = ExecutionTimingProvenance(
                unmeasuredStartsMissingScheduleCount = 3,
                unmeasuredCompletionsMissingDueDateCount = 3
            )
        )

        val context = createEmptyContext(startTime = windowStart, endTime = windowEnd, timingSummary = timing).copy(
            productivity = ProductivityContext(
                taskSummary = TaskContextSummary(completedTasks = listOf(t1, t2, t3)),
                executionTiming = timing
            )
        )

        val result = engine.discoverInsights(userId, context, emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), zoneId)

        // Start and completion schedule alignment insights MUST NOT claim 0ms delay!
        assertNull(result.discoveredInsights.find { it.title == "Task Start Schedule Alignment" })
        assertNull(result.discoveredInsights.find { it.title == "Task Completion Schedule Alignment" })
    }

    // 12. Unavailable event history does not become complete history
    @Test
    fun testUnavailableEventHistory_representedAccuratelyWithReducedConfidence() {
        val windowStart = baseTime - 7 * oneDayMs
        val windowEnd = baseTime
        val day1 = windowStart + oneDayMs + 10 * 3600_000L
        val day2 = windowStart + 2 * oneDayMs + 10 * 3600_000L
        val day3 = windowStart + 3 * oneDayMs + 10 * 3600_000L

        val fallbackSamples = listOf(
            TaskCompletionDelaySample("t-1", day1, day1 + 600_000L, 600_000L, TemporalSampleSource.TASK_TIMESTAMP_FALLBACK, null),
            TaskCompletionDelaySample("t-2", day2, day2 + 600_000L, 600_000L, TemporalSampleSource.TASK_TIMESTAMP_FALLBACK, null),
            TaskCompletionDelaySample("t-3", day3, day3 + 600_000L, 600_000L, TemporalSampleSource.TASK_TIMESTAMP_FALLBACK, null)
        )

        val timing = ExecutionTimingSummary(
            hasSufficientData = true,
            windowStartTimeMs = windowStart,
            windowEndTimeMs = windowEnd,
            timeZoneId = zoneId.id,
            completionDelaySamples = fallbackSamples,
            provenance = ExecutionTimingProvenance(
                eventRepositoryAvailable = false,
                timestampFallbackCompletionCount = 3
            )
        )

        val context = createEmptyContext(startTime = windowStart, endTime = windowEnd, timingSummary = timing).copy(
            provenanceMap = mapOf(
                "Events" to SourceProvenanceAndFreshness("Events", SourceAvailability.UNAVAILABLE_HARDWARE, true, null, null, false)
            )
        )

        val result = engine.discoverInsights(userId, context, emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), zoneId)
        val insight = result.discoveredInsights.find { it.title == "Task Completion Schedule Alignment" }

        assertNotNull(insight)
        assertEquals(0.60f, insight!!.confidence, 0.01f)
        assertTrue(insight.explainabilityBlock.uncertaintyOrLimitations.contains("Event repository was unavailable"))
    }

    // 13. Partial history remains explicitly represented
    @Test
    fun testTruncatedEventHistory_remainsExplicitlyRepresented() {
        val windowStart = baseTime - 7 * oneDayMs
        val windowEnd = baseTime
        val day1 = windowStart + oneDayMs + 9 * 3600_000L
        val day2 = windowStart + 2 * oneDayMs + 9 * 3600_000L
        val day3 = windowStart + 3 * oneDayMs + 9 * 3600_000L

        val startSamples = listOf(
            TaskStartDelaySample("t-1", day1, day1 + 1_800_000L, 1_800_000L, "ev-s1"),
            TaskStartDelaySample("t-2", day2, day2 + 1_800_000L, 1_800_000L, "ev-s2"),
            TaskStartDelaySample("t-3", day3, day3 + 1_800_000L, 1_800_000L, "ev-s3")
        )

        val timing = ExecutionTimingSummary(
            hasSufficientData = true,
            windowStartTimeMs = windowStart,
            windowEndTimeMs = windowEnd,
            timeZoneId = zoneId.id,
            startDelaySamples = startSamples,
            provenance = ExecutionTimingProvenance(
                isEventHistoryTruncated = true,
                retainedWindowEventCount = 250
            )
        )

        val context = createEmptyContext(startTime = windowStart, endTime = windowEnd, timingSummary = timing)
        val result = engine.discoverInsights(userId, context, emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), zoneId)
        val startInsight = result.discoveredInsights.find { it.title == "Task Start Schedule Alignment" }

        assertNotNull(startInsight)
        assertEquals(0.70f, startInsight!!.confidence, 0.01f) // Capped/reduced from 0.85f due to truncation
        assertTrue(startInsight.explainabilityBlock.uncertaintyOrLimitations.contains("Event history query reached limit"))
    }

    // 14. Non-causal wording and epistemic authority remain intact
    @Test
    fun testNonCausalWordingAndEpistemicAuthority_remainIntact() {
        val windowStart = baseTime - 7 * oneDayMs
        val windowEnd = baseTime
        val day1 = windowStart + oneDayMs + 9 * 3600_000L
        val day2 = windowStart + 2 * oneDayMs + 9 * 3600_000L
        val day3 = windowStart + 3 * oneDayMs + 9 * 3600_000L

        val startSamples = listOf(
            TaskStartDelaySample("t-1", day1, day1 + 1_800_000L, 1_800_000L, "ev-s1"),
            TaskStartDelaySample("t-2", day2, day2 + 1_800_000L, 1_800_000L, "ev-s2"),
            TaskStartDelaySample("t-3", day3, day3 + 1_800_000L, 1_800_000L, "ev-s3")
        )

        val timing = ExecutionTimingSummary(
            hasSufficientData = true,
            windowStartTimeMs = windowStart,
            windowEndTimeMs = windowEnd,
            timeZoneId = zoneId.id,
            startDelaySamples = startSamples
        )

        val context = createEmptyContext(startTime = windowStart, endTime = windowEnd, timingSummary = timing)
        val result = engine.discoverInsights(userId, context, emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), zoneId)

        val forbiddenPhrases = listOf("lazy", "discipline", "phone caused", "procrastination", "bad habit", "fault")

        result.discoveredInsights.forEach { insight ->
            val text = "${insight.title} ${insight.explanation} ${insight.explainabilityBlock.whatWasObserved} ${insight.explainabilityBlock.uncertaintyOrLimitations}"
            forbiddenPhrases.forEach { phrase ->
                assertFalse("Insight text must not contain '$phrase'", text.contains(phrase, ignoreCase = true))
            }
            if (insight.title.contains("Alignment")) {
                assertEquals(EpistemicAuthority.DERIVED_CALCULATION, insight.epistemicAuthority)
            } else if (insight.title.contains("Rhythm")) {
                assertEquals(EpistemicAuthority.AI_HYPOTHESIS, insight.epistemicAuthority)
            }
        }
    }

    // 15. Existing TASK_SCHEDULING behavior and unrelated insight tests remain valid
    @Test
    fun testExistingTaskSchedulingAndOtherCategories_remainValid() {
        val windowStart = baseTime - 7 * oneDayMs
        val windowEnd = baseTime
        val day1Morning = windowStart + oneDayMs + 9 * 3600_000L
        val day2Morning = windowStart + 2 * oneDayMs + 9 * 3600_000L
        val day3Morning = windowStart + 3 * oneDayMs + 9 * 3600_000L

        val t1 = createTask("t-1", completedAt = day1Morning)
        val t2 = createTask("t-2", completedAt = day2Morning)
        val t3 = createTask("t-3", completedAt = day3Morning)

        val context = createEmptyContext(startTime = windowStart, endTime = windowEnd).copy(
            productivity = ProductivityContext(
                taskSummary = TaskContextSummary(completedTasks = listOf(t1, t2, t3))
            )
        )

        val result = engine.discoverInsights(userId, context, emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), zoneId)
        val rhythmInsight = result.discoveredInsights.find { it.title == "Task Completion Timing Rhythm" }

        assertNotNull("Task Completion Timing Rhythm generated when 3 morning tasks span 3 days", rhythmInsight)
        assertEquals(InsightCategory.TASK_SCHEDULING, rhythmInsight!!.category)
        assertTrue(rhythmInsight.explanation.contains("morning hours"))
        assertEquals(listOf("t-1", "t-2", "t-3"), rhythmInsight.supportingEvidenceIds)
    }
}
