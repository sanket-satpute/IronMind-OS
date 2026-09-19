package com.sanket_satpute_20.ironmind.domain.usecase.auth

import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.AuthUser
import com.sanket_satpute_20.ironmind.domain.repository.AuthRepository

class SignInAnonymouslyUseCase(
    private val authRepository: AuthRepository
) {
    suspend operator fun invoke(): Result<AuthUser, Exception> {
        return authRepository.signInAnonymously()
    }
}
