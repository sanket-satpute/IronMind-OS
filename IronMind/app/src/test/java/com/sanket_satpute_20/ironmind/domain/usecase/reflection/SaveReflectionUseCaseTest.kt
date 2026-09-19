package com.sanket_satpute_20.ironmind.domain.usecase.reflection

import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.testutil.fake.FakeClock
import com.sanket_satpute_20.ironmind.testutil.fake.FakeIdGenerator
import com.sanket_satpute_20.ironmind.domain.model.Reflection
import com.sanket_satpute_20.ironmind.domain.repository.ReflectionRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class FakeReflectionRepository : ReflectionRepository {
    private val reflections = mutableMapOf<String, Reflection>()

    override suspend fun saveReflection(reflection: Reflection): Result<Unit, Exception> {
        reflections[reflection.id] = reflection
        return Result.Success(Unit)
    }

    override suspend fun getReflection(id: String): Result<Reflection?, Exception> {
        return Result.Success(reflections[id])
    }
}

class SaveReflectionUseCaseTest {

    private lateinit var repository: FakeReflectionRepository
    private lateinit var idGenerator: FakeIdGenerator
    private lateinit var clock: FakeClock
    private lateinit var useCase: SaveReflectionUseCase

    @Before
    fun setup() {
        repository = FakeReflectionRepository()
        idGenerator = FakeIdGenerator()
        clock = FakeClock()
        useCase = SaveReflectionUseCase(repository, idGenerator, clock)
    }

    @Test
    fun `saveReflection success stores reflection`() = runTest {
        idGenerator.nextId = "ref-1"
        
        val result = useCase(
            userId = "user-1",
            content = "Today was a good day."
        )

        assertTrue(result is Result.Success)
        val reflection = (result as Result.Success).data
        
        assertEquals("ref-1", reflection.id)
        assertEquals("user-1", reflection.userId)
        assertEquals("Today was a good day.", reflection.content)
        assertEquals(clock.currentTimeMillis(), reflection.createdAt)

        val saved = (repository.getReflection("ref-1") as Result.Success).data
        assertEquals(reflection, saved)
    }

    @Test
    fun `saveReflection with blank content returns failure`() = runTest {
        val result = useCase(
            userId = "user-1",
            content = "   "
        )

        assertTrue(result is Result.Failure)
        assertTrue((result as Result.Failure).error is IllegalArgumentException)
    }
}
