package com.sanket_satpute_20.ironmind.data.repository

import com.sanket_satpute_20.ironmind.data.local.dao.ObservationDao
import com.sanket_satpute_20.ironmind.data.local.entity.toDomain
import com.sanket_satpute_20.ironmind.data.local.entity.toEntity
import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.observation.Observation
import com.sanket_satpute_20.ironmind.domain.model.observation.ObservationType
import com.sanket_satpute_20.ironmind.domain.repository.ObservationRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ObservationRepositoryImpl(
    private val observationDao: ObservationDao
) : ObservationRepository {

    override suspend fun insertObservation(observation: Observation): Result<Unit, Exception> = withContext(Dispatchers.IO) {
        try {
            observationDao.insertObservation(observation.toEntity())
            println("IronMindLifecycle [Observation] [RECORDED] id=${observation.id} type=${observation.type} source=${observation.source}")
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }

    override suspend fun getObservations(userId: String, limit: Int, offset: Int): Result<List<Observation>, Exception> = withContext(Dispatchers.IO) {
        try {
            val observations = observationDao.getObservations(userId, limit, offset).map { it.toDomain() }
            Result.Success(observations)
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }

    override suspend fun getObservationsByType(
        userId: String,
        type: ObservationType,
        limit: Int,
        offset: Int
    ): Result<List<Observation>, Exception> = withContext(Dispatchers.IO) {
        try {
            val observations = observationDao.getObservationsByType(userId, type.name, limit, offset).map { it.toDomain() }
            Result.Success(observations)
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }

    override suspend fun getObservationById(id: String): Result<Observation, Exception> = withContext(Dispatchers.IO) {
        try {
            val entity = observationDao.getObservationById(id)
            if (entity != null) {
                Result.Success(entity.toDomain())
            } else {
                Result.Failure(Exception("Observation not found for id: $id"))
            }
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }

    override suspend fun deleteObservation(id: String): Result<Unit, Exception> = withContext(Dispatchers.IO) {
        try {
            observationDao.deleteObservation(id)
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }
}
