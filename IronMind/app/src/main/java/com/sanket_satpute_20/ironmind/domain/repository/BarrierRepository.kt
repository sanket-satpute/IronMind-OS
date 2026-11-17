package com.sanket_satpute_20.ironmind.domain.repository

import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.barrier.BarrierHypothesis

/**
 * Sprint V2.9: Repository for accessing and persisting BarrierHypothesis entities.
 */
interface BarrierRepository {
    suspend fun saveBarrier(barrier: BarrierHypothesis): Result<Unit, Exception>
    suspend fun getBarriersForUser(userId: String): Result<List<BarrierHypothesis>, Exception>
    fun observeBarriersForUser(userId: String): kotlinx.coroutines.flow.Flow<List<BarrierHypothesis>> = kotlinx.coroutines.flow.emptyFlow()
    suspend fun getActiveBarriersForUser(userId: String, excludedStates: List<com.sanket_satpute_20.ironmind.domain.model.barrier.BarrierConfirmationState>, limit: Int): Result<List<BarrierHypothesis>, Exception>
    suspend fun getBarrier(id: String): Result<BarrierHypothesis?, Exception>
    suspend fun updateBarrierState(
        id: String,
        userId: String,
        confirmationState: com.sanket_satpute_20.ironmind.domain.model.barrier.BarrierConfirmationState,
        status: com.sanket_satpute_20.ironmind.domain.model.barrier.BarrierStatus,
        now: Long
    ): Result<Unit, Exception> = Result.Success(Unit)

    suspend fun updateBarrierStateWithEvent(
        id: String,
        userId: String,
        confirmationState: com.sanket_satpute_20.ironmind.domain.model.barrier.BarrierConfirmationState,
        status: com.sanket_satpute_20.ironmind.domain.model.barrier.BarrierStatus,
        now: Long,
        event: com.sanket_satpute_20.ironmind.domain.model.Event? = null
    ): Result<Unit, Exception> = updateBarrierState(id, userId, confirmationState, status, now)
}
