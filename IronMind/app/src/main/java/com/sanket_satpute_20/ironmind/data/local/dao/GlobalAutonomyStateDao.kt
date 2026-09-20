package com.sanket_satpute_20.ironmind.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.sanket_satpute_20.ironmind.data.local.entity.GlobalAutonomyStateEntity

@Dao
interface GlobalAutonomyStateDao {
    @Query("SELECT * FROM global_autonomy_state WHERE userId = :userId LIMIT 1")
    suspend fun getStateForUser(userId: String): GlobalAutonomyStateEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertState(state: GlobalAutonomyStateEntity)
}
