package com.sanket_satpute_20.ironmind.testutil.fake

import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.AutonomyCapability
import com.sanket_satpute_20.ironmind.domain.model.AutonomyLevel
import com.sanket_satpute_20.ironmind.domain.model.AutonomySettings
import com.sanket_satpute_20.ironmind.domain.repository.AutonomySettingsRepository

class FakeAutonomySettingsRepository : AutonomySettingsRepository {
    private val settingsMap = mutableMapOf<String, MutableMap<AutonomyCapability, AutonomyLevel>>()
    var shouldFail = false

    override suspend fun getSettings(userId: String): Result<AutonomySettings, Exception> {
        if (shouldFail) return Result.Failure(Exception("Fake failure"))
        val userMap = settingsMap[userId] ?: mutableMapOf()
        return Result.Success(AutonomySettings(userId, userMap))
    }

    override suspend fun updateLevel(
        userId: String,
        capability: AutonomyCapability,
        level: AutonomyLevel
    ): Result<Unit, Exception> {
        if (shouldFail) return Result.Failure(Exception("Fake failure"))
        val userMap = settingsMap.getOrPut(userId) { mutableMapOf() }
        userMap[capability] = level
        return Result.Success(Unit)
    }
}
