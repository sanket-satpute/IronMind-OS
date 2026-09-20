package com.sanket_satpute_20.ironmind.domain.model.notification

data class NotificationRecord(
    val id: String,
    val timestamp: Long,
    val deduplicationKey: String?,
    val deliveryStatus: NotificationDeliveryStatus,
    val suppressionReason: String? = null
)

enum class NotificationDeliveryStatus {
    DELIVERED,
    SUPPRESSED
}
