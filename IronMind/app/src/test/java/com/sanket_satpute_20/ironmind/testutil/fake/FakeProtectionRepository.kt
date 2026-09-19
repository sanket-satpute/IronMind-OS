package com.sanket_satpute_20.ironmind.testutil.fake

import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.ProtectionRule
import com.sanket_satpute_20.ironmind.domain.model.ProtectionSession
import com.sanket_satpute_20.ironmind.domain.model.ProtectionSessionStatus
import com.sanket_satpute_20.ironmind.domain.repository.ProtectionRepository

class FakeProtectionRepository : ProtectionRepository {
    private val rules = mutableMapOf<String, ProtectionRule>()
    private val sessions = mutableMapOf<String, ProtectionSession>()

    override suspend fun saveProtectionRule(rule: ProtectionRule): Result<Unit, Exception> {
        rules[rule.id] = rule
        return Result.Success(Unit)
    }

    override suspend fun getProtectionRule(id: String): Result<ProtectionRule, Exception> {
        val rule = rules[id]
        return if (rule != null) {
            Result.Success(rule)
        } else {
            Result.Failure(Exception("ProtectionRule not found"))
        }
    }

    override suspend fun getProtectionRulesForUser(userId: String): Result<List<ProtectionRule>, Exception> {
        val userRules = rules.values.filter { it.userId == userId }.sortedByDescending { it.priority }
        return Result.Success(userRules)
    }

    override suspend fun saveProtectionSession(session: ProtectionSession): Result<Unit, Exception> {
        sessions[session.id] = session
        return Result.Success(Unit)
    }

    override suspend fun getProtectionSession(id: String): Result<ProtectionSession, Exception> {
        val session = sessions[id]
        return if (session != null) {
            Result.Success(session)
        } else {
            Result.Failure(Exception("ProtectionSession not found"))
        }
    }

    override suspend fun getActiveProtectionSessionsForUser(userId: String): Result<List<ProtectionSession>, Exception> {
        val activeSessions = sessions.values
            .filter { it.userId == userId && it.status == ProtectionSessionStatus.ACTIVE }
            .sortedByDescending { it.startedAt }
        return Result.Success(activeSessions)
    }
}
