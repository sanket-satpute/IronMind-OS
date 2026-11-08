package com.sanket_satpute_20.ironmind.domain.usecase.protection

import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.EntitySource
import com.sanket_satpute_20.ironmind.domain.model.ProtectionSession
import com.sanket_satpute_20.ironmind.domain.model.ProtectionSessionStatus
import com.sanket_satpute_20.ironmind.testutil.fake.FakeAppProtectionProvider
import com.sanket_satpute_20.ironmind.testutil.fake.FakeClock
import com.sanket_satpute_20.ironmind.testutil.fake.FakeEventRepository
import com.sanket_satpute_20.ironmind.testutil.fake.FakeProtectionRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class GetActiveProtectionSessionUseCaseTest {

    private lateinit var protectionRepository: FakeProtectionRepository
    private lateinit var appProtectionProvider: FakeAppProtectionProvider
    private lateinit var clock: FakeClock
    private lateinit var eventRepository: FakeEventRepository
    private lateinit var stopProtectionSessionUseCase: StopProtectionSessionUseCase
    private lateinit var getActiveProtectionSessionUseCase: GetActiveProtectionSessionUseCase

    private val userId = "user_test_get_active"

    @Before
    fun setup() {
        protectionRepository = FakeProtectionRepository()
        appProtectionProvider = FakeAppProtectionProvider()
        clock = FakeClock(1_000_000L)
        eventRepository = FakeEventRepository()
        stopProtectionSessionUseCase = StopProtectionSessionUseCase(
            protectionRepository = protectionRepository,
            appProtectionProvider = appProtectionProvider,
            clock = clock,
            eventRepository = eventRepository
        )
        getActiveProtectionSessionUseCase = GetActiveProtectionSessionUseCase(
            protectionRepository = protectionRepository,
            clock = clock,
            stopProtectionSessionUseCase = stopProtectionSessionUseCase
        )
    }

    @Test
    fun `returns null when no active session exists`() = runTest {
        val result = getActiveProtectionSessionUseCase(userId)
        assertTrue(result is Result.Success)
        assertNull((result as Result.Success).data)
    }

    @Test
    fun `returns active session when scheduledEndAt is in the future`() = runTest {
        val session = createSession("s1", scheduledEndAt = 2_000_000L)
        protectionRepository.saveProtectionSession(session)

        val result = getActiveProtectionSessionUseCase(userId)
        assertTrue(result is Result.Success)
        val active = (result as Result.Success).data
        assertNotNull(active)
        assertEquals("s1", active?.id)
        assertEquals(ProtectionSessionStatus.ACTIVE, active?.status)
    }

    @Test
    fun `transitions session to EXPIRED and returns null when scheduledEndAt has passed`() = runTest {
        val session = createSession("s2", scheduledEndAt = 1_500_000L)
        protectionRepository.saveProtectionSession(session)

        // Advance clock past scheduledEndAt
        clock.setTime(1_600_000L)

        val result = getActiveProtectionSessionUseCase(userId)
        assertTrue(result is Result.Success)
        assertNull((result as Result.Success).data)

        // Verify session in repository transitioned to EXPIRED
        val dbSession = (protectionRepository.getProtectionSession("s2") as Result.Success).data
        assertEquals(ProtectionSessionStatus.EXPIRED, dbSession.status)
        assertEquals(1_500_000L, dbSession.endedAt)
    }

    @Test
    fun `transitions session to EXPIRED and returns null when scheduledEndAt equals current time`() = runTest {
        val session = createSession("s3", scheduledEndAt = 1_500_000L)
        protectionRepository.saveProtectionSession(session)

        // Clock exactly at scheduledEndAt
        clock.setTime(1_500_000L)

        val result = getActiveProtectionSessionUseCase(userId)
        assertTrue(result is Result.Success)
        assertNull((result as Result.Success).data)

        val dbSession = (protectionRepository.getProtectionSession("s3") as Result.Success).data
        assertEquals(ProtectionSessionStatus.EXPIRED, dbSession.status)
        assertEquals(1_500_000L, dbSession.endedAt)
    }

    private fun createSession(id: String, scheduledEndAt: Long?): ProtectionSession {
        return ProtectionSession(
            id = id,
            userId = userId,
            startedAt = 1_000_000L,
            scheduledEndAt = scheduledEndAt,
            endedAt = null,
            status = ProtectionSessionStatus.ACTIVE,
            source = EntitySource.USER,
            overrideAllowed = true,
            createdAt = 1_000_000L,
            updatedAt = 1_000_000L
        )
    }
}

