package com.sanket_satpute_20.ironmind.domain.engine

import com.sanket_satpute_20.ironmind.domain.model.protection.ProtectionCandidate
import com.sanket_satpute_20.ironmind.domain.model.protection.ProtectionResult

interface AutoProtectionEngine {
    suspend fun protect(userId: String, candidate: ProtectionCandidate): ProtectionResult
}
