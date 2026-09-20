package com.sanket_satpute_20.ironmind.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "notification_records")
data class NotificationRecordEntity(
    @PrimaryKey val id: String,
    val timestamp: Long,
    val deduplicationKey: String?,
    val deliveryStatus: String,
    val suppressionReason: String?
)
