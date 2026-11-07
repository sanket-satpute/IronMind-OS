package com.sanket_satpute_20.ironmind.domain.model.digital

/**
 * Domain model representing a user-configured app restriction.
 *
 * Epistemic & Behavioral Guarantees:
 * - Pure configuration model; does NOT actively block applications on its own.
 * - Requires explicit user selection and user ownership ([userId]).
 * - Persisted safely without requiring schema-breaking Room migrations.
 * - Stores a snapshot of the application display name at the time of configuration.
 */
data class AppRestrictionConfig(
    val id: String,
    val userId: String,
    val packageName: String,
    val appDisplayName: String,
    val isEnabled: Boolean = true,
    val createdAt: Long,
    val updatedAt: Long,
    val scheduleReferenceId: String? = null
)
