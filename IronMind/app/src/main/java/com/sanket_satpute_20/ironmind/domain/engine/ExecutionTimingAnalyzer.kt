package com.sanket_satpute_20.ironmind.domain.engine

import com.sanket_satpute_20.ironmind.domain.model.Event
import com.sanket_satpute_20.ironmind.domain.model.EventType
import com.sanket_satpute_20.ironmind.domain.model.Task
import com.sanket_satpute_20.ironmind.domain.model.TaskStatus
import com.sanket_satpute_20.ironmind.domain.model.context.ExecutionTimeBucket
import com.sanket_satpute_20.ironmind.domain.model.context.ExecutionTimingProvenance
import com.sanket_satpute_20.ironmind.domain.model.context.ExecutionTimingSummary
import com.sanket_satpute_20.ironmind.domain.model.context.TaskCompletionDelaySample
import com.sanket_satpute_20.ironmind.domain.model.context.TaskStartDelaySample
import com.sanket_satpute_20.ironmind.domain.model.context.TemporalSampleSource
import java.time.Instant
import java.time.ZoneId
import kotlin.math.roundToLong

/**
 * Pure, deterministic calculator for temporal execution metrics over an explicit observation window
 * and timezone. Never fabricates start times from Task.createdAt or Task.updatedAt, and keeps
 * event-derived samples distinct from historical Task timestamp fallbacks.
 */
object ExecutionTimingAnalyzer {

    fun analyze(
        userId: String,
        tasks: List<Task>,
        events: List<Event>,
        windowStartMs: Long,
        windowEndMs: Long,
        zoneId: ZoneId,
        eventRepositoryAvailable: Boolean = true,
        isEventHistoryTruncated: Boolean = false,
        retainedWindowEventCount: Int = events.size
    ): ExecutionTimingSummary {
        val userTasks = tasks.filter { it.userId == userId }
        val tasksById = userTasks.associateBy { it.id }

        val windowEvents = events
            .asSequence()
            .filter { it.userId == userId && it.occurredAt in windowStartMs..windowEndMs }
            .distinctBy { it.id }
            .sortedWith(compareBy<Event> { it.occurredAt }.thenBy { it.id })
            .toList()

        val startTimeDist = mutableMapOf<ExecutionTimeBucket, Int>()
        val completionTimeDist = mutableMapOf<ExecutionTimeBucket, Int>()
        val startDelaySamples = mutableListOf<TaskStartDelaySample>()
        val completionDelaySamples = mutableListOf<TaskCompletionDelaySample>()

        val startedTaskIdsWithEvent = mutableSetOf<String>()
        val completedTaskIdsWithEvent = mutableSetOf<String>()
        val postponedTaskIdsWithEvent = mutableSetOf<String>()
        val legacyFallbackTaskIds = mutableSetOf<String>()
        val legacyPostponedTaskIds = mutableSetOf<String>()

        var unmeasuredStartsMissingSchedule = 0
        var unmeasuredCompletionsMissingDueDate = 0
        var timestampFallbackCompletionCount = 0
        var timestampFallbackPostponementCount = 0

        // 1. TASK_STARTED events (only source of true execution start timestamps)
        val startEvents = windowEvents.filter { it.type == EventType.TASK_STARTED }
        for (event in startEvents) {
            event.entityId?.let { startedTaskIdsWithEvent.add(it) }
            val bucket = toExecutionBucket(event.occurredAt, zoneId)
            startTimeDist[bucket] = (startTimeDist[bucket] ?: 0) + 1

            val task = event.entityId?.let { tasksById[it] }
            val plannedStart = parseMetadataLong(event.metadata, "newScheduledAt") ?: task?.scheduledAt
            if (plannedStart != null) {
                startDelaySamples.add(
                    TaskStartDelaySample(
                        taskId = event.entityId ?: event.id,
                        scheduledAt = plannedStart,
                        startedAt = event.occurredAt,
                        delayMs = event.occurredAt - plannedStart,
                        eventId = event.id
                    )
                )
            } else {
                unmeasuredStartsMissingSchedule++
            }
        }

        // 2. TASK_COMPLETED events
        val completionEvents = windowEvents.filter { it.type == EventType.TASK_COMPLETED }
        for (event in completionEvents) {
            event.entityId?.let { completedTaskIdsWithEvent.add(it) }
            val bucket = toExecutionBucket(event.occurredAt, zoneId)
            completionTimeDist[bucket] = (completionTimeDist[bucket] ?: 0) + 1

            val task = event.entityId?.let { tasksById[it] }
            val plannedDue = parseMetadataLong(event.metadata, "newDueAt") ?: task?.dueAt
            if (plannedDue != null) {
                completionDelaySamples.add(
                    TaskCompletionDelaySample(
                        taskId = event.entityId ?: event.id,
                        dueAt = plannedDue,
                        completedAt = event.occurredAt,
                        delayMs = event.occurredAt - plannedDue,
                        source = TemporalSampleSource.EVENT_DERIVED,
                        eventId = event.id
                    )
                )
            } else {
                unmeasuredCompletionsMissingDueDate++
            }
        }

        // 3. Historical Task.completedAt fallback for pre-3.6.7.6A tasks (strictly COMPLETED status)
        for (task in userTasks.sortedBy { it.id }) {
            val completedAt = task.completedAt ?: continue
            if (task.status == TaskStatus.COMPLETED &&
                completedAt in windowStartMs..windowEndMs &&
                task.id !in completedTaskIdsWithEvent
            ) {
                timestampFallbackCompletionCount++
                legacyFallbackTaskIds.add(task.id)
                val bucket = toExecutionBucket(completedAt, zoneId)
                completionTimeDist[bucket] = (completionTimeDist[bucket] ?: 0) + 1

                val plannedDue = task.dueAt
                if (plannedDue != null) {
                    completionDelaySamples.add(
                        TaskCompletionDelaySample(
                            taskId = task.id,
                            dueAt = plannedDue,
                            completedAt = completedAt,
                            delayMs = completedAt - plannedDue,
                            source = TemporalSampleSource.TASK_TIMESTAMP_FALLBACK,
                            eventId = null
                        )
                    )
                } else {
                    unmeasuredCompletionsMissingDueDate++
                }
            }
        }

        // 4. Genuine TASK_POSTPONED events + historical Task.postponedAt fallback
        val postponeEvents = windowEvents.filter { it.type == EventType.TASK_POSTPONED }
        for (event in postponeEvents) {
            event.entityId?.let { postponedTaskIdsWithEvent.add(it) }
        }

        for (task in userTasks.sortedBy { it.id }) {
            val postponedAt = task.postponedAt ?: continue
            if (postponedAt in windowStartMs..windowEndMs && task.id !in postponedTaskIdsWithEvent) {
                timestampFallbackPostponementCount++
                legacyPostponedTaskIds.add(task.id)
                legacyFallbackTaskIds.add(task.id)
            }
        }

        // Record in-progress tasks active in this window that have no TASK_STARTED event without fabricating start time
        for (task in userTasks) {
            if (task.status == TaskStatus.IN_PROGRESS &&
                task.updatedAt in windowStartMs..windowEndMs &&
                task.id !in startedTaskIdsWithEvent &&
                task.id !in completedTaskIdsWithEvent &&
                task.id !in postponedTaskIdsWithEvent
            ) {
                legacyFallbackTaskIds.add(task.id)
            }
        }

        val totalDist = mergeDistributions(startTimeDist, completionTimeDist)
        val forwardPostponementCount = postponeEvents.size + timestampFallbackPostponementCount
        val postponedTaskIds = (postponedTaskIdsWithEvent + legacyPostponedTaskIds).toList().sorted()

        val hasSufficientData = startDelaySamples.isNotEmpty() ||
            completionDelaySamples.isNotEmpty() ||
            forwardPostponementCount > 0 ||
            totalDist.values.sum() > 0

        val noteParts = buildList {
            if (!eventRepositoryAvailable) {
                add("Event repository unavailable; using historical Task timestamps where supported.")
            }
            if (isEventHistoryTruncated) {
                add("Window event history hit query cap; lifecycle events were prioritized explicitly.")
            }
            if (legacyFallbackTaskIds.isNotEmpty()) {
                add("${legacyFallbackTaskIds.size} historical task(s) lacked lifecycle events; start timestamps were not fabricated.")
            }
        }

        return ExecutionTimingSummary(
            hasSufficientData = hasSufficientData,
            windowStartTimeMs = windowStartMs,
            windowEndTimeMs = windowEndMs,
            timeZoneId = zoneId.id,
            startDelaySamples = startDelaySamples,
            averageStartDelayMs = computeAverage(startDelaySamples.map { it.delayMs }),
            medianStartDelayMs = computeMedian(startDelaySamples.map { it.delayMs }),
            completionDelaySamples = completionDelaySamples,
            averageCompletionDelayMs = computeAverage(completionDelaySamples.map { it.delayMs }),
            medianCompletionDelayMs = computeMedian(completionDelaySamples.map { it.delayMs }),
            forwardPostponementCount = forwardPostponementCount,
            postponedTaskIds = postponedTaskIds,
            startTimeDistribution = startTimeDist.toMap(),
            completionTimeDistribution = completionTimeDist.toMap(),
            totalExecutionTimeDistribution = totalDist,
            provenance = ExecutionTimingProvenance(
                eventRepositoryAvailable = eventRepositoryAvailable,
                eventDerivedStartCount = startEvents.size,
                eventDerivedCompletionCount = completionEvents.size,
                timestampFallbackCompletionCount = timestampFallbackCompletionCount,
                eventDerivedPostponementCount = postponeEvents.size,
                timestampFallbackPostponementCount = timestampFallbackPostponementCount,
                unmeasuredStartsMissingScheduleCount = unmeasuredStartsMissingSchedule,
                unmeasuredCompletionsMissingDueDateCount = unmeasuredCompletionsMissingDueDate,
                legacyTasksMissingLifecycleEventsCount = legacyFallbackTaskIds.size,
                isEventHistoryTruncated = isEventHistoryTruncated,
                retainedWindowEventCount = retainedWindowEventCount,
                notes = noteParts.takeIf { it.isNotEmpty() }?.joinToString(" ")
            )
        )
    }

    internal fun toExecutionBucket(timestampMs: Long, zoneId: ZoneId): ExecutionTimeBucket {
        val hour = Instant.ofEpochMilli(timestampMs).atZone(zoneId).hour
        return when (hour) {
            in 6..11 -> ExecutionTimeBucket.MORNING
            in 12..16 -> ExecutionTimeBucket.AFTERNOON
            in 17..21 -> ExecutionTimeBucket.EVENING
            else -> ExecutionTimeBucket.NIGHT
        }
    }

    internal fun parseMetadataLong(metadata: String?, key: String): Long? {
        if (metadata.isNullOrBlank()) return null
        val prefix = "$key="
        return metadata.split(';')
            .firstOrNull { it.startsWith(prefix) }
            ?.substringAfter('=')
            ?.trim()
            ?.takeIf { it.isNotEmpty() && !it.equals("none", ignoreCase = true) }
            ?.toLongOrNull()
    }

    private fun mergeDistributions(
        first: Map<ExecutionTimeBucket, Int>,
        second: Map<ExecutionTimeBucket, Int>
    ): Map<ExecutionTimeBucket, Int> {
        val merged = mutableMapOf<ExecutionTimeBucket, Int>()
        first.forEach { (bucket, count) ->
            if (count > 0) merged[bucket] = (merged[bucket] ?: 0) + count
        }
        second.forEach { (bucket, count) ->
            if (count > 0) merged[bucket] = (merged[bucket] ?: 0) + count
        }
        return merged
    }

    private fun computeAverage(values: List<Long>): Long? {
        if (values.isEmpty()) return null
        return values.map { it.toDouble() }.average().roundToLong()
    }

    private fun computeMedian(values: List<Long>): Long? {
        if (values.isEmpty()) return null
        val sorted = values.sorted()
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 1) {
            sorted[mid]
        } else {
            ((sorted[mid - 1].toDouble() + sorted[mid].toDouble()) / 2.0).roundToLong()
        }
    }
}
