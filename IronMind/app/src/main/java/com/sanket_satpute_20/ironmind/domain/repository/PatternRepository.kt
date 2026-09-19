package com.sanket_satpute_20.ironmind.domain.repository

import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.pattern.Pattern
import com.sanket_satpute_20.ironmind.domain.model.pattern.PatternType
import com.sanket_satpute_20.ironmind.domain.model.pattern.PatternStatus

interface PatternRepository {
    suspend fun getPattern(id: String): Result<Pattern?, Exception>
    suspend fun getPatternsForUser(userId: String): Result<List<Pattern>, Exception>
    suspend fun getPatternsByType(userId: String, type: PatternType): Result<List<Pattern>, Exception>
    suspend fun getPatternsByStatus(userId: String, status: PatternStatus): Result<List<Pattern>, Exception>
    suspend fun savePattern(pattern: Pattern): Result<Unit, Exception>
    suspend fun updatePatternConfidence(id: String, confidence: Float, lastObservedAt: Long): Result<Unit, Exception>
    suspend fun deletePattern(id: String): Result<Unit, Exception>
}
