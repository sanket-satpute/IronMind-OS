package com.sanket_satpute_20.ironmind.data.repository

import androidx.room.withTransaction
import com.sanket_satpute_20.ironmind.data.local.IronMindDatabase
import com.sanket_satpute_20.ironmind.data.local.dao.BarrierDao
import com.sanket_satpute_20.ironmind.data.local.dao.IronMindDao
import com.sanket_satpute_20.ironmind.data.local.entity.BarrierHypothesisEntity
import com.sanket_satpute_20.ironmind.data.local.entity.toEntity
import com.sanket_satpute_20.ironmind.domain.ai.BarrierCategory
import com.sanket_satpute_20.ironmind.domain.common.IntelligenceMetadataCodec
import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.Event
import com.sanket_satpute_20.ironmind.domain.model.barrier.BarrierConfirmationState
import com.sanket_satpute_20.ironmind.domain.model.barrier.BarrierHypothesis
import com.sanket_satpute_20.ironmind.domain.model.barrier.BarrierStatus
import com.sanket_satpute_20.ironmind.domain.repository.BarrierRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class BarrierRepositoryImpl(
    private val barrierDao: BarrierDao,
    private val database: IronMindDatabase? = null,
    private val eventDao: IronMindDao? = null
) : BarrierRepository {

    override suspend fun saveBarrier(barrier: BarrierHypothesis): Result<Unit, Exception> = withContext(Dispatchers.IO) {
        try {
            barrierDao.insert(barrier.toEntity())
            Result.Success(Unit)
        } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) {
            Result.Failure(e)
        }
    }

    override suspend fun getBarriersForUser(userId: String): Result<List<BarrierHypothesis>, Exception> = withContext(Dispatchers.IO) {
        try {
            val entities = barrierDao.getBarriersByUserId(userId)
            val hypotheses = entities.map { it.toDomain() }
            Result.Success(hypotheses)
        } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) {
            Result.Failure(e)
        }
    }

    override suspend fun getActiveBarriersForUser(userId: String, excludedStates: List<BarrierConfirmationState>, limit: Int): Result<List<BarrierHypothesis>, Exception> = withContext(Dispatchers.IO) {
        try {
            val entities = barrierDao.getActiveBarriersForUser(userId, excludedStates.map { it.name }, limit)
            val hypotheses = entities.map { it.toDomain() }
            Result.Success(hypotheses)
        } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) {
            Result.Failure(e)
        }
    }

    override fun observeBarriersForUser(userId: String): Flow<List<BarrierHypothesis>> {
        return barrierDao.observeBarriersForUser(userId).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun updateBarrierState(
        id: String,
        userId: String,
        confirmationState: BarrierConfirmationState,
        status: BarrierStatus,
        now: Long
    ): Result<Unit, Exception> = updateBarrierStateWithEvent(id, userId, confirmationState, status, now, null)

    override suspend fun updateBarrierStateWithEvent(
        id: String,
        userId: String,
        confirmationState: BarrierConfirmationState,
        status: BarrierStatus,
        now: Long,
        event: Event?
    ): Result<Unit, Exception> = withContext(Dispatchers.IO) {
        try {
            if (database != null) {
                database.withTransaction {
                    val existing = barrierDao.getBarrierById(id)
                        ?: throw IllegalArgumentException("Barrier not found: $id")
                    if (existing.userId != userId) {
                        throw IllegalStateException("Barrier $id does not belong to user $userId")
                    }
                    if (existing.confirmationState == confirmationState.name && existing.status == status.name) {
                        return@withTransaction
                    }
                    barrierDao.updateBarrierState(id, userId, confirmationState.name, status.name, now)
                    if (event != null) {
                        val targetEventDao = eventDao ?: database.ironMindDao()
                        targetEventDao.insertEvent(event.toEntity())
                    }
                }
            } else {
                val existing = barrierDao.getBarrierById(id)
                    ?: return@withContext Result.Failure(IllegalArgumentException("Barrier not found: $id"))
                if (existing.userId != userId) {
                    return@withContext Result.Failure(IllegalStateException("Barrier $id does not belong to user $userId"))
                }
                if (existing.confirmationState == confirmationState.name && existing.status == status.name) {
                    return@withContext Result.Success(Unit)
                }
                barrierDao.updateBarrierState(id, userId, confirmationState.name, status.name, now)
                if (event != null && eventDao != null) {
                    eventDao.insertEvent(event.toEntity())
                }
            }
            Result.Success(Unit)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }

    override suspend fun getBarrier(id: String): Result<BarrierHypothesis?, Exception> = withContext(Dispatchers.IO) {
        try {
            val entity = barrierDao.getBarrierById(id)
            val hypothesis = entity?.toDomain()
            Result.Success(hypothesis)
        } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) {
            Result.Failure(e)
        }
    }

    private fun BarrierHypothesis.toEntity(): BarrierHypothesisEntity {
        val encodedSource = IntelligenceMetadataCodec.encodeBarrierMetadata(this)
        return BarrierHypothesisEntity(
            id = id,
            userId = userId,
            category = category.name,
            description = description,
            confirmationState = confirmationState.name,
            status = status.name,
            sourceReflectionId = encodedSource,
            firstObservedAt = firstObservedAt,
            lastObservedAt = lastObservedAt
        )
    }

    private fun BarrierHypothesisEntity.toDomain(): BarrierHypothesis {
        val meta = IntelligenceMetadataCodec.decodeBarrierMetadata(sourceReflectionId, confirmationState)
        return BarrierHypothesis(
            id = id,
            userId = userId,
            category = BarrierCategory.valueOf(category),
            description = description,
            confirmationState = BarrierConfirmationState.valueOf(confirmationState),
            status = BarrierStatus.valueOf(status),
            sourceReflectionId = meta.originalRef,
            firstObservedAt = firstObservedAt,
            lastObservedAt = lastObservedAt,
            title = meta.title,
            supportingPatternIds = meta.supportingPatternIds,
            evidenceReferences = emptyList(),
            evidenceCount = meta.evidenceCount,
            distinctDays = meta.distinctDays,
            confidence = meta.confidence,
            epistemicAuthority = meta.epistemicAuthority,
            sourceProvenance = meta.sourceProvenance,
            sourceFreshness = meta.sourceFreshness
        )
    }
}
