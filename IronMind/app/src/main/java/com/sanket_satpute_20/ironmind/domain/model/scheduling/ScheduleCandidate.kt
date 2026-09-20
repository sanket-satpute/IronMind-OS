package com.sanket_satpute_20.ironmind.domain.model.scheduling

data class ScheduleCandidate(
    val title: String,
    val description: String,
    val proposedStartTime: Long,
    val proposedEndTime: Long,
    val goalId: String? = null,
    val planId: String? = null,
    val taskId: String? = null,
    val parentCommitmentId: String? = null,
    val priority: Int = 0
)
