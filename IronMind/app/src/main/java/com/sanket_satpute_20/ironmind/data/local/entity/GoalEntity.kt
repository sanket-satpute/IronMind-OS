package com.sanket_satpute_20.ironmind.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "goal")
data class GoalEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val ambitionId: String?,
    val title: String,
    val description: String,
    val why: String,
    val importance: Int,
    val status: String,
    val targetAt: Long?,
    val startedAt: Long?,
    val completedAt: Long?,
    val createdAt: Long,
    val updatedAt: Long,
    val schemaVersion: Int
)
