package com.sanket_satpute_20.ironmind.domain.engine

import com.sanket_satpute_20.ironmind.domain.ai.AIOutput
import com.sanket_satpute_20.ironmind.domain.ai.AIRequest
import com.sanket_satpute_20.ironmind.domain.ai.IronMindAI
import com.sanket_satpute_20.ironmind.domain.common.Clock
import com.sanket_satpute_20.ironmind.domain.common.IdGenerator
import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.AutonomyCapability
import com.sanket_satpute_20.ironmind.domain.model.AutonomyLevel
import com.sanket_satpute_20.ironmind.domain.model.AutonomySettings
import com.sanket_satpute_20.ironmind.domain.model.Memory
import com.sanket_satpute_20.ironmind.domain.model.MemoryConfirmationState
import com.sanket_satpute_20.ironmind.domain.model.Reflection
import com.sanket_satpute_20.ironmind.domain.model.pattern.Pattern
import com.sanket_satpute_20.ironmind.domain.model.pattern.PatternStatus
import com.sanket_satpute_20.ironmind.domain.repository.AutonomySettingsRepository
import com.sanket_satpute_20.ironmind.domain.repository.MemoryRepository
import com.sanket_satpute_20.ironmind.domain.repository.PatternRepository
import com.sanket_satpute_20.ironmind.domain.repository.ReflectionRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AutonomousReflectionEngineImplTest {

    private lateinit var reflectionRepository: AutoRefFakeReflectionRepository
    private lateinit var ironMindAI: AutoRefFakeIronMindAI
    private lateinit var autonomySettingsRepository: AutoRefFakeAutonomySettingsRepository
    private lateinit var memoryRepository: AutoRefFakeMemoryRepository
    private lateinit var patternRepository: AutoRefFakePatternRepository
    private lateinit var idGenerator: AutoRefFakeIdGenerator
    private lateinit var clock: AutoRefFakeClock
    private lateinit var engine: AutonomousReflectionEngineImpl

    @Before
    fun setup() {
        reflectionRepository = AutoRefFakeReflectionRepository()
        ironMindAI = AutoRefFakeIronMindAI()
        autonomySettingsRepository = AutoRefFakeAutonomySettingsRepository()
        memoryRepository = AutoRefFakeMemoryRepository()
        patternRepository = AutoRefFakePatternRepository()
        idGenerator = AutoRefFakeIdGenerator()
        clock = AutoRefFakeClock()
        
        engine = AutonomousReflectionEngineImpl(
            reflectionRepository = reflectionRepository,
            ironMindAI = ironMindAI,
            autonomySettingsRepository = autonomySettingsRepository,
            memoryRepository = memoryRepository,
            patternRepository = patternRepository,
            idGenerator = idGenerator,
            clock = clock
        )
    }

    @Test
    fun `processReflection skips processing if REFLECTION_PROCESSING is OFF`() = runTest {
        val reflection = Reflection(
            id = "r1",
            userId = "user1",
            content = "This was a good day.",
            targetEntityId = null,
            targetEntityType = null,
            sentiment = null,
            createdAt = clock.currentTimeMillis()
        )
        reflectionRepository.reflections.add(reflection)
        
        autonomySettingsRepository.settings = AutonomySettings(
            userId = "user1",
            levels = mapOf(
                AutonomyCapability.REFLECTION_PROCESSING to AutonomyLevel.OFF
            )
        )

        val result = engine.processReflection("r1")
        assertTrue(result is Result.Success)
        
        // AI should not be called
        assertEquals(0, ironMindAI.requests.size)
    }

    @Test
    fun `processReflection creates memory automatically when FULL_AUTO`() = runTest {
        val reflection = Reflection(
            id = "r2",
            userId = "user1",
            content = "I prefer reading in the morning.",
            targetEntityId = null,
            targetEntityType = null,
            sentiment = null,
            createdAt = clock.currentTimeMillis()
        )
        reflectionRepository.reflections.add(reflection)
        
        autonomySettingsRepository.settings = AutonomySettings(
            userId = "user1",
            levels = mapOf(
                AutonomyCapability.REFLECTION_PROCESSING to AutonomyLevel.FULL_AUTO,
                AutonomyCapability.MEMORY_PATTERN_PROCESSING to AutonomyLevel.FULL_AUTO
            )
        )
        
        ironMindAI.mockResponse = AIOutput.MemoryCandidate(
            candidateContent = "User prefers reading in the morning.",
            confidence = 0.9f
        )

        val result = engine.processReflection("r2")
        assertTrue(result is Result.Success)
        
        // Check memory is saved and auto-confirmed
        assertEquals(1, memoryRepository.memories.size)
        assertEquals(MemoryConfirmationState.SYSTEM_CONFIRMED, memoryRepository.memories[0].confirmationState)
        assertEquals("User prefers reading in the morning.", memoryRepository.memories[0].content)
    }

    @Test
    fun `processReflection proposes memory when ASK_BEFORE_ACTION`() = runTest {
        val reflection = Reflection(
            id = "r3",
            userId = "user1",
            content = "I prefer reading in the morning.",
            targetEntityId = null,
            targetEntityType = null,
            sentiment = null,
            createdAt = clock.currentTimeMillis()
        )
        reflectionRepository.reflections.add(reflection)
        
        autonomySettingsRepository.settings = AutonomySettings(
            userId = "user1",
            levels = mapOf(
                AutonomyCapability.REFLECTION_PROCESSING to AutonomyLevel.FULL_AUTO,
                AutonomyCapability.MEMORY_PATTERN_PROCESSING to AutonomyLevel.ASK_BEFORE_ACTION
            )
        )
        
        ironMindAI.mockResponse = AIOutput.MemoryCandidate(
            candidateContent = "User prefers reading in the morning.",
            confidence = 0.9f
        )

        val result = engine.processReflection("r3")
        assertTrue(result is Result.Success)
        
        // Check memory is saved but unconfirmed
        assertEquals(1, memoryRepository.memories.size)
        assertEquals(MemoryConfirmationState.UNCONFIRMED, memoryRepository.memories[0].confirmationState)
    }

    @Test
    fun `processReflection creates pattern automatically when FULL_AUTO`() = runTest {
        val reflection = Reflection(
            id = "r4",
            userId = "user1",
            content = "I always feel tired after lunch.",
            targetEntityId = null,
            targetEntityType = null,
            sentiment = null,
            createdAt = clock.currentTimeMillis()
        )
        reflectionRepository.reflections.add(reflection)
        
        autonomySettingsRepository.settings = AutonomySettings(
            userId = "user1",
            levels = mapOf(
                AutonomyCapability.REFLECTION_PROCESSING to AutonomyLevel.FULL_AUTO,
                AutonomyCapability.MEMORY_PATTERN_PROCESSING to AutonomyLevel.FULL_AUTO
            )
        )
        
        ironMindAI.mockResponse = AIOutput.PatternCandidate(
            patternDescription = "User experiences afternoon fatigue post-lunch.",
            confidence = 0.85f
        )

        val result = engine.processReflection("r4")
        assertTrue(result is Result.Success)
        
        // Check pattern is saved and active
        assertEquals(1, patternRepository.patterns.size)
        assertEquals(PatternStatus.ACTIVE, patternRepository.patterns[0].status)
        assertEquals("User experiences afternoon fatigue post-lunch.", patternRepository.patterns[0].description)
    }

    @Test
    fun `processReflection skips candidate creation when SUGGEST_ONLY`() = runTest {
        val reflection = Reflection(
            id = "r5",
            userId = "user1",
            content = "I always feel tired after lunch.",
            targetEntityId = null,
            targetEntityType = null,
            sentiment = null,
            createdAt = clock.currentTimeMillis()
        )
        reflectionRepository.reflections.add(reflection)
        
        autonomySettingsRepository.settings = AutonomySettings(
            userId = "user1",
            levels = mapOf(
                AutonomyCapability.REFLECTION_PROCESSING to AutonomyLevel.FULL_AUTO,
                AutonomyCapability.MEMORY_PATTERN_PROCESSING to AutonomyLevel.SUGGEST_ONLY
            )
        )
        
        ironMindAI.mockResponse = AIOutput.PatternCandidate(
            patternDescription = "User experiences afternoon fatigue post-lunch.",
            confidence = 0.85f
        )

        val result = engine.processReflection("r5")
        assertTrue(result is Result.Success)
        
        // Pattern should not be saved automatically since it's SUGGEST_ONLY
        assertEquals(0, patternRepository.patterns.size)
    }
}

// ---------------------------------------------------------
// Fakes for Test
// ---------------------------------------------------------

class AutoRefFakeReflectionRepository : ReflectionRepository {
    val reflections = mutableListOf<Reflection>()

    override suspend fun saveReflection(reflection: Reflection): Result<Unit, Exception> {
        reflections.add(reflection)
        return Result.Success(Unit)
    }

    override suspend fun getReflection(id: String): Result<Reflection?, Exception> {
        return Result.Success(reflections.find { it.id == id })
    }

    override suspend fun getReflectionsForDateRange(userId: String, startTime: Long, endTime: Long): Result<List<Reflection>, Exception> {
        return Result.Success(reflections.filter { it.userId == userId && it.createdAt in startTime..endTime })
    }
    
    override suspend fun searchReflections(userId: String, query: String): Result<List<Reflection>, Exception> {
        return Result.Success(reflections.filter { it.userId == userId && it.content.contains(query, ignoreCase = true) })
    }
}

class AutoRefFakeIronMindAI : IronMindAI {
    var mockResponse: AIOutput = AIOutput.NoAction()
    val requests = mutableListOf<AIRequest>()

    override suspend fun process(request: AIRequest): Result<AIOutput, Exception> {
        requests.add(request)
        return Result.Success(mockResponse)
    }
}

class AutoRefFakeAutonomySettingsRepository : AutonomySettingsRepository {
    var settings: AutonomySettings = AutonomySettings("user1", emptyMap())

    override suspend fun getSettings(userId: String): Result<AutonomySettings, Exception> {
        return Result.Success(settings)
    }

    override suspend fun updateLevel(userId: String, capability: AutonomyCapability, level: AutonomyLevel): Result<Unit, Exception> {
        return Result.Success(Unit)
    }

    override suspend fun setGlobalPause(userId: String, isPaused: Boolean): Result<Unit, Exception> {
        return Result.Success(Unit)
    }
}

class AutoRefFakeMemoryRepository : MemoryRepository {
    val memories = mutableListOf<Memory>()

    override suspend fun saveMemory(memory: Memory): Result<Memory, Exception> {
        memories.add(memory)
        return Result.Success(memory)
    }

    override suspend fun getMemoryById(id: String): Result<Memory?, Exception> = Result.Success(memories.find { it.id == id })
    override suspend fun getMemoriesForUser(userId: String): Result<List<Memory>, Exception> = Result.Success(memories.filter { it.userId == userId })
    override suspend fun getActiveMemoriesForUser(userId: String): Result<List<Memory>, Exception> = Result.Success(memories)
    override suspend fun getMemoriesForDateRange(userId: String, startTime: Long, endTime: Long): Result<List<Memory>, Exception> = Result.Success(memories)
    override suspend fun searchMemories(userId: String, query: String): Result<List<Memory>, Exception> = Result.Success(memories)
}

class AutoRefFakePatternRepository : PatternRepository {
    val patterns = mutableListOf<Pattern>()

    override suspend fun savePattern(pattern: Pattern): Result<Unit, Exception> {
        patterns.add(pattern)
        return Result.Success(Unit)
    }

    override suspend fun getPattern(id: String): Result<Pattern?, Exception> = Result.Success(patterns.find { it.id == id })
    override suspend fun getPatternsForUser(userId: String): Result<List<Pattern>, Exception> = Result.Success(patterns.filter { it.userId == userId })
    override suspend fun getPatternsByType(userId: String, type: com.sanket_satpute_20.ironmind.domain.model.pattern.PatternType): Result<List<Pattern>, Exception> = Result.Success(patterns.filter { it.type == type })
    override suspend fun getPatternsByStatus(userId: String, status: PatternStatus): Result<List<Pattern>, Exception> = Result.Success(patterns.filter { it.status == status })
    override suspend fun updatePatternConfidence(id: String, confidence: Float, lastObservedAt: Long): Result<Unit, Exception> = Result.Success(Unit)
    override suspend fun deletePattern(id: String): Result<Unit, Exception> = Result.Success(Unit)
}

class AutoRefFakeIdGenerator : IdGenerator {
    private var nextId = 1
    override fun generateId(): String = "id_${nextId++}"
}

class AutoRefFakeClock : Clock {
    var time: Long = 1000L
    override fun currentTimeMillis(): Long = time
}
