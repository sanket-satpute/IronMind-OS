package com.sanket_satpute_20.ironmind.data.repository

import com.sanket_satpute_20.ironmind.data.local.dao.*
import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.repository.DataManagementRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class DataManagementRepositoryImpl(
    private val ironMindDao: IronMindDao,
    private val patternDao: PatternDao,
    private val observationDao: ObservationDao,
    private val interventionDao: InterventionDao,
    private val experimentDao: ExperimentDao,
    private val autonomySettingsDao: AutonomySettingsDao,
    private val globalAutonomyStateDao: GlobalAutonomyStateDao
) : DataManagementRepository {

    override suspend fun deleteAllUserData(userId: String): Result<Unit, Exception> = withContext(Dispatchers.IO) {
        try {
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
            
            patternDao.deletePatternsForUser(userId)
            observationDao.deleteObservationsForUser(userId)
            interventionDao.deleteInterventionsForUser(userId)
            experimentDao.deleteExperimentsForUser(userId)
            autonomySettingsDao.deleteAutonomySettingsForUser(userId)
            globalAutonomyStateDao.deleteGlobalAutonomyStateForUser(userId)
            
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Failure(e)
        }
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
