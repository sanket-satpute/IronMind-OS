package com.sanket_satpute_20.ironmind.data.repository

import com.sanket_satpute_20.ironmind.data.local.dao.IronMindDao
import com.sanket_satpute_20.ironmind.data.local.entity.MemoryEntity
import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.EntitySource
import com.sanket_satpute_20.ironmind.domain.model.MemoryStatus
import com.sanket_satpute_20.ironmind.domain.model.action.ActionJsonCodec
import com.sanket_satpute_20.ironmind.domain.model.action.ActionReviewStatus
import com.sanket_satpute_20.ironmind.domain.model.action.ProposedAction
import com.sanket_satpute_20.ironmind.domain.repository.ActionRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * Phase 3.5.1: ActionRepository implementation utilizing Room MemoryEntity table.
 * Provides durable local storage across app restarts with ZERO Room migrations.
 */
class ActionRepositoryImpl(
    private val dao: IronMindDao
) : ActionRepository {

    companion object {
        const val MEMORY_TYPE_PROPOSED_ACTION = "PROPOSED_ACTION"
    }

    override suspend fun saveAction(action: ProposedAction): Result<ProposedAction, Exception> = withContext(Dispatchers.IO) {
        try {
            require(action.userId.isNotBlank()) { "userId must not be blank" }
            require(action.id.isNotBlank()) { "action id must not be blank" }

            val jsonPayload = ActionJsonCodec.encodeToJson(action)

            val memoryEntity = MemoryEntity(
                id = action.id,
                userId = action.userId,
                type = MEMORY_TYPE_PROPOSED_ACTION,
                content = jsonPayload,
                source = EntitySource.SYSTEM.name,
                confidence = action.confidence,
                evidenceCount = action.supportingEvidenceIds.size,
                firstObservedAt = action.createdAt,
                lastObservedAt = action.executedAt ?: action.createdAt,
                confirmationState = action.reviewStatus.name,
                status = MemoryStatus.ACTIVE.name,
                expiresAt = action.expiresAt,
                createdAt = action.createdAt,
                updatedAt = System.currentTimeMillis(),
                schemaVersion = 1
            )

            dao.insertMemory(memoryEntity)
            Result.Success(action)
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }

    override suspend fun getActionById(id: String): Result<ProposedAction?, Exception> = withContext(Dispatchers.IO) {
        try {
            val entity = dao.getMemoryById(id)
            if (entity == null || entity.type != MEMORY_TYPE_PROPOSED_ACTION) {
                return@withContext Result.Success(null)
            }
            val action = ActionJsonCodec.decodeFromJson(
                id = entity.id,
                userId = entity.userId,
                jsonString = entity.content,
                fallbackCreatedAt = entity.createdAt
            )
            Result.Success(action)
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }

    override suspend fun getActionsForUser(userId: String): Result<List<ProposedAction>, Exception> = withContext(Dispatchers.IO) {
        try {
            val entities = dao.getMemoriesForUser(userId)
            val actions = entities
                .filter { it.type == MEMORY_TYPE_PROPOSED_ACTION && it.status != MemoryStatus.DELETED.name }
                .mapNotNull {
                    ActionJsonCodec.decodeFromJson(
                        id = it.id,
                        userId = it.userId,
                        jsonString = it.content,
                        fallbackCreatedAt = it.createdAt
                    )
                }
            Result.Success(actions)
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }

    override fun observeActionsForUser(userId: String): Flow<List<ProposedAction>> {
        return dao.observeMemoriesForUser(userId)
            .map { list ->
                list.filter { it.type == MEMORY_TYPE_PROPOSED_ACTION && it.status != MemoryStatus.DELETED.name }
                    .mapNotNull {
                        ActionJsonCodec.decodeFromJson(
                            id = it.id,
                            userId = it.userId,
                            jsonString = it.content,
                            fallbackCreatedAt = it.createdAt
                        )
                    }
            }
            .catch { emit(emptyList()) }
    }

    override suspend fun updateActionStatus(
        id: String,
        userId: String,
        status: ActionReviewStatus,
        timestamp: Long
    ): Result<Unit, Exception> = withContext(Dispatchers.IO) {
        try {
            val existing = when (val res = getActionById(id)) {
                is Result.Success -> res.data
                is Result.Failure -> return@withContext Result.Failure(res.error)
            } ?: return@withContext Result.Failure(IllegalArgumentException("Action not found: $id"))

            if (existing.userId != userId) {
                return@withContext Result.Failure(SecurityException("Action $id does not belong to user $userId"))
            }

            val updatedAction = existing.copy(
                reviewStatus = status,
                reviewedAt = timestamp
            )
            saveAction(updatedAction)
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }

    override suspend fun deleteAction(id: String, userId: String): Result<Unit, Exception> = withContext(Dispatchers.IO) {
        try {
            val existing = when (val res = getActionById(id)) {
                is Result.Success -> res.data
                is Result.Failure -> return@withContext Result.Failure(res.error)
            } ?: return@withContext Result.Failure(IllegalArgumentException("Action not found: $id"))

            if (existing.userId != userId) {
                return@withContext Result.Failure(SecurityException("Action $id does not belong to user $userId"))
            }

            dao.deleteMemory(userId, id)
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }
}
