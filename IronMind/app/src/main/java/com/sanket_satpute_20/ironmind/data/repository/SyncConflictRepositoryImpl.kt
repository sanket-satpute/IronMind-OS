package com.sanket_satpute_20.ironmind.data.repository

import androidx.room.withTransaction
import com.sanket_satpute_20.ironmind.data.local.IronMindDatabase
import com.sanket_satpute_20.ironmind.data.local.dao.IronMindDao
import com.sanket_satpute_20.ironmind.data.local.dao.OutboxDao
import com.sanket_satpute_20.ironmind.data.local.dao.SyncConflictDao
import com.sanket_satpute_20.ironmind.data.local.entity.CommitmentEntity
import com.sanket_satpute_20.ironmind.data.local.entity.GoalEntity
import com.sanket_satpute_20.ironmind.data.local.entity.MemoryEntity
import com.sanket_satpute_20.ironmind.data.local.entity.PlanEntity
import com.sanket_satpute_20.ironmind.data.local.entity.ReflectionEntity
import com.sanket_satpute_20.ironmind.data.local.entity.SyncConflictEntity
import com.sanket_satpute_20.ironmind.data.local.entity.TaskEntity
import com.sanket_satpute_20.ironmind.data.local.entity.toDomain
import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.SyncConflict
import com.sanket_satpute_20.ironmind.domain.repository.ConflictResolutionStrategy
import com.sanket_satpute_20.ironmind.domain.repository.SyncConflictRepository
import org.json.JSONObject

class SyncConflictRepositoryImpl(
    private val syncConflictDao: SyncConflictDao,
    private val outboxDao: OutboxDao,
    private val database: IronMindDatabase? = null,
    private val ironMindDao: IronMindDao? = null
) : SyncConflictRepository {

    override suspend fun getUnresolvedConflictsForUser(userId: String): Result<List<SyncConflict>, Exception> {
        if (userId.isBlank()) return Result.Failure(IllegalArgumentException("userId cannot be blank"))
        return try {
            val conflicts = syncConflictDao.getUnresolvedConflictsForUser(userId)
            Result.Success(conflicts.map { it.toDomain() })
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }

    override suspend fun getConflictByIdForUser(
        conflictId: String,
        userId: String
    ): Result<SyncConflict?, Exception> {
        if (userId.isBlank() || conflictId.isBlank()) {
            return Result.Failure(IllegalArgumentException("userId and conflictId cannot be blank"))
        }
        return try {
            val conflict = syncConflictDao.getConflictByIdForUser(conflictId, userId)
            Result.Success(conflict?.toDomain())
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }

    override suspend fun resolveConflictForUser(
        conflictId: String,
        userId: String,
        strategy: ConflictResolutionStrategy,
        resolvedBy: String
    ): Result<Unit, Exception> {
        if (userId.isBlank() || conflictId.isBlank()) {
            return Result.Failure(IllegalArgumentException("userId and conflictId cannot be blank"))
        }
        return try {
            val executeResolution = suspend {
                val conflict = syncConflictDao.getConflictByIdForUser(conflictId, userId)
                    ?: throw NoSuchElementException("Conflict $conflictId not found for user $userId")

                if (conflict.resolutionStatus != "UNRESOLVED") {
                    throw IllegalStateException("Conflict $conflictId has already been resolved with status ${conflict.resolutionStatus}")
                }

                val now = System.currentTimeMillis()

                when (strategy) {
                    ConflictResolutionStrategy.USE_LOCAL -> {
                        // Rebase local outbox mutation against remote version
                        val remoteVersion = try {
                            val rJson = JSONObject(conflict.remotePayload ?: "{}")
                            val v = rJson.optLong("version", -1L)
                            if (v >= 0L) v else rJson.optLong("updatedAt", 0L)
                        } catch (_: Exception) {
                            0L
                        }

                        // Advance updatedAt and set baseVersion to remoteVersion in local payload
                        val updatedPayload = try {
                            val lJson = JSONObject(conflict.localPayload)
                            lJson.put("updatedAt", now)
                            lJson.put("baseVersion", remoteVersion)
                            lJson.toString()
                        } catch (_: Exception) {
                            conflict.localPayload
                        }

                        // Rebase outbox entry
                        outboxDao.updateBaseVersionAndPayload(
                            operationId = conflict.operationId,
                            userId = userId,
                            newBaseVersion = remoteVersion,
                            newPayload = updatedPayload,
                            now = now
                        )

                        // Atomic CAS: ensure conflict was still UNRESOLVED
                        val updatedRows = syncConflictDao.compareAndSetResolutionStatusForUser(
                            conflictId = conflictId,
                            userId = userId,
                            resolutionStatus = "RESOLVED_LOCAL",
                            resolvedAt = now,
                            resolvedBy = resolvedBy,
                            updatedAt = now
                        )
                        if (updatedRows <= 0) {
                            throw IllegalStateException("Conflict $conflictId was concurrently resolved")
                        }
                    }
                    ConflictResolutionStrategy.USE_REMOTE -> {
                        // Update local Room entity to converge with remote payload
                        if (!conflict.remotePayload.isNullOrBlank()) {
                            applyRemotePayloadToLocal(conflict.entityType, conflict.remotePayload)
                        }

                        // Discard local outbox entry (remote version takes precedence)
                        outboxDao.deleteEntryForUser(conflict.operationId, userId)

                        val updatedRows = syncConflictDao.compareAndSetResolutionStatusForUser(
                            conflictId = conflictId,
                            userId = userId,
                            resolutionStatus = "RESOLVED_REMOTE",
                            resolvedAt = now,
                            resolvedBy = resolvedBy,
                            updatedAt = now
                        )
                        if (updatedRows <= 0) {
                            throw IllegalStateException("Conflict $conflictId was concurrently resolved")
                        }
                    }
                    ConflictResolutionStrategy.DISCARD -> {
                        // Discard local outbox entry
                        outboxDao.deleteEntryForUser(conflict.operationId, userId)

                        val updatedRows = syncConflictDao.compareAndSetResolutionStatusForUser(
                            conflictId = conflictId,
                            userId = userId,
                            resolutionStatus = "DISCARDED",
                            resolvedAt = now,
                            resolvedBy = resolvedBy,
                            updatedAt = now
                        )
                        if (updatedRows <= 0) {
                            throw IllegalStateException("Conflict $conflictId was concurrently resolved")
                        }
                    }
                }
            }

            if (database != null) {
                database.withTransaction {
                    executeResolution()
                }
            } else {
                executeResolution()
            }

            Result.Success(Unit)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }

    internal fun applyRemotePayloadToLocal(entityType: String, remotePayload: String) {
        val targetDao = ironMindDao ?: database?.ironMindDao() ?: return
        RemotePayloadApplier.apply(targetDao, entityType, remotePayload)
    }
}
