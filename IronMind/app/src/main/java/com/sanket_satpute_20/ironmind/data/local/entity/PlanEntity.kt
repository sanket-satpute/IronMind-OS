package com.sanket_satpute_20.ironmind.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "plan")
data class PlanEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val goalId: String?,
    val ambitionId: String?,
    val title: String,
    val description: String,
    val status: String,
    val createdAt: Long,
    val updatedAt: Long,
    val startedAt: Long?,
    val completedAt: Long?,
    val source: String,
    val schemaVersion: Int
)
