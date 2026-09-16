package com.sanket_satpute_20.ironmind.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "task")
data class TaskEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val goalId: String?,
    val planId: String?,
    val parentTaskId: String?,
    val title: String,
    val description: String,
    val status: String,
    val priority: Int,
    val estimatedDurationMinutes: Int?,
    val scheduledAt: Long?,
    val dueAt: Long?,
    val createdAt: Long,
    val updatedAt: Long,
    val completedAt: Long?,
    val postponedAt: Long?,
    val source: String,
    val schemaVersion: Int
)
