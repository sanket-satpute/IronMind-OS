package com.sanket_satpute_20.ironmind.domain.usecase.pattern

import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.engine.PatternAcceptanceEvaluator
import com.sanket_satpute_20.ironmind.domain.engine.PatternAcceptanceResult
import com.sanket_satpute_20.ironmind.domain.engine.ValidatedEvidence
import com.sanket_satpute_20.ironmind.domain.model.pattern.PatternCandidate
import com.sanket_satpute_20.ironmind.domain.repository.PatternRepository
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.ZoneId
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class PatternAcceptanceUseCase(
    private val evaluator: PatternAcceptanceEvaluator,
    private val patternRepository: PatternRepository,
    private val idGenerator: () -> String = { UUID.randomUUID().toString() }
) {
    // Application-level serialization to prevent data-loss race conditions during evaluate->persist
    private val userMutexes = ConcurrentHashMap<String, Mutex>()

    suspend operator fun invoke(
        userId: String,
        candidate: PatternCandidate,
        evidence: List<ValidatedEvidence>,
        currentTimeMillis: Long,
        zoneId: ZoneId
    ): Result<PatternAcceptanceResult, Exception> {
        val mutex = userMutexes.getOrPut(userId) { Mutex() }
        
        return mutex.withLock {
            try {
                // Identity fingerprint canonicalization
                val normalizedDescription = candidate.description.trim().lowercase()
                val fingerprint = "${userId}_${candidate.type.name}_${normalizedDescription}"

                // Fetch existing pattern, enforcing domain ownership by passing userId
                val existingPatternResult = patternRepository.getPatternByFingerprint(userId, fingerprint)
                val existingPattern = if (existingPatternResult is Result.Success) existingPatternResult.data else null

                // Evaluate (Pure Domain Logic)
                val newPatternId = idGenerator()
                val result = evaluator.evaluate(
                    candidate = candidate,
                    evidence = evidence,
                    existingPattern = existingPattern,
                    newPatternId = newPatternId,
                    currentTimeMillis = currentTimeMillis,
                    zoneId = zoneId,
                    userId = userId
                )

                // Persist Deterministic Result
                when (result) {
                    is PatternAcceptanceResult.AcceptedNew -> {
                        val saveResult = patternRepository.savePattern(result.pattern)
                        if (saveResult is Result.Failure) {
                            return@withLock Result.Failure(saveResult.error)
                        }
                    }
                    is PatternAcceptanceResult.UpdatedExisting -> {
                        val updateResult = patternRepository.updatePattern(result.pattern)
                        if (updateResult is Result.Failure) {
                            return@withLock Result.Failure(updateResult.error)
                        }
                    }
                    is PatternAcceptanceResult.NoOp -> {
                        // Do nothing
                    }
                    is PatternAcceptanceResult.Rejected -> {
                        // Evaluation rejected the candidate, no persistence needed
                    }
                }

                // Log Result
                println("IronMindLifecycle [PatternAcceptance] [EVALUATED] result=${result.javaClass.simpleName} type=${candidate.type.name}")
                
                Result.Success(result)
            } catch (e: Exception) {
                Result.Failure(e)
            }
        }
    }
}
