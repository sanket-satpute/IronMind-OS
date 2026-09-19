package com.sanket_satpute_20.ironmind.domain.usecase.sync

import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.SyncState
import com.sanket_satpute_20.ironmind.data.sync.SyncOrchestrator

/**
 * Use case that triggers a manual sync cycle.
 * Delegates to SyncOrchestrator which coordinates the outbox drain.
 */
class SyncUseCase(
    private val syncOrchestrator: SyncOrchestrator
) {
    suspend operator fun invoke(userId: String): Result<SyncState, Exception> {
        return syncOrchestrator.sync(userId)
    }
}
