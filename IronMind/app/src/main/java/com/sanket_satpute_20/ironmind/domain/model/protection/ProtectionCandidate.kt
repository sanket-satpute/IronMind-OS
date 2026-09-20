package com.sanket_satpute_20.ironmind.domain.model.protection

data class ProtectionCandidate(
    val title: String,
    val description: String,
    val targetPackages: List<String>,
    val durationMs: Long,
    val ruleId: String? = null,
    val commitmentId: String? = null,
    val taskId: String? = null
)
