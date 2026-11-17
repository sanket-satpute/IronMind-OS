package com.sanket_satpute_20.ironmind.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.sanket_satpute_20.ironmind.data.local.entity.SyncConflictEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for durable sync conflict persistence.
 *
 * Guarantees strict SQL-level user partitioning to prevent cross-user conflict leakage.
 */
@Dao
interface SyncConflictDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertConflict(conflict: SyncConflictEntity)

    @Deprecated("Legacy unscoped lookup. Use getConflictByIdForUser in production code.")
    @Query("SELECT * FROM sync_conflicts WHERE conflictId = :conflictId LIMIT 1")
    fun getConflictById(conflictId: String): SyncConflictEntity?

    @Query("SELECT * FROM sync_conflicts WHERE conflictId = :conflictId AND userId = :userId LIMIT 1")
    fun getConflictByIdForUser(conflictId: String, userId: String): SyncConflictEntity?

    @Deprecated("Legacy unscoped lookup. Use getConflictByOperationIdForUser in production code.")
    @Query("SELECT * FROM sync_conflicts WHERE operationId = :operationId LIMIT 1")
    fun getConflictByOperationId(operationId: String): SyncConflictEntity?

    @Query("SELECT * FROM sync_conflicts WHERE operationId = :operationId AND userId = :userId LIMIT 1")
    fun getConflictByOperationIdForUser(operationId: String, userId: String): SyncConflictEntity?

    @Query("SELECT * FROM sync_conflicts WHERE userId = :userId ORDER BY createdAt DESC")
    fun observeConflictsForUser(userId: String): Flow<List<SyncConflictEntity>>

    @Query("SELECT * FROM sync_conflicts WHERE userId = :userId ORDER BY createdAt DESC")
    fun getConflictsForUser(userId: String): List<SyncConflictEntity>

    @Query("SELECT * FROM sync_conflicts WHERE userId = :userId AND resolutionStatus = 'UNRESOLVED' ORDER BY createdAt DESC")
    fun getUnresolvedConflictsForUser(userId: String): List<SyncConflictEntity>

    @Query("SELECT COUNT(*) FROM sync_conflicts WHERE userId = :userId AND resolutionStatus = 'UNRESOLVED'")
    fun countUnresolvedConflictsForUser(userId: String): Int

    @Deprecated("Unscoped update by PK. Use updateResolutionStatusForUser in production code.")
    @Update
    fun updateConflict(conflict: SyncConflictEntity)

    @Deprecated("Legacy unscoped update. Use updateResolutionStatusForUser in production code.")
    @Query("UPDATE sync_conflicts SET resolutionStatus = :resolutionStatus, resolvedAt = :resolvedAt, resolvedBy = :resolvedBy, updatedAt = :updatedAt WHERE conflictId = :conflictId")
    fun updateResolutionStatus(
        conflictId: String,
        resolutionStatus: String,
        resolvedAt: Long,
        resolvedBy: String,
        updatedAt: Long
    )

    @Query("UPDATE sync_conflicts SET resolutionStatus = :resolutionStatus, resolvedAt = :resolvedAt, resolvedBy = :resolvedBy, updatedAt = :updatedAt WHERE conflictId = :conflictId AND userId = :userId")
    fun updateResolutionStatusForUser(
        conflictId: String,
        userId: String,
        resolutionStatus: String,
        resolvedAt: Long,
        resolvedBy: String,
        updatedAt: Long
    ): Int

    @Query("UPDATE sync_conflicts SET resolutionStatus = :resolutionStatus, remotePayload = :remotePayload, resolvedAt = :resolvedAt, resolvedBy = :resolvedBy, updatedAt = :updatedAt WHERE conflictId = :conflictId AND userId = :userId")
    fun updateResolutionStatusWithDataForUser(
        conflictId: String,
        userId: String,
        resolutionStatus: String,
        remotePayload: String?,
        resolvedAt: Long,
        resolvedBy: String,
        updatedAt: Long
    ): Int

    @Query("UPDATE sync_conflicts SET resolutionStatus = :resolutionStatus, resolvedAt = :resolvedAt, resolvedBy = :resolvedBy, updatedAt = :updatedAt WHERE conflictId = :conflictId AND userId = :userId AND resolutionStatus = 'UNRESOLVED'")
    fun compareAndSetResolutionStatusForUser(
        conflictId: String,
        userId: String,
        resolutionStatus: String,
        resolvedAt: Long,
        resolvedBy: String,
        updatedAt: Long
    ): Int

    @Deprecated("Legacy unscoped delete. Use deleteConflictForUser in production code.")
    @Query("DELETE FROM sync_conflicts WHERE conflictId = :conflictId")
    fun deleteConflict(conflictId: String)

    @Query("DELETE FROM sync_conflicts WHERE conflictId = :conflictId AND userId = :userId")
    fun deleteConflictForUser(conflictId: String, userId: String): Int

    @Query("DELETE FROM sync_conflicts WHERE userId = :userId")
    fun deleteConflictsForUser(userId: String)

    @Deprecated("Legacy unscoped query for diagnostics only. Do not use in user UI.")
    @Query("SELECT * FROM sync_conflicts ORDER BY createdAt DESC")
    fun getAllConflicts(): List<SyncConflictEntity>
}
