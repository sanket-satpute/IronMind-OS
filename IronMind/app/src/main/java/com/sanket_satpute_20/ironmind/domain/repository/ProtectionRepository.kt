package com.sanket_satpute_20.ironmind.domain.repository

import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.ProtectionRule
import com.sanket_satpute_20.ironmind.domain.model.ProtectionSession

interface ProtectionRepository {
    suspend fun saveProtectionRule(rule: ProtectionRule): Result<Unit, Exception>
    suspend fun getProtectionRule(id: String): Result<ProtectionRule, Exception>
    suspend fun getProtectionRulesForUser(userId: String): Result<List<ProtectionRule>, Exception>
    
    suspend fun saveProtectionSession(session: ProtectionSession): Result<Unit, Exception>
    suspend fun getProtectionSession(id: String): Result<ProtectionSession, Exception>
    suspend fun getActiveProtectionSessionsForUser(userId: String): Result<List<ProtectionSession>, Exception>
}
