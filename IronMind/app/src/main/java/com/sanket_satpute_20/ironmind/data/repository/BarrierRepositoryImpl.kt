package com.sanket_satpute_20.ironmind.data.repository

import com.sanket_satpute_20.ironmind.data.local.dao.BarrierDao
import com.sanket_satpute_20.ironmind.data.local.entity.BarrierHypothesisEntity
import com.sanket_satpute_20.ironmind.domain.ai.BarrierCategory
import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.barrier.BarrierConfirmationState
import com.sanket_satpute_20.ironmind.domain.model.barrier.BarrierHypothesis
import com.sanket_satpute_20.ironmind.domain.model.barrier.BarrierStatus
import com.sanket_satpute_20.ironmind.domain.repository.BarrierRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class BarrierRepositoryImpl(
    private val barrierDao: BarrierDao
) : BarrierRepository {

    override suspend fun saveBarrier(barrier: BarrierHypothesis): Result<Unit, Exception> = withContext(Dispatchers.IO) {
        try {
            val entity = BarrierHypothesisEntity(
                id = barrier.id,
                userId = barrier.userId,
                category = barrier.category.name,
                description = barrier.description,
                confirmationState = barrier.confirmationState.name,
                status = barrier.status.name,
                sourceReflectionId = barrier.sourceReflectionId,
                firstObservedAt = barrier.firstObservedAt,
                lastObservedAt = barrier.lastObservedAt
            )
            barrierDao.insert(entity)
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }

    override suspend fun getBarriersForUser(userId: String): Result<List<BarrierHypothesis>, Exception> = withContext(Dispatchers.IO) {
        try {
            val entities = barrierDao.getBarriersByUserId(userId)
            val hypotheses = entities.map {
                BarrierHypothesis(
                    id = it.id,
                    userId = it.userId,
                    category = BarrierCategory.valueOf(it.category),
                    description = it.description,
                    confirmationState = BarrierConfirmationState.valueOf(it.confirmationState),
                    status = BarrierStatus.valueOf(it.status),
                    sourceReflectionId = it.sourceReflectionId,
                    firstObservedAt = it.firstObservedAt,
                    lastObservedAt = it.lastObservedAt
                )
            }
            Result.Success(hypotheses)
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }
}
