package com.sanket_satpute_20.ironmind.domain.model

data class ProtectionSession(
    val id: String,
    val userId: String,
    val ruleId: String? = null,
    val commitmentId: String? = null,
    val taskId: String? = null,
    val startedAt: Long,
    val scheduledEndAt: Long? = null,
    val endedAt: Long? = null,
    val status: ProtectionSessionStatus,
    val source: EntitySource,
    val overrideAllowed: Boolean,
    val createdAt: Long,
    val updatedAt: Long
)
