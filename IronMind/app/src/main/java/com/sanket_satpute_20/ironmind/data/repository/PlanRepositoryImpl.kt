package com.sanket_satpute_20.ironmind.data.repository

import com.sanket_satpute_20.ironmind.data.local.dao.IronMindDao
import com.sanket_satpute_20.ironmind.data.local.entity.PlanEntity
import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.EntitySource
import com.sanket_satpute_20.ironmind.domain.model.Plan
import com.sanket_satpute_20.ironmind.domain.model.PlanStatus
import com.sanket_satpute_20.ironmind.domain.repository.PlanRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class PlanRepositoryImpl(
    private val dao: IronMindDao
) : PlanRepository {

    override suspend fun savePlan(plan: Plan): Result<Unit, Exception> {
        return try {
            withContext(Dispatchers.IO) {
                dao.insertPlan(plan.toEntity())
            }
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }

    override suspend fun getPlan(id: String): Result<Plan?, Exception> {
        return try {
            val entity = withContext(Dispatchers.IO) {
                dao.getPlan(id)
            }
            Result.Success(entity?.toDomain())
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }

    override suspend fun getPlansForGoal(goalId: String): Result<List<Plan>, Exception> {
        return try {
            val entities = withContext(Dispatchers.IO) {
                dao.getPlansForGoal(goalId)
            }
            Result.Success(entities.map { it.toDomain() })
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }

    private fun Plan.toEntity(): PlanEntity {
        return PlanEntity(
            id = id,
            userId = userId,
            goalId = goalId,
            ambitionId = ambitionId,
            title = title,
            description = description,
            status = status.name,
            createdAt = createdAt,
            updatedAt = updatedAt,
            startedAt = startedAt,
            completedAt = completedAt,
            source = source.name,
            schemaVersion = 1
        )
    }

    private fun PlanEntity.toDomain(): Plan {
        return Plan(
            id = id,
            userId = userId,
            goalId = goalId,
            ambitionId = ambitionId,
            title = title,
            description = description,
            status = PlanStatus.valueOf(status),
            createdAt = createdAt,
            updatedAt = updatedAt,
            startedAt = startedAt,
            completedAt = completedAt,
            source = EntitySource.valueOf(source)
        )
    }
}
