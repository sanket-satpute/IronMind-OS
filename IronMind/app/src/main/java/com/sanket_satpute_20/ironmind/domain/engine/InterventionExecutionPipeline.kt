package com.sanket_satpute_20.ironmind.domain.engine

import com.sanket_satpute_20.ironmind.domain.ai.AIOutput
import com.sanket_satpute_20.ironmind.domain.common.Clock
import com.sanket_satpute_20.ironmind.domain.common.IdGenerator
import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.intervention.InterventionRecord
import com.sanket_satpute_20.ironmind.domain.model.intervention.InterventionResolutionReason
import com.sanket_satpute_20.ironmind.domain.model.intervention.InterventionState

interface InterventionExecutionPipeline {
    suspend fun propose(
        userId: String,
        candidate: AIOutput.InterventionRecommendation
    ): Result<InterventionRecord, Exception>

    suspend fun trigger(
        id: String
    ): Result<InterventionRecord, Exception>

    suspend fun deliver(
        id: String
    ): Result<InterventionRecord, Exception>

    suspend fun resolve(
        id: String,
        reason: InterventionResolutionReason
    ): Result<InterventionRecord, Exception>
}
