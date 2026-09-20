package com.sanket_satpute_20.ironmind.testutil.fake

import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.decision.DecisionRecord
import com.sanket_satpute_20.ironmind.domain.repository.DecisionRecordRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

class FakeDecisionRecordRepository : DecisionRecordRepository {
    val savedRecords = mutableListOf<DecisionRecord>()

    override suspend fun saveDecision(decision: DecisionRecord): Result<Unit, Exception> {
        savedRecords.add(decision)
        return Result.Success(Unit)
    }

    override fun getDecisions(userId: String): Flow<List<DecisionRecord>> {
        return flowOf(savedRecords)
    }

    override fun getDecisionsByCapability(
        userId: String,
        capability: String
    ): Flow<List<DecisionRecord>> {
        return flowOf(savedRecords.filter { it.capability.name == capability })
    }
}
