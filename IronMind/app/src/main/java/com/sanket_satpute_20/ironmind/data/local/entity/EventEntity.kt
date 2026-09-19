package com.sanket_satpute_20.ironmind.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.sanket_satpute_20.ironmind.domain.model.EntitySource
import com.sanket_satpute_20.ironmind.domain.model.Event
import com.sanket_satpute_20.ironmind.domain.model.EventType

@Entity(tableName = "events")
data class EventEntity(
    @PrimaryKey val id: String,
    val userId: String,
    
    val type: String,
    
    val entityType: String?,
    val entityId: String?,
    
    val occurredAt: Long,
    val recordedAt: Long,
    val processedAt: Long?,
    
    val source: String,
    
    val previousState: String?,
    val newState: String?,
    
    val metadata: String?,
    
    val correlationId: String?,
    val causationId: String?,
    
    val schemaVersion: Int
)

fun EventEntity.toDomainModel(): Event {
    return Event(
        id = id,
        userId = userId,
        type = EventType.valueOf(type),
        entityType = entityType,
        entityId = entityId,
        occurredAt = occurredAt,
        recordedAt = recordedAt,
        processedAt = processedAt,
        source = EntitySource.valueOf(source),
        previousState = previousState,
        newState = newState,
        metadata = metadata,
        correlationId = correlationId,
        causationId = causationId,
        schemaVersion = schemaVersion
    )
}

fun Event.toEntity(): EventEntity {
    return EventEntity(
        id = id,
        userId = userId,
        type = type.name,
        entityType = entityType,
        entityId = entityId,
        occurredAt = occurredAt,
        recordedAt = recordedAt,
        processedAt = processedAt,
        source = source.name,
        previousState = previousState,
        newState = newState,
        metadata = metadata,
        correlationId = correlationId,
        causationId = causationId,
        schemaVersion = schemaVersion
    )
}
