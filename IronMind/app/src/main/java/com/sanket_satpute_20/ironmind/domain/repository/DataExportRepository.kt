package com.sanket_satpute_20.ironmind.domain.repository

import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.usecase.data.UserDataExport

/**
 * Repository interface providing atomic, transactional snapshots of all user-scoped data.
 */
interface DataExportRepository {
    /**
     * Executes an atomic, transactional snapshot of all 26 user-owned Room entities
     * for the specified [userId].
     *
     * Runs inside a single database transaction. Fails fast if any single query fails.
     */
    suspend fun captureUserSnapshot(userId: String): Result<UserDataExport, Exception>
}
