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
}
