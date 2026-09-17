package com.sanket_satpute_20.ironmind.domain.model

data class Plan(
    val id: String,
    val userId: String,
    val goalId: String? = null,
    val ambitionId: String? = null,
    val title: String,
    val description: String,
    val status: PlanStatus,
    val createdAt: Long,
    val updatedAt: Long,
    val startedAt: Long? = null,
    val completedAt: Long? = null,
    val source: EntitySource
)
