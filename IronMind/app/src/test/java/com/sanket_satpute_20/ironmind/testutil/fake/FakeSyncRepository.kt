package com.sanket_satpute_20.ironmind.testutil.fake

import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.OutboxEntry
import com.sanket_satpute_20.ironmind.domain.repository.SyncRepository
import com.sanket_satpute_20.ironmind.domain.repository.SyncUploadResult

class FakeSyncRepository : SyncRepository {
    val uploadedEntries = mutableListOf<OutboxEntry>()
    val conflictOperationIds = mutableSetOf<String>()
    val failingOperationIds = mutableSetOf<String>()

    override suspend fun upload(userId: String, entry: OutboxEntry): Result<SyncUploadResult, Exception> {
        return when {
            entry.operationId in failingOperationIds -> {
                Result.Failure(Exception("Simulated network failure for ${entry.operationId}"))
            }
            entry.operationId in conflictOperationIds -> {
                Result.Success(SyncUploadResult.Conflict)
            }
            else -> {
                uploadedEntries.add(entry)
                Result.Success(SyncUploadResult.Success)
            }
        }
    }
}
