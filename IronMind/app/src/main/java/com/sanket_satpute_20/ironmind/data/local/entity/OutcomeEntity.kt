package com.sanket_satpute_20.ironmind.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "outcome")
data class OutcomeEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val sourceEntityId: String,
    val sourceEntityType: String,
    val resultStatus: String,
    val actualDurationMinutes: Int?,
    val completedAt: Long,
    val createdAt: Long,
    val schemaVersion: Int
)
