package com.sanket_satpute_20.ironmind.domain.model.notification

data class NotificationCandidate(
    val id: String,
    val title: String,
    val message: String,
    val priority: NotificationPriority,
    val channelId: String,
    val deduplicationKey: String? = null
)

enum class NotificationPriority {
    LOW,
    DEFAULT,
    HIGH
}
