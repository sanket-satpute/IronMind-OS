package com.sanket_satpute_20.ironmind.data.export

import com.sanket_satpute_20.ironmind.data.local.Jdk25CompatibleRobolectricTestRunner
import com.sanket_satpute_20.ironmind.domain.ai.*
import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.*
import com.sanket_satpute_20.ironmind.domain.model.barrier.*
import com.sanket_satpute_20.ironmind.domain.model.experiment.*
import com.sanket_satpute_20.ironmind.domain.model.intervention.*
import com.sanket_satpute_20.ironmind.domain.model.observation.*
import com.sanket_satpute_20.ironmind.domain.model.pattern.*
import com.sanket_satpute_20.ironmind.domain.usecase.data.*
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.nio.charset.StandardCharsets

/**
 * Stage 3A Verification Tests:
 * 1. Valid JSON structure and root metadata.
 * 2. All 26 categories present under "data".
 * 3. Empty collections serialize as [].
 * 4. Null objective remains JSON null.
 * 5. Non-null objective is preserved.
 * 6. 1,050+ observations serialize incrementally without truncation.
 * 7. Unicode, quotes, newlines, and control characters are safely escaped.
 * 8. Deterministic serialization across identical snapshots.
 * 9. Temporary incomplete file deleted on serialization failure.
 * 10. Successful output file metadata and round-trip parseability.
 * 11. Excluded device-level tables do not appear in data.
 */
@RunWith(Jdk25CompatibleRobolectricTestRunner::class)
@Config(sdk = [34])
class UserDataExportStreamingTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var exportDir: File
    private lateinit var serializer: UserDataExportJsonSerializer
    private lateinit var fileWriter: UserDataExportFileWriter

    @Before
    fun setUp() {
        exportDir = tempFolder.newFolder("test_exports")
        serializer = UserDataExportJsonSerializer()
        fileWriter = UserDataExportFileWriter(exportDir, serializer)
    }

    @Test
    fun serializer_rootMetadataAndEmptyCollectionsContract() {
        val export = UserDataExport(
            exportVersion = 1,
            exportedAt = 1_730_000_000_000L,
            userId = "user-123",
            profile = null
        )

        val out = ByteArrayOutputStream()
        serializer.write(out, export, appVersion = "1.0-DEBUG")
        val jsonString = out.toString(StandardCharsets.UTF_8.name())

        val root = JSONObject(jsonString)
        assertEquals("ironmind-user-data", root.getString("format"))
        assertEquals(1, root.getInt("exportVersion"))
        assertEquals(1_730_000_000_000L, root.getLong("exportedAt"))
        assertEquals("user-123", root.getString("userId"))
        assertEquals("1.0-DEBUG", root.getString("appVersion"))

        assertTrue("Root must contain data object", root.has("data"))
        val data = root.getJSONObject("data")

        // User profile is null
        assertTrue("userProfile must be null", data.isNull("userProfile"))

        // All 26 categories must be present
        val expectedCategories = listOf(
            "userProfile", "goals", "plans", "tasks", "commitments",
            "outcomes", "reflections", "protectionRules", "protectionSessions",
            "events", "memories", "observations", "patterns", "autonomySettings",
            "globalAutonomyState", "appUsageObservationSettings", "interventionRecords",
            "notificationObservationSettings", "calendarObservationSettings",
            "locationObservationSettings", "activityObservationSettings",
            "experimentRecords", "barrierHypotheses", "interventionRecommendations",
            "learnerEventStatuses", "correctionReviews"
        )

        assertEquals("Exactly 26 categories must be present in data", 26, expectedCategories.size)
        for (category in expectedCategories) {
            assertTrue("Category '$category' must be present in data", data.has(category))
        }

        // Empty collections must serialize as []
        val listCategories = listOf(
            "goals", "plans", "tasks", "commitments", "outcomes", "reflections",
            "protectionRules", "protectionSessions", "events", "memories", "observations",
            "patterns", "autonomySettings", "interventionRecords", "experimentRecords",
            "barrierHypotheses", "interventionRecommendations", "learnerEventStatuses", "correctionReviews"
        )
        for (category in listCategories) {
            val array = data.getJSONArray(category)
            assertEquals("Empty collection for '$category' must serialize as []", 0, array.length())
        }

        // Verify device-level tables are NOT present in data
        assertFalse(data.has("decision_records"))
        assertFalse(data.has("notification_records"))
        assertFalse(data.has("outbox"))
    }

    @Test
    fun serializer_preservesHistoricalNullObjectiveAndNonNullObjective() {
        val rec1 = InterventionRecommendationExportRecord(
            id = "rec-null-obj",
            userId = "user-123",
            interventionType = "REMIND",
            objective = null, // Historical record
            targetEntityId = "t-1",
            targetEntityType = "TASK",
            rationale = "Pre-v21 recommendation",
            suggestedAction = "Pause work",
            status = "PENDING",
            createdAt = 1000L,
            expiresAt = 2000L,
            causalHypothesisId = null,
            causalHypothesisType = null
        )

        val rec2 = InterventionRecommendationExportRecord(
            id = "rec-with-obj",
            userId = "user-123",
            interventionType = "CHALLENGE",
            objective = "INITIATE_ACTION",
            targetEntityId = "t-2",
            targetEntityType = "TASK",
            rationale = "Action prompt",
            suggestedAction = "Start task",
            status = "ACCEPTED",
            createdAt = 1100L,
            expiresAt = 2100L,
            causalHypothesisId = "hyp-1",
            causalHypothesisType = "BARRIER"
        )

        val export = UserDataExport(
            exportVersion = 1,
            exportedAt = 1_730_000_000_000L,
            userId = "user-123",
            profile = null,
            interventionRecommendations = listOf(rec1, rec2)
        )

        val out = ByteArrayOutputStream()
        serializer.write(out, export)
        val root = JSONObject(out.toString(StandardCharsets.UTF_8.name()))
        val recs = root.getJSONObject("data").getJSONArray("interventionRecommendations")

        assertEquals(2, recs.length())

        val jsonRec1 = recs.getJSONObject(0)
        assertEquals("rec-null-obj", jsonRec1.getString("id"))
        assertTrue("Historical record objective must be JSON null", jsonRec1.isNull("objective"))
        assertEquals("REMIND", jsonRec1.getString("interventionType"))
        assertTrue("causalHypothesisId must be JSON null", jsonRec1.isNull("causalHypothesisId"))

        val jsonRec2 = recs.getJSONObject(1)
        assertEquals("rec-with-obj", jsonRec2.getString("id"))
        assertEquals("INITIATE_ACTION", jsonRec2.getString("objective"))
        assertEquals("CHALLENGE", jsonRec2.getString("interventionType"))
        assertEquals("hyp-1", jsonRec2.getString("causalHypothesisId"))
        assertEquals("BARRIER", jsonRec2.getString("causalHypothesisType"))
    }

    @Test
    fun serializer_largeObservationCollection_1050Items_streamsWithoutTruncation() {
        val largeObservations = (1..1050).map { index ->
            Observation(
                id = "obs-$index",
                userId = "user-large",
                type = ObservationType.APP_USAGE_SESSION,
                source = ObservationSource.SYSTEM,
                occurredAt = 1000L + index,
                recordedAt = 1000L + index,
                subjectId = "com.app.$index",
                value = "foreground_session",
                context = "{\"sessionDurationMs\": ${index * 1000}}",
                confidence = 0.95f,
                provenance = ObservationProvenance(
                    source = ObservationSource.SYSTEM,
                    sourceReference = "sensor-$index",
                    capturedAt = 1000L + index
                ),
                schemaVersion = 1
            )
        }

        val export = UserDataExport(
            exportVersion = 1,
            exportedAt = 1_730_000_000_000L,
            userId = "user-large",
            profile = null,
            observations = largeObservations
        )

        val out = ByteArrayOutputStream()
        serializer.write(out, export)
        val jsonString = out.toString(StandardCharsets.UTF_8.name())

        val root = JSONObject(jsonString)
        val observationsArray = root.getJSONObject("data").getJSONArray("observations")

        assertEquals(1050, observationsArray.length())
        assertEquals("obs-1", observationsArray.getJSONObject(0).getString("id"))
        assertEquals("obs-1050", observationsArray.getJSONObject(1049).getString("id"))
        assertEquals(0.95, observationsArray.getJSONObject(0).getDouble("confidence"), 0.001)
    }

    @Test
    fun serializer_safelyEscapesQuotesNewlinesControlCharactersAndUnicode() {
        val testContent = "Line 1\nLine 2\r\nLine with \"quotes\" and \t tabs and Unicode 🚀 & \u0000 control character"

        val export = UserDataExport(
            exportVersion = 1,
            exportedAt = 1_730_000_000_000L,
            userId = "user-escaping",
            profile = null,
            reflections = listOf(
                Reflection(
                    id = "ref-escape",
                    userId = "user-escaping",
                    targetEntityId = null,
                    targetEntityType = null,
                    content = testContent,
                    sentiment = "POSITIVE",
                    createdAt = 1000L,
                    schemaVersion = 1
                )
            )
        )

        val out = ByteArrayOutputStream()
        serializer.write(out, export)
        val jsonString = out.toString(StandardCharsets.UTF_8.name())

        // Round-trip parse to guarantee JSON validity
        val root = JSONObject(jsonString)
        val reflections = root.getJSONObject("data").getJSONArray("reflections")
        assertEquals(1, reflections.length())

        val recoveredContent = reflections.getJSONObject(0).getString("content")
        assertTrue("Recovered content must contain quotes", recoveredContent.contains("\"quotes\""))
        assertTrue("Recovered content must contain newline", recoveredContent.contains("\n"))
        assertTrue("Recovered content must contain emoji", recoveredContent.contains("🚀"))
    }

    @Test
    fun serializer_deterministicOutputForIdenticalSnapshots() {
        val export = UserDataExport(
            exportVersion = 1,
            exportedAt = 1_730_000_000_000L,
            userId = "user-deterministic",
            profile = UserProfile(
                id = "user-deterministic",
                createdAt = 1000L,
                updatedAt = 2000L,
                displayName = "Alex",
                timezone = "UTC",
                createdFrom = "DEVICE",
                status = "ACTIVE"
            ),
            goals = listOf(
                Goal(
                    id = "g-1",
                    userId = "user-deterministic",
                    title = "Goal 1",
                    description = "Description",
                    why = "Why",
                    importance = 5,
                    status = GoalStatus.ACTIVE,
                    createdAt = 1000L,
                    updatedAt = 2000L
                )
            )
        )

        val out1 = ByteArrayOutputStream()
        serializer.write(out1, export)
        val json1 = out1.toString(StandardCharsets.UTF_8.name())

        val out2 = ByteArrayOutputStream()
        serializer.write(out2, export)
        val json2 = out2.toString(StandardCharsets.UTF_8.name())

        assertEquals("Identical snapshots must yield bitwise identical JSON serialization", json1, json2)
    }

    @Test
    fun fileWriter_writesCompleteFileAndReturnsTypedMetadata() {
        val export = UserDataExport(
            exportVersion = 1,
            exportedAt = 1_730_000_000_000L,
            userId = "user-file-test",
            profile = null,
            goals = listOf(
                Goal(
                    id = "g-file",
                    userId = "user-file-test",
                    title = "File Goal",
                    description = "Description",
                    why = "Why",
                    importance = 3,
                    status = GoalStatus.ACTIVE,
                    createdAt = 1000L,
                    updatedAt = 2000L
                )
            )
        )

        val writeResult = fileWriter.writeToFile(export, appVersion = "1.0")
        assertTrue("Writing export to file must succeed", writeResult is Result.Success)

        val metadata = (writeResult as Result.Success).data
        assertTrue("Export file must exist", metadata.file.exists())
        assertEquals("ironmind_export_user-file-test_1730000000000.json", metadata.fileName)
        assertTrue("File size must be greater than zero", metadata.sizeBytes > 0)
        assertEquals(1, metadata.recordCount)
        assertEquals("ironmind-user-data", metadata.format)
        assertEquals(1, metadata.exportVersion)
        assertEquals("user-file-test", metadata.userId)

        // Read file contents back and parse
        val content = metadata.file.readText(StandardCharsets.UTF_8)
        val parsed = JSONObject(content)
        assertEquals("ironmind-user-data", parsed.getString("format"))
        assertEquals("user-file-test", parsed.getString("userId"))
        val goals = parsed.getJSONObject("data").getJSONArray("goals")
        assertEquals(1, goals.length())
        assertEquals("g-file", goals.getJSONObject(0).getString("id"))

        // Ensure no temporary files (.tmp) remain in the directory
        val tmpFiles = exportDir.listFiles { _, name -> name.endsWith(".tmp") } ?: emptyArray()
        assertEquals("No temporary files should linger after successful export", 0, tmpFiles.size)
    }

    @Test
    fun fileWriter_onSerializationFailure_deletesIncompleteFile() {
        val failingSerializer = object : UserDataExportJsonSerializer() {
            override fun write(outputStream: OutputStream, export: UserDataExport, appVersion: String?) {
                outputStream.write("{\"format\": \"partial".toByteArray(StandardCharsets.UTF_8))
                throw IOException("Simulated disk error or out-of-memory during streaming")
            }
        }

        val failingFileWriter = UserDataExportFileWriter(exportDir, failingSerializer)

        val export = UserDataExport(
            exportVersion = 1,
            exportedAt = 1_730_000_000_123L,
            userId = "user-fail",
            profile = null
        )

        val result = failingFileWriter.writeToFile(export)
        assertTrue("Simulated failure must return Result.Failure", result is Result.Failure)
        assertTrue((result as Result.Failure).error is IOException)

        val remainingFiles = exportDir.listFiles() ?: emptyArray()
        assertEquals("No temporary or corrupted files should exist after serialization failure", 0, remainingFiles.size)
    }

    @Test
    fun exportUserDataToFileUseCase_coordinatesSnapshotAndStreamingFileWriter() = kotlinx.coroutines.runBlocking {
        val userA = "user-usecase-test"
        val mockSnapshot = UserDataExport(
            exportVersion = 1,
            exportedAt = 1_730_000_500_000L,
            userId = userA,
            profile = null,
            goals = listOf(
                Goal(
                    id = "g-coordinated",
                    userId = userA,
                    title = "Coordinated Goal",
                    description = "Test Description",
                    why = "Test Why",
                    importance = 4,
                    status = GoalStatus.ACTIVE,
                    createdAt = 1000L,
                    updatedAt = 2000L
                )
            )
        )

        val mockRepo = object : com.sanket_satpute_20.ironmind.domain.repository.DataExportRepository {
            override suspend fun captureUserSnapshot(userId: String): Result<UserDataExport, Exception> {
                return Result.Success(mockSnapshot)
            }
        }

        val snapshotUseCase = ExportUserDataUseCase(
            dataExportRepository = mockRepo,
            userSessionProvider = null
        )

        val toFileUseCase = ExportUserDataToFileUseCase(
            exportUserDataUseCase = snapshotUseCase,
            fileWriter = fileWriter
        )

        val fileResult = toFileUseCase(requestedUserId = userA, appVersion = "1.0-STAGING")
        assertTrue("File export use case must succeed", fileResult is Result.Success)

        val metadata = (fileResult as Result.Success).data
        assertTrue(metadata.file.exists())
        assertEquals(1, metadata.recordCount)
        assertEquals("ironmind-user-data", metadata.format)

        val parsed = JSONObject(metadata.file.readText(StandardCharsets.UTF_8))
        assertEquals("1.0-STAGING", parsed.getString("appVersion"))
        assertEquals(userA, parsed.getString("userId"))
        assertEquals(1, parsed.getJSONObject("data").getJSONArray("goals").length())
    }
}
