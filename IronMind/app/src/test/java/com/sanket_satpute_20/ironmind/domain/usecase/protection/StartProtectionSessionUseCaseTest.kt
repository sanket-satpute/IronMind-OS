package com.sanket_satpute_20.ironmind.domain.usecase.protection

import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.EntitySource
import com.sanket_satpute_20.ironmind.domain.model.ProtectionSessionStatus
import com.sanket_satpute_20.ironmind.testutil.fake.FakeAppProtectionProvider
import com.sanket_satpute_20.ironmind.testutil.fake.FakeClock
import com.sanket_satpute_20.ironmind.testutil.fake.FakeIdGenerator
import com.sanket_satpute_20.ironmind.testutil.fake.FakeProtectionRepository
import com.sanket_satpute_20.ironmind.testutil.fake.FakeEventRepository
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class StartProtectionSessionUseCaseTest {

    private lateinit var protectionRepository: FakeProtectionRepository
    private lateinit var protectionProvider: FakeAppProtectionProvider
    private lateinit var idGenerator: FakeIdGenerator
    private lateinit var clock: FakeClock
    private lateinit var eventRepository: FakeEventRepository
    private lateinit var startProtectionSessionUseCase: StartProtectionSessionUseCase

    @Before
    fun setup() {
        protectionRepository = FakeProtectionRepository()
        protectionProvider = FakeAppProtectionProvider()
        idGenerator = FakeIdGenerator()
        clock = FakeClock()
        eventRepository = FakeEventRepository()
        startProtectionSessionUseCase = StartProtectionSessionUseCase(
            protectionRepository = protectionRepository,
            protectionProvider = protectionProvider,
            idGenerator = idGenerator,
            clock = clock,
            eventRepository = eventRepository
        )
    }

    @Test
    fun `starts session with correct fields and ACTIVE status`() = runTest {
        val userId = "user1"
        val commitmentId = "commit1"
        
        idGenerator.nextId = "test_session_id"
        
        val result = startProtectionSessionUseCase(
            userId = userId,
            commitmentId = commitmentId,
            overrideAllowed = false
        )
        
        assertTrue(result is Result.Success)
        val session = (result as Result.Success).data
        
        assertEquals("test_session_id", session.id)
        assertEquals(userId, session.userId)
        assertEquals(commitmentId, session.commitmentId)
        assertEquals(ProtectionSessionStatus.ACTIVE, session.status)
        assertEquals(EntitySource.USER, session.source)
        assertEquals(false, session.overrideAllowed)
        assertEquals(clock.currentTimeMillis(), session.startedAt)
        assertNull(session.endedAt)
        
        val savedSessionResult = protectionRepository.getProtectionSession(session.id)
        assertTrue(savedSessionResult is Result.Success)
        assertEquals(session, (savedSessionResult as Result.Success).data)
    }
}
