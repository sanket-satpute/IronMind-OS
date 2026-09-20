package com.sanket_satpute_20.ironmind.domain.usecase.data

import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.repository.DataManagementRepository

class DeleteUserDataUseCase(
    private val dataManagementRepository: DataManagementRepository
) {
    suspend operator fun invoke(userId: String): Result<Unit, Exception> {
        return dataManagementRepository.deleteAllUserData(userId)
    }
}
