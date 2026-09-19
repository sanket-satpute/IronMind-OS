package com.sanket_satpute_20.ironmind.data.repository

import com.sanket_satpute_20.ironmind.data.local.dao.PatternDao
import com.sanket_satpute_20.ironmind.data.local.entity.PatternEntity
import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.MemoryConfirmationState
import com.sanket_satpute_20.ironmind.domain.model.pattern.Pattern
import com.sanket_satpute_20.ironmind.domain.model.pattern.PatternStatus
import com.sanket_satpute_20.ironmind.domain.model.pattern.PatternType
import com.sanket_satpute_20.ironmind.domain.repository.PatternRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class PatternRepositoryImpl(
    private val dao: PatternDao
) : PatternRepository {

    override suspend fun getPattern(id: String): Result<Pattern?, Exception> = withContext(Dispatchers.IO) {
        try {
            val entity = dao.getPatternById(id)
            Result.Success(entity?.toDomain())
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }

    override suspend fun getPatternsForUser(userId: String): Result<List<Pattern>, Exception> = withContext(Dispatchers.IO) {
        try {
            Result.Success(dao.getPatternsForUser(userId).map { it.toDomain() })
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }

    override suspend fun getPatternsByType(userId: String, type: PatternType): Result<List<Pattern>, Exception> = withContext(Dispatchers.IO) {
        try {
            Result.Success(dao.getPatternsByType(userId, type.name).map { it.toDomain() })
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }

    override suspend fun getPatternsByStatus(userId: String, status: PatternStatus): Result<List<Pattern>, Exception> = withContext(Dispatchers.IO) {
        try {
            Result.Success(dao.getPatternsByStatus(userId, status.name).map { it.toDomain() })
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }

    override suspend fun savePattern(pattern: Pattern): Result<Unit, Exception> = withContext(Dispatchers.IO) {
        try {
            dao.insertPattern(pattern.toEntity())
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }

    override suspend fun updatePatternConfidence(id: String, confidence: Float, lastObservedAt: Long): Result<Unit, Exception> = withContext(Dispatchers.IO) {
        try {
            dao.updatePatternConfidence(id, confidence, lastObservedAt, System.currentTimeMillis())
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }

    override suspend fun deletePattern(id: String): Result<Unit, Exception> = withContext(Dispatchers.IO) {
        try {
            dao.deletePattern(id)
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }

    private fun PatternEntity.toDomain() = Pattern(
        id = id,
        userId = userId,
        type = PatternType.valueOf(type),
        description = description,
        conditions = conditions,
        predictedBehavior = predictedBehavior,
        confidence = confidence,
        evidenceCount = evidenceCount,
        evidenceReferences = evidenceReferences?.split(",")?.filter { it.isNotBlank() },
        firstObservedAt = firstObservedAt,
        lastObservedAt = lastObservedAt,
        status = PatternStatus.valueOf(status),
        confirmationState = MemoryConfirmationState.valueOf(confirmationState),
        createdAt = createdAt,
        updatedAt = updatedAt
    )

    private fun Pattern.toEntity() = PatternEntity(
        id = id,
        userId = userId,
        type = type.name,
        description = description,
        conditions = conditions,
        predictedBehavior = predictedBehavior,
        confidence = confidence,
        evidenceCount = evidenceCount,
        evidenceReferences = evidenceReferences?.joinToString(","),
        firstObservedAt = firstObservedAt,
        lastObservedAt = lastObservedAt,
        status = status.name,
        confirmationState = confirmationState.name,
        createdAt = createdAt,
        updatedAt = updatedAt
    )
}
