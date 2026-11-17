package com.sanket_satpute_20.ironmind.data.repository

import com.sanket_satpute_20.ironmind.data.local.dao.IronMindDao
import com.sanket_satpute_20.ironmind.data.local.entity.MemoryEntity
import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.EntitySource
import com.sanket_satpute_20.ironmind.domain.model.MemoryConfirmationState
import com.sanket_satpute_20.ironmind.domain.model.MemoryStatus
import com.sanket_satpute_20.ironmind.domain.model.context.EpistemicAuthority
import com.sanket_satpute_20.ironmind.domain.model.insight.InsightJsonCodec
import com.sanket_satpute_20.ironmind.domain.model.insight.InsightReviewStatus
import com.sanket_satpute_20.ironmind.domain.model.insight.PersonalInsight
import com.sanket_satpute_20.ironmind.domain.repository.InsightRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * Phase 3.4: Implementation of InsightRepository utilizing existing Room MemoryEntity table.
 * Guarantees zero Room migrations while providing full offline persistence, real-time observation flows,
 * and crash-safe JSON serialization.
 */
class InsightRepositoryImpl(
    private val dao: IronMindDao
) : InsightRepository {

    companion object {
        const val MEMORY_TYPE_PERSONAL_INSIGHT = "PERSONAL_INSIGHT"
    }

    override suspend fun saveInsight(insight: PersonalInsight): Result<PersonalInsight, Exception> = withContext(Dispatchers.IO) {
        try {
            require(insight.userId.isNotBlank()) { "userId must not be blank" }
            require(insight.id.isNotBlank()) { "insight id must not be blank" }

            val jsonPayload = InsightJsonCodec.encodeToJson(insight)

            val memoryEntity = MemoryEntity(
                id = insight.id,
                userId = insight.userId,
                type = MEMORY_TYPE_PERSONAL_INSIGHT,
                content = jsonPayload,
                source = EntitySource.AI.name,
                confidence = insight.confidence,
                evidenceCount = insight.supportingEvidenceIds.size,
                firstObservedAt = insight.firstObservedAt,
                lastObservedAt = insight.lastObservedAt,
                confirmationState = insight.confirmationState.name,
                status = MemoryStatus.ACTIVE.name,
                expiresAt = null,
                createdAt = insight.createdAt,
                updatedAt = insight.updatedAt,
                schemaVersion = 1
            )

            dao.insertMemory(memoryEntity)
            Result.Success(insight)
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }

    override suspend fun getInsightById(id: String): Result<PersonalInsight?, Exception> = withContext(Dispatchers.IO) {
        try {
            val entity = dao.getMemoryById(id)
            if (entity == null || entity.type != MEMORY_TYPE_PERSONAL_INSIGHT) {
                return@withContext Result.Success(null)
            }
            val insight = InsightJsonCodec.decodeFromJson(
                id = entity.id,
                userId = entity.userId,
                jsonString = entity.content,
                fallbackCreatedAt = entity.createdAt
            )
            Result.Success(insight)
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }

    override suspend fun getInsightsForUser(userId: String): Result<List<PersonalInsight>, Exception> = withContext(Dispatchers.IO) {
        try {
            val entities = dao.getMemoriesForUser(userId)
            val insights = entities
                .filter { it.type == MEMORY_TYPE_PERSONAL_INSIGHT }
                .mapNotNull {
                    InsightJsonCodec.decodeFromJson(
                        id = it.id,
                        userId = it.userId,
                        jsonString = it.content,
                        fallbackCreatedAt = it.createdAt
                    )
                }
            Result.Success(insights)
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }

    override fun observeInsightsForUser(userId: String): Flow<List<PersonalInsight>> {
        return dao.observeMemoriesForUser(userId)
            .map { entities ->
                entities
                    .filter { it.type == MEMORY_TYPE_PERSONAL_INSIGHT }
                    .mapNotNull {
                        InsightJsonCodec.decodeFromJson(
                            id = it.id,
                            userId = it.userId,
                            jsonString = it.content,
                            fallbackCreatedAt = it.createdAt
                        )
                    }
            }
            .catch { emit(emptyList()) }
    }

    override suspend fun updateReviewStatus(
        id: String,
        userId: String,
        status: InsightReviewStatus,
        feedback: String?,
        reviewedAt: Long
    ): Result<Unit, Exception> = withContext(Dispatchers.IO) {
        try {
            val entity = dao.getMemoryById(id)
                ?: return@withContext Result.Failure(IllegalArgumentException("Insight not found with id: $id"))

            if (entity.userId != userId) {
                return@withContext Result.Failure(SecurityException("User $userId is not owner of insight $id"))
            }

            val currentInsight = InsightJsonCodec.decodeFromJson(
                id = entity.id,
                userId = entity.userId,
                jsonString = entity.content,
                fallbackCreatedAt = entity.createdAt
            ) ?: return@withContext Result.Failure(IllegalStateException("Failed to parse insight payload for id: $id"))

            val updatedInsight = currentInsight.copy(
                reviewStatus = status,
                userFeedback = feedback ?: currentInsight.userFeedback,
                reviewedAt = reviewedAt,
                updatedAt = reviewedAt
            )

            saveInsight(updatedInsight)
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }

    override suspend fun updateFactualConfirmation(
        id: String,
        userId: String,
        confirmationState: MemoryConfirmationState,
        correction: String?,
        timestamp: Long
    ): Result<Unit, Exception> = withContext(Dispatchers.IO) {
        try {
            val entity = dao.getMemoryById(id)
                ?: return@withContext Result.Failure(IllegalArgumentException("Insight not found with id: $id"))

            if (entity.userId != userId) {
                return@withContext Result.Failure(SecurityException("User $userId is not owner of insight $id"))
            }

            val currentInsight = InsightJsonCodec.decodeFromJson(
                id = entity.id,
                userId = entity.userId,
                jsonString = entity.content,
                fallbackCreatedAt = entity.createdAt
            ) ?: return@withContext Result.Failure(IllegalStateException("Failed to parse insight payload for id: $id"))

            val newAuthority = if (confirmationState == MemoryConfirmationState.USER_CONFIRMED) {
                EpistemicAuthority.USER_CONFIRMED_FACT
            } else {
                currentInsight.epistemicAuthority
            }

            val updatedInsight = currentInsight.copy(
                confirmationState = confirmationState,
                epistemicAuthority = newAuthority,
                userFeedback = correction ?: currentInsight.userFeedback,
                updatedAt = timestamp
            )

            saveInsight(updatedInsight)
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }
}
