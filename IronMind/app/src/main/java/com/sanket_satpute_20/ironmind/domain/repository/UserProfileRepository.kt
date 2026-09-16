package com.sanket_satpute_20.ironmind.domain.repository

import com.sanket_satpute_20.ironmind.domain.model.UserProfile
import com.sanket_satpute_20.ironmind.domain.common.Result

interface UserProfileRepository : Repository {
    suspend fun saveProfile(profile: UserProfile): Result<Unit, Exception>
    suspend fun getProfile(id: String): Result<UserProfile?, Exception>
}
