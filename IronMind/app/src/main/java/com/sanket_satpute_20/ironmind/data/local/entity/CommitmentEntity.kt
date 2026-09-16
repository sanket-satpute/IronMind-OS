package com.sanket_satpute_20.ironmind.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "commitment")
data class CommitmentEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val goalId: String?,
    val planId: String?,
    val taskId: String?,
    val title: String,
    val description: String,
    val committedAt: Long,
    val scheduledStartAt: Long?,
    val scheduledEndAt: Long?,
    val status: String,
    val priority: Int,
    val source: String,
    val createdAt: Long,
    val updatedAt: Long,
    val startedAt: Long?,
    val completedAt: Long?,
    val postponedAt: Long?,
    val missedAt: Long?,
    val recoveredAt: Long?,
    val parentCommitmentId: String?,
    val schemaVersion: Int
)
