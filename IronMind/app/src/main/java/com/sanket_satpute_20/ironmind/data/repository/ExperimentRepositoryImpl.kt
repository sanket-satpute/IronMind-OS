package com.sanket_satpute_20.ironmind.data.repository

import com.sanket_satpute_20.ironmind.data.local.dao.ExperimentDao
import com.sanket_satpute_20.ironmind.data.local.entity.ExperimentRecordEntity
import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.experiment.ExperimentRecord
import com.sanket_satpute_20.ironmind.domain.repository.ExperimentRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ExperimentRepositoryImpl(
    private val dao: ExperimentDao
) : ExperimentRepository {

    override suspend fun save(record: ExperimentRecord): Result<ExperimentRecord, Exception> = withContext(Dispatchers.IO) {
        try {
            val entity = ExperimentRecordEntity(
                id = record.id,
                userId = record.userId,
                hypothesis = record.hypothesis,
                activeVariation = record.activeVariation,
                controlVariation = record.controlVariation,
                targetMetric = record.targetMetric,
                state = record.state,
                startedAt = record.startedAt,
                endedAt = record.endedAt,
                outcomeSummary = record.outcomeSummary
            )
            val existing = dao.getById(record.id)
            if (existing == null) {
                dao.insert(entity)
            } else {
                dao.update(entity)
            }
            Result.Success(record)
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }

    override suspend fun getById(id: String): Result<ExperimentRecord?, Exception> = withContext(Dispatchers.IO) {
        try {
            val entity = dao.getById(id)
            Result.Success(entity?.toDomain())
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }

    override suspend fun getActiveExperiments(userId: String): Result<List<ExperimentRecord>, Exception> = withContext(Dispatchers.IO) {
        try {
            val entities = dao.getActiveExperiments(userId)
            Result.Success(entities.map { it.toDomain() })
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }

    override suspend fun getAllForUser(userId: String): Result<List<ExperimentRecord>, Exception> = withContext(Dispatchers.IO) {
        try {
            val entities = dao.getAllForUser(userId)
            Result.Success(entities.map { it.toDomain() })
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }

    private fun ExperimentRecordEntity.toDomain(): ExperimentRecord {
        return ExperimentRecord(
            id = id,
            userId = userId,
            hypothesis = hypothesis,
            activeVariation = activeVariation,
            controlVariation = controlVariation,
            targetMetric = targetMetric,
            state = state,
            startedAt = startedAt,
            endedAt = endedAt,
            outcomeSummary = outcomeSummary
        )
    }
}
