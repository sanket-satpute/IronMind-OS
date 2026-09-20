package com.sanket_satpute_20.ironmind.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.sanket_satpute_20.ironmind.data.local.entity.DecisionRecordEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DecisionRecordDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(decisionRecord: DecisionRecordEntity)

    @Query("SELECT * FROM decision_records ORDER BY timestamp DESC")
    fun getAll(): Flow<List<DecisionRecordEntity>>

    @Query("SELECT * FROM decision_records WHERE capability = :capability ORDER BY timestamp DESC")
    fun getByCapability(capability: String): Flow<List<DecisionRecordEntity>>
    @Query("SELECT COUNT(*) FROM decision_records")
    fun getDecisionCount(): kotlinx.coroutines.flow.Flow<Int>
    
    @Query("DELETE FROM decision_records")
    fun clearAll()
}
