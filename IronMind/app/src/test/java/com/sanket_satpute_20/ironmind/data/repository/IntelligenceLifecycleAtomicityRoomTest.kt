package com.sanket_satpute_20.ironmind.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.sanket_satpute_20.ironmind.data.local.IronMindDatabase
import com.sanket_satpute_20.ironmind.data.local.Jdk25CompatibleRobolectricTestRunner
import com.sanket_satpute_20.ironmind.data.local.entity.BarrierHypothesisEntity
import com.sanket_satpute_20.ironmind.data.local.entity.MemoryEntity
import com.sanket_satpute_20.ironmind.data.local.entity.PatternEntity
import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.EntitySource
import com.sanket_satpute_20.ironmind.domain.model.Event
import com.sanket_satpute_20.ironmind.domain.model.EventType
import com.sanket_satpute_20.ironmind.domain.model.MemoryConfirmationState
import com.sanket_satpute_20.ironmind.domain.model.MemoryStatus
import com.sanket_satpute_20.ironmind.domain.model.barrier.BarrierConfirmationState
import com.sanket_satpute_20.ironmind.domain.model.barrier.BarrierStatus
import com.sanket_satpute_20.ironmind.domain.model.pattern.PatternStatus
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
class IntelligenceLifecycleAtomicityRoomTest {

    private lateinit var context: Context
    private lateinit var db: IronMindDatabase
    private lateinit var patternRepository: PatternRepositoryImpl
    private lateinit var barrierRepository: BarrierRepositoryImpl
    private lateinit var memoryRepository: MemoryRepositoryImpl

    private val userId = "user-atomic-test"

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, IronMindDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        patternRepository = PatternRepositoryImpl(
            dao = db.patternDao(),
            database = db,
            eventDao = db.ironMindDao()
        )
        barrierRepository = BarrierRepositoryImpl(
            barrierDao = db.barrierDao(),
            database = db,
            eventDao = db.ironMindDao()
        )
        memoryRepository = MemoryRepositoryImpl(
            dao = db.ironMindDao(),
            database = db
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `pattern state update and event insertion commit atomically in Room`() = runBlocking {
        val initialPattern = PatternEntity(
            id = "pat-1",
            userId = userId,
            fingerprint = "fp-1",
            type = "TIME_PATTERN",
            description = "Works well in morning",
            conditions = null,
            predictedBehavior = null,
            confidence = 0.8f,
            evidenceCount = 2,
            evidenceReferences = null,
            firstObservedAt = 100L,
            lastObservedAt = 200L,
            status = "ACTIVE",
            confirmationState = "UNCONFIRMED",
            createdAt = 100L,
            updatedAt = 200L
        )
        db.patternDao().insertPattern(initialPattern)

        val event = Event(
            id = "ev-pat-1",
            userId = userId,
            type = EventType.PATTERN_CONFIRMED,
            entityType = "PATTERN",
            entityId = "pat-1",
            occurredAt = 300L,
            recordedAt = 300L,
            source = EntitySource.USER,
            previousState = "UNCONFIRMED",
            newState = "USER_CONFIRMED"
        )

        val result = patternRepository.updatePatternStateWithEvent(
            id = "pat-1",
            userId = userId,
            confirmationState = MemoryConfirmationState.USER_CONFIRMED,
            status = PatternStatus.ACTIVE,
            updatedAt = 300L,
            event = event
        )
        assertTrue(result is Result.Success)

        // Verify entity updated
        val updatedPattern = db.patternDao().getPatternById("pat-1")
        assertNotNull(updatedPattern)
        assertEquals("USER_CONFIRMED", updatedPattern!!.confirmationState)

        // Verify event inserted
        val persistedEvent = db.ironMindDao().getEvent("ev-pat-1")
        assertNotNull(persistedEvent)
        assertEquals("PATTERN_CONFIRMED", persistedEvent!!.type)
        assertEquals("pat-1", persistedEvent.entityId)

        // Repeated action is idempotent and does not create duplicate event
        val repeatedResult = patternRepository.updatePatternStateWithEvent(
            id = "pat-1",
            userId = userId,
            confirmationState = MemoryConfirmationState.USER_CONFIRMED,
            status = PatternStatus.ACTIVE,
            updatedAt = 400L,
            event = event.copy(id = "ev-pat-dup")
        )
        assertTrue(repeatedResult is Result.Success)
        assertNull(db.ironMindDao().getEvent("ev-pat-dup"))
    }

    @Test
    fun `barrier state update and event insertion commit atomically in Room`() = runBlocking {
        val initialBarrier = BarrierHypothesisEntity(
            id = "bar-1",
            userId = userId,
            category = "DISTRACTION",
            description = "Social media notifications",
            confirmationState = "UNCONFIRMED",
            status = "ACTIVE",
            sourceReflectionId = "ref-1",
            firstObservedAt = 100L,
            lastObservedAt = 200L
        )
        db.barrierDao().insert(initialBarrier)

        val event = Event(
            id = "ev-bar-1",
            userId = userId,
            type = EventType.BARRIER_CONFIRMED,
            entityType = "BARRIER",
            entityId = "bar-1",
            occurredAt = 300L,
            recordedAt = 300L,
            source = EntitySource.USER,
            previousState = "UNCONFIRMED",
            newState = "CONFIRMED"
        )

        val result = barrierRepository.updateBarrierStateWithEvent(
            id = "bar-1",
            userId = userId,
            confirmationState = BarrierConfirmationState.CONFIRMED,
            status = BarrierStatus.ACTIVE,
            now = 300L,
            event = event
        )
        assertTrue(result is Result.Success)

        val updated = db.barrierDao().getBarrierById("bar-1")
        assertNotNull(updated)
        assertEquals("CONFIRMED", updated!!.confirmationState)

        val persistedEvent = db.ironMindDao().getEvent("ev-bar-1")
        assertNotNull(persistedEvent)
        assertEquals("BARRIER_CONFIRMED", persistedEvent!!.type)
    }

    @Test
    fun `memory deletion and MEMORY_DELETED event commit atomically and event survives row deletion`() = runBlocking {
        val initialMemory = MemoryEntity(
            id = "mem-1",
            userId = userId,
            type = "fact",
            content = "Old address",
            source = "USER",
            confidence = 1.0f,
            evidenceCount = 1,
            firstObservedAt = 100L,
            lastObservedAt = 200L,
            confirmationState = "USER_CONFIRMED",
            status = "ACTIVE",
            expiresAt = null,
            createdAt = 100L,
            updatedAt = 200L,
            schemaVersion = 1
        )
        db.ironMindDao().insertMemory(initialMemory)

        val event = Event(
            id = "ev-mem-del-1",
            userId = userId,
            type = EventType.MEMORY_DELETED,
            entityType = "MEMORY",
            entityId = "mem-1",
            occurredAt = 300L,
            recordedAt = 300L,
            source = EntitySource.USER,
            previousState = "Old address"
        )

        val deleteResult = memoryRepository.deleteMemoryWithEvent(userId, "mem-1", event)
        assertTrue(deleteResult is Result.Success)

        // Memory row is purged
        val memoryInDb = db.ironMindDao().getMemoryById("mem-1")
        assertNull(memoryInDb)

        // Event survives and is persisted
        val eventInDb = db.ironMindDao().getEvent("ev-mem-del-1")
        assertNotNull(eventInDb)
        assertEquals("MEMORY_DELETED", eventInDb!!.type)
        assertEquals("Old address", eventInDb.previousState)
    }
}
