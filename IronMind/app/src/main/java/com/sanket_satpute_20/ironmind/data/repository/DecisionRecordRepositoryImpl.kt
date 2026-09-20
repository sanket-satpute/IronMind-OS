package com.sanket_satpute_20.ironmind.data.repository

import com.sanket_satpute_20.ironmind.data.local.dao.DecisionRecordDao
import com.sanket_satpute_20.ironmind.data.local.entity.DecisionRecordEntity
import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.AutonomyCapability
import com.sanket_satpute_20.ironmind.domain.model.AutonomyLevel
import com.sanket_satpute_20.ironmind.domain.model.decision.DecisionRecord
import com.sanket_satpute_20.ironmind.domain.model.decision.DecisionResult
import com.sanket_satpute_20.ironmind.domain.repository.DecisionRecordRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class DecisionRecordRepositoryImpl(
    private val dao: DecisionRecordDao
) : DecisionRecordRepository {

    override suspend fun saveDecision(decision: DecisionRecord): Result<Unit, Exception> = withContext(Dispatchers.IO) {
        try {
            dao.insert(decision.toEntity())
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }

    override fun getDecisions(userId: String): Flow<List<DecisionRecord>> {
        return dao.getAll().map { list -> list.map { it.toDomain() } }
    }

    override fun getDecisionsByCapability(userId: String, capability: String): Flow<List<DecisionRecord>> {
        return dao.getByCapability(capability).map { list -> list.map { it.toDomain() } }
    }
}

fun DecisionRecord.toEntity(): DecisionRecordEntity = DecisionRecordEntity(
    id = id,
    timestamp = timestamp,
    capability = capability.name,
    action = action,
    trigger = trigger,
    source = source,
    contextSummary = contextSummary,
    policy = policy,
    autonomyLevel = autonomyLevel.name,
    reasoning = reasoning,
    confidence = confidence,
    result = result.name,
    failureReason = failureReason,
    userResponse = userResponse
)

fun DecisionRecordEntity.toDomain(): DecisionRecord = DecisionRecord(
    id = id,
    timestamp = timestamp,
    capability = try { AutonomyCapability.valueOf(capability) } catch (e: Exception) { AutonomyCapability.PLANNING },
    action = action,
    trigger = trigger,
    source = source,
    contextSummary = contextSummary,
    policy = policy,
    autonomyLevel = try { AutonomyLevel.valueOf(autonomyLevel) } catch (e: Exception) { AutonomyLevel.OFF },
    reasoning = reasoning,
    confidence = confidence,
    result = try { DecisionResult.valueOf(result) } catch (e: Exception) { DecisionResult.STAY_SILENT },
    failureReason = failureReason,
    userResponse = userResponse
)
