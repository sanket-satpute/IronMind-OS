package com.sanket_satpute_20.ironmind.domain.repository

import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.pattern.Pattern
import com.sanket_satpute_20.ironmind.domain.model.pattern.PatternType
import com.sanket_satpute_20.ironmind.domain.model.pattern.PatternStatus

interface PatternRepository {
    suspend fun getPattern(id: String): Result<Pattern?, Exception>
    suspend fun getPatternsForUser(userId: String): Result<List<Pattern>, Exception>
    fun observePatternsForUser(userId: String): kotlinx.coroutines.flow.Flow<List<Pattern>> = kotlinx.coroutines.flow.emptyFlow()
    suspend fun getPatternsByType(userId: String, type: PatternType): Result<List<Pattern>, Exception>
    suspend fun getPatternsByStatus(userId: String, status: PatternStatus): Result<List<Pattern>, Exception>
    suspend fun getPatternsByStatus(userId: String, status: PatternStatus, limit: Int): Result<List<Pattern>, Exception>
    suspend fun savePattern(pattern: Pattern): Result<Unit, Exception>
    suspend fun updatePattern(pattern: Pattern): Result<Unit, Exception>
    suspend fun updatePatternConfidence(id: String, confidence: Float, lastObservedAt: Long): Result<Unit, Exception>
    suspend fun updatePatternState(
        id: String,
        userId: String,
        confirmationState: com.sanket_satpute_20.ironmind.domain.model.MemoryConfirmationState,
        status: PatternStatus,
        updatedAt: Long
    ): Result<Unit, Exception> = Result.Success(Unit)

    suspend fun updatePatternStateWithEvent(
        id: String,
        userId: String,
        confirmationState: com.sanket_satpute_20.ironmind.domain.model.MemoryConfirmationState,
        status: PatternStatus,
        updatedAt: Long,
        event: com.sanket_satpute_20.ironmind.domain.model.Event? = null
    ): Result<Unit, Exception> = updatePatternState(id, userId, confirmationState, status, updatedAt)
    suspend fun deletePattern(id: String): Result<Unit, Exception>
    suspend fun getPatternByFingerprint(userId: String, fingerprint: String): Result<Pattern?, Exception>
}
