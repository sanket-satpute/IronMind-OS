package com.sanket_satpute_20.ironmind.data.repository

import androidx.room.withTransaction
import com.sanket_satpute_20.ironmind.data.local.IronMindDatabase
import com.sanket_satpute_20.ironmind.data.local.dao.CorrectionReviewDao
import com.sanket_satpute_20.ironmind.data.local.entity.toDomainModel
import com.sanket_satpute_20.ironmind.data.local.entity.toEntity
import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.EntitySource
import com.sanket_satpute_20.ironmind.domain.model.Event
import com.sanket_satpute_20.ironmind.domain.model.EventType
import com.sanket_satpute_20.ironmind.domain.model.MemoryConfirmationState
import com.sanket_satpute_20.ironmind.domain.model.barrier.BarrierConfirmationState
import com.sanket_satpute_20.ironmind.domain.model.barrier.BarrierStatus
import com.sanket_satpute_20.ironmind.domain.model.intervention.CausalHypothesisType
import com.sanket_satpute_20.ironmind.domain.model.intervention.CorrectionReviewCandidate
import com.sanket_satpute_20.ironmind.domain.model.intervention.CorrectionReviewDecision
import com.sanket_satpute_20.ironmind.domain.model.intervention.CorrectionReviewItem
import com.sanket_satpute_20.ironmind.domain.model.intervention.CorrectionReviewStatus
import com.sanket_satpute_20.ironmind.domain.repository.BarrierRepository
import com.sanket_satpute_20.ironmind.domain.repository.CorrectionReviewRepository
import com.sanket_satpute_20.ironmind.domain.repository.EventRepository
import com.sanket_satpute_20.ironmind.domain.repository.PatternRepository
import com.sanket_satpute_20.ironmind.domain.usecase.intervention.EvaluateCorrectionReviewDecisionUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.intervention.GetCorrectionReviewCandidatesUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

class CorrectionReviewRepositoryImpl(
    private val eventRepository: EventRepository,
    private val evaluateCorrectionReviewDecisionUseCase: EvaluateCorrectionReviewDecisionUseCase,
    private val correctionReviewDao: CorrectionReviewDao? = null,
    private val database: IronMindDatabase? = null,
    private val patternRepository: PatternRepository? = null,
    private val barrierRepository: BarrierRepository? = null,
    private val getCorrectionReviewCandidatesUseCase: GetCorrectionReviewCandidatesUseCase = GetCorrectionReviewCandidatesUseCase(
        eventRepository = eventRepository,
        evaluateCorrectionReviewDecisionUseCase = evaluateCorrectionReviewDecisionUseCase
    )
) : CorrectionReviewRepository {

    override suspend fun persistCandidate(candidate: CorrectionReviewItem): Result<Unit, Exception> {
        if (candidate.userId.isBlank() || candidate.id.isBlank() || candidate.sourceEventId.isBlank()) {
            return Result.Failure(IllegalArgumentException("candidate userId, id, and sourceEventId must not be blank"))
        }
        val dao = correctionReviewDao ?: return Result.Success(Unit)
        return try {
            dao.insertIgnore(candidate.toEntity())
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }

    override suspend fun getPendingReviews(
        userId: String,
        limit: Int
    ): Result<List<CorrectionReviewItem>, Exception> {
        if (userId.isBlank()) {
            return Result.Failure(IllegalArgumentException("userId must not be blank"))
        }
        val dao = correctionReviewDao
        if (dao != null) {
            return try {
                val entities = dao.getByStatus(userId, CorrectionReviewStatus.PENDING.name, limit)
                Result.Success(entities.map { it.toDomainModel() })
            } catch (e: Exception) {
                Result.Failure(e)
            }
        }

        // Fallback: in-memory projection from events if DAO unconfigured
        val candidatesResult = getReviewCandidates(userId = userId, limit = limit)
        return when (candidatesResult) {
            is Result.Failure -> Result.Failure(candidatesResult.error)
            is Result.Success -> {
                val items = candidatesResult.data.map { candidate ->
                    CorrectionReviewItem(
                        id = "cr_${candidate.userId}_${candidate.eventId}",
                        userId = candidate.userId,
                        sourceEventId = candidate.eventId,
                        recommendationId = candidate.recommendationId,
                        causalHypothesis = candidate.causalHypothesis,
                        correctionScope = candidate.correctionScope,
                        correctedText = candidate.correctedText,
                        decisionState = "DEFERRED_FOR_CONSIDERATION",
                        reviewStatus = CorrectionReviewStatus.PENDING,
                        createdAt = candidate.occurredAt,
                        updatedAt = candidate.occurredAt
                    )
                }
                Result.Success(items)
            }
        }
    }

    override suspend fun getReview(
        userId: String,
        reviewId: String
    ): Result<CorrectionReviewItem?, Exception> {
        if (userId.isBlank() || reviewId.isBlank()) {
            return Result.Failure(IllegalArgumentException("userId and reviewId must not be blank"))
        }
        val dao = correctionReviewDao ?: return Result.Success(null)
        return try {
            val entity = dao.getById(reviewId, userId)
            Result.Success(entity?.toDomainModel())
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }

    override suspend fun getReviewByEventId(
        userId: String,
        eventId: String
    ): Result<CorrectionReviewItem?, Exception> {
        if (userId.isBlank() || eventId.isBlank()) {
            return Result.Failure(IllegalArgumentException("userId and eventId must not be blank"))
        }
        val dao = correctionReviewDao
        if (dao != null) {
            return try {
                val entity = dao.getBySourceEventId(eventId, userId)
                Result.Success(entity?.toDomainModel())
            } catch (e: Exception) {
                Result.Failure(e)
            }
        }
        return Result.Success(null)
    }

    override fun observePendingReviews(userId: String): Flow<List<CorrectionReviewItem>> {
        if (userId.isBlank()) return flowOf(emptyList())
        val dao = correctionReviewDao ?: return flowOf(emptyList())
        return dao.observePendingForUser(userId).map { list ->
            list.map { it.toDomainModel() }
        }
    }

    override suspend fun updateReviewDecision(
        userId: String,
        reviewId: String,
        newStatus: CorrectionReviewStatus,
        feedback: String?,
        reviewedAt: Long
    ): Result<Unit, Exception> {
        if (userId.isBlank() || reviewId.isBlank()) {
            return Result.Failure(IllegalArgumentException("userId and reviewId must not be blank"))
        }
        val dao = correctionReviewDao
            ?: return Result.Failure(IllegalStateException("CorrectionReviewDao is unconfigured"))

        if (database != null) {
            return try {
                database.withTransaction {
                    val existing = dao.getById(reviewId, userId)
                        ?: throw IllegalArgumentException("Correction review not found for user $userId")

                    // Enforce state transition rules
                    if (existing.reviewStatus == newStatus.name) {
                        // Idempotent: repeated identical action is a no-op
                        return@withTransaction
                    }

                    if (existing.reviewStatus != CorrectionReviewStatus.PENDING.name) {
                        throw IllegalStateException("Cannot transition correction review from ${existing.reviewStatus} to ${newStatus.name}")
                    }

                    if (newStatus == CorrectionReviewStatus.APPROVED) {
                        propagateFeedbackToHypothesis(
                            userId = userId,
                            hypothesisId = existing.causalHypothesisId,
                            hypothesisType = existing.causalHypothesisType,
                            reviewedAt = reviewedAt,
                            db = database
                        )
                    }

                    val updatedRows = dao.updateStatus(
                        id = reviewId,
                        userId = userId,
                        newStatus = newStatus.name,
                        feedback = feedback,
                        reviewedAt = reviewedAt,
                        updatedAt = reviewedAt
                    )

                    if (updatedRows <= 0) {
                        throw IllegalStateException("Failed to update correction review: zero rows affected")
                    }
                }
                Result.Success(Unit)
            } catch (e: Exception) {
                Result.Failure(e)
            }
        }

        return try {
            val existing = dao.getById(reviewId, userId)
                ?: return Result.Failure(IllegalArgumentException("Correction review not found for user $userId"))

            // Enforce state transition rules
            if (existing.reviewStatus == newStatus.name) {
                // Idempotent: repeated identical action is a no-op
                return Result.Success(Unit)
            }

            if (existing.reviewStatus != CorrectionReviewStatus.PENDING.name) {
                return Result.Failure(
                    IllegalStateException("Cannot transition correction review from ${existing.reviewStatus} to ${newStatus.name}")
                )
            }

            if (newStatus == CorrectionReviewStatus.APPROVED) {
                propagateFeedbackViaRepositories(
                    userId = userId,
                    hypothesisId = existing.causalHypothesisId,
                    hypothesisType = existing.causalHypothesisType,
                    reviewedAt = reviewedAt
                )
            }

            val updatedRows = dao.updateStatus(
                id = reviewId,
                userId = userId,
                newStatus = newStatus.name,
                feedback = feedback,
                reviewedAt = reviewedAt,
                updatedAt = reviewedAt
            )

            if (updatedRows > 0) {
                Result.Success(Unit)
            } else {
                Result.Failure(IllegalStateException("Failed to update correction review: zero rows affected"))
            }
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }

    private suspend fun propagateFeedbackToHypothesis(
        userId: String,
        hypothesisId: String,
        hypothesisType: String,
        reviewedAt: Long,
        db: IronMindDatabase
    ) {
        when (hypothesisType) {
            CausalHypothesisType.PATTERN.name -> {
                val patternDao = db.patternDao()
                val pattern = patternDao.getPatternById(hypothesisId)
                if (pattern != null) {
                    if (pattern.userId != userId) {
                        throw IllegalStateException("Cross-user pattern hypothesis ownership mismatch: pattern userId ${pattern.userId} != $userId")
                    }
                    when (pattern.confirmationState) {
                        MemoryConfirmationState.USER_CONFIRMED.name -> {
                            // Epistemic safeguard: NEVER demote or decay user-confirmed pattern
                        }
                        MemoryConfirmationState.USER_REJECTED.name -> {
                            // Already rejected; preserve rejected state without reactivation
                        }
                        MemoryConfirmationState.UNCONFIRMED.name,
                        MemoryConfirmationState.SYSTEM_CONFIRMED.name -> {
                            val decayedConfidence = (pattern.confidence * 0.75f).coerceAtLeast(0.1f)
                            patternDao.updatePatternConfidence(
                                id = pattern.id,
                                confidence = decayedConfidence,
                                lastObservedAt = reviewedAt,
                                updatedAt = reviewedAt
                            )
                            val event = Event(
                                id = "evt_${userId}_${reviewedAt}_decay_${pattern.id}",
                                userId = userId,
                                type = EventType.PATTERN_DECAYED,
                                entityType = "Pattern",
                                entityId = pattern.id,
                                occurredAt = reviewedAt,
                                recordedAt = reviewedAt,
                                source = EntitySource.SYSTEM,
                                metadata = """{"reason":"correction_approved","previousConfidence":${pattern.confidence},"newConfidence":$decayedConfidence}"""
                            )
                            db.ironMindDao().insertEvent(event.toEntity())
                        }
                    }
                }
            }
            CausalHypothesisType.BARRIER.name -> {
                val barrierDao = db.barrierDao()
                val barrier = barrierDao.getBarrierById(hypothesisId)
                if (barrier != null) {
                    if (barrier.userId != userId) {
                        throw IllegalStateException("Cross-user barrier hypothesis ownership mismatch: barrier userId ${barrier.userId} != $userId")
                    }
                    when (barrier.confirmationState) {
                        BarrierConfirmationState.CONFIRMED.name -> {
                            // Epistemic safeguard: NEVER demote or decay user-confirmed barrier
                        }
                        BarrierConfirmationState.REJECTED.name,
                        BarrierConfirmationState.DISMISSED.name -> {
                            // Already dismissed/rejected
                        }
                        BarrierConfirmationState.UNCONFIRMED.name -> {
                            barrierDao.updateBarrierState(
                                id = barrier.id,
                                userId = userId,
                                confirmationState = BarrierConfirmationState.DISMISSED.name,
                                status = BarrierStatus.EXPIRED.name,
                                now = reviewedAt
                            )
                            val event = Event(
                                id = "evt_${userId}_${reviewedAt}_dismiss_${barrier.id}",
                                userId = userId,
                                type = EventType.BARRIER_DISMISSED,
                                entityType = "Barrier",
                                entityId = barrier.id,
                                occurredAt = reviewedAt,
                                recordedAt = reviewedAt,
                                source = EntitySource.SYSTEM,
                                metadata = """{"reason":"correction_approved"}"""
                            )
                            db.ironMindDao().insertEvent(event.toEntity())
                        }
                    }
                }
            }
        }
    }

    private suspend fun propagateFeedbackViaRepositories(
        userId: String,
        hypothesisId: String,
        hypothesisType: String,
        reviewedAt: Long
    ) {
        when (hypothesisType) {
            CausalHypothesisType.PATTERN.name -> {
                val repo = patternRepository ?: return
                val patternResult = repo.getPattern(hypothesisId)
                if (patternResult is Result.Success && patternResult.data != null) {
                    val pattern = patternResult.data!!
                    if (pattern.userId != userId) {
                        throw IllegalStateException("Cross-user pattern hypothesis ownership mismatch: pattern userId ${pattern.userId} != $userId")
                    }
                    when (pattern.confirmationState) {
                        MemoryConfirmationState.USER_CONFIRMED,
                        MemoryConfirmationState.USER_REJECTED -> {
                            // Epistemic safeguard: protected
                        }
                        MemoryConfirmationState.UNCONFIRMED,
                        MemoryConfirmationState.SYSTEM_CONFIRMED -> {
                            val decayedConfidence = (pattern.confidence * 0.75f).coerceAtLeast(0.1f)
                            repo.updatePatternConfidence(pattern.id, decayedConfidence, reviewedAt)
                        }
                    }
                }
            }
            CausalHypothesisType.BARRIER.name -> {
                val repo = barrierRepository ?: return
                val barrierResult = repo.getBarrier(hypothesisId)
                if (barrierResult is Result.Success && barrierResult.data != null) {
                    val barrier = barrierResult.data!!
                    if (barrier.userId != userId) {
                        throw IllegalStateException("Cross-user barrier hypothesis ownership mismatch: barrier userId ${barrier.userId} != $userId")
                    }
                    when (barrier.confirmationState) {
                        BarrierConfirmationState.CONFIRMED,
                        BarrierConfirmationState.REJECTED,
                        BarrierConfirmationState.DISMISSED -> {
                            // Epistemic safeguard: protected
                        }
                        BarrierConfirmationState.UNCONFIRMED -> {
                            repo.updateBarrierState(
                                id = barrier.id,
                                userId = userId,
                                confirmationState = BarrierConfirmationState.DISMISSED,
                                status = BarrierStatus.EXPIRED,
                                now = reviewedAt
                            )
                        }
                    }
                }
            }
        }
    }

    override suspend fun deleteAllForUser(userId: String): Result<Unit, Exception> {
        if (userId.isBlank()) {
            return Result.Failure(IllegalArgumentException("userId must not be blank"))
        }
        val dao = correctionReviewDao ?: return Result.Success(Unit)
        return try {
            dao.deleteAllForUser(userId)
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }

    // ── Existing Projection Implementations ─────────────────────────────────

    override suspend fun getReviewCandidates(
        userId: String,
        startTime: Long,
        endTime: Long,
        limit: Int
    ): Result<List<CorrectionReviewCandidate>, Exception> {
        if (userId.isBlank()) {
            return Result.Failure(IllegalArgumentException("userId must not be blank"))
        }
        return getCorrectionReviewCandidatesUseCase(userId, startTime, endTime, limit)
    }

    override suspend fun getReviewCandidate(
        userId: String,
        eventId: String
    ): Result<CorrectionReviewCandidate?, Exception> {
        if (userId.isBlank() || eventId.isBlank()) {
            return Result.Failure(IllegalArgumentException("userId and eventId must not be blank"))
        }

        val eventResult = eventRepository.getEvent(eventId)
        if (eventResult is Result.Failure) {
            return Result.Failure(eventResult.error)
        }
        val event = (eventResult as Result.Success).data
            ?: return Result.Success(null)

        if (event.userId != userId) {
            return Result.Success(null) // User isolation
        }

        val decision = evaluateCorrectionReviewDecisionUseCase.evaluate(
            userId = userId,
            eventId = eventId,
            recommendationId = event.causationId
        )

        return when (decision) {
            is CorrectionReviewDecision.DeferredForConsideration -> {
                Result.Success(
                    CorrectionReviewCandidate(
                        userId = decision.userId,
                        eventId = decision.eventId,
                        recommendationId = decision.recommendationId,
                        causalHypothesis = decision.causalHypothesis,
                        correctionScope = decision.correctionScope,
                        correctedText = decision.correctedText,
                        occurredAt = event.occurredAt,
                        decision = decision
                    )
                )
            }
            is CorrectionReviewDecision.NotReviewable -> {
                Result.Success(null)
            }
        }
    }

    override suspend fun verifyCandidatePersisted(
        userId: String,
        eventId: String
    ): Result<Unit, Exception> {
        val dao = correctionReviewDao
        if (dao != null) {
            return try {
                val record = dao.getBySourceEventId(eventId, userId)
                if (record != null) {
                    Result.Success(Unit)
                } else {
                    Result.Failure(IllegalStateException("Candidate for event $eventId is not durably recorded in Room"))
                }
            } catch (e: Exception) {
                Result.Failure(e)
            }
        }

        // Fallback for tests/unconfigured DAO
        val candidateResult = getReviewCandidate(userId, eventId)
        return when (candidateResult) {
            is Result.Failure -> Result.Failure(candidateResult.error)
            is Result.Success -> {
                if (candidateResult.data != null) {
                    Result.Success(Unit)
                } else {
                    Result.Failure(IllegalStateException("Candidate for event $eventId is not durably recorded or not eligible"))
                }
            }
        }
    }
}
