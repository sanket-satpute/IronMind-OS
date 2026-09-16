package com.sanket_satpute_20.ironmind.data.repository

import com.sanket_satpute_20.ironmind.data.local.dao.IronMindDao
import com.sanket_satpute_20.ironmind.data.local.entity.UserProfileEntity
import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.UserProfile
import com.sanket_satpute_20.ironmind.domain.repository.UserProfileRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class UserProfileRepositoryImpl(
    private val dao: IronMindDao
) : UserProfileRepository {
    
    override suspend fun saveProfile(profile: UserProfile): Result<Unit, Exception> {
        return try {
            withContext(Dispatchers.IO) {
                dao.insertUserProfile(
                    UserProfileEntity(
                        id = profile.id,
                        createdAt = profile.createdAt,
                        updatedAt = profile.updatedAt,
                        displayName = profile.displayName,
                        timezone = profile.timezone,
                        createdFrom = profile.createdFrom,
                        status = profile.status,
                        schemaVersion = 1
                    )
                )
            }
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }

    override suspend fun getProfile(id: String): Result<UserProfile?, Exception> {
        return try {
            val entity = withContext(Dispatchers.IO) {
                dao.getUserProfile(id)
            }
            val profile = entity?.let {
                UserProfile(
                    id = it.id,
                    createdAt = it.createdAt,
                    updatedAt = it.updatedAt,
                    displayName = it.displayName,
                    timezone = it.timezone,
                    createdFrom = it.createdFrom,
                    status = it.status
                )
            }
            Result.Success(profile)
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }
}
