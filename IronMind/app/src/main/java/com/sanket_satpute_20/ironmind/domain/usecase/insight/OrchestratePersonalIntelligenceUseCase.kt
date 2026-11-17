package com.sanket_satpute_20.ironmind.domain.usecase.insight

import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.engine.PersonalIntelligenceOrchestrator
import com.sanket_satpute_20.ironmind.domain.engine.PersonalIntelligenceResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.ZoneId

class OrchestratePersonalIntelligenceUseCase(
    private val orchestrator: PersonalIntelligenceOrchestrator
) {
    suspend operator fun invoke(
        userId: String,
        windowStartTimeMs: Long,
        windowEndTimeMs: Long,
        zoneId: ZoneId
    ): Result<PersonalIntelligenceResult, Exception> = withContext(Dispatchers.IO) {
        orchestrator.orchestrate(
            userId = userId,
            windowStartTimeMs = windowStartTimeMs,
            windowEndTimeMs = windowEndTimeMs,
            zoneId = zoneId
        )
    }
}
