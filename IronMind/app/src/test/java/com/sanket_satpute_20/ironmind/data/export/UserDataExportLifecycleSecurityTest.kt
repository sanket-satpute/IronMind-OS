package com.sanket_satpute_20.ironmind.data.export

import com.sanket_satpute_20.ironmind.data.local.Jdk25CompatibleRobolectricTestRunner
import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.Goal
import com.sanket_satpute_20.ironmind.domain.model.GoalStatus
import com.sanket_satpute_20.ironmind.domain.repository.AuthRepository
import com.sanket_satpute_20.ironmind.domain.repository.DataExportRepository
import com.sanket_satpute_20.ironmind.domain.repository.DataManagementRepository
import com.sanket_satpute_20.ironmind.domain.session.ReconciliationResult
import com.sanket_satpute_20.ironmind.domain.session.SessionState
import com.sanket_satpute_20.ironmind.domain.session.UserSessionProvider
import com.sanket_satpute_20.ironmind.domain.usecase.data.DeleteUserDataUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.data.ExportUserDataToFileUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.data.ExportUserDataUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.data.UserDataExport
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.nio.charset.StandardCharsets

/**
 * Phase 8.3.2 Stage 3A.2 Comprehensive Lifecycle Security & Erasure Safety Regression Suite:
 *
 * 1. Promotion & Partial File Exposure Safety
 * 2. Coroutine Cancellation Cleanup & Propagation
 * 3. Session Barrier Lifetime & Erasure Synchronization
 * 4. Cache Retention & Multi-User Isolation
 * 5. Erasure Cache Purging & Multi-User Scoping
 */
@RunWith(Jdk25CompatibleRobolectricTestRunner::class)
@Config(sdk = [34])
class UserDataExportLifecycleSecurityTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var exportDir: File
    private lateinit var cacheManager: DefaultUserDataExportCacheManager
    private lateinit var serializer: UserDataExportJsonSerializer
    private lateinit var fileWriter: UserDataExportFileWriter

    @Before
    fun setUp() {
        exportDir = tempFolder.newFolder("lifecycle_exports")
        cacheManager = DefaultUserDataExportCacheManager(exportDir)
        serializer = UserDataExportJsonSerializer()
        fileWriter = UserDataExportFileWriter(exportDir, serializer, cacheManager)
    }

    private fun createDummyExport(userId: String, timestamp: Long): UserDataExport {
        return UserDataExport(
            exportVersion = 1,
            exportedAt = timestamp,
            userId = userId,
            profile = null,
            goals = listOf(
                Goal(
                    id = "g-$timestamp",
                    userId = userId,
                    title = "Goal $timestamp",
                    description = "Description",
                    why = "Why",
                    importance = 3,
                    status = GoalStatus.ACTIVE,
                    createdAt = timestamp,
                    updatedAt = timestamp
                )
            )
        )
    }

    // =========================================================================
    // 1. PROMOTION & PARTIAL FILE EXPOSURE TESTS
    // =========================================================================

    @Test
    fun fileWriter_existingValidExport_preservedWhenNewExportFails() {
        val userId = "user-preserve"
        val initialExport = createDummyExport(userId, 1000L)

        // Write first successful export
        val firstResult = fileWriter.writeToFile(initialExport)
        assertTrue(firstResult is Result.Success)
        val originalFile = (firstResult as Result.Success).data.file
        assertTrue(originalFile.exists())
        val originalLength = originalFile.length()

        // Create failing file writer for second export
        val failingSerializer = object : UserDataExportJsonSerializer() {
            override fun write(outputStream: OutputStream, export: UserDataExport, appVersion: String?) {
                outputStream.write("{\"incomplete".toByteArray(StandardCharsets.UTF_8))
                throw IOException("Disk write failure during replacement export")
            }
        }
        val failingWriter = UserDataExportFileWriter(exportDir, failingSerializer, cacheManager)

        val secondExport = createDummyExport(userId, 2000L)
        val secondResult = failingWriter.writeToFile(secondExport)

        assertTrue(secondResult is Result.Failure)

        // Original valid export at 1000L MUST still exist and be intact
        assertTrue("Previous valid export must remain intact after a failed replacement export", originalFile.exists())
        assertEquals(originalLength, originalFile.length())

        // No new corrupted 2000L export file should exist
        val secondFile = File(exportDir, "ironmind_export_${userId}_2000.json")
        assertFalse("Incomplete replacement export must not exist on disk", secondFile.exists())
    }

    @Test
    fun fileWriter_diskFullDuringSerialization_purgesAllTempFilesAndLeavesNoIncompleteJson() {
        val failingSerializer = object : UserDataExportJsonSerializer() {
            override fun write(outputStream: OutputStream, export: UserDataExport, appVersion: String?) {
                outputStream.write(ByteArray(5000) { 1 })
                throw IOException("ENOSPC: No space left on device")
            }
        }
        val failingWriter = UserDataExportFileWriter(exportDir, failingSerializer, cacheManager)

        val export = createDummyExport("user-disk-full", 3000L)
        val result = failingWriter.writeToFile(export)

        assertTrue(result is Result.Failure)
        assertTrue((result as Result.Failure).error is IOException)

        // Assert no temp, promotion, or final files exist in the directory
        val allFiles = exportDir.listFiles() ?: emptyArray()
        assertEquals("No temporary or partial files should exist after disk full failure", 0, allFiles.size)
    }

    // =========================================================================
    // 2. COROUTINE CANCELLATION TESTS
    // =========================================================================

    @Test
    fun fileWriter_cancellationDuringSerialization_cleansUpTempFilesAndRethrowsCancellationException() {
        val cancellingSerializer = object : UserDataExportJsonSerializer() {
            override fun write(outputStream: OutputStream, export: UserDataExport, appVersion: String?) {
                outputStream.write("{\"format\": \"partial".toByteArray(StandardCharsets.UTF_8))
                throw CancellationException("User cancelled export job")
            }
        }
        val writer = UserDataExportFileWriter(exportDir, cancellingSerializer, cacheManager)
        val export = createDummyExport("user-cancel", 4000L)

        try {
            writer.writeToFile(export)
            fail("writeToFile must rethrow CancellationException")
        } catch (e: CancellationException) {
            assertEquals("User cancelled export job", e.message)
        }

        // Assert cleanup
        val allFiles = exportDir.listFiles() ?: emptyArray()
        assertEquals("Temporary files must be deleted on cancellation", 0, allFiles.size)
    }

    @Test
    fun useCase_cancellationDuringExport_releasesSingleFlightGuardAndRethrows() = runBlocking {
        val userId = "user-singleflight-cancel"
        val mockRepo = object : DataExportRepository {
            override suspend fun captureUserSnapshot(userId: String): Result<UserDataExport, Exception> {
                throw CancellationException("Coroutine scope cancelled")
            }
        }

        val snapshotUseCase = ExportUserDataUseCase(dataExportRepository = mockRepo)
        val fileUseCase = ExportUserDataToFileUseCase(snapshotUseCase, fileWriter)

        try {
            fileUseCase(requestedUserId = userId)
            fail("Expected CancellationException to be rethrown")
        } catch (e: CancellationException) {
            assertEquals("Coroutine scope cancelled", e.message)
        }

        // Single-flight guard MUST be released in finally block: subsequent invocation must succeed
        val successfulRepo = object : DataExportRepository {
            override suspend fun captureUserSnapshot(userId: String): Result<UserDataExport, Exception> {
                return Result.Success(createDummyExport(userId, 5000L))
            }
        }
        val subsequentUseCase = ExportUserDataToFileUseCase(
            ExportUserDataUseCase(successfulRepo),
            fileWriter
        )

        val subsequentResult = subsequentUseCase(requestedUserId = userId)
        assertTrue("Subsequent export call must succeed after cancelled call releases guard", subsequentResult is Result.Success)
    }

    // =========================================================================
    // 3. RETENTION POLICY TESTS
    // =========================================================================

    @Test
    fun retention_retainsOnlyLatestCompletedExportPerUser_andPreservesOtherUsers() {
        val userA = "user-retention-A"
        val userB = "user-retention-B"

        // User A exports at t=1000
        val resA1 = fileWriter.writeToFile(createDummyExport(userA, 1000L))
        assertTrue(resA1 is Result.Success)

        // User B exports at t=1500
        val resB = fileWriter.writeToFile(createDummyExport(userB, 1500L))
        assertTrue(resB is Result.Success)

        // User A exports at t=2000
        val resA2 = fileWriter.writeToFile(createDummyExport(userA, 2000L))
        assertTrue(resA2 is Result.Success)

        val allFiles = exportDir.listFiles()?.map { it.name }?.toSet() ?: emptySet()

        // User A's t=1000 export must be pruned; User A's t=2000 must remain
        assertFalse("User A older export (1000) must be pruned by retention", allFiles.contains("ironmind_export_${userA}_1000.json"))
        assertTrue("User A newest export (2000) must be retained", allFiles.contains("ironmind_export_${userA}_2000.json"))

        // User B's export must remain untouched!
        assertTrue("User B export must not be pruned by User A retention", allFiles.contains("ironmind_export_${userB}_1500.json"))
        assertEquals(2, allFiles.size)
    }

    // =========================================================================
    // 4. ERASURE CACHE PURGING TESTS
    // =========================================================================

    @Test
    fun erasure_purgesAllUserAExportFiles_preservesUserBFiles() = runBlocking {
        val userA = "user-erasure-A"
        val userB = "user-erasure-B"

        // Create completed and temp files for User A
        File(exportDir, "ironmind_export_${userA}_1000.json").writeText("{}")
        File(exportDir, "ironmind_export_${userA}_2000.json").writeText("{}")
        File(exportDir, "temp_export_${userA}_3000_abc123.tmp").writeText("temp")
        File(exportDir, "promotion_export_${userA}_3000_def456.tmp").writeText("promo")

        // Create completed file for User B
        File(exportDir, "ironmind_export_${userB}_1500.json").writeText("{}")

        // Fake DataManagementRepository
        val mockDataManagementRepo = object : DataManagementRepository {
            override suspend fun deleteAllUserData(userId: String): Result<Unit, Exception> = Result.Success(Unit)
            override suspend fun cleanupExpiredPatterns(userId: String, thresholdTime: Long): Result<Unit, Exception> = Result.Success(Unit)
            override suspend fun cleanupForgottenMemories(userId: String, thresholdTime: Long): Result<Unit, Exception> = Result.Success(Unit)
            override fun cancelAllUserWorkers() {}
        }

        val deleteUseCase = DeleteUserDataUseCase(
            dataManagementRepository = mockDataManagementRepo,
            userSessionProvider = null,
            authRepository = null,
            exportCacheManager = cacheManager
        )

        val erasureResult = deleteUseCase(userA)
        assertTrue("Erasure of User A must succeed", erasureResult is Result.Success)

        val remainingFiles = exportDir.listFiles()?.map { it.name }?.toSet() ?: emptySet()

        // Assert all User A files were deleted
        assertFalse(remainingFiles.contains("ironmind_export_${userA}_1000.json"))
        assertFalse(remainingFiles.contains("ironmind_export_${userA}_2000.json"))
        assertFalse(remainingFiles.contains("temp_export_${userA}_3000_abc123.tmp"))
        assertFalse(remainingFiles.contains("promotion_export_${userA}_3000_def456.tmp"))

        // Assert User B file remains intact
        assertTrue("User B export must remain untouched after User A erasure", remainingFiles.contains("ironmind_export_${userB}_1500.json"))
        assertEquals(1, remainingFiles.size)
    }

    @Test
    fun erasure_surfacesFailureIfExportFileCannotBeDeleted() = runBlocking {
        val userA = "user-locked"

        // Mock cache manager where deletion fails
        val failingCacheManager = object : UserDataExportCacheManager {
            override fun purgeUserExports(userId: String): Result<Int, Exception> {
                return Result.Failure(IOException("OS file lock prevents deletion"))
            }
            override fun enforceRetention(userId: String, keepCount: Int): Result<Int, Exception> = Result.Success(0)
            override fun cleanStaleTempFiles(maxAgeMs: Long): Result<Int, Exception> = Result.Success(0)
        }

        val mockDataManagementRepo = object : DataManagementRepository {
            override suspend fun deleteAllUserData(userId: String): Result<Unit, Exception> = Result.Success(Unit)
            override suspend fun cleanupExpiredPatterns(userId: String, thresholdTime: Long): Result<Unit, Exception> = Result.Success(Unit)
            override suspend fun cleanupForgottenMemories(userId: String, thresholdTime: Long): Result<Unit, Exception> = Result.Success(Unit)
            override fun cancelAllUserWorkers() {}
        }

        val deleteUseCase = DeleteUserDataUseCase(
            dataManagementRepository = mockDataManagementRepo,
            userSessionProvider = null,
            authRepository = null,
            exportCacheManager = failingCacheManager
        )

        val erasureResult = deleteUseCase(userA)
        assertTrue("Erasure must fail when export file deletion fails", erasureResult is Result.Failure)
        assertTrue((erasureResult as Result.Failure).error.message!!.contains("Failed to purge user export files during data erasure"))
    }

    // =========================================================================
    // 5. SESSION BARRIER & ERASURE SYNCHRONIZATION TESTS
    // =========================================================================

    @Test
    fun sessionBarrier_holdsReadBarrierDuringFileWriting_blockingErasureUntilComplete() = runBlocking {
        val userId = "user-barrier-sync"
        val barrier = com.sanket_satpute_20.ironmind.data.session.SessionReadWriteBarrier()

        // Coordinating session provider that uses the real SessionReadWriteBarrier
        val coordinatingSessionProvider = object : UserSessionProvider {
            val state = MutableStateFlow<SessionState>(SessionState.Authenticated(userId, isAnonymous = false))
            override val sessionState: StateFlow<SessionState> = state
            override fun getActiveUserId(): String = userId
            override fun getSessionGeneration(): Long = 1L
            override fun markUserErasing(userId: String, generation: Long) {}
            override fun markUserErased(userId: String, generation: Long) {}
            override fun clearUserErasing(userId: String, generation: Long) {}
            override fun isUserErased(userId: String, generation: Long): Boolean = false
            override fun permitOfflineFallback() {}
            override suspend fun retryReconciliation(): Result<ReconciliationResult, Exception> = Result.Failure(IllegalStateException())

            override suspend fun <T> withUserSession(action: suspend (userId: String) -> T): T {
                return barrier.withReadAccess { action(userId) }
            }
            override suspend fun <T> withExclusiveAccess(action: suspend (userId: String) -> T): T {
                return barrier.withWriteAccess { action(userId) }
            }
            override suspend fun <T> withExclusiveAccess(drainTimeoutMs: Long, action: suspend (userId: String) -> T): T {
                return barrier.withWriteAccess(drainTimeoutMs) { action(userId) }
            }
        }

        val exportStarted = CompletableDeferred<Unit>()
        val exportCanFinish = CompletableDeferred<Unit>()
        var erasureExecutedWhileExportRunning = false

        // Custom slow serializer that holds the export in-flight
        val slowSerializer = object : UserDataExportJsonSerializer() {
            override fun write(outputStream: OutputStream, export: UserDataExport, appVersion: String?) {
                outputStream.write("{\"format\":\"ironmind-user-data\"}".toByteArray(StandardCharsets.UTF_8))
                exportStarted.complete(Unit)
                // Block until test releases export
                runBlocking { exportCanFinish.await() }
            }
        }
        val slowWriter = UserDataExportFileWriter(exportDir, slowSerializer, cacheManager)

        val mockExportRepo = object : DataExportRepository {
            override suspend fun captureUserSnapshot(userId: String): Result<UserDataExport, Exception> {
                return Result.Success(createDummyExport(userId, 6000L))
            }
        }

        val exportUseCase = ExportUserDataUseCase(mockExportRepo, coordinatingSessionProvider)
        val exportToFileUseCase = ExportUserDataToFileUseCase(exportUseCase, slowWriter)

        val mockDataManagementRepo = object : DataManagementRepository {
            override suspend fun deleteAllUserData(userId: String): Result<Unit, Exception> {
                if (exportCanFinish.isActive) {
                    erasureExecutedWhileExportRunning = true
                }
                return Result.Success(Unit)
            }
            override suspend fun cleanupExpiredPatterns(userId: String, thresholdTime: Long): Result<Unit, Exception> = Result.Success(Unit)
            override suspend fun cleanupForgottenMemories(userId: String, thresholdTime: Long): Result<Unit, Exception> = Result.Success(Unit)
            override fun cancelAllUserWorkers() {}
        }

        val deleteUseCase = DeleteUserDataUseCase(
            dataManagementRepository = mockDataManagementRepo,
            userSessionProvider = coordinatingSessionProvider,
            exportCacheManager = cacheManager,
            drainTimeoutMs = 5000L
        )

        // Launch in-flight export in coroutine
        val exportJob = async(Dispatchers.IO) {
            exportToFileUseCase(requestedUserId = userId)
        }

        // Wait until export has acquired read barrier and is actively serializing to disk
        exportStarted.await()

        // Launch erasure concurrently
        val erasureJob = async(Dispatchers.IO) {
            deleteUseCase(userId)
        }

        // Give erasure a moment to attempt write barrier access; verify it is suspended waiting for reader drain
        delay(100)
        assertFalse("Erasure must NOT execute while export holds the read barrier", erasureExecutedWhileExportRunning)

        // Now allow export to finish
        exportCanFinish.complete(Unit)

        val exportResult = exportJob.await()
        assertTrue("Export must finish successfully", exportResult is Result.Success)

        val erasureResult = erasureJob.await()
        assertTrue("Erasure must complete after export releases read barrier", erasureResult is Result.Success)
        assertFalse("Erasure must never execute while export was running", erasureExecutedWhileExportRunning)
    }

    @Test
    fun sessionBarrier_exportCannotStartAfterErasureAdmissionCloses() = runBlocking {
        val userId = "user-erasing-blocked"

        val provider = object : UserSessionProvider {
            val state = MutableStateFlow<SessionState>(SessionState.Authenticated(userId, isAnonymous = false))
            override val sessionState: StateFlow<SessionState> = state
            override fun getActiveUserId(): String = userId
            override fun getSessionGeneration(): Long = 1L
            override fun markUserErasing(userId: String, generation: Long) {}
            override fun markUserErased(userId: String, generation: Long) {}
            override fun clearUserErasing(userId: String, generation: Long) {}
            // Simulate admission fence closed for erasure
            override fun isUserErased(userId: String, generation: Long): Boolean = true
            override fun permitOfflineFallback() {}
            override suspend fun retryReconciliation(): Result<ReconciliationResult, Exception> = Result.Failure(IllegalStateException())
            override suspend fun <T> withUserSession(action: suspend (userId: String) -> T): T = action(userId)
            override suspend fun <T> withExclusiveAccess(action: suspend (userId: String) -> T): T = action(userId)
            override suspend fun <T> withExclusiveAccess(drainTimeoutMs: Long, action: suspend (userId: String) -> T): T = action(userId)
        }

        val mockRepo = object : DataExportRepository {
            override suspend fun captureUserSnapshot(userId: String): Result<UserDataExport, Exception> {
                return Result.Success(createDummyExport(userId, 7000L))
            }
        }

        val exportUseCase = ExportUserDataUseCase(mockRepo, provider)
        val exportToFileUseCase = ExportUserDataToFileUseCase(exportUseCase, fileWriter)

        val result = exportToFileUseCase(requestedUserId = userId)
        assertTrue("Export must be rejected when admission fence is closed for erasure", result is Result.Failure)
        assertTrue((result as Result.Failure).error is IllegalStateException)
        assertTrue(result.error.message!!.contains("Cannot export data for erased user"))
    }
}

