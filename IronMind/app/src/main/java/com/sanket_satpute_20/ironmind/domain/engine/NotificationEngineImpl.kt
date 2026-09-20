package com.sanket_satpute_20.ironmind.domain.engine

import com.sanket_satpute_20.ironmind.domain.logging.IronLogger
import com.sanket_satpute_20.ironmind.domain.model.notification.NotificationCandidate
import com.sanket_satpute_20.ironmind.domain.model.notification.NotificationDeliveryStatus
import com.sanket_satpute_20.ironmind.domain.model.notification.NotificationPriority
import com.sanket_satpute_20.ironmind.domain.model.notification.NotificationRecord
import com.sanket_satpute_20.ironmind.domain.provider.NotificationProvider
import com.sanket_satpute_20.ironmind.domain.repository.NotificationRecordRepository
import java.util.Calendar
import java.util.UUID

class NotificationEngineImpl(
    private val notificationRecordRepository: NotificationRecordRepository,
    private val notificationProvider: NotificationProvider,
    private val logger: IronLogger,
    private val timeProvider: () -> Long = { System.currentTimeMillis() }
) : NotificationEngine {

    companion object {
        const val COOLDOWN_MS = 15 * 60 * 1000L // 15 minutes general cooldown
        const val DEDUPLICATION_MS = 60 * 60 * 1000L // 1 hour deduplication
    }

    override suspend fun process(candidate: NotificationCandidate): NotificationDeliveryStatus {
        val now = timeProvider()

        // 1. Quiet Period Policy
        if (isQuietPeriod(now) && candidate.priority != NotificationPriority.HIGH) {
            return recordAndLog(candidate, NotificationDeliveryStatus.SUPPRESSED, "QUIET_PERIOD", now)
        }

        // 2. Cooldown Policy (Skip if HIGH priority)
        if (candidate.priority != NotificationPriority.HIGH) {
            val recentRecords = notificationRecordRepository.getRecent(1).getOrNull()
            if (!recentRecords.isNullOrEmpty()) {
                val lastRecord = recentRecords.first()
                if (lastRecord.deliveryStatus == NotificationDeliveryStatus.DELIVERED && now - lastRecord.timestamp < COOLDOWN_MS) {
                    return recordAndLog(candidate, NotificationDeliveryStatus.SUPPRESSED, "COOLDOWN_ACTIVE", now)
                }
            }
        }

        // 3. Deduplication Policy
        if (candidate.deduplicationKey != null) {
            val mostRecentByKey = notificationRecordRepository.getMostRecentByKey(candidate.deduplicationKey).getOrNull()
            if (mostRecentByKey != null && mostRecentByKey.deliveryStatus == NotificationDeliveryStatus.DELIVERED) {
                if (now - mostRecentByKey.timestamp < DEDUPLICATION_MS) {
                    return recordAndLog(candidate, NotificationDeliveryStatus.SUPPRESSED, "DEDUPLICATED", now)
                }
            }
        }

        // 4. Delivery
        // Trigger delivery via provider
        notificationProvider.showNotification(candidate)
        
        return recordAndLog(candidate, NotificationDeliveryStatus.DELIVERED, null, now)
    }

    private suspend fun recordAndLog(
        candidate: NotificationCandidate,
        status: NotificationDeliveryStatus,
        suppressionReason: String?,
        timestamp: Long
    ): NotificationDeliveryStatus {
        
        val record = NotificationRecord(
            id = UUID.randomUUID().toString(),
            timestamp = timestamp,
            deduplicationKey = candidate.deduplicationKey,
            deliveryStatus = status,
            suppressionReason = suppressionReason
        )

        notificationRecordRepository.saveRecord(record)

        logger.logLifecycle(
            component = "Notification",
            event = status.name,
            parameters = mapOf(
                "candidateId" to candidate.id,
                "priority" to candidate.priority.name,
                "suppressionReason" to (suppressionReason ?: "none")
            )
        )

        return status
    }

    private fun isQuietPeriod(timeMs: Long): Boolean {
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = timeMs
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        // Quiet period: 22:00 to 07:00 (10 PM to 7 AM)
        return hour >= 22 || hour < 7
    }
}
