package com.sanket_satpute_20.ironmind.testutil.fake

import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.UserProfile
import com.sanket_satpute_20.ironmind.domain.repository.UserProfileRepository

class FakeUserProfileRepository : UserProfileRepository {
    private val profiles = mutableMapOf<String, UserProfile>()
    var shouldFail = false

    override suspend fun saveProfile(profile: UserProfile): Result<Unit, Exception> {
        if (shouldFail) return Result.Failure(Exception("Fake failure"))
        profiles[profile.id] = profile
        return Result.Success(Unit)
    }

    override suspend fun getProfile(id: String): Result<UserProfile?, Exception> {
        if (shouldFail) return Result.Failure(Exception("Fake failure"))
        return Result.Success(profiles[id])
    }

    fun clear() {
        profiles.clear()
    }
}
