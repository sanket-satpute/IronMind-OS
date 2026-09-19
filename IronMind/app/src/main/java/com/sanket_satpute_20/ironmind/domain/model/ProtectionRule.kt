package com.sanket_satpute_20.ironmind.domain.model

data class ProtectionRule(
    val id: String,
    val userId: String,
    val targetPackage: String? = null,
    val targetCategory: String? = null,
    val triggerType: ProtectionTriggerType,
    val conditions: String,
    val enabled: Boolean,
    val priority: Int,
    val createdAt: Long,
    val updatedAt: Long
)
