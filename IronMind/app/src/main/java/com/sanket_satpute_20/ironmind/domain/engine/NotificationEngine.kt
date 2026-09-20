package com.sanket_satpute_20.ironmind.domain.engine

import com.sanket_satpute_20.ironmind.domain.model.notification.NotificationCandidate
import com.sanket_satpute_20.ironmind.domain.model.notification.NotificationDeliveryStatus

interface NotificationEngine {
    /**
     * Evaluates a notification candidate against policies (quiet periods, cooldown, priority, deduplication).
     * If allowed, triggers delivery and records the outcome.
     * @return DELIVERED if it was sent, SUPPRESSED if blocked by policy.
     */
    suspend fun process(candidate: NotificationCandidate): NotificationDeliveryStatus
}
