package com.sanket_satpute_20.ironmind.domain.usecase.profile

import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.UserProfile
import com.sanket_satpute_20.ironmind.domain.repository.UserProfileRepository

class GetLocalProfileUseCase(
    private val repository: UserProfileRepository
) {
    suspend operator fun invoke(): Result<UserProfile?, Exception> {
        return repository.getLocalProfile()
    }
}
