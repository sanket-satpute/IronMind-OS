package com.sanket_satpute_20.ironmind.domain.model

data class Event(
    val id: String,
    val userId: String,
    
    val type: EventType,
    
    val entityType: String? = null,
    val entityId: String? = null,
    
    val occurredAt: Long,
    val recordedAt: Long,
    val processedAt: Long? = null,
    
    val source: EntitySource,
    
    val previousState: String? = null,
    val newState: String? = null,
    
    val metadata: String? = null,
    
    val correlationId: String? = null,
    val causationId: String? = null,
    
    val schemaVersion: Int = 1
)
