package com.sanket_satpute_20.ironmind.domain.engine

import com.sanket_satpute_20.ironmind.domain.common.Clock
import com.sanket_satpute_20.ironmind.domain.model.decision.CandidateAction
import com.sanket_satpute_20.ironmind.domain.model.decision.DecisionResult
import com.sanket_satpute_20.ironmind.domain.model.decision.EvaluationContext
import com.sanket_satpute_20.ironmind.domain.model.protection.ProtectionCandidate
import com.sanket_satpute_20.ironmind.domain.model.protection.ProtectionResult
import com.sanket_satpute_20.ironmind.domain.usecase.protection.StartProtectionSessionUseCase
import com.sanket_satpute_20.ironmind.testutil.fake.FakeIronLogger
import com.sanket_satpute_20.ironmind.testutil.fake.FakeEventRepository
import com.sanket_satpute_20.ironmind.testutil.fake.FakeProtectionRepository
import com.sanket_satpute_20.ironmind.domain.common.IdGenerator
import com.sanket_satpute_20.ironmind.domain.provider.AppProtectionProvider
import com.sanket_satpute_20.ironmind.domain.common.Result
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import com.sanket_satpute_20.ironmind.domain.model.EntitySource

class AutoProtectionEngineImplTest {

    private lateinit var logger: FakeIronLogger
    private lateinit var decisionEngine: FakeDecisionEngine
    private lateinit var protectionProvider: FakeAppProtectionProvider
    private lateinit var engine: AutoProtectionEngineImpl

    private var currentTimeMs: Long = 1000000L

    @Before
    fun setup() {
        logger = FakeIronLogger()
        decisionEngine = FakeDecisionEngine()
        protectionProvider = FakeAppProtectionProvider()

        val startProtectionSessionUseCase = StartProtectionSessionUseCase(
            protectionRepository = FakeProtectionRepository(),
            protectionProvider = protectionProvider,
            idGenerator = object : IdGenerator {
                override fun generateId() = "id-1"
            },
            clock = object : Clock {
                override fun currentTimeMillis() = currentTimeMs
            },
            eventRepository = FakeEventRepository()
        )

        engine = AutoProtectionEngineImpl(
            decisionEngine = decisionEngine,
            startProtectionSessionUseCase = startProtectionSessionUseCase,
            clock = object : Clock {
                override fun currentTimeMillis() = currentTimeMs
            },
            logger = logger
        )
    }

    @Test
    fun `protect returns Conflict when targetPackages is empty`() = runTest {
        val candidate = ProtectionCandidate(
            title = "Test",
            description = "Desc",
            targetPackages = emptyList(),
            durationMs = 3600000L
        )

        val result = engine.protect("u1", candidate)

        assertTrue(result is ProtectionResult.Conflict)
    }

    @Test
    fun `protect returns Conflict when duration is invalid`() = runTest {
        val candidate = ProtectionCandidate(
            title = "Test",
            description = "Desc",
            targetPackages = listOf("com.example.app"),
            durationMs = 5 * 60 * 60 * 1000L // 5 hours (over max of 4)
        )

        val result = engine.protect("u1", candidate)

        assertTrue(result is ProtectionResult.Conflict)
    }

    @Test
    fun `protect creates session when authorized and parameters valid`() = runTest {
        decisionEngine.nextResult = DecisionResult.EXECUTE

        val candidate = ProtectionCandidate(
            title = "Test",
            description = "Desc",
            targetPackages = listOf("com.example.app"),
            durationMs = 3600000L // 1 hour
        )

        val result = engine.protect("u1", candidate)

        assertTrue(result is ProtectionResult.Protected)
        val session = (result as ProtectionResult.Protected).session
        assertEquals(EntitySource.AI, session.source)
        assertTrue(session.overrideAllowed) // Reversible
        assertEquals(currentTimeMs + 3600000L, session.scheduledEndAt)
    }

    @Test
    fun `protect returns Conflict when missing permissions`() = runTest {
        decisionEngine.nextResult = DecisionResult.EXECUTE
        protectionProvider.hasPermissions = false

        val candidate = ProtectionCandidate(
            title = "Test",
            description = "Desc",
            targetPackages = listOf("com.example.app"),
            durationMs = 3600000L
        )

        val result = engine.protect("u1", candidate)

        assertTrue(result is ProtectionResult.Conflict)
    }

    @Test
    fun `protect returns RequiresPermission when DecisionEngine yields ASK_USER`() = runTest {
        decisionEngine.nextResult = DecisionResult.ASK_USER

        val candidate = ProtectionCandidate(
            title = "Test",
            description = "Desc",
            targetPackages = listOf("com.example.app"),
            durationMs = 3600000L
        )

        val result = engine.protect("u1", candidate)

        assertTrue(result is ProtectionResult.RequiresPermission)
    }
}

class FakeAppProtectionProvider : AppProtectionProvider {
    var hasPermissions = true
    var isActive = false

    override fun hasRequiredPermissions(): Boolean = hasPermissions

    override suspend fun applyProtection(targetPackages: List<String>): Result<Unit, Exception> {
        isActive = true
        return Result.Success(Unit)
    }

    override suspend fun removeProtection(): Result<Unit, Exception> {
        isActive = false
        return Result.Success(Unit)
    }

    override fun isProtectionActive(): Boolean = isActive
}
