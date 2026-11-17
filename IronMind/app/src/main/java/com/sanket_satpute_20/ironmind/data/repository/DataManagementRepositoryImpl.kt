package com.sanket_satpute_20.ironmind.data.repository

import androidx.room.withTransaction
import com.sanket_satpute_20.ironmind.data.local.IronMindDatabase
import com.sanket_satpute_20.ironmind.data.local.dao.*
import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.repository.DataManagementRepository
import com.sanket_satpute_20.ironmind.infrastructure.worker.EventProcessingWorker
import com.sanket_satpute_20.ironmind.infrastructure.worker.InterventionEvaluationWorker
import com.sanket_satpute_20.ironmind.infrastructure.worker.ObservationScheduler
import com.sanket_satpute_20.ironmind.infrastructure.worker.ObservationWorker
import com.sanket_satpute_20.ironmind.infrastructure.worker.PatternUpdateWorker
import com.sanket_satpute_20.ironmind.infrastructure.worker.ReflectionProcessingWorker
import com.sanket_satpute_20.ironmind.infrastructure.worker.SyncWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class DataManagementRepositoryImpl(
    private val ironMindDao: IronMindDao,
    private val patternDao: PatternDao,
    private val observationDao: ObservationDao,
    private val interventionDao: InterventionDao,
    private val experimentDao: ExperimentDao,
    private val autonomySettingsDao: AutonomySettingsDao,
    private val globalAutonomyStateDao: GlobalAutonomyStateDao,
    private val learnerEventStatusDao: LearnerEventStatusDao? = null,
    private val database: IronMindDatabase? = null,
    private val workManager: androidx.work.WorkManager? = null,
    private val interventionRecommendationDao: InterventionRecommendationDao? = null,
    private val barrierDao: BarrierDao? = null,
    private val outboxDao: OutboxDao? = null,
    private val appUsageObservationSettingsDao: AppUsageObservationSettingsDao? = null,
    private val notificationObservationSettingsDao: NotificationObservationSettingsDao? = null,
    private val calendarObservationSettingsDao: CalendarObservationSettingsDao? = null,
    private val locationObservationSettingsDao: LocationObservationSettingsDao? = null,
    private val activityObservationSettingsDao: ActivityObservationSettingsDao? = null,
    private val correctionReviewDao: CorrectionReviewDao? = null,
    private val syncConflictDao: SyncConflictDao? = null
) : DataManagementRepository {

    override suspend fun deleteAllUserData(userId: String): Result<Unit, Exception> = withContext(Dispatchers.IO) {
        try {
            val db = database ?: return@withContext Result.Failure(
                IllegalStateException("Database required for transactional user data erasure is unavailable")
            )
            val recDao = interventionRecommendationDao ?: try {
                db.interventionRecommendationDao()
            } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) {
                null
            } ?: return@withContext Result.Failure(
                IllegalStateException("InterventionRecommendationDao could not be resolved for user data erasure")
            )
            val bDao = barrierDao ?: try {
                db.barrierDao()
            } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) {
                null
            } ?: return@withContext Result.Failure(
                IllegalStateException("BarrierDao could not be resolved for user data erasure")
            )

            val oDao = outboxDao ?: try { db.outboxDao() } catch (_: Exception) { null }
            val appUsageDao = appUsageObservationSettingsDao ?: try { db.appUsageObservationSettingsDao() } catch (_: Exception) { null }
            val notifDao = notificationObservationSettingsDao ?: try { db.notificationObservationSettingsDao() } catch (_: Exception) { null }
            val calDao = calendarObservationSettingsDao ?: try { db.calendarObservationSettingsDao() } catch (_: Exception) { null }
            val locDao = locationObservationSettingsDao ?: try { db.locationObservationSettingsDao() } catch (_: Exception) { null }
            val actDao = activityObservationSettingsDao ?: try { db.activityObservationSettingsDao() } catch (_: Exception) { null }
            val crDao = correctionReviewDao ?: try { db.correctionReviewDao() } catch (_: Exception) { null }
            val scDao = syncConflictDao ?: try { db.syncConflictDao() } catch (_: Exception) { null }

            db.withTransaction {
                executeAllDeletions(
                    userId = userId,
                    recDao = recDao,
                    bDao = bDao,
                    oDao = oDao,
                    appUsageDao = appUsageDao,
                    notifDao = notifDao,
                    calDao = calDao,
                    locDao = locDao,
                    actDao = actDao,
                    crDao = crDao,
                    scDao = scDao
                )
            }

            cancelAllUserWorkers()

            Result.Success(Unit)
        } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) {
            Result.Failure(e)
        }
    }

    override fun cancelAllUserWorkers() {
        val wm = workManager ?: return
        try {
            // Cancel by verified unique names
            wm.cancelUniqueWork("SyncWorker")
            wm.cancelUniqueWork("PatternUpdateWorker")
            wm.cancelUniqueWork(EventProcessingWorker.UNIQUE_WORK_NAME)
            wm.cancelUniqueWork(EventProcessingWorker.RECONCILIATION_WORK_NAME)
            wm.cancelUniqueWork(ObservationScheduler.UNIQUE_WORK_NAME)
            wm.cancelUniqueWork(InterventionEvaluationWorker.UNIQUE_WORK_NAME)
            wm.cancelUniqueWork(InterventionEvaluationWorker.UNIQUE_WORK_NAME_SCHEDULED)
            wm.cancelUniqueWork(InterventionEvaluationWorker.UNIQUE_WORK_NAME_PERIODIC)
            wm.cancelUniqueWork(InterventionEvaluationWorker.UNIQUE_WORK_NAME_EVENT)

            // Cancel by explicit stable tags (matches enqueue sites)
            wm.cancelAllWorkByTag(ReflectionProcessingWorker.TAG)
            wm.cancelAllWorkByTag(SyncWorker.TAG)
            wm.cancelAllWorkByTag(PatternUpdateWorker.TAG)
            wm.cancelAllWorkByTag(ObservationWorker.TAG)
            wm.cancelAllWorkByTag(EventProcessingWorker.UNIQUE_WORK_NAME)
            wm.cancelAllWorkByTag(EventProcessingWorker.RECONCILIATION_WORK_NAME)
            wm.cancelAllWorkByTag(ObservationScheduler.UNIQUE_WORK_NAME)
            wm.cancelAllWorkByTag(InterventionEvaluationWorker.TAG)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            println("IronMindLifecycle [DataManagement] [WORKER_CANCELLATION_FAILED] error=${e.message}")
        }
    }

    private suspend fun executeAllDeletions(
        userId: String,
        recDao: InterventionRecommendationDao,
        bDao: BarrierDao,
        oDao: OutboxDao?,
        appUsageDao: AppUsageObservationSettingsDao?,
        notifDao: NotificationObservationSettingsDao?,
        calDao: CalendarObservationSettingsDao?,
        locDao: LocationObservationSettingsDao?,
        actDao: ActivityObservationSettingsDao?,
        crDao: CorrectionReviewDao? = null,
        scDao: SyncConflictDao? = null
    ) {
        // Core user entities
        ironMindDao.deleteUserProfile(userId)
        ironMindDao.deleteGoalsForUser(userId)
        ironMindDao.deletePlansForUser(userId)
        ironMindDao.deleteTasksForUser(userId)
        ironMindDao.deleteCommitmentsForUser(userId)
        ironMindDao.deleteReflectionsForUser(userId)
        ironMindDao.deleteProtectionRulesForUser(userId)
        ironMindDao.deleteProtectionSessionsForUser(userId)
        ironMindDao.deleteEventsForUser(userId)
        ironMindDao.deleteMemoriesForUser(userId)
        ironMindDao.deleteOutcomesForUser(userId)

        // Intervention & barrier models
        recDao.deleteRecommendationsForUser(userId)
        bDao.deleteBarriersForUser(userId)
        crDao?.deleteAllForUser(userId)

        val sDao = learnerEventStatusDao ?: database?.learnerEventStatusDao()
        sDao?.deleteAllForUser(userId)

        patternDao.deletePatternsForUser(userId)
        observationDao.deleteObservationsForUser(userId)
        interventionDao.deleteInterventionsForUser(userId)
        experimentDao.deleteExperimentsForUser(userId)
        autonomySettingsDao.deleteAutonomySettingsForUser(userId)
        globalAutonomyStateDao.deleteGlobalAutonomyStateForUser(userId)

        // Observation Settings entities
        appUsageDao?.deleteSettingsForUser(userId)
        notifDao?.deleteSettingsForUser(userId)
        calDao?.deleteSettingsForUser(userId)
        locDao?.deleteSettingsForUser(userId)
        actDao?.deleteSettingsForUser(userId)

        // Outbox: strictly delete verified target-user entries across ALL statuses; preserve foreign and ambiguous entries
        if (oDao != null) {
            oDao.deleteEntriesForUser(userId)
            val allEntries = oDao.getAllEntries()
            for (entry in allEntries) {
                if (entry.userId == null) {
                    val payloadUserId = try {
                        val json = org.json.JSONObject(entry.payload)
                        if (json.has("userId")) json.optString("userId").takeIf { it.isNotBlank() } else null
                    } catch (_: Exception) {
                        null
                    }
                    if (payloadUserId == userId) {
                        oDao.deleteEntry(entry.operationId)
                    }
                }
            }
        }

        // Sync conflicts: strictly delete verified target-user conflict records; preserve foreign records
        scDao?.deleteConflictsForUser(userId)
    }

    override suspend fun cleanupExpiredPatterns(userId: String, thresholdTime: Long): Result<Unit, Exception> = withContext(Dispatchers.IO) {
        try {
            patternDao.deleteExpiredPatternsOlderThan(userId, thresholdTime)
            Result.Success(Unit)
        } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) {
            Result.Failure(e)
        }
    }

    override suspend fun cleanupForgottenMemories(userId: String, thresholdTime: Long): Result<Unit, Exception> = withContext(Dispatchers.IO) {
        try {
            ironMindDao.deleteForgottenMemoriesOlderThan(userId, thresholdTime)
            Result.Success(Unit)
        } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) {
            Result.Failure(e)
        }
    }
}
