package com.sanket_satpute_20.ironmind.domain.usecase.profile

import com.sanket_satpute_20.ironmind.domain.common.Clock
import com.sanket_satpute_20.ironmind.domain.common.IdGenerator
import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.UserProfile
import com.sanket_satpute_20.ironmind.domain.repository.UserProfileRepository
import java.util.TimeZone

class InitializeLocalProfileUseCase(
    private val repository: UserProfileRepository,
    private val idGenerator: IdGenerator,
    private val clock: Clock
) {
    suspend operator fun invoke(): Result<UserProfile, Exception> {
        return when (val existingResult = repository.getLocalProfile()) {
            is Result.Failure -> Result.Failure(existingResult.error)
            is Result.Success -> {
                if (existingResult.data != null) {
                    Result.Success(existingResult.data)
                } else {
                    // Create new profile
                    val now = clock.currentTimeMillis()
                    val newProfile = UserProfile(
                        id = idGenerator.generateId(),
                        createdAt = now,
                        updatedAt = now,
                        displayName = "User", // Default name
                        timezone = TimeZone.getDefault().id,
                        createdFrom = "local_device",
                        status = "ACTIVE"
                    )
                    
                    when (val saveResult = repository.saveProfile(newProfile)) {
                        is Result.Failure -> Result.Failure(saveResult.error)
                        is Result.Success -> Result.Success(newProfile)
                    }
                }
            }
        }
    }
}
