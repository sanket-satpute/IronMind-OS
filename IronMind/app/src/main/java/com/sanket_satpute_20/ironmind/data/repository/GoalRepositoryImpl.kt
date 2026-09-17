package com.sanket_satpute_20.ironmind.data.repository

import com.sanket_satpute_20.ironmind.data.local.dao.IronMindDao
import com.sanket_satpute_20.ironmind.data.local.entity.GoalEntity
import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.Goal
import com.sanket_satpute_20.ironmind.domain.model.GoalStatus
import com.sanket_satpute_20.ironmind.domain.repository.GoalRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class GoalRepositoryImpl(
    private val dao: IronMindDao
) : GoalRepository {

    override suspend fun saveGoal(goal: Goal): Result<Unit, Exception> {
        return try {
            withContext(Dispatchers.IO) {
                dao.insertGoal(goal.toEntity())
            }
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }

    override suspend fun getGoal(id: String): Result<Goal?, Exception> {
        return try {
            val entity = withContext(Dispatchers.IO) {
                dao.getGoal(id)
            }
            Result.Success(entity?.toDomain())
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }

    override suspend fun getGoalsForUser(userId: String): Result<List<Goal>, Exception> {
        return try {
            val entities = withContext(Dispatchers.IO) {
                dao.getGoalsForUser(userId)
            }
            Result.Success(entities.map { it.toDomain() })
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }

    private fun Goal.toEntity(): GoalEntity {
        return GoalEntity(
            id = id,
            userId = userId,
            ambitionId = ambitionId,
            title = title,
            description = description,
            why = why,
            importance = importance,
            status = status.name,
            targetAt = targetAt,
            startedAt = startedAt,
            completedAt = completedAt,
            createdAt = createdAt,
            updatedAt = updatedAt,
            schemaVersion = 1
        )
    }

    private fun GoalEntity.toDomain(): Goal {
        return Goal(
            id = id,
            userId = userId,
            ambitionId = ambitionId,
            title = title,
            description = description,
            why = why,
            importance = importance,
            status = GoalStatus.valueOf(status),
            targetAt = targetAt,
            startedAt = startedAt,
            completedAt = completedAt,
            createdAt = createdAt,
            updatedAt = updatedAt
        )
    }
}
