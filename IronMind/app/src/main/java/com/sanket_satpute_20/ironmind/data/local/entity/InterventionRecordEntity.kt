package com.sanket_satpute_20.ironmind.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.sanket_satpute_20.ironmind.domain.ai.InterventionType
import com.sanket_satpute_20.ironmind.domain.model.intervention.InterventionState
import com.sanket_satpute_20.ironmind.domain.model.intervention.InterventionResolutionReason

@Entity(tableName = "intervention_records")
data class InterventionRecordEntity(
    @PrimaryKey
    val id: String,
    val userId: String,
    val type: InterventionType,
    val state: InterventionState,
    val title: String,
    val description: String,
    val resolutionReason: InterventionResolutionReason?,
    val contextData: String?,
    val createdAt: Long,
    val updatedAt: Long
)
