package com.sanket_satpute_20.ironmind.domain.model

data class Goal(
    val id: String,
    val userId: String,
    val ambitionId: String? = null,
    val title: String,
    val description: String,
    val why: String,
    val importance: Int,
    val status: GoalStatus,
    val targetAt: Long? = null,
    val startedAt: Long? = null,
    val completedAt: Long? = null,
    val createdAt: Long,
    val updatedAt: Long
)
