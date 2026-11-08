package com.sanket_satpute_20.ironmind.data.repository

import androidx.room.withTransaction
import com.sanket_satpute_20.ironmind.data.local.IronMindDatabase
import com.sanket_satpute_20.ironmind.data.local.dao.AppUsageObservationCheckpointDao
import com.sanket_satpute_20.ironmind.data.local.dao.ObservationDao
import com.sanket_satpute_20.ironmind.data.local.entity.toDomain
import com.sanket_satpute_20.ironmind.data.local.entity.toEntity
import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.observation.Observation
import com.sanket_satpute_20.ironmind.domain.repository.AppUsageCheckpoint
import com.sanket_satpute_20.ironmind.domain.repository.AppUsageCheckpointStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Room-backed implementation of [AppUsageCheckpointStore].
 *
 * Guarantees transactional consistency by persisting observation records and cursor progression
 * atomically inside [IronMindDatabase.withTransaction].
 */
class RoomAppUsageCheckpointStore(
    private val database: IronMindDatabase,
    private val observationDao: ObservationDao,
    private val checkpointDao: AppUsageObservationCheckpointDao
) : AppUsageCheckpointStore {

    override suspend fun getCheckpoint(userId: String): Result<AppUsageCheckpoint?, Exception> = withContext(Dispatchers.IO) {
        try {
            val entity = checkpointDao.getCheckpointAsync(userId)
            Result.Success(entity?.toDomain())
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            println("IronMindLifecycle [AppUsageCheckpoint] [READ_FAILED] userId=$userId error=${e.message}")
            Result.Failure(e)
        }
    }

    override suspend fun saveCheckpoint(checkpoint: AppUsageCheckpoint): Result<Unit, Exception> = withContext(Dispatchers.IO) {
        try {
            checkpointDao.upsertCheckpointAsync(checkpoint.toEntity())
            Result.Success(Unit)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            println("IronMindLifecycle [AppUsageCheckpoint] [SAVE_FAILED] userId=${checkpoint.userId} error=${e.message}")
            Result.Failure(e)
        }
    }

    override suspend fun saveObservationsWithCheckpoint(
        observations: List<Observation>,
        checkpoint: AppUsageCheckpoint
    ): Result<Unit, Exception> = withContext(Dispatchers.IO) {
        try {
            database.withTransaction {
                for (obs in observations) {
                    observationDao.insertObservation(obs.toEntity())
                }
                checkpointDao.upsertCheckpoint(checkpoint.toEntity())
            }
            println("IronMindLifecycle [AppUsageCheckpoint] [ATOMIC_COMMIT_SUCCESS] userId=${checkpoint.userId} obsCount=${observations.size} intervalEnd=${checkpoint.lastCollectedIntervalEndMs}")
            Result.Success(Unit)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            println("IronMindLifecycle [AppUsageCheckpoint] [ATOMIC_COMMIT_FAILED] userId=${checkpoint.userId} error=${e.message}")
            Result.Failure(e)
        }
    }

    override suspend fun clearCheckpointForUser(userId: String): Result<Unit, Exception> = withContext(Dispatchers.IO) {
        try {
            checkpointDao.deleteCheckpointForUserAsync(userId)
            Result.Success(Unit)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            println("IronMindLifecycle [AppUsageCheckpoint] [CLEAR_FAILED] userId=$userId error=${e.message}")
            Result.Failure(e)
        }
    }
}

