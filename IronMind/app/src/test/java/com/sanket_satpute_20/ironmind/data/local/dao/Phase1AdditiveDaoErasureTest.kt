package com.sanket_satpute_20.ironmind.data.local.dao

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.sanket_satpute_20.ironmind.data.local.IronMindDatabase
import com.sanket_satpute_20.ironmind.data.local.Jdk25CompatibleRobolectricTestRunner
import com.sanket_satpute_20.ironmind.data.local.entity.ActivityObservationSettingsEntity
import com.sanket_satpute_20.ironmind.data.local.entity.AppUsageObservationSettingsEntity
import com.sanket_satpute_20.ironmind.data.local.entity.CalendarObservationSettingsEntity
import com.sanket_satpute_20.ironmind.data.local.entity.LocationObservationSettingsEntity
import com.sanket_satpute_20.ironmind.data.local.entity.NotificationObservationSettingsEntity
import com.sanket_satpute_20.ironmind.data.local.entity.OutcomeEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Targeted tests for Phase 1 additive DAO user-scoped deletion queries.
 * Validates correctness, multi-user isolation, and idempotence.
 */
@RunWith(Jdk25CompatibleRobolectricTestRunner::class)
@Config(sdk = [34])
class Phase1AdditiveDaoErasureTest {

    private lateinit var db: IronMindDatabase
    private lateinit var ironMindDao: IronMindDao
    private lateinit var appUsageDao: AppUsageObservationSettingsDao
    private lateinit var notificationSettingsDao: NotificationObservationSettingsDao
    private lateinit var calendarSettingsDao: CalendarObservationSettingsDao
    private lateinit var locationSettingsDao: LocationObservationSettingsDao
    private lateinit var activitySettingsDao: ActivityObservationSettingsDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, IronMindDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        ironMindDao = db.ironMindDao()
        appUsageDao = db.appUsageObservationSettingsDao()
        notificationSettingsDao = db.notificationObservationSettingsDao()
        calendarSettingsDao = db.calendarObservationSettingsDao()
        locationSettingsDao = db.locationObservationSettingsDao()
        activitySettingsDao = db.activityObservationSettingsDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun deleteOutcomesForUser_deletesTargetUserAndPreservesOtherUsers() {
        val userAOutcome = OutcomeEntity(
            id = "outcome-user-a",
            userId = "user-A",
            sourceEntityId = "task-1",
            sourceEntityType = "TASK",
            resultStatus = "COMPLETED",
            actualDurationMinutes = 25,
            completedAt = 1000L,
            createdAt = 1000L,
            schemaVersion = 1
        )
        val userBOutcome = OutcomeEntity(
            id = "outcome-user-b",
            userId = "user-B",
            sourceEntityId = "task-2",
            sourceEntityType = "TASK",
            resultStatus = "FAILED",
            actualDurationMinutes = 15,
            completedAt = 2000L,
            createdAt = 2000L,
            schemaVersion = 1
        )

        ironMindDao.insertOutcome(userAOutcome)
        ironMindDao.insertOutcome(userBOutcome)

        assertNotNull("User A outcome should be inserted", ironMindDao.getOutcome("outcome-user-a"))
        assertNotNull("User B outcome should be inserted", ironMindDao.getOutcome("outcome-user-b"))

        ironMindDao.deleteOutcomesForUser("user-A")

        assertNull("User A outcome must be deleted", ironMindDao.getOutcome("outcome-user-a"))
        val preservedUserB = ironMindDao.getOutcome("outcome-user-b")
        assertNotNull("User B outcome must remain untouched", preservedUserB)
        assertEquals("user-B", preservedUserB?.userId)
    }

    @Test
    fun appUsageObservationSettingsDao_deleteSettingsForUser_isolatesTargetUser() = runBlocking {
        val settingsA = AppUsageObservationSettingsEntity(userId = "user-A", isEnabled = true, updatedAt = 1000L)
        val settingsB = AppUsageObservationSettingsEntity(userId = "user-B", isEnabled = false, updatedAt = 2000L)

        appUsageDao.upsertSettings(settingsA)
        appUsageDao.upsertSettings(settingsB)

        assertNotNull(appUsageDao.getSettingsForUser("user-A"))
        assertNotNull(appUsageDao.getSettingsForUser("user-B"))

        appUsageDao.deleteSettingsForUser("user-A")

        assertNull("User A settings must be deleted", appUsageDao.getSettingsForUser("user-A"))
        val preservedB = appUsageDao.getSettingsForUser("user-B")
        assertNotNull("User B settings must be preserved", preservedB)
        assertEquals(false, preservedB?.isEnabled)
    }

    @Test
    fun notificationObservationSettingsDao_deleteSettingsForUser_isolatesTargetUser() = runBlocking {
        val settingsA = NotificationObservationSettingsEntity(userId = "user-A", isEnabled = true, updatedAt = 1000L)
        val settingsB = NotificationObservationSettingsEntity(userId = "user-B", isEnabled = true, updatedAt = 2000L)

        notificationSettingsDao.upsertSettings(settingsA)
        notificationSettingsDao.upsertSettings(settingsB)

        assertNotNull(notificationSettingsDao.getSettings("user-A"))
        assertNotNull(notificationSettingsDao.getSettings("user-B"))

        notificationSettingsDao.deleteSettingsForUser("user-A")

        assertNull("User A notification settings must be deleted", notificationSettingsDao.getSettings("user-A"))
        assertNotNull("User B notification settings must be preserved", notificationSettingsDao.getSettings("user-B"))
    }

    @Test
    fun calendarObservationSettingsDao_deleteSettingsForUser_isolatesTargetUser() = runBlocking {
        val settingsA = CalendarObservationSettingsEntity(userId = "user-A", isEnabled = true, updatedAt = 1000L)
        val settingsB = CalendarObservationSettingsEntity(userId = "user-B", isEnabled = false, updatedAt = 2000L)

        calendarSettingsDao.insertOrUpdate(settingsA)
        calendarSettingsDao.insertOrUpdate(settingsB)

        assertNotNull(calendarSettingsDao.getSettings("user-A"))
        assertNotNull(calendarSettingsDao.getSettings("user-B"))

        calendarSettingsDao.deleteSettingsForUser("user-A")

        assertNull("User A calendar settings must be deleted", calendarSettingsDao.getSettings("user-A"))
        assertNotNull("User B calendar settings must be preserved", calendarSettingsDao.getSettings("user-B"))
    }

    @Test
    fun locationObservationSettingsDao_deleteSettingsForUser_isolatesTargetUser() = runBlocking {
        val settingsA = LocationObservationSettingsEntity(userId = "user-A", isEnabled = true, updatedAt = 1000L)
        val settingsB = LocationObservationSettingsEntity(userId = "user-B", isEnabled = false, updatedAt = 2000L)

        locationSettingsDao.insertOrUpdate(settingsA)
        locationSettingsDao.insertOrUpdate(settingsB)

        assertNotNull(locationSettingsDao.getSettings("user-A"))
        assertNotNull(locationSettingsDao.getSettings("user-B"))

        locationSettingsDao.deleteSettingsForUser("user-A")

        assertNull("User A location settings must be deleted", locationSettingsDao.getSettings("user-A"))
        assertNotNull("User B location settings must be preserved", locationSettingsDao.getSettings("user-B"))
    }

    @Test
    fun activityObservationSettingsDao_deleteSettingsForUser_isolatesTargetUser() = runBlocking {
        val settingsA = ActivityObservationSettingsEntity(userId = "user-A", isEnabled = true, lastUpdatedAt = 1000L)
        val settingsB = ActivityObservationSettingsEntity(userId = "user-B", isEnabled = false, lastUpdatedAt = 2000L)

        activitySettingsDao.insertOrUpdateSettings(settingsA)
        activitySettingsDao.insertOrUpdateSettings(settingsB)

        assertNotNull(activitySettingsDao.getSettings("user-A"))
        assertNotNull(activitySettingsDao.getSettings("user-B"))

        activitySettingsDao.deleteSettingsForUser("user-A")

        assertNull("User A activity settings must be deleted", activitySettingsDao.getSettings("user-A"))
        assertNotNull("User B activity settings must be preserved", activitySettingsDao.getSettings("user-B"))
    }

    @Test
    fun allNewDaoDeletionQueries_areIdempotentWhenRowsDoNotExist() = runBlocking {
        // Invoking delete on empty tables or non-existent user must complete without error
        ironMindDao.deleteOutcomesForUser("non_existent_user")
        appUsageDao.deleteSettingsForUser("non_existent_user")
        notificationSettingsDao.deleteSettingsForUser("non_existent_user")
        calendarSettingsDao.deleteSettingsForUser("non_existent_user")
        locationSettingsDao.deleteSettingsForUser("non_existent_user")
        activitySettingsDao.deleteSettingsForUser("non_existent_user")
    }
}
