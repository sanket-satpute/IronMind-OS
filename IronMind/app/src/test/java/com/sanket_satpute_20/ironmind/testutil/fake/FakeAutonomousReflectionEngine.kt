package com.sanket_satpute_20.ironmind.testutil.fake

import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.engine.AutonomousReflectionEngine
import com.sanket_satpute_20.ironmind.domain.model.pattern.PatternCandidate

class FakeAutonomousReflectionEngine : AutonomousReflectionEngine {
    var shouldFail = false
    var candidatesToReturn: List<PatternCandidate> = emptyList()

    override suspend fun processReflection(reflectionId: String): Result<List<PatternCandidate>, Exception> {
        if (shouldFail) {
            return Result.Failure(Exception("Fake engine failure"))
        }
        return Result.Success(candidatesToReturn)
    }
}
