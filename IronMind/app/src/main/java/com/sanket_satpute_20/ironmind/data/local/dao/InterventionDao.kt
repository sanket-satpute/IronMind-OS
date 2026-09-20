package com.sanket_satpute_20.ironmind.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.sanket_satpute_20.ironmind.data.local.entity.InterventionRecordEntity

@Dao
interface InterventionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(record: InterventionRecordEntity)

    @Update
    fun update(record: InterventionRecordEntity)

    @Query("SELECT * FROM intervention_records WHERE id = :id")
    fun getById(id: String): InterventionRecordEntity?

    @Query("SELECT * FROM intervention_records WHERE userId = :userId AND type = :type ORDER BY createdAt DESC")
    fun getInterventionsByType(userId: String, type: String): List<InterventionRecordEntity>

    @Query("SELECT COUNT(*) FROM intervention_records")
    fun getInterventionCount(): kotlinx.coroutines.flow.Flow<Int>

    // Sprint V4.13: Data Deletion & Lifecycle Foundation
    @Query("DELETE FROM intervention_records WHERE userId = :userId")
    fun deleteInterventionsForUser(userId: String)

    @Query("DELETE FROM intervention_records")
    fun deleteAll()

    @Query("SELECT * FROM intervention_records WHERE userId = :userId AND createdAt >= :since ORDER BY createdAt DESC")
    fun getRecentInterventions(userId: String, since: Long): List<InterventionRecordEntity>

    @Query("SELECT * FROM intervention_records WHERE userId = :userId AND state IN ('PROPOSED', 'APPROVED', 'TRIGGERED')")
    fun getActiveInterventions(userId: String): List<InterventionRecordEntity>
}
