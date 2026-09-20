package com.sanket_satpute_20.ironmind.domain.model.protection

import com.sanket_satpute_20.ironmind.domain.model.ProtectionSession

sealed class ProtectionResult {
    data class Protected(val session: ProtectionSession) : ProtectionResult()
    data class Conflict(val reason: String) : ProtectionResult()
    object RequiresPermission : ProtectionResult()
    object NotAllowed : ProtectionResult()
}
