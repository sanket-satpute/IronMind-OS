package com.sanket_satpute_20.ironmind.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.sanket_satpute_20.ironmind.data.local.IronMindDatabase
import com.sanket_satpute_20.ironmind.data.local.Jdk25CompatibleRobolectricTestRunner
import com.sanket_satpute_20.ironmind.data.local.dao.BarrierDao
import com.sanket_satpute_20.ironmind.data.local.entity.BarrierHypothesisEntity
import com.sanket_satpute_20.ironmind.domain.ai.BarrierCategory
import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.barrier.BarrierConfirmationState
import com.sanket_satpute_20.ironmind.domain.model.barrier.BarrierStatus
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(Jdk25CompatibleRobolectricTestRunner::class)
@Config(sdk = [34])
class BarrierDaoAndRepositoryLookupTest {

    private lateinit var context: Context
    private lateinit var db: IronMindDatabase
    private lateinit var barrierDao: BarrierDao
    private lateinit var repository: BarrierRepositoryImpl

    private val user1 = "user-1"
    private val user2 = "user-2"

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, IronMindDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        barrierDao = db.barrierDao()
        repository = BarrierRepositoryImpl(barrierDao)
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun createBarrierEntity(id: String, userId: String): BarrierHypothesisEntity {
        return BarrierHypothesisEntity(
            id = id,
            userId = userId,
            category = "LOW_ENERGY",
            description = "Fatigue after lunch",
            confirmationState = "UNCONFIRMED",
            status = "ACTIVE",
            sourceReflectionId = "ref-1",
            firstObservedAt = 1000L,
            lastObservedAt = 2000L
        )
    }

    @Test
    fun barrierDao_getBarrierById_returnsEntityWhenPresent() = runBlocking {
        val entity = createBarrierEntity("bar-10", user1)
        barrierDao.insert(entity)

        val retrieved = barrierDao.getBarrierById("bar-10")
        assertNotNull(retrieved)
        assertEquals("bar-10", retrieved?.id)
        assertEquals(user1, retrieved?.userId)
        assertEquals("LOW_ENERGY", retrieved?.category)
        assertEquals("Fatigue after lunch", retrieved?.description)
    }

    @Test
    fun barrierDao_getBarrierById_returnsNullWhenAbsent() = runBlocking {
        val retrieved = barrierDao.getBarrierById("bar-nonexistent")
        assertNull(retrieved)
    }

    @Test
    fun barrierRepository_getBarrier_returnsMappedDomainModel() = runBlocking {
        val entity = createBarrierEntity("bar-20", user2)
        barrierDao.insert(entity)

        val result = repository.getBarrier("bar-20")
        assertTrue(result is Result.Success)
        val barrier = (result as Result.Success).data
        assertNotNull(barrier)
        assertEquals("bar-20", barrier?.id)
        assertEquals(user2, barrier?.userId)
        assertEquals(BarrierCategory.LOW_ENERGY, barrier?.category)
        assertEquals(BarrierStatus.ACTIVE, barrier?.status)
        assertEquals(BarrierConfirmationState.UNCONFIRMED, barrier?.confirmationState)
    }

    @Test
    fun barrierRepository_getBarrier_returnsNullWhenAbsent() = runBlocking {
        val result = repository.getBarrier("bar-nonexistent")
        assertTrue(result is Result.Success)
        assertNull((result as Result.Success).data)
    }
}
