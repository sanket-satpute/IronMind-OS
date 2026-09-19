package com.sanket_satpute_20.ironmind.domain.engine

import com.sanket_satpute_20.ironmind.domain.common.Clock
import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.pattern.PatternStatus
import com.sanket_satpute_20.ironmind.domain.repository.PatternRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class PatternEngineImpl(
    private val clock: Clock,
    private val patternRepository: PatternRepository
) : PatternEngine {

    companion object {
        // e.g., patterns decay if not observed for 30 days
        private const val DECAY_THRESHOLD_MS = 30L * 24L * 60L * 60L * 1000L
        private const val DECAY_FACTOR = 0.5f
        private const val EXPIRATION_CONFIDENCE_THRESHOLD = 0.1f
    }

    override suspend fun decayStalePatterns(userId: String): Result<Unit, Exception> = withContext(Dispatchers.IO) {
        try {
            val now = clock.currentTimeMillis()
            val result = patternRepository.getPatternsByStatus(userId, PatternStatus.ACTIVE)
            if (result is Result.Success) {
                val patterns = result.data
                for (pattern in patterns) {
                    val timeSinceLastObserved = now - pattern.lastObservedAt
                    if (timeSinceLastObserved > DECAY_THRESHOLD_MS) {
                        val newConfidence = pattern.confidence * DECAY_FACTOR
                        if (newConfidence < EXPIRATION_CONFIDENCE_THRESHOLD) {
                            val updatedPattern = pattern.copy(
                                confidence = newConfidence,
                                status = PatternStatus.EXPIRED,
                                updatedAt = now
                            )
                            patternRepository.savePattern(updatedPattern)
                            println("IronMindLifecycle [PatternEngine] [PATTERN_EXPIRED] patternId=\${pattern.id}")
                        } else {
                            patternRepository.updatePatternConfidence(pattern.id, newConfidence, pattern.lastObservedAt)
                            println("IronMindLifecycle [PatternEngine] [PATTERN_DECAYED] patternId=\${pattern.id} confidence=\$newConfidence")
                        }
                    }
                }
            }
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }
}
