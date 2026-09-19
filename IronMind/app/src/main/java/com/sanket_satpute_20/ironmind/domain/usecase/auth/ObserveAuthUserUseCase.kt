package com.sanket_satpute_20.ironmind.domain.usecase.auth

import com.sanket_satpute_20.ironmind.domain.model.AuthUser
import com.sanket_satpute_20.ironmind.domain.repository.AuthRepository
import kotlinx.coroutines.flow.Flow

class ObserveAuthUserUseCase(
    private val authRepository: AuthRepository
) {
    operator fun invoke(): Flow<AuthUser?> {
        return authRepository.currentUser
    }
}
