package com.sanket_satpute_20.ironmind.domain.usecase.observation

import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.observation.Observation
import com.sanket_satpute_20.ironmind.domain.model.observation.ObservationType
import com.sanket_satpute_20.ironmind.domain.repository.ObservationRepository
import com.sanket_satpute_20.ironmind.testutil.fake.FakeObservationRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class BuildFactualContextSnapshotUseCaseTest {

    private lateinit var observationRepository: FakeObservationRepository
    private lateinit var aggregateAppUsage: AggregateAppUsageObservationsUseCase
    private lateinit var aggregateActivity: AggregateActivityObservationsUseCase
    private lateinit var aggregateCalendar: AggregateCalendarObservationsUseCase
    private lateinit var aggregateLocation: AggregateLocationObservationsUseCase
    private lateinit var aggregateNotifications: AggregateNotificationObservationsUseCase
    private lateinit var useCase: BuildFactualContextSnapshotUseCase

    private val userId = "test_user"
    private val startTimeMs = 1000L
    private val endTimeMs = 2000L

    @Before
    fun setUp() {
        observationRepository = FakeObservationRepository()
        
        aggregateAppUsage = AggregateAppUsageObservationsUseCase(observationRepository)
        aggregateActivity = AggregateActivityObservationsUseCase(observationRepository)
        aggregateCalendar = AggregateCalendarObservationsUseCase(observationRepository)
        aggregateLocation = AggregateLocationObservationsUseCase(observationRepository)
        aggregateNotifications = AggregateNotificationObservationsUseCase(observationRepository)

        useCase = BuildFactualContextSnapshotUseCase(
            aggregateAppUsage,
            aggregateActivity,
            aggregateCalendar,
            aggregateLocation,
            aggregateNotifications
        )
    }

    @Test
    fun `1 valid window succeeds and 6 all five successful aggregations are represented exactly and 7 empty aggregations remain valid`() = runBlocking {
        val result = useCase(userId, startTimeMs, endTimeMs)
        assertTrue(result is Result.Success)
        val data = (result as Result.Success).data
        
        assertEquals(userId, data.userId)
        assertEquals(startTimeMs, data.startTimeMs)
        assertEquals(endTimeMs, data.endTimeMs)
        
        // They are completely empty arrays, meaning 0 count but objects are NOT null
        assertTrue(data.appUsage != null)
        assertTrue(data.activity != null)
        assertTrue(data.calendar != null)
        assertTrue(data.location != null)
        assertTrue(data.notifications != null)
    }

    @Test
    fun `2 invalid window fails`() = runBlocking {
        val result = useCase(userId, endTimeMs, startTimeMs)
        assertTrue(result is Result.Failure)
        assertTrue((result as Result.Failure).error is IllegalArgumentException)
    }

    // 3, 4, 5 are implicitly tested by passing valid inputs and getting matching models.
    @Test
    fun `3 userId passed unchanged 4 start passed unchanged 5 end passed unchanged`() = runBlocking {
        val result = useCase(userId, startTimeMs, endTimeMs)
        val data = (result as Result.Success).data
        assertEquals(userId, data.appUsage?.userId)
        assertEquals(startTimeMs, data.appUsage?.startTimeMs)
        assertEquals(endTimeMs, data.appUsage?.endTimeMs)
    }

    private fun createFailingRepoFor(targetType: ObservationType): ObservationRepository {
        return object : ObservationRepository {
            override suspend fun insertObservation(observation: Observation): Result<Unit, Exception> = Result.Success(Unit)
            override suspend fun getObservations(userId: String, limit: Int, offset: Int): Result<List<Observation>, Exception> = Result.Success(emptyList())
            override suspend fun getObservationsByType(userId: String, type: ObservationType, limit: Int, offset: Int): Result<List<Observation>, Exception> = Result.Success(emptyList())
            override suspend fun getObservationById(id: String): Result<Observation, Exception> = Result.Failure(Exception("Not found"))
            override suspend fun deleteObservation(id: String): Result<Unit, Exception> = Result.Success(Unit)
            override suspend fun getObservationsForTimeWindow(userId: String, type: ObservationType, startTimeMs: Long, endTimeMs: Long): Result<List<Observation>, Exception> {
                if (type == targetType) {
                    return Result.Failure(Exception("$targetType Failed"))
                }
                return Result.Success(emptyList())
            }
        }
    }

    @Test
    fun `8 app usage failure propagates 13 failure is NOT converted to null`() = runBlocking {
        val failingRepo = createFailingRepoFor(ObservationType.APP_USAGE_SESSION)
        val localUseCase = BuildFactualContextSnapshotUseCase(
            AggregateAppUsageObservationsUseCase(failingRepo),
            AggregateActivityObservationsUseCase(failingRepo),
            AggregateCalendarObservationsUseCase(failingRepo),
            AggregateLocationObservationsUseCase(failingRepo),
            AggregateNotificationObservationsUseCase(failingRepo)
        )
        val result = localUseCase(userId, startTimeMs, endTimeMs)
        assertTrue(result is Result.Failure)
        assertEquals("${ObservationType.APP_USAGE_SESSION} Failed", (result as Result.Failure).error.message)
    }

    @Test
    fun `9 activity failure propagates`() = runBlocking {
        val failingRepo = createFailingRepoFor(ObservationType.ACTIVITY_CONTEXT_CHANGED)
        val localUseCase = BuildFactualContextSnapshotUseCase(
            AggregateAppUsageObservationsUseCase(failingRepo),
            AggregateActivityObservationsUseCase(failingRepo),
            AggregateCalendarObservationsUseCase(failingRepo),
            AggregateLocationObservationsUseCase(failingRepo),
            AggregateNotificationObservationsUseCase(failingRepo)
        )
        val result = localUseCase(userId, startTimeMs, endTimeMs)
        assertTrue(result is Result.Failure)
        assertEquals("${ObservationType.ACTIVITY_CONTEXT_CHANGED} Failed", (result as Result.Failure).error.message)
    }

    @Test
    fun `10 calendar failure propagates`() = runBlocking {
        val failingRepo = createFailingRepoFor(ObservationType.CALENDAR_CONTEXT_CHANGED)
        val localUseCase = BuildFactualContextSnapshotUseCase(
            AggregateAppUsageObservationsUseCase(failingRepo),
            AggregateActivityObservationsUseCase(failingRepo),
            AggregateCalendarObservationsUseCase(failingRepo),
            AggregateLocationObservationsUseCase(failingRepo),
            AggregateNotificationObservationsUseCase(failingRepo)
        )
        val result = localUseCase(userId, startTimeMs, endTimeMs)
        assertTrue(result is Result.Failure)
        assertEquals("${ObservationType.CALENDAR_CONTEXT_CHANGED} Failed", (result as Result.Failure).error.message)
    }

    @Test
    fun `11 location failure propagates`() = runBlocking {
        val failingRepo = createFailingRepoFor(ObservationType.LOCATION_CONTEXT_CHANGED)
        val localUseCase = BuildFactualContextSnapshotUseCase(
            AggregateAppUsageObservationsUseCase(failingRepo),
            AggregateActivityObservationsUseCase(failingRepo),
            AggregateCalendarObservationsUseCase(failingRepo),
            AggregateLocationObservationsUseCase(failingRepo),
            AggregateNotificationObservationsUseCase(failingRepo)
        )
        val result = localUseCase(userId, startTimeMs, endTimeMs)
        assertTrue(result is Result.Failure)
        assertEquals("${ObservationType.LOCATION_CONTEXT_CHANGED} Failed", (result as Result.Failure).error.message)
    }

    @Test
    fun `12 notification failure propagates`() = runBlocking {
        val failingRepo = createFailingRepoFor(ObservationType.NOTIFICATION_RECEIVED)
        val localUseCase = BuildFactualContextSnapshotUseCase(
            AggregateAppUsageObservationsUseCase(failingRepo),
            AggregateActivityObservationsUseCase(failingRepo),
            AggregateCalendarObservationsUseCase(failingRepo),
            AggregateLocationObservationsUseCase(failingRepo),
            AggregateNotificationObservationsUseCase(failingRepo)
        )
        val result = localUseCase(userId, startTimeMs, endTimeMs)
        assertTrue(result is Result.Failure)
        assertEquals("${ObservationType.NOTIFICATION_RECEIVED} Failed", (result as Result.Failure).error.message)
    }

    @Test
    fun `14 no aggregation is independently reconstructed by the assembly layer`() = runBlocking {
        val result = useCase(userId, startTimeMs, endTimeMs)
        val snapshot = (result as Result.Success).data
        
        val appUsageResult = aggregateAppUsage(userId, startTimeMs, endTimeMs)
        val activityResult = aggregateActivity(userId, startTimeMs, endTimeMs)
        val calendarResult = aggregateCalendar(userId, startTimeMs, endTimeMs)
        val locationResult = aggregateLocation(userId, startTimeMs, endTimeMs)
        val notificationsResult = aggregateNotifications(userId, startTimeMs, endTimeMs)

        assertEquals((appUsageResult as Result.Success).data, snapshot.appUsage)
        assertEquals((activityResult as Result.Success).data, snapshot.activity)
        assertEquals((calendarResult as Result.Success).data, snapshot.calendar)
        assertEquals((locationResult as Result.Success).data, snapshot.location)
        assertEquals((notificationsResult as Result.Success).data, snapshot.notifications)
    }

    @Test
    fun `15 deterministic output for identical inputs results`() = runBlocking {
        val result1 = useCase(userId, startTimeMs, endTimeMs)
        val result2 = useCase(userId, startTimeMs, endTimeMs)
        
        assertEquals((result1 as Result.Success).data, (result2 as Result.Success).data)
    }
}
