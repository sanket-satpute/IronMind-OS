package com.sanket_satpute_20.ironmind.domain.engine

import com.sanket_satpute_20.ironmind.domain.common.Clock
import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.CommitmentStatus
import com.sanket_satpute_20.ironmind.domain.model.DueTaskItem
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
import java.util.Calendar

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
    private val getPlanProgressUseCase: GetPlanProgressUseCase? = null
) : ContextEngine {

    companion object {
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
            val calendar = Calendar.getInstance().apply { timeInMillis = now }
            val dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)
            
            // 1. Fetch active commitments for today
            val commitmentsResult = commitmentRepository.getCommitmentsForUser(userId)
            val activeCommitments = if (commitmentsResult is Result.Success) {
                commitmentsResult.data.filter { it.status in ACTIVE_COMMITMENT_STATUSES }
            } else {
                emptyList()
            }

            // 2. Fetch recent events
            val eventsResult = eventRepository.getEventsForUser(userId)
            val recentEvents = if (eventsResult is Result.Success) {
                eventsResult.data.sortedByDescending { it.occurredAt }.take(10)
            } else {
                emptyList()
            }

            // 3. Fetch factual context snapshot (24-hour window)
            val observationStartTimeMs = now - 86_400_000L
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
                availability = if (allGoals.isEmpty()) SourceAvailability.EMPTY_NO_DATA else SourceAvailability.AVAILABLE,
                isPermissionGranted = true,
                lastRecordedAt = goalLastRecorded,
                dataAgeMs = goalLastRecorded?.let { now - it },
                isStale = goalLastRecorded?.let { (now - it) > 7 * 86_400_000L } ?: false
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
                availability = if (allPlans.isEmpty()) SourceAvailability.EMPTY_NO_DATA else SourceAvailability.AVAILABLE,
                isPermissionGranted = true,
                lastRecordedAt = planLastRecorded,
                dataAgeMs = planLastRecorded?.let { now - it },
                isStale = planLastRecorded?.let { (now - it) > 7 * 86_400_000L } ?: false
            )

            // Tasks
            val tasksResult = taskRepository?.getTasksForUser(userId)
            val allTasks = if (tasksResult is Result.Success) {
                tasksResult.data.filter { it.userId == userId }
            } else emptyList()

            val dueTasksResult = getDueTasksUseCase?.invoke(userId, referenceTimeMillis = now)
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
                availability = if (allTasks.isEmpty()) SourceAvailability.EMPTY_NO_DATA else SourceAvailability.AVAILABLE,
                isPermissionGranted = true,
                lastRecordedAt = taskLastRecorded,
                dataAgeMs = taskLastRecorded?.let { now - it },
                isStale = taskLastRecorded?.let { (now - it) > 7 * 86_400_000L } ?: false
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
                availability = if (allCommitments.isEmpty()) SourceAvailability.EMPTY_NO_DATA else SourceAvailability.AVAILABLE,
                isPermissionGranted = true,
                lastRecordedAt = commLastRecorded,
                dataAgeMs = commLastRecorded?.let { now - it },
                isStale = commLastRecorded?.let { (now - it) > 7 * 86_400_000L } ?: false
            )

            // Reflections
            val reflectionsResult = reflectionRepository.getReflectionsForDateRange(userId, startTime, endTime)
            val recentReflections = if (reflectionsResult is Result.Success) {
                reflectionsResult.data.filter { it.userId == userId }.sortedByDescending { it.createdAt }.take(5)
            } else emptyList()

            val refLastRecorded = recentReflections.firstOrNull()?.createdAt
            provenanceMap["Reflections"] = SourceProvenanceAndFreshness(
                sourceName = "Room_Reflection",
                availability = if (recentReflections.isEmpty()) SourceAvailability.EMPTY_NO_DATA else SourceAvailability.AVAILABLE,
                isPermissionGranted = true,
                lastRecordedAt = refLastRecorded,
                dataAgeMs = refLastRecorded?.let { now - it },
                isStale = refLastRecorded?.let { (now - it) > 7 * 86_400_000L } ?: false
            )

            // Outcomes
            val outcomesResult = outcomeRepository?.getOutcomesForUser(userId)
            val recentOutcomes = if (outcomesResult is Result.Success) {
                outcomesResult.data.filter { it.userId == userId }.sortedByDescending { it.completedAt }.take(10)
            } else emptyList()

            val outcomeLastRecorded = recentOutcomes.firstOrNull()?.completedAt
            provenanceMap["Outcomes"] = SourceProvenanceAndFreshness(
                sourceName = "Room_Outcome",
                availability = if (recentOutcomes.isEmpty()) SourceAvailability.EMPTY_NO_DATA else SourceAvailability.AVAILABLE,
                isPermissionGranted = true,
                lastRecordedAt = outcomeLastRecorded,
                dataAgeMs = outcomeLastRecorded?.let { now - it },
                isStale = outcomeLastRecorded?.let { (now - it) > 7 * 86_400_000L } ?: false
            )

            // Recent Events
            val eventsResult = eventRepository.getEventsForUser(userId)
            val recentEvents = if (eventsResult is Result.Success) {
                eventsResult.data.filter { it.userId == userId }.sortedByDescending { it.occurredAt }.take(15)
            } else emptyList()

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
                userConfirmedBarriers = userConfirmedBarriers
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

            val appSummaries = mutableListOf<AppUsageSummary>()
            var lastObservedUsageTime: Long? = null
            var finalDigitalAvailability = baseAvailability
            var isDigitalStale = false

            val checkpointResult = checkpointStore?.getCheckpoint(userId)
            val checkpoint = (checkpointResult as? Result.Success)?.data

            if (permStatus == UsagePermissionStatus.GRANTED && observationRepository != null) {
                val obsResult = observationRepository.getObservationsForTimeWindow(
                    userId = userId,
                    type = ObservationType.APP_USAGE_SESSION,
                    startTimeMs = startTime,
                    endTimeMs = endTime
                )
                if (obsResult is Result.Success) {
                    val observations = obsResult.data.filter { it.userId == userId }
                    val grouped = observations.groupBy { it.value }
                    for ((pkg, obsList) in grouped) {
                        val totalDuration = obsList.sumOf { obs ->
                            obs.context.removePrefix("duration_ms=").toLongOrNull() ?: 0L
                        }
                        val firstObs = obsList.minOfOrNull { it.occurredAt }
                        val lastObs = obsList.maxOfOrNull { obs ->
                            val dur = obs.context.removePrefix("duration_ms=").toLongOrNull() ?: 0L
                            obs.occurredAt + dur
                        }
                        if (lastObs != null && (lastObservedUsageTime == null || lastObs > lastObservedUsageTime)) {
                            lastObservedUsageTime = lastObs
                        }
                        appSummaries.add(
                            AppUsageSummary(
                                packageName = pkg,
                                totalDurationMs = totalDuration,
                                sessionCount = obsList.size,
                                firstObservedAt = firstObs,
                                lastObservedAt = lastObs,
                                authority = EpistemicAuthority.SYSTEM_RECORDED_OBSERVATION
                            )
                        )
                    }
                    appSummaries.sortWith(
                        compareByDescending<AppUsageSummary> { it.totalDurationMs }
                            .thenBy { it.packageName }
                    )
                } else if (obsResult is Result.Failure) {
                    finalDigitalAvailability = SourceAvailability.UNAVAILABLE_HARDWARE
                }

                if (finalDigitalAvailability != SourceAvailability.UNAVAILABLE_HARDWARE) {
                    val dataAge = checkpoint?.lastCollectedIntervalEndMs?.let { now - it }
                    isDigitalStale = dataAge?.let { it > maxStaleDelayMs } ?: false

                    if (checkpoint != null && isDigitalStale) {
                        finalDigitalAvailability = SourceAvailability.STALE_DATA
                    } else if (appSummaries.isNotEmpty()) {
                        // Persisted observations exist. Verify that collection has reached requested window end.
                        if (checkpoint == null || checkpoint.lastCollectedIntervalEndMs < endTime) {
                            finalDigitalAvailability = SourceAvailability.PARTIAL_HISTORY
                        } else {
                            finalDigitalAvailability = SourceAvailability.AVAILABLE
                        }
                    } else {
                        // 0 observations in requested window.
                        if (checkpoint != null && checkpoint.lastCollectedIntervalEndMs >= endTime) {
                            // Check whether collection history proves coverage at or before startTime
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
            }

            // Focus and Protection Activity
            val sessionsResult = protectionRepository.getProtectionSessionsForUser(userId)
            val rulesResult = protectionRepository.getProtectionRulesForUser(userId)
            val isProtectionDataAvailable = sessionsResult is Result.Success && rulesResult is Result.Success

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
                val sessionEnd = session.endedAt?.coerceAtLeast(session.startedAt) ?: Long.MAX_VALUE
                session.startedAt <= endTime && sessionEnd >= startTime
            }
            val activeSessions = recentSessions.filter { it.status == ProtectionSessionStatus.ACTIVE }
            val rules = if (rulesResult is Result.Success) {
                rulesResult.data.filter { it.userId == userId }
            } else {
                emptyList()
            }

            val totalFocusDuration = recentSessions.sumOf { session ->
                val effectiveStart = maxOf(session.startedAt, startTime)
                val rawEnd = session.endedAt?.coerceAtLeast(session.startedAt)
                    ?: minOf(now, session.scheduledEndAt ?: now)
                val effectiveEnd = minOf(rawEnd, endTime)
                (effectiveEnd - effectiveStart).coerceAtLeast(0L)
            }

            val focusSummary = FocusActivitySummary(
                activeSessions = activeSessions,
                recentSessions = recentSessions,
                totalFocusDurationMs = totalFocusDuration,
                activeRules = rules
            )

            provenanceMap["AppUsage"] = SourceProvenanceAndFreshness(
                sourceName = "Room_Observation",
                availability = finalDigitalAvailability,
                isPermissionGranted = isUsagePermissionGranted,
                lastRecordedAt = lastObservedUsageTime ?: checkpoint?.lastCollectedIntervalEndMs,
                dataAgeMs = checkpoint?.lastCollectedIntervalEndMs?.let { now - it }
                    ?: (lastObservedUsageTime?.let { now - it }),
                isStale = isDigitalStale,
                notes = digitalNotes ?: when (finalDigitalAvailability) {
                    SourceAvailability.PARTIAL_HISTORY -> "Collection history is incomplete for requested window [${startTime}, ${endTime}]"
                    SourceAvailability.STALE_DATA -> "Collection checkpoint is older than freshness threshold (${maxStaleDelayMs / 3600_000}h)"
                    else -> null
                }
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
                notes = digitalNotes
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

            // Heuristic 2: Focus protection active during completed tasks
            val completedTasks = taskSummary.completedTasks
            if (activeSessions.isNotEmpty() && completedTasks.isNotEmpty()) {
                val completedTask = completedTasks.first()
                val session = activeSessions.first()
                correlations.add(
                    CrossDomainCorrelation(
                        id = "corr_focus_complete_${now}_${completedTask.id}",
                        title = "Active focus session during task completion",
                        description = "Task '${completedTask.title}' was completed while a focus protection session was active.",
                        productivityEntityId = completedTask.id,
                        productivityEntityType = "TASK",
                        productivityEntityTitle = completedTask.title,
                        digitalAppPackage = null,
                        digitalUsageDurationMs = (now - session.startedAt).coerceAtLeast(0L),
                        observedWindowStartMs = startTime,
                        observedWindowEndMs = endTime,
                        correlationType = CrossDomainCorrelationType.FOCUS_PROTECTION_DURING_COMPLETED_TASK,
                        confidence = 0.85f,
                        authority = EpistemicAuthority.AI_HYPOTHESIS,
                        disclaimer = "Tentative correlation hypothesis only. Does not imply causation or exclusive attribution."
                    )
                )
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
