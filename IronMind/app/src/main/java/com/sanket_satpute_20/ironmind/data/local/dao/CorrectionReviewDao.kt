package com.sanket_satpute_20.ironmind.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.sanket_satpute_20.ironmind.data.local.entity.CorrectionReviewEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CorrectionReviewDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(entity: CorrectionReviewEntity): Long

    @Update
    suspend fun update(entity: CorrectionReviewEntity): Int

    @Query("SELECT * FROM correction_reviews WHERE id = :id AND userId = :userId LIMIT 1")
    suspend fun getById(id: String, userId: String): CorrectionReviewEntity?

    @Query("SELECT * FROM correction_reviews WHERE sourceEventId = :sourceEventId AND userId = :userId LIMIT 1")
    suspend fun getBySourceEventId(sourceEventId: String, userId: String): CorrectionReviewEntity?

    @Query("SELECT * FROM correction_reviews WHERE userId = :userId AND reviewStatus = :status ORDER BY createdAt DESC LIMIT :limit")
    suspend fun getByStatus(userId: String, status: String, limit: Int = 100): List<CorrectionReviewEntity>

    @Query("SELECT * FROM correction_reviews WHERE userId = :userId ORDER BY createdAt DESC LIMIT :limit")
    suspend fun getAllForUser(userId: String, limit: Int = 100): List<CorrectionReviewEntity>

    @Query("SELECT * FROM correction_reviews WHERE userId = :userId ORDER BY createdAt DESC, id ASC")
    suspend fun getAllReviewsForUser(userId: String): List<CorrectionReviewEntity>

    @Query("SELECT * FROM correction_reviews WHERE userId = :userId AND reviewStatus = 'PENDING' ORDER BY createdAt DESC")
    fun observePendingForUser(userId: String): Flow<List<CorrectionReviewEntity>>

    @Query("UPDATE correction_reviews SET reviewStatus = :newStatus, reviewFeedback = :feedback, reviewedAt = :reviewedAt, updatedAt = :updatedAt WHERE id = :id AND userId = :userId")
    suspend fun updateStatus(id: String, userId: String, newStatus: String, feedback: String?, reviewedAt: Long, updatedAt: Long): Int

    @Query("DELETE FROM correction_reviews WHERE userId = :userId")
    suspend fun deleteAllForUser(userId: String): Int
}
