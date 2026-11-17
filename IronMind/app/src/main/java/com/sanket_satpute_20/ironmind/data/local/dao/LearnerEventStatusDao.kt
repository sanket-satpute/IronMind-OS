package com.sanket_satpute_20.ironmind.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.sanket_satpute_20.ironmind.data.local.entity.LearnerEventStatusEntity

@Dao
interface LearnerEventStatusDao {

    @Query("SELECT * FROM learner_event_status WHERE eventId = :eventId AND userId = :userId LIMIT 1")
    suspend fun getEventStatusForUser(eventId: String, userId: String): LearnerEventStatusEntity?

    @Query("""
        UPDATE learner_event_status
        SET status = 'PROCESSING', leaseToken = :newLeaseToken, leaseExpiresAt = :leaseExpiresAt,
            claimCount = claimCount + 1, lastAttemptAt = :now, updatedAt = :now
        WHERE eventId = :eventId AND userId = :userId
          AND status IN ('PENDING', 'FAILED_RETRYABLE')
          AND nextAttemptAt <= :now AND claimCount < maxAttempts
    """)
    suspend fun claimPendingEventForUser(
        eventId: String, userId: String, now: Long, leaseExpiresAt: Long, newLeaseToken: String
    ): Int

    @Query("""
        UPDATE learner_event_status
        SET status = 'PROCESSING', leaseToken = :newLeaseToken, leaseExpiresAt = :leaseExpiresAt,
            claimCount = claimCount + 1, lastAttemptAt = :now, updatedAt = :now
        WHERE eventId = :eventId AND userId = :userId
          AND status = 'PROCESSING' AND leaseExpiresAt < :now AND claimCount < maxAttempts
    """)
    suspend fun claimExpiredProcessingEventForUser(
        eventId: String, userId: String, now: Long, leaseExpiresAt: Long, newLeaseToken: String
    ): Int

    @Query("""
        UPDATE learner_event_status
        SET status = 'PENDING', leaseToken = NULL, leaseExpiresAt = NULL, updatedAt = :now
        WHERE eventId = :eventId AND userId = :userId
          AND leaseToken = :myLeaseToken AND status = 'PROCESSING'
    """)
    suspend fun releaseLeaseForUser(eventId: String, userId: String, myLeaseToken: String, now: Long): Int

    @Query("""
        UPDATE learner_event_status
        SET status = 'SUCCEEDED', leaseToken = NULL, leaseExpiresAt = NULL,
            errorMessage = NULL, updatedAt = :now
        WHERE eventId = :eventId AND userId = :userId
          AND leaseToken = :myLeaseToken AND status = 'PROCESSING'
    """)
    suspend fun completeEventForUser(eventId: String, userId: String, myLeaseToken: String, now: Long): Int

    @Query("""
        UPDATE learner_event_status
        SET status = 'FAILED_RETRYABLE', leaseToken = NULL, leaseExpiresAt = NULL,
            failureCount = failureCount + 1, nextAttemptAt = :nextAttemptAt,
            errorMessage = :errorMessage, updatedAt = :now
        WHERE eventId = :eventId AND userId = :userId
          AND leaseToken = :myLeaseToken AND status = 'PROCESSING'
    """)
    suspend fun failRetryableForUser(
        eventId: String, userId: String, myLeaseToken: String,
        nextAttemptAt: Long, errorMessage: String?, now: Long
    ): Int

    @Query("""
        UPDATE learner_event_status
        SET status = 'FAILED_PERMANENT', leaseToken = NULL, leaseExpiresAt = NULL,
            failureCount = failureCount + 1, errorMessage = :errorMessage, updatedAt = :now
        WHERE eventId = :eventId AND userId = :userId
          AND leaseToken = :myLeaseToken AND status = 'PROCESSING'
    """)
    suspend fun failPermanentForUser(
        eventId: String, userId: String, myLeaseToken: String, errorMessage: String?, now: Long
    ): Int

    @Query("""
        UPDATE learner_event_status
        SET status = 'FAILED_PERMANENT', leaseToken = NULL, leaseExpiresAt = NULL,
            errorMessage = :errorMessage, updatedAt = :now
        WHERE userId = :userId AND claimCount >= maxAttempts
          AND status NOT IN ('SUCCEEDED', 'FAILED_PERMANENT')
          AND (status IN ('PENDING', 'FAILED_RETRYABLE')
            OR (status = 'PROCESSING' AND leaseExpiresAt < :now))
    """)
    suspend fun sweepClaimExhaustionForUser(
        userId: String, now: Long, errorMessage: String = "CLAIM_LIMIT_EXHAUSTED"
    ): Int

    @Query("""
        SELECT status.eventId FROM learner_event_status AS status
        WHERE status.userId = :userId AND status.status IN ('PENDING', 'FAILED_RETRYABLE')
          AND status.nextAttemptAt <= :now AND status.claimCount < status.maxAttempts
          AND (NOT EXISTS (SELECT 1 FROM events AS event WHERE event.id = status.eventId)
            OR EXISTS (SELECT 1 FROM events AS event WHERE event.id = status.eventId AND event.userId = :userId))
        ORDER BY status.nextAttemptAt ASC LIMIT :limit
    """)
    suspend fun getPendingEligibleEventIdsForUser(userId: String, now: Long, limit: Int): List<String>

    @Query("""
        SELECT status.eventId FROM learner_event_status AS status
        WHERE status.userId = :userId AND status.status = 'PROCESSING'
          AND status.leaseExpiresAt < :now AND status.claimCount < status.maxAttempts
          AND (NOT EXISTS (SELECT 1 FROM events AS event WHERE event.id = status.eventId)
            OR EXISTS (SELECT 1 FROM events AS event WHERE event.id = status.eventId AND event.userId = :userId))
        ORDER BY status.leaseExpiresAt ASC LIMIT :limit
    """)
    suspend fun getExpiredProcessingEventIdsForUser(userId: String, now: Long, limit: Int): List<String>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(status: LearnerEventStatusEntity)

    @Query("SELECT * FROM learner_event_status WHERE eventId = :eventId LIMIT 1")
    suspend fun getEventStatus(eventId: String): LearnerEventStatusEntity?

    @Query("""
        UPDATE learner_event_status
        SET status = 'PROCESSING',
            leaseToken = :newLeaseToken,
            leaseExpiresAt = :leaseExpiresAt,
            claimCount = claimCount + 1,
            lastAttemptAt = :now,
            updatedAt = :now
        WHERE eventId = :eventId
          AND status IN ('PENDING', 'FAILED_RETRYABLE')
          AND nextAttemptAt <= :now
          AND claimCount < maxAttempts
    """)
    suspend fun claimPendingEvent(
        eventId: String,
        now: Long,
        leaseExpiresAt: Long,
        newLeaseToken: String
    ): Int

    @Query("""
        UPDATE learner_event_status
        SET status = 'PROCESSING',
            leaseToken = :newLeaseToken,
            leaseExpiresAt = :leaseExpiresAt,
            claimCount = claimCount + 1,
            lastAttemptAt = :now,
            updatedAt = :now
        WHERE eventId = :eventId
          AND status = 'PROCESSING'
          AND leaseExpiresAt < :now
          AND claimCount < maxAttempts
    """)
    suspend fun claimExpiredProcessingEvent(
        eventId: String,
        now: Long,
        leaseExpiresAt: Long,
        newLeaseToken: String
    ): Int

    @Query("""
        UPDATE learner_event_status
        SET status = 'SUCCEEDED',
            leaseToken = NULL,
            leaseExpiresAt = NULL,
            errorMessage = NULL,
            updatedAt = :now
        WHERE eventId = :eventId
          AND leaseToken = :myLeaseToken
          AND status = 'PROCESSING'
    """)
    suspend fun completeEvent(
        eventId: String,
        myLeaseToken: String,
        now: Long
    ): Int

    @Query("""
        UPDATE learner_event_status
        SET status = 'FAILED_RETRYABLE',
            leaseToken = NULL,
            leaseExpiresAt = NULL,
            failureCount = failureCount + 1,
            nextAttemptAt = :nextAttemptAt,
            errorMessage = :errorMessage,
            updatedAt = :now
        WHERE eventId = :eventId
          AND leaseToken = :myLeaseToken
          AND status = 'PROCESSING'
    """)
    suspend fun failRetryable(
        eventId: String,
        myLeaseToken: String,
        nextAttemptAt: Long,
        errorMessage: String?,
        now: Long
    ): Int

    @Query("""
        UPDATE learner_event_status
        SET status = 'FAILED_PERMANENT',
            leaseToken = NULL,
            leaseExpiresAt = NULL,
            failureCount = failureCount + 1,
            errorMessage = :errorMessage,
            updatedAt = :now
        WHERE eventId = :eventId
          AND leaseToken = :myLeaseToken
          AND status = 'PROCESSING'
    """)
    suspend fun failPermanent(
        eventId: String,
        myLeaseToken: String,
        errorMessage: String?,
        now: Long
    ): Int

    @Query("""
        UPDATE learner_event_status
        SET status = 'PENDING',
            leaseToken = NULL,
            leaseExpiresAt = NULL,
            updatedAt = :now
        WHERE eventId = :eventId
          AND leaseToken = :myLeaseToken
          AND status = 'PROCESSING'
    """)
    suspend fun releaseLease(
        eventId: String,
        myLeaseToken: String,
        now: Long
    ): Int

    @Query("""
        UPDATE learner_event_status
        SET status = 'FAILED_PERMANENT',
            leaseToken = NULL,
            leaseExpiresAt = NULL,
            errorMessage = :errorMessage,
            updatedAt = :now
        WHERE claimCount >= maxAttempts
          AND status NOT IN ('SUCCEEDED', 'FAILED_PERMANENT')
          AND (
              status IN ('PENDING', 'FAILED_RETRYABLE')
              OR (status = 'PROCESSING' AND leaseExpiresAt < :now)
          )
    """)
    suspend fun sweepClaimExhaustion(
        now: Long,
        errorMessage: String = "CLAIM_LIMIT_EXHAUSTED"
    ): Int

    @Query("""
        SELECT eventId FROM learner_event_status
        WHERE status IN ('PENDING', 'FAILED_RETRYABLE')
          AND nextAttemptAt <= :now
          AND claimCount < maxAttempts
        ORDER BY nextAttemptAt ASC
        LIMIT :limit
    """)
    suspend fun getPendingEligibleEventIds(now: Long, limit: Int): List<String>

    @Query("""
        SELECT eventId FROM learner_event_status
        WHERE status = 'PROCESSING'
          AND leaseExpiresAt < :now
          AND claimCount < maxAttempts
        ORDER BY leaseExpiresAt ASC
        LIMIT :limit
    """)
    suspend fun getExpiredProcessingEventIds(now: Long, limit: Int): List<String>

    @Query("SELECT * FROM learner_event_status WHERE userId = :userId ORDER BY updatedAt DESC, eventId ASC")
    suspend fun getAllForUser(userId: String): List<LearnerEventStatusEntity>

    @Query("DELETE FROM learner_event_status WHERE userId = :userId")
    suspend fun deleteAllForUser(userId: String): Int
}
