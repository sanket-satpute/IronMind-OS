package com.sanket_satpute_20.ironmind.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.sanket_satpute_20.ironmind.data.local.entity.InterventionRecommendationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface InterventionRecommendationDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(entity: InterventionRecommendationEntity)

    @Query("SELECT * FROM intervention_recommendations WHERE id = :id")
    fun getById(id: String): InterventionRecommendationEntity?

    @Query("SELECT * FROM intervention_recommendations WHERE userId = :userId AND status = 'PENDING' AND (expiresAt IS NULL OR expiresAt > :currentTime)")
    fun getPendingRecommendations(userId: String, currentTime: Long): Flow<List<InterventionRecommendationEntity>>

    @Query("UPDATE intervention_recommendations SET status = :status WHERE id = :id")
    fun updateStatus(id: String, status: String)

    @Query("""
        SELECT * FROM intervention_recommendations 
        WHERE userId = :userId 
        AND objective = :objective 
        AND (targetEntityType = :targetEntityType OR (targetEntityType IS NULL AND :targetEntityType IS NULL))
        AND (targetEntityId = :targetEntityId OR (targetEntityId IS NULL AND :targetEntityId IS NULL))
        ORDER BY createdAt DESC
    """)
    fun getEquivalentRecommendations(
        userId: String,
        objective: String,
        targetEntityType: String?,
        targetEntityId: String?
    ): List<InterventionRecommendationEntity>
}
