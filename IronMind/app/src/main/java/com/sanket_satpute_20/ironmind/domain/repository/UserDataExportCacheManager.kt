package com.sanket_satpute_20.ironmind.domain.repository

import com.sanket_satpute_20.ironmind.domain.common.Result

/**
 * Abstraction responsible for managing the lifecycle, retention, and erasure
 * of user export files within the app's private cache directory.
 */
interface UserDataExportCacheManager {
    /**
     * Purges all completed, temporary, and promotion export files belonging strictly to [userId].
     * Never deletes files belonging to other users or unrelated cache contents.
     *
     * @return Result containing the count of deleted files on success, or an Exception if any file could not be deleted.
     */
    fun purgeUserExports(userId: String): Result<Int, Exception>

    /**
     * Enforces the retention policy for [userId].
     * Retains the latest [keepCount] completed exports and deletes older completed exports and temporary files.
     *
     * @return Result containing the count of pruned files on success.
     */
    fun enforceRetention(userId: String, keepCount: Int = 1): Result<Int, Exception>

    /**
     * Deletes orphaned temporary and promotion files older than [maxAgeMs].
     */
    fun cleanStaleTempFiles(maxAgeMs: Long = 3_600_000L): Result<Int, Exception>
}
