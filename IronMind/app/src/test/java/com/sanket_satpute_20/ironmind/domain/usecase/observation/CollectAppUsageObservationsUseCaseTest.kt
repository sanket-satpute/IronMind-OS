package com.sanket_satpute_20.ironmind.domain.usecase.observation

import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.AppUsageObservationSettings
import com.sanket_satpute_20.ironmind.domain.model.observation.ObservationType
import com.sanket_satpute_20.ironmind.domain.provider.AppUsageEvent
import com.sanket_satpute_20.ironmind.domain.provider.AppUsageObservationProvider
import com.sanket_satpute_20.ironmind.domain.repository.AppUsageObservationSettingsRepository
import com.sanket_satpute_20.ironmind.testutil.fake.FakeClock
import com.sanket_satpute_20.ironmind.testutil.fake.FakeIdGenerator
import com.sanket_satpute_20.ironmind.testutil.fake.FakeObservationRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class CollectAppUsageObservationsUseCaseTest {

    private lateinit var settingsRepository: FakeAppUsageObservationSettingsRepository
    private lateinit var observationRepository: FakeObservationRepository
    private lateinit var appUsageObservationProvider: FakeAppUsageObservationProvider
    private lateinit var idGenerator: FakeIdGenerator
    private lateinit var clock: FakeClock
    private lateinit var useCase: CollectAppUsageObservationsUseCase

    @Before
    fun setup() {
        settingsRepository = FakeAppUsageObservationSettingsRepository()
        observationRepository = FakeObservationRepository()
        appUsageObservationProvider = FakeAppUsageObservationProvider()
        idGenerator = FakeIdGenerator()
        clock = FakeClock()
        clock.setTime(1600000000000L) // arbitrary time

        useCase = CollectAppUsageObservationsUseCase(
            settingsRepository = settingsRepository,
            observationRepository = observationRepository,
            appUsageObservationProvider = appUsageObservationProvider,
            idGenerator = idGenerator,
            clock = clock
        )
    }

    @Test
    fun `SAME INPUT produces SAME ID`() = runTest {
        val userId = "user-1"
        
        // Emulate an app usage event
        val event = AppUsageEvent(
            packageName = "com.instagram.android",
            startTimeMillis = clock.currentTimeMillis() - 10000,
            endTimeMillis = clock.currentTimeMillis(),
            totalDurationMillis = 10000
        )
        
        appUsageObservationProvider.mockEvents = listOf(event)

        val result1 = useCase(userId)
        assertTrue(result1 is Result.Success)
        
        // Grab the saved observation
        val list1 = (observationRepository.getObservations(userId) as Result.Success).data
        assertEquals(1, list1.size)
        val id1 = list1.first().id

        // Clear the repository to run again clean (simulating testing pure generation logic)
        observationRepository.deleteObservation(id1)

        val result2 = useCase(userId)
        assertTrue(result2 is Result.Success)

        val list2 = (observationRepository.getObservations(userId) as Result.Success).data
        assertEquals(1, list2.size)
        val id2 = list2.first().id

        assertEquals("Observation IDs should be completely identical for the exact same input", id1, id2)
    }

    @Test
    fun `DIFFERENT APP produces DIFFERENT ID`() = runTest {
        val userId = "user-1"
        
        val event1 = AppUsageEvent(
            packageName = "com.instagram.android",
            startTimeMillis = 1000L,
            endTimeMillis = 2000L,
            totalDurationMillis = 1000L
        )
        
        val event2 = AppUsageEvent(
            packageName = "com.twitter.android",
            startTimeMillis = 1000L, // Same start time
            endTimeMillis = 2000L,
            totalDurationMillis = 1000L
        )
        
        appUsageObservationProvider.mockEvents = listOf(event1, event2)

        val result = useCase(userId)
        assertTrue(result is Result.Success)
        
        val list = (observationRepository.getObservations(userId) as Result.Success).data
        assertEquals(2, list.size)
        
        assertNotEquals("Observation IDs must differ when package names differ", list[0].id, list[1].id)
    }

    @Test
    fun `DIFFERENT START TIME produces DIFFERENT ID`() = runTest {
        val userId = "user-1"
        
        val event1 = AppUsageEvent(
            packageName = "com.instagram.android",
            startTimeMillis = 1000L,
            endTimeMillis = 2000L,
            totalDurationMillis = 1000L
        )
        
        val event2 = AppUsageEvent(
            packageName = "com.instagram.android",
            startTimeMillis = 3000L, // Different start time
            endTimeMillis = 4000L,
            totalDurationMillis = 1000L
        )
        
        appUsageObservationProvider.mockEvents = listOf(event1, event2)

        val result = useCase(userId)
        assertTrue(result is Result.Success)
        
        val list = (observationRepository.getObservations(userId) as Result.Success).data
        assertEquals(2, list.size)
        
        assertNotEquals("Observation IDs must differ when start times differ", list[0].id, list[1].id)
    }

    @Test
    fun `OVERLAPPING COLLECTION DOES NOT DUPLICATE`() = runTest {
        val userId = "user-1"
        
        val event = AppUsageEvent(
            packageName = "com.instagram.android",
            startTimeMillis = 1000L,
            endTimeMillis = 2000L,
            totalDurationMillis = 1000L
        )
        
        appUsageObservationProvider.mockEvents = listOf(event)

        // Run 1
        useCase(userId)
        
        // Run 2 (Simulating overlapping lookback window catching the exact same event)
        useCase(userId)
        
        // Because FakeObservationRepository uses a Map keyed by `id`, and the ID is deterministic,
        // it acts identically to Room's OnConflictStrategy.REPLACE.
        val list = (observationRepository.getObservations(userId) as Result.Success).data
        
        assertEquals("There should only be 1 logical observation saved, despite two collections.", 1, list.size)
    }

    // --- Inline Fakes ---
    class FakeAppUsageObservationSettingsRepository : AppUsageObservationSettingsRepository {
        var settings = AppUsageObservationSettings("user-1", isEnabled = true)
        override suspend fun getSettings(userId: String): Result<AppUsageObservationSettings, Exception> {
            return Result.Success(settings.copy(userId = userId))
        }
        override suspend fun setEnabled(userId: String, isEnabled: Boolean): Result<Unit, Exception> {
            this.settings = this.settings.copy(userId = userId, isEnabled = isEnabled)
            return Result.Success(Unit)
        }
    }

    class FakeAppUsageObservationProvider : AppUsageObservationProvider {
        var permissionGranted = true
        var mockEvents = emptyList<AppUsageEvent>()

        override fun isPermissionGranted(): Boolean = permissionGranted

        override fun getAppUsageSince(fromTimeMillis: Long): List<AppUsageEvent> {
            return mockEvents
        }
    }
}
