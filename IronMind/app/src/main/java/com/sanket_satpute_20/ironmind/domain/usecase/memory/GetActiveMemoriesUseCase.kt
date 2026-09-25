package com.sanket_satpute_20.ironmind.domain.usecase.memory

import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.Memory
import com.sanket_satpute_20.ironmind.domain.repository.MemoryRepository

/**
 * Retrieves all currently usable (active/decaying) memories for the user.
 * Excludes memories that are EXPIRED, DELETED, or otherwise inactive.
 * Results are deterministically ordered (e.g. by confidence/recency) 
 * as defined in the underlying repository/DAO contract.
 */
class GetActiveMemoriesUseCase(
    private val repository: MemoryRepository
) {
    suspend operator fun invoke(userId: String): Result<List<Memory>, Exception> {
        println("IronMindLifecycle Memory [LOAD_START] userId=$userId type=ACTIVE_MEMORIES")
        val result = repository.getActiveMemoriesForUser(userId)
        if (result is Result.Success) {
            println("IronMindLifecycle Memory [LOAD_SUCCESS] userId=$userId retrievedCount=${result.data.size}")
        } else {
            println("IronMindLifecycle Memory [LOAD_FAILURE] userId=$userId error=${(result as Result.Failure).error.message}")
        }
        return result
    }
}
