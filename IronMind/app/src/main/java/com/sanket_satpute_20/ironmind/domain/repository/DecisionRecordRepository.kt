package com.sanket_satpute_20.ironmind.domain.repository

import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.decision.DecisionRecord
import kotlinx.coroutines.flow.Flow

interface DecisionRecordRepository {
    suspend fun saveDecision(decision: DecisionRecord): Result<Unit, Exception>
    fun getDecisions(userId: String): Flow<List<DecisionRecord>>
    fun getDecisionsByCapability(userId: String, capability: String): Flow<List<DecisionRecord>>
}
