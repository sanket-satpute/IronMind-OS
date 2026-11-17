package com.sanket_satpute_20.ironmind.domain.usecase.data

import com.sanket_satpute_20.ironmind.domain.model.*
import com.sanket_satpute_20.ironmind.domain.model.barrier.BarrierHypothesis
import com.sanket_satpute_20.ironmind.domain.model.experiment.ExperimentRecord
import com.sanket_satpute_20.ironmind.domain.model.intervention.CorrectionReviewItem
import com.sanket_satpute_20.ironmind.domain.model.intervention.InterventionRecord
import com.sanket_satpute_20.ironmind.domain.model.observation.Observation
import com.sanket_satpute_20.ironmind.domain.model.pattern.Pattern

/**
 * Domain record representing an autonomy setting for a specific capability.
 */
data class AutonomySettingRecord(
    val userId: String,
    val capability: String,
    val level: String,
    val updatedAt: Long
)

/**
 * Domain record representing the global autonomy pause state.
 */
data class GlobalAutonomyStateRecord(
    val userId: String,
    val isGlobalPauseActive: Boolean,
    val updatedAt: Long
)

/**
 * Domain record representing background learner processing status for an event.
 */
data class LearnerEventStatusRecord(
    val eventId: String,
    val userId: String,
    val status: String,
    val claimCount: Int,
    val failureCount: Int,
    val maxAttempts: Int,
    val leaseToken: String?,
    val leaseExpiresAt: Long?,
    val lastAttemptAt: Long?,
    val nextAttemptAt: Long,
    val errorMessage: String?,
    val hadMalformedMetadata: Boolean,
    val createdAt: Long,
    val updatedAt: Long
)

/**
 * Export-specific representation of an intervention recommendation.
 *
 * Epistemic & Historical Fidelity Guarantee:
 * Preserves raw database fields faithfully, including nullable objective for historical
 * pre-migration-21 records (where objective is null). Does NOT fabricate or invent
 * an objective (e.g. INITIATE_ACTION) when none was persisted.
 */
data class InterventionRecommendationExportRecord(
    val id: String,
    val userId: String,
    val interventionType: String,
    val objective: String?,
    val targetEntityId: String?,
    val targetEntityType: String?,
    val rationale: String,
    val suggestedAction: String,
    val status: String,
    val createdAt: Long,
    val expiresAt: Long?,
    val causalHypothesisId: String? = null,
    val causalHypothesisType: String? = null
)

/**
 * Versioned, comprehensive snapshot of all user-owned domain data.
 *
 * Captures all 26 user-owned Room entities in a single atomic transaction.
 * Device-level tables (decision_records, notification_records, outbox) are intentionally excluded.
 */
data class UserDataExport(
    val exportVersion: Int = CURRENT_EXPORT_VERSION,
    val exportedAt: Long,
    val userId: String,
    val profile: UserProfile?,
    val goals: List<Goal> = emptyList(),
    val plans: List<Plan> = emptyList(),
    val tasks: List<Task> = emptyList(),
    val commitments: List<Commitment> = emptyList(),
    val outcomes: List<Outcome> = emptyList(),
    val reflections: List<Reflection> = emptyList(),
    val protectionRules: List<ProtectionRule> = emptyList(),
    val protectionSessions: List<ProtectionSession> = emptyList(),
    val events: List<Event> = emptyList(),
    val memories: List<Memory> = emptyList(),
    val observations: List<Observation> = emptyList(),
    val patterns: List<Pattern> = emptyList(),
    val autonomySettings: List<AutonomySettingRecord> = emptyList(),
    val globalAutonomyState: GlobalAutonomyStateRecord? = null,
    val appUsageObservationSettings: AppUsageObservationSettings? = null,
    val interventionRecords: List<InterventionRecord> = emptyList(),
    val notificationObservationSettings: NotificationObservationSettings? = null,
    val calendarObservationSettings: CalendarObservationSettings? = null,
    val locationObservationSettings: LocationObservationSettings? = null,
    val activityObservationSettings: ActivityObservationSettings? = null,
    val experimentRecords: List<ExperimentRecord> = emptyList(),
    val barrierHypotheses: List<BarrierHypothesis> = emptyList(),
    val interventionRecommendations: List<InterventionRecommendationExportRecord> = emptyList(),
    val learnerEventStatuses: List<LearnerEventStatusRecord> = emptyList(),
    val correctionReviews: List<CorrectionReviewItem> = emptyList()
) {
    /**
     * Calculates the total count of individual records across all 26 categories.
     */
    fun totalRecordCount(): Int =
        (if (profile != null) 1 else 0) +
        goals.size + plans.size + tasks.size + commitments.size +
        outcomes.size + reflections.size + protectionRules.size +
        protectionSessions.size + events.size + memories.size +
        observations.size + patterns.size + autonomySettings.size +
        (if (globalAutonomyState != null) 1 else 0) +
        (if (appUsageObservationSettings != null) 1 else 0) +
        interventionRecords.size +
        (if (notificationObservationSettings != null) 1 else 0) +
        (if (calendarObservationSettings != null) 1 else 0) +
        (if (locationObservationSettings != null) 1 else 0) +
        (if (activityObservationSettings != null) 1 else 0) +
        experimentRecords.size + barrierHypotheses.size +
        interventionRecommendations.size + learnerEventStatuses.size +
        correctionReviews.size

    companion object {
        const val CURRENT_EXPORT_VERSION = 1
        const val EXPORT_FORMAT_IDENTIFIER = "ironmind-user-data"

        /**
         * Device-level tables that are strictly excluded from user data exports:
         * 1. decision_records - Local execution audit and AI policy evaluation history
         * 2. notification_records - Local notification delivery and deduplication tracking
         * 3. outbox - Local pending sync mutation queue for remote synchronization
         */
        val EXCLUDED_DEVICE_LEVEL_TABLES = listOf(
            "decision_records",
            "notification_records",
            "outbox"
        )
    }
}
