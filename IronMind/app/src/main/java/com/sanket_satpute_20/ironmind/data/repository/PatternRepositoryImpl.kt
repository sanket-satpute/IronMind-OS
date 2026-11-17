package com.sanket_satpute_20.ironmind.data.repository

import androidx.room.withTransaction
import com.sanket_satpute_20.ironmind.data.local.IronMindDatabase
import com.sanket_satpute_20.ironmind.data.local.dao.IronMindDao
import com.sanket_satpute_20.ironmind.data.local.dao.PatternDao
import com.sanket_satpute_20.ironmind.data.local.entity.PatternEntity
import com.sanket_satpute_20.ironmind.data.local.entity.toEntity
import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.Event
import com.sanket_satpute_20.ironmind.domain.model.MemoryConfirmationState
import com.sanket_satpute_20.ironmind.domain.model.pattern.Pattern
import com.sanket_satpute_20.ironmind.domain.model.pattern.PatternStatus
import com.sanket_satpute_20.ironmind.domain.model.pattern.PatternType
import com.sanket_satpute_20.ironmind.domain.repository.PatternRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class PatternRepositoryImpl(
    private val dao: PatternDao,
    private val database: IronMindDatabase? = null,
    private val eventDao: IronMindDao? = null
) : PatternRepository {

    override suspend fun getPattern(id: String): Result<Pattern?, Exception> = withContext(Dispatchers.IO) {
        try {
            val entity = dao.getPatternById(id)
            Result.Success(entity?.toDomain())
        } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) {
            Result.Failure(e)
        }
    }

    override suspend fun getPatternByFingerprint(userId: String, fingerprint: String): Result<Pattern?, Exception> = withContext(Dispatchers.IO) {
        try {
            val entity = dao.getPatternByFingerprint(userId, fingerprint)
            Result.Success(entity?.toDomain())
        } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) {
            Result.Failure(e)
        }
    }

    override suspend fun getPatternsForUser(userId: String): Result<List<Pattern>, Exception> = withContext(Dispatchers.IO) {
        try {
            Result.Success(dao.getPatternsForUser(userId).map { it.toDomain() })
        } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) {
            Result.Failure(e)
        }
    }

    override fun observePatternsForUser(userId: String): Flow<List<Pattern>> {
        return dao.observePatternsForUser(userId).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun getPatternsByType(userId: String, type: PatternType): Result<List<Pattern>, Exception> = withContext(Dispatchers.IO) {
        try {
            Result.Success(dao.getPatternsByType(userId, type.name).map { it.toDomain() })
        } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) {
            Result.Failure(e)
        }
    }

    override suspend fun getPatternsByStatus(userId: String, status: PatternStatus): Result<List<Pattern>, Exception> = withContext(Dispatchers.IO) {
        try {
            Result.Success(dao.getPatternsByStatus(userId, status.name).map { it.toDomain() })
        } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) {
            Result.Failure(e)
        }
    }

    override suspend fun getPatternsByStatus(userId: String, status: PatternStatus, limit: Int): Result<List<Pattern>, Exception> = withContext(Dispatchers.IO) {
        try {
            Result.Success(dao.getPatternsByStatus(userId, status.name, limit).map { it.toDomain() })
        } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) {
            Result.Failure(e)
        }
    }

    override suspend fun savePattern(pattern: Pattern): Result<Unit, Exception> = withContext(Dispatchers.IO) {
        try {
            dao.insertPattern(pattern.toEntity())
            Result.Success(Unit)
        } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) {
            Result.Failure(e)
        }
    }

    override suspend fun updatePattern(pattern: Pattern): Result<Unit, Exception> = withContext(Dispatchers.IO) {
        try {
            dao.updatePattern(pattern.toEntity())
            Result.Success(Unit)
        } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) {
            Result.Failure(e)
        }
    }

    override suspend fun updatePatternConfidence(id: String, confidence: Float, lastObservedAt: Long): Result<Unit, Exception> = withContext(Dispatchers.IO) {
        try {
            dao.updatePatternConfidence(id, confidence, lastObservedAt, System.currentTimeMillis())
            Result.Success(Unit)
        } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) {
            Result.Failure(e)
        }
    }

    override suspend fun updatePatternState(
        id: String,
        userId: String,
        confirmationState: MemoryConfirmationState,
        status: PatternStatus,
        updatedAt: Long
    ): Result<Unit, Exception> = updatePatternStateWithEvent(id, userId, confirmationState, status, updatedAt, null)

    override suspend fun updatePatternStateWithEvent(
        id: String,
        userId: String,
        confirmationState: MemoryConfirmationState,
        status: PatternStatus,
        updatedAt: Long,
        event: Event?
    ): Result<Unit, Exception> = withContext(Dispatchers.IO) {
        try {
            if (database != null) {
                database.withTransaction {
                    val existing = dao.getPatternById(id)
                        ?: throw IllegalArgumentException("Pattern not found: $id")
                    if (existing.userId != userId) {
                        throw IllegalStateException("Pattern $id does not belong to user $userId")
                    }
                    if (existing.confirmationState == confirmationState.name && existing.status == status.name) {
                        return@withTransaction
                    }
                    dao.updatePatternState(id, userId, confirmationState.name, status.name, updatedAt)
                    if (event != null) {
                        val targetEventDao = eventDao ?: database.ironMindDao()
                        targetEventDao.insertEvent(event.toEntity())
                    }
                }
            } else {
                val existing = dao.getPatternById(id)
                    ?: return@withContext Result.Failure(IllegalArgumentException("Pattern not found: $id"))
                if (existing.userId != userId) {
                    return@withContext Result.Failure(IllegalStateException("Pattern $id does not belong to user $userId"))
                }
                if (existing.confirmationState == confirmationState.name && existing.status == status.name) {
                    return@withContext Result.Success(Unit)
                }
                dao.updatePatternState(id, userId, confirmationState.name, status.name, updatedAt)
                if (event != null && eventDao != null) {
                    eventDao.insertEvent(event.toEntity())
                }
            }
            Result.Success(Unit)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }

    override suspend fun deletePattern(id: String): Result<Unit, Exception> = withContext(Dispatchers.IO) {
        try {
            dao.deletePattern(id)
            Result.Success(Unit)
        } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) {
            Result.Failure(e)
        }
    }

    private fun PatternEntity.toDomain() = Pattern(
        id = id,
        userId = userId,
        fingerprint = fingerprint,
        type = PatternType.valueOf(type),
        description = description,
        conditions = conditions,
        predictedBehavior = predictedBehavior,
        confidence = confidence,
        evidenceCount = evidenceCount,
        evidenceReferences = evidenceReferences?.split(",")?.filter { it.isNotBlank() }?.mapNotNull { com.sanket_satpute_20.ironmind.domain.model.pattern.EvidenceReference.deserialize(it) },
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
        fingerprint = fingerprint,
        type = type.name,
        description = description,
        conditions = conditions,
        predictedBehavior = predictedBehavior,
        confidence = confidence,
        evidenceCount = evidenceCount,
        evidenceReferences = evidenceReferences?.joinToString(",") { it.serialize() },
        firstObservedAt = firstObservedAt,
        lastObservedAt = lastObservedAt,
        status = status.name,
        confirmationState = confirmationState.name,
        createdAt = createdAt,
        updatedAt = updatedAt
    )
}
