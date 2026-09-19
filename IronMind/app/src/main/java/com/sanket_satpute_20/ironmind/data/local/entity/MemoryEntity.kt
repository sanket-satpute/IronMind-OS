package com.sanket_satpute_20.ironmind.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.sanket_satpute_20.ironmind.domain.model.EntitySource
import com.sanket_satpute_20.ironmind.domain.model.Memory
import com.sanket_satpute_20.ironmind.domain.model.MemoryConfirmationState
import com.sanket_satpute_20.ironmind.domain.model.MemoryStatus

@Entity(tableName = "memory")
data class MemoryEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val type: String,
    val content: String,
    val source: String,
    val confidence: Float,
    val evidenceCount: Int,
    val firstObservedAt: Long,
    val lastObservedAt: Long,
    val confirmationState: String,
    val status: String,
    val expiresAt: Long?,
    val createdAt: Long,
    val updatedAt: Long,
    val schemaVersion: Int
) {
    fun toDomain(): Memory = Memory(
        id = id,
        userId = userId,
        type = type,
        content = content,
        source = EntitySource.valueOf(source),
        confidence = confidence,
        evidenceCount = evidenceCount,
        firstObservedAt = firstObservedAt,
        lastObservedAt = lastObservedAt,
        confirmationState = MemoryConfirmationState.valueOf(confirmationState),
        status = MemoryStatus.valueOf(status),
        expiresAt = expiresAt,
        createdAt = createdAt,
        updatedAt = updatedAt,
        schemaVersion = schemaVersion
    )

    companion object {
        fun fromDomain(memory: Memory): MemoryEntity = MemoryEntity(
            id = memory.id,
            userId = memory.userId,
            type = memory.type,
            content = memory.content,
            source = memory.source.name,
            confidence = memory.confidence,
            evidenceCount = memory.evidenceCount,
            firstObservedAt = memory.firstObservedAt,
            lastObservedAt = memory.lastObservedAt,
            confirmationState = memory.confirmationState.name,
            status = memory.status.name,
            expiresAt = memory.expiresAt,
            createdAt = memory.createdAt,
            updatedAt = memory.updatedAt,
            schemaVersion = memory.schemaVersion
        )
    }
}
