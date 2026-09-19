package com.sanket_satpute_20.ironmind.domain.repository

import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.Outcome

interface OutcomeRepository : Repository {
    suspend fun saveOutcome(outcome: Outcome): Result<Unit, Exception>
    suspend fun getOutcomeForSource(sourceEntityId: String): Result<Outcome?, Exception>
}
