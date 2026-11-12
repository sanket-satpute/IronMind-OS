package com.sanket_satpute_20.ironmind.domain.usecase.protection

import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.EntitySource
import com.sanket_satpute_20.ironmind.domain.model.EventType
import com.sanket_satpute_20.ironmind.domain.model.ProtectionSession
import com.sanket_satpute_20.ironmind.domain.model.ProtectionSessionStatus
import com.sanket_satpute_20.ironmind.testutil.fake.FakeClock
import com.sanket_satpute_20.ironmind.testutil.fake.FakeProtectionRepository
import com.sanket_satpute_20.ironmind.testutil.fake.FakeAppProtectionProvider
import com.sanket_satpute_20.ironmind.testutil.fake.FakeEventRepository
import com.sanket_satpute_20.ironmind.testutil.fake.FakeIdGenerator
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class StopProtectionSessionUseCaseTest {

    private lateinit var protectionRepository: FakeProtectionRepository
    private lateinit var protectionProvider: FakeAppProtectionProvider
    private lateinit var clock: FakeClock
    private lateinit var eventRepository: FakeEventRepository
    private lateinit var stopProtectionSessionUseCase: StopProtectionSessionUseCase

    @Before
    fun setup() {
        protectionRepository = FakeProtectionRepository()
        protectionProvider = FakeAppProtectionProvider()
        clock = FakeClock(1000L)
        eventRepository = FakeEventRepository()
        stopProtectionSessionUseCase = StopProtectionSessionUseCase(
            protectionRepository = protectionRepository,
            appProtectionProvider = protectionProvider,
            clock = clock,
            eventRepository = eventRepository
        )
    }

    @Test
    fun `stops active session successfully`() = runTest {
        val session = createActiveSession("s1")
        protectionRepository.saveProtectionSession(session)
        
        clock.advanceTimeBy(1000)
        
        val result = stopProtectionSessionUseCase("s1", StopProtectionSessionUseCase.StopReason.USER_INITIATED)
        assertTrue(result is Result.Success)
        
        val updatedSessionResult = protectionRepository.getProtectionSession("s1")
        assertTrue(updatedSessionResult is Result.Success)
        
        val updatedSession = (updatedSessionResult as Result.Success).data
        assertEquals(ProtectionSessionStatus.COMPLETED, updatedSession.status)
        assertEquals(clock.currentTimeMillis(), updatedSession.endedAt)
    }

    @Test
    fun `stops session with CANCELLED reason`() = runTest {
        val session = createActiveSession("s2")
        protectionRepository.saveProtectionSession(session)
        
        val result = stopProtectionSessionUseCase("s2", StopProtectionSessionUseCase.StopReason.CANCELLED)
        assertTrue(result is Result.Success)
        
        val updatedSession = (protectionRepository.getProtectionSession("s2") as Result.Success).data
        assertEquals(ProtectionSessionStatus.CANCELLED, updatedSession.status)
    }

    @Test
    fun `stopping non-active session returns error`() = runTest {
        val session = createActiveSession("s3").copy(status = ProtectionSessionStatus.COMPLETED)
        protectionRepository.saveProtectionSession(session)
        
        val result = stopProtectionSessionUseCase("s3")
        assertTrue(result is Result.Failure)
    }

    @Test
    fun `stopping session fails if provider removal fails and session remains active`() = runTest {
        val session = createActiveSession("s4")
        protectionRepository.saveProtectionSession(session)
        
        protectionProvider.shouldFailRemoval = true
        
        val result = stopProtectionSessionUseCase("s4")
        assertTrue(result is Result.Failure)
        
        val updatedSessionResult = protectionRepository.getProtectionSession("s4")
        assertTrue(updatedSessionResult is Result.Success)
        
        val updatedSession = (updatedSessionResult as Result.Success).data
        assertEquals(ProtectionSessionStatus.ACTIVE, updatedSession.status)
    }

    @Test
    fun `stopping session with EXPIRED reason sets status EXPIRED and effective endedAt`() = runTest {
        val session = createActiveSession("s_expire").copy(scheduledEndAt = 2000L)
        protectionRepository.saveProtectionSession(session)
        clock.setTime(2500L)

        val result = stopProtectionSessionUseCase("s_expire", StopProtectionSessionUseCase.StopReason.EXPIRED)
        assertTrue(result is Result.Success)

        val updatedSession = (protectionRepository.getProtectionSession("s_expire") as Result.Success).data
        assertEquals(ProtectionSessionStatus.EXPIRED, updatedSession.status)
        assertEquals(2000L, updatedSession.endedAt)
    }

    @Test
    fun `calling EXPIRED reason on already EXPIRED session is idempotent`() = runTest {
        val session = createActiveSession("s_already_exp").copy(
            status = ProtectionSessionStatus.EXPIRED,
            scheduledEndAt = 2000L,
            endedAt = 2000L
        )
        protectionRepository.saveProtectionSession(session)

        val result = stopProtectionSessionUseCase("s_already_exp", StopProtectionSessionUseCase.StopReason.EXPIRED)
        assertTrue(result is Result.Success)

        val updatedSession = (protectionRepository.getProtectionSession("s_already_exp") as Result.Success).data
        assertEquals(ProtectionSessionStatus.EXPIRED, updatedSession.status)
    }

    @Test
    fun `calling EXPIRED reason on OVERRIDDEN session fails and preserves OVERRIDDEN`() = runTest {
        val session = createActiveSession("s_overridden").copy(
            status = ProtectionSessionStatus.OVERRIDDEN,
            scheduledEndAt = 2000L,
            endedAt = 1500L
        )
        protectionRepository.saveProtectionSession(session)

        val result = stopProtectionSessionUseCase("s_overridden", StopProtectionSessionUseCase.StopReason.EXPIRED)
        assertTrue(result is Result.Failure)

        val updatedSession = (protectionRepository.getProtectionSession("s_overridden") as Result.Success).data
        assertEquals(ProtectionSessionStatus.OVERRIDDEN, updatedSession.status)
        assertEquals(1500L, updatedSession.endedAt)
    }

    @Test
    fun `OVERRIDDEN stop reason emits PROTECTION_OVERRIDDEN with reason metadata and repeated stop does not duplicate`() = runTest {
        val stopWithIdGen = StopProtectionSessionUseCase(
            protectionRepository = protectionRepository,
            appProtectionProvider = protectionProvider,
            clock = clock,
            eventRepository = eventRepository,
            idGenerator = FakeIdGenerator()
        )
        val session = createActiveSession("s_override_evt").copy(userId = "user-alpha")
        protectionRepository.saveProtectionSession(session)

        clock.advanceTimeBy(1500L)
        val result = stopWithIdGen("s_override_evt", StopProtectionSessionUseCase.StopReason.OVERRIDDEN)
        assertTrue(result is Result.Success)

        val events = (eventRepository.getEventsForUser("user-alpha") as Result.Success).data
        assertEquals(1, events.size)
        assertEquals(EventType.PROTECTION_OVERRIDDEN, events[0].type)
        assertEquals("user-alpha", events[0].userId)
        assertEquals("s_override_evt", events[0].entityId)
        assertEquals("PROTECTION_SESSION", events[0].entityType)
        assertEquals("ACTIVE", events[0].previousState)
        assertEquals("OVERRIDDEN", events[0].newState)
        assertTrue(events[0].metadata!!.contains("reason=OVERRIDDEN"))

        // Repeated stop calls cannot create duplicate events
        assertTrue(stopWithIdGen("s_override_evt", StopProtectionSessionUseCase.StopReason.OVERRIDDEN) is Result.Failure)
        assertTrue(stopWithIdGen("s_override_evt", StopProtectionSessionUseCase.StopReason.USER_INITIATED) is Result.Failure)
        assertEquals(1, (eventRepository.getEventsForUser("user-alpha") as Result.Success).data.size)
    }

    @Test
    fun `USER_INITIATED, CANCELLED, and EXPIRED stops emit PROTECTION_ENDED with reason metadata without duplicates on repeat`() = runTest {
        val stopWithIdGen = StopProtectionSessionUseCase(
            protectionRepository = protectionRepository,
            appProtectionProvider = protectionProvider,
            clock = clock,
            eventRepository = eventRepository,
            idGenerator = FakeIdGenerator()
        )

        val sUser = createActiveSession("s-ui").copy(userId = "user-beta")
        val sCancel = createActiveSession("s-can").copy(userId = "user-beta")
        val sExpire = createActiveSession("s-exp").copy(userId = "user-beta", scheduledEndAt = 1500L)
        protectionRepository.saveProtectionSession(sUser)
        protectionRepository.saveProtectionSession(sCancel)
        protectionRepository.saveProtectionSession(sExpire)

        clock.setTime(2000L)
        assertTrue(stopWithIdGen("s-ui", StopProtectionSessionUseCase.StopReason.USER_INITIATED) is Result.Success)
        assertTrue(stopWithIdGen("s-can", StopProtectionSessionUseCase.StopReason.CANCELLED) is Result.Success)
        assertTrue(stopWithIdGen("s-exp", StopProtectionSessionUseCase.StopReason.EXPIRED) is Result.Success)

        // Repeated calls must not emit additional events
        assertTrue(stopWithIdGen("s-ui", StopProtectionSessionUseCase.StopReason.USER_INITIATED) is Result.Failure)
        assertTrue(stopWithIdGen("s-can", StopProtectionSessionUseCase.StopReason.CANCELLED) is Result.Failure)
        assertTrue(stopWithIdGen("s-exp", StopProtectionSessionUseCase.StopReason.EXPIRED) is Result.Success)

        val events = (eventRepository.getEventsForUser("user-beta") as Result.Success).data
        assertEquals(3, events.size)
        assertTrue(events.all { it.type == EventType.PROTECTION_ENDED && it.userId == "user-beta" })

        val uiEvt = events.first { it.entityId == "s-ui" }
        assertTrue(uiEvt.metadata!!.contains("reason=USER_INITIATED"))
        assertEquals("COMPLETED", uiEvt.newState)

        val canEvt = events.first { it.entityId == "s-can" }
        assertTrue(canEvt.metadata!!.contains("reason=CANCELLED"))
        assertEquals("CANCELLED", canEvt.newState)

        val expEvt = events.first { it.entityId == "s-exp" }
        assertTrue(expEvt.metadata!!.contains("reason=EXPIRED"))
        assertEquals("EXPIRED", expEvt.newState)
    }

    private fun createActiveSession(id: String): ProtectionSession {
        return ProtectionSession(
            id = id,
            userId = "user",
            startedAt = clock.currentTimeMillis(),
            status = ProtectionSessionStatus.ACTIVE,
            source = EntitySource.USER,
            overrideAllowed = true,
            createdAt = clock.currentTimeMillis(),
            updatedAt = clock.currentTimeMillis()
        )
    }
}
