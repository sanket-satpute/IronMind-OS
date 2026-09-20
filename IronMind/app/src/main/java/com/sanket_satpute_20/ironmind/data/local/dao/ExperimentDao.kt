package com.sanket_satpute_20.ironmind.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.sanket_satpute_20.ironmind.data.local.entity.ExperimentRecordEntity

@Dao
interface ExperimentDao {
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(record: ExperimentRecordEntity)

    @Update
    fun update(record: ExperimentRecordEntity)

    @Query("SELECT * FROM experiment_records WHERE id = :id")
    fun getById(id: String): ExperimentRecordEntity?

    @Query("SELECT * FROM experiment_records WHERE userId = :userId ORDER BY startedAt DESC")
    fun getAllForUser(userId: String): List<ExperimentRecordEntity>

    @Query("SELECT * FROM experiment_records WHERE userId = :userId AND state = 'ACTIVE'")
    fun getActiveExperiments(userId: String): List<ExperimentRecordEntity>

    @Query("SELECT COUNT(*) FROM experiment_records")
    fun getExperimentCount(): kotlinx.coroutines.flow.Flow<Int>

    // Sprint V4.13: Data Deletion & Lifecycle Foundation
    @Query("DELETE FROM experiment_records WHERE userId = :userId")
    fun deleteExperimentsForUser(userId: String)
}
