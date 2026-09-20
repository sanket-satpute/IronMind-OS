package com.sanket_satpute_20.ironmind.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.sanket_satpute_20.ironmind.domain.model.experiment.ExperimentState

@Entity(tableName = "experiment_records")
data class ExperimentRecordEntity(
    @PrimaryKey
    val id: String,
    val userId: String,
    val hypothesis: String,
    val activeVariation: String,
    val controlVariation: String,
    val targetMetric: String,
    val state: ExperimentState,
    val startedAt: Long,
    val endedAt: Long?,
    val outcomeSummary: String?
)
