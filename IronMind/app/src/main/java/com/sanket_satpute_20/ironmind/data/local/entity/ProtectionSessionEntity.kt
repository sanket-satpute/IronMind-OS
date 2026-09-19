package com.sanket_satpute_20.ironmind.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.sanket_satpute_20.ironmind.domain.model.EntitySource
import com.sanket_satpute_20.ironmind.domain.model.ProtectionSession
import com.sanket_satpute_20.ironmind.domain.model.ProtectionSessionStatus

@Entity(tableName = "protection_sessions")
data class ProtectionSessionEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val ruleId: String?,
    val commitmentId: String?,
    val taskId: String?,
    val startedAt: Long,
    val scheduledEndAt: Long?,
    val endedAt: Long?,
    val status: String,
    val source: String,
    val overrideAllowed: Boolean,
    val createdAt: Long,
    val updatedAt: Long
)

fun ProtectionSessionEntity.toDomain(): ProtectionSession {
    return ProtectionSession(
        id = id,
        userId = userId,
        ruleId = ruleId,
        commitmentId = commitmentId,
        taskId = taskId,
        startedAt = startedAt,
        scheduledEndAt = scheduledEndAt,
        endedAt = endedAt,
        status = ProtectionSessionStatus.valueOf(status),
        source = EntitySource.valueOf(source),
        overrideAllowed = overrideAllowed,
        createdAt = createdAt,
        updatedAt = updatedAt
    )
}

fun ProtectionSession.toEntity(): ProtectionSessionEntity {
    return ProtectionSessionEntity(
        id = id,
        userId = userId,
        ruleId = ruleId,
        commitmentId = commitmentId,
        taskId = taskId,
        startedAt = startedAt,
        scheduledEndAt = scheduledEndAt,
        endedAt = endedAt,
        status = status.name,
        source = source.name,
        overrideAllowed = overrideAllowed,
        createdAt = createdAt,
        updatedAt = updatedAt
    )
}
