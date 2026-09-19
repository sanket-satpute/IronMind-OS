package com.sanket_satpute_20.ironmind.domain.usecase.auth

import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.repository.AuthRepository

class SignOutUseCase(
    private val authRepository: AuthRepository
) {
    suspend operator fun invoke(): Result<Unit, Exception> {
        return authRepository.signOut()
    }
}
