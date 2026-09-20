package com.sanket_satpute_20.ironmind.data.repository

import com.sanket_satpute_20.ironmind.data.local.dao.InterventionDao
import com.sanket_satpute_20.ironmind.data.local.entity.InterventionRecordEntity
import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.intervention.InterventionRecord
import com.sanket_satpute_20.ironmind.domain.repository.InterventionRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class InterventionRepositoryImpl(
    private val interventionDao: InterventionDao
) : InterventionRepository {

    override suspend fun save(record: InterventionRecord): Result<InterventionRecord, Exception> = withContext(Dispatchers.IO) {
        try {
            val entity = InterventionRecordEntity(
                id = record.id,
                userId = record.userId,
                type = record.type,
                state = record.state,
                title = record.title,
                description = record.description,
                resolutionReason = record.resolutionReason,
                contextData = record.contextData,
                createdAt = record.createdAt,
                updatedAt = record.updatedAt
            )
            
            val existing = interventionDao.getById(record.id)
            if (existing == null) {
                interventionDao.insert(entity)
            } else {
                interventionDao.update(entity)
            }
            Result.Success(record)
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }

    override suspend fun getById(id: String): Result<InterventionRecord?, Exception> = withContext(Dispatchers.IO) {
        try {
            val entity = interventionDao.getById(id)
            if (entity != null) {
                val record = InterventionRecord(
                    id = entity.id,
                    userId = entity.userId,
                    type = entity.type,
                    state = entity.state,
                    title = entity.title,
                    description = entity.description,
                    resolutionReason = entity.resolutionReason,
                    contextData = entity.contextData,
                    createdAt = entity.createdAt,
                    updatedAt = entity.updatedAt
                )
                Result.Success(record)
            } else {
                Result.Success(null)
            }
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }

    override suspend fun getRecentInterventions(
        userId: String,
        since: Long
    ): Result<List<InterventionRecord>, Exception> = withContext(Dispatchers.IO) {
        try {
            val entities = interventionDao.getRecentInterventions(userId, since)
            val records = entities.map { mapToDomain(it) }
            Result.Success(records)
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }

    override suspend fun getActiveInterventions(userId: String): Result<List<InterventionRecord>, Exception> = withContext(Dispatchers.IO) {
        try {
            val entities = interventionDao.getActiveInterventions(userId)
            val records = entities.map { mapToDomain(it) }
            Result.Success(records)
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }

    private fun mapToDomain(entity: InterventionRecordEntity): InterventionRecord {
        return InterventionRecord(
            id = entity.id,
            userId = entity.userId,
            type = entity.type,
            state = entity.state,
            title = entity.title,
            description = entity.description,
            resolutionReason = entity.resolutionReason,
            contextData = entity.contextData,
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt
        )
    }
}
