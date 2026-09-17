package com.sanket_satpute_20.ironmind.domain.model

data class Task(
    val id: String,
    val userId: String,
    val goalId: String? = null,
    val planId: String? = null,
    val parentTaskId: String? = null,
    val title: String,
    val description: String,
    val status: TaskStatus,
    val priority: Int,
    val estimatedDurationMinutes: Int? = null,
    val scheduledAt: Long? = null,
    val dueAt: Long? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val completedAt: Long? = null,
    val postponedAt: Long? = null,
    val source: EntitySource
)
