package com.sanket_satpute_20.ironmind.domain.repository

import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.barrier.BarrierHypothesis

/**
 * Sprint V2.9: Repository for accessing and persisting BarrierHypothesis entities.
 */
interface BarrierRepository {
    suspend fun saveBarrier(barrier: BarrierHypothesis): Result<Unit, Exception>
    suspend fun getBarriersForUser(userId: String): Result<List<BarrierHypothesis>, Exception>
    suspend fun getActiveBarriersForUser(userId: String, excludedStates: List<com.sanket_satpute_20.ironmind.domain.model.barrier.BarrierConfirmationState>, limit: Int): Result<List<BarrierHypothesis>, Exception>
}
