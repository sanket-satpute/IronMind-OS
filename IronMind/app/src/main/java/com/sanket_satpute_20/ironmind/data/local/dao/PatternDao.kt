package com.sanket_satpute_20.ironmind.data.local.dao

import androidx.room.*
import com.sanket_satpute_20.ironmind.data.local.entity.PatternEntity

@Dao
interface PatternDao {

    @Query("SELECT * FROM patterns WHERE id = :id")
    fun getPatternById(id: String): PatternEntity?

    @Query("SELECT * FROM patterns WHERE userId = :userId ORDER BY lastObservedAt DESC")
    fun getPatternsForUser(userId: String): List<PatternEntity>

    @Query("SELECT * FROM patterns WHERE userId = :userId AND type = :type ORDER BY lastObservedAt DESC")
    fun getPatternsByType(userId: String, type: String): List<PatternEntity>

    @Query("SELECT * FROM patterns WHERE userId = :userId AND status = :status ORDER BY lastObservedAt DESC")
    fun getPatternsByStatus(userId: String, status: String): List<PatternEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertPattern(pattern: PatternEntity)

    @Query("UPDATE patterns SET confidence = :confidence, lastObservedAt = :lastObservedAt, updatedAt = :updatedAt WHERE id = :id")
    fun updatePatternConfidence(id: String, confidence: Float, lastObservedAt: Long, updatedAt: Long)

    @Query("DELETE FROM patterns WHERE id = :id")
    fun deletePattern(id: String)
}
