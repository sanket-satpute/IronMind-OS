package com.sanket_satpute_20.ironmind.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.sanket_satpute_20.ironmind.data.local.entity.OutboxEntity

@Dao
interface OutboxDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insertEntry(entry: OutboxEntity)

    @Query("SELECT * FROM outbox WHERE status IN ('PENDING', 'FAILED') ORDER BY createdAt ASC")
    fun getPendingEntries(): List<OutboxEntity>

    @Query("UPDATE outbox SET status = :status, retryCount = :retryCount, lastAttemptAt = :lastAttemptAt WHERE operationId = :operationId")
    fun updateStatus(operationId: String, status: String, retryCount: Int, lastAttemptAt: Long)

    @Query("DELETE FROM outbox WHERE operationId = :operationId")
    fun deleteEntry(operationId: String)

    @Query("SELECT COUNT(*) FROM outbox WHERE status IN ('PENDING', 'FAILED')")
    fun countPendingEntries(): Int

    @Query("SELECT COUNT(*) FROM outbox")
    fun getOutboxCount(): kotlinx.coroutines.flow.Flow<Int>

    @Query("SELECT COUNT(*) > 0 FROM outbox WHERE operationId = :operationId")
    fun entryExists(operationId: String): Boolean
}
