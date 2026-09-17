package com.sanket_satpute_20.ironmind.domain.model

data class Commitment(
    val id: String,
    val userId: String,
    val goalId: String? = null,
    val planId: String? = null,
    val taskId: String? = null,
    val parentCommitmentId: String? = null,
    val title: String,
    val description: String,
    val committedAt: Long,
    val scheduledStartAt: Long? = null,
    val scheduledEndAt: Long? = null,
    val status: CommitmentStatus,
    val priority: Int,
    val source: EntitySource,
    val createdAt: Long,
    val updatedAt: Long,
    val startedAt: Long? = null,
    val completedAt: Long? = null,
    val postponedAt: Long? = null,
    val missedAt: Long? = null,
    val recoveredAt: Long? = null
)
