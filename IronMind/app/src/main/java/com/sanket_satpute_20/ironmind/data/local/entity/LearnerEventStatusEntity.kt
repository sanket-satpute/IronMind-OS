package com.sanket_satpute_20.ironmind.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "learner_event_status",
    indices = [
        Index(value = ["status", "nextAttemptAt"], name = "index_learner_event_status_poll"),
        Index(value = ["status", "leaseExpiresAt"], name = "index_learner_event_status_lease"),
        Index(value = ["userId"], name = "index_learner_event_status_user")
    ]
)
data class LearnerEventStatusEntity(
    @PrimaryKey val eventId: String,
    val userId: String,
    val status: String,
    val claimCount: Int,
    val failureCount: Int,
    val maxAttempts: Int,
    val leaseToken: String?,
    val leaseExpiresAt: Long?,
    val lastAttemptAt: Long?,
    val nextAttemptAt: Long,
    val errorMessage: String?,
    val hadMalformedMetadata: Boolean,
    val createdAt: Long,
    val updatedAt: Long
)
