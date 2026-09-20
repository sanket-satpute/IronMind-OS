package com.sanket_satpute_20.ironmind.domain.repository

import com.sanket_satpute_20.ironmind.domain.common.Result

interface DataManagementRepository {
    suspend fun deleteAllUserData(userId: String): Result<Unit, Exception>
    suspend fun cleanupExpiredPatterns(userId: String, thresholdTime: Long): Result<Unit, Exception>
    suspend fun cleanupForgottenMemories(userId: String, thresholdTime: Long): Result<Unit, Exception>
}
