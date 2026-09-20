package com.sanket_satpute_20.ironmind.domain.engine

import com.sanket_satpute_20.ironmind.domain.model.AutonomyCapability
import com.sanket_satpute_20.ironmind.domain.model.AutonomyLevel
import com.sanket_satpute_20.ironmind.domain.model.decision.CandidateAction
import com.sanket_satpute_20.ironmind.domain.model.decision.DecisionResult
import com.sanket_satpute_20.ironmind.domain.model.decision.EvaluationContext
import com.sanket_satpute_20.ironmind.testutil.fake.FakeAutonomySettingsRepository
import com.sanket_satpute_20.ironmind.testutil.fake.FakeIronLogger
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class DecisionEngineImplTest {

    private lateinit var settingsRepository: FakeAutonomySettingsRepository
    private lateinit var logger: FakeIronLogger
    private lateinit var engine: DecisionEngineImpl

    @Before
    fun setup() {
        settingsRepository = FakeAutonomySettingsRepository()
        logger = FakeIronLogger()
        engine = DecisionEngineImpl(settingsRepository, logger)
    }

    @Test
    fun `user override active forces STAY_SILENT`() = runTest {
        val candidate = CandidateAction(AutonomyCapability.PROTECTION)
        val context = EvaluationContext(isUserOverrideActive = true)
        
        val result = engine.evaluate("user-1", candidate, context)
        
        assertEquals(DecisionResult.STAY_SILENT, result)
        assertEquals(1, logger.loggedMessages.size)
        assert(logger.loggedMessages[0].contains("USER_OVERRIDE_ACTIVE"))
    }

    @Test
    fun `cooldown active forces STAY_SILENT`() = runTest {
        val candidate = CandidateAction(AutonomyCapability.PROTECTION)
        val context = EvaluationContext(isCooldownActive = true)
        
        val result = engine.evaluate("user-1", candidate, context)
        
        assertEquals(DecisionResult.STAY_SILENT, result)
        assert(logger.loggedMessages[0].contains("COOLDOWN_ACTIVE"))
    }

    @Test
    fun `duplicate action forces STAY_SILENT`() = runTest {
        val candidate = CandidateAction(AutonomyCapability.PROTECTION)
        val context = EvaluationContext(isDuplicate = true)
        
        val result = engine.evaluate("user-1", candidate, context)
        
        assertEquals(DecisionResult.STAY_SILENT, result)
        assert(logger.loggedMessages[0].contains("DUPLICATE_ACTION"))
    }

    @Test
    fun `level OFF forces STAY_SILENT`() = runTest {
        settingsRepository.updateLevel("user-1", AutonomyCapability.PLANNING, AutonomyLevel.OFF)
        val candidate = CandidateAction(AutonomyCapability.PLANNING)
        val context = EvaluationContext()
        
        val result = engine.evaluate("user-1", candidate, context)
        
        assertEquals(DecisionResult.STAY_SILENT, result)
    }

    @Test
    fun `level SUGGEST_ONLY returns SUGGEST`() = runTest {
        settingsRepository.updateLevel("user-1", AutonomyCapability.PLANNING, AutonomyLevel.SUGGEST_ONLY)
        val candidate = CandidateAction(AutonomyCapability.PLANNING)
        val context = EvaluationContext()
        
        val result = engine.evaluate("user-1", candidate, context)
        
        assertEquals(DecisionResult.SUGGEST, result)
    }

    @Test
    fun `level ASK_BEFORE_ACTION returns ASK_USER`() = runTest {
        settingsRepository.updateLevel("user-1", AutonomyCapability.PLANNING, AutonomyLevel.ASK_BEFORE_ACTION)
        val candidate = CandidateAction(AutonomyCapability.PLANNING)
        val context = EvaluationContext()
        
        val result = engine.evaluate("user-1", candidate, context)
        
        assertEquals(DecisionResult.ASK_USER, result)
    }

    @Test
    fun `level FULL_AUTO executes when safe and permitted`() = runTest {
        settingsRepository.updateLevel("user-1", AutonomyCapability.PLANNING, AutonomyLevel.FULL_AUTO)
        val candidate = CandidateAction(AutonomyCapability.PLANNING, isSafe = true, isReversible = true)
        val context = EvaluationContext(hasRequiredPermissions = true)
        
        val result = engine.evaluate("user-1", candidate, context)
        
        assertEquals(DecisionResult.EXECUTE, result)
    }

    @Test
    fun `level FULL_AUTO downgrades to ASK_USER when not safe`() = runTest {
        settingsRepository.updateLevel("user-1", AutonomyCapability.PLANNING, AutonomyLevel.FULL_AUTO)
        val candidate = CandidateAction(AutonomyCapability.PLANNING, isSafe = false, isReversible = true)
        val context = EvaluationContext(hasRequiredPermissions = true)
        
        val result = engine.evaluate("user-1", candidate, context)
        
        assertEquals(DecisionResult.ASK_USER, result)
    }

    @Test
    fun `level FULL_AUTO downgrades to ASK_USER when irreversible`() = runTest {
        settingsRepository.updateLevel("user-1", AutonomyCapability.PLANNING, AutonomyLevel.FULL_AUTO)
        val candidate = CandidateAction(AutonomyCapability.PLANNING, isSafe = true, isReversible = false)
        val context = EvaluationContext(hasRequiredPermissions = true)
        
        val result = engine.evaluate("user-1", candidate, context)
        
        assertEquals(DecisionResult.ASK_USER, result)
    }

    @Test
    fun `level FULL_AUTO downgrades to ASK_USER when lacking permissions`() = runTest {
        settingsRepository.updateLevel("user-1", AutonomyCapability.PLANNING, AutonomyLevel.FULL_AUTO)
        val candidate = CandidateAction(AutonomyCapability.PLANNING, isSafe = true, isReversible = true)
        val context = EvaluationContext(hasRequiredPermissions = false)
        
        val result = engine.evaluate("user-1", candidate, context)
        
        assertEquals(DecisionResult.ASK_USER, result)
    }
}
