package com.sanket_satpute_20.ironmind.data.repository

import com.sanket_satpute_20.ironmind.data.local.dao.IronMindDao
import com.sanket_satpute_20.ironmind.data.local.entity.ReflectionEntity
import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.Reflection
import com.sanket_satpute_20.ironmind.domain.repository.ReflectionRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ReflectionRepositoryImpl(
    private val dao: IronMindDao
) : ReflectionRepository {

    override suspend fun saveReflection(reflection: Reflection): Result<Unit, Exception> = withContext(Dispatchers.IO) {
        try {
            val entity = ReflectionEntity(
                id = reflection.id,
                userId = reflection.userId,
                targetEntityId = reflection.targetEntityId,
                targetEntityType = reflection.targetEntityType,
                content = reflection.content,
                sentiment = reflection.sentiment,
                createdAt = reflection.createdAt,
                schemaVersion = reflection.schemaVersion
            )
            dao.insertReflection(entity)
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }

    override suspend fun getReflection(id: String): Result<Reflection?, Exception> = withContext(Dispatchers.IO) {
        try {
            val entity = dao.getReflection(id)
            if (entity != null) {
                val domainModel = Reflection(
                    id = entity.id,
                    userId = entity.userId,
                    targetEntityId = entity.targetEntityId,
                    targetEntityType = entity.targetEntityType,
                    content = entity.content,
                    sentiment = entity.sentiment,
                    createdAt = entity.createdAt,
                    schemaVersion = entity.schemaVersion
                )
                Result.Success(domainModel)
            } else {
                Result.Success(null)
            }
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }

    override suspend fun getReflectionsForDateRange(userId: String, startTime: Long, endTime: Long): Result<List<Reflection>, Exception> = withContext(Dispatchers.IO) {
        try {
            val entities = dao.getReflectionsForDateRange(userId, startTime, endTime)
            val domainModels = entities.map { entity ->
                Reflection(
                    id = entity.id,
                    userId = entity.userId,
                    targetEntityId = entity.targetEntityId,
                    targetEntityType = entity.targetEntityType,
                    content = entity.content,
                    sentiment = entity.sentiment,
                    createdAt = entity.createdAt,
                    schemaVersion = entity.schemaVersion
                )
            }
            Result.Success(domainModels)
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }
}
