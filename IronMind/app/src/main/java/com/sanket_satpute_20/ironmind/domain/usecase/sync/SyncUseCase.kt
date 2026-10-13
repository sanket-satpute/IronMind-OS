package com.sanket_satpute_20.ironmind.domain.usecase.sync

import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.SyncState

/**
 * Domain orchestrator interface for coordinating remote sync cycles.
 * Inverts the dependency so domain use cases do not depend on data-layer classes.
 */
interface SyncOrchestrator {
    suspend fun sync(userId: String): Result<SyncState, Exception>
}

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
