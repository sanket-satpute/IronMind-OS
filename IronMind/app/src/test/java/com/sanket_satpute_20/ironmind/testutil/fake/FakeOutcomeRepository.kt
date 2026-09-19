package com.sanket_satpute_20.ironmind.testutil.fake

import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.Outcome
import com.sanket_satpute_20.ironmind.domain.repository.OutcomeRepository

class FakeOutcomeRepository : OutcomeRepository {
    
    val outcomes = mutableMapOf<String, Outcome>()
    var shouldFail = false

    override suspend fun saveOutcome(outcome: Outcome): Result<Unit, Exception> {
        if (shouldFail) return Result.Failure(Exception("Fake failure"))
        outcomes[outcome.id] = outcome
        return Result.Success(Unit)
    }

    override suspend fun getOutcomeForSource(sourceEntityId: String): Result<Outcome?, Exception> {
        if (shouldFail) return Result.Failure(Exception("Fake failure"))
        return Result.Success(outcomes.values.find { it.sourceEntityId == sourceEntityId })
    }
}
