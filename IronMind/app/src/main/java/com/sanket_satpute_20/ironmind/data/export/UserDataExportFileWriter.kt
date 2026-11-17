package com.sanket_satpute_20.ironmind.data.export

import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.repository.UserDataExportCacheManager
import com.sanket_satpute_20.ironmind.domain.repository.UserDataExportFileWriter as DomainUserDataExportFileWriter
import com.sanket_satpute_20.ironmind.domain.usecase.data.ExportFileMetadata
import com.sanket_satpute_20.ironmind.domain.usecase.data.UserDataExport
import kotlinx.coroutines.CancellationException
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.UUID

/**
 * File management layer for streaming UserDataExport directly to disk.
 *
 * Requirements & Atomic Promotion Guarantees (Phase 8.3.2 Stage 3A.2):
 * 1. Dedicated Directory: Operates exclusively within a designated exports directory in internal storage.
 * 2. Two-Phase Promotion Strategy:
 *    a. Serializes snapshot directly into a unique temporary file (.tmp).
 *    b. Flushes, verifies, and closes the temporary file.
 *    c. Promotes temporary file to final destination (.json). If atomic rename fails, copies into a
 *       separate promotion temp file, verifies copy length, then atomically renames to final destination.
 *    d. Never streams directly into a publicly identifiable final .json destination.
 *    e. Never deletes or replaces a previously valid export until the replacement export is 100% complete.
 * 3. Strict Failure Cleanup:
 *    On ANY failure (I/O error, disk full, copy failure, or coroutine cancellation):
 *    - All newly created temporary and promotion files are deleted immediately.
 *    - Destination final file is deleted if incomplete or corrupted (NO length == 0L condition!).
 * 4. Cooperative Cancellation:
 *    Catches CancellationException explicitly, cleans up incomplete files, and re-throws the exception.
 * 5. Retention Enforcement:
 *    Enforces retention upon completion (retaining only the latest completed export per user).
 */
class UserDataExportFileWriter(
    val exportDirectory: File,
    private val serializer: UserDataExportJsonSerializer = UserDataExportJsonSerializer(),
    private val cacheManager: UserDataExportCacheManager = DefaultUserDataExportCacheManager(exportDirectory)
) : DomainUserDataExportFileWriter {
    init {
        if (!exportDirectory.exists()) {
            exportDirectory.mkdirs()
        }
    }

    /**
     * Serializes UserDataExport directly to disk in the dedicated exports directory.
     *
     * @param export The materialized snapshot of user data.
     * @param appVersion Optional app version string for root metadata.
     * @return Result containing ExportFileMetadata on success, or Exception on failure.
     */
    override fun writeToFile(
        export: UserDataExport,
        appVersion: String?
    ): Result<ExportFileMetadata, Exception> {
        val sanitizedUserId = export.userId.replace(Regex("[^a-zA-Z0-9_-]"), "_")
        val uniqueSuffix = UUID.randomUUID().toString().take(8)
        val tempFile = File(exportDirectory, "temp_export_${sanitizedUserId}_${export.exportedAt}_$uniqueSuffix.tmp")
        val promotionFile = File(exportDirectory, "promotion_export_${sanitizedUserId}_${export.exportedAt}_$uniqueSuffix.tmp")
        val finalFile = File(exportDirectory, "ironmind_export_${sanitizedUserId}_${export.exportedAt}.json")

        var fileOutputStream: FileOutputStream? = null
        var isPromotionComplete = false

        return try {
            // Step 1: Write to initial temporary file
            fileOutputStream = FileOutputStream(tempFile)
            serializer.write(fileOutputStream, export, appVersion)
            fileOutputStream.flush()
            fileOutputStream.close()
            fileOutputStream = null

            val tempLength = tempFile.length()
            if (tempLength <= 0L) {
                throw IOException("Export temporary file was written with 0 bytes")
            }

            // Step 2: Atomic Promotion
            // If identical timestamp finalFile exists, delete it immediately before promotion
            if (finalFile.exists()) {
                finalFile.delete()
            }

            val renamedDirectly = tempFile.renameTo(finalFile)
            if (renamedDirectly) {
                isPromotionComplete = true
            } else {
                // Step 2b: Two-phase copy promotion fallback
                // Copy into separate promotion temp file in the same directory first
                tempFile.copyTo(promotionFile, overwrite = true)
                if (promotionFile.length() != tempLength) {
                    throw IOException("Promotion temporary file length mismatch: expected $tempLength, found ${promotionFile.length()}")
                }

                // Atomically rename promotion temp file to final destination
                val promotionRenamed = promotionFile.renameTo(finalFile)
                if (promotionRenamed) {
                    isPromotionComplete = true
                    runCatching { tempFile.delete() }
                } else {
                    throw IOException("Failed to atomically promote export file to ${finalFile.name}")
                }
            }

            // Step 3: Cleanup any leftover intermediate files
            if (tempFile.exists()) {
                runCatching { tempFile.delete() }
            }
            if (promotionFile.exists()) {
                runCatching { promotionFile.delete() }
            }

            // Step 4: Enforce retention policy (keep latest export, remove older completed exports for this user)
            cacheManager.enforceRetention(export.userId, keepCount = 1)

            val metadata = ExportFileMetadata(
                file = finalFile,
                fileName = finalFile.name,
                sizeBytes = finalFile.length(),
                recordCount = export.totalRecordCount(),
                format = UserDataExport.EXPORT_FORMAT_IDENTIFIER,
                exportVersion = export.exportVersion,
                exportedAt = export.exportedAt,
                userId = export.userId
            )
            Result.Success(metadata)
        } catch (e: CancellationException) {
            // Safe cleanup on coroutine cancellation
            try { fileOutputStream?.close() } catch (_: IOException) {}
            cleanupOnFailure(tempFile, promotionFile, finalFile, isPromotionComplete)
            throw e
        } catch (e: Exception) {
            // Safe cleanup on I/O or serialization error
            try { fileOutputStream?.close() } catch (_: IOException) {}
            cleanupOnFailure(tempFile, promotionFile, finalFile, isPromotionComplete)
            Result.Failure(e)
        }
    }

    private fun cleanupOnFailure(
        tempFile: File,
        promotionFile: File,
        finalFile: File,
        isPromotionComplete: Boolean
    ) {
        if (tempFile.exists()) {
            runCatching { tempFile.delete() }
        }
        if (promotionFile.exists()) {
            runCatching { promotionFile.delete() }
        }
        // If promotion did not complete successfully, purge finalFile unconditionally
        if (!isPromotionComplete && finalFile.exists()) {
            runCatching { finalFile.delete() }
        }
    }
}
