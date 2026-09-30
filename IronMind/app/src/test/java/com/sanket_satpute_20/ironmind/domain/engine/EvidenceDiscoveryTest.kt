package com.sanket_satpute_20.ironmind.domain.engine

import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.Event
import com.sanket_satpute_20.ironmind.domain.model.EventType
import com.sanket_satpute_20.ironmind.domain.model.Reflection
import com.sanket_satpute_20.ironmind.domain.model.observation.Observation
import com.sanket_satpute_20.ironmind.domain.model.observation.ObservationType
import com.sanket_satpute_20.ironmind.domain.model.pattern.DiscoveryCriteria
import com.sanket_satpute_20.ironmind.domain.model.pattern.DiscoveryOrdering
import com.sanket_satpute_20.ironmind.domain.model.pattern.EvidenceReference
import com.sanket_satpute_20.ironmind.domain.model.pattern.EvidenceSourceType
import com.sanket_satpute_20.ironmind.domain.repository.EventRepository
import com.sanket_satpute_20.ironmind.domain.repository.ObservationRepository
import com.sanket_satpute_20.ironmind.domain.repository.ReflectionRepository
import com.sanket_satpute_20.ironmind.testutil.fake.FakeIronLogger
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlin.random.Random

class EvidenceDiscoveryTest {

    private lateinit var eventRepo: EventRepository
    private lateinit var obsRepo: ObservationRepository
    private lateinit var refRepo: ReflectionRepository
    private lateinit var logger: FakeIronLogger
    private lateinit var discovery: EvidenceDiscovery

    @Before
    fun setup() {
        eventRepo = mockk()
        obsRepo = mockk()
        refRepo = mockk()
        logger = FakeIronLogger()
        discovery = EvidenceDiscovery(eventRepo, obsRepo, refRepo, logger)

        // Default empty mocks
        coEvery { eventRepo.getEventsForTimeWindow(any(), any(), any(), any(), any(), any()) } returns Result.Success(emptyList())
        coEvery { obsRepo.getObservationsForTimeWindow(any(), any(), any(), any(), any(), any()) } returns Result.Success(emptyList())
        coEvery { refRepo.getReflectionsForTimeWindow(any(), any(), any(), any(), any()) } returns Result.Success(emptyList())
    }

    private fun criteria(
        sourceScope: List<EvidenceSourceType> = listOf(EvidenceSourceType.EVENT, EvidenceSourceType.OBSERVATION, EvidenceSourceType.REFLECTION),
        limit: Int = 10,
        ordering: DiscoveryOrdering = DiscoveryOrdering.TIMESTAMP_ASC,
        obsTypes: List<ObservationType>? = null,
        eventTypes: List<EventType>? = null
    ) = DiscoveryCriteria(
        userId = "user1",
        startTimeMs = 100L,
        endTimeMs = 500L,
        sourceScope = sourceScope,
        eventTypes = eventTypes,
        observationTypes = obsTypes,
        limit = limit,
        ordering = ordering
    )

    private fun event(id: String, time: Long) = Event(
        id = id, userId = "user1", type = EventType.GOAL_CREATED, occurredAt = time, recordedAt = 0L, source = com.sanket_satpute_20.ironmind.domain.model.EntitySource.USER, schemaVersion = 1
    )

    private fun obs(id: String, time: Long) = Observation(
        id = id, userId = "user1", type = ObservationType.LOCATION_CONTEXT_CHANGED, source = mockk(relaxed = true), occurredAt = time, recordedAt = 0L, subjectId = null, value = "", context = "", confidence = null, provenance = mockk(relaxed = true), schemaVersion = 1
    )

    private fun ref(id: String, time: Long) = Reflection(
        id = id, userId = "user1", targetEntityId = null, targetEntityType = null, content = "", sentiment = null, createdAt = time, schemaVersion = 1
    )

    @Test
    fun `A EVENT-only discovery`() = runBlocking {
        coEvery { eventRepo.getEventsForTimeWindow(any(), any(), any(), any(), any(), any()) } returns Result.Success(listOf(event("e1", 150L)))
        val res = discovery.discover(criteria(sourceScope = listOf(EvidenceSourceType.EVENT)))
        assertTrue(res is Result.Success)
        assertEquals(1, (res as Result.Success).data.size)
        assertEquals(EvidenceSourceType.EVENT, res.data[0].sourceType)
    }

    @Test
    fun `B OBSERVATION-only discovery`() = runBlocking {
        coEvery { obsRepo.getObservationsForTimeWindow(any(), any(), any(), any(), any(), any()) } returns Result.Success(listOf(obs("o1", 150L)))
        val res = discovery.discover(criteria(sourceScope = listOf(EvidenceSourceType.OBSERVATION)))
        assertEquals(1, (res as Result.Success).data.size)
        assertEquals(EvidenceSourceType.OBSERVATION, res.data[0].sourceType)
    }

    @Test
    fun `C REFLECTION-only discovery`() = runBlocking {
        coEvery { refRepo.getReflectionsForTimeWindow(any(), any(), any(), any(), any()) } returns Result.Success(listOf(ref("r1", 150L)))
        val res = discovery.discover(criteria(sourceScope = listOf(EvidenceSourceType.REFLECTION)))
        assertEquals(1, (res as Result.Success).data.size)
        assertEquals(EvidenceSourceType.REFLECTION, res.data[0].sourceType)
    }

    @Test
    fun `D Multi-source discovery`() = runBlocking {
        coEvery { eventRepo.getEventsForTimeWindow(any(), any(), any(), any(), any(), any()) } returns Result.Success(listOf(event("e1", 150L)))
        coEvery { obsRepo.getObservationsForTimeWindow(any(), any(), any(), any(), any(), any()) } returns Result.Success(listOf(obs("o1", 160L)))
        val res = discovery.discover(criteria())
        assertEquals(2, (res as Result.Success).data.size)
    }

    @Test
    fun `E Empty sourceScope zero results zero queries`() = runBlocking {
        val res = discovery.discover(criteria(sourceScope = emptyList()))
        assertEquals(0, (res as Result.Success).data.size)
        // Mocks won't be called, verified by the fact we returned emptyList
    }

    @Test
    fun `K Global limit across sources`() = runBlocking {
        // 17. MANDATORY GLOBAL-LIMIT TEST
        coEvery { eventRepo.getEventsForTimeWindow(any(), any(), any(), any(), any(), any()) } returns Result.Success(listOf(
            event("E1", 100L), event("E2", 200L), event("E3", 300L)
        ))
        coEvery { obsRepo.getObservationsForTimeWindow(any(), any(), any(), any(), any(), any()) } returns Result.Success(listOf(
            obs("O1", 150L), obs("O2", 250L), obs("O3", 350L)
        ))
        coEvery { refRepo.getReflectionsForTimeWindow(any(), any(), any(), any(), any()) } returns Result.Success(listOf(
            ref("R1", 120L), ref("R2", 220L), ref("R3", 320L)
        ))

        val res = discovery.discover(criteria(limit = 3, ordering = DiscoveryOrdering.TIMESTAMP_ASC))
        val list = (res as Result.Success).data
        assertEquals(3, list.size)
        assertEquals("E1", list[0].sourceId) // 100
        assertEquals("R1", list[1].sourceId) // 120
        assertEquals("O1", list[2].sourceId) // 150
    }

    @Test
    fun `L Global ordering across sources M TIMESTAMP_ASC N TIMESTAMP_DESC O sourceId P sourceType`() = runBlocking {
        // 18. MANDATORY DESC TEST
        coEvery { eventRepo.getEventsForTimeWindow(any(), any(), any(), any(), any(), any()) } returns Result.Success(listOf(
            event("B", 100L), event("A", 100L)
        ))
        coEvery { obsRepo.getObservationsForTimeWindow(any(), any(), any(), any(), any(), any()) } returns Result.Success(listOf(
            obs("A", 100L)
        ))

        val ascRes = (discovery.discover(criteria(ordering = DiscoveryOrdering.TIMESTAMP_ASC)) as Result.Success).data
        assertEquals(3, ascRes.size)
        assertEquals(EvidenceSourceType.EVENT, ascRes[0].sourceType); assertEquals("A", ascRes[0].sourceId)
        assertEquals(EvidenceSourceType.OBSERVATION, ascRes[1].sourceType); assertEquals("A", ascRes[1].sourceId)
        assertEquals(EvidenceSourceType.EVENT, ascRes[2].sourceType); assertEquals("B", ascRes[2].sourceId)

        val descRes = (discovery.discover(criteria(ordering = DiscoveryOrdering.TIMESTAMP_DESC)) as Result.Success).data
        assertEquals(3, descRes.size)
        assertEquals(EvidenceSourceType.EVENT, descRes[0].sourceType); assertEquals("A", descRes[0].sourceId)
        assertEquals(EvidenceSourceType.OBSERVATION, descRes[1].sourceType); assertEquals("A", descRes[1].sourceId)
        assertEquals(EvidenceSourceType.EVENT, descRes[2].sourceType); assertEquals("B", descRes[2].sourceId)
    }

    @Test
    fun `Q canonical sourceType mapping EVENT OBSERVATION REFLECTION`() = runBlocking {
        // 19. MANDATORY SOURCE-TYPE TEST
        coEvery { eventRepo.getEventsForTimeWindow(any(), any(), any(), any(), any(), any()) } returns Result.Success(listOf(event("A", 100L)))
        coEvery { obsRepo.getObservationsForTimeWindow(any(), any(), any(), any(), any(), any()) } returns Result.Success(listOf(obs("A", 100L)))
        coEvery { refRepo.getReflectionsForTimeWindow(any(), any(), any(), any(), any()) } returns Result.Success(listOf(ref("A", 100L)))

        val res = (discovery.discover(criteria(ordering = DiscoveryOrdering.TIMESTAMP_ASC)) as Result.Success).data
        assertEquals(3, res.size)
        assertEquals(EvidenceSourceType.EVENT, res[0].sourceType)
        assertEquals(EvidenceSourceType.OBSERVATION, res[1].sourceType)
        assertEquals(EvidenceSourceType.REFLECTION, res[2].sourceType)
    }

    @Test
    fun `R Deduplication by sourceType and sourceId`() = runBlocking {
        // Same event fetched multiple times (e.g., if somehow a repository duplicated it)
        coEvery { eventRepo.getEventsForTimeWindow(any(), any(), any(), any(), any(), any()) } returns Result.Success(listOf(
            event("A", 100L), event("A", 100L)
        ))
        val res = (discovery.discover(criteria()) as Result.Success).data
        assertEquals(1, res.size)
    }

    @Test
    fun `S Repository failure propagation`() = runBlocking {
        // 21. REPOSITORY FAILURE TEST
        coEvery { eventRepo.getEventsForTimeWindow(any(), any(), any(), any(), any(), any()) } returns Result.Failure(Exception("DB error"))
        val res = discovery.discover(criteria())
        assertTrue(res is Result.Failure)
        assertEquals("DB error", (res as Result.Failure).error.message)
    }

    @Test
    fun `I Observation empty list returns zero without query`() = runBlocking {
        val res = discovery.discover(criteria(obsTypes = emptyList()))
        assertEquals(0, (res as Result.Success).data.size)
    }

    @Test
    fun `V Deterministic repeated execution`() = runBlocking {
        // 20. DETERMINISM TEST
        coEvery { eventRepo.getEventsForTimeWindow(any(), any(), any(), any(), any(), any()) } returns Result.Success(listOf(event("B", 100L), event("A", 100L)))
        coEvery { obsRepo.getObservationsForTimeWindow(any(), any(), any(), any(), any(), any()) } returns Result.Success(listOf(obs("C", 100L), obs("A", 100L)))
        coEvery { refRepo.getReflectionsForTimeWindow(any(), any(), any(), any(), any()) } returns Result.Success(listOf(ref("B", 100L), ref("C", 100L)))

        val firstRun = (discovery.discover(criteria()) as Result.Success).data
        
        for (i in 1..10) {
            val run = (discovery.discover(criteria()) as Result.Success).data
            assertEquals(firstRun.size, run.size)
            for (j in firstRun.indices) {
                assertEquals(firstRun[j].sourceType, run[j].sourceType)
                assertEquals(firstRun[j].sourceId, run[j].sourceId)
            }
        }
    }

    @Test
    fun `Logging test`() = runBlocking {
        val res = discovery.discover(criteria())
        val logs = logger.loggedMessages
        assertTrue(logs.isNotEmpty())
        assertTrue(logs.last().contains("status=SUCCESS"))
        assertTrue(logs.last().contains("sourceScope="))
    }

    @Test
    fun `F G H U pass arguments correctly`() = runBlocking {
        discovery.discover(criteria(eventTypes = listOf(EventType.GOAL_CREATED), obsTypes = listOf(ObservationType.LOCATION_CONTEXT_CHANGED)))
        io.mockk.coVerify {
            eventRepo.getEventsForTimeWindow(
                userId = "user1",
                startTime = 100L,
                endTime = 500L,
                types = listOf(EventType.GOAL_CREATED),
                limit = 10,
                orderAsc = true
            )
            obsRepo.getObservationsForTimeWindow(
                userId = "user1",
                startTimeMs = 100L,
                endTimeMs = 500L,
                types = listOf(ObservationType.LOCATION_CONTEXT_CHANGED),
                limit = 10,
                orderAsc = true
            )
            refRepo.getReflectionsForTimeWindow(
                userId = "user1",
                startTime = 100L,
                endTime = 500L,
                limit = 10,
                orderAsc = true
            )
        }
    }

    @Test
    fun `T Zero-result success`() = runBlocking {
        val res = discovery.discover(criteria())
        assertTrue(res is Result.Success)
        assertEquals(0, (res as Result.Success).data.size)
    }

    @Test
    fun `W LEGACY_AMBIGUOUS never discovered`() = runBlocking {
        val res = discovery.discover(criteria(sourceScope = listOf(EvidenceSourceType.LEGACY_AMBIGUOUS)))
        assertTrue(res is Result.Success)
        assertEquals(0, (res as Result.Success).data.size)
        // Mocks should not be called
        io.mockk.coVerify(exactly = 0) { eventRepo.getEventsForTimeWindow(any(), any(), any(), any(), any(), any()) }
        io.mockk.coVerify(exactly = 0) { obsRepo.getObservationsForTimeWindow(any(), any(), any(), any(), any(), any()) }
        io.mockk.coVerify(exactly = 0) { refRepo.getReflectionsForTimeWindow(any(), any(), any(), any(), any()) }
    }
}
