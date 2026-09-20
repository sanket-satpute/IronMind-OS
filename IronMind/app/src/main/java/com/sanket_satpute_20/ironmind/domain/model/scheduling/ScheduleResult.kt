package com.sanket_satpute_20.ironmind.domain.model.scheduling

import com.sanket_satpute_20.ironmind.domain.model.Commitment

sealed class ScheduleResult {
    data class Scheduled(val commitment: Commitment) : ScheduleResult()
    data class Conflict(val reason: String) : ScheduleResult()
    object RequiresPermission : ScheduleResult()
    object NotAllowed : ScheduleResult()
}
