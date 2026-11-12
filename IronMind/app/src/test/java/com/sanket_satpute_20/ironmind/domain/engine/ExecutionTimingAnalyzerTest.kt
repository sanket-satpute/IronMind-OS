package com.sanket_satpute_20.ironmind.domain.engine

import com.sanket_satpute_20.ironmind.domain.model.EntitySource
import com.sanket_satpute_20.ironmind.domain.model.Event
import com.sanket_satpute_20.ironmind.domain.model.EventType
import com.sanket_satpute_20.ironmind.domain.model.Task
import com.sanket_satpute_20.ironmind.domain.model.TaskStatus
import com.sanket_satpute_20.ironmind.domain.model.context.ExecutionTimeBucket
import com.sanket_satpute_20.ironmind.domain.model.context.TemporalSampleSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class ExecutionTimingAnalyzerTest {

    private val userId = "user-a"
    private val zoneUtc = ZoneId.of("UTC")

    private fun task(
        id: String,
        userId: String = this.userId,
        status: TaskStatus = TaskStatus.PENDING,
        scheduledAt: Long? = null,
        dueAt: Long? = null,
        createdAt: Long = 1_000L,
        updatedAt: Long = 1_000L,
        completedAt: Long? = null,
        postponedAt: Long? = null
    ) = Task(
        id = id,
        userId = userId,
        title = "Task $id",
        description = "Desc",
        status = status,
        priority = 2,
        estimatedDurationMinutes = 30,
        scheduledAt = scheduledAt,
        dueAt = dueAt,
        createdAt = createdAt,
        updatedAt = updatedAt,
        completedAt = completedAt,
        postponedAt = postponedAt,
        source = EntitySource.USER
    )

    private fun event(
        id: String,
        type: EventType,
        entityId: String?,
        occurredAt: Long,
        userId: String = this.userId,
        metadata: String? = null
    ) = Event(
        id = id,
        userId = userId,
        type = type,
        entityType = "TASK",
        entityId = entityId,
        occurredAt = occurredAt,
        recordedAt = occurredAt,
        source = EntitySource.USER,
        metadata = metadata
    )

    @Test
    fun `computes start delay and completion delay accurately including early negative values`() {
        val windowStart = 1_700_000_000_000L
        val windowEnd = windowStart + 86_400_000L

        // Task 1: started 15m late (+900_000), completed 20m late (+1_200_000)
        val t1Scheduled = windowStart + 3_600_000L
        val t1Started = t1Scheduled + 900_000L
        val t1Due = windowStart + 7_200_000L
        val t1Completed = t1Due + 1_200_000L

        // Task 2: started 5m early (-300_000), completed 10m early (-600_000)
        val t2Scheduled = windowStart + 10_800_000L
        val t2Started = t2Scheduled - 300_000L
        val t2Due = windowStart + 14_400_000L
        val t2Completed = t2Due - 600_000L

        val tasks = listOf(
            task("t1", status = TaskStatus.COMPLETED, scheduledAt = t1Scheduled, dueAt = t1Due, completedAt = t1Completed),
            task("t2", status = TaskStatus.COMPLETED, scheduledAt = t2Scheduled, dueAt = t2Due, completedAt = t2Completed)
        )
        val events = listOf(
            event("e1", EventType.TASK_STARTED, "t1", t1Started),
            event("e2", EventType.TASK_COMPLETED, "t1", t1Completed),
            event("e3", EventType.TASK_STARTED, "t2", t2Started),
            event("e4", EventType.TASK_COMPLETED, "t2", t2Completed)
        )

        val summary = ExecutionTimingAnalyzer.analyze(
            userId = userId,
            tasks = tasks,
            events = events,
            windowStartMs = windowStart,
            windowEndMs = windowEnd,
            zoneId = zoneUtc
        )

        assertTrue(summary.hasSufficientData)
        assertEquals(2, summary.startDelaySamples.size)
        assertEquals(900_000L, summary.startDelaySamples[0].delayMs)
        assertEquals(-300_000L, summary.startDelaySamples[1].delayMs)
        assertEquals(300_000L, summary.averageStartDelayMs)
        assertEquals(300_000L, summary.medianStartDelayMs)

        assertEquals(2, summary.completionDelaySamples.size)
        assertEquals(1_200_000L, summary.completionDelaySamples[0].delayMs)
        assertEquals(-600_000L, summary.completionDelaySamples[1].delayMs)
        assertEquals(300_000L, summary.averageCompletionDelayMs)
        assertEquals(300_000L, summary.medianCompletionDelayMs)
        assertTrue(summary.completionDelaySamples.all { it.source == TemporalSampleSource.EVENT_DERIVED })

        assertEquals(2, summary.provenance.eventDerivedStartCount)
        assertEquals(2, summary.provenance.eventDerivedCompletionCount)
        assertEquals(0, summary.provenance.timestampFallbackCompletionCount)
    }

    @Test
    fun `missing scheduledAt or dueAt skips delay without fabricating defaults and never invents start from createdAt`() {
        val windowStart = 1_700_000_000_000L
        val windowEnd = windowStart + 86_400_000L

        val inProgressLegacyTask = task(
            id = "t-inprogress-legacy",
            status = TaskStatus.IN_PROGRESS,
            scheduledAt = windowStart + 1_000_000L,
            dueAt = windowStart + 5_000_000L,
            createdAt = windowStart + 500_000L,
            updatedAt = windowStart + 2_000_000L
        )
        val unscheduledStartedTask = task(
            id = "t-unscheduled",
            status = TaskStatus.COMPLETED,
            scheduledAt = null,
            dueAt = null,
            completedAt = windowStart + 4_000_000L
        )

        val events = listOf(
            event("e-start", EventType.TASK_STARTED, "t-unscheduled", windowStart + 3_000_000L),
            event("e-done", EventType.TASK_COMPLETED, "t-unscheduled", windowStart + 4_000_000L)
        )

        val summary = ExecutionTimingAnalyzer.analyze(
            userId = userId,
            tasks = listOf(inProgressLegacyTask, unscheduledStartedTask),
            events = events,
            windowStartMs = windowStart,
            windowEndMs = windowEnd,
            zoneId = zoneUtc
        )

        // Never invents start delay from createdAt/updatedAt or when scheduledAt/dueAt are missing
        assertTrue(summary.startDelaySamples.isEmpty())
        assertNull(summary.averageStartDelayMs)
        assertNull(summary.medianStartDelayMs)
        assertTrue(summary.completionDelaySamples.isEmpty())
        assertNull(summary.averageCompletionDelayMs)
        assertNull(summary.medianCompletionDelayMs)
        assertEquals(1, summary.provenance.unmeasuredStartsMissingScheduleCount)
        assertEquals(1, summary.provenance.unmeasuredCompletionsMissingDueDateCount)
        assertEquals(1, summary.provenance.legacyTasksMissingLifecycleEventsCount)
        // Distribution still reflects real TASK_STARTED and TASK_COMPLETED timestamps
        assertEquals(1, summary.startTimeDistribution.values.sum())
        assertEquals(1, summary.completionTimeDistribution.values.sum())
    }

    @Test
    fun `historical pre-3_6_7_6A completed and postponed tasks use timestamp fallback separately and ignore FAILED tasks`() {
        val windowStart = 1_700_000_000_000L
        val windowEnd = windowStart + 86_400_000L

        val legacyCompleted = task(
            id = "t-legacy-done",
            status = TaskStatus.COMPLETED,
            dueAt = windowStart + 10_000_000L,
            completedAt = windowStart + 11_500_000L
        )
        // FAILED sets Task.completedAt in UpdateTaskStatusUseCase without completion: must NOT count as completed
        val failedWithTimestamp = task(
            id = "t-failed",
            status = TaskStatus.FAILED,
            dueAt = windowStart + 10_000_000L,
            completedAt = windowStart + 12_000_000L
        )
        val legacyPostponed = task(
            id = "t-legacy-postponed",
            status = TaskStatus.POSTPONED,
            postponedAt = windowStart + 15_000_000L
        )
        // Even if Task.completedAt is present, matching TASK_COMPLETED event avoids double counting
        val modernCompleted = task(
            id = "t-modern-done",
            status = TaskStatus.COMPLETED,
            dueAt = windowStart + 20_000_000L,
            completedAt = windowStart + 19_000_000L
        )

        val events = listOf(
            event("e-mod-done", EventType.TASK_COMPLETED, "t-modern-done", windowStart + 19_000_000L)
        )

        val summary = ExecutionTimingAnalyzer.analyze(
            userId = userId,
            tasks = listOf(legacyCompleted, failedWithTimestamp, legacyPostponed, modernCompleted),
            events = events,
            windowStartMs = windowStart,
            windowEndMs = windowEnd,
            zoneId = zoneUtc
        )

        assertEquals(2, summary.completionDelaySamples.size)
        val legacySample = summary.completionDelaySamples.first { it.taskId == "t-legacy-done" }
        val modernSample = summary.completionDelaySamples.first { it.taskId == "t-modern-done" }
        assertEquals(TemporalSampleSource.TASK_TIMESTAMP_FALLBACK, legacySample.source)
        assertEquals(1_500_000L, legacySample.delayMs)
        assertEquals(TemporalSampleSource.EVENT_DERIVED, modernSample.source)
        assertEquals(-1_000_000L, modernSample.delayMs)
        assertEquals(1, summary.provenance.eventDerivedCompletionCount)
        assertEquals(1, summary.provenance.timestampFallbackCompletionCount)
        assertEquals(1, summary.forwardPostponementCount)
        assertEquals(0, summary.provenance.eventDerivedPostponementCount)
        assertEquals(1, summary.provenance.timestampFallbackPostponementCount)
        assertEquals(listOf("t-legacy-postponed"), summary.postponedTaskIds)
        assertEquals(2, summary.provenance.legacyTasksMissingLifecycleEventsCount)
    }

    @Test
    fun `explicit ZoneId and DST transition determine local execution time buckets`() {
        // 2026-03-08 11:30 UTC (EDT spring-forward day in America/New_York: UTC-4 -> 07:30 Morning;
        // Asia/Kolkata UTC+5:30 -> 17:00 Evening)
        val instantMs = ZonedDateTime.of(2026, 3, 8, 11, 30, 0, 0, zoneUtc).toInstant().toEpochMilli()
        val windowStart = instantMs - 3_600_000L
        val windowEnd = instantMs + 3_600_000L

        val ev = event("e-tz", EventType.TASK_STARTED, "t1", instantMs)

        val nySummary = ExecutionTimingAnalyzer.analyze(
            userId = userId,
            tasks = emptyList(),
            events = listOf(ev),
            windowStartMs = windowStart,
            windowEndMs = windowEnd,
            zoneId = ZoneId.of("America/New_York")
        )
        val kolkataSummary = ExecutionTimingAnalyzer.analyze(
            userId = userId,
            tasks = emptyList(),
            events = listOf(ev),
            windowStartMs = windowStart,
            windowEndMs = windowEnd,
            zoneId = ZoneId.of("Asia/Kolkata")
        )

        assertEquals("America/New_York", nySummary.timeZoneId)
        assertEquals(mapOf(ExecutionTimeBucket.MORNING to 1), nySummary.startTimeDistribution)
        assertEquals("Asia/Kolkata", kolkataSummary.timeZoneId)
        assertEquals(mapOf(ExecutionTimeBucket.EVENING to 1), kolkataSummary.startTimeDistribution)
    }

    @Test
    fun `empty window and repository failure report distinct provenance with null averages`() {
        val windowStart = 1_700_000_000_000L
        val windowEnd = windowStart + 86_400_000L

        val emptySummary = ExecutionTimingAnalyzer.analyze(
            userId = userId,
            tasks = listOf(task("t-other-user", userId = "user-b", status = TaskStatus.COMPLETED, dueAt = windowStart, completedAt = windowStart + 1000L)),
            events = emptyList(),
            windowStartMs = windowStart,
            windowEndMs = windowEnd,
            zoneId = zoneUtc,
            eventRepositoryAvailable = true
        )

        assertFalse(emptySummary.hasSufficientData)
        assertTrue(emptySummary.provenance.eventRepositoryAvailable)
        assertNull(emptySummary.averageStartDelayMs)
        assertNull(emptySummary.averageCompletionDelayMs)
        assertEquals(0, emptySummary.forwardPostponementCount)

        val failedRepoSummary = ExecutionTimingAnalyzer.analyze(
            userId = userId,
            tasks = emptyList(),
            events = emptyList(),
            windowStartMs = windowStart,
            windowEndMs = windowEnd,
            zoneId = zoneUtc,
            eventRepositoryAvailable = false
        )

        assertFalse(failedRepoSummary.hasSufficientData)
        assertFalse(failedRepoSummary.provenance.eventRepositoryAvailable)
        assertTrue(failedRepoSummary.provenance.notes!!.contains("unavailable", ignoreCase = true))
    }

    @Test
    fun `uses event metadata planned timestamps if task schedule was subsequently cleared or modified`() {
        val windowStart = 1_700_000_000_000L
        val windowEnd = windowStart + 86_400_000L
        val originalScheduled = windowStart + 2_000_000L
        val startedAt = originalScheduled + 600_000L

        val currentTask = task("t1", status = TaskStatus.IN_PROGRESS, scheduledAt = null)
        val startEvent = event(
            id = "e-start",
            type = EventType.TASK_STARTED,
            entityId = "t1",
            occurredAt = startedAt,
            metadata = "transitionedAt=$startedAt;newScheduledAt=$originalScheduled"
        )

        val summary = ExecutionTimingAnalyzer.analyze(
            userId = userId,
            tasks = listOf(currentTask),
            events = listOf(startEvent),
            windowStartMs = windowStart,
            windowEndMs = windowEnd,
            zoneId = zoneUtc
        )

        assertEquals(1, summary.startDelaySamples.size)
        assertEquals(600_000L, summary.startDelaySamples[0].delayMs)
        assertEquals(originalScheduled, summary.startDelaySamples[0].scheduledAt)
    }
}
