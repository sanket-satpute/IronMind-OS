package com.sanket_satpute_20.ironmind.data.export

import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.repository.UserDataExportCacheManager
import java.io.File
import java.io.IOException
import java.util.regex.Pattern

// Retain typealias for data-layer and test backwards-compatibility
typealias UserDataExportCacheManager = com.sanket_satpute_20.ironmind.domain.repository.UserDataExportCacheManager

/**
 * Default implementation of [UserDataExportCacheManager] operating on a dedicated exports directory.
 */
class DefaultUserDataExportCacheManager(
    val exportDirectory: File
) : UserDataExportCacheManager {

    init {
        if (!exportDirectory.exists()) {
            exportDirectory.mkdirs()
        }
    }

    companion object {
        fun buildCompletedFileRegex(userId: String): Regex {
            val sanitized = userId.replace(Regex("[^a-zA-Z0-9_-]"), "_")
            return Regex("^ironmind_export_${Pattern.quote(sanitized)}_(\\d+)\\.json$")
        }

        fun buildUserFileRegex(userId: String): Regex {
            val sanitized = userId.replace(Regex("[^a-zA-Z0-9_-]"), "_")
            return Regex("^(ironmind_export|temp_export|promotion_export)_${Pattern.quote(sanitized)}_(\\d+)(_[a-zA-Z0-9_-]+)?\\.(json|tmp)$")
        }
    }

    override fun purgeUserExports(userId: String): Result<Int, Exception> {
        if (!exportDirectory.exists() || !exportDirectory.isDirectory) {
            return Result.Success(0)
        }

        val userFilePattern = buildUserFileRegex(userId)
        val files = exportDirectory.listFiles() ?: return Result.Success(0)

        var deletedCount = 0
        for (file in files) {
            if (userFilePattern.matches(file.name)) {
                val deleted = file.delete()
                if (!deleted && file.exists()) {
                    return Result.Failure(IOException("Failed to delete user export file during erasure: ${file.name}"))
                }
                deletedCount++
            }
        }

        return Result.Success(deletedCount)
    }

    override fun enforceRetention(userId: String, keepCount: Int): Result<Int, Exception> {
        if (!exportDirectory.exists() || !exportDirectory.isDirectory) {
            return Result.Success(0)
        }

        val completedPattern = buildCompletedFileRegex(userId)
        val userFiles = exportDirectory.listFiles() ?: return Result.Success(0)

        // Find all completed files for this user and extract their timestamps
        val completedExports = userFiles.mapNotNull { file ->
            val match = completedPattern.matchEntire(file.name)
            if (match != null) {
                val timestamp = match.groupValues[1].toLongOrNull() ?: 0L
                Pair(file, timestamp)
            } else {
                null
            }
        }.sortedByDescending { it.second } // Most recent first

        var prunedCount = 0

        // Keep the latest `keepCount` exports, delete any older ones
        if (completedExports.size > keepCount) {
            val toDelete = completedExports.drop(keepCount)
            for ((file, _) in toDelete) {
                if (file.exists() && file.delete()) {
                    prunedCount++
                }
            }
        }

        // Clean up any stray temporary files belonging to this user
        val tempPattern = Regex("^(temp_export|promotion_export)_${Pattern.quote(userId.replace(Regex("[^a-zA-Z0-9_-]"), "_"))}_.*\\.tmp$")
        for (file in userFiles) {
            if (tempPattern.matches(file.name) && file.exists()) {
                if (file.delete()) {
                    prunedCount++
                }
            }
        }

        return Result.Success(prunedCount)
    }

    override fun cleanStaleTempFiles(maxAgeMs: Long): Result<Int, Exception> {
        if (!exportDirectory.exists() || !exportDirectory.isDirectory) {
            return Result.Success(0)
        }

        val files = exportDirectory.listFiles() ?: return Result.Success(0)
        val now = System.currentTimeMillis()
        var cleanedCount = 0

        for (file in files) {
            if (file.name.endsWith(".tmp") && (now - file.lastModified() > maxAgeMs)) {
                if (file.delete()) {
                    cleanedCount++
                }
            }
        }

        return Result.Success(cleanedCount)
    }
}
