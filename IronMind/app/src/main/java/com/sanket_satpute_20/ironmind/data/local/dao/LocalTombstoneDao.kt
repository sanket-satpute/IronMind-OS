package com.sanket_satpute_20.ironmind.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.sanket_satpute_20.ironmind.data.local.entity.LocalTombstoneEntity

@Dao
interface LocalTombstoneDao {

    @Query("""
        SELECT * FROM local_tombstones
        WHERE userId = :userId AND entityType = :entityType AND entityId = :entityId
        LIMIT 1
    """)
    fun getTombstone(userId: String, entityType: String, entityId: String): LocalTombstoneEntity?

    @Query("""
        SELECT * FROM local_tombstones
        WHERE userId = :userId AND entityType = :entityType AND entityId = :entityId
        LIMIT 1
    """)
    suspend fun getTombstoneAsync(userId: String, entityType: String, entityId: String): LocalTombstoneEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertTombstone(tombstone: LocalTombstoneEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertOrUpdate(tombstone: LocalTombstoneEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTombstoneAsync(tombstone: LocalTombstoneEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateAsync(tombstone: LocalTombstoneEntity)

    @Query("DELETE FROM local_tombstones WHERE userId = :userId AND entityType = :entityType AND entityId = :entityId")
    fun deleteTombstone(userId: String, entityType: String, entityId: String): Int

    @Query("DELETE FROM local_tombstones WHERE userId = :userId AND entityType = :entityType AND entityId = :entityId")
    suspend fun deleteTombstoneAsync(userId: String, entityType: String, entityId: String): Int

    @Query("SELECT * FROM local_tombstones WHERE userId = :userId AND status = 'PENDING'")
    fun getPendingTombstones(userId: String): List<LocalTombstoneEntity>

    @Query("DELETE FROM local_tombstones WHERE userId = :userId")
    fun clearTombstonesForUser(userId: String)

    @Query("DELETE FROM local_tombstones WHERE userId = :userId")
    suspend fun clearTombstonesForUserAsync(userId: String)
}
