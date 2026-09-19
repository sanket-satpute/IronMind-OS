package com.sanket_satpute_20.ironmind.data.sync

import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.SyncState
import com.sanket_satpute_20.ironmind.domain.model.SyncStatus
import com.sanket_satpute_20.ironmind.domain.repository.OutboxRepository
import com.sanket_satpute_20.ironmind.domain.repository.SyncRepository
import com.sanket_satpute_20.ironmind.domain.repository.SyncUploadResult

/**
 * Coordinates the sync cycle:
 * 1. Read all PENDING / FAILED entries from the outbox
 * 2. For each entry, attempt remote upload via SyncRepository
 * 3. On success: delete the outbox entry (confirmed)
 * 4. On conflict: mark status CONFLICT (requires human review — no auto-overwrite)
 * 5. On failure: mark FAILED, increment retryCount (idempotent retry is safe via operationId)
 *
 * Architecture boundary: depends only on domain interfaces.
 * No Firestore types here.
 */
class SyncOrchestrator(
    private val outboxRepository: OutboxRepository,
    private val syncRepository: SyncRepository
) {
    suspend fun sync(userId: String): Result<SyncState, Exception> {
        println("IronMindLifecycle [Sync] [STARTED] userId=$userId")

        val pendingResult = outboxRepository.getPendingEntries()
        if (pendingResult is Result.Failure) {
            println("IronMindLifecycle [Sync] [FAILED] reason=outbox_read_error")
            return Result.Failure(pendingResult.error)
        }

        val entries = (pendingResult as Result.Success).data
        if (entries.isEmpty()) {
            println("IronMindLifecycle [Sync] [COMPLETED] uploaded=0 conflicts=0 failures=0")
            return Result.Success(
                SyncState(
                    isIdle = true,
                    isSyncing = false,
                    lastSyncedAt = System.currentTimeMillis(),
                    lastError = null,
                    pendingCount = 0
                )
            )
        }

        var uploaded = 0
        var conflicts = 0
        var failures = 0
        val now = System.currentTimeMillis()

        for (entry in entries) {
            // Mark as SYNCING before attempting
            outboxRepository.updateStatus(
                operationId = entry.operationId,
                status = SyncStatus.SYNCING,
                retryCount = entry.retryCount,
                lastAttemptAt = now
            )

            val uploadResult = syncRepository.upload(userId, entry)

            when {
                uploadResult is Result.Success && uploadResult.data == SyncUploadResult.Success -> {
                    outboxRepository.deleteEntry(entry.operationId)
                    uploaded++
                }
                uploadResult is Result.Success && uploadResult.data == SyncUploadResult.Conflict -> {
                    outboxRepository.updateStatus(
                        operationId = entry.operationId,
                        status = SyncStatus.CONFLICT,
                        retryCount = entry.retryCount + 1,
                        lastAttemptAt = now
                    )
                    println("IronMindLifecycle [Sync] [CONFLICT] operationId=${entry.operationId} entityType=${entry.entityType} entityId=${entry.entityId}")
                    conflicts++
                }
                else -> {
                    outboxRepository.updateStatus(
                        operationId = entry.operationId,
                        status = SyncStatus.FAILED,
                        retryCount = entry.retryCount + 1,
                        lastAttemptAt = now
                    )
                    println("IronMindLifecycle [Sync] [RETRY] operationId=${entry.operationId} retryCount=${entry.retryCount + 1}")
                    failures++
                }
            }
        }

        val pendingCountResult = outboxRepository.countPendingEntries()
        val remainingPending = (pendingCountResult as? Result.Success)?.data ?: 0

        println("IronMindLifecycle [Sync] [COMPLETED] uploaded=$uploaded conflicts=$conflicts failures=$failures")

        return Result.Success(
            SyncState(
                isIdle = remainingPending == 0,
                isSyncing = false,
                lastSyncedAt = now,
                lastError = if (failures > 0) "Some entries failed to sync" else null,
                pendingCount = remainingPending
            )
        )
    }
}
