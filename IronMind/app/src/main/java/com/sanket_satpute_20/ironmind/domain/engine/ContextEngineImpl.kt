package com.sanket_satpute_20.ironmind.domain.engine

import com.sanket_satpute_20.ironmind.domain.common.Clock
import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.CommitmentStatus
import com.sanket_satpute_20.ironmind.domain.model.DueTaskItem
import com.sanket_satpute_20.ironmind.domain.model.Event
import com.sanket_satpute_20.ironmind.domain.model.EventType
import com.sanket_satpute_20.ironmind.domain.model.GoalStatus
import com.sanket_satpute_20.ironmind.domain.model.MemoryConfirmationState
import com.sanket_satpute_20.ironmind.domain.model.MemoryStatus
import com.sanket_satpute_20.ironmind.domain.model.PlanStatus
import com.sanket_satpute_20.ironmind.domain.model.ProgressInfo
import com.sanket_satpute_20.ironmind.domain.model.ProtectionSessionStatus
import com.sanket_satpute_20.ironmind.domain.model.TaskStatus
import com.sanket_satpute_20.ironmind.domain.model.barrier.BarrierConfirmationState
import com.sanket_satpute_20.ironmind.domain.model.context.*
import com.sanket_satpute_20.ironmind.domain.model.observation.ObservationType
import com.sanket_satpute_20.ironmind.domain.model.pattern.PatternStatus
import com.sanket_satpute_20.ironmind.domain.provider.AppUsageObservationProvider
import com.sanket_satpute_20.ironmind.domain.repository.AppUsageCheckpointStore
import com.sanket_satpute_20.ironmind.domain.repository.AppUsageObservationSettingsRepository
import com.sanket_satpute_20.ironmind.domain.repository.BarrierRepository
import com.sanket_satpute_20.ironmind.domain.repository.CommitmentRepository
import com.sanket_satpute_20.ironmind.domain.repository.EventRepository
import com.sanket_satpute_20.ironmind.domain.repository.GoalRepository
import com.sanket_satpute_20.ironmind.domain.repository.MemoryRepository
import com.sanket_satpute_20.ironmind.domain.repository.ObservationRepository
import com.sanket_satpute_20.ironmind.domain.repository.OutcomeRepository
import com.sanket_satpute_20.ironmind.domain.repository.PatternRepository
import com.sanket_satpute_20.ironmind.domain.repository.PlanRepository
import com.sanket_satpute_20.ironmind.domain.repository.ProtectionRepository
import com.sanket_satpute_20.ironmind.domain.repository.ReflectionRepository
import com.sanket_satpute_20.ironmind.domain.repository.TaskRepository
import com.sanket_satpute_20.ironmind.domain.usecase.goal.GetGoalProgressUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.observation.BuildFactualContextSnapshotUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.plan.GetPlanProgressUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.task.GetDueTasksUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId

class ContextEngineImpl(
    private val clock: Clock,
    private val commitmentRepository: CommitmentRepository,
    private val eventRepository: EventRepository,
    private val buildFactualContextSnapshotUseCase: BuildFactualContextSnapshotUseCase,
    private val reflectionRepository: ReflectionRepository,
    private val protectionRepository: ProtectionRepository,
    private val goalRepository: GoalRepository,
    private val patternRepository: PatternRepository,
    private val memoryRepository: MemoryRepository? = null,
    private val planRepository: PlanRepository? = null,
    private val taskRepository: TaskRepository? = null,
    private val outcomeRepository: OutcomeRepository? = null,
    private val barrierRepository: BarrierRepository? = null,
    private val appUsageObservationProvider: AppUsageObservationProvider? = null,
    private val appUsageSettingsRepository: AppUsageObservationSettingsRepository? = null,
    private val observationRepository: ObservationRepository? = null,
    private val checkpointStore: AppUsageCheckpointStore? = null,
    private val maxStaleDelayMs: Long = 4 * 60 * 60 * 1000L, // 4 hours
    private val getDueTasksUseCase: GetDueTasksUseCase? = null,
    private val getGoalProgressUseCase: GetGoalProgressUseCase? = null,
    private val getPlanProgressUseCase: GetPlanProgressUseCase? = null,
    private val zoneId: ZoneId = ZoneId.systemDefault(),
    private val shortBurstThresholdMs: Long = AppUsageIntervalAnalyzer.DEFAULT_SHORT_BURST_THRESHOLD_MS
) : ContextEngine {

    companion object {
        const val MAX_WINDOW_EVENTS = 250
        const val MAX_PRIORITY_WINDOW_EVENTS = 250
        const val MAX_WINDOW_OUTCOMES = 100
        const val MAX_WINDOW_APP_USAGE_OBSERVATIONS = 500

        val EXECUTION_AND_PROTECTION_EVENT_TYPES = listOf(
            EventType.TASK_STARTED,
            EventType.TASK_COMPLETED,
            EventType.TASK_POSTPONED,
            EventType.TASK_CANCELLED,
            EventType.TASK_ABANDONED,
            EventType.COMMITMENT_STARTED,
            EventType.COMMITMENT_COMPLETED,
            EventType.COMMITMENT_POSTPONED,
            EventType.COMMITMENT_RESCHEDULED,
            EventType.COMMITMENT_MISSED,
            EventType.PROTECTION_STARTED,
            EventType.PROTECTION_ENDED,
            EventType.PROTECTION_ENABLED,
            EventType.PROTECTION_DISABLED,
            EventType.PROTECTION_OVERRIDDEN,
            EventType.PROTECTION_EXPIRED
        )

        val ACTIVE_COMMITMENT_STATUSES = setOf(
            CommitmentStatus.PLANNED,
            CommitmentStatus.COMMITTED,
            CommitmentStatus.STARTED,
            CommitmentStatus.RECOVERED
        )
    }

    override suspend fun getCurrentContext(userId: String): Result<ContextSnapshot, Exception> = withContext(Dispatchers.IO) {
        try {
            val now = clock.currentTimeMillis()
            val localZdt = Instant.ofEpochMilli(now).atZone(zoneId)
            val dayOfWeek = (localZdt.dayOfWeek.value % 7) + 1 // Preserve Calendar 1 (Sun) .. 7 (Sat) in injected zoneId

            // 1. Fetch active commitments for today
            val commitmentsResult = commitmentRepository.getCommitmentsForUser(userId)
            val activeCommitments = if (commitmentsResult is Result.Success) {
                commitmentsResult.data.filter { it.status in ACTIVE_COMMITMENT_STATUSES }
            } else {
                emptyList()
            }

            // 2. Fetch factual context window & bounded window-scoped events (avoid unbounded getEventsForUser)
            val observationStartTimeMs = now - 86_400_000L
            val exclusiveEventEnd = if (now == Long.MAX_VALUE) Long.MAX_VALUE else now + 1L
            val eventsResult = eventRepository.getEventsForTimeWindow(
                userId = userId,
                startTime = observationStartTimeMs,
                endTime = exclusiveEventEnd,
                types = null,
                limit = MAX_WINDOW_EVENTS + 1,
                orderAsc = false
            )
            val recentEvents = if (eventsResult is Result.Success) {
                val rawEvents = eventsResult.data.filter { it.occurredAt in observationStartTimeMs..now }
                val generalRetained = rawEvents.take(MAX_WINDOW_EVENTS)
                if (rawEvents.size > MAX_WINDOW_EVENTS) {
                    val priorityResult = eventRepository.getEventsForTimeWindow(
                        userId = userId,
                        startTime = observationStartTimeMs,
                        endTime = exclusiveEventEnd,
                        types = EXECUTION_AND_PROTECTION_EVENT_TYPES,
                        limit = MAX_PRIORITY_WINDOW_EVENTS + 1,
                        orderAsc = false
                    )
                    val priorityRetained = if (priorityResult is Result.Success) {
                        priorityResult.data
                            .filter { it.occurredAt in observationStartTimeMs..now }
                            .take(MAX_PRIORITY_WINDOW_EVENTS)
                    } else {
                        emptyList()
                    }
                    (generalRetained + priorityRetained)
                        .distinctBy { it.id }
                        .sortedByDescending { it.occurredAt }
                } else {
                    generalRetained.sortedByDescending { it.occurredAt }
                }
            } else {
                emptyList()
            }

            // 3. Fetch factual context snapshot (24-hour window)
            val factualContextResult = buildFactualContextSnapshotUseCase(userId, observationStartTimeMs, now)
            if (factualContextResult is Result.Failure) {
                println("IronMindLifecycle [ContextEngine] [FACTUAL_CONTEXT_FAILED] userId=$userId error=${factualContextResult.error.message}")
                return@withContext Result.Failure(factualContextResult.error)
            }
            val factualContextSnapshot = (factualContextResult as Result.Success).data
            println("IronMindLifecycle [ContextEngine] [FACTUAL_CONTEXT_ATTACHED] userId=$userId startTimeMs=$observationStartTimeMs endTimeMs=$now")

            // 4. Fetch recent reflections
            // Using past 7 days roughly
            val startTime = now - (7 * 24 * 60 * 60 * 1000L)
            val reflectionsResult = reflectionRepository.getReflectionsForDateRange(userId, startTime, now)
            val recentReflections = if (reflectionsResult is Result.Success) {
                reflectionsResult.data.sortedByDescending { it.createdAt }.take(3)
            } else {
                emptyList()
            }

            // 5. Fetch active protection session
            val activeSessionResult = protectionRepository.getActiveProtectionSessionsForUser(userId)
            val activeProtectionSession = if (activeSessionResult is Result.Success) activeSessionResult.data.firstOrNull() else null

            // 6. Fetch active goals
            val goalsResult = goalRepository.getGoalsForUser(userId)
            val activeGoals = if (goalsResult is Result.Success) {
                goalsResult.data.filter { it.status.name == "ACTIVE" } // Assuming GoalStatus has name
            } else {
                emptyList()
            }

            // 7. Fetch active patterns (excluding INACTIVE, DELETED, EXPIRED, and USER_REJECTED)
            val patternsResult = patternRepository.getPatternsForUser(userId)
            val recentPatterns = if (patternsResult is Result.Success) {
                patternsResult.data
                    .filter { pattern ->
                        pattern.userId == userId &&
                        pattern.status !in setOf(PatternStatus.INACTIVE, PatternStatus.DELETED, PatternStatus.EXPIRED) &&
                        pattern.confirmationState != MemoryConfirmationState.USER_REJECTED
                    }
                    .sortedByDescending { it.updatedAt }
                    .take(5)
            } else {
                emptyList()
            }

            // 8. Fetch eligible active memories
            val eligibleMemories = if (memoryRepository != null) {
                val memoriesResult = memoryRepository.getActiveMemoriesForUser(userId)
                if (memoriesResult is Result.Success) {
                    memoriesResult.data
                        .filter {
                            it.userId == userId &&
                            it.status !in setOf(MemoryStatus.EXPIRED, MemoryStatus.DELETED, MemoryStatus.INACTIVE) &&
                            it.confirmationState != MemoryConfirmationState.USER_REJECTED
                        }
                        .sortedWith(
                            compareByDescending<com.sanket_satpute_20.ironmind.domain.model.Memory> { it.confidence }
                                .thenByDescending { it.lastObservedAt }
                        )
                        .take(10)
                } else {
                    emptyList()
                }
            } else {
                emptyList()
            }

            val snapshot = ContextSnapshot(
                timestamp = now,
                dayOfWeek = dayOfWeek,
                activeCommitments = activeCommitments,
                recentEvents = recentEvents,
                factualContextSnapshot = factualContextSnapshot,
                recentReflections = recentReflections,
                recentPatterns = recentPatterns,
                activeProtectionSession = activeProtectionSession,
                activeGoals = activeGoals,
                activeMemories = eligibleMemories
            )

            println("IronMindLifecycle [ContextEngine] [SNAPSHOT_GENERATED] userId=\$userId timestamp=\$now")
            Result.Success(snapshot)
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }

    override suspend fun getUnifiedContext(
        userId: String,
        windowStartTimeMs: Long?,
        windowEndTimeMs: Long?
    ): Result<UnifiedContext, Exception> = withContext(Dispatchers.IO) {
        if (userId.isBlank()) {
            return@withContext Result.Failure(IllegalArgumentException("userId cannot be blank"))
        }

        try {
            val now = clock.currentTimeMillis()
            val endTime = windowEndTimeMs ?: now
            val startTime = windowStartTimeMs ?: (endTime - 86_400_000L) // Default 24-hour window

            if (startTime >= endTime) {
                return@withContext Result.Failure(IllegalArgumentException("windowStartTimeMs must be less than windowEndTimeMs"))
            }

            val provenanceMap = mutableMapOf<String, SourceProvenanceAndFreshness>()

            // 1. PRODUCTIVITY CONTEXT
            // Goals
            val goalsResult = goalRepository.getGoalsForUser(userId)
            val allGoals = if (goalsResult is Result.Success) {
                goalsResult.data.filter { it.userId == userId }
            } else emptyList()

            val goalItems = allGoals.map { goal ->
                val progress = if (getGoalProgressUseCase != null) {
                    val pRes = getGoalProgressUseCase.invoke(goal.id, userId)
                    if (pRes is Result.Success) pRes.data else ProgressInfo.ZERO
                } else ProgressInfo.ZERO

                val statusChangeDesc = when {
                    goal.completedAt != null -> "Completed at ${goal.completedAt}"
                    goal.startedAt != null -> "Started at ${goal.startedAt}"
                    else -> "Updated at ${goal.updatedAt}"
                }

                GoalContextItem(
                    goal = goal,
                    progress = progress,
                    statusChangeDescription = statusChangeDesc,
                    authority = EpistemicAuthority.USER_CONFIRMED_FACT
                )
            }
            val activeGoals = goalItems.filter { it.goal.status == GoalStatus.ACTIVE }
            val pausedGoals = goalItems.filter { it.goal.status == GoalStatus.PAUSED }
            val completedGoals = goalItems.filter { it.goal.status == GoalStatus.COMPLETED }

            val goalLastRecorded = allGoals.maxOfOrNull { it.updatedAt }
            provenanceMap["Goals"] = SourceProvenanceAndFreshness(
                sourceName = "Room_Goal",
                availability = when {
                    goalsResult is Result.Failure -> SourceAvailability.UNAVAILABLE_HARDWARE
                    allGoals.isEmpty() -> SourceAvailability.EMPTY_NO_DATA
                    else -> SourceAvailability.AVAILABLE
                },
                isPermissionGranted = true,
                lastRecordedAt = goalLastRecorded,
                dataAgeMs = goalLastRecorded?.let { now - it },
                isStale = if (goalsResult is Result.Failure) false else goalLastRecorded?.let { (now - it) > 7 * 86_400_000L } ?: false,
                notes = if (goalsResult is Result.Failure) "Goal history unavailable: ${goalsResult.error.message}" else null
            )

            // Plans
            val plansResult = planRepository?.getPlansForUser(userId)
            val allPlans = if (plansResult is Result.Success) {
                plansResult.data.filter { it.userId == userId }
            } else emptyList()

            val goalTitleMap = allGoals.associate { it.id to it.title }
            val planItems = allPlans.map { plan ->
                val progress = if (getPlanProgressUseCase != null) {
                    val pRes = getPlanProgressUseCase.invoke(plan.id, userId)
                    if (pRes is Result.Success) pRes.data else ProgressInfo.ZERO
                } else ProgressInfo.ZERO

                PlanContextItem(
                    plan = plan,
                    parentGoalTitle = plan.goalId?.let { goalTitleMap[it] },
                    progress = progress,
                    authority = EpistemicAuthority.USER_CONFIRMED_FACT
                )
            }
            val activePlans = planItems.filter { it.plan.status == PlanStatus.ACTIVE }
            val pausedPlans = planItems.filter { it.plan.status == PlanStatus.PAUSED }
            val completedPlans = planItems.filter { it.plan.status == PlanStatus.COMPLETED }

            val planLastRecorded = allPlans.maxOfOrNull { it.updatedAt }
            provenanceMap["Plans"] = SourceProvenanceAndFreshness(
                sourceName = "Room_Plan",
                availability = when {
                    planRepository == null || plansResult is Result.Failure -> SourceAvailability.UNAVAILABLE_HARDWARE
                    allPlans.isEmpty() -> SourceAvailability.EMPTY_NO_DATA
                    else -> SourceAvailability.AVAILABLE
                },
                isPermissionGranted = true,
                lastRecordedAt = planLastRecorded,
                dataAgeMs = planLastRecorded?.let { now - it },
                isStale = if (planRepository == null || plansResult is Result.Failure) false else planLastRecorded?.let { (now - it) > 7 * 86_400_000L } ?: false,
                notes = when {
                    planRepository == null -> "PlanRepository not configured"
                    plansResult is Result.Failure -> "Plan history unavailable: ${plansResult.error.message}"
                    else -> null
                }
            )

            // Tasks
            val tasksResult = taskRepository?.getTasksForUser(userId)
            val allTasks = if (tasksResult is Result.Success) {
                tasksResult.data.filter { it.userId == userId }
            } else emptyList()

            val dueTasksResult = getDueTasksUseCase?.invoke(
                userId = userId,
                referenceTimeMillis = now,
                zoneId = zoneId
            )
            val (dueToday, overdue) = if (dueTasksResult is Result.Success) {
                Pair(dueTasksResult.data.dueToday, dueTasksResult.data.overdue)
            } else {
                Pair(emptyList<DueTaskItem>(), emptyList<DueTaskItem>())
            }

            val taskSummary = TaskContextSummary(
                allTasks = allTasks,
                pendingTasks = allTasks.filter { it.status == TaskStatus.PENDING },
                inProgressTasks = allTasks.filter { it.status == TaskStatus.IN_PROGRESS },
                completedTasks = allTasks.filter { it.status == TaskStatus.COMPLETED },
                overdueTasks = overdue,
                dueTodayTasks = dueToday,
                postponedTasks = allTasks.filter { it.status == TaskStatus.POSTPONED },
                failedTasks = allTasks.filter { it.status == TaskStatus.FAILED },
                cancelledTasks = allTasks.filter { it.status == TaskStatus.CANCELLED }
            )

            val taskLastRecorded = allTasks.maxOfOrNull { it.updatedAt }
            provenanceMap["Tasks"] = SourceProvenanceAndFreshness(
                sourceName = "Room_Task",
                availability = when {
                    taskRepository == null || tasksResult is Result.Failure -> SourceAvailability.UNAVAILABLE_HARDWARE
                    allTasks.isEmpty() -> SourceAvailability.EMPTY_NO_DATA
                    else -> SourceAvailability.AVAILABLE
                },
                isPermissionGranted = true,
                lastRecordedAt = taskLastRecorded,
                dataAgeMs = taskLastRecorded?.let { now - it },
                isStale = if (taskRepository == null || tasksResult is Result.Failure) false else taskLastRecorded?.let { (now - it) > 7 * 86_400_000L } ?: false,
                notes = listOfNotNull(
                    when {
                        taskRepository == null -> "TaskRepository not configured"
                        tasksResult is Result.Failure -> "Task history unavailable: ${tasksResult.error.message}"
                        else -> null
                    },
                    if (dueTasksResult is Result.Failure) "Due task classification unavailable: ${dueTasksResult.error.message}" else null
                ).takeIf { it.isNotEmpty() }?.joinToString("; ")
            )

            // Commitments
            val commitmentsResult = commitmentRepository.getCommitmentsForUser(userId)
            val allCommitments = if (commitmentsResult is Result.Success) {
                commitmentsResult.data.filter { it.userId == userId }
            } else emptyList()

            val todayCommitments = allCommitments.filter {
                (it.createdAt in startTime..endTime) ||
                (it.committedAt in startTime..endTime)
            }
            val activeCommitments = allCommitments.filter { it.status in ACTIVE_COMMITMENT_STATUSES }
            val completedCommitments = allCommitments.filter { it.status == CommitmentStatus.COMPLETED }
            val missedCommitments = allCommitments.filter { it.status == CommitmentStatus.MISSED }
            val postponedCommitments = allCommitments.filter { it.status == CommitmentStatus.POSTPONED }

            // Repeated missed commitments where evidence exists (>= 2 times)
            val repeatedMissed = missedCommitments
                .groupBy { it.taskId ?: it.title.trim().lowercase() }
                .filter { it.value.size >= 2 }
                .map { entry ->
                    val sample = entry.value.maxByOrNull { it.updatedAt } ?: entry.value.first()
                    RepeatedMissedCommitment(
                        title = sample.title,
                        taskId = sample.taskId,
                        planId = sample.planId,
                        goalId = sample.goalId,
                        missCount = entry.value.size,
                        lastMissedAt = entry.value.mapNotNull { it.missedAt ?: it.updatedAt }.maxOrNull() ?: sample.updatedAt,
                        evidenceSummaries = entry.value.map { "Missed on ${it.committedAt}" }
                    )
                }

            val commitmentSummary = CommitmentContextSummary(
                todayCommitments = todayCommitments,
                activeCommitments = activeCommitments,
                completedCommitments = completedCommitments,
                missedCommitments = missedCommitments,
                postponedCommitments = postponedCommitments,
                repeatedMissedCommitments = repeatedMissed
            )

            val commLastRecorded = allCommitments.maxOfOrNull { it.updatedAt }
            provenanceMap["Commitments"] = SourceProvenanceAndFreshness(
                sourceName = "Room_Commitment",
                availability = when {
                    commitmentsResult is Result.Failure -> SourceAvailability.UNAVAILABLE_HARDWARE
                    allCommitments.isEmpty() -> SourceAvailability.EMPTY_NO_DATA
                    else -> SourceAvailability.AVAILABLE
                },
                isPermissionGranted = true,
                lastRecordedAt = commLastRecorded,
                dataAgeMs = commLastRecorded?.let { now - it },
                isStale = if (commitmentsResult is Result.Failure) false else commLastRecorded?.let { (now - it) > 7 * 86_400_000L } ?: false,
                notes = if (commitmentsResult is Result.Failure) "Commitment history unavailable: ${commitmentsResult.error.message}" else null
            )

            // Reflections
            val reflectionsResult = reflectionRepository.getReflectionsForDateRange(userId, startTime, endTime)
            val recentReflections = if (reflectionsResult is Result.Success) {
                reflectionsResult.data.filter { it.userId == userId }.sortedByDescending { it.createdAt }.take(5)
            } else emptyList()

            val refLastRecorded = recentReflections.firstOrNull()?.createdAt
            provenanceMap["Reflections"] = SourceProvenanceAndFreshness(
                sourceName = "Room_Reflection",
                availability = when {
                    reflectionsResult is Result.Failure -> SourceAvailability.UNAVAILABLE_HARDWARE
                    recentReflections.isEmpty() -> SourceAvailability.EMPTY_NO_DATA
                    else -> SourceAvailability.AVAILABLE
                },
                isPermissionGranted = true,
                lastRecordedAt = refLastRecorded,
                dataAgeMs = refLastRecorded?.let { now - it },
                isStale = if (reflectionsResult is Result.Failure) false else refLastRecorded?.let { (now - it) > 7 * 86_400_000L } ?: false,
                notes = if (reflectionsResult is Result.Failure) "Reflection history unavailable: ${reflectionsResult.error.message}" else null
            )

            // Outcomes (window-scoped with explicit cap & failure reporting)
            val outcomesResult = outcomeRepository?.getOutcomesForUser(userId)
            val windowOutcomes = if (outcomesResult is Result.Success) {
                outcomesResult.data
                    .filter { it.userId == userId && it.completedAt in startTime..endTime }
                    .sortedWith(compareByDescending<com.sanket_satpute_20.ironmind.domain.model.Outcome> { it.completedAt }.thenBy { it.id })
            } else emptyList()
            val outcomesTruncated = windowOutcomes.size > MAX_WINDOW_OUTCOMES
            val recentOutcomes = windowOutcomes.take(MAX_WINDOW_OUTCOMES)

            val outcomeLastRecorded = recentOutcomes.firstOrNull()?.completedAt
            provenanceMap["Outcomes"] = SourceProvenanceAndFreshness(
                sourceName = "Room_Outcome",
                availability = when {
                    outcomeRepository != null && outcomesResult is Result.Failure -> SourceAvailability.UNAVAILABLE_HARDWARE
                    recentOutcomes.isEmpty() -> SourceAvailability.EMPTY_NO_DATA
                    else -> SourceAvailability.AVAILABLE
                },
                isPermissionGranted = true,
                lastRecordedAt = outcomeLastRecorded,
                dataAgeMs = outcomeLastRecorded?.let { now - it },
                isStale = outcomeLastRecorded?.let { (now - it) > 7 * 86_400_000L } ?: false,
                notes = when {
                    outcomesResult is Result.Failure -> "Outcome history unavailable: ${outcomesResult.error.message}"
                    outcomesTruncated -> "Window outcomes truncated at cap=$MAX_WINDOW_OUTCOMES (total=${windowOutcomes.size})"
                    else -> null
                }
            )

            // Recent Events (window-scoped via getEventsForTimeWindow; prioritizes execution/protection events when capped)
            val exclusiveEventWindowEnd = if (endTime == Long.MAX_VALUE) Long.MAX_VALUE else endTime + 1L
            val eventsResult = eventRepository.getEventsForTimeWindow(
                userId = userId,
                startTime = startTime,
                endTime = exclusiveEventWindowEnd,
                types = null,
                limit = MAX_WINDOW_EVENTS + 1,
                orderAsc = false
            )
            var eventRepoAvailable = true
            var isEventHistoryTruncated = false
            var recentEvents: List<Event> = emptyList()

            when (eventsResult) {
                is Result.Success -> {
                    val rawWindowEvents = eventsResult.data
                        .filter { it.userId == userId && it.occurredAt in startTime..endTime }
                    val generalTruncated = rawWindowEvents.size > MAX_WINDOW_EVENTS
                    val retainedGeneral = rawWindowEvents.take(MAX_WINDOW_EVENTS)

                    if (!generalTruncated) {
                        recentEvents = retainedGeneral
                            .distinctBy { it.id }
                            .sortedWith(compareByDescending<Event> { it.occurredAt }.thenBy { it.id })
                    } else {
                        isEventHistoryTruncated = true
                        val priorityResult = eventRepository.getEventsForTimeWindow(
                            userId = userId,
                            startTime = startTime,
                            endTime = exclusiveEventWindowEnd,
                            types = EXECUTION_AND_PROTECTION_EVENT_TYPES,
                            limit = MAX_PRIORITY_WINDOW_EVENTS + 1,
                            orderAsc = false
                        )
                        val priorityRetained = if (priorityResult is Result.Success) {
                            priorityResult.data
                                .filter { it.userId == userId && it.occurredAt in startTime..endTime }
                                .take(MAX_PRIORITY_WINDOW_EVENTS)
                        } else {
                            emptyList()
                        }
                        recentEvents = (retainedGeneral + priorityRetained)
                            .distinctBy { it.id }
                            .sortedWith(compareByDescending<Event> { it.occurredAt }.thenBy { it.id })
                    }
                }
                is Result.Failure -> {
                    eventRepoAvailable = false
                    recentEvents = emptyList()
                }
            }

            val eventLastRecorded = recentEvents.firstOrNull()?.occurredAt
            provenanceMap["Events"] = SourceProvenanceAndFreshness(
                sourceName = "Room_Event",
                availability = when {
                    !eventRepoAvailable -> SourceAvailability.UNAVAILABLE_HARDWARE
                    recentEvents.isEmpty() -> SourceAvailability.EMPTY_NO_DATA
                    else -> SourceAvailability.AVAILABLE
                },
                isPermissionGranted = true,
                lastRecordedAt = eventLastRecorded,
                dataAgeMs = eventLastRecorded?.let { now - it },
                isStale = false,
                notes = when {
                    eventsResult is Result.Failure -> "Event history unavailable: ${eventsResult.error.message}"
                    isEventHistoryTruncated -> "Window event history truncated at cap=$MAX_WINDOW_EVENTS; execution and protection lifecycle events prioritized"
                    else -> null
                }
            )

            val executionTiming = ExecutionTimingAnalyzer.analyze(
                userId = userId,
                tasks = allTasks,
                events = recentEvents,
                windowStartMs = startTime,
                windowEndMs = endTime,
                zoneId = zoneId,
                eventRepositoryAvailable = eventRepoAvailable,
                isEventHistoryTruncated = isEventHistoryTruncated,
                retainedWindowEventCount = recentEvents.size
            )

            // User-confirmed observations and barriers
            val userConfirmedBarriers = if (barrierRepository != null) {
                val bRes = barrierRepository.getBarriersForUser(userId)
                if (bRes is Result.Success) {
                    bRes.data.filter { it.userId == userId && it.confirmationState == BarrierConfirmationState.CONFIRMED }
                } else emptyList()
            } else emptyList()

            val userConfirmedObservations = if (observationRepository != null) {
                val obsRes = observationRepository.getObservationsForTimeWindow(
                    userId = userId,
                    type = com.sanket_satpute_20.ironmind.domain.model.observation.ObservationType.USER_INPUT,
                    startTimeMs = startTime,
                    endTimeMs = endTime
                )
                if (obsRes is Result.Success) {
                    obsRes.data.filter { it.userId == userId && it.provenance.source == com.sanket_satpute_20.ironmind.domain.model.observation.ObservationSource.USER }
                } else emptyList()
            } else emptyList()

            val productivityContext = ProductivityContext(
                activeGoals = activeGoals,
                pausedGoals = pausedGoals,
                completedGoals = completedGoals,
                activePlans = activePlans,
                pausedPlans = pausedPlans,
                completedPlans = completedPlans,
                taskSummary = taskSummary,
                commitmentSummary = commitmentSummary,
                recentReflections = recentReflections,
                recentEvents = recentEvents,
                userConfirmedObservations = userConfirmedObservations,
                recentOutcomes = recentOutcomes,
                userConfirmedBarriers = userConfirmedBarriers,
                executionTiming = executionTiming
            )

            // 2. DIGITAL BEHAVIOUR CONTEXT
            val isUsageSettingEnabled = appUsageSettingsRepository?.getSettings(userId)?.let {
                if (it is Result.Success) it.data.isEnabled else true
            } ?: true

            val isUsagePermissionGranted = appUsageObservationProvider?.isPermissionGranted() ?: false

            val (permStatus, baseAvailability, digitalNotes) = when {
                appUsageObservationProvider == null ->
                    Triple(UsagePermissionStatus.UNAVAILABLE, SourceAvailability.UNAVAILABLE_HARDWARE, "AppUsageObservationProvider not configured")
                !isUsageSettingEnabled ->
                    Triple(UsagePermissionStatus.DENIED, SourceAvailability.PERMISSION_NOT_REQUESTED, "Digital usage observation disabled in user settings")
                !isUsagePermissionGranted ->
                    Triple(UsagePermissionStatus.DENIED, SourceAvailability.PERMISSION_DENIED, "PACKAGE_USAGE_STATS permission not granted in Android Settings")
                observationRepository == null ->
                    Triple(UsagePermissionStatus.UNAVAILABLE, SourceAvailability.UNAVAILABLE_HARDWARE, "ObservationRepository not configured")
                else ->
                    Triple(UsagePermissionStatus.GRANTED, SourceAvailability.AVAILABLE, null)
            }

            var retainedUsageObservations = emptyList<com.sanket_satpute_20.ironmind.domain.model.observation.Observation>()
            var isUsageQueryTruncated = false
            var finalDigitalAvailability = baseAvailability
            var isDigitalStale = false
            var isUsageRepoReadSuccess = false

            val checkpointResult = checkpointStore?.getCheckpoint(userId)
            val checkpoint = (checkpointResult as? Result.Success)?.data

            if (permStatus == UsagePermissionStatus.GRANTED && observationRepository != null) {
                val usageQueryStartMs = (endTime - AppUsageIntervalAnalyzer.MAX_LOOKBACK_MS).coerceAtLeast(0L)
                val obsResult = observationRepository.getObservationsForTimeWindow(
                    userId = userId,
                    startTimeMs = usageQueryStartMs,
                    endTimeMs = endTime,
                    types = listOf(ObservationType.APP_USAGE_SESSION),
                    limit = MAX_WINDOW_APP_USAGE_OBSERVATIONS + 1,
                    orderAsc = false
                )
                if (obsResult is Result.Success) {
                    isUsageRepoReadSuccess = true
                    val userObs = obsResult.data.filter { it.userId == userId }
                    isUsageQueryTruncated = userObs.size > MAX_WINDOW_APP_USAGE_OBSERVATIONS
                    retainedUsageObservations = userObs.take(MAX_WINDOW_APP_USAGE_OBSERVATIONS)
                } else if (obsResult is Result.Failure) {
                    finalDigitalAvailability = SourceAvailability.UNAVAILABLE_HARDWARE
                }
            }

            // Focus and Protection Activity
            val sessionsResult = protectionRepository.getProtectionSessionsForUser(userId)
            val rulesResult = protectionRepository.getProtectionRulesForUser(userId)
            val isProtectionSessionsAvailable = sessionsResult is Result.Success
            val isProtectionDataAvailable = isProtectionSessionsAvailable && rulesResult is Result.Success

            val trackableStatuses = setOf(
                ProtectionSessionStatus.ACTIVE,
                ProtectionSessionStatus.COMPLETED,
                ProtectionSessionStatus.CANCELLED,
                ProtectionSessionStatus.OVERRIDDEN,
                ProtectionSessionStatus.EXPIRED
            )

            val allUserSessions = if (sessionsResult is Result.Success) {
                sessionsResult.data.filter { it.userId == userId }
            } else {
                emptyList()
            }
            val recentSessions = allUserSessions.filter { session ->
                if (session.status !in trackableStatuses) return@filter false
                val sessionEnd = AppUsageIntervalAnalyzer.effectiveProtectionEndMs(session, now) ?: return@filter false
                session.startedAt <= endTime && sessionEnd >= startTime
            }
            val activeSessions = recentSessions.filter { session ->
                session.status == ProtectionSessionStatus.ACTIVE &&
                    (session.scheduledEndAt == null || now < session.scheduledEndAt)
            }
            val rules = if (rulesResult is Result.Success) {
                rulesResult.data.filter { it.userId == userId }
            } else {
                emptyList()
            }

            val totalFocusDuration = recentSessions.sumOf { session ->
                val rawEnd = AppUsageIntervalAnalyzer.effectiveProtectionEndMs(session, now) ?: return@sumOf 0L
                val effectiveStart = maxOf(session.startedAt, startTime)
                val effectiveEnd = minOf(rawEnd, endTime)
                (effectiveEnd - effectiveStart).coerceAtLeast(0L)
            }

            val focusSummary = FocusActivitySummary(
                activeSessions = activeSessions,
                recentSessions = recentSessions,
                totalFocusDurationMs = totalFocusDuration,
                activeRules = rules
            )

            // Authoritative single-path interval, switching, burst, and protection-overlap analysis
            val isUsageSourceReadable = permStatus == UsagePermissionStatus.GRANTED &&
                observationRepository != null &&
                isUsageRepoReadSuccess

            val preliminaryAnalysis = AppUsageIntervalAnalyzer.analyze(
                userId = userId,
                observations = retainedUsageObservations,
                protectionSessions = allUserSessions,
                windowStartMs = startTime,
                windowEndMs = endTime,
                nowMs = now,
                usageAvailability = finalDigitalAvailability,
                isUsageSourceReadable = isUsageSourceReadable,
                isProtectionHistoryAvailable = isProtectionSessionsAvailable,
                isTruncated = isUsageQueryTruncated,
                shortBurstThresholdMs = shortBurstThresholdMs
            )

            if (isUsageRepoReadSuccess && observationRepository != null) {
                val dataAge = checkpoint?.lastCollectedIntervalEndMs?.let { now - it }
                isDigitalStale = dataAge?.let { it > maxStaleDelayMs } ?: false

                if (checkpoint != null && isDigitalStale) {
                    finalDigitalAvailability = SourceAvailability.STALE_DATA
                } else if (preliminaryAnalysis.appSummaries.isNotEmpty()) {
                    if (checkpoint == null || checkpoint.lastCollectedIntervalEndMs < endTime) {
                        finalDigitalAvailability = SourceAvailability.PARTIAL_HISTORY
                    } else {
                        finalDigitalAvailability = SourceAvailability.AVAILABLE
                    }
                } else {
                    if (checkpoint != null && checkpoint.lastCollectedIntervalEndMs >= endTime) {
                        val priorResult = observationRepository.getObservationsForTimeWindow(
                            userId = userId,
                            type = ObservationType.APP_USAGE_SESSION,
                            startTimeMs = 0L,
                            endTimeMs = startTime
                        )
                        val hasPriorCoverage = (priorResult is Result.Success && priorResult.data.any { it.userId == userId }) ||
                            (checkpoint.activeSessionStartTimeMs != null && checkpoint.activeSessionStartTimeMs <= startTime)

                        if (hasPriorCoverage) {
                            finalDigitalAvailability = SourceAvailability.EMPTY_NO_DATA
                        } else {
                            finalDigitalAvailability = SourceAvailability.PARTIAL_HISTORY
                        }
                    } else {
                        finalDigitalAvailability = SourceAvailability.PARTIAL_HISTORY
                    }
                }
            }

            val intervalAnalysis = if (preliminaryAnalysis.temporalSummary.provenance.usageSourceAvailability != finalDigitalAvailability) {
                preliminaryAnalysis.copy(
                    temporalSummary = preliminaryAnalysis.temporalSummary.copy(
                        provenance = preliminaryAnalysis.temporalSummary.provenance.copy(
                            usageSourceAvailability = finalDigitalAvailability
                        )
                    )
                )
            } else {
                preliminaryAnalysis
            }

            val appSummaries = intervalAnalysis.appSummaries
            val lastObservedUsageTime = intervalAnalysis.lastObservedAt

            provenanceMap["AppUsage"] = SourceProvenanceAndFreshness(
                sourceName = "Room_Observation",
                availability = finalDigitalAvailability,
                isPermissionGranted = isUsagePermissionGranted,
                lastRecordedAt = lastObservedUsageTime ?: checkpoint?.lastCollectedIntervalEndMs,
                dataAgeMs = checkpoint?.lastCollectedIntervalEndMs?.let { now - it }
                    ?: (lastObservedUsageTime?.let { now - it }),
                isStale = isDigitalStale,
                notes = listOfNotNull(
                    digitalNotes ?: when (finalDigitalAvailability) {
                        SourceAvailability.PARTIAL_HISTORY -> "Collection history is incomplete for requested window [${startTime}, ${endTime}]"
                        SourceAvailability.STALE_DATA -> "Collection checkpoint is older than freshness threshold (${maxStaleDelayMs / 3600_000}h)"
                        else -> null
                    },
                    if (isUsageQueryTruncated) "App usage query truncated at cap=$MAX_WINDOW_APP_USAGE_OBSERVATIONS" else null,
                    if (intervalAnalysis.temporalSummary.provenance.isLookbackClamped) {
                        "Lookback clamped to maximum ${AppUsageIntervalAnalyzer.MAX_LOOKBACK_MS}ms window"
                    } else null
                ).takeIf { it.isNotEmpty() }?.joinToString("; ")
            )

            val focusLastRecordedAt = recentSessions.maxOfOrNull { it.endedAt ?: it.startedAt }
                ?: activeSessions.maxOfOrNull { it.startedAt }
            provenanceMap["FocusProtection"] = SourceProvenanceAndFreshness(
                sourceName = "Room_Protection",
                availability = when {
                    !isProtectionDataAvailable -> SourceAvailability.UNAVAILABLE_HARDWARE
                    recentSessions.isEmpty() && activeSessions.isEmpty() && rules.isEmpty() -> SourceAvailability.EMPTY_NO_DATA
                    else -> SourceAvailability.AVAILABLE
                },
                isPermissionGranted = true,
                lastRecordedAt = focusLastRecordedAt,
                dataAgeMs = focusLastRecordedAt?.let { now - it },
                isStale = false,
                notes = if (!isProtectionDataAvailable) "Protection session history unavailable due to repository failure" else null
            )

            val digitalContext = DigitalBehaviourContext(
                permissionStatus = permStatus,
                availability = finalDigitalAvailability,
                windowStartTimeMs = startTime,
                windowEndTimeMs = endTime,
                appUsages = appSummaries,
                focusActivity = focusSummary,
                recentProtectionEvents = recentEvents.filter { it.type.name.startsWith("PROTECTION_") },
                lastObservedAt = lastObservedUsageTime,
                notes = digitalNotes,
                usageIntervals = intervalAnalysis.intervals,
                usageTemporalSummary = intervalAnalysis.temporalSummary
            )

            // 3. CROSS-DOMAIN CONTEXT (TENTATIVE CORRELATIONS, NON-CAUSAL)
            val correlations = mutableListOf<CrossDomainCorrelation>()

            // Heuristic 1: Non-causal observation of app usage during pending / overdue tasks or missed commitments
            val pendingOrOverdue = overdue.map { it.task }.ifEmpty { taskSummary.pendingTasks.take(1) }
            val topApp = appSummaries.firstOrNull()
            if (pendingOrOverdue.isNotEmpty() && topApp != null && topApp.totalDurationMs >= 600_000L) {
                val targetTask = pendingOrOverdue.first()
                correlations.add(
                    CrossDomainCorrelation(
                        id = "corr_app_pending_${now}_${targetTask.id}",
                        title = "App activity observed with pending or overdue tasks",
                        description = "Observed ${topApp.totalDurationMs / 60_000}m usage in ${topApp.packageName} while '${targetTask.title}' was pending or overdue.",
                        productivityEntityId = targetTask.id,
                        productivityEntityType = "TASK",
                        productivityEntityTitle = targetTask.title,
                        digitalAppPackage = topApp.packageName,
                        digitalUsageDurationMs = topApp.totalDurationMs,
                        observedWindowStartMs = startTime,
                        observedWindowEndMs = endTime,
                        correlationType = CrossDomainCorrelationType.CONCURRENT_DIGITAL_ACTIVITY_DURING_PENDING_TASK,
                        confidence = 0.65f,
                        authority = EpistemicAuthority.AI_HYPOTHESIS,
                        disclaimer = "Tentative correlation hypothesis only. Does not imply causation, intent, or confirmed distraction."
                    )
                )
            }

            // Heuristic 2: Protection session temporally overlapping actual task completion timestamp
            val completionEventsByTaskId = recentEvents
                .filter { it.userId == userId && it.type == EventType.TASK_COMPLETED && it.entityId != null }
                .groupBy { it.entityId!! }
            val completedTaskCandidates = taskSummary.completedTasks
                .filter { it.userId == userId }
                .mapNotNull { task ->
                    val actualCompletedAt = completionEventsByTaskId[task.id]?.maxOfOrNull { it.occurredAt }
                        ?: task.completedAt
                    actualCompletedAt?.let { task to it }
                }
                .filter { (_, completedAt) -> completedAt in startTime..endTime }

            if (recentSessions.isNotEmpty() && completedTaskCandidates.isNotEmpty()) {
                var matchedPair: Triple<com.sanket_satpute_20.ironmind.domain.model.Task, com.sanket_satpute_20.ironmind.domain.model.ProtectionSession, Long>? = null
                outer@ for ((task, completedAt) in completedTaskCandidates) {
                    for (session in recentSessions) {
                        val effectiveEnd = AppUsageIntervalAnalyzer.effectiveProtectionEndMs(session, now) ?: continue
                        val clippedStart = maxOf(session.startedAt, startTime)
                        val clippedEnd = minOf(effectiveEnd, endTime)
                        if (clippedEnd >= clippedStart && completedAt in clippedStart..clippedEnd) {
                            matchedPair = Triple(task, session, (clippedEnd - clippedStart).coerceAtLeast(0L))
                            break@outer
                        }
                    }
                }
                matchedPair?.let { (completedTask, _, clippedProtectionMs) ->
                    correlations.add(
                        CrossDomainCorrelation(
                            id = "corr_focus_complete_${now}_${completedTask.id}",
                            title = "Focus session active at task completion",
                            description = "Task '${completedTask.title}' completion timestamp overlapped a focus protection session.",
                            productivityEntityId = completedTask.id,
                            productivityEntityType = "TASK",
                            productivityEntityTitle = completedTask.title,
                            digitalAppPackage = null,
                            digitalUsageDurationMs = clippedProtectionMs,
                            observedWindowStartMs = startTime,
                            observedWindowEndMs = endTime,
                            correlationType = CrossDomainCorrelationType.FOCUS_PROTECTION_DURING_COMPLETED_TASK,
                            confidence = 0.85f,
                            authority = EpistemicAuthority.AI_HYPOTHESIS,
                            disclaimer = "Tentative correlation hypothesis only. Does not imply causation or exclusive attribution."
                        )
                    )
                }
            }

            // 4. UNIFIED CONTEXT
            val isFresh = provenanceMap.values.none { it.isStale }
            val isDegraded = permStatus != UsagePermissionStatus.GRANTED

            val unified = UnifiedContext(
                userId = userId,
                generatedAt = now,
                windowStartTimeMs = startTime,
                windowEndTimeMs = endTime,
                productivity = productivityContext,
                digitalBehaviour = digitalContext,
                correlations = correlations,
                provenanceMap = provenanceMap,
                isFresh = isFresh,
                isDegraded = isDegraded
            )

            println("IronMindLifecycle [ContextEngine] [UNIFIED_CONTEXT_GENERATED] userId=$userId fresh=$isFresh degraded=$isDegraded correlations=${correlations.size}")
            Result.Success(unified)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            println("IronMindLifecycle [ContextEngine] [UNIFIED_CONTEXT_FAILED] userId=$userId error=${e.message}")
            Result.Failure(e)
        }
    }
}
