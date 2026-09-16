package com.sanket_satpute_20.ironmind.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "reflection")
data class ReflectionEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val targetEntityId: String?,
    val targetEntityType: String?,
    val content: String,
    val sentiment: String?,
    val createdAt: Long,
    val schemaVersion: Int
)
