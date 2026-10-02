package com.sanket_satpute_20.ironmind.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "barrier_hypotheses")
data class BarrierHypothesisEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val category: String,
    val description: String,
    val confirmationState: String,
    val status: String,
    val sourceReflectionId: String,
    val firstObservedAt: Long,
    val lastObservedAt: Long
)
