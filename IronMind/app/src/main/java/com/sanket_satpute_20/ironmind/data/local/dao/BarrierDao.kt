package com.sanket_satpute_20.ironmind.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.sanket_satpute_20.ironmind.data.local.entity.BarrierHypothesisEntity

@Dao
interface BarrierDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(barrier: BarrierHypothesisEntity)

    @Query("SELECT * FROM barrier_hypotheses WHERE userId = :userId")
    suspend fun getBarriersByUserId(userId: String): List<BarrierHypothesisEntity>
}
