package com.sanket_satpute_20.ironmind.domain.usecase.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.sanket_satpute_20.ironmind.data.export.UserDataExportFileWriter
import com.sanket_satpute_20.ironmind.data.export.UserDataExportJsonSerializer
import com.sanket_satpute_20.ironmind.data.local.IronMindDatabase
import com.sanket_satpute_20.ironmind.data.local.Jdk25CompatibleRobolectricTestRunner
import com.sanket_satpute_20.ironmind.data.local.entity.CommitmentEntity
import com.sanket_satpute_20.ironmind.data.local.entity.GoalEntity
import com.sanket_satpute_20.ironmind.data.local.entity.PlanEntity
import com.sanket_satpute_20.ironmind.data.local.entity.ReflectionEntity
import com.sanket_satpute_20.ironmind.data.local.entity.TaskEntity
import com.sanket_satpute_20.ironmind.data.repository.DataExportRepositoryImpl
import com.sanket_satpute_20.ironmind.domain.common.Clock
import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.session.SessionState
import com.sanket_satpute_20.ironmind.testutil.FakeUserSessionProvider
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File

/**
 * Phase 8.3.2 Stage 3B.3 Part B Verification Test:
 * Populated Data Export Verification with representative records:
 * - One Goal
 * - One Plan
 * - Two Tasks
 * - One Commitment
 * - One Reflection
 *
 * Validates:
 * 1. All seeded records are present in output JSON.
 * 2. Exact IDs and ownership fields are correct.
 * 3. Nullable values remain JSON null (not converted to default enums or empty strings).
 * 4. Output JSON is fully well-formed and valid.
 * 5. Total record count in metadata and JSON matches exactly (6 records).
 * 6. Foreign user records are strictly excluded.
 */
@RunWith(Jdk25CompatibleRobolectricTestRunner::class)
@Config(sdk = [34])
class PopulatedUserDataExportVerificationTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var db: IronMindDatabase
    private lateinit var sessionProvider: FakeUserSessionProvider
    private lateinit var repository: DataExportRepositoryImpl
    private lateinit var exportUserDataUseCase: ExportUserDataUseCase
    private lateinit var exportUserDataToFileUseCase: ExportUserDataToFileUseCase
    private lateinit var exportDir: File

    private val targetUserId = "test-user-isolated"
    private val foreignUserId = "foreign-user-99"

    private val testClock = object : Clock {
        override fun currentTimeMillis(): Long = 1_700_000_000_000L
    }

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, IronMindDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        sessionProvider = FakeUserSessionProvider(SessionState.Authenticated(targetUserId, false))
        repository = DataExportRepositoryImpl(db, testClock)
        exportUserDataUseCase = ExportUserDataUseCase(repository, sessionProvider, testClock)

        exportDir = tempFolder.newFolder("exports")
        val serializer = UserDataExportJsonSerializer()
        val fileWriter = UserDataExportFileWriter(exportDir, serializer)
        exportUserDataToFileUseCase = ExportUserDataToFileUseCase(exportUserDataUseCase, fileWriter)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun populatedDataExport_preservesEntities_nulls_recordCounts_and_userIsolation() = runBlocking {
        val dao = db.ironMindDao()

        // Seed target user representative records:
        // 1. One Goal
        val goal = GoalEntity(
            id = "goal-target-1",
            userId = targetUserId,
            ambitionId = null, // Nullable value must remain null
            title = "Run Half Marathon",
            description = "Train and finish 21km",
            why = "Build physical endurance",
            importance = 5,
            status = "ACTIVE",
            targetAt = 1_710_000_000_000L,
            startedAt = 1_700_000_000_000L,
            completedAt = null, // Nullable
            createdAt = 1_700_000_000_000L,
            updatedAt = 1_700_000_000_000L,
            schemaVersion = 1
        )
        dao.insertGoal(goal)

        // 2. One Plan
        val plan = PlanEntity(
            id = "plan-target-1",
            userId = targetUserId,
            goalId = "goal-target-1",
            ambitionId = null, // Nullable
            title = "12-Week Endurance Plan",
            description = "Gradual volume ramp-up",
            status = "ACTIVE",
            createdAt = 1_700_000_000_000L,
            updatedAt = 1_700_000_000_000L,
            startedAt = 1_700_000_000_000L,
            completedAt = null, // Nullable
            source = "USER",
            schemaVersion = 1
        )
        dao.insertPlan(plan)

        // 3. Two Tasks
        val task1 = TaskEntity(
            id = "task-target-1",
            userId = targetUserId,
            goalId = "goal-target-1",
            planId = "plan-target-1",
            parentTaskId = null, // Nullable
            title = "Morning 5k Run",
            description = "Easy pace aerobic run",
            status = "PENDING",
            priority = 2,
            estimatedDurationMinutes = 35,
            scheduledAt = 1_700_050_000_000L,
            dueAt = 1_700_060_000_000L,
            createdAt = 1_700_000_000_000L,
            updatedAt = 1_700_000_000_000L,
            completedAt = null, // Nullable
            postponedAt = null, // Nullable
            source = "USER",
            schemaVersion = 1
        )
        val task2 = TaskEntity(
            id = "task-target-2",
            userId = targetUserId,
            goalId = "goal-target-1",
            planId = "plan-target-1",
            parentTaskId = null, // Nullable
            title = "Post-Run Mobility Stretch",
            description = "15 min hip and calf stretching",
            status = "PENDING",
            priority = 1,
            estimatedDurationMinutes = 15,
            scheduledAt = 1_700_065_000_000L,
            dueAt = 1_700_070_000_000L,
            createdAt = 1_700_000_000_000L,
            updatedAt = 1_700_000_000_000L,
            completedAt = null, // Nullable
            postponedAt = null, // Nullable
            source = "USER",
            schemaVersion = 1
        )
        dao.insertTask(task1)
        dao.insertTask(task2)

        // 4. One Commitment
        val commitment = CommitmentEntity(
            id = "commitment-target-1",
            userId = targetUserId,
            goalId = "goal-target-1",
            planId = "plan-target-1",
            taskId = "task-target-1",
            parentCommitmentId = null, // Nullable
            title = "Execute Monday Run",
            description = "Wake up at 6am and run",
            committedAt = 1_700_000_000_000L,
            scheduledStartAt = 1_700_050_000_000L,
            scheduledEndAt = 1_700_055_000_000L,
            status = "COMMITTED",
            priority = 2,
            source = "USER",
            createdAt = 1_700_000_000_000L,
            updatedAt = 1_700_000_000_000L,
            startedAt = 1_700_000_000_000L,
            completedAt = null, // Nullable
            postponedAt = null, // Nullable
            missedAt = null, // Nullable
            recoveredAt = null, // Nullable
            schemaVersion = 1
        )
        dao.insertCommitment(commitment)

        // 5. One Reflection
        val reflection = ReflectionEntity(
            id = "reflection-target-1",
            userId = targetUserId,
            targetEntityId = "task-target-1",
            targetEntityType = "TASK",
            content = "Felt light on my feet and held consistent cadence",
            sentiment = "POSITIVE",
            createdAt = 1_700_075_000_000L,
            schemaVersion = 1
        )
        dao.insertReflection(reflection)

        // Seed foreign user records to verify multi-tenant isolation
        dao.insertGoal(goal.copy(id = "foreign-goal-99", userId = foreignUserId, title = "Foreign Goal"))
        dao.insertPlan(plan.copy(id = "foreign-plan-99", userId = foreignUserId, title = "Foreign Plan"))
        dao.insertTask(task1.copy(id = "foreign-task-99", userId = foreignUserId, title = "Foreign Task"))
        dao.insertCommitment(commitment.copy(id = "foreign-commitment-99", userId = foreignUserId, title = "Foreign Commitment"))
        dao.insertReflection(reflection.copy(id = "foreign-reflection-99", userId = foreignUserId, content = "Foreign Reflection"))

        // Execute export use case
        val exportResult = exportUserDataToFileUseCase(targetUserId, "1.0.0-test")
        assertTrue("Export must succeed", exportResult is Result.Success)

        val metadata = (exportResult as Result.Success).data
        val exportFile = metadata.file
        assertTrue("Export file must exist", exportFile.exists())
        assertTrue("Export file must be non-empty", exportFile.length() > 0L)

        // Expected count: 1 goal + 1 plan + 2 tasks + 1 commitment + 1 reflection = 6 records
        assertEquals(6, metadata.recordCount)

        // Read and parse exported JSON
        val rawJson = exportFile.readText(Charsets.UTF_8)
        val rootJson = JSONObject(rawJson)

        // Verify root metadata
        assertEquals("ironmind-user-data", rootJson.getString("format"))
        assertEquals(1, rootJson.getInt("exportVersion"))
        assertEquals(targetUserId, rootJson.getString("userId"))
        assertEquals("1.0.0-test", rootJson.getString("appVersion"))
        assertEquals(1_700_000_000_000L, rootJson.getLong("exportedAt"))

        val dataObj = rootJson.getJSONObject("data")

        // 1. Verify Goal
        val goalsArray = dataObj.getJSONArray("goals")
        assertEquals(1, goalsArray.length())
        val exportedGoal = goalsArray.getJSONObject(0)
        assertEquals("goal-target-1", exportedGoal.getString("id"))
        assertEquals(targetUserId, exportedGoal.getString("userId"))
        assertEquals("Run Half Marathon", exportedGoal.getString("title"))
        assertTrue("Nullable ambitionId must be null", exportedGoal.isNull("ambitionId"))
        assertTrue("Nullable completedAt must be null", exportedGoal.isNull("completedAt"))

        // 2. Verify Plan
        val plansArray = dataObj.getJSONArray("plans")
        assertEquals(1, plansArray.length())
        val exportedPlan = plansArray.getJSONObject(0)
        assertEquals("plan-target-1", exportedPlan.getString("id"))
        assertEquals(targetUserId, exportedPlan.getString("userId"))
        assertEquals("goal-target-1", exportedPlan.getString("goalId"))
        assertTrue("Nullable ambitionId must be null", exportedPlan.isNull("ambitionId"))
        assertTrue("Nullable completedAt must be null", exportedPlan.isNull("completedAt"))

        // 3. Verify Tasks (2 tasks)
        val tasksArray = dataObj.getJSONArray("tasks")
        assertEquals(2, tasksArray.length())
        val taskIds = setOf(tasksArray.getJSONObject(0).getString("id"), tasksArray.getJSONObject(1).getString("id"))
        assertEquals(setOf("task-target-1", "task-target-2"), taskIds)
        for (i in 0 until 2) {
            val t = tasksArray.getJSONObject(i)
            assertEquals(targetUserId, t.getString("userId"))
            assertEquals("goal-target-1", t.getString("goalId"))
            assertEquals("plan-target-1", t.getString("planId"))
            assertTrue("Nullable parentTaskId must be null", t.isNull("parentTaskId"))
            assertTrue("Nullable completedAt must be null", t.isNull("completedAt"))
            assertTrue("Nullable postponedAt must be null", t.isNull("postponedAt"))
        }

        // 4. Verify Commitment
        val commitmentsArray = dataObj.getJSONArray("commitments")
        assertEquals(1, commitmentsArray.length())
        val exportedCommitment = commitmentsArray.getJSONObject(0)
        assertEquals("commitment-target-1", exportedCommitment.getString("id"))
        assertEquals(targetUserId, exportedCommitment.getString("userId"))
        assertEquals("task-target-1", exportedCommitment.getString("taskId"))
        assertTrue("Nullable parentCommitmentId must be null", exportedCommitment.isNull("parentCommitmentId"))
        assertTrue("Nullable completedAt must be null", exportedCommitment.isNull("completedAt"))
        assertTrue("Nullable postponedAt must be null", exportedCommitment.isNull("postponedAt"))
        assertTrue("Nullable missedAt must be null", exportedCommitment.isNull("missedAt"))
        assertTrue("Nullable recoveredAt must be null", exportedCommitment.isNull("recoveredAt"))

        // 5. Verify Reflection
        val reflectionsArray = dataObj.getJSONArray("reflections")
        assertEquals(1, reflectionsArray.length())
        val exportedReflection = reflectionsArray.getJSONObject(0)
        assertEquals("reflection-target-1", exportedReflection.getString("id"))
        assertEquals(targetUserId, exportedReflection.getString("userId"))
        assertEquals("task-target-1", exportedReflection.getString("targetEntityId"))
        assertEquals("TASK", exportedReflection.getString("targetEntityType"))
        assertEquals("Felt light on my feet and held consistent cadence", exportedReflection.getString("content"))

        // 6. Verify strictly NO foreign records leaked in any array
        assertFalse("Raw JSON must not contain foreignUserId", rawJson.contains(foreignUserId))
        assertFalse("Raw JSON must not contain foreign-goal-99", rawJson.contains("foreign-goal-99"))
        assertFalse("Raw JSON must not contain foreign-plan-99", rawJson.contains("foreign-plan-99"))
        assertFalse("Raw JSON must not contain foreign-task-99", rawJson.contains("foreign-task-99"))
        assertFalse("Raw JSON must not contain foreign-commitment-99", rawJson.contains("foreign-commitment-99"))
        assertFalse("Raw JSON must not contain foreign-reflection-99", rawJson.contains("foreign-reflection-99"))

        // 7. Verify unseeded categories are empty arrays or null
        assertEquals(0, dataObj.getJSONArray("outcomes").length())
        assertEquals(0, dataObj.getJSONArray("protectionRules").length())
        assertEquals(0, dataObj.getJSONArray("protectionSessions").length())
        assertEquals(0, dataObj.getJSONArray("events").length())
        assertEquals(0, dataObj.getJSONArray("memories").length())
        assertEquals(0, dataObj.getJSONArray("observations").length())
        assertEquals(0, dataObj.getJSONArray("patterns").length())
        assertEquals(0, dataObj.getJSONArray("autonomySettings").length())
        assertEquals(0, dataObj.getJSONArray("interventionRecords").length())
        assertEquals(0, dataObj.getJSONArray("experimentRecords").length())
        assertEquals(0, dataObj.getJSONArray("barrierHypotheses").length())
        assertEquals(0, dataObj.getJSONArray("interventionRecommendations").length())
        assertEquals(0, dataObj.getJSONArray("learnerEventStatuses").length())
        assertEquals(0, dataObj.getJSONArray("correctionReviews").length())
        assertTrue(dataObj.isNull("userProfile"))
        assertTrue(dataObj.isNull("globalAutonomyState"))
        assertTrue(dataObj.isNull("appUsageObservationSettings"))
        assertTrue(dataObj.isNull("notificationObservationSettings"))
        assertTrue(dataObj.isNull("calendarObservationSettings"))
        assertTrue(dataObj.isNull("locationObservationSettings"))
        assertTrue(dataObj.isNull("activityObservationSettings"))
    }
}
