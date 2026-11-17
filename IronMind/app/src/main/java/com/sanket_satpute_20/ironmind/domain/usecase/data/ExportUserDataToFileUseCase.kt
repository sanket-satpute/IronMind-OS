package com.sanket_satpute_20.ironmind.domain.usecase.data

import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.repository.UserDataExportFileWriter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Use case to produce an atomic user data export file artifact.
 *
 * Architecture & Concurrency Lifecycle (Phase 8.3.2 Stage 3A.2):
 * 1. Encloses the complete export lifecycle—from session validation through snapshot capture,
 *    temporary file writing, and final promotion—within a single continuous session read barrier
 *    via [ExportUserDataUseCase.executeInExportSession].
 * 2. Room SQLite transaction completes upon snapshot capture, while the session barrier remains
 *    held during file streaming on Dispatchers.IO.
 * 3. Erasure safety: If user erasure is initiated concurrently, it will wait for file writing
 *    and promotion to conclude before entering its destructive deletion phase.
 * 4. Yields strongly-typed ExportFileMetadata containing file path, size, and record count.
 * 5. Re-throws CancellationException directly to preserve coroutine cancellation semantics.
 */
class ExportUserDataToFileUseCase(
    private val exportUserDataUseCase: ExportUserDataUseCase,
    private val fileWriter: UserDataExportFileWriter
) {
    suspend operator fun invoke(
        requestedUserId: String? = null,
        appVersion: String? = null
    ): Result<ExportFileMetadata, Exception> {
        return try {
            exportUserDataUseCase.executeInExportSession(requestedUserId) { _, snapshot ->
                withContext(Dispatchers.IO) {
                    fileWriter.writeToFile(snapshot, appVersion)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }
}
