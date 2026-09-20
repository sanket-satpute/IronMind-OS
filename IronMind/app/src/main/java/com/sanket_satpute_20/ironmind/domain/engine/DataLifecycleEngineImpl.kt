package com.sanket_satpute_20.ironmind.domain.engine

import com.sanket_satpute_20.ironmind.domain.common.Clock
import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.logging.IronLogger
import com.sanket_satpute_20.ironmind.domain.repository.DataManagementRepository

class DataLifecycleEngineImpl(
    private val dataManagementRepository: DataManagementRepository,
    private val clock: Clock,
    private val logger: IronLogger
) : DataLifecycleEngine {

    companion object {
        // Hard delete expired patterns / forgotten memories that are older than 30 days
        private const val RETENTION_WINDOW_MS = 30L * 24L * 60L * 60L * 1000L
    }

    override suspend fun performRoutineCleanup(userId: String): Result<Unit, Exception> {
        val thresholdTime = clock.currentTimeMillis() - RETENTION_WINDOW_MS
        
        val patternCleanupResult = dataManagementRepository.cleanupExpiredPatterns(userId, thresholdTime)
        if (patternCleanupResult is Result.Failure) {
            logger.logLifecycle("DataLifecycle", "CLEANUP_FAILED", mapOf("target" to "patterns", "error" to patternCleanupResult.error.message.toString()))
            return patternCleanupResult
        }
        
        val memoryCleanupResult = dataManagementRepository.cleanupForgottenMemories(userId, thresholdTime)
        if (memoryCleanupResult is Result.Failure) {
            logger.logLifecycle("DataLifecycle", "CLEANUP_FAILED", mapOf("target" to "memories", "error" to memoryCleanupResult.error.message.toString()))
            return memoryCleanupResult
        }

        logger.logLifecycle("DataLifecycle", "CLEANUP_COMPLETED", mapOf("userId" to userId, "thresholdTime" to thresholdTime.toString()))
        return Result.Success(Unit)
    }
}
