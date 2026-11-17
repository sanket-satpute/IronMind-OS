package com.sanket_satpute_20.ironmind.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.sanket_satpute_20.ironmind.data.local.entity.AppSessionStateEntity

@Dao
interface AppSessionStateDao {

    @Query("SELECT * FROM app_session_state WHERE id = 1 LIMIT 1")
    fun getSessionState(): AppSessionStateEntity?

    @Query("SELECT * FROM app_session_state WHERE id = 1 LIMIT 1")
    suspend fun getSessionStateAsync(): AppSessionStateEntity?

    @Query("SELECT currentGeneration FROM app_session_state WHERE id = 1 LIMIT 1")
    fun getCurrentGeneration(): Long?

    @Query("SELECT currentGeneration FROM app_session_state WHERE id = 1 LIMIT 1")
    suspend fun getCurrentGenerationAsync(): Long?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun setSessionState(state: AppSessionStateEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun setSessionStateAsync(state: AppSessionStateEntity)

    @Query("""
        UPDATE app_session_state
        SET currentGeneration = currentGeneration + 1,
            activeUserId = :newUserId,
            updatedAt = :now
        WHERE id = 1
    """)
    fun advanceSessionGeneration(newUserId: String, now: Long): Int

    @Query("""
        UPDATE app_session_state
        SET currentGeneration = currentGeneration + 1,
            activeUserId = :newUserId,
            updatedAt = :now
        WHERE id = 1
    """)
    suspend fun advanceSessionGenerationAsync(newUserId: String, now: Long): Int
}
