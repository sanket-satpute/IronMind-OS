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

fun OutcomeEntity.toDomain(): com.sanket_satpute_20.ironmind.domain.model.Outcome {
    return com.sanket_satpute_20.ironmind.domain.model.Outcome(
        id = id,
        userId = userId,
        sourceEntityId = sourceEntityId,
        sourceEntityType = sourceEntityType,
        resultStatus = com.sanket_satpute_20.ironmind.domain.model.ResultStatus.valueOf(resultStatus),
        actualDurationMinutes = actualDurationMinutes,
        completedAt = completedAt,
        createdAt = createdAt,
        schemaVersion = schemaVersion
    )
}

fun com.sanket_satpute_20.ironmind.domain.model.Outcome.toEntity(): OutcomeEntity {
    return OutcomeEntity(
        id = id,
        userId = userId,
        sourceEntityId = sourceEntityId,
        sourceEntityType = sourceEntityType,
        resultStatus = resultStatus.name,
        actualDurationMinutes = actualDurationMinutes,
        completedAt = completedAt,
        createdAt = createdAt,
        schemaVersion = schemaVersion
    )
}
