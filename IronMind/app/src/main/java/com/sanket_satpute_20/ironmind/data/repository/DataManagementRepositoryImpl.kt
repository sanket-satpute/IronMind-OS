package com.sanket_satpute_20.ironmind.data.repository

import androidx.room.withTransaction
import com.sanket_satpute_20.ironmind.data.local.IronMindDatabase
import com.sanket_satpute_20.ironmind.data.local.dao.*
import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.repository.DataManagementRepository
import com.sanket_satpute_20.ironmind.infrastructure.worker.EventProcessingWorker
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
    private val barrierDao: BarrierDao? = null
) : DataManagementRepository {

    override suspend fun deleteAllUserData(userId: String): Result<Unit, Exception> = withContext(Dispatchers.IO) {
        try {
            val db = database ?: return@withContext Result.Failure(
                IllegalStateException("Database required for transactional user data erasure is unavailable")
            )
            val recDao = interventionRecommendationDao ?: try {
                db.interventionRecommendationDao()
            } catch (e: Exception) {
                null
            } ?: return@withContext Result.Failure(
                IllegalStateException("InterventionRecommendationDao could not be resolved for user data erasure")
            )
            val bDao = barrierDao ?: try {
                db.barrierDao()
            } catch (e: Exception) {
                null
            } ?: return@withContext Result.Failure(
                IllegalStateException("BarrierDao could not be resolved for user data erasure")
            )

            db.withTransaction {
                executeAllDeletions(userId, recDao, bDao)
            }
            try {
                workManager?.cancelUniqueWork(EventProcessingWorker.UNIQUE_WORK_NAME)
                workManager?.cancelUniqueWork(EventProcessingWorker.RECONCILIATION_WORK_NAME)
            } catch (e: Exception) {
                // Supplementary cancellation must not cause erasure failure
            }
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }

    private suspend fun executeAllDeletions(
        userId: String,
        recDao: InterventionRecommendationDao,
        bDao: BarrierDao
    ) {
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

        recDao.deleteRecommendationsForUser(userId)
        bDao.deleteBarriersForUser(userId)

        val sDao = learnerEventStatusDao ?: database?.learnerEventStatusDao()
        sDao?.deleteAllForUser(userId)

        patternDao.deletePatternsForUser(userId)
        observationDao.deleteObservationsForUser(userId)
        interventionDao.deleteInterventionsForUser(userId)
        experimentDao.deleteExperimentsForUser(userId)
        autonomySettingsDao.deleteAutonomySettingsForUser(userId)
        globalAutonomyStateDao.deleteGlobalAutonomyStateForUser(userId)
    }

    override suspend fun cleanupExpiredPatterns(userId: String, thresholdTime: Long): Result<Unit, Exception> = withContext(Dispatchers.IO) {
        try {
            patternDao.deleteExpiredPatternsOlderThan(userId, thresholdTime)
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }

    override suspend fun cleanupForgottenMemories(userId: String, thresholdTime: Long): Result<Unit, Exception> = withContext(Dispatchers.IO) {
        try {
            ironMindDao.deleteForgottenMemoriesOlderThan(userId, thresholdTime)
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }
}
