package com.sanket_satpute_20.ironmind.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "patterns")
data class PatternEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val type: String,
    val description: String,
    val conditions: String?,
    val predictedBehavior: String?,
    val confidence: Float,
    val evidenceCount: Int,
    val evidenceReferences: String?,
    val firstObservedAt: Long,
    val lastObservedAt: Long,
    val status: String,
    val confirmationState: String,
    val createdAt: Long,
    val updatedAt: Long
)
