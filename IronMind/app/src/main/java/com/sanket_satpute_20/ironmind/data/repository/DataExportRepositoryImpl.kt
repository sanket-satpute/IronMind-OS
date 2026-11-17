package com.sanket_satpute_20.ironmind.data.repository

import androidx.room.withTransaction
import com.sanket_satpute_20.ironmind.data.local.IronMindDatabase
import com.sanket_satpute_20.ironmind.data.local.entity.toDomain
import com.sanket_satpute_20.ironmind.data.local.entity.toDomainModel
import com.sanket_satpute_20.ironmind.domain.common.Clock
import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.*
import com.sanket_satpute_20.ironmind.domain.model.barrier.BarrierConfirmationState
import com.sanket_satpute_20.ironmind.domain.model.barrier.BarrierHypothesis
import com.sanket_satpute_20.ironmind.domain.model.barrier.BarrierStatus
import com.sanket_satpute_20.ironmind.domain.model.experiment.ExperimentRecord
import com.sanket_satpute_20.ironmind.domain.model.intervention.InterventionRecommendation
import com.sanket_satpute_20.ironmind.domain.model.intervention.InterventionRecord
import com.sanket_satpute_20.ironmind.domain.model.pattern.Pattern
import com.sanket_satpute_20.ironmind.domain.model.pattern.PatternStatus
import com.sanket_satpute_20.ironmind.domain.model.pattern.PatternType
import com.sanket_satpute_20.ironmind.domain.repository.DataExportRepository
import com.sanket_satpute_20.ironmind.domain.usecase.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Production implementation of [DataExportRepository].
 *
 * Executes an atomic snapshot across all 26 user-owned Room entities within a single
 * Room database transaction on [Dispatchers.IO].
 */
class DataExportRepositoryImpl(
    private val database: IronMindDatabase,
    private val clock: Clock
) : DataExportRepository {

    override suspend fun captureUserSnapshot(userId: String): Result<UserDataExport, Exception> =
        withContext(Dispatchers.IO) {
            try {
                val exportedAt = clock.currentTimeMillis()

                val snapshot = database.withTransaction {
                    val ironMindDao = database.ironMindDao()

                    // 1. UserProfile (1:1 PK)
                    val profileEntity = ironMindDao.getUserProfile(userId)
                    val profile = profileEntity?.let {
                        UserProfile(
                            id = it.id,
                            createdAt = it.createdAt,
                            updatedAt = it.updatedAt,
                            displayName = it.displayName,
                            timezone = it.timezone,
                            createdFrom = it.createdFrom,
                            status = it.status
                        )
                    }

                    // 2. Goals
                    val goals = ironMindDao.getGoalsForUser(userId).map {
                        Goal(
                            id = it.id,
                            userId = it.userId,
                            ambitionId = it.ambitionId,
                            title = it.title,
                            description = it.description,
                            why = it.why,
                            importance = it.importance,
                            status = GoalStatus.valueOf(it.status),
                            targetAt = it.targetAt,
                            startedAt = it.startedAt,
                            completedAt = it.completedAt,
                            createdAt = it.createdAt,
                            updatedAt = it.updatedAt
                        )
                    }

                    // 3. Plans
                    val plans = ironMindDao.getPlansForUser(userId).map {
                        Plan(
                            id = it.id,
                            userId = it.userId,
                            goalId = it.goalId,
                            ambitionId = it.ambitionId,
                            title = it.title,
                            description = it.description,
                            status = PlanStatus.valueOf(it.status),
                            createdAt = it.createdAt,
                            updatedAt = it.updatedAt,
                            startedAt = it.startedAt,
                            completedAt = it.completedAt,
                            source = EntitySource.valueOf(it.source)
                        )
                    }

                    // 4. Tasks
                    val tasks = ironMindDao.getTasksForUser(userId).map {
                        Task(
                            id = it.id,
                            userId = it.userId,
                            goalId = it.goalId,
                            planId = it.planId,
                            parentTaskId = it.parentTaskId,
                            title = it.title,
                            description = it.description,
                            status = TaskStatus.valueOf(it.status),
                            priority = it.priority,
                            estimatedDurationMinutes = it.estimatedDurationMinutes,
                            scheduledAt = it.scheduledAt,
                            dueAt = it.dueAt,
                            createdAt = it.createdAt,
                            updatedAt = it.updatedAt,
                            completedAt = it.completedAt,
                            postponedAt = it.postponedAt,
                            source = EntitySource.valueOf(it.source)
                        )
                    }

                    // 5. Commitments
                    val commitments = ironMindDao.getCommitmentsForUser(userId).map {
                        Commitment(
                            id = it.id,
                            userId = it.userId,
                            goalId = it.goalId,
                            planId = it.planId,
                            taskId = it.taskId,
                            parentCommitmentId = it.parentCommitmentId,
                            title = it.title,
                            description = it.description,
                            committedAt = it.committedAt,
                            scheduledStartAt = it.scheduledStartAt,
                            scheduledEndAt = it.scheduledEndAt,
                            status = CommitmentStatus.valueOf(it.status),
                            priority = it.priority,
                            source = EntitySource.valueOf(it.source),
                            createdAt = it.createdAt,
                            updatedAt = it.updatedAt,
                            startedAt = it.startedAt,
                            completedAt = it.completedAt,
                            postponedAt = it.postponedAt,
                            missedAt = it.missedAt,
                            recoveredAt = it.recoveredAt
                        )
                    }

                    // 6. Outcomes
                    val outcomes = ironMindDao.getOutcomesForUser(userId).map { it.toDomain() }

                    // 7. Reflections
                    val reflections = ironMindDao.getReflectionsForUser(userId).map {
                        Reflection(
                            id = it.id,
                            userId = it.userId,
                            targetEntityId = it.targetEntityId,
                            targetEntityType = it.targetEntityType,
                            content = it.content,
                            sentiment = it.sentiment,
                            createdAt = it.createdAt,
                            schemaVersion = it.schemaVersion
                        )
                    }

                    // 8. Protection Rules
                    val protectionRules = ironMindDao.getProtectionRulesForUser(userId).map { it.toDomain() }

                    // 9. Protection Sessions
                    val protectionSessions = ironMindDao.getProtectionSessionsForUser(userId).map { it.toDomain() }

                    // 10. Events
                    val events = ironMindDao.getEventsForUser(userId).map { it.toDomainModel() }

                    // 11. Memories
                    val memories = ironMindDao.getMemoriesForUser(userId).map { it.toDomain() }

                    // 12. Observations
                    val observations = database.observationDao().getAllObservationsForUser(userId).map { it.toDomain() }

                    // 13. Patterns
                    val patterns = database.patternDao().getPatternsForUser(userId).map {
                        Pattern(
                            id = it.id,
                            userId = it.userId,
                            fingerprint = it.fingerprint,
                            type = PatternType.valueOf(it.type),
                            description = it.description,
                            conditions = it.conditions,
                            predictedBehavior = it.predictedBehavior,
                            confidence = it.confidence,
                            evidenceCount = it.evidenceCount,
                            evidenceReferences = it.evidenceReferences?.split(",")
                                ?.filter { ref -> ref.isNotBlank() }
                                ?.mapNotNull { ref -> com.sanket_satpute_20.ironmind.domain.model.pattern.EvidenceReference.deserialize(ref) },
                            firstObservedAt = it.firstObservedAt,
                            lastObservedAt = it.lastObservedAt,
                            status = PatternStatus.valueOf(it.status),
                            confirmationState = MemoryConfirmationState.valueOf(it.confirmationState),
                            createdAt = it.createdAt,
                            updatedAt = it.updatedAt
                        )
                    }

                    // 14. Autonomy Settings
                    val autonomySettings = database.autonomySettingsDao().getSettingsForUser(userId).map {
                        AutonomySettingRecord(
                            userId = it.userId,
                            capability = it.capability,
                            level = it.level,
                            updatedAt = it.updatedAt
                        )
                    }

                    // 15. Global Autonomy State
                    val globalAutonomyState = database.globalAutonomyStateDao().getStateForUser(userId)?.let {
                        GlobalAutonomyStateRecord(
                            userId = it.userId,
                            isGlobalPauseActive = it.isGlobalPauseActive,
                            updatedAt = it.updatedAt
                        )
                    }

                    // 16. App Usage Observation Settings
                    val appUsageSettings = database.appUsageObservationSettingsDao().getSettingsForUser(userId)?.let {
                        AppUsageObservationSettings(
                            userId = it.userId,
                            isEnabled = it.isEnabled
                        )
                    }

                    // 17. Intervention Records
                    val interventionRecords = database.interventionDao().getInterventionsForUser(userId).map {
                        InterventionRecord(
                            id = it.id,
                            userId = it.userId,
                            type = it.type,
                            state = it.state,
                            title = it.title,
                            description = it.description,
                            channel = it.channel,
                            resolutionReason = it.resolutionReason,
                            contextData = it.contextData,
                            createdAt = it.createdAt,
                            updatedAt = it.updatedAt
                        )
                    }

                    // 18. Notification Observation Settings
                    val notificationSettings = database.notificationObservationSettingsDao().getSettings(userId)?.let {
                        NotificationObservationSettings(
                            userId = it.userId,
                            isEnabled = it.isEnabled
                        )
                    }

                    // 19. Calendar Observation Settings
                    val calendarSettings = database.calendarObservationSettingsDao().getSettings(userId)?.let {
                        CalendarObservationSettings(
                            userId = it.userId,
                            isEnabled = it.isEnabled,
                            updatedAt = it.updatedAt
                        )
                    }

                    // 20. Location Observation Settings
                    val locationSettings = database.locationObservationSettingsDao().getSettings(userId)?.let {
                        LocationObservationSettings(
                            userId = it.userId,
                            isEnabled = it.isEnabled,
                            updatedAt = it.updatedAt
                        )
                    }

                    // 21. Activity Observation Settings
                    val activitySettings = database.activityObservationSettingsDao().getSettings(userId)?.let {
                        ActivityObservationSettings(
                            userId = it.userId,
                            isEnabled = it.isEnabled,
                            lastUpdatedAt = it.lastUpdatedAt
                        )
                    }

                    // 22. Experiment Records
                    val experimentRecords = database.experimentDao().getAllForUser(userId).map {
                        ExperimentRecord(
                            id = it.id,
                            userId = it.userId,
                            hypothesis = it.hypothesis,
                            activeVariation = it.activeVariation,
                            controlVariation = it.controlVariation,
                            targetMetric = it.targetMetric,
                            state = it.state,
                            startedAt = it.startedAt,
                            endedAt = it.endedAt,
                            outcomeSummary = it.outcomeSummary
                        )
                    }

                    // 23. Barrier Hypotheses
                    val barrierHypotheses = database.barrierDao().getAllBarriersForUser(userId).map {
                        BarrierHypothesis(
                            id = it.id,
                            userId = it.userId,
                            category = com.sanket_satpute_20.ironmind.domain.ai.BarrierCategory.valueOf(it.category),
                            description = it.description,
                            confirmationState = BarrierConfirmationState.valueOf(it.confirmationState),
                            status = BarrierStatus.valueOf(it.status),
                            sourceReflectionId = it.sourceReflectionId,
                            firstObservedAt = it.firstObservedAt,
                            lastObservedAt = it.lastObservedAt
                        )
                    }

                    // 24. Intervention Recommendations
                    // Historical Data Fidelity (Phase 8.3.2 Stage 3A):
                    // Maps directly to InterventionRecommendationExportRecord to preserve original
                    // database fields faithfully. Pre-migration-21 records with objective == null
                    // remain null and are NOT artificially forced to INITIATE_ACTION.
                    val interventionRecommendations = database.interventionRecommendationDao()
                        .getAllRecommendationsForUser(userId)
                        .map { entity ->
                            InterventionRecommendationExportRecord(
                                id = entity.id,
                                userId = entity.userId,
                                interventionType = entity.interventionType,
                                objective = entity.objective,
                                targetEntityId = entity.targetEntityId,
                                targetEntityType = entity.targetEntityType,
                                rationale = entity.rationale,
                                suggestedAction = entity.suggestedAction,
                                status = entity.status,
                                createdAt = entity.createdAt,
                                expiresAt = entity.expiresAt,
                                causalHypothesisId = entity.causalHypothesisId,
                                causalHypothesisType = entity.causalHypothesisType
                            )
                        }

                    // 25. Learner Event Statuses
                    val learnerEventStatuses = database.learnerEventStatusDao().getAllForUser(userId).map {
                        LearnerEventStatusRecord(
                            eventId = it.eventId,
                            userId = it.userId,
                            status = it.status,
                            claimCount = it.claimCount,
                            failureCount = it.failureCount,
                            maxAttempts = it.maxAttempts,
                            leaseToken = it.leaseToken,
                            leaseExpiresAt = it.leaseExpiresAt,
                            lastAttemptAt = it.lastAttemptAt,
                            nextAttemptAt = it.nextAttemptAt,
                            errorMessage = it.errorMessage,
                            hadMalformedMetadata = it.hadMalformedMetadata,
                            createdAt = it.createdAt,
                            updatedAt = it.updatedAt
                        )
                    }

                    // 26. Correction Reviews
                    val correctionReviews = database.correctionReviewDao().getAllReviewsForUser(userId).map {
                        it.toDomainModel()
                    }

                    UserDataExport(
                        exportVersion = UserDataExport.CURRENT_EXPORT_VERSION,
                        exportedAt = exportedAt,
                        userId = userId,
                        profile = profile,
                        goals = goals,
                        plans = plans,
                        tasks = tasks,
                        commitments = commitments,
                        outcomes = outcomes,
                        reflections = reflections,
                        protectionRules = protectionRules,
                        protectionSessions = protectionSessions,
                        events = events,
                        memories = memories,
                        observations = observations,
                        patterns = patterns,
                        autonomySettings = autonomySettings,
                        globalAutonomyState = globalAutonomyState,
                        appUsageObservationSettings = appUsageSettings,
                        interventionRecords = interventionRecords,
                        notificationObservationSettings = notificationSettings,
                        calendarObservationSettings = calendarSettings,
                        locationObservationSettings = locationSettings,
                        activityObservationSettings = activitySettings,
                        experimentRecords = experimentRecords,
                        barrierHypotheses = barrierHypotheses,
                        interventionRecommendations = interventionRecommendations,
                        learnerEventStatuses = learnerEventStatuses,
                        correctionReviews = correctionReviews
                    )
                }

                Result.Success(snapshot)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.Failure(e)
            }
        }
}
