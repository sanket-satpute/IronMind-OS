package com.sanket_satpute_20.ironmind.domain.usecase.protection

import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.EntitySource
import com.sanket_satpute_20.ironmind.domain.model.ProtectionSession
import com.sanket_satpute_20.ironmind.domain.model.ProtectionSessionStatus
import com.sanket_satpute_20.ironmind.testutil.fake.FakeClock
import com.sanket_satpute_20.ironmind.testutil.fake.FakeProtectionRepository
import com.sanket_satpute_20.ironmind.testutil.fake.FakeAppProtectionProvider
import com.sanket_satpute_20.ironmind.testutil.fake.FakeEventRepository
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
