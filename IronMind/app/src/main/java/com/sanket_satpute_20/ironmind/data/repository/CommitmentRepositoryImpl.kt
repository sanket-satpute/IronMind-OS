package com.sanket_satpute_20.ironmind.data.repository

import com.sanket_satpute_20.ironmind.data.local.dao.IronMindDao
import com.sanket_satpute_20.ironmind.data.local.entity.CommitmentEntity
import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.Commitment
import com.sanket_satpute_20.ironmind.domain.model.CommitmentStatus
import com.sanket_satpute_20.ironmind.domain.model.EntitySource
import com.sanket_satpute_20.ironmind.domain.repository.CommitmentRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class CommitmentRepositoryImpl(
    private val dao: IronMindDao
) : CommitmentRepository {

    override suspend fun saveCommitment(commitment: Commitment): Result<Unit, Exception> {
        return try {
            withContext(Dispatchers.IO) {
                dao.insertCommitment(commitment.toEntity())
            }
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }

    override suspend fun getCommitment(id: String): Result<Commitment?, Exception> {
        return try {
            val entity = withContext(Dispatchers.IO) {
                dao.getCommitment(id)
            }
            Result.Success(entity?.toDomain())
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }

    override suspend fun getCommitmentsForUser(userId: String): Result<List<Commitment>, Exception> {
        return try {
            val entities = withContext(Dispatchers.IO) {
                dao.getCommitmentsForUser(userId)
            }
            Result.Success(entities.map { it.toDomain() })
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }

    override suspend fun getActiveCommitmentsForUser(
        userId: String,
        statuses: List<CommitmentStatus>
    ): Result<List<Commitment>, Exception> {
        return try {
            val entities = withContext(Dispatchers.IO) {
                dao.getActiveCommitmentsForUser(userId, statuses.map { it.name })
            }
            Result.Success(entities.map { it.toDomain() })
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }

    override suspend fun getCommitmentsForGoal(goalId: String): Result<List<Commitment>, Exception> {
        return try {
            val entities = withContext(Dispatchers.IO) {
                dao.getCommitmentsForGoal(goalId)
            }
            Result.Success(entities.map { it.toDomain() })
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }

    override suspend fun getCommitmentsForPlan(planId: String): Result<List<Commitment>, Exception> {
        return try {
            val entities = withContext(Dispatchers.IO) {
                dao.getCommitmentsForPlan(planId)
            }
            Result.Success(entities.map { it.toDomain() })
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }

    override suspend fun getCommitmentsForTask(taskId: String): Result<List<Commitment>, Exception> {
        return try {
            val entities = withContext(Dispatchers.IO) {
                dao.getCommitmentsForTask(taskId)
            }
            Result.Success(entities.map { it.toDomain() })
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }

    private fun Commitment.toEntity(): CommitmentEntity {
        return CommitmentEntity(
            id = id,
            userId = userId,
            goalId = goalId,
            planId = planId,
            taskId = taskId,
            title = title,
            description = description,
            committedAt = committedAt,
            scheduledStartAt = scheduledStartAt,
            scheduledEndAt = scheduledEndAt,
            status = status.name,
            priority = priority,
            source = source.name,
            createdAt = createdAt,
            updatedAt = updatedAt,
            startedAt = startedAt,
            completedAt = completedAt,
            postponedAt = postponedAt,
            missedAt = missedAt,
            recoveredAt = recoveredAt,
            parentCommitmentId = parentCommitmentId,
            schemaVersion = 1
        )
    }

    private fun CommitmentEntity.toDomain(): Commitment {
        return Commitment(
            id = id,
            userId = userId,
            goalId = goalId,
            planId = planId,
            taskId = taskId,
            parentCommitmentId = parentCommitmentId,
            title = title,
            description = description,
            committedAt = committedAt,
            scheduledStartAt = scheduledStartAt,
            scheduledEndAt = scheduledEndAt,
            status = CommitmentStatus.valueOf(status),
            priority = priority,
            source = EntitySource.valueOf(source),
            createdAt = createdAt,
            updatedAt = updatedAt,
            startedAt = startedAt,
            completedAt = completedAt,
            postponedAt = postponedAt,
            missedAt = missedAt,
            recoveredAt = recoveredAt
        )
    }
}
