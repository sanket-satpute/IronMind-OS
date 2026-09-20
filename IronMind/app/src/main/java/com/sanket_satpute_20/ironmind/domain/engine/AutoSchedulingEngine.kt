package com.sanket_satpute_20.ironmind.domain.engine

import com.sanket_satpute_20.ironmind.domain.model.scheduling.ScheduleCandidate
import com.sanket_satpute_20.ironmind.domain.model.scheduling.ScheduleResult

interface AutoSchedulingEngine {
    suspend fun schedule(userId: String, candidate: ScheduleCandidate): ScheduleResult
}
