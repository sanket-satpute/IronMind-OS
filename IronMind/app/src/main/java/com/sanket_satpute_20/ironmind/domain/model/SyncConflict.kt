package com.sanket_satpute_20.ironmind.domain.model

/**
 * Domain model representing a synchronization conflict between a local mutation and a remote document.
 *
 * Epistemic & Clean Architecture Guarantee:
 * Decoupled from Room entity annotations and persistence details.
 */
data class SyncConflict(
    val conflictId: String,
    val operationId: String,
    val userId: String,
    val entityType: String,
    val entityId: String,
    val localPayload: String,
    val remotePayload: String? = null,
    val conflictReason: String,
    val createdAt: Long,
    val updatedAt: Long,
    val resolutionStatus: String,
    val resolvedAt: Long? = null,
    val resolvedBy: String? = null
)
