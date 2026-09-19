package com.sanket_satpute_20.ironmind.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.sanket_satpute_20.ironmind.data.local.entity.ObservationEntity

@Dao
interface ObservationDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertObservation(observation: ObservationEntity)

    @Query("SELECT * FROM observations WHERE userId = :userId ORDER BY occurredAt DESC LIMIT :limit OFFSET :offset")
    fun getObservations(userId: String, limit: Int, offset: Int): List<ObservationEntity>

    @Query("SELECT * FROM observations WHERE userId = :userId AND type = :type ORDER BY occurredAt DESC LIMIT :limit OFFSET :offset")
    fun getObservationsByType(userId: String, type: String, limit: Int, offset: Int): List<ObservationEntity>

    @Query("SELECT * FROM observations WHERE id = :id")
    fun getObservationById(id: String): ObservationEntity?

    @Query("DELETE FROM observations WHERE id = :id")
    fun deleteObservation(id: String)
}
