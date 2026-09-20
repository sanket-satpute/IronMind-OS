package com.sanket_satpute_20.ironmind.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "decision_records")
data class DecisionRecordEntity(
    @PrimaryKey
    val id: String,
    val timestamp: Long,
    val capability: String,
    val action: String,
    val trigger: String?,
    val source: String?,
    val contextSummary: String?,
    val policy: String?,
    val autonomyLevel: String,
    val reasoning: String?,
    val confidence: Float?,
    val result: String,
    val failureReason: String?,
    val userResponse: String?
)
