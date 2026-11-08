package com.sanket_satpute_20.ironmind.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.sanket_satpute_20.ironmind.data.local.IronMindDatabase
import com.sanket_satpute_20.ironmind.data.local.Jdk25CompatibleRobolectricTestRunner
import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.observation.Observation
import com.sanket_satpute_20.ironmind.domain.model.observation.ObservationProvenance
import com.sanket_satpute_20.ironmind.domain.model.observation.ObservationSource
import com.sanket_satpute_20.ironmind.domain.model.observation.ObservationType
import com.sanket_satpute_20.ironmind.domain.repository.AppUsageCheckpoint
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(Jdk25CompatibleRobolectricTestRunner::class)
@Config(sdk = [34])
class RoomAppUsageCheckpointStoreTest {

    private lateinit var database: IronMindDatabase
    private lateinit var store: RoomAppUsageCheckpointStore

    @Before
    fun setUp() {
        val context: Context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, IronMindDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        store = RoomAppUsageCheckpointStore(
            database = database,
            observationDao = database.observationDao(),
            checkpointDao = database.appUsageObservationCheckpointDao()
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun getCheckpoint_returnsNullWhenNoCheckpointExists() = runBlocking {
        val result = store.getCheckpoint("non-existent-user")
        assertTrue(result is Result.Success)
        assertNull((result as Result.Success).data)
    }

    @Test
    fun saveAndGetCheckpoint_persistsAndRetrievesCorrectly() = runBlocking {
        val checkpoint = AppUsageCheckpoint(
            userId = "user-1",
            lastCollectedIntervalEndMs = 1700000000000L,
            activePackageName = "com.android.chrome",
            activeSessionStartTimeMs = 1699999000000L,
            updatedAt = 1700000000500L,
            schemaVersion = 1
        )

        val saveResult = store.saveCheckpoint(checkpoint)
        assertTrue(saveResult is Result.Success)

        val getResult = store.getCheckpoint("user-1")
        assertTrue(getResult is Result.Success)
        val loaded = (getResult as Result.Success).data
        assertNotNull(loaded)
        assertEquals("user-1", loaded!!.userId)
        assertEquals(1700000000000L, loaded.lastCollectedIntervalEndMs)
        assertEquals("com.android.chrome", loaded.activePackageName)
        assertEquals(1699999000000L, loaded.activeSessionStartTimeMs)
        assertEquals(1700000000500L, loaded.updatedAt)
    }

    @Test
    fun userIsolation_checkpointsForDifferentUsersAreStrictlyIsolated() = runBlocking {
        val cpUser1 = AppUsageCheckpoint(
            userId = "user-alpha",
            lastCollectedIntervalEndMs = 1000L,
            activePackageName = "com.alpha.app",
            activeSessionStartTimeMs = 500L,
            updatedAt = 1000L
        )
        val cpUser2 = AppUsageCheckpoint(
            userId = "user-beta",
            lastCollectedIntervalEndMs = 2000L,
            activePackageName = "com.beta.app",
            activeSessionStartTimeMs = 1500L,
            updatedAt = 2000L
        )

        store.saveCheckpoint(cpUser1)
        store.saveCheckpoint(cpUser2)

        val loadedUser1 = (store.getCheckpoint("user-alpha") as Result.Success).data
        val loadedUser2 = (store.getCheckpoint("user-beta") as Result.Success).data

        assertEquals("com.alpha.app", loadedUser1?.activePackageName)
        assertEquals(1000L, loadedUser1?.lastCollectedIntervalEndMs)
        assertEquals("com.beta.app", loadedUser2?.activePackageName)
        assertEquals(2000L, loadedUser2?.lastCollectedIntervalEndMs)

        // Clear user 1, user 2 must remain
        store.clearCheckpointForUser("user-alpha")
        assertNull((store.getCheckpoint("user-alpha") as Result.Success).data)
        assertNotNull((store.getCheckpoint("user-beta") as Result.Success).data)
    }

    @Test
    fun saveObservationsWithCheckpoint_commitsAtomically() = runBlocking {
        val obs1 = Observation(
            id = "obs-atomic-1",
            userId = "user-tx",
            type = ObservationType.APP_USAGE_SESSION,
            source = ObservationSource.APP_USAGE,
            occurredAt = 1000L,
            recordedAt = 2000L,
            subjectId = null,
            value = "com.app.one",
            context = "duration_ms=1000",
            confidence = null,
            provenance = ObservationProvenance(ObservationSource.APP_USAGE, "test", 2000L)
        )
        val obs2 = Observation(
            id = "obs-atomic-2",
            userId = "user-tx",
            type = ObservationType.APP_USAGE_SESSION,
            source = ObservationSource.APP_USAGE,
            occurredAt = 2000L,
            recordedAt = 3000L,
            subjectId = null,
            value = "com.app.two",
            context = "duration_ms=1000",
            confidence = null,
            provenance = ObservationProvenance(ObservationSource.APP_USAGE, "test", 3000L)
        )
        val checkpoint = AppUsageCheckpoint(
            userId = "user-tx",
            lastCollectedIntervalEndMs = 3000L,
            activePackageName = null,
            activeSessionStartTimeMs = null,
            updatedAt = 3000L
        )

        val txResult = store.saveObservationsWithCheckpoint(listOf(obs1, obs2), checkpoint)
        assertTrue(txResult is Result.Success)

        // Verify observations in Room
        val savedObs = database.observationDao().getObservations("user-tx", 10, 0)
        assertEquals(2, savedObs.size)

        // Verify checkpoint in Room
        val savedCp = database.appUsageObservationCheckpointDao().getCheckpoint("user-tx")
        assertNotNull(savedCp)
        assertEquals(3000L, savedCp!!.lastCollectedIntervalEndMs)
    }
}

